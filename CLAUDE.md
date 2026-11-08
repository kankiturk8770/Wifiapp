# Kanki Modem (اپ اندروید کنترل مودم) — راهنمای Claude

اپ اندروید (Kotlin + Jetpack Compose، پکیج `cafe.kankipro.modem`) برای کنترل مودم جیبی 4G صاحب ریپو.
پنل مودم: `http://192.168.0.1/index.html` (ساختار ZTE، عنوان «4G Hostless Modem»، `PRODUCT_TYPE: UFI`).
به VPN کانکی ربطی ندارد.

## API مودم (از `js/com.js` خود مودم، ماژول `service`)
- خواندن: `GET /goform/goform_get_cmd_process?cmd=a,b&multi_data=1`؛ تغییر: `POST /goform/goform_set_cmd_process` با `goformId=…`.
  این مودم `AD`/`RD`/`LD` ندارد. هدر `Referer` و کوکی جلسه فرستاده می‌شود.
- رمزها (ورود `LOGIN` و رمز وای‌فای): اگر `PASSWORD_ENCODE` در `js/set.js` مودم `true` باشد (همین مودم) Base64، وگرنه
  AES-192-CBC با ZeroPadding، کلید ۲۴ و iv ۱۶ حرف اول `rnum_js + imei + "FFFF…"` (`Codec.modemAes`). برنامه `set.js` را خودش می‌خواند.
- جواب ورود: `0`/`4` درست، `3` رمز غلط، `2` کاربر دیگر؛ `psw_fail_num_str` = تعداد فرصت باقی، `login_lock_time` ثانیهٔ قفل.
- پیامک: `SEND_SMS` (UCS-2 هکس، `UNICODE`)، `DELETE_SMS`، `SET_MSG_READ`؛ بعد از ارسال/حذف `sms_cmd_status_info&sms_cmd=4/6` تا `3` (موفق) یا `2`.
- وای‌فای: خواندن `WPAPSK1_encode` (Base64) یا `WPAPSK1_enaes` (AES)؛ ذخیره `SET_WIFI_SSID1_SETTINGS` (`passphrase`، `security_shared_mode`=cipher).
- دیگر دستورها (برای قابلیت‌های بعدی): `APN_PROC_EX`، `SET_BEARER_PREFERENCE`، `SCAN_NETWORK`/`SET_NETWORK`، `GOFORM_SET_BAND`،
  `WIFI_MAC_FILTER`، `USSD_PROCESS`، `SET_WIFI_SLEEP_INFO`، `DATA_LIMIT_SETTING`، `URL_FILTER_ADD`، `DHCP_SETTING`، `RESTORE_FACTORY_SETTINGS`.
- کدهای وب مودم که صاحب ریپو فرستاد: `main.js`، `net.js`، `set.js`، `sim_device.js`، `wifi.js`، `com.js`.

## نقشهٔ کد (`app/src/main/java/cafe/kankipro/modem/`)
| کار | فایل |
|---|---|
| حرف زدن با مودم (ورود، خواندن، تغییر، پیامک، وای‌فای، گزارش عیب‌یابی) | `ModemApi.kt` |
| HTTP همیشه روی شبکهٔ وای‌فای (نه دیتای موبایل)، کوکی | `HttpTransport.kt` |
| Base64/AES رمزها، متن پیامک UCS-2، قالب عدد و حجم | `Codec.kt` |
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
- [ ] روی مودم واقعی: ورود، وضعیت، وصل/قطع، ذخیرهٔ وای‌فای، پیامک، ری‌استارت.
- [ ] بعدی‌ها: APN، انتخاب شبکه و باند، فیلتر MAC، شبکهٔ مهمان، USSD، فیلتر سایت، حالت خواب وای‌فای.
