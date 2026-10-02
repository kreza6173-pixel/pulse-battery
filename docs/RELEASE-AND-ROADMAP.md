# PULSE // BATTERY 1.0: release map and Android playbook

Updated: 2026-10-02

This is the practical record for continuing the project without relying on chat history.

## 1. Current release state

- Native Kotlin + Jetpack Compose app, package `io.github.kreza6173pixel.pulsebattery`.
- `native-app-v0` merged into `main`; release `v1.0.0` published on GitHub with the signed APK.
- Version `1.0.0` (code 1), Android 8.0+ (SDK 26), target/compile SDK 36.
- Release certificate `CN=PULSE, O=kreza6173-pixel`, SHA-256 `3f8c1e2df2deaf9d561a8fdce3c667ed0ceda972622f20d006a1b6098c2a2512`.
- English-only. No `INTERNET` permission. Dependency-info block disabled for F-Droid style checks.
- Verified on Xiaomi / Android 16 / Shizuku shell uid 2000.

## 2. Distribution channels

### GitHub Releases + Obtainium (primary)

The release page is the canonical download. Obtainium users can add the repo URL directly and get updates from GitHub Releases. Always bump `versionCode` and attach a signed APK for every release.

### F-Droid

- Inclusion policy: https://f-droid.org/docs/Inclusion_Policy/
- Requests for packaging: https://gitlab.com/fdroid/rfp/-/issues (needs a GitLab account)

F-Droid builds the app from source itself. Expect weeks, and questions about build, dependencies and permissions. Answer questions about tooling honestly if asked.

Request text:

```text
PULSE // BATTERY
https://github.com/kreza6173-pixel/pulse-battery

Open-source (MIT) Android battery utility built on Shizuku. Overnight per-app alarm-wakeup report,
one-tap Restrict with read-back verification, battery/wake lock/alarm diagnostics, Standby & Doze
controls, APK Vault, shell console. No INTERNET permission, no ads, no trackers.

Package: io.github.kreza6173pixel.pulsebattery
Version: 1.0.0 (1), tag v1.0.0
Build: Gradle, module android-app/app, no prebuilt binaries
Fastlane metadata: fastlane/metadata/android/en-US
```

### IzzyOnDroid

- Policy: https://izzyondroid.org/docs/general/AppInclusionPolicy/
- Requests: https://codeberg.org/IzzyOnDroid/repodata/issues

IzzyOnDroid rejects apps whose code was written fully or partly by generative AI ("vibe-coded" apps). This app's code was written with AI assistance, so an honest request would most likely be rejected. Do not submit while hiding that. Revisit only if the policy changes.

### awesome-shizuku

https://github.com/timschneeb/awesome-shizuku (pull request to README.md)

```markdown
- [PULSE // BATTERY](https://github.com/kreza6173-pixel/pulse-battery) - Overnight battery drain report with one-tap standby restrict, wake lock and alarm diagnostics, Doze controls and APK backup. `MIT`
```

Section: Power management.

### AlternativeTo

https://alternativeto.net/ : add app, Android, open source (MIT), link to GitHub Releases. Alternatives: Naptime, BetterBatteryStats, GSam Battery Monitor.

### Launch posts

Post after the release page works. Be factual, mention Shizuku and Android 8.0+, and say up front that it was built with AI assistance and tested on a real device. Communities react far worse to discovering it later.

```text
PULSE // BATTERY: open-source Android battery utility built on Shizuku.

Start a period before bed; in the morning it ranks the apps that woke your phone (alarm wakeups,
per hour) and lets you move one to the Restricted standby bucket, verified by reading it back,
with one-tap revert. Also battery/wake lock/alarm diagnostics, Standby & Doze controls, APK Vault.

No root, no internet permission, no ads, no tracking. MIT.
Built with AI assistance, tested on Xiaomi / Android 16.
https://github.com/kreza6173-pixel/pulse-battery/releases/tag/v1.0.0
```

## 3. Reusable Android engineering playbook

- **Probe before feature:** run the smallest real-device command first and turn its exact output into a parser test.
- **Pin the toolchain:** upgrades are their own change, never mixed with features.
- **Shell is a boundary:** strict package regex, quoting, fixed templates, on-device filtering, timeouts.
- **Verify every write:** exit 0 is not success; read the state back and offer revert.
- **Two authorities:** CI proves compilation and tests; the phone proves behavior.
- **Design failure first:** denied permission, binder death, timeout, empty output, reboot, counter reset, protected packages.
- **Honest UI:** a wakeup count is not mAh; label heuristics.
- **Release hygiene:** neutral certificate before v1.0, publish SHA-256, never lose or rotate the keystore, secrets only in Secrets.
- **Ship in slices:** one feature per push, one device test per slice.

## 4. Next projects

1. Template repo (Shizuku core, UserService, console, UI helpers, CI, signing).
2. VOID // APPS (Cyber App Manager, Autostart, Privacy Audit, Purge, Install).
3. VOID // WALL, separate, with an emergency disable path.
4. PULSE M5c (debuggable data backup); root only after real-root tests.
5. void-pulse on hold.

## 5. When stuck

Capture the screen, copy the output, note device/Android/Shizuku versions, find the first `e:` line if CI is red, reproduce with one command, change one thing at a time. Never share a keystore, password, token, seed phrase or private key.
