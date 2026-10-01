# PROGRESS

Branch `native-app-v0`. CI is the only compiler in this workflow — nothing below was built
locally.

## M0 — toolchain bring-up: GREEN
Green run **36795807603**. Toolchain pinned in `android-app/gradle/libs.versions.toml`:
Gradle 8.13 / JDK 17 / AGP 8.13.1 / Kotlin 2.2.21 / Compose BOM 2025.12.00 (material3 1.4.0) /
compileSdk 36 / minSdk 26. Artifact `app-debug` confirmed to exist (11,270,321 B).
The user installed that APK on their phone: it opens, shows the title, does not crash.

## M1 — Shizuku client state machine + home screen: GREEN, VERIFIED ON THE PHONE
Green run **36806012324**, artifact `app-debug` 11,317,991 B.
**User-verified on device:** the state machine works, the permission dialog works, `READY` is
reached, and **Shizuku uid = 2000 (shell)**. No crash.

CI history:
- `36805705739` — **failure**, `:app:compileDebugKotlin`, one error:
  `ShizukuRuntime.kt:98:14 Unresolved reference 'addPackage'` (`Intent.setPackage` returns void
  and cannot be chained onto the constructor).
- `7dc8dd7` fixed it; run **`36806012324` — success**.
- `36806442225` — success (docs-only push).

Verification beyond the step conclusion (a green run is not proof):
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

M1 code:
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
- `res/values/strings.xml` (en) + `res/values-fa/strings.xml` (fa).
- `test/.../shizuku/ShizukuStateTest.kt` — 11 hermetic assertions over the pure logic.
- `build.gradle.kts` — `dev.rikka.shizuku:api` and `:provider` added.

Also fixed: the title was rendered twice (`R.string.app_name` in the `TopAppBar` plus
`R.string.hello`, same literal). The `hello` string and its body `Text` are gone.
The launcher icon was **not** touched.

## M2 — Shizuku UserService (AIDL) + exec bridge + console: CODE WRITTEN, AWAITING CI
M3 is not started.

CI:
- `36808003294` — **failure**, `:app:compileDebugAidl`, two errors:
  `Couldn't find import for class ExecResult` and `Failed to resolve 'ExecResult'`. The
  result now travels as a `Bundle` instead; see DECISIONS.md decision 21.
- `36808508335` — **failure**, `:app:compileDebugKotlin`, one error:
  `ShizukuExecService.kt:56:18 Unresolved reference 'toBundle'`. A `getOrElse` inference
  problem over a platform type; see DECISIONS.md decision 28.

Written:
- `src/main/aidl/.../exec/IUserService.aidl` — `exec(command, timeoutMs)` returning the result,
  plus `cancel()`. AIDL re-enabled with `buildFeatures { aidl = true }` (AGP 8 disables it).
- `exec/ExecResult.kt` — Parcelable result (`exitCode`, `stdout`, `stderr`, `truncated`) with a
  `@JvmField CREATOR`, plus `toBundle()`/`fromBundle()` for the binder boundary.
- `exec/ShizukuExecService.kt` — the UserService. `/system/bin/sh -c`, single-threaded executor
  so commands are serialised, stdout and stderr drained concurrently so a full pipe cannot
  deadlock the child, 64 KiB cap per stream setting `truncated`, stdin closed, timeout enforced by
  `waitFor` then `destroy()`, and `cancel()` destroying the running child.
- `exec/ExecBridge.kt` — client. `ConnectionState` DISCONNECTED/CONNECTING/CONNECTED, binds only
  while Shizuku is READY, drops the binder on Shizuku binder-dead and rebinds on the next READY.
- `exec/ShellQuoting.kt`, `exec/Redaction.kt`, `exec/OutputCollector.kt` — pure, no Android imports.
- `exec/ConsoleHistory.kt` — capped in-memory history, redacted at insert time.
- `ui/console/ConsoleScreen.kt` — command field, Run, Cancel, the two read-only self-test buttons
  (`id`, `getprop ro.build.version.sdk`), and the history list. The blocking AIDL call runs on
  `Dispatchers.IO`, never the main thread.
- `MainActivity.kt` — home shows "Open console" only when READY; back button returns home.
- Manifest: `.exec.ShizukuExecService`, `exported=true`.
- New tests: `ShellQuotingTest`, `RedactionTest`, `OutputCollectorTest`, `ConsoleHistoryTest`.

## Next
- Read the M2 CI result. Green is not proof — list `actions/runs/<id>/artifacts` and confirm
  `app-debug` exists.
- User installs the M2 APK and checks the console on the phone.
- M3 only after that.

## Known issues
- `lint` still runs with `abortOnError = false`, so a green run does not mean lint is clean.
- `Shizuku.requestPermission` is deprecated upstream in 13.1.5; it is wrapped in `runCatching`
  so it can never crash the app, but a future library bump may drop it.
- The M2 console is a raw shell. The redaction filter is a display guard, not a security
  boundary — see DECISIONS.md decision 26. A command that prints a secret in a form the filter
  does not recognise will still show it.

## UNVERIFIED — must be checked on the phone, not by CI
- **All M2 runtime behaviour.** Nothing in M2 has been executed: not one command has been run
  through the UserService, on any device.
- That `aidl` accepts the Kotlin `ExecResult` Parcelable as a return type without a
  `parcelable ExecResult;` declaration (DECISIONS.md decision 21).
- That Shizuku actually binds the service, i.e. that `exported="true"` on the service is correct
  and no `android:process` is needed.
- That the self-test `id` really reports uid 2000 in the UserService process.
- That the timeout, the 64 KiB truncation cap and `cancel()` behave as intended.
- The RTL title fix was a code-level change; the user has not yet confirmed the Persian title.
- `<queries>` package visibility was verified in the merged manifest only, not on a device.

## UNVERIFIED (command-level — to be validated on-device later)
- `am get-standby-bucket` / `am set-standby-bucket <pkg> <bucket>` (M4).
- `dumpsys deviceidle whitelist [+|-]<pkg>` (M4).
- `dumpsys deviceidle force-idle` / `unforce` (M4).
- `pm install-multiple -r <apks>` permission on Android 13+ shell uid (M5).
- `restorecon -R /data/data/<pkg>` availability in Shizuku shell/root context (M5).