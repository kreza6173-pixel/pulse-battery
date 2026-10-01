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
