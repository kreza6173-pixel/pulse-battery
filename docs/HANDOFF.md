# PULSE // BATTERY — راهنمای ادامهٔ کار (Handoff)

> برای وقتی که کار با مدل یا ایجنت دیگری ادامه پیدا می‌کند. اول کل فایل را بخوان.
> اگر چیزی اینجا با ریپو یا لاگ CI نمی‌خواند، **ریپو و لاگ مرجع‌اند**.
> توضیحات فارسی، پرامپت‌ها انگلیسی. به‌روزشده: 2026-10-02.

---

## ۱. وضعیت فعلی (با شاهد)

| مرحله | وضعیت | شاهد روی گوشی |
|---|---|---|
| M0 اسکلت + CI | ✅ | run سبز `36795807603` |
| M1 ماشین حالت Shizuku | ✅ | PERMISSION_NEEDED ← READY، uid 2000 |
| M2 UserService + کنسول | ✅ | اتصال در ۳ ثانیه، `id` ← `uid=2000(shell)`، لغو ← exit 124، redaction |
| M3 تشخیص | ✅ | باتری، wakelock با نسبت درست (`com.android.vending`)، ۱۰ آلارم برتر |
| M4 Standby و Doze | ✅ | `app.yuki` working_set ← rare و برگشت؛ فهرست سفید Added/Removed؛ force-idle ← deep IDLE، unforce ← ACTIVE؛ ۵۰۵ اپ |
| M5a/b گاوصندوق APK | ✅ | خروجی ۳ اپ (تا ۱۲۸ مگابایت)، حذف، بازیابی `app.morphe.manager` ← `Success` |
| M5c پشتیبان داده | ⏸ | فقط debuggable با `run-as` (تست دستی کار کرد). روت **عمداً قفل** |
| Final | 🔄 در حال انجام | آیکون + صفحهٔ درباره/مجوزها انجام شد؛ بقیه بخش ۵ |

دستگاه مرجع: **Xiaomi، Android 16 (SDK 36)، Shizuku با uid 2000، فارسی/RTL**.
شاخه: `native-app-v0`. package: `io.github.kreza6173pixel.pulsebattery`.

---

## ۲. حقایق معماری که نباید دست بخورند

1. **UserService خودش binder است، نه `android.app.Service`.** `ShizukuExecService : IUserService.Stub()` با سازندهٔ بی‌آرگومان. سرور Shizuku کلاس را با reflection می‌سازد و به `IBinder` کست می‌کند (`RikkaApps/Shizuku-API` ← `server-shared/.../server/UserService.java`).
2. سرویس در manifest تعریف **نمی‌شود**. manifest مجوز INTERNET ندارد و نباید داشته باشد.
3. `processNameSuffix("user_service")` در 13.1.5 اجباری است. یک نمونهٔ `UserServiceArgs` برای bind/peek/unbind.
4. AIDL: `destroy() = 16777114`، `exec = 1`، `cancel = 2`. با هر تغییر سرویس، `USER_SERVICE_VERSION` را بالا ببر.
5. تنها مسیر اجرای دستور: `ExecBridge.execBlocking` از `Dispatchers.IO`.
6. خروجی سرویس حداکثر 64 KiB؛ روی گوشی با `grep` فیلتر کن.
7. parserها خالص و با خروجی **واقعی گوشی** تست می‌شوند.
8. متن فنی با `LtrMonoText`. هر عدد داخل رشتهٔ فارسی بین `\u2066` و `\u2069`.
9. هر دستور نوشتنی بعد از اجرا با دستور خواندنی **تأیید** می‌شود و فقط آن‌وقت «اعمال و تأیید شد».
10. نام بسته قبل از shell هم با regex چک و هم با `ShellQuoting.quote` کوت می‌شود.
11. نسخه‌ها ثابت‌اند: AGP 8.13.1، Kotlin 2.2.21، Gradle 8.13، BOM 2025.12.00، SDK 36، Shizuku 13.1.5. **هرگز SDK 37 یا AGP 9.**

---

## ۳. چک‌لیست هر push

- [ ] diff را کامل یک بار بخوان.
- [ ] هر push یک هدف. حداکثر ۲ push در هر مرحله مگر با اجازهٔ رضا.
- [ ] فقط **run آخر** مهم است: سبز + آرتیفکت `app-debug`.
- [ ] قرمز: فقط خطوط `e:` از آرتیفکت `build-log`. **اولین** `e:` علت است.
- [ ] CI سبز یعنی کامپایل و تست واحد، نه «کار می‌کند».
- [ ] رشتهٔ جدید در `values/` **و** `values-fa/`، بدون آپاستروف در انگلیسی.
- [ ] بعد از تأیید گوشی، جدول بخش ۱ را به‌روز کن.

---

## ۴. تله‌هایی که run یا تست سوزاندند

| تله | راه درست |
|---|---|
| نمونهٔ تگ (ستاره-job-ستاره-اسلش) داخل `/** */` | ستاره+اسلش کامنت را می‌بندد؛ نمونه را در تست به‌صورت string بگذار |
| `as? Generic` بدون نوع | `if (x is DiagResult.Ok) x.value` |
| `CharArray` به جای `CharSequence` | `String(chunk, 0, n)` |
| `ExecutorService.submit { }` | از `synchronized` استفاده کن |
| `Int.coerceIn(Long, Long)` | اول `toLong()` |
| `Intent.setPackage(...)` زنجیری | void است؛ جدا بنویس |
| `SmallTopAppBar` | در material3 1.4.0 نیست؛ `TopAppBar` + `@OptIn` |
| `ConsoleHistory.items` را برعکس کردن | خودش newest-first است |
| `SelectionContainer` در `LazyColumn` | روی گوشی کار نکرد؛ `CopyShareButtons` |
| عدد فارسی کنار `·` یا واحد لاتین | `\u2066%1$d\u2069` |
| `pm install /sdcard/...` | system_server به fuse دسترسی ندارد؛ از `/data/local/tmp` نصب کن |
| `pm install-multiple` | فقط دستور adb است؛ روی گوشی `pm install -r base.apk split*.apk` |
| مسیر APK در `upload-artifact` | `android-app/**/build/outputs/apk/debug/app-debug.apk` |
| اعتماد به گزارش ایجنت یا docs | فقط run، لاگ و تست گوشی شاهدند |

---

## ۵. باقی‌ماندهٔ Final (به ترتیب)

1. ~~آیکون adaptive + monochrome~~ ✅ (تأیید بصری روی گوشی لازم)
2. ~~صفحهٔ درباره و مجوزها~~ ✅
3. `debuggable` سرویس فقط در بیلد debug (از `ApplicationInfo.FLAG_DEBUGGABLE`).
4. بیلد **release امضاشده** در CI: کلید در GitHub Secrets (رضا باید یک بار keystore بسازد؛ راهنما جدا). `versionCode`/`versionName` = 1 / 1.0.0.
5. `fastlane/metadata/android/{en-US,fa-IR}`: عنوان، توضیح کوتاه و بلند، اسکرین‌شات‌ها.
6. README جدید (اپ نیتیو به جای ماژول) + ادغام `native-app-v0` در `main`.
7. تگ `v1.0.0` و GitHub Release با APK امضاشده، بعد درخواست IzzyOnDroid.

---

## ۶. نقشهٔ راه پروژه‌ها (توافق‌شده)

1. **PULSE // BATTERY v1.0** (همین).
2. **ریپوی template** از M0 تا M2: هستهٔ Shizuku، کنسول، `ui/common`، CI. کپی، نه کتابخانهٔ مشترک.
3. **VOID // APPS**: ادغام Cyber App Manager + Autostart + Privacy Audit + Purge + Install (همه لیست اپ + `pm`/`appops`).
4. **VOID // WALL**: فایروال، جدا (ریسک قطع اینترنت).
5. void-pulse فعلاً کنار (EQ سراسری سرویس نیتیو می‌خواهد).
6. قابلیت‌های روت: دیده شوند ولی قفل، تا وقتی یک کاربر روت واقعی با حالت «فقط نمایش دستور» تست کند.

---

## ۷. پرامپت آماده برای ایجنت (Kilo / Jules)

```text
Repo kreza6173-pixel/pulse-battery, branch native-app-v0. Read docs/HANDOFF.md first,
fully. It is the source of truth together with the code; CI logs beat both.

TASK: <one step from section 5>

Hard rules:
- Do NOT change toolchain versions. Never SDK 37, never AGP 9.
- Do NOT touch ShizukuExecService/ExecBridge/IUserService.aidl unless the task says so.
  If you change the service, bump USER_SERVICE_VERSION.
- New parsers: pure Kotlin + unit test from REAL phone output pasted below.
- Never put a star followed by a slash inside a block comment.
- New strings in values/ AND values-fa/. Numbers in fa strings wrapped in
  \u2066 ... \u2069. Shell text rendered with LtrMonoText.
- Every write command is followed by a read-back; report "applied" only if it matches.
- Re-read the whole diff before pushing. Max 2 pushes.
- Push: git push origin HEAD:native-app-v0
- Report: run id, green/red, artifact app-debug exists. If red, quote ONLY e: lines.
- Nothing "works" until I confirm on the phone. End with exactly what I should tap
  and which output to copy back.

Real phone output for this task:
<paste here>
```
