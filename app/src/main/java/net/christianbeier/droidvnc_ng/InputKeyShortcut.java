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

/**
 * Configurable VNC keyboard shortcuts (see issue #13).
 *
 * <p>The user assigns, per action ({@link ShortcutAction}), one {@link ChordKey} from a fixed
 * candidate list (or {@link ChordKey#NONE}). This class turns those assignments into
 * {@link ChordBinding}s and matches an incoming (modifier state, trigger keysym) against them. It
 * performs no Android calls of its own -- the caller (InputService) executes the returned action.
 *
 * <p>Modifier matching is <em>exact</em>: a binding matches only when its ctrl/alt/shift flags equal
 * the current modifier state, so a bare trigger key and its modified chord never collide.
 *
 * <p>RFB/X11 keysyms used as triggers: Home = 0xFF50, Left = 0xFF51, Esc = 0xFF1B, Delete = 0xFFFF,
 * End = 0xFF57, PageUp = 0xFF55, PageDown = 0xFF56, Backspace = 0xFF08.
 */
final class InputKeyShortcut {

    private InputKeyShortcut() {}

    /** Action a chord is assigned to. Execution lives in the caller. */
    enum ShortcutAction {
        NONE, RECENTS, HOME, BACK, POWER_DIALOG, VOLUME_UP, VOLUME_DOWN, ROTATE
    }

    /**
     * The fixed candidate chords offered in the UI. {@link #NONE} means "not assigned". The
     * declaration order is also the order shown in the spinners. Each carries its exact modifier
     * state and the trigger keysym (0 for NONE, which never matches).
     */
    enum ChordKey {
        // Constructor args, in order: (persisted key, ctrl, alt, shift, trigger keysym).
        // The persisted key uses the same "+"-joined notation as the UI labels.
        NONE("none", false, false, false, 0),
        HOME("home", false, false, false, 0xFF50),
        ESC("esc", false, false, false, 0xFF1B),
        END("end", false, false, false, 0xFF57),
        CTRL_ESC("ctrl+esc", true, false, false, 0xFF1B),
        CTRL_SHIFT_ESC("ctrl+shift+esc", true, false, true, 0xFF1B),
        CTRL_ALT_DEL("ctrl+alt+del", true, true, false, 0xFFFF),
        CTRL_ALT_HOME("ctrl+alt+home", true, true, false, 0xFF50),
        CTRL_ALT_END("ctrl+alt+end", true, true, false, 0xFF57),
        CTRL_ALT_BACKSPACE("ctrl+alt+backspace", true, true, false, 0xFF08),
        CTRL_ALT_PAGEUP("ctrl+alt+pageup", true, true, false, 0xFF55),
        CTRL_ALT_PAGEDOWN("ctrl+alt+pagedown", true, true, false, 0xFF56),
        ALT_HOME("alt+home", false, true, false, 0xFF50),
        ALT_LEFT("alt+left", false, true, false, 0xFF51);

        final String key;
        final boolean ctrl;
        final boolean alt;
        final boolean shift;
        final long triggerKeysym;

        ChordKey(String key, boolean ctrl, boolean alt, boolean shift, long triggerKeysym) {
            this.key = key;
            this.ctrl = ctrl;
            this.alt = alt;
            this.shift = shift;
            this.triggerKeysym = triggerKeysym;
        }

        /** Resolves a persisted chord key (case-insensitive); falls back to {@link #NONE}. */
        static ChordKey fromKey(String key) {
            if (key != null) {
                for (ChordKey c : values()) {
                    if (c.key.equalsIgnoreCase(key)) {
                        return c;
                    }
                }
            }
            return NONE;
        }
    }

    /** A resolved chord: exact modifier state + trigger keysym -> action. */
    static final class ChordBinding {
        final boolean ctrl;
        final boolean alt;
        final boolean shift;
        final long triggerKeysym;
        final ShortcutAction action;

        ChordBinding(ChordKey chord, ShortcutAction action) {
            this.ctrl = chord.ctrl;
            this.alt = chord.alt;
            this.shift = chord.shift;
            this.triggerKeysym = chord.triggerKeysym;
            this.action = action;
        }
    }

    /**
     * Builds the active binding list from the per-action chord assignments (each a {@link ChordKey}
     * persisted key; unknown/"none" contributes no binding).
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

    private static void addBinding(List<ChordBinding> bindings, String chordKey, ShortcutAction action) {
        ChordKey chord = ChordKey.fromKey(chordKey);
        if (chord != ChordKey.NONE) {
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
