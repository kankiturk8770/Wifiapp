# Kanki Modem (اپ اندروید کنترل مودم) — راهنمای Claude

اپ اندروید (Kotlin + Jetpack Compose، پکیج `cafe.kankipro.modem`) برای کنترل مودم جیبی 4G صاحب ریپو.
پنل مودم: `http://192.168.0.1/index.html` (ساختار ZTE، عنوان «4G Hostless Modem»، `PRODUCT_TYPE: UFI`).
به VPN کانکی ربطی ندارد.

## API مودم (همان که صفحهٔ وب مودم استفاده می‌کند)
- خواندن: `GET /goform/goform_get_cmd_process?isTest=false&cmd=a,b&multi_data=1`
- تغییر: `POST /goform/goform_set_cmd_process` با `goformId=…` (+ `AD` اگر مودم `RD` بدهد)
- هدر `Referer: http://<host>/index.html` لازم است؛ کوکی جلسه نگه داشته می‌شود.
- ورود و `AD` بین نسخه‌های نرم‌افزار فرق دارد و خودکار تشخیص داده می‌شود (`ModemApi.login`، `ModemApi.ad`).
  **هنوز با فایل `js/com.js` خود مودم (جای `service`) چک نشده.** وقتی رسید، اسم دستورها و پارامترها با آن یکی شود.
- کدهای وب مودم که صاحب ریپو فرستاد: `main.js`، `net.js`، `set.js`، `sim_device.js`، `wifi.js` (بدون `com.js`).

## نقشهٔ کد (`app/src/main/java/cafe/kankipro/modem/`)
| کار | فایل |
|---|---|
| حرف زدن با مودم (ورود، خواندن، تغییر، پیامک، وای‌فای، گزارش عیب‌یابی) | `ModemApi.kt` |
| HTTP همیشه روی شبکهٔ وای‌فای (نه دیتای موبایل)، کوکی | `HttpTransport.kt` |
| هش‌ها، متن پیامک UCS-2، قالب عدد و حجم | `Codec.kt` |
| وضعیت برنامه، ورود خودکار، تازه کردن هر ۲ ثانیه | `AppState.kt` |
| صفحه‌ها: ورود، خانه، وای‌فای، دستگاه‌ها، پیامک، بیشتر | `Screens.kt` |
| رنگ‌ها و اجزای مشترک، راست‌به‌چپ | `Ui.kt` |
| تست با مودم ساختگی | `app/src/test/.../ModemApiTest.kt` |

## قوانین
1. نسخه در `app/build.gradle.kts` بالا برود (`versionCode` و `versionName`) و یک خط در «تازه‌های نسخه» `README.md`.
2. متن UI فارسی؛ کامنت‌ها انگلیسی ساده. بدون اسم مدل در کامنت یا کامیت.
3. `./gradlew testDebugUnitTest assembleRelease` سبز. امضا با `app/kanki-modem.keystore` ثابت (تا آپدیت روی قبلی نصب شود).
4. CI (`.github/workflows/build.yml`) هر push را می‌سازد؛ فقط `main` ریلیز می‌دهد.
5. صاحب ریپو فارسی‌زبان است و برنامه‌نویسی یاد می‌گیرد: جواب کوتاه و ساده. قبل از مرج به main بپرس.

## کارهای باز
- [ ] فایل `js/com.js` مودم: اسم دستورها، ورود و `AD` با آن چک شود.
- [ ] روی مودم واقعی: ورود، وضعیت، وصل/قطع، ذخیرهٔ وای‌فای، پیامک، ری‌استارت.
- [ ] بعدی‌ها: APN، انتخاب شبکه و باند، فیلتر MAC، شبکهٔ مهمان، USSD، فیلتر سایت، حالت خواب وای‌فای.
