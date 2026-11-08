package cafe.kankipro.modem

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
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

enum class Tab { HOME, WIFI, DEVICES, SMS, MORE }

class AppState(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("modem", Context.MODE_PRIVATE)
    val api = ModemApi(HttpTransport(app))

    var host by mutableStateOf(prefs.getString("host", "192.168.0.1")!!)
    var loggedIn by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)   // short note shown at the bottom
    var tab by mutableStateOf(Tab.HOME)

    var status by mutableStateOf(JSONObject())
    var offline by mutableStateOf(false)
    var wifi by mutableStateOf<JSONObject?>(null)
    var devices by mutableStateOf<List<Device>?>(null)
    var sms by mutableStateOf<List<Sms>?>(null)

    val savedPassword: String get() = prefs.getString("pass", "") ?: ""
    private var poll: Job? = null

    init {
        api.host = host
        if (savedPassword.isNotEmpty()) login(savedPassword, remember = true)
    }

    private suspend fun <T> io(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching(block) }

    private fun netError(e: Throwable): String =
        if (e is ApiError) "مودم خطا داد: ${e.message}"
        else "مودم پیدا نشد. گوشی به وای‌فای مودم وصل است؟ (${host})"

    fun login(password: String, remember: Boolean) {
        viewModelScope.launch {
            busy = true
            api.host = host
            prefs.edit().putString("host", host).apply()
            val r = io { api.login(password) }
            busy = false
            r.onFailure { message = netError(it) }
            r.onSuccess { res ->
                when (res) {
                    is ModemApi.Login.Ok -> {
                        prefs.edit()
                            .putString("pass", if (remember) password else "")
                            .apply()
                        loggedIn = true
                        message = null
                        startPolling()
                    }
                    is ModemApi.Login.Wrong -> message =
                        "رمز اشتباه است" + if (res.left.isNotEmpty()) " (${res.left} بار دیگر می‌توانی امتحان کنی)" else ""
                    is ModemApi.Login.Locked -> message =
                        "ورود قفل شد. " + (res.seconds.toIntOrNull()?.let { "${(it + 59) / 60} دقیقهٔ دیگر" } ?: "چند دقیقه صبر کن و") + " دوباره امتحان کن"
                    is ModemApi.Login.Failed -> message = "ورود نشد: ${res.why}"
                }
            }
        }
    }

    fun logout() {
        poll?.cancel()
        viewModelScope.launch { io { api.logout() } }
        prefs.edit().putString("pass", "").apply()
        loggedIn = false
        status = JSONObject(); wifi = null; devices = null; sms = null
        tab = Tab.HOME
    }

    private fun startPolling() {
        poll?.cancel()
        poll = viewModelScope.launch {
            var misses = 0
            while (isActive) {
                val r = io { api.status() }
                r.onSuccess {
                    offline = false
                    misses = 0
                    status = it
                    // Session ended on the modem (timeout or someone logged in elsewhere): log in again.
                    if (it.optString("loginfo").isNotEmpty() && it.optString("loginfo") != "ok") {
                        val pass = savedPassword
                        if (pass.isNotEmpty()) io { api.login(pass) } else { loggedIn = false; cancelPoll() }
                    }
                }
                r.onFailure { if (++misses >= 2) offline = true }
                delay(2000)
            }
        }
    }

    private fun cancelPoll() { poll?.cancel() }

    // Run a change on the modem and show the result.
    private fun act(ok: String, fail: String, then: (() -> Unit)? = null, block: () -> Boolean) {
        viewModelScope.launch {
            busy = true
            val r = io(block)
            busy = false
            message = r.fold({ if (it) ok else fail }, { netError(it) })
            if (r.getOrNull() == true) then?.invoke()
        }
    }

    fun loadWifi() = viewModelScope.launch {
        io { api.wifi() }.onSuccess { wifi = it }.onFailure { message = netError(it) }
    }

    fun loadDevices() = viewModelScope.launch {
        io { api.devices() }.onSuccess { devices = it }.onFailure { message = netError(it) }
    }

    fun loadSms() = viewModelScope.launch {
        io { api.sms() }.onSuccess { sms = it }.onFailure { message = netError(it) }
    }

    fun connect(on: Boolean) = act(
        if (on) "دستور وصل شدن رفت" else "اینترنت مودم قطع شد", "مودم قبول نکرد",
    ) { api.connect(on) }

    fun turnOff() = act("مودم خاموش می‌شود", "مودم قبول نکرد") { api.turnOff() }

    fun reboot() = act("مودم دارد دوباره روشن می‌شود (حدود یک دقیقه)", "مودم قبول نکرد") { api.reboot() }

    fun saveWifi(ssid: String, pass: String, mode: String, hidden: Boolean, max: String) {
        val old = wifi ?: JSONObject()
        act("ذخیره شد. وای‌فای چند ثانیه قطع می‌شود؛ اگر رمز را عوض کردی با رمز تازه وصل شو", "مودم قبول نکرد") {
            api.saveWifi(ssid, pass, mode, hidden, max, old)
        }
    }

    fun sendSms(number: String, text: String) =
        act("پیامک فرستاده شد", "پیامک فرستاده نشد", then = { loadSms() }) { api.sendSms(number, text) }

    fun readSms(s: Sms) {
        if (!s.unread) return
        viewModelScope.launch {
            io { api.markRead(s.id) }
            sms = sms?.map { if (it.id == s.id) it.copy(unread = false) else it }
        }
    }

    fun deleteSms(s: Sms) =
        act("پاک شد", "پاک نشد", then = { sms = sms?.filterNot { it.id == s.id } }) { api.deleteSms(s.id) }

    suspend fun diagnostics(): String = io { api.diagnostics() }.getOrElse { "error: ${it.message}" }
}
