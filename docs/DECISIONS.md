# Decisions Log

0. **[2026-09-30, M0 CI green] Toolchain pinned to compose-samples `v2025.12.00`.**
   The previous toolchain (AGP 9.4.1 / Gradle 9.6.0 / compileSdk 37 / Compose BOM 2026.09.00) was
   red: CI run `36788889668` failed in the job step **`Install Android SDK 37`** with
   `Warning: Failed to find package 'platforms;android-37'` → `##[error]Process completed with exit code 1.`
   `platforms;android-37` is not available on the stable SDK channel, so the whole build never started.

   Decision: use the **latest official sample release that still targets compileSdk 36**, namely
   `android/compose-samples` tag **`v2025.12.00`** — a single consistent source for every version.

   Evidence (fetched from
   `https://raw.githubusercontent.com/android/compose-samples/v2025.12.00/Jetchat/gradle/libs.versions.toml`):
   - `androidGradlePlugin = "8.13.1"`
   - `kotlin = "2.2.21"`
   - `androidx-compose-bom = "2025.12.00"`
   - `androidx-activity-compose = "1.12.1"`
   - `androidx-lifecycle-compose = "2.10.0"` (used for `lifecycle-runtime-ktx`)
   - `compileSdk = "36"`, `minSdk = "23"`

   Newer tags were rejected: `v2026.01.00`+ use `compileSdk = "37"`, `v2026.08.00`/`main` use AGP 9.x.

   Supporting evidence:
   - Gradle **8.13** — `gradle/wrapper/gradle-wrapper.properties` at `v2025.12.00` pins
     `gradle-8.13-bin.zip`; AGP 8.13.x requires Gradle 8.13+. CI pins the same via
     `gradle/actions/setup-gradle` `gradle-version: '8.13'`.
   - JDK 17 — `.github/workflows/build-sample.yml` at `v2026.08.00` uses `actions/setup-java@v5`
     with `java-version: 17`; unchanged in our CI.
   - Plugin set — `Jetchat/app/build.gradle.kts` at `v2025.12.00` applies
     `com.android.application` + `org.jetbrains.kotlin.android` + `org.jetbrains.kotlin.plugin.compose`.
     Unlike AGP 9, **AGP 8.13.1 requires the standalone `org.jetbrains.kotlin.android` plugin**.
   - No dependency in this catalog has metadata requiring `minCompileSdk` above 36 (BOM 2025.12.00,
     activity-compose 1.12.1, lifecycle 2.10.0, core-splashscreen 1.2.0).

1. ~~**Toolchain** — AGP 9.4.1 ...~~ **SUPERSEDED by decision 0.**

2. ~~**compileSdk = 37 ...**~~ **SUPERSEDED by decision 0** (API 37 platform is not installable from
   the stable SDK channel — see CI run `36788889668`).

3. **No gradle-wrapper.jar** committed. CI invokes `gradle` directly (setup-gradle `gradle-version`).
   `gradle/wrapper/gradle-wrapper.properties` committed only for local-dev reference (pinned 8.13).

4. **CI cache provider = `basic`** (open-source MIT) instead of the default `enhanced` (proprietary)
   to keep the toolchain 100% FOSS-friendly tooling, matching the F-Droid spirit.

5. **Shizuku library 13.1.5** (api + provider). Confirmed as the latest version on Maven Central
   (badge `img.shields.io/maven-central/v/dev.rikka.shizuku/api` → v13.1.5). The manager app is at
   13.6.0, but the LIBRARY artifact's latest published is 13.1.5; the Shizuku API is stable within
   the 13.x line, so this is sufficient and is the latest published library.

6. **Single-activity + Navigation** (Compose). One `MainActivity`. Navigation added when the feature
   set grows beyond the home state machine.

7. **No DI framework.** The app is small; a DI framework is not justified for the milestone scope.
   Dependencies are passed explicitly (constructor params). Re-evaluate if the codebase grows.

8. **Vault path = `/sdcard/pulse-vault`** (shell-accessible from the UserService process). The shell
   uid (2000) cannot read this app's private data dir, so the vault cannot live under
   `/data/data/io.github.kreza6173pixel.pulsebattery`. Matches legacy `VAULT_ROOT`.

9. **State persistence** = Jetpack DataStore (Preferences) for settings/theme; Room only if a
   structured command history or vault metadata DB is needed (defer).

10. **AI assistant** — README describes no AI feature; nothing to gate. Decision: do not implement.

11. **`testDebugUnitTest`** uses JUnit 4 (`junit:junit:4.13.2`) for hermetic JVM tests of parsers.
    (Instrumented tests require a device; the phone-only workflow can't run them in CI, so they are
    optional and not part of the green-CI gate.)

12. **lint `abortOnError = false`** for the first milestone(s) so trivial M0 warnings cannot block CI;
    revisit to strict once the codebase stabilizes.

13. **XML app theme is `android:Theme.Material.Light.NoActionBar`, not `Theme.Material3.*`.**
    The M0 skeleton has no `com.google.android.material` dependency, so `Theme.Material3.DayNight.NoActionBar`
    and the `attr/colorPrimary` / `attr/colorOnPrimary` attrs do not exist and
    `:app:processDebugResources` fails at link time (CI run `36792965261`:
    `values.xml:334: error: resource attr/colorPrimary ... not found`). Adding the Material Views library
    just for one style is not justified at M0; dark mode is already handled in Compose via
    `PulseBatteryTheme` / `isSystemInDarkTheme()`. Revisit when XML themes gain real theming needs.

14. **Compose API surface must match the resolved BOM, not the newest-looking code.**
    Compose BOM `2025.12.00` resolves `androidx.compose.material3:material3` to **1.4.0** (verified by
    reading `compose-bom-2025.12.00.pom` from Google Maven). In 1.4.0 `SmallTopAppBar` **no longer
    exists** — verified directly against the artifact: `material3-android-1.4.0.aar` →
    `androidx/compose/material3/AppBarKt.class` contains 0 occurrences of `SmallTopAppBar` and 76 of
    `TopAppBar`. CI run `36793279060` failed `:app:compileDebugKotlin` with
    `MainActivity.kt:11:35 Unresolved reference 'SmallTopAppBar'`. Replaced with `TopAppBar`
    (with `@OptIn(ExperimentalMaterial3Api::class)`).

15. **`PulseBatteryTheme` was missing `@Composable`.** Same run reported
     `Theme.kt:14:5 Functions which invoke @Composable functions must be marked with the @Composable
     annotation` plus 3 follow-on "invocations can only happen from the context of a @Composable
     function" errors at lines 15, 16 and 22. This was a genuine defect, not cascade from the
     `SmallTopAppBar` error (separate file, separate root cause) — it had never been caught because
     the build previously died at the SDK-install step before compiling.

16. **[2026-10-01, M1] Shizuku client library pinned to `13.1.5` — VERIFIED, not assumed.**
     M0 recorded `shizuku = "13.1.5"` as never resolved by Gradle. M1 did the one permitted lookup.

     Evidence 1 — `https://repo1.maven.org/maven2/dev/rikka/shizuku/api/maven-metadata.xml`
     returned **HTTP 403** (Sonatype: "This IP has been blocked for excessive or automated
     consumption of Maven Central"). The same lookup against Google's Maven Central mirror,
     `https://maven-central.storage-download.googleapis.com/maven2/dev/rikka/shizuku/api/maven-metadata.xml`,
     returned:

     ```
     <latest>13.1.5</latest>
     <release>13.1.5</release>
     <lastUpdated>20230921012957</lastUpdated>
     ```

     Versions present: `11.0.2, 11.0.3, 12.0.0, 12.1.0, 12.2.0, 13.0.0, 13.1.0, 13.1.1,
     13.1.2, 13.1.3, 13.1.4, 13.1.5`. `dev/rikka/shizuku/provider/maven-metadata.xml` returned an
     identical version list with `<lastUpdated>20230921012958</lastUpdated>`, so `api` and
     `provider` are version-locked to each other — one `shizuku` version reference is correct.

     Evidence 2 — `api-13.1.5.aar` (24,652 B) and `provider-13.1.5.aar` (7,056 B) were both
     downloaded with HTTP 200, so both coordinates genuinely exist. Their class files were
     enumerated and the public API surface read directly rather than recalled from memory:

     - `rikka.shizuku.Shizuku` exposes `pingBinder()Z`, `checkSelfPermission()I`,
       `requestPermission(I)Z`, `shouldShowRequestPermissionRationale()Z`, `getUid()I`,
       `addBinderReceivedListener(OnBinderReceivedListener)V`, `addBinderDeadListener(OnBinderDeadListener)V`,
       `addRequestPermissionResultListener(OnRequestPermissionResultListener)V`, and the matching
       `remove*` methods returning `Z`.
     - The listener interfaces are single-abstract-method Java interfaces, so Kotlin SAM
       conversion applies: `onBinderReceived()V`, `onBinderDead()V`, `onRequestPermissionResult(II)V`.
     - `rikka.shizuku.ShizukuProvider` (provider artifact) asserts the binder and requires
       `android:exported="true"` and `android:multiprocess="false"`; its authority is
       `content://` + `getPackageName()` + `.shizuku`, i.e. `${applicationId}.shizuku`.
     - The provider AAR manifest contributes `moe.shizuku.manager.permission.API_V23`, and the
       `V3_SUPPORT` meta-data. M1 also declares the permission in `AndroidManifest.xml` explicitly
       so the requirement does not depend on manifest-merger behaviour.

     **Now verified in a build:** CI run `36806012324` (success) compiled against these
     artifacts. Run `36805705739` failed `:app:compileDebugKotlin` with exactly one error and it
     was not a Shizuku one — `ShizukuRuntime.kt:98:14 Unresolved reference 'addPackage'` — so the
     coordinates resolved and every `Shizuku.*` reference in the app resolved too. The shipped
     APK contains `rikka/shizuku/Shizuku`, `rikka/shizuku/ShizukuProvider` and
     `Shizuku$OnRequestPermissionResultListener` in its dex files, and the merged manifest inside
     the APK contains the provider, the `${applicationId}.shizuku` authority, the `API_V23`
     permission and both `<queries>` packages.

17. **[2026-10-01, M1] The state machine is pure Kotlin; only a thin runtime touches Android.**
     `shizuku/ShizukuState.kt` has **no** Android and **no** Shizuku imports: it holds
     `ShizukuState`, `ShizukuSignals`, `resolveShizukuState(...)`, `classifyUid(...)`. All side
     effects live in `shizuku/ShizukuRuntime.kt` (PackageManager, binder listeners, permission,
     intents). Reason: instrumented tests cannot run in CI (see decision 11), so the only way to
     test the `NOT_INSTALLED -> NOT_RUNNING -> PERMISSION_NEEDED -> READY` transitions is to keep
     them free of Android types. M0's LESSONS §5 notes a green `testDebugUnitTest` proved almost
     nothing; it now covers 11 real assertions across all 8 signal combinations.

18. **[2026-10-01, M1] Title clipping and the duplicated title.**
     The title appeared twice because `MainActivity` rendered `R.string.app_name` in the
     `TopAppBar` **and** a body `Text` using `R.string.hello`, whose value was the same string
     literal. `hello` was deleted and the body text removed; only the `TopAppBar` title remains.
     The trailing-edge clipping under the `fa` (RTL) locale was fixed with
     `maxLines = 1` + `overflow = TextOverflow.Ellipsis` + `fillMaxWidth()` on the title `Text`,
     so a title wider than the bar ellipsizes instead of being cut off. All new padding uses
     `start`/`end` (RTL-aware `paddingRelative`), never `left`/`right`. `supportsRtl="true"` was
     already set and is retained.

     **UNVERIFIED:** this is a code-level fix. Only the user can confirm it, by switching the
     phone to Persian and looking at the title bar.

19. **[2026-10-01, M1] Manager launches need `FLAG_ACTIVITY_NEW_TASK`.**
     `ShizukuRuntime` deliberately holds the *application* context (an Activity context would leak),
     so `context.startActivity(...)` must add `Intent.FLAG_ACTIVITY_NEW_TASK` or the framework
     throws `AndroidRuntimeException` at runtime. Every launch goes through one private
     `startIntent(...)` helper that adds the flag. This is a runtime-only defect — CI compiles it
     fine either way — so it would never have been caught by the build.

20. **[2026-10-01, M2] AIDL re-enabled via `buildFeatures { aidl = true }`.**
     AGP 8 disables AIDL generation by default, so without this flag `src/main/aidl/` is silently
     ignored and `IUserService` would never be generated. This is a build-file change only; it does
     not touch the pinned toolchain (same AGP 8.13.1, same Kotlin, same compileSdk 36).

21. **[2026-10-01, M2] AIDL result travels as a `Bundle`, not as the `ExecResult` Parcelable.
     This is forced by the toolchain — PROVEN, not a preference.**
     I first wrote `IUserService.exec` to return the Kotlin `ExecResult` Parcelable directly,
     on the assumption that AIDL marshals any Java-defined Parcelable the way it marshals a
     framework one. **That assumption was wrong**, and CI run `36808003294` failed
     `:app:compileDebugAidl` with exactly two errors:

     ```
     > Task :app:compileDebugAidl FAILED
     ERROR: .../IUserService.aidl: Couldn't find import for class ExecResult. Searched here:
       - .../versionedparcelable-1.1.1/aidl/
       - .../core-1.16.0/aidl/
       - .../src/debug/aidl/
       - .../src/main/aidl/
     ERROR: .../IUserService.aidl:21.1-15: Failed to resolve 'ExecResult'
     ```

     Root cause, read off the `aidl` invocation in the same log: the tool is called with
     `-p<framework.aidl>` and four `-I` include directories (the app's `src/main/aidl`,
     `src/debug/aidl`, and the `aidl/` directories of extracted AARs) and **no Java
     classpath at all**. So an app-defined Parcelable is simply invisible to it.

     The only way to make `ExecResult` resolvable would be a `parcelable ExecResult;`
     declaration — and that makes `aidl` emit an empty Java class with the same
     fully-qualified name, which collides with the Kotlin implementation.

     Decision: the AIDL method returns `android.os.Bundle`, written fully qualified so it
     needs no import. `ExecResult` stays the canonical typed result (still a real
     `Parcelable` with a `@JvmField CREATOR`, per the brief), and `ExecResult.toBundle()` /
     `ExecResult.fromBundle()` convert at the binder boundary. All four fields
     (`exitCode`, `stdout`, `stderr`, `truncated`) cross intact. The alternative of `out`
     parameters was rejected because AIDL's `out` parameter marshalling was not verifiable
     here, whereas `Bundle` needs no type resolution beyond the framework.

     **Lesson:** the aidl compiler's type resolution is narrower than the Java compiler's.
     Reading a dependency's AAR tells you what a *Java* class can see; it says nothing about
     what `aidl` can see.

22. **[2026-10-01, M2] The `bindUserService` return value is discarded, on purpose.**
     Read from the bytecode of `api-13.1.5.aar`, not from memory:
     `Shizuku` is `public` (class access `0x0021`), but `ShizukuServiceConnection` and
     `ShizukuServiceConnections` are **package-private** (class access `0x0020`, no `ACC_PUBLIC`).
     So the type of `Shizuku.bindUserService(...)`'s result cannot be named from application code.
     The call is written as a bare statement inside a `try`/`catch` rather than wrapped in
     `runCatching`, because `runCatching`'s type parameter would force Kotlin to name that
     inaccessible return type. The API surface actually used was read from the AAR:
     `Shizuku.UserServiceArgs(ComponentName)` with fluent `daemon(boolean)`, `debuggable(boolean)`,
     `version(int)`, `processNameSuffix(String)`; `Shizuku.bindUserService(UserServiceArgs,
     ServiceConnection)`; `Shizuku.unbindUserService(UserServiceArgs, ServiceConnection)`.

23. **[2026-10-01, M2] `Shizuku.newProcess` is not used.** The brief forbade it and the code
     complies: there is no reference to `newProcess` or `ShizukuRemoteProcess` anywhere. Every
     command runs inside `ShizukuExecService`, the declared UserService.

24. **[2026-10-01, M2] No new dependency.**
     The console screen needs `kotlinx.coroutines.Dispatchers.IO` and `withContext`, and
     `rememberCoroutineScope`. Neither is declared here: `androidx.compose.runtime:runtime` exposes
     `kotlinx-coroutines-core` as an `api` dependency, so coroutines are already on the compile
     classpath through the existing Compose dependencies. The version catalog is unchanged.
     Coroutines are also the reason the blocking AIDL call is not on the main thread.

25. **[2026-10-01, M2] Shell quoting is unconditional.**
     `ShellQuoting.quote` always wraps in single quotes rather than only when the argument looks
     unsafe. A "quote if needed" optimisation leaves user input unquoted for whatever characters
     the author believed were safe, and that judgement is exactly what becomes an injection later.
     Always quoting makes the helper safe by construction and testable without a shell.
     `ExecResult` for a command is the *raw* line the user typed — this is an intentional
     shell console, so the operator supplies the syntax; what M2 guarantees is that no *other*
     part of the app concatenates user input into a command. The only commands the app itself
     builds are the two read-only self-test presets, which are constants.

26. **[2026-10-01, M2] Redaction happens once, at insert time, and over-masks on purpose.**
     `ConsoleHistory.record(...)` stores only redacted text; raw stdout/stderr is never retained,
     so a secret cannot resurface through a later render. `Redaction` masks the value of an
     assignment whose key looks sensitive, and masks an `Authorization` header to end of line —
     an earlier version masked only `\S+` and leaked the token in
     `Authorization: Bearer eyJ...`, which the `RedactionTest` caught before pushing. Where the
     filter has to choose, it hides extra text rather than risking a leak.

27. **[2026-10-01, M2] `ConsoleHistory` takes plain values, not `ExecOutcome`.**
     `ExecOutcome` carries an `ExecResult`, which is a `Parcelable`. Depending on it would pull an
     Android type into the classpath of the hermetic JVM tests. Following decision 17, the history
     takes primitives and Strings and the screen does the mapping. Same reason `Redaction`,
     `ShellQuoting` and `OutputCollector` carry no Android imports at all.

28. **[2026-10-01, M2] `ExecutorService.submit { }` picked the `Runnable` overload and erased the
     result. My first diagnosis of this was WRONG and is corrected here.**
     Run `36808508335` failed with `ShizukuExecService.kt:56:18 Unresolved reference 'toBundle'`,
     and I concluded it was `getOrElse`'s `T : R` inference widening a platform type. **That was
     wrong.** The real cause was present from the start and only surfaced once the type was made
     explicit in run `36808950548`:

     ```
     e: .../exec/ShizukuExecService.kt:51:38 Initializer type mismatch: expected 'ExecResult', actual 'Any!'.
     ```

     `ExecutorService` declares `submit(Runnable): Future<?>`, `submit(Callable<T>): Future<T>` and
     `submit(Runnable, T): Future<T>`. A zero-parameter lambda returning a value fits **both**
     `Runnable` and `Callable<T>`, and Kotlin selected `Runnable` — whose result is discarded, so
     the future came back `Future<*>` and `get()` yielded `Any!`. Nothing to do with `getOrElse`.

     Fix: serialisation now uses a plain monitor (`synchronized(commandLock) { ... }`) instead of an
     executor. No overload to choose, no inference to get wrong, and the guarantee is stronger —
     nothing can be submitted past the lock. The same file's drain futures are now explicitly
     typed `val readers: List<Future<*>>` so the element type is never inferred either.

     **How I got it wrong, and the generalisable lesson:** I explained away a platform-type error
     with a plausible-sounding story about generics instead of proving it, and I only discovered the
     real cause because a later run failed again with a *different* message from the same line. When
     a fix changes the message but not the symptom, the previous diagnosis was wrong — re-derive it
     rather than adjusting it. `compileDebugKotlin` reports a *variable* number of errors (1 in one
     run, 5 in the next for identical source), so "the first error" is not a reliable sample of the
     whole problem.

29. **[2026-10-01, M2] Two more compile errors in the same run, both mechanical.**
     From run `36809228035`:
     - `ExecBridge.kt:100:85 No value passed for parameter 'p2'.` — `Shizuku.unbindUserService`
       takes **three** arguments. Verified from `api-13.1.5.aar`:
       `unbindUserService(UserServiceArgs, ServiceConnection, boolean)V`. The third is the
       server-API-version flag for the v13 user-service path; we only ever bind after reaching
       `READY`, which requires a v13+ server, so it is always `true`. `bindUserService` takes two
       and was already correct.
     - `ShizukuExecService.kt:150:41 None of the following candidates is applicable:
       constructor(size: Int): CharArray` — `CharArray(chunk, 0, n)` is not a constructor; the only
       constructors take a size. Copying a range of an existing array is `copyOfRange(0, n)`.

     Also fixed in the same pass: `Int.coerceIn` only accepts two `Int` bounds, so
     `timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)` failed against the `Long` constants
     (`250L`, `120_000L`). `toLong()` has to come first.

30. **[2026-10-01, M2] `Redaction` rewritten: one sensitive key can hide another.**
     While auditing the unit tests (which had still never been compiled or run) I found a real leak:
     `binder error: password=hunter2` was **not** redacted. The token-level value pattern
     `[^\s'"]+` matched `error: password=hunter2` as a whole and, because `error` is not sensitive,
     the `password` inside it was never examined. `ConsoleHistoryTest` would have failed on it.

     `Redaction` now works line by line and locates the key with `KEY_ASSIGN.findAll`, taking the
     **first sensitive** match and masking from its value to end of line. That is simpler than the
     three-regex version it replaces, fixes the leak, and still subsumes the `Authorization` header
     case (authorization is in the key list). Short keys (`pin`, `otp`) now only match exactly,
     because suffix matching them would mask innocent text such as `spin=1`.

     **Method note:** the expected values in the new tests were computed with a throwaway script
     that re-implements the pure logic, rather than hand-derived. Hand-derivation got three
     literals wrong (including two apostrophe-escaping cases) and one assertion backwards. Pure
     display/parse logic is cheap to model and cheap to check — do that instead of counting
     characters by eye.

31. **[2026-10-01, M2] `CharArray` is not a `CharSequence`, and `copyOfRange` returns one.**
     Run `36810306371` failed with a single error:

     ```
     e: .../exec/ShizukuExecService.kt:154:41 Argument type mismatch: actual type is 'CharArray', but 'CharSequence' was expected.
     ```

     The previous fix for the nonexistent `CharArray(chunk, 0, n)` constructor had substituted
     `chunk.copyOfRange(0, n)`. That exists, returns a `CharArray`, and `CharArray` does **not**
     implement `CharSequence`, so it cannot be passed to `OutputCollector.appendAll(CharSequence)`.
     I had checked that the *constructor* existed without checking the *returned type's*
     relationship to the *parameter*. Fixed with `String(chunk, 0, n)`, the stdlib
     CharArray/offset/length conversion, which also copies once instead of allocating an
     intermediate array. Run `36811639208` is green.

     **Method note.** This is the third M2 error in the same family — JVM-vs-Kotlin type identity
     rather than logic: `CharArray` vs `CharSequence`, `Int` vs `Long` literals, and a Java
     overload that erases a type. All three compile fine in the reader's head and fail in CI.
     Converting a `CharArray` to text must go through `String(...)` or `concatToString()`.

32. **[2026-10-01, M2] M2 is CI-green (`36811639208`) but proves nothing at runtime.**
     The build now proves: AIDL generates, all Kotlin compiles, `ShellQuotingTest`,
     `RedactionTest`, `OutputCollectorTest` and `ConsoleHistoryTest` compile and pass, the service
     and provider are in the merged manifest, and all M2 classes are in the shipped dex. It does
     **not** prove that Shizuku binds the service, that a command runs, that `id` reports uid 2000,
     or that timeout/truncation/cancel work. Treat M2 as unverified until the phone check.

33. **[2026-10-01, M2, phone] The UserService stayed DISCONNECTED. Cause NOT yet known; the
     reason it was undiagnosable is this decision.**
     The user installed `36811639208` and reported: READY with uid 2000, the **Open console** button
     present, the console opening, but the header stuck at `User service: disconnected` with Run,
     Cancel and both quick buttons disabled — so `onServiceConnected` never fired.

     Where the pieces are:
     - `exec/ExecBridge.kt:96` `connect()`; `:116` the `Shizuku.bindUserService` call.
     - Args built at `:106-108`: `UserServiceArgs(component)` with `.daemon(false)` and
       `.debuggable(true)`. `component` is built at `:38` from `applicationContext`. **No `tag`,
       no `version(...)`, no `processNameSuffix(...)`.**
     - Trigger is **becoming READY**, not opening the console: `MainActivity.kt:76-78`,
       `DisposableEffect(ready) { if (ready) bridge.connect() else bridge.disconnect() }` where
       `ready = runtime.state == ShizukuState.READY` (`MainActivity.kt:75`).
     - UI state is written at `ExecBridge.kt:68` (`onServiceConnected` → CONNECTED), `:63` (null
       binder), `:75` (`onServiceDisconnected`), `:127` (bind failed), and on Shizuku binder-death.

     **The defect that hid the cause:** `connect()` used `catch (_: Exception) { false }` and set
     DISCONNECTED. Every failure mode — wrong component, service not in the manifest, missing
     exported flag, version mismatch, permission race — produced the *identical* UI with the reason
     thrown away. `connect()` also sets `CONNECTING` and, on failure, resets it inside one frame, so
     the user never even sees the intermediate state. `runCatching { Shizuku.bindUserService(...) }`
     made it worse, because naming the result would force Kotlin to name the package-private
     `ShizukuServiceConnection` return type (decision 22).

     Verified against `api-13.1.5.aar` rather than assumed: `UserServiceArgs` really does have the
     `(Landroid/content/ComponentName;)V` constructor plus `daemon`/`debuggable`
     (`(Z)L…UserServiceArgs;`), `version` (`(I)…`) and `processNameSuffix`/`tag`
     (`(Ljava/lang/String;)…`). So the API usage is valid and the failure is behavioural.

     This push therefore adds **diagnostics only** and changes no bind logic: a capped, timestamped
     bind log records the component, the args, whether `bindUserService` returned or threw (with
     exception class, message and cause), the result of `Shizuku.peekUserService`, and both
     `ServiceConnection` callbacks. It is rendered on the console so it is readable without logcat.
     `peekUserService` was checked for an error-level deprecation first: `Ljava/lang/Deprecated;`
     and every `DeprecatedLevel` constant are absent from the class file.

     **Deliberately not fixed blind.** The candidates I can see in the code are all plausible and
     none is confirmed; guessing between them would burn another push and another device round-trip.

34. **[2026-10-01, M2, phone] CAUSE FOUND: `processNameSuffix` is MANDATORY in 13.1.5.**
     The bind log shipped in `36813097142` paid for itself immediately. The user transcribed:

     ```
     bindUserService THREW java.lang.NullPointerException, message: process name suffix must not be null
     ```

     That message is Shizuku's own, and `api-13.1.5.aar` shows exactly where it comes from.
     `UserServiceArgs` has one Bundle builder, `forAdd()Landroid/os/Bundle;`, and the constant pool
     runs `Objects.requireNonNull` → `putString` → `shizuku:user-service-arg-process-name`
     immediately after each other. So `forAdd()` unconditionally dereferences `mProcessName`:

     ```java
     args.putString(ShizukuApiConstants.USER_SERVICE_ARG_PROCESS_NAME,
             Objects.requireNonNull(mProcessName, "process name suffix must not be null"));
     ```

     `mProcessName` is only ever set by `processNameSuffix(String)` — verified present as
     `(Ljava/lang/String;)Lrikka/shizuku/Shizuku$UserServiceArgs;` — and there is **no default value
     anywhere in the client library**. So in 13.1.5 the suffix is not optional, and omitting it makes
     `bindUserService` throw on every single attempt. M1 and M2 both called `bindUserService`
     without it; this is why the connection was never anything but DISCONNECTED.

     Fix: `.processNameSuffix("user_service")`, added to the existing chain in
     `ExecBridge.connect()`. The bind log is untouched and still reports the args, the bind result,
     `peekUserService`, and both callbacks, so the next failure is equally legible.

     **Left unset deliberately:** `tag(String)` and `version(int)` both exist in the AAR, but neither
     is mandatory — `forAdd()` does `putString(key, null)` for the tag, which is legal, and
     `versionCode` defaults to 0 and is just copied through. Setting them would be speculative.

     **Main remaining risk, stated up front:** whether a non-empty suffix makes Shizuku start the
     service in a *separate* process named `<package>:user_service`. If it does, the manifest also
     needs `android:process=":user_service"` on the service, and the bind will fail again with a
     different message. The server-side naming rule is not in the client AAR, so this could not be
     confirmed offline; the bind log will show it immediately.

     **Method note:** this cost four device round-trips purely because the first `connect()`
     discarded its exception. One `note()` per state transition would have made it a single build.

35. **[2026-10-01, M2, phone] `processNameSuffix` fix worked; now `onServiceConnected` never fires.
     The client AAR cannot answer why — and that is itself the finding.**
     Phone result for `36814399485`: the NPE is gone. The bind log now reads
     `processNameSuffix=user_service`, `bindUserService returned normally, peekUserService=-1
     (bind is async)` — and then nothing. No `onServiceConnected`, no
     `onServiceDisconnected`, the header sits on `connecting` indefinitely, buttons disabled.
     The clipped label is fixed.

     **Finding (1) — what the 13.1.5 AAR actually contains.** I scanned all 15 classes of
     `api-13.1.5.aar` for every symbol involved in instantiating or locating a service class:
     `Class`, `getConstructor`, `getDeclaredConstructor`, `newInstance`, `PackageManager`,
     `getServiceInfo`, `ServiceInfo`, `bindService`, `startService`, `getApplicationInfo`,
     `ClassLoader`, `loadClass`. **All absent from every class.** The single `forName` is in
     `SystemServiceHelper`, which resolves system services and is unrelated.

     So the client library **never instantiates the UserService class, never reads the manifest,
     and never binds or starts anything.** It only marshals a `Bundle` of arguments over the
     Shizuku binder. Which constructor is accepted, and whether the class must be declared in the
     manifest, is decided by the **Shizuku server** — a different app
     (`moe.shizuku.privileged.api`) that is not in this AAR and whose code cannot be read from
     here. Answering (1) from the client AAR is therefore impossible, and I am not going to guess
     it. Adding `android:process` would be exactly that guess.

     **Our own service is not obviously at fault.** `ShizukuExecService`
     (`exec/ShizukuExecService.kt:25`) has a no-arg constructor and its only field initialisers
     are `Any()` (`:37`), `Executors.newCachedThreadPool()` (`:40`) and two `AtomicReference`s
     (`:43`, `:46`), plus the `IUserService.Stub` anonymous object (`:48`). None touches a
     `Context`, a resource, `Shizuku` or any Android static, so construction cannot throw on
     those grounds.

     **Finding (2) — diagnostics added, no logic changed.** `ExecBridge` now logs, at bind time and
     again at t+10s: `pingBinder()`, the server API version, the server uid and
     `checkSelfPermission()` (each rendered as its value, or `THREW <class>` if the call could not
     be made). A watchdog on the main looper re-checks `peekUserService` at t+3s and t+10s and
     emits `NO onServiceConnected after 10s` if the state is still `CONNECTING`. Method names were
     verified present in the AAR before use: `pingBinder`, `getVersion`, `getUid`,
     `checkSelfPermission`, `peekUserService`. All callbacks cancel the watchdog, so it cannot
     fire after a successful bind.

     **Self-inflicted errors caught before pushing:** the first draft passed `peek(args)` as the
     second argument of a helper whose parameter is a *lambda* — a guaranteed compile error — and
     referenced `PackageManager.PERMISSION_GRANTED` without importing it. Both were caught by
     re-reading the diff, not by CI. Reading the diff is now part of the routine.
