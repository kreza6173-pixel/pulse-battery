# PULSE // BATTERY 1.0: release map and Android playbook

Updated: 2026-10-02

This is the practical record for continuing the project without relying on chat history. It covers the native app, the release path, and the reusable engineering lessons.

## 1. Current release state

- Native Kotlin + Jetpack Compose app, package `io.github.kreza6173pixel.pulsebattery`.
- Branch `native-app-v0` merged into `main`.
- Version `1.0.0`, Android 8.0+ (SDK 26 minimum), target/compile SDK 36.
- Release APK signed with the neutral certificate `CN=PULSE, O=kreza6173-pixel`.
- Release SHA-256: `3f8c1e2df2deaf9d561a8fdce3c667ed0ceda972622f20d006a1b6098c2a2512`.
- English-only app and metadata. No `INTERNET` permission.
- The old root Shevery module and its ZIP-era files are removed from the active tree.
- Verified on Xiaomi / Android 16 / Shizuku shell uid 2000: connection, diagnostics, standby and Doze, APK Vault restore, console, and overnight report.

## 2. v1.0 publish checklist

### Before publishing

- [ ] Latest `main` CI is green: unit tests, lint, debug APK, signed release APK.
- [ ] Download the signed `app-release` artifact and install it on the reference phone.
- [ ] Confirm the certificate SHA-256 matches the value above.
- [ ] Open the app from a clean install: Shizuku permission prompt appears automatically.
- [ ] Start a drain period, wait, refresh, and confirm the timestamp and elapsed duration change.
- [ ] Restrict one non-protected app, confirm "Applied and verified", then Revert it.
- [ ] Confirm the star prompt appears once after the first successful fix.
- [ ] Smoke-test Diagnostics, Standby, Vault, and Console.
- [ ] Upload the six phone screenshots to `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- [ ] Check README links and social preview.

### GitHub Release

1. Open the latest green CI run on `main`.
2. Download `app-release` and rename the APK to `pulse-battery-v1.0.0.apk`.
3. Create tag `v1.0.0` on the `main` branch.
4. Create release title `PULSE // BATTERY 1.0.0`.
5. Attach the APK and paste the release notes below.
6. Publish as the latest release.

Suggested notes:

```text
First release of the native PULSE // BATTERY app.

- Overnight drain report with per-app alarm wakeups and hourly rate
- One-tap Restrict and verified Revert
- Battery, wake lock and top alarm diagnostics
- Standby bucket and Doze whitelist control
- APK Vault export and restore
- Shizuku shell console with read-only quick commands
- English-only, no root, no internet permission, no ads, no trackers

Requires Android 8.0+ and Shizuku.

Release certificate SHA-256:
3f8c1e2df2deaf9d561a8fdce3c667ed0ceda972622f20d006a1b6098c2a2512
```

Never replace or rotate the release keystore after publishing. Back it up separately from its password.

## 3. Where to submit

### IzzyOnDroid

Links:
- Inclusion policy: https://izzyondroid.org/docs/general/AppInclusionPolicy/
- New app process: https://izzyondroid.org/contributing/NewAppInclusions/
- Repository: https://codeberg.org/IzzyOnDroid/repomaker

Process:
1. Publish a tagged GitHub release with a signed APK under 30 MB.
2. Confirm Fastlane metadata contains `title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/1.txt`, `icon.png`, and phone screenshots.
3. Open a new inclusion request in the IzzyOnDroid repository using their current issue template.
4. Include the project URL, release URL, license, package id, version name/code, APK SHA-256, and a short statement about permissions.
5. Be transparent that AI was used for research/debugging and implementation support. The policy allows documentation help and read-only research, but rejects vibe-coded code. Do not claim something untrue. Add human review notes and device test evidence.

Suggested request text:

```text
I am the developer of PULSE // BATTERY and request inclusion.

Repository: https://github.com/kreza6173-pixel/pulse-battery
Release: https://github.com/kreza6173-pixel/pulse-battery/releases/tag/v1.0.0
Package: io.github.kreza6173pixel.pulsebattery
Version: 1.0.0 (1)
License: MIT
APK SHA-256: 3f8c1e2df2deaf9d561a8fdce3c667ed0ceda972622f20d006a1b6098c2a2512

PULSE uses Shizuku to inspect battery-related system state, manage standby/Doze controls,
and create an overnight alarm-wakeup report. It has no INTERNET permission, no ads, and no tracker.
The release APK is signed with the developer's stable release key and is reproducibly built by the
public GitHub Actions workflow from the tagged source.

On-device testing: Xiaomi, Android 16, Shizuku shell uid 2000. Tested diagnostics, report, verified
standby Restrict/Revert, Doze controls, APK Vault restore, and console.
```

Important: IzzyOnDroid currently has a strict AI policy. Read it before submitting and answer its AI fields honestly. If the policy rejects the current development history, do not hide it. The app can still be distributed through GitHub Releases, F-Droid review, and other channels.

### F-Droid

Links:
- Inclusion overview: https://f-droid.org/docs/Inclusion_Policy/
- Request repository: https://gitlab.com/fdroid/rfp

Process:
1. Publish the stable tag and release first.
2. Open one request in the F-Droid request-for-packaging repository.
3. Include the GitHub URL, tag, package id, license, version, and why the app belongs in F-Droid.
4. Expect a review of build reproducibility, dependencies, permissions, metadata, and source tarball. Do not use prebuilt binaries in the source tree.
5. Wait for the review. Do not change application id or signing identity during the review.

Suggested request text:

```text
[PULSE // BATTERY](https://github.com/kreza6173-pixel/pulse-battery)

Open-source MIT Android app for battery diagnostics and overnight alarm-wakeup reports.
Uses Shizuku for system access without root. Includes standby/Doze controls, APK Vault, and a shell console.
No INTERNET permission, ads, account, or tracker. Native Kotlin/Compose project with a public Gradle build.

Package: io.github.kreza6173pixel.pulsebattery
Initial version: 1.0.0 (1)
```

### awesome-shizuku

Repository: https://github.com/timschneeb/awesome-shizuku

Open an issue or pull request according to the repo's current contribution rules. Suggested entry:

```markdown
- [PULSE // BATTERY](https://github.com/kreza6173-pixel/pulse-battery) - Battery diagnostics, overnight drain report, standby/Doze controls and APK Vault. **[FOSS]**
```

Place it under the power management or software management section, whichever the maintainers prefer.

### AlternativeTo

Website: https://alternativeto.net/

Create a listing for PULSE // BATTERY with:
- URL: https://github.com/kreza6173-pixel/pulse-battery
- Platform: Android
- License: Open Source, MIT
- Tags: Battery Monitor, Battery Saver, System Utility, Android, Shizuku
- Alternatives: Naptime, GSam Battery Monitor, BetterBatteryStats, App Manager
- Description: "Find which apps wake your phone overnight, then restrict the worst offender with one verified tap. No root, no ads, no tracking."

Use the official GitHub release as the download link, not a random mirror.

### Launch posts

Post only after the GitHub release is live and the APK has been tested from the release page.
Use the same factual description, not exaggerated battery claims. Mention Android version and Shizuku.

A good short post:

```text
PULSE // BATTERY is an open-source Android battery utility built around Shizuku.

It records per-app alarm wakeups over a period, shows which apps woke your phone overnight,
and lets you move one offender to Android's Restricted standby bucket with a read-back verification.

Also includes battery/wakelock/alarm diagnostics, Standby & Doze controls, and an APK Vault.
No root, no internet permission, no ads, no tracking.

Android 8.0+ | MIT | https://github.com/kreza6173-pixel/pulse-battery/releases/tag/v1.0.0
```

## 4. Reusable Android engineering playbook

### Start with a probe, not a feature

Before writing UI, run the smallest real-device command and paste its exact output into a pure parser test. Android and OEM output is not a stable API. A parser that only saw a blog example is not ready.

### Pin the toolchain

Keep AGP, Kotlin, Gradle, SDK and major libraries fixed. Upgrade in a separate maintenance change with a green baseline. Never mix an upgrade with a feature.

### Treat shell as an untrusted boundary

Validate package names with a strict regex, quote every value, use fixed command templates, filter output before crossing the binder, and cap timeouts. Never interpolate raw user input.

### Verify every write

A zero exit code means the command ran, not that Android applied it. Read the state back and show the resulting state. Every destructive action needs an explicit confirmation and a reversible path where possible.

### Make the device the authority

The CI machine proves compilation and unit tests. The phone proves Shizuku behavior, OEM behavior, permissions, package manager behavior, and visual quality. Keep both checklists.

### Design for the failure path

Plan disconnected, denied permission, binder death, timeout, empty output, reboot, counter reset, unknown bucket, protected package, and stale backup states before the happy path.

### Keep the UI honest

Call a wakeup count a wakeup count, not mAh. Mark heuristics. Never imply a background service when the app only reads Android-maintained counters.

### Release hygiene

Use a fresh neutral release certificate before v1.0, publish SHA-256, keep the keystore forever, test the signed APK rather than debug, and never put secrets in Git, Variables, screenshots, or chat.

### Ship in slices

One feature per push, one device test per slice, one short evidence loop. For later projects, copy the M0-M2 template instead of rebuilding Shizuku and CI from scratch.

## 5. Future project roadmap

1. **PULSE v1.0:** publish GitHub release, submit listings, collect real user feedback.
2. **Template repo:** extract Shizuku state machine, UserService, console, UI helpers, CI, signing guide, and test conventions. Keep it copied, not a shared runtime dependency.
3. **VOID // APPS:** combine Cyber App Manager, Autostart, Privacy Audit, Purge, and Install. Build around a shared app inventory, capability matrix, and read-back verification.
4. **VOID // WALL:** separate firewall project. Treat network blocking as high risk, add an emergency disable path, and test on real devices before enabling enforcement.
5. **PULSE M5c:** optional debuggable-app data backup, then root support only after real-root tests. Keep unsupported root actions visible but locked.
6. **void-pulse:** hold until a native audio service and a real test plan exist.

## 6. Quick incident checklist

When something breaks: capture the exact screen, copy the result or console output, note device/Android/Shizuku versions, identify the first failing `e:` line if CI is red, reproduce with one command, and change one thing at a time. Never paste a keystore, password, token, seed phrase, or private key.
