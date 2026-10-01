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

Single source of truth: `android-app/gradle/libs.versions.toml`, whose values come from one
consistent upstream — the `android/compose-samples` tag `v2025.12.00`. Provenance for each
version is in `docs/DECISIONS.md` decision 0; do not bump these without updating that file.

| Item | Version | Where |
|---|---|---|
| Gradle | **8.13** | `gradle/wrapper/gradle-wrapper.properties` → `gradle-8.13-bin.zip`; `gradle-version: '8.13'` in `ci.yml` |
| JDK | **17** (temurin) | `ci.yml` → `actions/setup-java@v5` |
| AGP | **8.13.1** | `libs.versions.toml` `agp` |
| Kotlin | **2.2.21** | `libs.versions.toml` `kotlin` |
| Compose BOM | **2025.12.00** | resolves `androidx.compose.material3:material3` to **1.4.0** |
| activity-compose | 1.12.1 | `libs.versions.toml` |
| lifecycle-runtime-ktx | 2.10.0 | `libs.versions.toml` |
| core-splashscreen | 1.2.0 | declared in the catalog but **not resolved** by any dependency |
| Shizuku `api` + `provider` | **13.1.5** | verified against Maven Central metadata *and* the published AARs; resolved and compiled in M1 |
| junit | 4.13.2 | JVM unit tests only; no instrumented tests |

- `compileSdk 36`, `targetSdk 36`, `minSdk 26` (`app/build.gradle.kts`).
- **Never SDK 37 and never AGP 9.** Both were tried and are dead ends here: `platforms;android-37`
  is not installable from the stable SDK channel (CI run `36888889668` failed with
  `Warning: Failed to find package 'platforms;android-37'`), and AGP 9 made the standalone
  `org.jetbrains.kotlin.android` plugin unnecessary, which this module relies on.
- **Compose API must match the resolved BOM, not the newest-looking code.** In material3 1.4.0
  `SmallTopAppBar` does not exist (CI run `36793279060`); use `TopAppBar` with
  `@OptIn(ExperimentalMaterial3Api::class)`. `Theme.Material3.*` XML styles and
  `attr/colorPrimary` do not resolve either — this module has no `com.google.android.material`
  dependency, so the XML theme is `android:Theme.Material.Light.NoActionBar` and dark mode is
  handled in Compose (CI run `36792965261`).
- AGP 8 disables AIDL generation by default, so `buildFeatures { aidl = true }` is required for
  the UserService interface.

## CI design (works without gradle-wrapper.jar)

Triggered on `push`, `pull_request` and `workflow_dispatch`. `permissions: contents: read`.
Job-level `defaults.run.working-directory: android-app`.

- `actions/setup-java@v5`, distribution `temurin`, `java-version: 17`.
- `gradle/actions/setup-gradle@v6` with `gradle-version: '8.13'` and `cache-provider: basic`
  (open-source, no proprietary caching library). CI invokes `gradle` directly, so
  `gradle-wrapper.jar` does not need to be committed.
- `sdkmanager --licenses` then install `platforms;android-36`, `build-tools;36.0.0`,
  `platform-tools`. **This step is fatal if a package is unavailable** — it is what killed the
  first run, before Gradle was ever invoked.
- Build: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
  --console=plain --stacktrace`, teed to `${RUNNER_TEMP}/build.log`, with the real exit code
  captured via `PIPESTATUS` and re-raised so a failure cannot be swallowed by the pipe.
- Artifacts: `app-debug` and `build-log`.
- On failure: last 40 lines of `build.log` into `$GITHUB_STEP_SUMMARY`.

**APK path — the one thing that silently breaks this workflow.**
The APK is written to `android-app/app/build/outputs/apk/debug/app-debug.apk`, and
`actions/upload-artifact` resolves its `path` **from the repository root**, not from
`defaults.run.working-directory`, because `uses:` steps ignore that setting. The step therefore
uses `path: android-app/**/build/outputs/apk/debug/app-debug.apk` with
`if-no-files-found: error`.

This matters because `if-no-files-found` defaults to `warn`, and `warn` does not turn a run red:
CI run `36794363757` reported **success** while uploading no APK at all
(`##[warning]No files were found with the provided path`), and only `build-log` existed in its
artifact list. **A green run is not proof.** After every run, confirm the artifact:

```
gh api repos/<owner>/<repo>/actions/runs/<id>/artifacts
```

`app-debug` must be present before a milestone counts as done. See `docs/LESSONS.md` §2.3.

**Verification status of these claims:** the toolchain above is proven by green runs
(`36795807603`, `36806012324`) plus artifact inspection. Two caveats that CI does *not* cover:
`lint` runs with `abortOnError = false`, so green does not mean lint-clean; and `./gradlew` is
never exercised in CI, so the wrapper path remains unverified.

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
