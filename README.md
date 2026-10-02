<div align="center">

# PULSE // BATTERY

**Find out what drained your battery overnight, and fix it in one tap.**
No root. Runs through [Shizuku](https://shizuku.rikka.app/).

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Shizuku](https://img.shields.io/badge/needs-Shizuku-8E7CC3)
![No internet](https://img.shields.io/badge/internet-none-success)
[![Latest release](https://img.shields.io/github/v/release/kreza6173-pixel/pulse-battery)](https://github.com/kreza6173-pixel/pulse-battery/releases/latest)

</div>

---

## Why

Your phone loses 15% overnight and the system battery screen just says "Android System".
PULSE asks Android directly which apps woke the device, ranks them, and lets you put the
worst one to sleep. Every change is read back from the system and can be reverted in one tap.

## Features

**Overnight drain report** (the headline)
- Tap *Start new period* before bed. In the morning: apps ranked by alarm wakeups, with a per-hour rate.
- One-tap **Restrict** moves an offender to the Restricted standby bucket. One-tap **Revert**.
- Android keeps the counters itself, so PULSE does not run in the background. Detects reboots.

**Diagnostics**
- Live battery state: level, temperature, voltage, health, power source.
- Active wake locks, attributed to the real package (including `*job*` tags).
- Top alarms table from `dumpsys alarm`.

**Standby & Doze**
- See and change the standby bucket of every app, with raw bucket codes shown.
- Add or remove apps from the user Doze whitelist.
- Force deep Doze on demand to test whether an app survives it.

**Vault (APK backup)**
- Export base + split APKs of any app to `Download/PulseVault`. Survives uninstalling PULSE.
- Restore with one tap.

**Console**
- Run any shell command as the Shizuku user, with 12 read-only quick commands, cancel, copy and share.

## Requirements

- Android 8.0 or newer.
- [Shizuku](https://shizuku.rikka.app/) running (wireless debugging, ADB or root).
- That is it. No root, no internet permission, no account, no ads, no trackers.

## Install

Download the APK from [Releases](https://github.com/kreza6173-pixel/pulse-battery/releases/latest).
On first launch PULSE asks Shizuku for permission. If Shizuku says allowed but PULSE still
asks, tap **Restart app** (some Shizuku builds apply a new grant only to a fresh process).

Release signing certificate SHA-256:

```
3f8c1e2df2deaf9d561a8fdce3c667ed0ceda972622f20d006a1b6098c2a2512
```

## FAQ

**Is Restrict safe?** It uses the same standby-bucket API Android itself uses. Restricted apps
still work when you open them; their background jobs and alarms just run rarely. Messaging
apps may deliver notifications late, so do not restrict the ones you need instantly.
Revert is one tap, or change the bucket anytime in *Standby & Doze*.

**Why alarm wakeups and not mAh?** Per-app mAh estimates are unreliable across vendors and
Android versions. Wakeups are counted by the system, are exact, and are the usual cause of
overnight drain.

**Some apps say "Protected by the system".** Android marks core and some vendor apps as
exempted. Their bucket cannot be changed without root, so PULSE does not pretend it can.

**Does PULSE drain battery itself?** No. Nothing runs while the app is closed. The report
screen refreshes once a minute only while it is open.

**Root features?** Not in 1.0. Anything that needs root stays locked until it can be tested on
a real rooted device.

## How it works

| Feature | Command |
|---|---|
| Drain report | `dumpsys alarm` (Alarm Stats per-package totals) |
| Battery | `dumpsys battery` |
| Wake locks | `dumpsys power` |
| Standby bucket | `am get-standby-bucket` / `am set-standby-bucket` |
| Doze whitelist | `dumpsys deviceidle whitelist +pkg` / `-pkg` |
| Force Doze | `dumpsys deviceidle force-idle` / `unforce` |
| APK export | `pm path` then `cp` |
| Restore | staged in `/data/local/tmp`, then `pm install -r` |

All parsers are pure Kotlin, unit-tested against real device output. Package names are
validated and shell-quoted before any command runs.

## Build

CI is the build system: every push builds, tests, lints and uploads `app-debug`.
With signing secrets present it also builds a signed `app-release`. See [docs/RELEASE.md](docs/RELEASE.md).

Toolchain: AGP 8.13.1, Kotlin 2.2.21, Gradle 8.13, Compose BOM 2025.12.00, compile/target SDK 36,
Shizuku API 13.1.5.

## Support

If PULSE saved your battery, a star helps other people find it.
Contributions and bug reports are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT, see [LICENSE](LICENSE). Uses Shizuku-API (MIT), AndroidX, Jetpack Compose and Kotlin
(Apache-2.0).
