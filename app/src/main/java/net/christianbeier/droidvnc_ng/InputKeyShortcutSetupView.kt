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
    }

    private val keys = Key.entries
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
        val keyLabels = keys.map { context.getString(labelResFor(it)) }.toTypedArray()

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
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, keyLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            row.key.adapter = adapter

            val chord = Chord.fromString(prefs.getString(spec.prefKey, spec.default(defaults)))
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

    /** Sets a row's modifier checkboxes and key spinner from a parsed chord. Mute listeners first. */
    private fun applyChord(row: Row, chord: Chord) {
        row.ctrl.isChecked = chord.ctrl
        row.alt.isChecked = chord.alt
        row.shift.isChecked = chord.shift
        row.key.setSelection(keys.indexOf(chord.key).coerceAtLeast(0))
    }

    /** Reads a row's controls into a [Chord]. */
    private fun readChord(row: Row): Chord =
        Chord(row.ctrl.isChecked, row.alt.isChecked, row.shift.isChecked, keys[row.key.selectedItemPosition])

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

    /** The localized spinner label for a trigger key. */
    private fun labelResFor(key: Key): Int = when (key) {
        Key.NONE -> R.string.key_label_none
        Key.HOME -> R.string.key_label_home
        Key.END -> R.string.key_label_end
        Key.ESC -> R.string.key_label_esc
        Key.DEL -> R.string.key_label_del
        Key.INS -> R.string.key_label_ins
        Key.BACKSPACE -> R.string.key_label_backspace
        Key.PAGEUP -> R.string.key_label_pageup
        Key.PAGEDOWN -> R.string.key_label_pagedown
        Key.LEFT -> R.string.key_label_left
        Key.RIGHT -> R.string.key_label_right
        Key.UP -> R.string.key_label_up
        Key.DOWN -> R.string.key_label_down
        Key.TAB -> R.string.key_label_tab
        Key.ENTER -> R.string.key_label_enter
        Key.F1 -> R.string.key_label_f1
        Key.F2 -> R.string.key_label_f2
        Key.F3 -> R.string.key_label_f3
        Key.F4 -> R.string.key_label_f4
        Key.F5 -> R.string.key_label_f5
        Key.F6 -> R.string.key_label_f6
        Key.F7 -> R.string.key_label_f7
        Key.F8 -> R.string.key_label_f8
        Key.F9 -> R.string.key_label_f9
        Key.F10 -> R.string.key_label_f10
        Key.F11 -> R.string.key_label_f11
        Key.F12 -> R.string.key_label_f12
    }

    private companion object {
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
