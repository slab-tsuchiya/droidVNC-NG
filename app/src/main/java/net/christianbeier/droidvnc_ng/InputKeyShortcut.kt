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

/*
 * Configurable VNC keyboard shortcuts (see issue #13).
 *
 * The model is split into small value types plus a manager:
 *  - [Action]: the device action a chord is assigned to (execution lives in the caller).
 *  - [Chord]: ctrl/alt/shift modifier flags plus the RFB/X11 [keysym] of one trigger key.
 *    Serializes to/from a "+"-joined string such as "Control_L+Alt_L+Delete" or "Escape". Every
 *    token -- both the modifiers and the trigger -- is an established X11 XK_ name with the "XK_"
 *    prefix stripped (see [Keysyms], generated from libvncserver's rfb/keysym.h); parsing is
 *    case-insensitive. The modifiers are named after their left-hand keys (Control_L/Alt_L/Shift_L)
 *    but match either side. Empty input or an unknown trigger key means "not assigned".
 *  - [Binding]: a [Chord] bound to an [Action].
 *  - [InputKeyShortcutManager]: holds the active bindings and resolves an incoming
 *    (modifier state, trigger keysym) to its [Action]. Built from the per-action chord strings.
 *
 * None of this touches Android APIs -- the caller (InputService) executes the returned [Action] --
 * so it stays free of any root/accessibility dependency and is trivially portable/testable.
 * Modifier matching is exact: a binding matches only when its ctrl/alt/shift flags equal the current
 * modifier state, so a bare trigger key and its modified chord never collide.
 */

/** Action a chord is assigned to. Execution lives in the caller (InputService). */
internal enum class Action {
    RECENTS, HOME, BACK, POWER_DIALOG, VOLUME_UP, VOLUME_DOWN, ROTATE
}

/**
 * A parsed chord: the ctrl/alt/shift modifier flags plus the RFB/X11 [keysym] of one trigger key
 * ([keysym] 0 -- which never matches -- means "not assigned").
 */
internal data class Chord(val ctrl: Boolean, val alt: Boolean, val shift: Boolean, val keysym: Long) {

    val isAssigned: Boolean get() = keysym != 0L

    /** Canonical persisted string: `Control_L+Alt_L+Shift_L+<XK token>`, or "" when unassigned. */
    override fun toString(): String {
        val token = Keysyms.tokenFor[keysym] ?: return ""
        val b = StringBuilder()
        if (ctrl) b.append("Control_L+")
        if (alt) b.append("Alt_L+")
        if (shift) b.append("Shift_L+")
        b.append(token)
        return b.toString()
    }

    companion object {
        /**
         * Parses a persisted chord string (case-insensitive). Tokens are separated by "+"; the
         * modifier tokens (the XK_ names Control_L/Control_R, Alt_L/Alt_R, Shift_L/Shift_R, prefix
         * stripped -- either side accepted) may appear in any order, and the last other token is the
         * trigger key (also an XK_ name with the prefix stripped -- see [Keysyms]). Empty / unknown
         * trigger input yields an unassigned chord.
         */
        fun fromString(s: String?): Chord {
            var ctrl = false
            var alt = false
            var shift = false
            var keysym = 0L
            if (s != null) {
                for (part in s.split('+')) {
                    when (val p = part.trim().lowercase()) {
                        "" -> { /* skip empty tokens from a leading/trailing/double "+" */ }
                        "control_l", "control_r" -> ctrl = true
                        "alt_l", "alt_r" -> alt = true
                        "shift_l", "shift_r" -> shift = true
                        else -> keysym = Keysyms.byToken[p] ?: 0L
                    }
                }
            }
            return Chord(ctrl, alt, shift, keysym)
        }
    }
}

/** A resolved binding: a [Chord] bound to an [Action]. */
internal data class Binding(val chord: Chord, val action: Action)

/**
 * Holds the active chord bindings and resolves an incoming (modifier state, trigger keysym) to the
 * bound [Action]. Build one from the per-action chord strings with [from]; it carries no Android
 * state, so InputService can keep a single instance and swap it when the settings change.
 */
internal class InputKeyShortcutManager(private val bindings: List<Binding>) {

    /**
     * Returns the action bound to the given (exact) modifier state and trigger keysym, or null if
     * nothing matches.
     */
    fun actionFor(ctrl: Boolean, alt: Boolean, shift: Boolean, keysym: Long): Action? {
        for (binding in bindings) {
            val c = binding.chord
            if (c.ctrl == ctrl && c.alt == alt && c.shift == shift && c.keysym == keysym) {
                return binding.action
            }
        }
        return null
    }

    companion object {
        /**
         * Builds a manager from the per-action chord strings (each a persisted "+"-string;
         * unassigned / empty / unknown-trigger contributes no binding).
         */
        @JvmStatic
        fun from(
            recents: String?, home: String?, back: String?, powerDialog: String?,
            volumeUp: String?, volumeDown: String?, rotate: String?
        ): InputKeyShortcutManager {
            val bindings = ArrayList<Binding>(7)
            add(bindings, recents, Action.RECENTS)
            add(bindings, home, Action.HOME)
            add(bindings, back, Action.BACK)
            add(bindings, powerDialog, Action.POWER_DIALOG)
            add(bindings, volumeUp, Action.VOLUME_UP)
            add(bindings, volumeDown, Action.VOLUME_DOWN)
            add(bindings, rotate, Action.ROTATE)
            return InputKeyShortcutManager(bindings)
        }

        private fun add(bindings: MutableList<Binding>, chordString: String?, action: Action) {
            val chord = Chord.fromString(chordString)
            if (chord.isAssigned) {
                bindings.add(Binding(chord, action))
            }
        }
    }
}
