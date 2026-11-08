package cafe.kankipro.modem

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The modem cannot push anything to the phone, so this service asks it every few seconds
 * for unread SMS and shows each new one as a normal phone notification.
 * It runs as a foreground service (with a small quiet notification) so Android keeps it alive,
 * and starts again after the phone restarts.
 */
class SmsWatch : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = prefs(this)
        if (!p.getBoolean(KEY_ON, true) || p.getString("pass", "").isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val n = watching(this, "در حال گرفتن پیامک‌های مودم")
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_WATCH, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID_WATCH, n)
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        val p = prefs(this)
        val api = ModemApi(HttpTransport(this))
        var loggedIn = false
        var fails = 0
        while (scope.isActive) {
            api.host = p.getString("host", "192.168.0.1") ?: "192.168.0.1"
            val pass = p.getString("pass", "") ?: ""
            if (pass.isEmpty() || !p.getBoolean(KEY_ON, true)) { stopSelf(); return }
            // A wrong password must not be tried again and again: the modem locks after 5 tries.
            if (p.getString(KEY_BAD, "") == pass) {
                update("رمز مودم عوض شده؛ برنامه را باز کن و دوباره وارد شو")
                delay(60_000L)
                continue
            }
            val ok = runCatching {
                if (!loggedIn || !api.loggedIn()) {
                    when (api.login(pass)) {
                        is ModemApi.Login.Ok -> loggedIn = true
                        is ModemApi.Login.Wrong, is ModemApi.Login.Locked -> { p.edit().putString(KEY_BAD, pass).apply(); loggedIn = false }
                        else -> loggedIn = false
                    }
                }
                if (loggedIn) check(api)
                loggedIn
            }.getOrDefault(false)
            fails = if (ok) 0 else fails + 1
            if (fails == 3) update("مودم در دسترس نیست؛ وقتی به وای‌فای مودم برگردی دوباره چک می‌کنم")
            if (ok && fails == 0) update("در حال گرفتن پیامک‌های مودم")
            // Not reachable (other Wi-Fi, mobile data): try less often.
            delay(if (ok) EVERY_MS else minOf(EVERY_MS * (1L shl minOf(fails, 3)), 120_000L))
        }
    }

    private var lastText = ""
    private fun update(text: String) {
        if (text == lastText) return
        lastText = text
        if (canNotify(this)) nm(this).notify(ID_WATCH, watching(this, text))
    }

    /** Read unread SMS from the modem memory and the SIM, and notify each new one once. */
    private fun check(api: ModemApi) {
        val s = api.get(
            "sms_dev_unread_num,sms_sim_unread_num,sms_unread_num,sms_received_flag,sts_received_flag," +
                "data_volume_limit_switch,data_volume_limit_unit,data_volume_limit_size,data_volume_alert_percent," +
                "monthly_rx_bytes,monthly_tx_bytes,monthly_time,fota_new_version_state",
        )
        runCatching { checkLimit(s) }
        runCatching { checkUpdate(s) }
        if (s.optString("sts_received_flag").let { it.isNotEmpty() && it != "0" }) runCatching { checkReports(api) }
        val count = listOf("sms_dev_unread_num", "sms_sim_unread_num", "sms_unread_num").sumOf { s.optString(it).toIntOrNull() ?: 0 }
        val p = prefs(this)
        val seen = p.getStringSet(KEY_SEEN, emptySet())!!.toMutableSet()
        if (count == 0 && s.optString("sms_received_flag") != "1") return
        val unread = (api.sms(1) + runCatching { api.sms(0) }.getOrDefault(emptyList())).filter { it.unread }
        val fresh = newOnes(unread, seen)
        if (fresh.isEmpty()) return
        if (fresh.size > 5) notifyMany(fresh.size)
        else fresh.forEach { notifySms(it) }
        // Remember only ids that are still unread, so the set does not grow forever.
        val keep = unread.map { key(it) }.toSet()
        p.edit().putStringSet(KEY_SEEN, (seen.filter { it in keep } + fresh.map { key(it) }).toSet()).apply()
    }

    /** Delivery reports of sent SMS (sms_status_rpt_data), as the modem page pops them up. */
    private fun checkReports(api: ModemApi) {
        val d = api.get("sms_status_rpt_data", multi = false, extra = "&page=0&data_per_page=10")
        val arr = d.optJSONArray("messages") ?: return
        val p = prefs(this)
        val seen = p.getStringSet(KEY_REPORTS, emptySet())!!.toMutableSet()
        val now = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            now += id
            if (id in seen || id.isEmpty()) continue
            notifySimple(("r" + id).hashCode(), "گزارش پیامک", "گزارش رسیدن پیامک ارسالی به ${o.optString("number")} آمد")
        }
        p.edit().putStringSet(KEY_REPORTS, now).apply()
    }

    /** Monthly cap: one notice when the alert percent is reached and one when it is used up (once a month each). */
    private fun checkLimit(s: org.json.JSONObject) {
        if (s.optString("data_volume_limit_switch") != "1") return
        val byData = s.optString("data_volume_limit_unit") != "time"
        val used: Double
        val total: Double
        if (byData) {
            val parts = s.optString("data_volume_limit_size").split("_")
            total = (parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0) * (parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0) * 1048576
            used = (s.optString("monthly_rx_bytes").toDoubleOrNull() ?: 0.0) + (s.optString("monthly_tx_bytes").toDoubleOrNull() ?: 0.0)
        } else {
            total = (s.optString("data_volume_limit_size").toDoubleOrNull() ?: 0.0) * 3600
            used = s.optString("monthly_time").toDoubleOrNull() ?: 0.0
        }
        if (total <= 0) return
        val alert = (s.optString("data_volume_alert_percent").toDoubleOrNull() ?: 100.0) / 100
        val level = when { used >= total -> "full"; used >= total * alert -> "alert"; else -> return }
        val c = java.util.Calendar.getInstance()
        val key = "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.MONTH)}-$level"
        val p = prefs(this)
        if (p.getString("limit_$level", "") == key) return
        p.edit().putString("limit_$level", key).apply()
        notifySimple(
            ID_LIMIT, "سقف مصرف ماهانه",
            if (level == "full") "سقف مصرف این ماه تمام شد." else "مصرف این ماه به ${(alert * 100).toInt()}٪ سقف رسید.",
        )
    }

    /** New modem software (fota_new_version_state), once per state. */
    private fun checkUpdate(s: org.json.JSONObject) {
        val v = s.optString("fota_new_version_state")
        val p = prefs(this)
        val has = v == "has_critical" || v == "has_optional" || v == "already_has_pkg"
        if (!has) { p.edit().remove(KEY_FOTA).apply(); return }
        if (p.getString(KEY_FOTA, "") == v) return
        p.edit().putString(KEY_FOTA, v).apply()
        notifySimple(ID_FOTA, "به‌روزرسانی مودم", if (v == "has_critical") "نسخهٔ تازه و مهم نرم‌افزار مودم آمده است." else "نسخهٔ تازهٔ نرم‌افزار مودم آمده است.")
    }

    private fun notifySimple(id: Int, title: String, text: String) {
        if (!canNotify(this)) return
        nm(this).notify(
            id,
            builder(this, CH_SMS).setSmallIcon(R.drawable.ic_sms).setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open(this, null)).setAutoCancel(true).build(),
        )
    }

    private fun notifySms(m: Sms) {
        if (!canNotify(this)) return
        val n = builder(this, CH_SMS)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle(m.number)
            .setContentText(m.text)
            .setStyle(Notification.BigTextStyle().bigText(m.text))
            .setSubText("پیامک مودم")
            .setContentIntent(openSms(this))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        nm(this).notify(key(m).hashCode(), n)
    }

    private fun notifyMany(n: Int) {
        if (!canNotify(this)) return
        nm(this).notify(
            ID_MANY,
            builder(this, CH_SMS).setSmallIcon(R.drawable.ic_sms)
                .setContentTitle("$n پیامک خوانده نشده در مودم")
                .setContentText("برای دیدن بزن")
                .setContentIntent(openSms(this)).setAutoCancel(true).build(),
        )
    }

    companion object {
        const val EVERY_MS = 15_000L
        private const val ID_WATCH = 1
        private const val ID_MANY = 2
        const val CH_SMS = "sms"
        const val CH_WATCH = "watch"
        const val KEY_ON = "notify"
        private const val KEY_SEEN = "notified"
        private const val KEY_REPORTS = "reports"
        private const val KEY_FOTA = "fota_seen"
        private const val ID_LIMIT = 3
        private const val ID_FOTA = 4
        const val KEY_BAD = "bad_pass"

        fun key(m: Sms) = "${m.store}:${m.id}"

        /** Messages that were not notified before. */
        fun newOnes(unread: List<Sms>, seen: Set<String>) = unread.filter { key(it) !in seen }

        fun prefs(c: Context) = c.getSharedPreferences("modem", Context.MODE_PRIVATE)

        fun start(c: Context) {
            val p = prefs(c)
            if (!p.getBoolean(KEY_ON, true) || p.getString("pass", "").isNullOrEmpty()) return
            runCatching { c.startForegroundService(Intent(c, SmsWatch::class.java)) }
        }

        fun stop(c: Context) { c.stopService(Intent(c, SmsWatch::class.java)) }

        fun canNotify(c: Context) =
            Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

        fun channels(c: Context) {
            nm(c).createNotificationChannel(
                NotificationChannel(CH_SMS, "پیامک‌های مودم", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "هر پیامکی که به سیم‌کارت مودم می‌رسد"
                    enableVibration(true)
                },
            )
            nm(c).createNotificationChannel(
                NotificationChannel(CH_WATCH, "گوش دادن به مودم", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "اعلان کوچکی که نشان می‌دهد برنامه پیامک‌ها را می‌گیرد"
                    setShowBadge(false)
                },
            )
        }

        private fun builder(c: Context, ch: String) = Notification.Builder(c, ch)

        private fun openSms(c: Context): PendingIntent = open(c, "sms")

        private fun open(c: Context, tab: String?): PendingIntent = PendingIntent.getActivity(
            c, if (tab == null) 8 else 7,
            Intent(c, MainActivity::class.java).apply { if (tab != null) putExtra("tab", tab) }
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun watching(c: Context, text: String): Notification = builder(c, CH_WATCH)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle("مودم من")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openSms(c))
            .build()
    }
}

/** Starts the watcher again after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) SmsWatch.start(context)
    }
}
