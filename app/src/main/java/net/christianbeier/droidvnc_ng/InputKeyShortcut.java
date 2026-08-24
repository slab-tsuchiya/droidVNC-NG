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
 * <p>The user assigns, per action ({@link ShortcutAction}), one chord: any combination of the
 * {@code ctrl}/{@code alt}/{@code shift} modifiers plus a single {@link TriggerKey}. A chord is
 * stored (in prefs / managed config) as a lower-case {@code "+"}-joined string such as
 * {@code "ctrl+alt+del"} or {@code "esc"}; the token {@code "none"} (or an empty/unknown trigger)
 * means "not assigned". {@link #parse(String)} turns a stored string into a {@link Chord}, and
 * {@link Chord#toKey()} turns it back, so no closed enum of pre-composed chords has to be extended
 * whenever a new modifier/key combination is wanted.
 *
 * <p>This class performs no Android calls of its own -- the caller (InputService) executes the
 * returned {@link ShortcutAction} -- so it stays free of any root/accessibility dependency and is
 * trivially portable. Modifier matching is <em>exact</em>: a binding matches only when its
 * ctrl/alt/shift flags equal the current modifier state, so a bare trigger key and its modified
 * chord never collide.
 */
final class InputKeyShortcut {

    private InputKeyShortcut() {}

    /** Action a chord is assigned to. Execution lives in the caller. */
    enum ShortcutAction {
        NONE, RECENTS, HOME, BACK, POWER_DIALOG, VOLUME_UP, VOLUME_DOWN, ROTATE
    }

    /**
     * The trigger keys offered in the UI's per-action key spinner. Each carries a display label, the
     * lower-case token used in the persisted {@code "+"}-string, and the RFB/X11 keysym the viewer
     * sends. {@link #NONE} (keysym 0, which never matches) means "action disabled". Declaration order
     * is the spinner order.
     */
    enum TriggerKey {
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

        TriggerKey(String label, String token, long keysym) {
            this.label = label;
            this.token = token;
            this.keysym = keysym;
        }

        /** Resolves a trigger token (case-insensitive); unknown/"none" -> {@link #NONE}. */
        static TriggerKey fromToken(String token) {
            if (token != null) {
                String t = token.trim().toLowerCase(Locale.ROOT);
                for (TriggerKey k : values()) {
                    if (k.token.equals(t)) {
                        return k;
                    }
                }
            }
            return NONE;
        }
    }

    /**
     * A parsed chord: the ctrl/alt/shift modifier flags plus one {@link TriggerKey}. A chord whose
     * trigger is {@link TriggerKey#NONE} is "not assigned" and contributes no binding.
     */
    static final class Chord {
        final boolean ctrl;
        final boolean alt;
        final boolean shift;
        final TriggerKey key;

        Chord(boolean ctrl, boolean alt, boolean shift, TriggerKey key) {
            this.ctrl = ctrl;
            this.alt = alt;
            this.shift = shift;
            this.key = key;
        }

        boolean isAssigned() {
            return key != TriggerKey.NONE;
        }

        /** Canonical persisted string: {@code ctrl+alt+shift+<key>}, or {@code "none"} if unassigned. */
        String toKey() {
            if (key == TriggerKey.NONE) {
                return TriggerKey.NONE.token;
            }
            StringBuilder b = new StringBuilder();
            if (ctrl) b.append("ctrl+");
            if (alt) b.append("alt+");
            if (shift) b.append("shift+");
            b.append(key.token);
            return b.toString();
        }
    }

    /**
     * Parses a persisted chord string (case-insensitive). Tokens are separated by {@code "+"} (the
     * canonical form) or {@code "_"} -- the latter for backward compatibility with the older
     * underscore-style keys ({@code "ctrl_shift_esc"} etc.), which migrate cleanly. Recognizes the
     * {@code ctrl}/{@code alt}/{@code shift} modifier tokens in any order; the last non-modifier token
     * is taken as the trigger key. Unknown/empty/"none" input yields an unassigned chord.
     */
    static Chord parse(String s) {
        boolean ctrl = false, alt = false, shift = false;
        TriggerKey key = TriggerKey.NONE;
        if (s != null) {
            for (String part : s.split("[+_]")) {
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
                        key = TriggerKey.fromToken(p);
                        break;
                }
            }
        }
        return new Chord(ctrl, alt, shift, key);
    }

    /** A resolved chord: exact modifier state + trigger keysym -> action. */
    static final class ChordBinding {
        final boolean ctrl;
        final boolean alt;
        final boolean shift;
        final long triggerKeysym;
        final ShortcutAction action;

        ChordBinding(Chord chord, ShortcutAction action) {
            this.ctrl = chord.ctrl;
            this.alt = chord.alt;
            this.shift = chord.shift;
            this.triggerKeysym = chord.key.keysym;
            this.action = action;
        }
    }

    /**
     * Builds the active binding list from the per-action chord strings (each a persisted
     * {@code "+"}-string; unassigned/"none"/unknown-trigger contributes no binding).
     */
    static List<ChordBinding> buildBindings(String recentsChord, String homeChord, String backChord,
            String powerDialogChord, String volumeUpChord, String volumeDownChord, String rotateChord) {
        List<ChordBinding> bindings = new ArrayList<>(7);
        addBinding(bindings, recentsChord, ShortcutAction.RECENTS);
        addBinding(bindings, homeChord, ShortcutAction.HOME);
        addBinding(bindings, backChord, ShortcutAction.BACK);
        addBinding(bindings, powerDialogChord, ShortcutAction.POWER_DIALOG);
        addBinding(bindings, volumeUpChord, ShortcutAction.VOLUME_UP);
        addBinding(bindings, volumeDownChord, ShortcutAction.VOLUME_DOWN);
        addBinding(bindings, rotateChord, ShortcutAction.ROTATE);
        return bindings;
    }

    private static void addBinding(List<ChordBinding> bindings, String chordString, ShortcutAction action) {
        Chord chord = parse(chordString);
        if (chord.isAssigned()) {
            bindings.add(new ChordBinding(chord, action));
        }
    }

    /**
     * Returns the action bound to the given (exact) modifier state and trigger keysym, or
     * {@link ShortcutAction#NONE} if nothing matches.
     */
    static ShortcutAction match(List<ChordBinding> bindings, boolean ctrl, boolean alt, boolean shift, long keysym) {
        if (bindings != null) {
            for (ChordBinding binding : bindings) {
                if (binding.ctrl == ctrl && binding.alt == alt && binding.shift == shift
                        && binding.triggerKeysym == keysym) {
                    return binding.action;
                }
            }
        }
        return ShortcutAction.NONE;
    }
}
