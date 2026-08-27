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
 *  - [Key]: a named trigger key -- the persisted [token] plus the RFB/X11 [keysym] the viewer sends.
 *    Its human-readable label is a localized string resource resolved by the settings view, so this
 *    model carries no display text and stays Android-free.
 *  - [Chord]: ctrl/alt/shift modifier flags plus one trigger [Key]; serializes to/from a lower-case
 *    "+"-joined string such as "ctrl+alt+del" or "esc". Empty / unknown input means "not assigned".
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
    NONE, RECENTS, HOME, BACK, POWER_DIALOG, VOLUME_UP, VOLUME_DOWN, ROTATE
}

/**
 * A named trigger key offered in the UI's per-action key spinner: the lower-case [token] used in the
 * persisted "+"-string and the RFB/X11 [keysym] the viewer sends. [NONE] (empty token, keysym 0 --
 * which never matches) means "action disabled". Declaration order is the spinner order. The
 * human-readable, localized label lives in the settings view (a string resource), not here.
 */
internal enum class Key(val token: String, val keysym: Long) {
    NONE("", 0),
    HOME("home", 0xFF50),
    END("end", 0xFF57),
    ESC("esc", 0xFF1B),
    DEL("del", 0xFFFF),
    INS("ins", 0xFF63),
    BACKSPACE("backspace", 0xFF08),
    PAGEUP("pageup", 0xFF55),
    PAGEDOWN("pagedown", 0xFF56),
    LEFT("left", 0xFF51),
    RIGHT("right", 0xFF53),
    UP("up", 0xFF52),
    DOWN("down", 0xFF54),
    TAB("tab", 0xFF09),
    ENTER("enter", 0xFF0D),
    F1("f1", 0xFFBE),
    F2("f2", 0xFFBF),
    F3("f3", 0xFFC0),
    F4("f4", 0xFFC1),
    F5("f5", 0xFFC2),
    F6("f6", 0xFFC3),
    F7("f7", 0xFFC4),
    F8("f8", 0xFFC5),
    F9("f9", 0xFFC6),
    F10("f10", 0xFFC7),
    F11("f11", 0xFFC8),
    F12("f12", 0xFFC9);

    companion object {
        /** Resolves a trigger token (case-insensitive); empty / unknown -> [NONE]. */
        fun fromToken(token: String?): Key {
            val t = token?.trim()?.lowercase() ?: return NONE
            return entries.firstOrNull { it != NONE && it.token == t } ?: NONE
        }
    }
}

/**
 * A parsed chord: the ctrl/alt/shift modifier flags plus one trigger [Key]. A chord whose key is
 * [Key.NONE] is "not assigned" and contributes no binding.
 */
internal data class Chord(val ctrl: Boolean, val alt: Boolean, val shift: Boolean, val key: Key) {

    val isAssigned: Boolean get() = key != Key.NONE

    /** Canonical persisted string: `ctrl+alt+shift+<token>`, or "" when unassigned. */
    override fun toString(): String {
        if (key == Key.NONE) return ""
        val b = StringBuilder()
        if (ctrl) b.append("ctrl+")
        if (alt) b.append("alt+")
        if (shift) b.append("shift+")
        b.append(key.token)
        return b.toString()
    }

    companion object {
        /**
         * Parses a persisted chord string (case-insensitive). Tokens are separated by "+"; recognizes
         * the ctrl/alt/shift modifier tokens in any order, and the last other token is the trigger
         * [Key]. Empty / unknown / unassigned input yields an unassigned chord.
         */
        fun fromString(s: String?): Chord {
            var ctrl = false
            var alt = false
            var shift = false
            var key = Key.NONE
            if (s != null) {
                for (part in s.split('+')) {
                    when (val p = part.trim().lowercase()) {
                        "" -> { /* skip empty tokens */ }
                        "ctrl" -> ctrl = true
                        "alt" -> alt = true
                        "shift" -> shift = true
                        else -> key = Key.fromToken(p)
                    }
                }
            }
            return Chord(ctrl, alt, shift, key)
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
     * Returns the action bound to the given (exact) modifier state and trigger keysym, or
     * [Action.NONE] if nothing matches.
     */
    fun actionFor(ctrl: Boolean, alt: Boolean, shift: Boolean, keysym: Long): Action {
        for (binding in bindings) {
            val c = binding.chord
            if (c.ctrl == ctrl && c.alt == alt && c.shift == shift && c.key.keysym == keysym) {
                return binding.action
            }
        }
        return Action.NONE
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
