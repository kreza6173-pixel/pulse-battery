# Root mode

Branch: `root-mode-v1`. Target version: 1.1.0 (versionCode 2).

This is the record of the root track: what was added, which command each control runs, how
the result is verified, and what was proposed but deliberately left out.

## The one rule everything follows

A control is only reported as applied when a **read-back** returns the value that was asked
for. Every repository in this track writes, then reads, then compares. When the system
silently reverts a write, the UI says so instead of claiming success. No exceptions were made
for root: root widens what can be attempted, it never widens what may be claimed.

## Access layer

`access/AccessMode.kt` resolves one value, `rootAvailable`, from observable facts only:

- the provider that answers the binder API: the Sui module, or the Shizuku manager app
- whether the service reached READY
- the real uid the service runs with, via `Shizuku.getUid()`

`rootAvailable` is true only when that uid is 0. An installed root manager proves nothing
about this process, so it is never used as a signal. Priority: Sui, then the Shizuku manager
app, then nothing.

Reference device: Redmi Note 14 4G (`tanzanite`, `mt6789`), HyperOS 3, Android 16 / SDK 36,
KernelSU with the Sui module 13.5.4.3. Verified on device: `uid 0 (root)`, provider `Sui`.

## What the track added

| Area | What it does | Commands / nodes |
| --- | --- | --- |
| Charge protection | The three HyperOS modes: charge fully, intelligent charging, battery protection | secure setting `security_pc_secure_protect_mode_key`, values 0 / 1 / 2 |
| Battery health | Read-only kernel view: capacity, cycles, voltage, current, temperature, technology | `/sys/class/power_supply/*` |
| Charge gate | Pause and resume charging now | `/sys/class/power_supply/battery/input_suspend` |
| Charge limit | Optional opt-in service that gates charging at a chosen level and recharges below a floor | the same node, driven by a foreground service |
| CPU by app | Per-uid CPU time attributed to packages | `/proc/uid_cputime/show_uid_stat` |
| Standby and Doze | Buckets, the Doze whitelist, forced idle, plus the root override below | `am set-standby-bucket`, `am get-standby-bucket`, `dumpsys deviceidle` |
| Wakelocks and background | Per-app wakelock and background-work blocking | `cmd appops set <pkg> WAKE_LOCK\|RUN_IN_BACKGROUND\|RUN_ANY_IN_BACKGROUND ignore` |

### Capability checks, not assumptions

The HyperOS protection mode is capability-checked: another HyperOS 3 device (Poco F5) has no
mode 2, and an absent setting key reads as intelligent charging rather than as an error. The
charge nodes are probed before use: on `tanzanite`, `charge_control_limit` is a thermal
current **level** (0 to 16), not a percentage, so it is never written as one, and
`battery_charging_enabled` / `charging_enabled` do not exist at all.

### The standby root override

`standby/StandbyRootPolicy.kt` first names why a package cannot leave its bucket:

- **user Doze whitelist**: removable from a shell, so it is dropped before the bucket write
- **ROM whitelist** (`system`, `system-excidle`): rebuilt at every boot from the ROM's
  `/etc/sysconfig` XML. A shell cannot remove it, root included; only an `/etc` overlay could.
  The write is still attempted, the limitation is disclosed, and the read-back decides.
- **the framework itself**: no whitelist entry explains the exemption (carrier app, headless
  system app, active reason). Attempted, verified, and reported honestly.

`exempted` and `never` are never offered as targets: the system derives them, a shell cannot
set them. A failed bucket write restores a whitelist entry that was dropped, so the device is
left as it was found.

### The appops screen needs no root

`cmd appops` is settable with the shell uid, so this screen works in shell mode too. It is
offered with the breakage stated on screen: WAKE_LOCK set to `ignore` makes every wakelock
that app takes a silent no-op, which can break its alarms, downloads, music and messaging.
An op that has never been used is not printed by `appops get`, so an absent entry and the
word `default` are folded into the same value; the read-back accepts both.

## Dropped from the original proposal, with reasons

1. **Kernel wakelock nodes** (`/sys/power/wake_lock`, `wake_unlock`). These create and release
   *kernel* wakelocks by name. They cannot block a wakelock an app takes through
   PowerManager. Replaced by the AppOps `WAKE_LOCK ignore` path, which actually does that.
2. **CPU governor writes.** The HyperOS performance and thermal services own
   `scaling_governor` and rewrite it within seconds. A control that cannot hold its value
   fails the read-back rule by design, so it was not shipped.
3. **iptables / traffic rules.** Not a battery feature. It belongs to the other project.
4. **`kill -9`.** Strictly weaker than `am force-stop`, which also tells the framework not to
   restart the process. No reason to ship the weaker one.
5. **A resident root daemon.** Unnecessary: when Sui or Shizuku is started as root, the user
   service already runs as uid 0. A second privileged process would add attack surface and
   buy nothing.
6. **Parsing `batterystats.bin`.** A private binary format whose layout changes between
   Android versions. `dumpsys batterystats` text and `/proc` are parsed instead, with unit
   tests built from real device output.
7. **`charge_control_limit` as a percentage.** Verified on the reference device to be a
   current level, not a percentage. Writing a percentage there would throttle current and
   look like a working limit while never stopping the charge.

## Testing

Pure logic is unit-tested on the JVM: `StandbyRootPolicyTest`, `AppOpsParsersTest`, plus the
existing parser suites. Parsers are built from real output captured on the reference device.
UI and device behaviour are verified by hand on the phone, one feature at a time.
