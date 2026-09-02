/*
 * DroidVNC-NG configurable keyboard shortcuts.
 *
 * Author is slab-tsuchiya <https://github.com/slab-tsuchiya>.
 *
 * You can redistribute and/or modify this program under the terms of the
 * GNU General Public License version 2 as published by the Free Software
 * Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General
 * Public License for more details.
 */

package net.christianbeier.droidvnc_ng

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.Toast
import androidx.preference.PreferenceManager

/**
 * Settings view for the configurable keyboard shortcuts (issue #13). Inflates one row per
 * [Action] -- three modifier checkboxes (Ctrl/Alt/Shift) plus a trigger-key [Spinner] -- and owns
 * their whole lifecycle: loading the persisted chords, offering localized key labels, rejecting a
 * chord already assigned to another action, persisting a change and live-updating the running
 * [InputService]. Keeping all of this here rather than in MainActivity makes the widget set
 * self-contained; the Activity just drops the view into its layout.
 */
class InputKeyShortcutSetupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TableLayout(context, attrs) {

    /** Runtime state for one action row: its resolved controls, prefs key and last-good chord. */
    private class Row(
        val prefKey: String,
        val ctrl: CheckBox,
        val alt: CheckBox,
        val shift: CheckBox,
        val key: Spinner,
    ) {
        /** Last-good canonical chord string ("" when unassigned); the revert target on a conflict. */
        var selected: String = ""

        /** Keysym for each key-spinner position, parallel to its adapter (a trailing custom entry
         *  is appended when the bound chord uses a key outside the curated list). */
        var keysyms: List<Long> = emptyList()
    }

    private val rows = ArrayList<Row>(SPECS.size)

    /** True while programmatically setting control state, so the change listeners stay quiet. */
    private var updating = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_key_shortcut_setup, this, true)
        if (!isInEditMode) {
            setupRows()
        }
    }

    private fun setupRows() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val defaults = Defaults(context)

        // resolve each row and set its initial state with the listeners still detached
        updating = true
        for (spec in SPECS) {
            val row = Row(
                spec.prefKey,
                findViewById(spec.ctrlId),
                findViewById(spec.altId),
                findViewById(spec.shiftId),
                findViewById(spec.keyId),
            )
            val chord = Chord.fromString(prefs.getString(spec.prefKey, spec.default(defaults)))
            bindKeyChoices(row, chord.keysym)
            row.selected = chord.toString()
            applyChord(row, chord)
            rows.add(row)
        }
        updating = false

        // attach the change listeners now that the initial state is in place
        for (i in rows.indices) {
            val row = rows[i]
            val onChecked = CompoundButton.OnCheckedChangeListener { _, _ -> onChordChanged(i) }
            row.ctrl.setOnCheckedChangeListener(onChecked)
            row.alt.setOnCheckedChangeListener(onChecked)
            row.shift.setOnCheckedChangeListener(onChecked)
            row.key.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                    onChordChanged(i)

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    /**
     * Populates a row's key spinner with the curated [KEY_CHOICES] and, when [keysym] is an assigned
     * key outside that list (e.g. an exotic key set via managed config), a trailing entry labelled
     * with that key's own XK token -- so it stays visible and editable instead of collapsing to
     * "None". [Row.keysyms] mirrors the resulting adapter positions for read-back.
     */
    private fun bindKeyChoices(row: Row, keysym: Long) {
        val labels = ArrayList<String>(KEY_CHOICES.size + 1)
        val keysyms = ArrayList<Long>(KEY_CHOICES.size + 1)
        for (choice in KEY_CHOICES) {
            labels.add(context.getString(choice.labelRes))
            keysyms.add(choice.keysym)
        }
        if (keysym != 0L && KEY_CHOICES.none { it.keysym == keysym }) {
            labels.add(Keysyms.tokenFor[keysym] ?: "0x" + keysym.toString(16))
            keysyms.add(keysym)
        }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        row.key.adapter = adapter
        row.keysyms = keysyms
    }

    /** Sets a row's modifier checkboxes and key spinner from a parsed chord. Mute listeners first. */
    private fun applyChord(row: Row, chord: Chord) {
        row.ctrl.isChecked = chord.ctrl
        row.alt.isChecked = chord.alt
        row.shift.isChecked = chord.shift
        row.key.setSelection(row.keysyms.indexOf(chord.keysym).coerceAtLeast(0))
    }

    /** Reads a row's controls into a [Chord]. */
    private fun readChord(row: Row): Chord =
        Chord(row.ctrl.isChecked, row.alt.isChecked, row.shift.isChecked,
            row.keysyms[row.key.selectedItemPosition])

    /**
     * Handles a change on row [idx]: recompose the chord; if it duplicates another action's assigned
     * chord, toast and revert; otherwise persist it and live-update the running [InputService].
     */
    private fun onChordChanged(idx: Int) {
        if (updating) {
            return
        }
        val row = rows[idx]
        val chord = readChord(row)
        val value = chord.toString()
        if (value == row.selected) {
            return // no actual change (e.g. a re-layout or unchanged re-selection callback)
        }
        // mutual exclusion: an assigned chord may not equal another action's assigned chord
        if (chord.isAssigned) {
            for (j in rows.indices) {
                if (j != idx && value == rows[j].selected) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.main_activity_settings_chord_conflict, value),
                        Toast.LENGTH_SHORT
                    ).show()
                    updating = true
                    applyChord(row, Chord.fromString(row.selected)) // revert to last good
                    updating = false
                    return
                }
            }
        }
        row.selected = value
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(row.prefKey, value).apply()
        // live-update the running input service (a no-op when it is not connected)
        InputService.updateShortcuts(
            rows[0].selected, rows[1].selected, rows[2].selected, rows[3].selected,
            rows[4].selected, rows[5].selected, rows[6].selected
        )
    }

    private companion object {
        /** One entry in the curated trigger-key spinner: its keysym (0 = "None") and label. */
        private class KeyChoice(val keysym: Long, val labelRes: Int)

        /** Resolves an XK_ token to a [KeyChoice]; "" -> the "None" entry. */
        private fun keyChoice(token: String, labelRes: Int) =
            KeyChoice(if (token.isEmpty()) 0L else Keysyms.byToken.getValue(token.lowercase()), labelRes)

        /**
         * Curated trigger keys offered in the UI spinner (declaration order = spinner order). Tokens
         * are X11 XK_ names resolved against the generated [Keysyms] table; power users can bind any
         * other key via the Defaults / managed-config chord string.
         */
        private val KEY_CHOICES = listOf(
            keyChoice("", R.string.key_label_none),
            keyChoice("Home", R.string.key_label_home),
            keyChoice("End", R.string.key_label_end),
            keyChoice("Escape", R.string.key_label_esc),
            keyChoice("Delete", R.string.key_label_del),
            keyChoice("Insert", R.string.key_label_ins),
            keyChoice("BackSpace", R.string.key_label_backspace),
            keyChoice("Page_Up", R.string.key_label_pageup),
            keyChoice("Page_Down", R.string.key_label_pagedown),
            keyChoice("Left", R.string.key_label_left),
            keyChoice("Right", R.string.key_label_right),
            keyChoice("Up", R.string.key_label_up),
            keyChoice("Down", R.string.key_label_down),
            keyChoice("Tab", R.string.key_label_tab),
            keyChoice("Return", R.string.key_label_enter),
            keyChoice("F1", R.string.key_label_f1),
            keyChoice("F2", R.string.key_label_f2),
            keyChoice("F3", R.string.key_label_f3),
            keyChoice("F4", R.string.key_label_f4),
            keyChoice("F5", R.string.key_label_f5),
            keyChoice("F6", R.string.key_label_f6),
            keyChoice("F7", R.string.key_label_f7),
            keyChoice("F8", R.string.key_label_f8),
            keyChoice("F9", R.string.key_label_f9),
            keyChoice("F10", R.string.key_label_f10),
            keyChoice("F11", R.string.key_label_f11),
            keyChoice("F12", R.string.key_label_f12),
        )

        /** Metadata for one action row. Order MUST match InputKeyShortcutManager.from(). */
        private class Spec(
            val ctrlId: Int,
            val altId: Int,
            val shiftId: Int,
            val keyId: Int,
            val prefKey: String,
            val default: (Defaults) -> String,
        )

        private val SPECS = listOf(
            Spec(
                R.id.settings_chord_recents_ctrl, R.id.settings_chord_recents_alt,
                R.id.settings_chord_recents_shift, R.id.settings_chord_recents_key,
                Constants.PREFS_KEY_SETTINGS_CHORD_RECENTS, { it.chordRecents }
            ),
            Spec(
                R.id.settings_chord_home_ctrl, R.id.settings_chord_home_alt,
                R.id.settings_chord_home_shift, R.id.settings_chord_home_key,
                Constants.PREFS_KEY_SETTINGS_CHORD_HOME, { it.chordHome }
            ),
            Spec(
                R.id.settings_chord_back_ctrl, R.id.settings_chord_back_alt,
                R.id.settings_chord_back_shift, R.id.settings_chord_back_key,
                Constants.PREFS_KEY_SETTINGS_CHORD_BACK, { it.chordBack }
            ),
            Spec(
                R.id.settings_chord_power_ctrl, R.id.settings_chord_power_alt,
                R.id.settings_chord_power_shift, R.id.settings_chord_power_key,
                Constants.PREFS_KEY_SETTINGS_CHORD_POWER, { it.chordPower }
            ),
            Spec(
                R.id.settings_chord_volume_up_ctrl, R.id.settings_chord_volume_up_alt,
                R.id.settings_chord_volume_up_shift, R.id.settings_chord_volume_up_key,
                Constants.PREFS_KEY_SETTINGS_CHORD_VOLUME_UP, { it.chordVolumeUp }
            ),
            Spec(
                R.id.settings_chord_volume_down_ctrl, R.id.settings_chord_volume_down_alt,
                R.id.settings_chord_volume_down_shift, R.id.settings_chord_volume_down_key,
                Constants.PREFS_KEY_SETTINGS_CHORD_VOLUME_DOWN, { it.chordVolumeDown }
            ),
            Spec(
                R.id.settings_chord_rotate_ctrl, R.id.settings_chord_rotate_alt,
                R.id.settings_chord_rotate_shift, R.id.settings_chord_rotate_key,
                Constants.PREFS_KEY_SETTINGS_CHORD_ROTATE, { it.chordRotate }
            ),
        )
    }
}
