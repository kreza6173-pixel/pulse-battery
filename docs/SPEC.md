# PULSE // BATTERY — Native App Spec

Authoritative source: this repo's `README.md` (legacy Shizuku ADB module) + `webui/script.js`.
The legacy app is a WebView + `window.Shizuku.exec()` shell bridge. The new app is a native
Jetpack Compose + Shizuku **UserService (AIDL)** client. Architecture is NOT ported; only
behavior is referenced.

Project name (from README/module.prop): **PULSE // BATTERY**
Project slug: `pulse-battery`
Package ID: `io.github.kreza6173pixel.pulsebattery` (contains no "shizuku"/"shevery")
Display name: `PULSE // BATTERY`
License: MIT (repo existing LICENSE).

## Feature list (from README)

### Diagnostics
- **Battery gauge** — live level, charging status, temperature, read from `dumpsys battery`.
- **Wakelock snapshot** — live wakelocks mapped to package names (read
  `dumpsys power` + `pm list packages -U` uid map).
- **Alarm activity** — best-effort `dumpsys alarm` wakeup lines.
- **Raw per-package battery report** — `dumpsys batterystats <pkg>`, shown raw.

### Standby & Doze control
- **App standby bucket** viewer/setter (`am get-standby-bucket` / `am set-standby-bucket`).
- **Doze whitelist** manager (dumpsys deviceidle whitelist, `+pkg`/`-pkg`).
- **Force-idle / unforce** (`dumpsys deviceidle force-idle` / `unforce`).

### Backup Vault
- **APK export** (base + splits) via `pm path <pkg>` then `cp` each split. No root needed.
- **App-data backup**
  - **root** (uid 0): `tar czf <dest>/data.tar.gz -C /data/data <pkg>`
  - **debuggable, no root** (uid 2000 shell): `run-as <pkg> sh -c "cd /data/data && tar czf - <pkg>"`
    piped to a shell-accessible path (`/sdcard/pulse-vault/...`).
- **Restore** — `pm install-multiple -r` for APKs; `tar xzf … -C /data/data` (root)
  or piped through `run-as` (debuggable) for data.
- **Vault browser** with delete; light/dark theme toggle (per-device).

## Native redesign (behavior → native)

All privileged I/O happens in a **Shizuku UserService** process that runs as the Shizuku backend
(uid 0 = root, uid 2000 = shell). The UI process talks to it over AIDL (`exec(command, timeoutMs)`).

| Legacy (shell bridge) | Native design |
|---|---|
| `window.Shizuku.exec(cmd)` → JSON | `UserService.Stub.exec(cmd, timeoutMs)` → ParcelableResult(exitCode, stdout, stderr, truncated) |
| `run-as <pkg> sh -c "cd /data/data && tar czf - <pkg>" > /sdcard/...` | UserService runs `run-as <pkg> sh -c "..."` and redirects stdout to a shell-accessible vault path `/sdcard/pulse-vault/<ts>/<pkg>/data.tar.gz`. The shell uid cannot read this app's private dir, so the vault lives on `/sdcard`. |
| `cp <apk path>` from `pm path` | same command, executed in UserService (shell uid) — `/data/app/*` APKs are world-readable, so `cp` works. |
| theme toggle in localStorage | DataStore Preference (dark/light/system), persisted. |
| vault = `/sdcard/pulse-vault` | unchanged (shell-accessible). |

## What is genuinely possible natively (verified by command existence)
- `dumpsys battery` / `dumpsys power` / `dumpsys alarm` / `dumpsys batterystats` / `dumpsys deviceidle` — `dumpsys` is present in every Shizuku shell/root context.
- `pm list packages -U`, `pm path <pkg>`, `pm install-multiple -r` — `pm` present in shell & root.
- `am get-standby-bucket` / `am set-standby-bucket` — `am` present; exact flag syntax **UNVERIFIED**, see docs/COMMAND_VERIFICATION.md.
- `tar` — present in Android shell (toybox). `run-as` — present in shell context.
- `dumpsys deviceidle whitelist [+|-]<pkg>`, `force-idle`, `unforce` — **UNVERIFIED exact syntax** (see COMMAND_VERIFICATION.md).

## What is NOT possible without root (honest limits)
- Data backup of **release-signed** apps requires root (uid 0). This is a real Android sandbox boundary; the module cannot bypass it.
- `run-as` only works for **debuggable** apps and only when Shizuku runs as shell (uid 2000). When Shizuku runs as **root**, the app already does `tar` directly (no run-as needed).
- `pm install-multiple -r` (restore) on Android 11+ (API 30+) requires `MANAGE_EXTERNAL_STORAGE`? No — it requires shell/root. Via Shizuku (shell uid) it works; on modern Android the shell uid holds `INSTALL_PACKAGES`? Actually ADB/shell is granted `android.permission.INSTALL_PACKAGES` historically; on Android 13+ the shell permissions were reduced — **UNVERIFIED**, see docs/COMMAND_VERIFICATION.md.

## AI assistant
The README describes NO AI assistant. The generic "optional AI" instruction from the task is therefore not applicable; there is no AI feature to gate. (If a future README section adds one, it must be OFF-by-default, opt-in, endpoint+key in Android Keystore.)

## Safety requirements (from task)
- Save original values before mutating; every change reversible (incl. after crash/reboot); "Restore defaults" action.
- Confirm destructive actions.
- Protected package list: this app, the Shizuku manager, system UI, launcher, phone/dialer, settings, etc. — never modifiable.
- Built-in console: every command + raw output visible; secrets redacted.
- Persist state via DataStore/Room; reapply on binder-received + after boot (BOOT_COMPLETED) with visible status.
- Gate by Build.VERSION.SDK_INT + runtime probe (`<cmd> help`); never crash; show "not supported" when unavailable.
- Quote/escape args with one tested helper; unit-test parsers with sample fixtures.
