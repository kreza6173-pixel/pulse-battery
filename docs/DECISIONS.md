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

21. **[2026-10-01, M2] `ExecResult` is a Kotlin Parcelable; the AIDL deliberately omits
     `parcelable ExecResult;`.**
     Writing `parcelable ExecResult;` in a `.aidl` file makes the AIDL compiler emit an empty Java
     stub class with the same fully-qualified name, which clashes with a Kotlin implementation.
     Instead the interface references the Kotlin `ExecResult` directly, and AIDL marshals it through
     `Parcelable.writeToParcel` / `Parcelable.CREATOR` at runtime — the same documented mechanism an
     AIDL interface uses for any Java-defined Parcelable. `ExecResult.kt` therefore carries a
     `@JvmField CREATOR`, and the field order in `writeToParcel` must match the read order in
     `createFromParcel`.

     **UNVERIFIED until CI:** whether `aidl` accepts an undeclared Parcelable as a return type. If
     `:app:compileDebugAidl` fails to resolve `ExecResult`, the fallback is to change the signature
     to `int exec(String command, int timeoutMs, out String stdout, out String stderr,
     out boolean truncated)`, which is unambiguously supported and carries the same data.

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
