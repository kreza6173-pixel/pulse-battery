# PULSE // BATTERY: handoff guide

> For when work continues with another model or agent. Read the whole file first.
> If anything here disagrees with the repo or a CI log, **the repo and the log win**.
> Updated: 2026-10-03.

---

## 1. Status (with evidence)

| Step | Status | Evidence |
|---|---|---|
| M0 skeleton + CI | done | green run `36795807603` |
| M1 Shizuku state machine | done | PERMISSION_NEEDED to READY, uid 2000 |
| M2 UserService + console | done | connects in 3 s, `id` gives `uid=2000(shell)`, cancel gives exit 124, redaction |
| M3 diagnostics | done | battery, wake locks attributed correctly, top 10 alarms |
| M4 Standby and Doze | done | `app.yuki` working_set to rare and back; whitelist Added/Removed; force-idle gives deep IDLE, unforce ACTIVE; 505 apps |
| M5a/b APK Vault | done | export of 3 apps (up to 128 MB), delete, restore `app.morphe.manager` gives `Success` |
| M5c data backup | paused | debuggable apps via `run-as` only (manual test worked). Root **deliberately locked** |
| Drain report | done | overnight period verified on the phone |
| Release signing | done | CI builds signed `app-release`, cert `CN=PULSE, O=kreza6173-pixel` |
| v1.0.0 release | **done** | merged into `main` (#1), tag `v1.0.0`, GitHub Release 2026-10-02 |
| Signed release APK on the phone | **to verify** | only debug builds were phone-tested; release has `debuggable=false` on the UserService |

Reference device: **Xiaomi, Android 16 (SDK 36), Shizuku as uid 2000, device locale fa (RTL)**.
The app is English-only and pins en-US in `MainActivity.attachBaseContext`.
Package: `io.github.kreza6173pixel.pulsebattery`.

**Branch: `main` only.** `native-app-v0` is frozen and behind `main`; never push to it.
It can be deleted at any time (PR #1 keeps its history and offers Restore branch).

---

## 2. Architecture facts that must not change

1. **The UserService is a binder, not an `android.app.Service`.** `ShizukuExecService : IUserService.Stub()` with a no-arg constructor. The Shizuku server instantiates it by reflection and casts it to `IBinder` (`RikkaApps/Shizuku-API`, `server-shared/.../server/UserService.java`).
2. The service is **not** declared in the manifest. The manifest has no INTERNET permission and must not get one.
3. `processNameSuffix("user_service")` is mandatory in 13.1.5. One `UserServiceArgs` instance for bind, peek and unbind. `debuggable` follows `FLAG_DEBUGGABLE`.
4. AIDL: `destroy() = 16777114`, `exec = 1`, `cancel = 2`. Bump `USER_SERVICE_VERSION` on any service change.
5. The only way to run a command: `ExecBridge.execBlocking` from `Dispatchers.IO`.
6. Service output is capped at 64 KiB; filter on the device with `grep`.
7. Parsers are pure and tested with **real device output**.
8. Shell text uses `LtrMonoText`. Numbers next to units are wrapped in `\u2066 ... \u2069`.
9. Every write is followed by a read-back and is reported as applied only if it matches.
10. Package names are regex-checked and quoted with `ShellQuoting.quote` before the shell.
11. Pinned versions: AGP 8.13.1, Kotlin 2.2.21, Gradle 8.13, BOM 2025.12.00, SDK 36, Shizuku 13.1.5. **Never SDK 37 or AGP 9.**
12. Release signing only from CI secrets (`docs/RELEASE.md`). Workflow files need the repo owner to edit them.
13. Every release bumps `versionCode` and is signed with the **same** keystore. Losing it ends updates for existing installs.

---

## 3. Checklist for every push

- [ ] Read the whole diff once.
- [ ] One goal per push.
- [ ] Only the **latest run** matters: green plus the `app-debug` artifact.
- [ ] Red: only the `e:` lines from the `build-log` artifact. The **first** `e:` is the cause.
- [ ] Green CI means it compiles and unit tests pass, not that it works.
- [ ] New strings in `values/` only (English). No apostrophes unless escaped.
- [ ] After phone confirmation, update the table in section 1.

---

## 4. Traps that already cost a run or a test

| Trap | Do this instead |
|---|---|
| A `*job*` tag sample inside a block comment | star + slash closes the comment; put samples in test strings |
| `as? Generic` without type arguments | `if (x is DiagResult.Ok) x.value` |
| `CharArray` instead of `CharSequence` | `String(chunk, 0, n)` |
| `ExecutorService.submit { }` | use `synchronized` |
| `Int.coerceIn(Long, Long)` | call `toLong()` first |
| chaining `Intent.setPackage(...)` | it returns void; separate statement |
| `SmallTopAppBar` | not in material3 1.4.0; `TopAppBar` + `@OptIn` |
| reversing `ConsoleHistory.items` | already newest-first |
| `SelectionContainer` in a `LazyColumn` | did not work on the phone; `CopyShareButtons` |
| `pm install /sdcard/...` | system_server cannot read fuse; install from `/data/local/tmp` |
| `pm install-multiple` | adb-only; on device use `pm install -r base.apk split*.apk` |
| APK path in `upload-artifact` | `android-app/**/build/outputs/apk/debug/app-debug.apk` |
| pushing `.github/workflows/*` via the API | needs the `workflow` scope; ask the owner to paste it |
| secrets saved under Variables | must be under Secrets, or the workflow sees `null` |
| trusting an agent report or the docs | only runs, logs and phone tests count |

---

## 5. Distribution status

| Channel | Status |
|---|---|
| GitHub Releases (+ Obtainium) | **done**, `v1.0.0` |
| awesome-shizuku | **done**, PR timschneeb/awesome-shizuku#162 merged 2026-10-02 (Power management) |
| IzzyOnDroid | **not submitted, by decision.** Its policy rejects code written fully or partly by generative AI. Do not submit while hiding that. Revisit only if the policy changes |
| F-Droid RFP | pending, needs the owner's GitLab account; request text in `RELEASE-AND-ROADMAP.md` |
| AlternativeTo, launch posts | optional, owner; texts in `RELEASE-AND-ROADMAP.md` (always state AI assistance up front) |

Owner housekeeping still open: keystore + passwords backed up offline in two places, phone test of the signed release APK, social preview image.

---

## 6. Project roadmap (updated 2026-10-03)

The owner's module repos are consolidated into **two native apps total**:

1. **PULSE // BATTERY** (this repo): shipped, maintenance only. M5c and root stay paused/locked.
2. **VOID // APPS**: one app, built in the Cyber-app-manager repo (to be renamed `void-apps`), reusing this repo's M0-M2 core by copy. It absorbs:
   - Cyber App Manager (registry, freeze, remove for user, snapshots, debloat)
   - void-autostart (boot receivers, background AppOps)
   - privacy-audit (runtime permissions, special-access AppOps)
   - void-pulse, notification part only (listener access, DND access, per-app notification mute)
   - pulse-install (session-based APK/APKS/XAPK/APKM install)
   - void-purge, only the parts that verifiably work as shell uid
   - VOID-WALL, non-root part only (Chain3 per-app network block, netpolicy background data)

Dropped on purpose: AI advisors and VirusTotal (no INTERNET permission is a core promise), the non-functional EQ engine, the DNS filter that never filtered, root iptables/tc until a real rooted tester exists.
No template repo: with only one new app it is not worth maintaining.
`android-app-hub` is unrelated and out of scope.

---

## 7. Ready prompt for an agent

```text
Repo kreza6173-pixel/pulse-battery, branch main. Read docs/HANDOFF.md first,
fully. It is the source of truth together with the code; CI logs beat both.

TASK: <one step>

Hard rules:
- Do NOT change toolchain versions. Never SDK 37, never AGP 9.
- Do NOT touch ShizukuExecService/ExecBridge/IUserService.aidl unless the task says so.
  If you change the service, bump USER_SERVICE_VERSION.
- New parsers: pure Kotlin + unit test from REAL phone output pasted below.
- Never put a star followed by a slash inside a block comment.
- Strings in values/ only, English. Shell text rendered with LtrMonoText.
- Every write command is followed by a read-back; report "applied" only if it matches.
- Re-read the whole diff before pushing.
- Push: git push origin HEAD:main
- Report: run id, green/red, artifact app-debug exists. If red, quote ONLY e: lines.
- Nothing "works" until the owner confirms on the phone. End with exactly what to tap
  and which output to copy back.

Real phone output for this task:
<paste here>
```
