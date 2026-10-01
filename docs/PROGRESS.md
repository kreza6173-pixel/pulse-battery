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

## M2 — Shizuku UserService (AIDL) + exec bridge + console: CI GREEN, **BLOCKED ON DEVICE**

### Phone result for run 36811639208
- PASS: READY, uid 2000 (shell), **Open console** button shown.
- PASS: console title not clipped in Persian.
- **FAIL: the console header stays `User service: disconnected`.** Run, Cancel and both quick
  buttons stay disabled, so `onServiceConnected` never fired and no command could run.
  Steps 3-9 of the checklist are untestable until it connects.
- Known minor issue: the second quick button's label (`getprop ro.build.version.sdk`) is clipped.

**Cause not yet known.** The bind path is at `exec/ExecBridge.kt:96` (`connect()`) and `:116`
(`Shizuku.bindUserService`), args at `:106-108`, triggered on becoming READY from
`MainActivity.kt:76-78`. The API usage was verified against `api-13.1.5.aar` and is correct, so
this is behavioural, not a wrong call. Diagnosis was impossible because `connect()` swallowed the
exception with `catch (_: Exception) { false }`, making every failure mode look identical — and it
reset `CONNECTING` inside one frame so the intermediate state was never even visible.

### This change: diagnostics only, no blind fix
`ExecBridge` now keeps a capped, timestamped `bindLog` recording: the component, the args, whether
`bindUserService` returned or threw (exception class, message, cause), the result of
`Shizuku.peekUserService`, and both `ServiceConnection` callbacks. The console renders the last 12
lines so it can be read without logcat. Strings added in English and Persian. See DECISIONS.md
decision 33.

Pending: read the bind log on the phone, then fix the actual cause in a later push. The clipped
quick-button label is also queued.
M3 is not started.

**Green run: `36811639208`.** Artifact `app-debug` 11,369,112 B — confirmed present, not assumed.

CI history (five red runs, each exposing a further layer, then green):
- `36808003294` — `:app:compileDebugAidl`. `Failed to resolve 'ExecResult'`: the aidl tool gets no
  Java classpath. Result moved to a `Bundle`; decision 21.
- `36808508335` — `:app:compileDebugKotlin`. `Unresolved reference 'toBundle'`; decision 28.
- `36808950548` — `:app:compileDebugKotlin`. `expected 'ExecResult', actual 'Any!'`; decision 28.
- `36809228035` — `:app:compileDebugAidl` OK, then **five** errors in `:app:compileDebugKotlin`;
  decisions 29 and 30.
- `36810306371` — `:app:compileDebugKotlin`. `actual type is 'CharArray', but 'CharSequence' was
  expected`: `copyOfRange` returns a `CharArray`, which is not a `CharSequence`. Fixed with
  `String(chunk, 0, n)`.

Verification beyond the green conclusion:
- `actions/runs/36811639208/artifacts` → `app-debug` 11,369,112 B and `build-log` 1,352 B.
- Downloaded APK at `android-app/app/build/outputs/apk/debug/app-debug.apk`.
- Merged manifest inside the APK contains `rikka.shizuku.ShizukuProvider`, the
  `${applicationId}.shizuku` authority, `moe.shizuku.manager.permission.API_V23`, `queries`,
  `moe.shizuku.privileged.api`, `com.hamondev.shevery`, **and** the new
  `.exec.ShizukuExecService`.
- Dex files contain `rikka/shizuku/Shizuku`, `ShizukuProvider`, `IUserService`,
  `ShizukuExecService`, `ConsoleScreenKt`, `HomeScreenKt`, `ExecBridge`, `ShellQuoting`,
  `Redaction`, `OutputCollector`, `ConsoleHistory`.
- Build log shows `:app:compileDebugUnitTestKotlin`, then `:app:testDebugUnitTest`, then
  `BUILD SUCCESSFUL`, with no `w:` or `e:` lines. The unit tests now compile **and** run for the
  first time: 57 assertions across `ShellQuokingTest`, `RedactionTest`, `OutputCollectorTest` and
  `ConsoleHistoryTest`. The uploaded artifact is the console log, so individual test names were
  not read — only that the task passed.

AIDL (`:app:compileDebugAidl`) and both Kotlin compilation steps are now proven. **No runtime
behaviour is proven at all.**

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
- Read the M2 CI result for the bind-log build, confirm `app-debug`, and have the user report what
  the **Bind log** on the console says. Then fix the real cause.
- The clipped second quick-button label (`getprop ro.build.version.sdk`) is a known follow-up.
- M3 is **not** started, per instruction.

## Known issues
- `lint` still runs with `abortOnError = false`, so a green run does not mean lint is clean.
- `Shizuku.requestPermission` is deprecated upstream in 13.1.5; it is wrapped in `runCatching`
  so it can never crash the app, but a future library bump may drop it.
- The M2 console is a raw shell. The redaction filter is a display guard, not a security
  boundary — see DECISIONS.md decision 26. A command that prints a secret in a form the filter
  does not recognise will still show it.

## UNVERIFIED — must be checked on the phone, not by CI
- **All M2 runtime behaviour.** Nothing in M2 has ever been executed. No command has run through
  the UserService, on any device.
- That Shizuku actually binds the service, i.e. that `exported="true"` on the service is correct
  and no `android:process` is needed. Nothing in the build can tell us this.
- That the self-test `id` really reports uid 2000 in the UserService process.
- That the timeout, the 64 KiB truncation cap and `cancel()` behave as intended.
- That redaction behaves as designed on real output; the unit tests only cover fixed strings.
- The per-test names were never read — only that `:app:testDebugUnitTest` did not fail.
- The RTL title fix was a code-level change; the user has not yet confirmed the Persian title.
- `<queries>` package visibility was verified in the merged manifest only, not on a device.

## UNVERIFIED (command-level — to be validated on-device later)
- `am get-standby-bucket` / `am set-standby-bucket <pkg> <bucket>` (M4).
- `dumpsys deviceidle whitelist [+|-]<pkg>` (M4).
- `dumpsys deviceidle force-idle` / `unforce` (M4).
- `pm install-multiple -r <apks>` permission on Android 13+ shell uid (M5).
- `restorecon -R /data/data/<pkg>` availability in Shizuku shell/root context (M5).