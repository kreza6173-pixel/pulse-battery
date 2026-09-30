# Decisions Log

1. **Toolchain** — AGP 9.4.1 (latest stable; 9.5.0 is alpha-only on Google Maven). Confirmed via
   `dl.google.com/.../gradle/maven-metadata.xml`. AGP 9.4.1 requires Gradle >= 9.6.0 and JDK 17
   (verified from AGP 9.4.0 release notes on developer.android.com). Gradle pinned to 9.6.0 in CI
   via `gradle/actions/setup-gradle` `gradle-version` (no wrapper jar needed in CI).
   AGP 9.4.1's POM declares Kotlin 2.2.10, so Kotlin Gradle plugin pinned to 2.2.10 to match
   the AGP-tested Kotlin (avoids Compose-compiler/Kotlin mismatch).

2. **compileSdk** = 36 (Android 16). AGP 9.4 supports up to API 37, but the API 37 SDK platform is
   not yet stable-published (maven 404 in this env date 2026-09), so 36 is the latest STABLE SDK.
   targetSdk = 36, minSdk = 26 (per spec; Shizuku API v13 drops pre-API 26 anyway).

3. **No gradle-wrapper.jar** committed. CI invokes `gradle` directly (setup-gradle `gradle-version`).
   `gradle/wrapper/gradle-wrapper.properties` committed only for local-dev reference (pinned 9.6.0).

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
