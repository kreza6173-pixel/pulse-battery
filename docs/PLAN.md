# PULSE // BATTERY — Native App Plan

## Architecture

```
/android-app/                  <- self-contained Gradle project (repo-root legacy files untouched)
├── settings.gradle.kts
├── build.gradle.kts           <- root: plugin aliases only
├── gradle.properties
├── gradle/libs.versions.toml  <- pinned versions (no +, no SNAPSHOT)
├── gradle/wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts        <- :app module (Compose, Material3)
│   ├── proguard-rules.pro
│   ├── src/main/AndroidManifest.xml
│   │   ├── MainActivity  (exported, single top)
│   │   ├── ShizukuProvider (added M1)
│   │   └── <queries> for Shizuku manager (added M1)
│   ├── src/main/java/.../MainActivity.kt
│   ├── src/main/java/.../ui/theme/Theme.kt
│   ├── src/main/java/.../data/            <- Shizuku backend (M2)
│   ├── src/main/aidl/  .../PulseUserService.aidl  (M2)
│   ├── src/main/res/  (icons M-final, fa strings M-final)
│   └── src/test/               <- JVM unit tests for parsers
├── .github/workflows/ci.yml
└── fastlane/metadata/android/en-US/...   (Final)
```

### Layers
1. **UI** — Jetpack Compose + Material3 (dynamic color, dark default, RTL). Single-activity, Navigation.
2. **Shizuku client layer** (`data/ShizukuClient`) — state machine:
   `NOT_INSTALLED -> NOT_RUNNING -> PERMISSION_NEEDED -> READY`; reacts to binder-received /
   binder-dead, pings binder, `checkSelfPermission`, `shouldShowRequestPermissionRationale`,
   `requestPermission`. Detects uid 0 (root) vs 2000 (shell) and enables features accordingly.
3. **Shizuku UserService (AIDL)** — runs as root/shell uid; exposes `exec(command, timeoutMs)`.
   Serialized execution, timeout, output-size cap, cancellation, binder-death recovery.
   This is the ONLY path that runs privileged commands (never `Shizuku.newProcess`).
4. **Repository layer** — each feature (`dumpsys`, `pm`, `am`, `tar`, `run-as`) is one or more
   `UserService.exec(...)` calls + a parser. Parsers are pure functions, unit-tested with sample
   fixtures (injected, not hardcoded, so tests are hermetic).

### Tooling (verified versions)
- AGP 9.4.1, Gradle 9.6.0 (AGP 9.4 min), JDK 17 (temurin), Kotlin 2.2.10.
- Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle-runtime-ktx 2.11.0, core-splashscreen 1.2.0.
- Shizuku library 13.1.5 (api + provider).
- compileSdk 36 (latest stable Android SDK as of env date 2026-09; API 37 SDK not yet stable-published),
  targetSdk 36, minSdk 26.

## CI design (works without gradle-wrapper.jar)
- `actions/setup-java@v5` temurin 17.
- `gradle/actions/setup-gradle@v6` with `gradle-version: '9.6.0'`, `cache-provider: basic` (open-source,
  no proprietary caching lib).
- `sdkmanager` installs `platforms;android-36` + `build-tasks;36.0.0` (idempotent).
- Run `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- On failure: write failing task + last 40 log lines to `$GITHUB_STEP_SUMMARY`; upload `build.log` artifact.
- On success: upload `app/build/outputs/apk/debug/*.apk`.

## Milestones
- **M0** empty Compose app + CI green (this step).
- **M1** Shizuku state machine + home screen (provider, queries, state machine UI).
- **M2** UserService AIDL + `exec` + console screen.
- **M3** Diagnostics (battery gauge, wakelocks, alarms, raw batterystats).
- **M4** Standby & Doze control (with command verification gating).
- **M5** Backup Vault (APK export, data backup/restore root + run-as, vault browser).
- **Final** adaptive icon + monochrome, Persian (fa) strings, Open-source licenses screen,
  fastlane metadata, docs (RELEASING, COMMAND_VERIFICATION, PROGRESS).

## Risks / open questions
- `am get-standby-bucket` / `am set-standby-bucket` and `dumpsys deviceidle whitelist`/`force-idle`
  exact syntax are UNVERIFIED — gated by runtime probe + marked UNVERIFIED until verified on-device.
- `pm install-multiple -r` permission on Android 13+ shell uid — UNVERIFIED.
- `restorecon` availability in Shizuku shell context — UNVERIFIED.
- Cannot run builds locally (phone-only workflow); rely on CI + on-device console for verification.
- gradle-wrapper.jar intentionally not committed; CI uses `gradle` from setup-gradle `gradle-version`.
