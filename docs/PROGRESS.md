# PROGRESS

Updated 2026-10-03. Current state lives in `docs/HANDOFF.md` section 1; this file is the
short milestone record. Detailed CI histories and root-cause write-ups are in
`docs/DECISIONS.md` (decisions 0-35) and `docs/LESSONS.md`. CI is the only compiler in
this workflow; nothing was ever built locally.

| Milestone | Result | Key evidence / lesson |
|---|---|---|
| M0 toolchain | green `36795807603` | SDK 37 / AGP 9 are dead ends; pinned to compose-samples `v2025.12.00`. Green run can ship no APK: `if-no-files-found: error` |
| M1 Shizuku state machine | phone-verified | READY with uid 2000. `Intent.setPackage` returns void |
| M2 UserService + console | phone-verified | `processNameSuffix` mandatory in 13.1.5; the UserService must extend `IUserService.Stub` (Shizuku casts it to IBinder). Five red runs on JVM/Kotlin type identity |
| M3 diagnostics | phone-verified | battery block parser stops at Xiaomi extras; wake locks attributed from `*job*` tags; `dumpsys alarm` filtered with grep (64 KiB cap) |
| M4 standby + Doze | phone-verified | every bucket/whitelist write read back; raw bucket codes shown; only EXEMPTED/NEVER locked |
| M5a/b APK Vault | phone-verified | system_server cannot read `/sdcard` (fuse): stage in `/data/local/tmp`; `install-multiple` is adb-only, use `pm install -r <all apks>` |
| M5c data backup | paused | `run-as` works for debuggable apps; root locked by decision |
| Drain report | phone-verified | alarm wakeups per package from Alarm Stats, reboot detection, one-tap Restrict/Revert |
| Release 1.0.0 | released 2026-10-02 | signed in CI from Secrets; English-only; no INTERNET; dependency-info block dropped for F-Droid |

## Still unverified
- The **signed release APK** on the phone (only debug builds were tested).
- `lint` runs with `abortOnError = false`, so green does not mean lint-clean.
- `./gradlew` is never exercised in CI (no wrapper jar committed by design).
