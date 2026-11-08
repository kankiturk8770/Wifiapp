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
  **مهم:** `DEVICE:"ext"` یعنی مودم بعد از `js/set.js` فایل `js/ext/set.js` را هم می‌خواند و پرچم‌ها را عوض می‌کند. صاحب ریپو تأیید کرد
  فیلتر سایت، شبکهٔ مهمان، باند، VPN و … در مودمش هست؛ پس همهٔ صفحه‌ها بدون نگاه به پرچم‌های `set.js` نشان داده می‌شوند (فقط انتخاب سیم‌کارت
  وقتی بیش از یک سیم هست). عوض کردن IMEI عمداً ساخته نشد.
- کدهای وب مودم که صاحب ریپو فرستاد: `main.js`، `net.js`، `set.js`، `sim_device.js`، `wifi.js`، `com.js`، `ext/set.js`، `ext/menu.js`،
  `subpg/ota_update.html`، `subpg/wifi_sleep_mode.html`، `subpg/firewall_rate_limit.html`، و بعد همهٔ ۵۲ صفحهٔ `subpg/*.html` (همه چک شد؛
  فقط `js/util.js` نیامد).
- `ext/set.js` (اسم مودم «4G Mobile Hotspot»): AP Station، کارت حافظه، باند ۵ گیگ و ۴۰ مگاهرتز، قفل شبکه روشن؛ حداکثر ۸ دستگاه؛ وای‌فای
  ۲.۴ فقط `6` (b/g/n/ax). `TSW_SUPPORT` (روشن/خاموش زمان‌دار) خاموش است، پس ساخته نشد. آپدیت با فایل: `cgi-bin/upload/upload.cgi` فیلد `filename`.

## نقشهٔ کد (`app/src/main/java/cafe/kankipro/modem/`)
| کار | فایل |
|---|---|
| حرف زدن با مودم (ورود، خواندن، تغییر، پیامک، وای‌فای، گزارش عیب‌یابی) | `ModemApi.kt` |
| همهٔ تنظیمات دیگر (هر تابع کنار اسم ماژول صفحهٔ مودم که از آن آمده) | `Features.kt` |
| HTTP همیشه روی شبکهٔ وای‌فای (نه دیتای موبایل)، کوکی | `HttpTransport.kt` |
| اعلان پیامک تازه، گزارش رسیدن پیامک، سقف ماهانه و نسخهٔ تازهٔ مودم: سرویس پیش‌زمینه (`specialUse`) هر ۱۵ ثانیه، `BootReceiver` بعد از روشن شدن گوشی؛ رمز غلط دوباره امتحان نمی‌شود (`bad_pass`) | `SmsWatch.kt` |
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
- [ ] روی مودم واقعی: ورود، وضعیت، وصل/قطع، ذخیرهٔ وای‌فای، پیامک، ری‌استارت، و همهٔ صفحه‌های تازه.
- [ ] اعلان پیامک روی گوشی واقعی: گوشی قفل و چند ساعت بی‌کار، بعد از ری‌استارت گوشی، و روی گوشی‌های شیائومی/سامسونگ (بهینه‌سازی باتری خودشان).
- مقدارهای واقعی مودم: `tools/modem_dump.py` در Termux (فقط می‌خواند، رمزها `***`). `util.js` وجود ندارد؛ توابعش در `js/lib.js` است
  (`encodeMessage` = UCS-2، `getEncodeType` با جدول GSM7، `transUnixTime` = `yyyy-MM-dd HH:mm:ss`). `get_support_band` فقط بعد از ورود پر است؛
  جواب کارت حافظهٔ خالی `no_sdcad` است (با غلط املایی خود مودم). سیم‌کارت این مودم جای مخاطب ندارد (۰).
- از HTMLها تأیید شد: کنترل والدین ردیف ۰ = یکشنبه، خانهٔ پر = ساعت بسته؛ AP Station رمزنگاری TKIP/CCMP/TKIPCCMP و WEP با کلید ۰ تا ۳؛
  کارت حافظه: آپلود `/cgi-bin/httpshare/<اسم>` با `filename` و دو فیلد زمان بعد از `GOFORM_HTTPSHARE_CHECK_FILE`، دانلود = خود مسیر `/mmc2/...`؛
  DNS دستی `0`/`1`؛ «ری‌استارت زمان‌دار» صفحهٔ مودم همان «نگه داشتن اتصال» است.
- عمداً ساخته نشد: عوض کردن IMEI.
