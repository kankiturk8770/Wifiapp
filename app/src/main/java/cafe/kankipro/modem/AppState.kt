package cafe.kankipro.modem

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
