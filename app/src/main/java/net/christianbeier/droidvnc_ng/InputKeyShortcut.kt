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
 *  - [Key]: a named trigger key (label / persisted token / RFB-X11 keysym).
 *  - [Chord]: ctrl/alt/shift modifier flags plus one trigger keysym; serializes to/from a
 *    lower-case "+"-joined string such as "ctrl+alt+del" (a [Key] token) or "ctrl+alt+0xffff"
 *    (a raw keysym for keys not in the table). "none" / empty / unknown means "not assigned".
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
 * A named trigger key offered in the UI's per-action key spinner: a display [label], the lower-case
 * [token] used in the persisted "+"-string, and the RFB/X11 [keysym] the viewer sends. [NONE]
 * (keysym 0, which never matches) means "action disabled". Declaration order is the spinner order.
 * Keys outside this table can still be used from managed config as a raw "0x<hex>" keysym.
 */
internal enum class Key(val label: String, val token: String, val keysym: Long) {
    NONE("None", "none", 0),
    HOME("Home", "home", 0xFF50),
    END("End", "end", 0xFF57),
    ESC("Esc", "esc", 0xFF1B),
    DEL("Del", "del", 0xFFFF),
    INS("Ins", "ins", 0xFF63),
    BACKSPACE("Backspace", "backspace", 0xFF08),
    PAGEUP("PageUp", "pageup", 0xFF55),
    PAGEDOWN("PageDown", "pagedown", 0xFF56),
    LEFT("Left", "left", 0xFF51),
    RIGHT("Right", "right", 0xFF53),
    UP("Up", "up", 0xFF52),
    DOWN("Down", "down", 0xFF54),
    TAB("Tab", "tab", 0xFF09),
    ENTER("Enter", "enter", 0xFF0D),
    F1("F1", "f1", 0xFFBE),
    F2("F2", "f2", 0xFFBF),
    F3("F3", "f3", 0xFFC0),
    F4("F4", "f4", 0xFFC1),
    F5("F5", "f5", 0xFFC2),
    F6("F6", "f6", 0xFFC3),
    F7("F7", "f7", 0xFFC4),
    F8("F8", "f8", 0xFFC5),
    F9("F9", "f9", 0xFFC6),
    F10("F10", "f10", 0xFFC7),
    F11("F11", "f11", 0xFFC8),
    F12("F12", "f12", 0xFFC9);

    companion object {
        /** Resolves a trigger token (case-insensitive); unknown/"none" -> [NONE]. */
        @JvmStatic
        fun fromToken(token: String?): Key {
            val t = token?.trim()?.lowercase() ?: return NONE
            return entries.firstOrNull { it.token == t } ?: NONE
        }

        /** The named key carrying [keysym], or null if none does (a raw keysym). */
        @JvmStatic
        fun fromKeysym(keysym: Long): Key? =
            entries.firstOrNull { it != NONE && it.keysym == keysym }
    }
}

/**
 * A parsed chord: the ctrl/alt/shift modifier flags plus one trigger [keysym]. A chord whose keysym
 * is 0 is "not assigned" and contributes no binding.
 */
internal data class Chord(val ctrl: Boolean, val alt: Boolean, val shift: Boolean, val keysym: Long) {

    val isAssigned: Boolean get() = keysym != 0L

    /**
     * Canonical persisted string: `ctrl+alt+shift+<trigger>`, or `"none"` if unassigned. The trigger
     * is a [Key] token for a named key, otherwise a raw `"0x<hex>"` keysym.
     */
    override fun toString(): String {
        if (keysym == 0L) return Key.NONE.token
        val b = StringBuilder()
        if (ctrl) b.append("ctrl+")
        if (alt) b.append("alt+")
        if (shift) b.append("shift+")
        b.append(Key.fromKeysym(keysym)?.token ?: "0x" + keysym.toString(16))
        return b.toString()
    }

    companion object {
        /**
         * Parses a persisted chord string (case-insensitive). Tokens are separated by `"+"`;
         * recognizes the ctrl/alt/shift modifier tokens in any order. The last non-modifier token is
         * the trigger: a [Key] token (e.g. `"esc"`) or a raw `"0x<hex>"` X11 keysym. Unknown / empty /
         * "none" input yields an unassigned chord.
         */
        @JvmStatic
        fun fromString(s: String?): Chord {
            var ctrl = false
            var alt = false
            var shift = false
            var keysym = 0L
            if (s != null) {
                for (part in s.split('+')) {
                    when (val p = part.trim().lowercase()) {
                        "" -> { /* skip empty tokens */ }
                        "ctrl" -> ctrl = true
                        "alt" -> alt = true
                        "shift" -> shift = true
                        else -> keysym = parseTrigger(p)
                    }
                }
            }
            return Chord(ctrl, alt, shift, keysym)
        }

        /**
         * Resolves a trigger token to a keysym: a named [Key] token, a raw `"0x<hex>"` keysym, or 0
         * (unassigned) for anything unrecognized.
         */
        private fun parseTrigger(p: String): Long {
            if (p.startsWith("0x")) {
                return p.substring(2).toLongOrNull(16) ?: 0L
            }
            return Key.fromToken(p).keysym
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
            if (c.ctrl == ctrl && c.alt == alt && c.shift == shift && c.keysym == keysym) {
                return binding.action
            }
        }
        return Action.NONE
    }

    companion object {
        /**
         * Builds a manager from the per-action chord strings (each a persisted "+"-string;
         * unassigned/"none"/unknown-trigger contributes no binding).
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
