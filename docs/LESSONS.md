# Lessons — M0 toolchain bring-up

Scope: everything below was observed in this repo between 2026-09-30 and 2026-10-01.
Every claim is either quoted from a CI log/artifact in this project or is explicitly
marked **UNVERIFIED**. Nothing here is inferred from general knowledge.

Branch: `native-app-v0`. Green run: **36795807603**
(<https://github.com/kreza6173-pixel/pulse-battery/actions/runs/36795807603>).

---

## 1. Versions in the green build

Copied verbatim from `android-app/gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.13.1"
kotlin = "2.2.21"
composeBom = "2025.12.00"
activityCompose = "1.12.1"
lifecycleRuntimeKtx = "2.10.0"
coreSplashscreen = "1.2.0"
shizuku = "13.1.5"
junit = "4.13.2"
```

Plugin ids applied (`[plugins]` in the same file, and `app/build.gradle.kts`):

```toml
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Not in the catalog, set elsewhere:

| Item | Value | Where |
|---|---|---|
| Gradle | `8.13` | `gradle/wrapper/gradle-wrapper.properties` → `gradle-8.13-bin.zip`; also `gradle-version: '8.13'` in `ci.yml` |
| JDK | `17` | `ci.yml` → `actions/setup-java@v5`, `distribution: temurin`, `java-version: 17` |
| `compileSdk` | `36` | `app/build.gradle.kts:9` |
| `targetSdk` | `36` | `app/build.gradle.kts:14` |
| `minSdk` | `26` | `app/build.gradle.kts:13` |
| Source of these versions | `android/compose-samples` tag `v2025.12.00`, `Jetchat/gradle/libs.versions.toml` | see §2.0 |

**UNVERIFIED:** the `AGP 8.13.1 requires Gradle >= 8.13` compatibility claim. It was never
checked against Google's AGP release notes. What *is* verified is that Gradle 8.13 + AGP
8.13.1 together produce a green build — the combination that the sample pins.

**UNVERIFIED:** `shizuku = "13.1.5"` and `coreSplashscreen = "1.2.0"` exist in the catalog but
are **not referenced** by `app/build.gradle.kts`, so Gradle never resolved them and CI never
downloaded them. Their availability is untested. The resolved set is only:
`activity-compose`, `lifecycle-runtime-ktx`, `compose-bom`, `material3`, `compose.ui`,
`ui-tooling`, `junit`.

---

## 2. CI failures encountered, in order

Runs on `native-app-v0`, oldest first. Only four logs were actually read; two were not.

| Run | SHA | Conclusion | Log read? |
|---|---|---|---|
| 36788153371 | `f6cf516` | failure | **no — UNVERIFIED** |
| 36788386512 | `3541021` | failure | **no — UNVERIFIED** |
| 36788889668 | `77393aa` | failure | yes |
| 36792965261 | `8fad552` | failure | yes |
| 36793279060 | `e3fe376` | failure | yes |
| 36794363757 | `b58f6d7` | success | yes |
| 36795807603 | `40ebc9f` | success | yes |

### 2.0 Pre-existing state (run 36788889668)

**Failing task:** workflow step `Install Android SDK 37`.
**Key error line:**

```
Warning: Failed to find package 'platforms;android-37'
##[error]Process completed with exit code 1.
```

**Root cause:** `platforms;android-37` was not installable by the CI runner's `sdkmanager`, so
the job died before Gradle ran. Nothing about the Kotlin or Compose setup had been exercised yet.

**Fix that worked:** replace the whole toolchain with the `v2025.12.00` compose-samples set
(`8fad552`) — compileSdk/targetSdk 36, AGP 8.13.1, Kotlin 2.2.21, Gradle 8.13, Compose BOM
2025.12.00, and re-add the standalone `org.jetbrains.kotlin.android` plugin (AGP 8.x needs it;
AGP 9 had made it unnecessary).

### 2.1 Run 36792965261 — resource linking

**Failing task:** `:app:processDebugResources` (workflow step `Build (test, lint, assemble)`).
**Key error lines:**

```
io.github.kreza6173pixel.pulsebattery.app-mergeDebugResources-36:/values/values.xml:334: error: resource attr/colorPrimary (aka io.github.kreza6173pixel.pulsebattery:attr/colorPrimary) not found.
error: resource style/Theme.Material3.DayNight.NoActionBar (aka io.github.kreza6173pixel.pulsebattery:style/Theme.Material3.DayNight.NoActionBar) not found.
error: failed linking references.
```

**Root cause:** `values/themes.xml` used `Theme.Material3.DayNight.NoActionBar` and
`attr/colorPrimary`/`attr/colorOnPrimary`. Those come from `com.google.android.material`, which
this module does not depend on.

**Fix that worked (`e3fe376`):** `Theme.PulseBattery` parent →
`android:Theme.Material.Light.NoActionBar`, drop the two `colorPrimary` items, and set
`android:statusBarColor` to a literal colour. Dark mode already lives in
`ui/theme/Theme.kt` via `isSystemInDarkTheme()`.

### 2.2 Run 36793279060 — Kotlin compilation

**Failing task:** `:app:compileDebugKotlin`.
**Key error lines:**

```
e: .../MainActivity.kt:11:35 Unresolved reference 'SmallTopAppBar'.
e: .../MainActivity.kt:24:32 Unresolved reference 'SmallTopAppBar'.
e: .../ui/theme/Theme.kt:14:5 Functions which invoke @Composable functions must be marked with the @Composable annotation
e: .../ui/theme/Theme.kt:15:32 @Composable invocations can only happen from the context of a @Composable function
e: .../ui/theme/Theme.kt:16:18 @Composable invocations can only happen from the context of a @Composable function
e: .../ui/theme/Theme.kt:22:5 @Composable invocations can only happen from the context of a @Composable function
```

**Root cause — two independent defects, not one cascade:**

1. `SmallTopAppBar` does not exist in the resolved Material3. Verified, not assumed:
   `compose-bom-2025.12.00.pom` (fetched from
   `dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.12.00/`) pins
   `androidx.compose.material3:material3` to **`1.4.0`**. Unzipping
   `material3-android-1.4.0.aar` and grepping `androidx/compose/material3/AppBarKt.class`
   gives **0** occurrences of `SmallTopAppBar` and **76** of `TopAppBar`.
   Whether it was "removed" in 1.4.0 or merely absent there is **UNVERIFIED** — I never
   checked an older Material3.
2. `PulseBatteryTheme` in `ui/theme/Theme.kt` genuinely lacked the `@Composable` annotation, so
   `LocalContext.current`, `isSystemInDarkTheme()` and `MaterialTheme()` were composable
   invocations from a non-composable function. Separate file, separate root cause. It had
   never surfaced because the build previously died at the SDK step.

**Fix that worked (`b58f6d7`):** `SmallTopAppBar` → `TopAppBar` with
`@OptIn(ExperimentalMaterial3Api::class)` on `MainActivity`; add `@Composable` to
`PulseBatteryTheme`.

### 2.3 Run 36794363757 — silently missing APK artifact

Not a failing build; a **green** run that shipped no APK. Found by listing artifacts rather
than by reading the log.

**Key line, from the `Upload Debug APK` step of run 36794363757:**

```
##[warning]No files were found with the provided path: app/build/outputs/apk/debug/*.apk. No artifacts will be uploaded.
```

The step's conclusion was `success` and only `build-log` existed in
`actions/runs/36794363757/artifacts`. `if-no-files-found` defaults to `warn`.

**Root cause — two compounding facts:**

1. `defaults.run.working-directory: android-app` applies to `run:` steps only.
   `actions/upload-artifact` resolves `path` from the repository root. Proven by this log: had
   the working directory applied, `app/build/outputs/apk/debug/*.apk` would have matched.
2. Module `:app` lives at `android-app/app`, so AGP writes the APK to
   `android-app/app/build/outputs/apk/debug/app-debug.apk`.

**Fix that worked (`40ebc9f`):** replace the step with one rooted at
`android-app/**/build/outputs/apk/debug/app-debug.apk` and `if-no-files-found: error`.

**Result, verified by downloading the artifact from run 36795807603:**

```
NAME          SIZE          EXPIRED
build-log       1,345 B    false
app-debug   11,270,321 B    false
```

```
app/build/outputs/apk/debug/app-debug.apk   11,734,822 B   Zip archive data18,551,300  classes.dex
       5,968  AndroidManifest.xml
     477,604  resources.arsc
```

The artifact's preserved internal path independently confirms the APK really is at
`android-app/app/build/...`.

### 2.4 A note on the pre-existing `docs/DECISIONS.md`

Before this work the file asserted that API 37 was available and that "the only thing
unavailable was the wrong maven-mirror URL". Run 36788889668 contradicts that. Those entries
are now marked SUPERSEDED. **Lesson: a plausible, well-written rationale in a docs file is not
evidence.** The `##[warning]` in §2.3 is the same trap — it never turns a run red.

---

## 3. The working `.github/workflows/ci.yml`, in full

This is the file that produced green run 36795807603, verbatim.

```yaml
name: CI

on:
  push:
  pull_request:
  workflow_dispatch:

# Uses the GitHub-hosted Gradle action. No secrets are required for debug builds.
permissions:
  contents: read

jobs:
  ci:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android-app

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6
        with:
          gradle-version: '8.13'
          cache-provider: basic

      - name: Install Android SDK 36
        run: |
          SDKMGR="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}/cmdline-tools/latest/bin/sdkmanager"
          if [ ! -x "$SDKMGR" ]; then SDKMGR="$(command -v sdkmanager || true)"; fi
          if [ -z "$SDKMGR" ]; then echo "::error::sdkmanager not found"; exit 1; fi
          yes | "$SDKMGR" --licenses
          yes | "$SDKMGR" "platforms;android-36" "build-tools;36.0.0" "platform-tools"

      - name: Build (test, lint, assemble)
        id: build
        run: |
          set +e
          gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain --stacktrace 2>&1 | tee "${RUNNER_TEMP}/build.log"
          code=${PIPESTATUS[0]}
          echo "gradle_exit=$code" >> "$GITHUB_OUTPUT"
          exit $code

      - name: Upload APK
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          # `uses:` steps ignore defaults.run.working-directory, so this path is
          # resolved from the repository root. Module `:app` lives at
          # android-app/app, so AGP writes the APK to
          # android-app/app/build/outputs/apk/debug/app-debug.apk.
          path: android-app/**/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error

      - name: Upload build log
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: build-log
          path: ${{ runner.temp }}/build.log

      - name: Write failure summary
        if: failure()
        run: |
          {
            echo "## ❌ PULSE native-app-v0 build failed (gradle exit ${{ steps.build.outputs.gradle_exit }})"
            echo
            echo '```'
            tail -n 40 "${RUNNER_TEMP}/build.log"
            echo '```'
          } >> "$GITHUB_STEP_SUMMARY"
```

Note: `gradle/wrapper/gradle-wrapper.jar` is intentionally not committed; CI uses
`gradle/actions/setup-gradle` with the pinned `gradle-version` and invokes `gradle` directly.
The workflow does not prove that the wrapper works locally — **UNVERIFIED**, since no local
build was ever possible (see §4).

---

## 4. Environment facts that cost time

**No local build is possible.** `java -version` → `command not found`; `which gradle` → empty;
`ANDROID_HOME` and `ANDROID_SDK_ROOT` both unset; `~/.gradle` does not exist. Every build in
this session was verified only through CI. Anything not checked by CI is unverified.

**The working branch does not exist locally.** `git branch -a` listed only
`kilo/true-tide-0jx`, `main`, and `remotes/origin/native-app-v0`. `git log native-app-v0`
failed with `fatal: ambiguous argument 'native-app-v0': unknown revision`. Working sequence:

```
git fetch origin native-app-v0
git checkout -b native-app-v0-work FETCH_HEAD
... work ...
git push origin HEAD:native-app-v0
```

Local branch is `native-app-v0-work`; it has **no upstream tracking** (`git branch -vv` shows
no bracket). Pushing therefore requires the explicit `HEAD:native-app-v0` refspec every time —
a bare `git push` will not work.

**`gh run download` must be run from inside a git repository.** Run from a temp dir it failed
with `failed to run git: fatal: not a git repository`. Cost one retry. Network access to
GitHub, `raw.githubusercontent.com` and `dl.google.com` all worked.

**`gh run view --log-failed` output contains ANSI escape codes.** Filter with
`sed 's/\x1b\[[0-9;]*m//g'` before grepping, or error lines get mangled. The log is also
prefixed with `job<TAB>step<TAB>timestamp ` on every line.

**Only failed-step logs are returned by `--log-failed`.** For run 36794363757 I had to use
`gh run view <id> --log` (all steps) to see the artifact warning. Grepping for the step name
is the way to isolate it.

**Artifacts are the ground truth, not step conclusions.** `gh api repos/<owner>/<repo>/actions/runs/<id>/artifacts`
listed only `build-log` for the supposedly-green run 36794363757. Checking step conclusions in
`gh run view --json jobs` would have reported success for a step that uploaded nothing.

**Scratch directory restrictions.** `cd /tmp && …` was denied by a project permission rule
(`external_directory`, pattern `*`). The writable scratch path is
`/tmp/agent_c5165b3b-4a42-4744-8fa9-4e3e44d1d373/`.

**No YAML tooling.** `python3` exists but has no `yaml` module and there is no `pip`. YAML was
validated by extracting step names with a regex instead.

**Writing a file to a path that does not exist silently creates directories.** A `write` to
`…/native-app-v0-work/android-app/gradle/libs.versions.toml` created a whole stray
`native-app-v0-work/` tree instead of updating the real
`android-app/gradle/libs.versions.toml`. Had to `mv` the file into place and `rm -rf` the
stray directory. Check `git status` for unexpected untracked directories after writing files.

**Version evidence: read the POM, not a directory listing.** A grep over
`material3/group-index.xml` suggested Material3 `1.5.0`; direct artifact fetches for
`material3/1.5.0/` returned 404. The authoritative answer came from reading
`compose-bom-2025.12.00.pom`, which pins `1.4.0`. I reported `1.5.0` in chat before
correcting it. Prefer resolving the version from the BOM POM.

**Per-sample catalogs.** `android/compose-samples` has no top-level `gradle/libs.versions.toml`
on `main` (HTTP 404). Per-sample copies work, e.g.
`https://raw.githubusercontent.com/android/compose-samples/v2025.12.00/Jetchat/gradle/libs.versions.toml`.
The file carries the warning that it is duplicated from a global
`scripts/libs.versions.toml`. The CI workflow in that repo is `build-sample.yml`, not
`build_and_test.yaml`.

---

## 4b. Lessons from M1 (2026-10-01)

**The working directory may be the wrong checkout.** This session started in a worktree on
`kilo/able-summit-va4` of the same repo, where `docs/` and `android-app/` did not exist.
`git remote -v` showed only `main` and `remotes/origin/native-app-v0`; the fix was
`git fetch origin native-app-v0 && git checkout -B native-app-v0 origin/native-app-v0`.
Check `git branch -a` before assuming the tree is missing.

**`repo1.maven.org` returns HTTP 403 from this container.** The body is Sonatype's
"This IP has been blocked for excessive or automated consumption of Maven Central". The working
mirror for the same lookup is Google's Maven Central mirror:
`https://maven-central.storage-download.googleapis.com/maven2/<group/path>/<artifact>/maven-metadata.xml`.
That is still one lookup of one artifact — it is a different host for the same file, not a search.

**Verifying a dependency's API surface from the published AAR is possible without a JDK.**
`javap` is unavailable, but `unzip` plus a short tolerant UTF-8 constant-pool scan (walk the
class file, keep every printable `CONSTANT_Utf8` run) lists the class names, method names and
descriptors. This is how the `Shizuku` API surface in DECISIONS.md decision 16 was read rather
than recalled, and it is what made the first M1 compile error the *only* compile error.
Two traps: a strict class-file parser is a waste of time (an exact-width one broke on
`CONSTANT_MethodHandle`/`MethodType`, which are 3 bytes after the tag, not 4), and the resyncing
version that just keeps printable ASCII runs is good enough and never needs fixing.

**Read the *published* manifest, not just your own.** `dev.rikka.shizuku:provider` merges
`moe.shizuku.manager.permission.API_V23` into the app. Decompressing the AAR shows what a
dependency will contribute to *your* manifest before you find out from a link error.

**An APK can be verified without a device.** `unzip` the downloaded artifact: the merged
`AndroidManifest.xml` string pool is UTF-16LE, so a regex for `(?:[\x20-\x7e]\x00){6,}` dumps
the authorities, permissions and `<queries>` entries. The dex string pools are MUTF-8, so plain
byte `in` tests find class descriptors — but check **every** `classes*.dex`, because dex
splitting scatters a single class across files and a grep of `classes.dex` alone finds nothing.

**Java setters do not chain in Kotlin.** `Intent(ACTION_VIEW, uri).addPackage(...)` failed with
`Unresolved reference 'addPackage'` — `setPackage` returns `void`. Grep for other void setters
before writing a builder chain.

---

## 5. Still unverified / not tested

- **The APK has never been installed or launched by me.** The user installed the M0 APK and
  reported it opens and shows only the title. The M1 APK (run `36806012324`) has never been
  installed by anyone. Everything about M1's on-device behaviour is untested.
- `coreSplashscreen = "1.2.0"` is still not resolved by Gradle. `shizuku = "13.1.5"` is now
  resolved and verified (M1).
- Unit tests now exist for the Shizuku state logic (`ShizukuStateTest`, 11 assertions), but the
  uploaded `build-log` artifact is the console log only, so per-test results were never read —
  only that `:app:testDebugUnitTest` did not fail the build.
- `lintDebug` runs with `abortOnError = false`, so a green run does not mean lint is clean.
- The Gradle wrapper path (`./gradlew`) is never exercised in CI.
- Whether AGP 8.13.1 has a documented minimum Gradle version was not checked.
