package cafe.kankipro.modem

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// How a request reaches the modem. The app uses HttpTransport; tests use a fake one.
interface Transport {
    /** Returns the response body. Throws on network errors. */
    fun send(host: String, path: String, form: String?): String
}

class ApiError(msg: String) : Exception(msg)

/**
 * Talks to the modem the same way its own web page does:
 *   read:   GET  /goform/goform_get_cmd_process?cmd=a,b,c&multi_data=1
 *   change: POST /goform/goform_set_cmd_process  goformId=NAME&...
 *
 * Login and the "AD" check value differ between firmware versions, so both are detected:
 *   - if the modem gives "LD", the password is SHA256(SHA256(pass) + LD) (upper case hex);
 *     otherwise it is Base64(pass) (older firmware).
 *   - if the modem gives "RD", every change carries AD = hash(hash(wa_inner_version + cr_version) + RD);
 *     MD5 is tried first, then SHA256, and the one that works is kept.
 */
class ModemApi(private val net: Transport) {
    @Volatile var host: String = "192.168.0.1"
    @Volatile var loginKind: String = "" // "sha256" or "base64", once known
    @Volatile private var adKind = 0     // 0 = md5, 1 = sha256 lower, 2 = sha256 upper

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun json(body: String): JSONObject {
        val t = body.trim()
        return try {
            if (t.startsWith("{")) JSONObject(t) else JSONObject().put("raw", t)
        } catch (e: Exception) {
            JSONObject().put("raw", t)
        }
    }

    /** Read one or more values. multi = several names in one request. */
    fun get(cmd: String, multi: Boolean = cmd.contains(','), extra: String = ""): JSONObject {
        val path = buildString {
            append("/goform/goform_get_cmd_process?isTest=false&cmd=").append(enc(cmd))
            if (multi) append("&multi_data=1")
            append(extra)
            append("&_=").append(System.currentTimeMillis())
        }
        return json(net.send(host, path, null))
    }

    private fun post(params: List<Pair<String, String>>): JSONObject {
        val form = params.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        return json(net.send(host, "/goform/goform_set_cmd_process", form))
    }

    private fun ad(): String? {
        val v = get("wa_inner_version,cr_version,RD")
        val rd = v.optString("RD")
        if (rd.isEmpty()) return null
        val base = v.optString("wa_inner_version") + v.optString("cr_version")
        return when (adKind) {
            0 -> Codec.md5(Codec.md5(base) + rd)
            1 -> Codec.sha256(Codec.sha256(base) + rd)
            else -> Codec.sha256(Codec.sha256(base).uppercase() + rd).uppercase()
        }
    }

    /**
     * Change something. Returns the modem's answer ("success" when it worked).
     * A "failure" with an AD is retried with the next AD hash; a refused change does nothing,
     * so trying again is safe.
     */
    fun set(goformId: String, params: List<Pair<String, String>> = emptyList()): JSONObject {
        var tries = 0
        while (true) {
            val code = ad()
            val all = mutableListOf("isTest" to "false", "goformId" to goformId)
            all += params
            if (code != null) all += "AD" to code
            val r = post(all)
            val failed = r.optString("result") == "failure"
            if (!failed || code == null || tries >= 2) return r
            adKind = (adKind + 1) % 3
            tries++
        }
    }

    fun setOk(goformId: String, params: List<Pair<String, String>> = emptyList()): Boolean {
        val r = set(goformId, params)
        val res = r.optString("result")
        return res == "success" || res == "0"
    }

    sealed class Login {
        object Ok : Login()
        data class Wrong(val left: String) : Login()
        object Locked : Login()
        data class Failed(val why: String) : Login()
    }

    fun login(password: String): Login {
        val ld = get("LD").optString("LD")
        val tries = if (ld.isNotEmpty()) {
            listOf("sha256" to Codec.sha256(Codec.sha256(password).uppercase() + ld).uppercase())
        } else {
            // Try the kind that worked before first, so a wrong password costs one attempt.
            val base = listOf("base64" to Codec.b64(password), "sha256" to Codec.sha256(password))
            if (loginKind == "sha256") base.reversed() else base
        }
        var last: Login = Login.Failed("no answer")
        for ((kind, pass) in tries) {
            val r = post(listOf("isTest" to "false", "goformId" to "LOGIN", "password" to pass))
            when (r.optString("result")) {
                "0" -> { loginKind = kind; return Login.Ok }
                "3" -> {
                    val left = get("psw_fail_num_str").optString("psw_fail_num_str")
                    last = Login.Wrong(left)
                    if (left == "0") return Login.Locked
                    // Base64 was refused: maybe this firmware wants SHA256. Try once more only
                    // if we have never logged in with base64 before.
                    if (loginKind == "base64") return last
                }
                "1", "5" -> return Login.Locked
                else -> last = Login.Failed(r.toString())
            }
        }
        return last
    }

    fun loggedIn(): Boolean = get("loginfo").optString("loginfo") == "ok"

    fun logout() { runCatching { set("LOGOUT") } }

    // ---- reading ----

    fun status(): JSONObject = get(STATUS_CMDS.joinToString(","))

    fun wifi(): JSONObject = get(WIFI_CMDS.joinToString(","))

    fun devices(): List<Device> {
        val r = get("station_list", multi = false)
        val arr: JSONArray = r.optJSONArray("station_list") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Device(
                name = o.optString("hostname").ifEmpty { o.optString("hostName") },
                ip = o.optString("ip_addr").ifEmpty { o.optString("ipAddress") },
                mac = o.optString("mac_addr").ifEmpty { o.optString("macAddress") },
            )
        }
    }

    fun sms(): List<Sms> {
        val r = get(
            "sms_data_total", multi = false,
            extra = "&page=0&data_per_page=200&mem_store=1&tags=10&order_by=" + enc("order by id desc"),
        )
        val arr = r.optJSONArray("messages") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Sms(
                id = o.optString("id"),
                number = o.optString("number"),
                text = Codec.ucs2Decode(o.optString("content")),
                date = Codec.smsDate(o.optString("date")),
                unread = o.optString("tag") == "1",
                sent = o.optString("tag") == "2" || o.optString("tag") == "3",
            )
        }
    }

    // ---- changing ----

    fun connect(on: Boolean) =
        setOk(if (on) "CONNECT_NETWORK" else "DISCONNECT_NETWORK", listOf("notCallback" to "true"))

    fun reboot() = setOk("REBOOT_DEVICE")

    fun saveWifi(ssid: String, pass: String, authMode: String, hidden: Boolean, maxUsers: String, encodePass: Boolean): Boolean {
        val cipher = if (authMode == "WPA2PSK" || authMode == "WPA3Personal" || authMode == "WPA2WPA3") "1" else "2"
        val p = mutableListOf(
            "ssid" to ssid,
            "broadcastSsidEnabled" to if (hidden) "1" else "0",
            "MAX_Access_num" to maxUsers,
            "security_mode" to authMode,
            "cipher" to cipher,
            "NoForwarding" to "0",
        )
        if (authMode != "OPEN") p += "passphrase" to if (encodePass) Codec.b64(pass) else pass
        return setOk("SET_WIFI_SSID1_SETTINGS", p)
    }

    fun sendSms(number: String, text: String) = setOk(
        "SEND_SMS",
        listOf(
            "notCallback" to "true",
            "Number" to number,
            "sms_time" to Codec.smsTime(),
            "MessageBody" to Codec.ucs2Encode(text),
            "ID" to "-1",
            "encode_type" to "UNICODE",
        ),
    )

    fun markRead(id: String) = setOk("SET_MSG_READ", listOf("msg_id" to "$id;", "tag" to "0"))

    fun deleteSms(id: String) = setOk("DELETE_SMS", listOf("msg_id" to "$id;", "notCallback" to "true"))

    /** Every value we know, as raw text, so the owner can send it when something looks wrong. */
    fun diagnostics(): String = buildString {
        fun add(title: String, block: () -> Any) {
            append("== ").append(title).append('\n')
            append(runCatching { block().toString() }.getOrElse { "error: ${it.message}" }).append("\n\n")
        }
        add("login") { get("LD,RD,loginfo,psw_fail_num_str,login_lock_time,wa_inner_version,cr_version") }
        add("status") { status() }
        add("wifi") {
            // Never put the Wi-Fi password in a report.
            wifi().apply { for (k in listOf("WPAPSK1", "WPAPSK1_encode")) if (has(k)) put(k, "***(${optString(k).length})") }
        }
        add("station_list") { get("station_list", multi = false) }
        add("sms_capacity") { get("sms_capacity_info", multi = false) }
        add("loginKind") { loginKind }
    }

    companion object {
        val STATUS_CMDS = listOf(
            "modem_main_state", "pin_status", "loginfo", "network_type", "sub_network_type",
            "network_provider", "spn_name_data", "signalbar", "rssi", "rscp", "rsrp", "rsrq", "sinr",
            "lte_rsrp", "lte_rsrq", "lte_snr", "cell_id", "lte_band", "ppp_status", "battery_charging",
            "battery_vol_percent", "battery_pers", "realtime_tx_thrpt", "realtime_rx_thrpt", "realtime_time",
            "realtime_tx_bytes", "realtime_rx_bytes", "monthly_tx_bytes", "monthly_rx_bytes", "monthly_time",
            "sms_unread_num", "sta_count", "SSID1", "lan_ipaddr", "wan_ipaddr", "imei", "msisdn",
            "cr_version", "wa_inner_version", "roam_setting_option",
        )
        val WIFI_CMDS = listOf(
            "SSID1", "AuthMode", "WPAPSK1", "WPAPSK1_encode", "HideSSID", "MAX_Access_num",
            "EncrypType", "NoForwarding", "wifi_onoff_state", "m_ssid_enable",
        )
    }
}

data class Device(val name: String, val ip: String, val mac: String)

data class Sms(
    val id: String,
    val number: String,
    val text: String,
    val date: String,
    val unread: Boolean,
    val sent: Boolean,
)
