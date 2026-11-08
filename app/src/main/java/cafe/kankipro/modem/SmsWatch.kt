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
        val s = api.get("sms_dev_unread_num,sms_sim_unread_num,sms_unread_num,sms_received_flag")
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

        private fun openSms(c: Context): PendingIntent = PendingIntent.getActivity(
            c, 7, Intent(c, MainActivity::class.java).putExtra("tab", "sms").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
