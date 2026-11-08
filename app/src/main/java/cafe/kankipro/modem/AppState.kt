package cafe.kankipro.modem

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class Tab { HOME, WIFI, DEVICES, SMS, SETTINGS }

class AppState(private val app: Application, transport: Transport?) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, null)

    private val prefs = app.getSharedPreferences("modem", Context.MODE_PRIVATE)
    val api = ModemApi(transport ?: HttpTransport(app))

    /** Where modem calls run; screenshot tests run them in place. */
    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    /** Screenshot tests: pages read the (fake) modem right away instead of on a later frame. */
    var eager = false
    var options: JSONObject = runCatching {
        JSONObject(app.assets.open("modem_set.json").bufferedReader().readText())
    }.getOrDefault(JSONObject())

    var host by mutableStateOf(prefs.getString("host", "192.168.0.1")!!)
    var loggedIn by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)   // short note shown at the bottom
    var tab by mutableStateOf(Tab.HOME)

    /** Open SMS compose window (from the SMS tab, a contact, or a reply). */
    var compose by mutableStateOf<Compose?>(null)

    /** Open sub-pages, last one on top (the system back button closes it). */
    val pages = mutableStateListOf<String>()

    /** Last result of each `Load` on screen, so a page shows it at once when you come back. */
    internal val loadCache = HashMap<String, Result<*>>()

    var status by mutableStateOf(JSONObject())
    var offline by mutableStateOf(false)
    var extra by mutableStateOf(JSONObject())

    val savedPassword: String get() = prefs.getString("pass", "") ?: ""

    /** New-SMS notifications (SmsWatch). On unless the owner turns them off. */
    var notifyOn by mutableStateOf(prefs.getBoolean(SmsWatch.KEY_ON, true))
        private set

    fun setNotify(on: Boolean) {
        notifyOn = on
        prefs.edit().putBoolean(SmsWatch.KEY_ON, on).apply()
        if (on) SmsWatch.start(app) else SmsWatch.stop(app)
        if (on && savedPassword.isEmpty()) message = "برای اعلان پیامک، بیرون برو و با تیک «رمز بماند» دوباره وارد شو"
    }
    private var poll: Job? = null

    val connected: Boolean get() = status.optString("ppp_status").let { it == "ppp_connected" || it == "ppp_connecting" }

    init {
        api.host = host
        if (savedPassword.isNotEmpty()) login(savedPassword, remember = true)
    }

    fun open(page: String) { pages += page }
    fun back() { if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex) }

    suspend fun <T> io(block: ModemApi.() -> T): Result<T> =
        withContext(ioDispatcher) { runCatching { api.block() } }

    fun netError(e: Throwable): String =
        if (e is ApiError) "مودم خطا داد: ${e.message}"
        else "به مودم وصل نشد. گوشی به وای‌فای مودم ($host) وصل است؟"

    fun login(password: String, remember: Boolean) {
        viewModelScope.launch {
            busy = true
            api.host = host
            prefs.edit().putString("host", host).apply()
            val r = io { login(password) }
            busy = false
            r.onFailure { message = netError(it) }
            r.onSuccess { res ->
                when (res) {
                    is ModemApi.Login.Ok -> {
                        prefs.edit().putString("pass", if (remember) password else "").remove(SmsWatch.KEY_BAD).apply()
                        SmsWatch.start(app)
                        loggedIn = true
                        message = null
                        startPolling()
                        launch { io { extraFunc() }.onSuccess { extra = it } }
                    }
                    is ModemApi.Login.Wrong -> message =
                        "رمز اشتباه است" + if (res.left.isNotEmpty()) " (${res.left} بار دیگر می‌توانی امتحان کنی)" else ""
                    is ModemApi.Login.Locked -> message =
                        "ورود قفل شد. " + (res.seconds.toIntOrNull()?.let { "${(it + 59) / 60} دقیقهٔ دیگر" } ?: "چند دقیقه صبر کن و") + " دوباره امتحان کن"
                    is ModemApi.Login.Failed -> message = "وارد نشد: ${res.why}"
                }
            }
        }
    }

    /** After the admin password changes, keep the saved one in step. */
    fun passwordChanged(new: String) {
        if (savedPassword.isNotEmpty()) prefs.edit().putString("pass", new).remove(SmsWatch.KEY_BAD).apply()
    }

    fun logout() {
        poll?.cancel()
        viewModelScope.launch { io { logout() } }
        prefs.edit().putString("pass", "").apply()
        SmsWatch.stop(app)
        loggedIn = false
        status = JSONObject()
        pages.clear()
        loadCache.clear()
        tab = Tab.HOME
    }

    private fun startPolling() {
        poll?.cancel()
        poll = viewModelScope.launch {
            var misses = 0
            while (isActive) {
                val r = io { status() }
                r.onSuccess {
                    offline = false
                    misses = 0
                    status = it
                    // Session ended on the modem (timeout or someone logged in elsewhere): log in again.
                    if (it.optString("loginfo").isNotEmpty() && it.optString("loginfo") != "ok") {
                        val pass = savedPassword
                        if (pass.isNotEmpty()) io { login(pass) } else { loggedIn = false; poll?.cancel() }
                    }
                }
                r.onFailure { if (++misses >= 2) offline = true }
                delay(2000)
            }
        }
    }

    // ---------------- best band test ----------------

    val bandResults = mutableStateListOf<BandResult>()
    var bandTestRunning by mutableStateOf(false)
    var bandTestStep by mutableStateOf("")
    var bandTestProgress by mutableStateOf(0f)
    private var bandJob: Job? = null

    /**
     * Locks the modem to each band in turn, waits until it is really on it, then measures signal,
     * ping (with jitter and loss) and, if asked, a short download. All bands are turned back on at
     * the end, also when it is stopped or fails.
     */
    fun startBandTest(bands: List<String>, restore: List<String>, withSpeed: Boolean, onDone: () -> Unit) {
        if (bandTestRunning) return
        bandResults.clear()
        bandJob = viewModelScope.launch {
            bandTestRunning = true
            stopLive()
            val http = api.net as? HttpTransport
            val letGo = http?.holdWifi() ?: {}
            try {
                bands.forEachIndexed { i, b ->
                    val name = bandName(b)
                    fun step(t: String, part: Float) { bandTestStep = "$name: $t"; bandTestProgress = (i + part) / bands.size }
                    step("قفل کردن مودم روی این باند…", 0f)
                    // The answer can get lost when the modem drops for the change; then ask what it has now.
                    val set = io { setBands(listOf(b)) }.getOrNull() == true ||
                        (1..5).any { delay(2000); io { bands().second }.getOrNull() == listOf(b) }
                    if (!set) {
                        bandResults += BandResult(b, false, note = "مودم قفل را قبول نکرد"); return@forEachIndexed
                    }
                    // Wait until the modem says it is on this band and the internet is back (up to 50 s).
                    var reached = false
                    var confirmed = false
                    for (w in 0 until 70) {
                        delay(1000)
                        step("صبر برای وصل شدن (${w + 1} ثانیه)", 0.1f + 0.4f * w / 70)
                        val st = io { get("ppp_status,signalbar") }.getOrNull() ?: continue
                        val res = io { band() }.getOrDefault("")
                        val num = BandProbe.residentNumber(res)
                        val on = st.optString("ppp_status") == "ppp_connected"
                        if (on && num != null && num == BandProbe.bandNumber(b) && BandProbe.isLte(res) == b.startsWith("LTE")) { reached = true; confirmed = true; break }
                        // Some firmware does not name the band: then trust the lock after a while.
                        if (on && res.isEmpty() && w >= 12) { reached = true; break }
                    }
                    if (!reached) { bandResults += BandResult(b, false, note = "این باند اینجا آنتن ندارد"); return@forEachIndexed }
                    step("اندازه گرفتن آنتن…", 0.55f)
                    delay(2500)
                    val sig = (0 until 3).mapNotNull {
                        delay(800)
                        io { get("lte_rsrp,rssi,signalbar,detail_cell_sinr,detail_cell_rsrq,sinr,rsrq,lte_snr") }.getOrNull()
                    }
                    fun avg(vararg keys: String): Double? = sig.mapNotNull { j -> keys.firstNotNullOfOrNull { k -> j.optString(k).toDoubleOrNull() } }.takeIf { it.isNotEmpty() }?.average()
                    step("پینگ (۲۰ بار)…", 0.65f)
                    val net = http?.wifiNetwork()
                    val (ping, jitter, loss) = withContext(Dispatchers.IO) { BandProbe.pingSeries(net) }
                    val mbps = if (withSpeed) { step("تست سرعت…", 0.85f); withContext(Dispatchers.IO) { BandProbe.speed(http?.wifiNetwork()) } } else null
                    bandResults += BandResult(
                        b, true,
                        rsrp = avg("lte_rsrp")?.toInt(), sinr = avg("detail_cell_sinr", "sinr", "lte_snr"), rsrq = avg("detail_cell_rsrq", "rsrq"),
                        bars = avg("signalbar")?.toInt(), ping = ping, jitter = jitter, loss = loss, mbps = mbps,
                        note = if (confirmed) "" else "مودم اسم باند را نگفت",
                    )
                }
                bandTestStep = "تمام شد"
            } catch (e: kotlinx.coroutines.CancellationException) {
                bandTestStep = "متوقف شد"
                throw e
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    bandTestStep = "روشن کردن دوبارهٔ همهٔ باندها…"
                    // Try hard: leaving the modem locked on one band by mistake would be bad.
                    for (t in 0 until 5) {
                        if (io { setBands(restore) }.getOrNull() == true) break
                        delay(3000)
                        if (io { bands().second }.getOrNull()?.toSet() == restore.toSet()) break
                    }
                    letGo()
                    bandTestRunning = false
                    bandTestProgress = 1f
                    if (bandTestStep.startsWith("روشن")) bandTestStep = "تمام شد؛ همهٔ باندها دوباره روشن شدند"
                    onDone()
                }
            }
        }
    }

    fun stopBandTest() { bandJob?.cancel() }

    // ---------------- live measure (no band change) ----------------

    /** Pings and signal per band, gathered while the modem stays on whatever band it picked itself. */
    class LiveBand(val band: String) {
        val pings = ArrayDeque<Double?>()
        var rsrp: Int? = null
        var sinr: Double? = null
        var bars: Int? = null
    }

    val live = mutableStateMapOf<String, LiveBand>()
    var liveBand by mutableStateOf("")
    var liveTick by mutableIntStateOf(0)
    private var liveJob: Job? = null

    /** Every 2 seconds: which band, signal, and one ping through the modem. Nothing on the modem is changed. */
    fun startLive() {
        if (liveJob?.isActive == true || bandTestRunning) return
        liveJob = viewModelScope.launch {
            val http = api.net as? HttpTransport
            while (isActive) {
                val res = io { band() }.getOrDefault("")
                val num = BandProbe.residentNumber(res)
                val key = when {
                    num == null -> ""
                    BandProbe.isLte(res) -> "LTEB$num"
                    else -> "WB$num"
                }
                val sig = io { get("lte_rsrp,signalbar,detail_cell_sinr,sinr,lte_snr") }.getOrNull()
                val ping = withContext(Dispatchers.IO) { BandProbe.pingOnce(http?.wifiNetwork()) }
                if (key.isNotEmpty()) {
                    val b = live.getOrPut(key) { LiveBand(key) }
                    b.pings.addLast(ping)
                    while (b.pings.size > 60) b.pings.removeFirst()
                    sig?.let { j ->
                        j.optString("lte_rsrp").toDoubleOrNull()?.let { b.rsrp = it.toInt() }
                        listOf("detail_cell_sinr", "sinr", "lte_snr").firstNotNullOfOrNull { j.optString(it).toDoubleOrNull() }?.let { b.sinr = it }
                        j.optString("signalbar").toIntOrNull()?.let { b.bars = it }
                    }
                }
                liveBand = key
                liveTick++
                delay(2000)
            }
        }
    }

    fun stopLive() { liveJob?.cancel(); liveJob = null }

    fun liveResult(b: LiveBand): BandResult {
        val (p, j, l) = BandProbe.summary(b.pings.toList())
        return BandResult(b.band, true, rsrp = b.rsrp, sinr = b.sinr, bars = b.bars, ping = p, jitter = j, loss = l,
            note = "${b.pings.size} بار سنجیده شد")
    }

    /** A small change with no spinner and no message (e.g. marking an SMS read). */
    fun quiet(block: ModemApi.() -> Unit) { viewModelScope.launch { io(block) } }

    /** Run a change on the modem, show the result, then call [then] when it worked. */
    fun run(ok: String, fail: String = "مودم قبول نکرد", then: (() -> Unit)? = null, block: ModemApi.() -> Boolean) {
        viewModelScope.launch {
            busy = true
            val r = io(block)
            busy = false
            message = r.fold({ if (it) ok else fail }, { netError(it) })
            if (r.getOrNull() == true) then?.invoke()
        }
    }
}
