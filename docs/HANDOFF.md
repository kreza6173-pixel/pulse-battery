# PULSE // BATTERY — راهنمای ادامهٔ کار (Handoff)

> این فایل برای وقتی است که کار با مدل یا ایجنت دیگری ادامه پیدا می‌کند.
> اول کل فایل را بخوان. اگر چیزی اینجا با ریپو یا لاگ CI نمی‌خواند، **ریپو و لاگ مرجع‌اند**.
> توضیحات فارسی، پرامپت‌های ایجنت انگلیسی. به‌روزشده: 2026-10-02.

---

## ۱. وضعیت فعلی (با شاهد)

| مرحله | وضعیت | شاهد |
|---|---|---|
| M0 اسکلت + CI | ✅ تمام | run سبز `36795807603` |
| M1 ماشین حالت Shizuku | ✅ تمام | تست گوشی: PERMISSION_NEEDED ← READY، uid 2000 |
| M2 UserService + کنسول | ✅ تمام | گوشی: اتصال در ۳ ثانیه، `id` ← `uid=2000(shell)`، `sleep 30`+لغو ← exit 124، redaction کار می‌کند |
| M3 تشخیص (باتری، wakelock، آلارم) | ✅ تمام | گوشی: باتری ۸۳٪، wakelock به `com.android.vending` نسبت داده شد، ۱۰ آلارم برتر |
| M4 Standby و Doze | ⏳ بعدی | بخش ۵ |
| M5 Backup Vault | ⏳ | بخش ۵ |
| Final | ⏳ | بخش ۵ |

دستگاه مرجع تست: **Xiaomi، Android 16 (SDK 36)، Shizuku با uid 2000 (shell)، زبان فارسی/RTL**.
شاخهٔ کار: `native-app-v0`. package: `io.github.kreza6173pixel.pulsebattery`.

---

## ۲. حقایق معماری که نباید دست بخورند

1. **UserService خودش binder است، نه `android.app.Service`.** `ShizukuExecService : IUserService.Stub()` با سازندهٔ بی‌آرگومان.
   سرور Shizuku کلاس را با reflection می‌سازد و به `IBinder` کست می‌کند
   (منبع: `RikkaApps/Shizuku-API` ← `server-shared/.../server/UserService.java`). همین اشتباه M2 را چند روز قفل کرده بود.
2. **سرویس در manifest تعریف نمی‌شود.** تعریفش بی‌فایده و اگر exported باشد حفرهٔ امنیتی است.
3. `processNameSuffix("user_service")` در 13.1.5 **اجباری** است. یک نمونهٔ `UserServiceArgs` برای bind/peek/unbind.
4. AIDL: `destroy() = 16777114` (رزرو Shizuku)، `exec = 1`، `cancel = 2`. با هر تغییر سرویس، `USER_SERVICE_VERSION` در `ExecBridge` را یکی بالا ببر.
5. تنها مسیر اجرای دستور: `ExecBridge.execBlocking` (از `Dispatchers.IO`). هرگز `Shizuku.newProcess`.
6. خروجی سرویس حداکثر **64 KiB** است. دستورهای بزرگ را روی خود گوشی با `grep` فیلتر کن (مثل `dumpsys alarm | grep -A 40 "Top Alarms:"`).
7. parserها خالص (بدون Android) و با خروجی **واقعی گوشی** تست می‌شوند، نه نمونهٔ ساختگی. فایل‌ها: `diag/*Parser.kt` و `src/test/.../diag`.
8. متن فنی (دستور، خروجی، نام پکیج، تگ) همیشه با `LtrMonoText` (پوشهٔ `ui/common`). هر عدد/واحد داخل رشتهٔ فارسی بین `\u2066` و `\u2069`.
9. مجوز Shizuku روی گوشی کاربر فقط بعد از بازگشایی اپ اعمال می‌شود. دکمهٔ «بازگشایی برنامه» همین را حل می‌کند (تأییدشده).
10. نسخه‌ها ثابت‌اند: AGP 8.13.1، Kotlin 2.2.21، Gradle 8.13، BOM 2025.12.00 (material3 1.4.0)، SDK 36، Shizuku 13.1.5. **هرگز SDK 37 یا AGP 9.**

---

## ۳. چک‌لیست هر push (برای خودت یا ایجنت)

- [ ] قبل از push، diff را یک بار کامل بخوان (بالای نصف خطاها همین‌جا پیدا شد).
- [ ] هر push یک هدف. حداکثر ۲ push برای هر مرحله مگر با اجازهٔ رضا.
- [ ] بعد از push: فقط **run آخر** مهم است. سبز + آرتیفکت `app-debug` باید باشد.
- [ ] اگر قرمز: فقط خطوط `e:` را از آرتیفکت `build-log` بخوان. **اولین** `e:` علت است، بقیه معمولاً سرریز.
- [ ] CI سبز یعنی کامپایل و تست واحد. **هیچ چیز تا تست روی گوشی «کار می‌کند» نیست.**
- [ ] هر رشتهٔ جدید در `values/strings.xml` **و** `values-fa/strings.xml`. بدون آپاستروف در متن انگلیسی.
- [ ] بعد از تأیید گوشی، جدول بخش ۱ همین فایل را به‌روز کن.

---

## ۴. تله‌هایی که run سوزاندند (تکرار نکن)

| تله | راه درست |
|---|---|
| نمونهٔ تگ مثل ستاره-job-ستاره-اسلش داخل کامنت `/** */` | ترکیب ستاره+اسلش کامنت را می‌بندد. نمونه را در تست به‌صورت string بگذار |
| `as? Generic` بدون نوع | `if (x is DiagResult.Ok) x.value` |
| `CharArray` به جای `CharSequence` | `String(chunk, 0, n)` |
| `ExecutorService.submit { }` | به `submit(Runnable)` می‌رود؛ از `synchronized` استفاده کن |
| `Int.coerceIn(Long, Long)` | اول `toLong()` |
| `Intent.setPackage(...)` زنجیری | void برمی‌گرداند؛ جدا بنویس |
| `SmallTopAppBar` | در material3 1.4.0 نیست؛ `TopAppBar` + `@OptIn` |
| `ConsoleHistory.items` را برعکس کردن | خودش newest-first است |
| `SelectionContainer` داخل `LazyColumn` | روی گوشی کار نکرد؛ دکمهٔ کپی/اشتراک (`CopyShareButtons`) |
| عدد فارسی کنار `·` یا واحد لاتین | bidi بهم می‌ریزدش؛ `\u2066%1$d\u2069` |
| مسیر APK در `upload-artifact` | از ریشهٔ ریپو: `android-app/**/build/outputs/apk/debug/app-debug.apk` |
| اعتماد به گزارش ایجنت یا فایل docs | فقط شمارهٔ run، لاگ و تست گوشی شاهدند |

---

## ۵. مراحل بعدی و تست پذیرش روی گوشی

### M4 — Standby و Doze
**اول فقط خواندنی.** این دستورها را در کنسول بزن و خروجی را کپی کن (برای fixture تست):
```
am get-standby-bucket com.google.android.gms
dumpsys deviceidle whitelist | head -n 25
dumpsys deviceidle | grep -E "mState=|mLightState=|mDeepEnabled|mLightEnabled"
```
بعد یک تست نوشتنی **با برگشت** (اول مقدار اصلی را یادداشت کن):
```
am get-standby-bucket com.xiaomi.xmsf
am set-standby-bucket com.xiaomi.xmsf rare
am get-standby-bucket com.xiaomi.xmsf
am set-standby-bucket com.xiaomi.xmsf <مقدار اصلی>
```
قابلیت‌ها: لیست اپ‌ها با bucket فعلی، تغییر bucket (active/working_set/frequent/rare/restricted)، لیست سفید Doze، force-idle / unforce.
**قانون:** هر دستور نوشتنی بعد از اجرا با دستور خواندنی تأیید شود (command verification gating) و نتیجه در UI دیده شود. قبل از تغییر، مقدار قبلی ذخیره و دکمهٔ «برگشت» باشد.
**پذیرش:** تغییر bucket یک اپ روی گوشی انجام و با `get-standby-bucket` تأیید شود؛ برگشت هم کار کند.

### M5 — Backup Vault (در ۳ زیرمرحله)
- **M5a خروجی APK:** `pm path <pkg>` سپس `cp` به `/sdcard/Download/PulseVault/`. با uid 2000 شدنی است. پذیرش: فایل در مدیر فایل دیده شود و حجمش با `ls -l` بخواند.
- **M5b بازیابی APK:** `pm install-multiple -r` — روی Android 16 با shell **تأییدنشده**؛ اول دستی در کنسول تست شود.
- **M5c پشتیبان داده:** با uid 2000 فقط اپ‌های debuggable با `run-as`. بقیه با پیام توضیحی قفل شوند (در صورت uid 0 با `tar`).

### Final
آیکون adaptive + monochrome، صفحهٔ لایسنس‌ها، fastlane metadata، حذف کارت لاگ اتصال از حالت پیش‌فرض (پشت دکمهٔ نمایش)، `debuggable(BuildConfig.DEBUG)`، امضای release، تگ `v1.0`، سپس IzzyOnDroid.

---

## ۶. پرامپت آماده برای ایجنت (Kilo / Jules)

```text
Repo kreza6173-pixel/pulse-battery, branch native-app-v0. Read docs/HANDOFF.md first,
fully. It is the source of truth together with the code; CI logs beat both.

TASK: <one milestone step, e.g. "M4a: read-only standby bucket list">

Hard rules:
- Do NOT change toolchain versions. Never SDK 37, never AGP 9.
- Do NOT touch ShizukuExecService/ExecBridge/IUserService.aidl unless the task says so.
  If you change the service, bump USER_SERVICE_VERSION.
- Every new parser is pure Kotlin with a unit test built from the REAL phone output
  I paste below. Do not invent fixtures.
- Never put a star followed by a slash inside a block comment.
- New strings in values/ AND values-fa/. Numbers inside fa strings wrapped in
  \u2066 ... \u2069. Shell text rendered with LtrMonoText.
- Re-read the whole diff before pushing. Max 2 pushes.
- Push: git push origin HEAD:native-app-v0
- After push report: run id, green/red, and that artifact app-debug exists.
  If red, quote ONLY the e: lines, first one first.
- Nothing "works" until I confirm it on the phone. End with exactly what I should
  tap and which output to copy back.

Real phone output for this task:
<paste here>
```

---

## ۷. اگر ایجنت گیر کرد

1. از ایجنت بخواه فقط بخواند، push نکند: آخرین run قرمز و خطوط `e:` با ۵ خط کد اطراف هر کدام.
2. اگر دو push پشت سر هم قرمز شد، به آخرین commit سبز برگرد، کار را کوچک‌تر کن.
3. رفتار Shizuku را از سورس رسمی `RikkaApps/Shizuku-API` بپرس، نه از حدس روی AAR.
4. اگر چیزی روی گوشی کار نکرد، اول همان دستور را دستی در کنسول اپ بزن. اگر آنجا هم کار نکرد، مشکل از دستور است نه از کد.
