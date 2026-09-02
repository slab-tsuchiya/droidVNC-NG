# Preseed Preferences

DroidVNC-NG can be supplied with defaults for preferences that apply if preferences
were not changed by the user.

## Via JSON File
A file named `defaults.json` needs to created under
`<external files directory>/Android/data/net.christianbeier.droidvnc_ng/files/` where
depending on your device, `<external files directory>` is something like `/storage/emulated/0` if
the device shows two external storages or simply `/sdcard` if the device has one external storage.

An example `defaults.json` with completely new defaults (not all entries need to be provided) is:

```json
{
    "port": 5901,
    "portReverse": 5555,
    "portRepeater": 5556,
    "scaling": 0.7,
    "viewOnly": false,
    "showPointers": true,
    "fileTransfer": true,
    "password": "supersecure",
    "accessKey": "evenmoresecure",
    "startOnBoot": true,
    "startOnBootDelay": 0,
    "chordRecents": "Control_L+Shift_L+Escape",
    "chordHome": "Home",
    "chordBack": "Escape",
    "chordPower": "End",
    "chordVolumeUp": "Control_L+Alt_L+Page_Up",
    "chordVolumeDown": "Control_L+Alt_L+Page_Down",
    "chordRotate": "Control_L+Alt_L+Delete"
}
```

### Keyboard shortcut chords
The `chord*` entries assign a VNC-side key combination to each Android action. Each value is a
`+`-joined string of an optional set of modifiers plus one trigger key, all named by their X11
`XK_` tokens with the `XK_` prefix stripped and parsed case-insensitively:

- Modifiers: `Control_L`, `Alt_L`, `Shift_L` (either the left- or right-hand key matches).
- Trigger key: any keysym name from libvncserver's `rfb/keysym.h`, e.g. `Home`, `End`, `Escape`,
  `Delete`, `Insert`, `BackSpace`, `Page_Up`, `Page_Down`, `Left`, `Right`, `Up`, `Down`, `Tab`,
  `Return`, `F1`..`F12`.
- An empty string disables the action.

Examples: `"Control_L+Alt_L+Delete"`, `"Escape"`, `""`. The values shown above are the defaults.

## Via Managed App Restrictions
If you are using a device owner app, you can also preseed the preferences via [managed app restrictions](https://developer.android.com/work/managed-configurations). The same keys as in the JSON file above can be used.

**NOTE**: Updates to app restrictions are only applied when the service restarts.
