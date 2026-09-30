# PROGRESS

## Done
- Spec extracted from legacy README + webui/script.js (docs/SPEC.md).
- Architecture & milestones defined (docs/PLAN.md).
- Decisions logged with version sources (docs/DECISIONS.md).
- New branch `native-app-v0` created.
- Toolchain verified: AGP 9.4.1, Gradle 9.6.0, JDK 17, Kotlin 2.2.10, Compose BOM 2026.09.00.
- (M0 in progress) Native Compose app skeleton under /android-app/ + CI workflow.

## Next
- Finish M0: Compose app module, minimal resources + launcher icon, unit test, CI workflow.
- Push `native-app-v0`, run CI, confirm green.

## Known issues
- None yet (M0 pending).

## UNVERIFIED (command-level — to be validated on-device via in-app console)
- `am get-standby-bucket` / `am set-standby-bucket <pkg> <bucket>` (M4).
- `dumpsys deviceidle whitelist [+|-]<pkg>` (M4).
- `dumpsys deviceidle force-idle` / `unforce` (M4).
- `pm install-multiple -r <apks>` permission on Android 13+ shell uid (M5).
- `restorecon -R /data/data/<pkg>` availability in Shizuku shell/root context (M5).
