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
 * Talks to the modem the same way its own web page (js/com.js, module "service") does:
 *   read:   GET  /goform/goform_get_cmd_process?cmd=a,b,c&multi_data=1
 *   change: POST /goform/goform_set_cmd_process  goformId=NAME&...
 *
 * Passwords: the page's set.js has PASSWORD_ENCODE. When true (this modem), the login and
 * Wi-Fi passwords travel as Base64. When false (newer firmware), they are AES-192-CBC with
 * key/iv taken from (rnum_js + imei + "FFFF..."), see [Codec.modemAes].
 */
class ModemApi(private val net: Transport) {
    @Volatile var host: String = "192.168.0.1"
    /** null = not read yet. */
    @Volatile var passwordEncode: Boolean? = null

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

    /** Change something. The answer is {"result":"success"} when it worked. */
    fun set(goformId: String, params: List<Pair<String, String>> = emptyList()): JSONObject {
        val all = listOf("isTest" to "false", "goformId" to goformId) + params
        val form = all.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        return json(net.send(host, "/goform/goform_set_cmd_process", form))
    }

    fun setOk(goformId: String, params: List<Pair<String, String>> = emptyList()): Boolean {
        val res = set(goformId, params).optString("result")
        return res == "success" || res == "0"
    }

    /** PASSWORD_ENCODE from the modem's own js/set.js; Base64 if it cannot be read. */
    private fun encodeFlag(): Boolean {
        passwordEncode?.let { return it }
        val v = runCatching { net.send(host, "/js/set.js", null) }.getOrNull()
        val flag = Regex("PASSWORD_ENCODE\\s*:\\s*(true|false)").find(v ?: "")?.groupValues?.get(1)
        return (flag != "false").also { if (flag != null) passwordEncode = it }
    }

    /** A password as the modem wants it (login and Wi-Fi). */
    fun secret(plain: String): String =
        if (encodeFlag()) Codec.b64(plain)
        else {
            val d = get("imei,rnum_js")
            Codec.modemAes(d.optString("rnum_js"), d.optString("imei"), plain)
        }

    sealed class Login {
        object Ok : Login()
        data class Wrong(val left: String) : Login()
        data class Locked(val seconds: String) : Login()
        data class Failed(val why: String) : Login()
    }

    fun login(password: String): Login {
        val r = set("LOGIN", listOf("password" to secret(password)))
        return when (val code = r.optString("result")) {
            "0", "4" -> Login.Ok
            "3", "1" -> {
                val d = get("psw_fail_num_str,login_lock_time")
                val left = d.optString("psw_fail_num_str")
                if (left == "0") Login.Locked(d.optString("login_lock_time")) else Login.Wrong(left)
            }
            "2" -> Login.Failed("کس دیگری با همین حساب وارد شده است")
            else -> Login.Failed(code.ifEmpty { r.toString() })
        }
    }

    fun loggedIn(): Boolean = get("loginfo").optString("loginfo") == "ok"

    fun logout() { runCatching { set("LOGOUT") } }

    // ---- reading ----

    fun status(): JSONObject = get(STATUS_CMDS.joinToString(","))

    fun wifi(): JSONObject {
        val w = get(WIFI_CMDS.joinToString(","))
        val pass = when {
            w.optString("WPAPSK1_encode").isNotEmpty() ->
                runCatching { String(java.util.Base64.getDecoder().decode(w.optString("WPAPSK1_encode"))) }.getOrDefault("")
            w.optString("WPAPSK1_enaes").isNotEmpty() ->
                runCatching { Codec.modemAesOpen(w.optString("rnum_js"), w.optString("imei"), w.optString("WPAPSK1_enaes")) }.getOrDefault("")
            else -> w.optString("WPAPSK1")
        }
        return w.put("password", pass)
    }

    fun devices(): List<Device> {
        val r = get("station_list", multi = false)
        val arr: JSONArray = r.optJSONArray("station_list") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Device(
                name = o.optString("hostname").takeIf { it != "--" } ?: "",
                ip = o.optString("ip_addr"),
                mac = o.optString("mac_addr"),
            )
        }
    }

    fun sms(): List<Sms> {
        val r = get(
            "sms_data_total", multi = false,
            extra = "&page=0&data_per_page=500&mem_store=1&tags=10&order_by=" + enc("order by id desc"),
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

    /** Same fields as the modem page's "SSID1" save (service.setWifiBasic). */
    fun saveWifi(ssid: String, pass: String, authMode: String, hidden: Boolean, maxUsers: String, old: JSONObject): Boolean {
        val cipher = if (authMode == "WPA2PSK" || authMode == "WPA3Personal" || authMode == "WPA2WPA3") "1" else "2"
        val p = mutableListOf(
            "ssid" to ssid,
            "broadcastSsidEnabled" to if (hidden) "1" else "0",
            "MAX_Access_num" to maxUsers,
            "security_mode" to authMode,
            "cipher" to cipher,
            "NoForwarding" to old.optString("NoForwarding").ifEmpty { "0" },
            "show_qrcode_flag" to old.optString("show_qrcode_flag").ifEmpty { "0" },
            "show_ssid_on_lcd" to old.optString("show_ssid_on_lcd").ifEmpty { "no" },
        )
        if (authMode == "OPEN") p += "security_shared_mode" to "NONE"
        else {
            p += "security_shared_mode" to cipher
            p += "passphrase" to secret(pass)
        }
        return setOk("SET_WIFI_SSID1_SETTINGS", p)
    }

    /** SMS jobs finish later; the page asks sms_cmd_status_info until 3 (done) or 2 (failed). */
    private fun smsDone(cmd: Int): Boolean {
        repeat(20) {
            Thread.sleep(700)
            when (get("sms_cmd_status_info", multi = false, extra = "&sms_cmd=$cmd").optString("sms_cmd_status_result")) {
                "3" -> return true
                "2" -> return false
            }
        }
        return false
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
    ) && smsDone(4)

    fun markRead(id: String) = setOk("SET_MSG_READ", listOf("msg_id" to "$id;", "tag" to "0"))

    fun deleteSms(id: String) = setOk("DELETE_SMS", listOf("msg_id" to "$id;", "notCallback" to "true")) && smsDone(6)

    fun turnOff() = setOk("TURN_OFF_DEVICE")

    /** Every value we know, as raw text, so the owner can send it when something looks wrong. */
    fun diagnostics(): String = buildString {
        fun add(title: String, block: () -> Any) {
            append("== ").append(title).append('\n')
            append(runCatching { block().toString() }.getOrElse { "error: ${it.message}" }).append("\n\n")
        }
        add("login") { get("loginfo,psw_fail_num_str,login_lock_time,modem_main_state,cr_version") }
        add("password_encode") { encodeFlag() }
        add("status") { status() }
        add("wifi") {
            // Never put the Wi-Fi password in a report.
            wifi().apply {
                for (k in listOf("WPAPSK1", "WPAPSK1_encode", "WPAPSK1_enaes", "password", "rnum_js")) if (has(k)) put(k, "***")
            }
        }
        add("station_list") { get("station_list", multi = false) }
        add("sms_capacity") { get("sms_capacity_info", multi = false) }
    }

    companion object {
        // Names the modem page itself asks for (statusBar, getDeviceInfo, getSignalStrength).
        val STATUS_CMDS = listOf(
            "modem_main_state", "pin_status", "loginfo", "network_type", "sub_network_type",
            "network_provider", "network_provider_spn", "spn_name_data", "spn_b1_flag", "spn_b2_flag",
            "signalbar", "rssi", "rscp", "lte_rsrp", "detail_cell_rsrq", "detail_cell_rssi", "detail_cell_sinr",
            "detail_cell_pci", "detail_cell_id", "ppp_status", "simcard_roam", "battery_charging",
            "battery_vol_percent", "battery_pers", "realtime_tx_thrpt", "realtime_rx_thrpt", "realtime_time",
            "realtime_tx_bytes", "realtime_rx_bytes", "monthly_tx_bytes", "monthly_rx_bytes", "monthly_time",
            "sms_unread_num", "sms_dev_unread_num", "sms_sim_unread_num", "sta_count", "m_sta_count",
            "wifi_cur_state", "SSID1", "lan_ipaddr", "wan_ipaddr", "imei", "msisdn", "ziccid",
            "cr_version", "cstm_sw_version", "hw_version",
        )
        val WIFI_CMDS = listOf(
            "SSID1", "AuthMode", "WPAPSK1_encode", "WPAPSK1_enaes", "WPAPSK1", "rnum_js", "imei",
            "HideSSID", "MAX_Access_num", "MAX_Station_num", "EncrypType", "NoForwarding",
            "wifi_cur_state", "show_qrcode_flag", "show_ssid_on_lcd",
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
