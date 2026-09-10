# PULSE // BATTERY

**Battery & Wakelock Doctor, with an integrated Backup Vault** — a Shevery
ADB module. Diagnoses what's draining your battery, gives you manual
control over app-standby buckets and the Doze whitelist (the same
mechanism apps like Naptime and Greenify manage), and backs up app data —
including a real, no-root path for debug-signed apps.

## Features

**Diagnostics**
- 🔋 Live battery gauge — level, charging status, temperature, read from `dumpsys battery`.
- ⚡ Live wakelock snapshot — what's holding a wakelock right now, mapped to package names.
- ⏰ Alarm activity — best-effort read of `dumpsys alarm` wakeup lines.
- 📄 Raw per-package battery report — `dumpsys batterystats <pkg>`, shown unfiltered. Full
  battery-stats parsing is genuinely unreliable across Android versions and OEMs, so this
  tab intentionally shows real output instead of a confident table that might be wrong.

**Standby & Doze control**
- 🪣 App standby bucket viewer/setter — `am get/set-standby-bucket`, the same testing API
  Android's own docs describe for controlling how freely an app runs in the background.
- 🌙 Doze whitelist manager — add/remove apps from battery-optimization exemption in bulk.
- 🧪 Force-idle / unforce — push the device into Doze on demand to test whether an app survives it.

**Backup Vault**
- 📦 APK export (base + splits) for any app, no root needed.
- 💾 Full app-data backup for **any app when rooted**, or for **debug-signed apps without
  root** via `run-as` — see below, this is a real distinction the module makes automatically.
- 🔁 Restore — reinstall APKs and restore data from a saved vault.
- 🗂 Vault browser with delete.
- 🌗 Light/dark theme toggle, remembered per device.

## The run-as discovery

A common question: is full app-data backup really impossible without root? **Not always.**
Apps built with a debug key (`android:debuggable="true"`, which is the default for a debug
build) can have their private data read by the `shell`/ADB user via `run-as <pkg>` — this is
a real, documented Android mechanism, not a workaround. This module detects debuggable apps
automatically during the backup scan and offers full data backup for them even with no root
at all. Release-signed apps still need root for data backup — that restriction is a genuine
Android sandboxing boundary, not a limitation of this module.

## Requirements

- [Shevery](https://github.com/HmnDev-Tech/shevery), access mode **Full** (or **Custom**
  with "WebUI shell bridge" enabled).
- No root needed for diagnostics, standby/Doze control, APK backup, and debuggable-app data backup.
- Root needed only for data backup of **release-signed** apps.

## Install

**From a release ZIP:** ADB Modules → Import → select the ZIP. `module.prop` sits at the
ZIP's root — if packaging from source, `cd` into the folder before zipping:

```bash
git clone https://github.com/kreza6173-pixel/pulse-battery.git
cd pulse-battery
zip -r ../pulse-battery.zip . -x ".git/*"
```

## Architecture

```
pulse-battery/
├── module.prop      # Module manifest (usesShellBridge=true)
├── lib.sh            # Shared shell helpers
├── action.sh          # Read-only summary shown on the module's Action button
├── webui/
│   ├── index.html        # Vitals / Standby & Doze / Backup / Restore tabs
│   ├── style.css           # PULSE design system — light, clinical-instrument aesthetic
│   └── script.js             # window.Shizuku.exec() shell bridge + all UI logic
├── LICENSE
└── README.md
```

## How it works

| Feature | Shell mechanism |
|---|---|
| Battery vitals | `dumpsys battery` — level, status, temperature |
| Live wakelocks | `dumpsys power`, mapped to packages via `pm list packages -U` |
| Alarm activity | `dumpsys alarm`, grepped for wakeup lines (best-effort) |
| Raw battery report | `dumpsys batterystats <pkg>` |
| Standby bucket | `am get-standby-bucket` / `am set-standby-bucket` |
| Doze whitelist | `dumpsys deviceidle whitelist` (read), `whitelist +pkg` / `-pkg` (write) |
| Force Doze | `dumpsys deviceidle force-idle` / `unforce` |
| Debuggable detection | `dumpsys package <pkg>`, checked for `DEBUGGABLE` in the flags line |
| APK export | `pm path <pkg>` → `cp` each split |
| Data backup (root) | `tar czf <dest>/data.tar.gz -C /data/data <pkg>` |
| Data backup (run-as) | `run-as <pkg> sh -c "cd /data/data && tar czf - <pkg>"` |
| APK restore | `pm install-multiple -r <apks…>` |
| Data restore | `tar xzf … -C /data/data` (root) or piped through `run-as … tar xzf -` (debuggable) |

## Known limitations

- **Wakelock and alarm views are heuristic.** `dumpsys power`/`dumpsys alarm` output format
  varies across Android versions and OEMs. When parsing comes up empty, the raw dump is
  always in the console drawer.
- **The raw battery report is intentionally raw.** Full `dumpsys batterystats` structure is
  complex enough, and changes often enough across versions, that a confident-looking parsed
  table would risk being wrong. You get the real data instead.
- **Data restore works best when the app version matches** the version it was backed up
  from — true for both the root and run-as paths, same caveat any root-level data restore
  tool (Titanium Backup and similar) carries.
- **run-as backup/restore depends on real device behavior** this project's test sandbox
  can't simulate (no Android runtime available while building it). The command choices are
  drawn from well-documented, widely-used `run-as` techniques, but if you hit an edge case,
  the console drawer shows exactly what ran and what it returned.

## License

MIT — see [LICENSE](LICENSE).
