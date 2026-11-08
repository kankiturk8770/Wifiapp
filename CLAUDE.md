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
- بقیهٔ دستورها (APN، شبکه، فایروال، PIN، USSD، دفترچه، ساعت، …) در `Features.kt`، هر کدام با اسم ماژول صفحهٔ مودم.
  صفحه‌هایی که `set.js` این مودم خاموش کرده (`HAS_URL`، `HAS_MULTI_SSID`، `HAS_BAND_SELECT`، `HAS_VPN`، `DDNS_SUPPORT`) عمداً نیامده‌اند؛
  DNS، TTL و انتخاب سیم‌کارت فقط وقتی `cstm_webui_*` آن «1» است دیده می‌شوند (مثل خود مودم).
- کدهای وب مودم که صاحب ریپو فرستاد: `main.js`، `net.js`، `set.js`، `sim_device.js`، `wifi.js`، `com.js`.

## نقشهٔ کد (`app/src/main/java/cafe/kankipro/modem/`)
| کار | فایل |
|---|---|
| حرف زدن با مودم (ورود، خواندن، تغییر، پیامک، وای‌فای، گزارش عیب‌یابی) | `ModemApi.kt` |
| همهٔ تنظیمات دیگر (هر تابع کنار اسم ماژول صفحهٔ مودم که از آن آمده) | `Features.kt` |
| HTTP همیشه روی شبکهٔ وای‌فای (نه دیتای موبایل)، کوکی | `HttpTransport.kt` |
| اعلان پیامک تازه: سرویس پیش‌زمینه (`specialUse`) هر ۱۵ ثانیه، `BootReceiver` بعد از روشن شدن گوشی؛ رمز غلط دوباره امتحان نمی‌شود (`bad_pass`) | `SmsWatch.kt` |
| Base64/AES رمزها، متن پیامک UCS-2، قالب عدد و حجم | `Codec.kt` |
| وضعیت برنامه، ورود خودکار، تازه کردن هر ۲ ثانیه، صفحه‌های باز (`open`/`back`)، `run` برای هر تغییر | `AppState.kt` |
| تب‌ها: ورود، خانه (موج آنتن `WaveDish`، `SimCard` برای PIN/PUK، `LimitBar`)، وای‌فای (`WifiQrDialog`)، دستگاه‌ها، پیامک (دریافتی/ارسالی/پیش‌نویس/سیم، `ComposeDialog`، `Room`)، تنظیمات | `Screens.kt` |
| زیرصفحه‌های تنظیمات (`PageHost` با اسم صفحه) | `Pages.kt` |
| رنگ‌ها (`K`)، فونت وزیرمتن، اجزای مشترک: `SubPage`، `Load`، `Group`، `NavRow`، `SwitchRow`، `InfoRow`، `Field`، `Choice`، `Ask` | `Ui.kt` |
| فهرست کشورها، کانال‌ها و منطقه‌های زمانی مودم (از `set.js`) | `app/src/main/assets/modem_set.json` |
| تست درخواست‌ها با مودم ساختگی | `app/src/test/.../ModemApiTest.kt` |
| عکس همهٔ صفحه‌ها با Paparazzi (`./gradlew recordPaparazziDebug` ← `app/src/test/snapshots/images`) | `app/src/test/.../Shots.kt` |

## طراحی
مشکی خالص (AMOLED)، یک آبی (`K.blue`) برای هر چیز قابل لمس، سبز/زرد/قرمز فقط برای وضعیت. فونت وزیرمتن. هر ردیف تنظیمات یک
کاشی آیکن رنگی دارد. عدد و آدرس چپ‌به‌راست (`Ltr`، `Codec.iso`). بعد از هر تغییر UI عکس‌ها را دوباره بگیر و نگاه کن.

## قوانین
1. نسخه در `app/build.gradle.kts` بالا برود (`versionCode` و `versionName`) و یک خط در «تازه‌های نسخه» `README.md`.
2. متن UI فارسی؛ کامنت‌ها انگلیسی ساده. بدون اسم مدل در کامنت یا کامیت.
3. `./gradlew testDebugUnitTest lintRelease assembleRelease` سبز (حداقل اندروید ۸، `minSdk 26`). امضا با `app/kanki-modem.keystore` ثابت (تا آپدیت روی قبلی نصب شود).
4. CI (`.github/workflows/build.yml`) هر push را می‌سازد؛ فقط `main` ریلیز می‌دهد.
5. صاحب ریپو فارسی‌زبان است و برنامه‌نویسی یاد می‌گیرد: جواب کوتاه و ساده. قبل از مرج به main بپرس.

## کارهای باز
- [ ] روی مودم واقعی: ورود، وضعیت، وصل/قطع، ذخیرهٔ وای‌فای، پیامک، ری‌استارت.
- [ ] اعلان پیامک روی گوشی واقعی: گوشی قفل و چند ساعت بی‌کار، بعد از ری‌استارت گوشی، و روی گوشی‌های شیائومی/سامسونگ (بهینه‌سازی باتری خودشان).
- [ ] فایل `js/ext/menu.js` مودم: معلوم شود کدام صفحه‌ها در منوی خود مودم هست (الان از روی پرچم‌های `set.js` است؛ فیلتر سایت،
      شبکهٔ مهمان، باند، VPN، DDNS در این مودم خاموش‌اند و نیامده‌اند).
- [ ] فایل `js/util.js` (اگر پیامک یا USSD فارسی خراب آمد): `encodeMessage`/`decodeMessage`.
- [ ] از صفحه‌های HTML مودم (`subpg/`) چک شود: واحد سرعت «محدودیت سرعت»، مقدارهای «برد وای‌فای» و «بررسی خودکار به‌روزرسانی»
      (`fota_updateMode` 0/1 فرض شده؛ اگر مقدار دیگری بود، آن بخش خودش پنهان می‌شود).
- عمداً نیامده: فقط-روت مودم (`xfnj1234`): عوض کردن IMEI، MAC یو‌اس‌بی، دستور AT، TR069؛ و آپدیت با فایل محلی (`cstm_webui_local_update`).
