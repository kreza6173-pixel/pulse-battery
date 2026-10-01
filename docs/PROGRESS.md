# PROGRESS

Branch `native-app-v0`. CI is the only compiler in this workflow — nothing below was built
locally.

## M0 — toolchain bring-up: GREEN
Green run **36795807603**. Toolchain pinned in `android-app/gradle/libs.versions.toml`:
Gradle 8.13 / JDK 17 / AGP 8.13.1 / Kotlin 2.2.21 / Compose BOM 2025.12.00 (material3 1.4.0) /
compileSdk 36 / minSdk 26. Artifact `app-debug` confirmed to exist (11,270,321 B).
The user installed that APK on their phone: it opens, shows the title, does not crash.

## M1 — Shizuku client state machine + home screen: CODE WRITTEN, AWAITING CI
Not started: anything else. M2 (UserService / exec) is explicitly out of scope for M1.

Written:
- `shizuku/ShizukuState.kt` — pure, no Android imports. `ShizukuState`
  (`NOT_INSTALLED -> NOT_RUNNING -> PERMISSION_NEEDED -> READY`), `ShizukuSignals`,
  `resolveShizukuState()`, `classifyUid()` (uid 0 = root, 2000 = shell).
- `shizuku/ShizukuRuntime.kt` — the only Android-facing code. Registers
  binder-received / binder-dead / permission-result listeners; reads `pingBinder()`,
  `checkSelfPermission()`, `shouldShowRequestPermissionRationale()`, `getUid()`; detects the
  manager package; requests permission; launches the manager / store / F-Droid.
- `ui/home/HomeScreen.kt` — one card + one action per state, plus the uid line and a Re-check
  button. All padding is `start`/`end`.
- `MainActivity.kt` — wires it up; listeners registered/torn down in a `DisposableEffect`;
  `onResume` re-checks; title fixed (`maxLines = 1`, ellipsis, `fillMaxWidth`).
- `AndroidManifest.xml` — `rikka.shizuku.ShizukuProvider` (authority `${applicationId}.shizuku`,
  `exported=true`, `multiprocess=false`), `<queries>` for `moe.shizuku.privileged.api` and
  `com.hamondev.shevery`, and the `API_V23` permission.
- `res/values/strings.xml` (en) + new `res/values-fa/strings.xml` (fa).
- `test/.../shizuku/ShizukuStateTest.kt` — 11 hermetic assertions over the pure logic.
- `build.gradle.kts` — `dev.rikka.shizuku:api` and `:provider` added.

Also fixed: the title was rendered twice (`R.string.app_name` in the `TopAppBar` plus
`R.string.hello`, same literal). The `hello` string and its body `Text` are gone.
The launcher icon was **not** touched.

## Next
- Read the M1 CI result. Green is not proof — list
  `actions/runs/<id>/artifacts` and confirm `app-debug` exists before calling it done.
- User installs the new APK and walks the four states on the phone, including a Persian
  (RTL) pass over the title bar.
- M2 only after M1 is verified on device: Shizuku `UserService` + `exec` bridge.

## Known issues
- `lint` still runs with `abortOnError = false`, so a green run does not mean lint is clean.
- `Shizuku.requestPermission` is deprecated upstream in 13.1.5; it is wrapped in `runCatching`
  so it can never crash the app, but a future library bump may drop it.

## UNVERIFIED — must be checked on the phone, not by CI
- Everything in M1 until a CI run is green.
- That `dev.rikka.shizuku:api:13.1.5` and `:provider:13.1.5` resolve in a Gradle build.
  The artifacts exist and the API surface was read directly from the AARs
  (docs/DECISIONS.md decision 16), but no build has resolved them yet.
- That the RTL title fix actually stops the clipping.
- That each of the four states is reachable and that `getUid()` reports 0 or 2000 as expected.

## UNVERIFIED (command-level — to be validated on-device later)
- `am get-standby-bucket` / `am set-standby-bucket <pkg> <bucket>` (M4).
- `dumpsys deviceidle whitelist [+|-]<pkg>` (M4).
- `dumpsys deviceidle force-idle` / `unforce` (M4).
- `pm install-multiple -r <apks>` permission on Android 13+ shell uid (M5).
- `restorecon -R /data/data/<pkg>` availability in Shizuku shell/root context (M5).