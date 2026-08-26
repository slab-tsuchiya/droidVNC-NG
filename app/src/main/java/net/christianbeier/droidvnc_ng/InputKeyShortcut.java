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

package net.christianbeier.droidvnc_ng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configurable VNC keyboard shortcuts (see issue #13).
 *
 * <p>The user assigns, per action ({@link Action}), one chord: any combination of the
 * {@code ctrl}/{@code alt}/{@code shift} modifiers plus a single trigger key. A chord is stored (in
 * prefs / managed config) as a lower-case {@code "+"}-joined string such as {@code "ctrl+alt+del"}
 * or {@code "esc"}; the token {@code "none"} (or an empty/unknown trigger) means "not assigned". The
 * trigger is written as a {@link Key} token when it is a named key, and otherwise as a raw
 * {@code "0x<hex>"} X11 keysym, so any keysym can be addressed from managed config without extending
 * the {@link Key} table. {@link #fromString(String)} turns a stored string into a {@link Chord} and
 * {@link Chord#toString()} turns it back.
 *
 * <p>This class performs no Android calls of its own -- the caller (InputService) executes the
 * returned {@link Action} -- so it stays free of any root/accessibility dependency and is trivially
 * portable. Modifier matching is <em>exact</em>: a binding matches only when its ctrl/alt/shift
 * flags equal the current modifier state, so a bare trigger key and its modified chord never collide.
 */
final class InputKeyShortcut {

    private InputKeyShortcut() {}

    /** Action a chord is assigned to. Execution lives in the caller. */
    enum Action {
        NONE, RECENTS, HOME, BACK, POWER_DIALOG, VOLUME_UP, VOLUME_DOWN, ROTATE
    }

    /**
     * The named trigger keys offered in the UI's per-action key spinner. Each carries a display
     * {@code label}, the lower-case {@code token} used in the persisted {@code "+"}-string, and the
     * RFB/X11 {@code keysym} the viewer sends. {@link #NONE} (keysym 0, which never matches) means
     * "action disabled". Declaration order is the spinner order. Keys outside this table can still be
     * used from managed config as a raw {@code "0x<hex>"} keysym.
     */
    enum Key {
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

        final String label;
        final String token;
        final long keysym;

        Key(String label, String token, long keysym) {
            this.label = label;
            this.token = token;
            this.keysym = keysym;
        }

        /** Resolves a trigger token (case-insensitive); unknown/"none" -> {@link #NONE}. */
        static Key fromToken(String token) {
            if (token != null) {
                String t = token.trim().toLowerCase(Locale.ROOT);
                for (Key k : values()) {
                    if (k.token.equals(t)) {
                        return k;
                    }
                }
            }
            return NONE;
        }

        /** The named key carrying {@code keysym}, or {@code null} if none does (a raw keysym). */
        static Key fromKeysym(long keysym) {
            for (Key k : values()) {
                if (k != NONE && k.keysym == keysym) {
                    return k;
                }
            }
            return null;
        }
    }

    /**
     * A parsed chord: the ctrl/alt/shift modifier flags plus one trigger keysym. A chord whose
     * keysym is {@code 0} is "not assigned" and contributes no binding.
     */
    static final class Chord {
        final boolean ctrl;
        final boolean alt;
        final boolean shift;
        final long keysym;

        Chord(boolean ctrl, boolean alt, boolean shift, long keysym) {
            this.ctrl = ctrl;
            this.alt = alt;
            this.shift = shift;
            this.keysym = keysym;
        }

        boolean isAssigned() {
            return keysym != 0;
        }

        /**
         * Canonical persisted string: {@code ctrl+alt+shift+<trigger>}, or {@code "none"} if
         * unassigned. The trigger is a {@link Key} token for a named key, otherwise a raw
         * {@code "0x<hex>"} keysym.
         */
        @Override
        public String toString() {
            if (keysym == 0) {
                return Key.NONE.token;
            }
            StringBuilder b = new StringBuilder();
            if (ctrl) b.append("ctrl+");
            if (alt) b.append("alt+");
            if (shift) b.append("shift+");
            Key named = Key.fromKeysym(keysym);
            b.append(named != null ? named.token : "0x" + Long.toHexString(keysym));
            return b.toString();
        }
    }

    /**
     * Parses a persisted chord string (case-insensitive). Tokens are separated by {@code "+"};
     * recognizes the {@code ctrl}/{@code alt}/{@code shift} modifier tokens in any order. The last
     * non-modifier token is the trigger: a {@link Key} token (e.g. {@code "esc"}) or a raw
     * {@code "0x<hex>"} X11 keysym. Unknown/empty/"none" input yields an unassigned chord.
     */
    static Chord fromString(String s) {
        boolean ctrl = false, alt = false, shift = false;
        long keysym = 0;
        if (s != null) {
            for (String part : s.split("\\+")) {
                String p = part.trim().toLowerCase(Locale.ROOT);
                if (p.isEmpty()) {
                    continue;
                }
                switch (p) {
                    case "ctrl":
                        ctrl = true;
                        break;
                    case "alt":
                        alt = true;
                        break;
                    case "shift":
                        shift = true;
                        break;
                    default:
                        keysym = parseTrigger(p);
                        break;
                }
            }
        }
        return new Chord(ctrl, alt, shift, keysym);
    }

    /**
     * Resolves a trigger token to a keysym: a named {@link Key} token, a raw {@code "0x<hex>"}
     * keysym, or {@code 0} (unassigned) for anything unrecognized.
     */
    private static long parseTrigger(String p) {
        if (p.startsWith("0x")) {
            try {
                return Long.parseLong(p.substring(2), 16);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return Key.fromToken(p).keysym;
    }

    /** A resolved chord: exact modifier state + trigger keysym -> action. */
    static final class Binding {
        final boolean ctrl;
        final boolean alt;
        final boolean shift;
        final long triggerKeysym;
        final Action action;

        Binding(Chord chord, Action action) {
            this.ctrl = chord.ctrl;
            this.alt = chord.alt;
            this.shift = chord.shift;
            this.triggerKeysym = chord.keysym;
            this.action = action;
        }
    }

    /**
     * Builds the active binding list from the per-action chord strings (each a persisted
     * {@code "+"}-string; unassigned/"none"/unknown-trigger contributes no binding).
     */
    static List<Binding> buildBindings(String recentsChord, String homeChord, String backChord,
            String powerDialogChord, String volumeUpChord, String volumeDownChord, String rotateChord) {
        List<Binding> bindings = new ArrayList<>(7);
        addBinding(bindings, recentsChord, Action.RECENTS);
        addBinding(bindings, homeChord, Action.HOME);
        addBinding(bindings, backChord, Action.BACK);
        addBinding(bindings, powerDialogChord, Action.POWER_DIALOG);
        addBinding(bindings, volumeUpChord, Action.VOLUME_UP);
        addBinding(bindings, volumeDownChord, Action.VOLUME_DOWN);
        addBinding(bindings, rotateChord, Action.ROTATE);
        return bindings;
    }

    private static void addBinding(List<Binding> bindings, String chordString, Action action) {
        Chord chord = fromString(chordString);
        if (chord.isAssigned()) {
            bindings.add(new Binding(chord, action));
        }
    }

    /**
     * Returns the action bound to the given (exact) modifier state and trigger keysym, or
     * {@link Action#NONE} if nothing matches.
     */
    static Action match(List<Binding> bindings, boolean ctrl, boolean alt, boolean shift, long keysym) {
        if (bindings != null) {
            for (Binding binding : bindings) {
                if (binding.ctrl == ctrl && binding.alt == alt && binding.shift == shift
                        && binding.triggerKeysym == keysym) {
                    return binding.action;
                }
            }
        }
        return Action.NONE;
    }
}
