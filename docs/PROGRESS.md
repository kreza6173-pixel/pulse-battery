# PROGRESS

Branch `native-app-v0`. CI is the only compiler in this workflow — nothing below was built
locally.

## M0 — toolchain bring-up: GREEN
Green run **36795807603**. Toolchain pinned in `android-app/gradle/libs.versions.toml`:
Gradle 8.13 / JDK 17 / AGP 8.13.1 / Kotlin 2.2.21 / Compose BOM 2025.12.00 (material3 1.4.0) /
compileSdk 36 / minSdk 26. Artifact `app-debug` confirmed to exist (11,270,321 B).
The user installed that APK on their phone: it opens, shows the title, does not crash.

## M1 — Shizuku client state machine + home screen: CI GREEN, awaiting phone verification
Not started: anything else. M2 (UserService / exec) is explicitly out of scope for M1.

CI:
- `36805705739` — **failure**, `:app:compileDebugKotlin`, one error:
  `ShizukuRuntime.kt:98:14 Unresolved reference 'addPackage'` (`Intent.setPackage` returns void
  and cannot be chained onto the constructor).
- `7dc8dd7` fixed it; run **`36806012324` — success**.

Green is not proof, so the artifact was checked, not just the conclusion:
- `actions/runs/36806012324/artifacts` → `app-debug` 11,317,991 B and `build-log` 1,346 B.
- Downloaded APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`, 11,793,554 B on disk.
- Inside the APK's merged `AndroidManifest.xml`: `rikka.shizuku.ShizukuProvider`, authority
  `io.github.kreza6173pixel.pulsebattery.shizuku`, `moe.shizuku.manager.permission.API_V23`,
  `queries`, `moe.shizuku.privileged.api`, `com.hamondev.shevery`, `moe.shizuku.client.V3_SUPPORT`.
- Inside the dex files: `rikka/shizuku/Shizuku`, `ShizukuProvider`,
  `Shizuku$OnRequestPermissionResultListener`, `shizuku/ShizukuRuntime`, `ui/home/HomeScreenKt`.
- Build log shows `:app:compileDebugUnitTestKotlin` then `:app:testDebugUnitTest` then
  `BUILD SUCCESSFUL`, with no `w:` warnings. The uploaded artifact is the console log only, so
  per-test names are not independently confirmed — the task passing is what is confirmed.

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
- User installs `app-debug` from run `36806012324` and walks the four states (see the on-device
  checklist in the M1 report).
- M2 only after the phone check: Shizuku `UserService` + `exec` bridge. Nothing in M1 runs a
  command.

## Known issues
- `lint` still runs with `abortOnError = false`, so a green run does not mean lint is clean.
- `Shizuku.requestPermission` is deprecated upstream in 13.1.5; it is wrapped in `runCatching`
  so it can never crash the app, but a future library bump may drop it.

## UNVERIFIED — must be checked on the phone, not by CI
- **All runtime behaviour.** CI proves it compiles, packages and unit-tests the pure logic. It
  proves nothing about what the app does on a real device with Shizuku installed.
- That the RTL title fix actually stops the clipping.
- That each of the four states is reachable in practice, and that `getUid()` reports 0 (root) or
  2000 (shell) as expected.
- That the "Get Shizuku" / "Open Shizuku" buttons actually open something, and that
  `FLAG_ACTIVITY_NEW_TASK` from the application context behaves as intended.
- `<queries>` package visibility was verified in the merged manifest only, not on a device.

## UNVERIFIED (command-level — to be validated on-device later)
- `am get-standby-bucket` / `am set-standby-bucket <pkg> <bucket>` (M4).
- `dumpsys deviceidle whitelist [+|-]<pkg>` (M4).
- `dumpsys deviceidle force-idle` / `unforce` (M4).
- `pm install-multiple -r <apks>` permission on Android 13+ shell uid (M5).
- `restorecon -R /data/data/<pkg>` availability in Shizuku shell/root context (M5).