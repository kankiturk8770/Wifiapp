package cafe.kankipro.modem

import org.json.JSONArray
import org.json.JSONObject

// Everything beyond the basics, each one copied from the modem's own page module
// (names in comments are the module in js/net.js, js/wifi.js, js/sim_device.js or js/com.js).

private fun JSONObject.s(k: String): String = optString(k)

private fun ok(r: JSONObject): Boolean = r.optString("result").let { it == "success" || it == "0" }

/** Values the modem uses for "can this page be shown" (service.getExtraFunc). "1" = shown, "2" = root only. */
fun ModemApi.extraFunc(): JSONObject = get(
    "cstm_webui_imei,cstm_webui_ttl,cstm_webui_dns,cstm_webui_bandselect,ussd_enable,vpn_enable," +
        "dns_manual_func_enable,tr069_func_enable,cstm_webui_restartplan,cstm_wifi_sleep,cstm_webui_local_update," +
        "cstm_webui_simswitch,sim_switch_number,cstm_webui_unlocksim",
)

// ---------------- internet ----------------

/** network_dial_set: auto_dial / manual_dial, roaming on / off. */
fun ModemApi.connectionMode(): JSONObject = get("ConnectionMode", multi = false)

fun ModemApi.setConnectionMode(mode: String, roaming: String) =
    setOk("SET_CONNECTION_MODE", listOf("ConnectionMode" to mode, "roam_setting_option" to roaming))

/** network_net_select. */
fun ModemApi.netSelect(): JSONObject =
    get("current_network_mode,m_netselect_save,net_select_mode,m_netselect_contents,net_select,ppp_status,modem_main_state")

fun ModemApi.setBearer(pref: String) = setOk("SET_BEARER_PREFERENCE", listOf("BearerPreference" to pref))

data class Carrier(val name: String, val numeric: String, val rat: Int, val state: Int, val subAct: Int) {
    /** 3 forbidden, 2 current, 1 available, 0 unknown. */
    val tech: String get() = when (rat) { 7 -> "4G"; 2 -> "3G"; 0 -> "2G"; else -> "" }
}

/** service.scanForNetwork: start a scan, wait while "manual_selecting", then read the list. */
fun ModemApi.scanNetworks(): List<Carrier>? {
    if (!ok(set("SCAN_NETWORK"))) return null
    repeat(120) {
        Thread.sleep(1000)
        if (get("m_netselect_status", multi = false).s("m_netselect_status") != "manual_selecting") {
            return parseCarriers(get("m_netselect_contents", multi = false).s("m_netselect_contents"))
        }
    }
    return null
}

fun parseCarriers(raw: String): List<Carrier> {
    if (raw.isBlank()) return emptyList()
    val fixed = raw.split(";").joinToString("") { if (it.split(",").size == 4) "$it,NON;" else "$it;" }
    return Regex("""([^,;]*),([^,]*),([^,]*),([^,]*),([^,;]*)""").findAll(fixed).map { m ->
        val g = m.groupValues
        Carrier(
            name = g[2].replace("\"", ""),
            numeric = g[3].filter { it.isDigit() },
            rat = g[4].trim().toIntOrNull() ?: -1,
            state = g[1].trim().toIntOrNull() ?: 0,
            subAct = g[5].trim().toIntOrNull() ?: -1,
        )
    }.toList()
}

/** service.setNetwork: register, then wait for m_netselect_result. */
fun ModemApi.register(c: Carrier): Boolean {
    val rat = c.rat
    if (rat != 0 && rat != 2 && rat != 7) return false
    val sub = if (c.subAct < 0) "" else c.subAct.toString()
    if (!ok(set("SET_NETWORK", listOf("NetworkNumber" to c.numeric, "Rat" to rat.toString(), "nSubrat" to sub)))) return false
    repeat(120) {
        Thread.sleep(1000)
        when (get("m_netselect_result", multi = false).s("m_netselect_result")) {
            "manual_success" -> return true
            "manual_fail" -> return false
        }
    }
    return false
}

// ---- APN (network_apn_set) ----

data class Apn(
    val name: String,
    val apn: String,
    val auth: String,
    val user: String,
    val pass: String,
    val pdp: String,
)

class ApnInfo(
    val mode: String,          // auto / manual
    val defaultIndex: Int,     // index of the profile in use
    val profiles: List<Apn>,
    val auto: List<Apn>,
    val max: Int,
)

private fun parseApn(one: String): Apn {
    val v = one.split("(\$)")
    fun at(i: Int) = v.getOrElse(i) { "" }
    return Apn(name = at(0), apn = at(1), auth = at(4).lowercase(), user = at(5), pass = at(6), pdp = at(7))
}

private fun apnList(joined: String): List<Apn> =
    joined.split("||").filter { it.isNotEmpty() && it.length > 5 }.map(::parseApn)

fun ModemApi.apns(): ApnInfo {
    val names = (0..19).joinToString(",") { "APN_configtmp$it" }
    val d = get("$names,apn_auto_config,apn_mode,index,Current_index,apn_num_preset")
    val list = (0..19).map { d.s("APN_configtmp$it") }.filter { it.length > 5 }.map(::parseApn)
    return ApnInfo(
        mode = d.s("apn_mode").ifEmpty { "auto" },
        defaultIndex = d.s("index").toIntOrNull() ?: 0,
        profiles = list,
        auto = apnList(d.s("apn_auto_config")),
        max = d.s("apn_num_preset").toIntOrNull() ?: 10,
    )
}

/** service.addOrEditApn; index = place in the list (a new one goes at the end). */
fun ModemApi.saveApn(index: Int, a: Apn): Boolean {
    val p = mutableListOf(
        "apn_action" to "save", "apn_mode" to "manual", "profile_name" to a.name, "wan_dial" to "*99#",
        "pdp_type" to a.pdp, "pdp_select" to "auto", "index" to index.toString(),
    )
    val v4 = listOf("wan_apn" to a.apn, "ppp_auth_mode" to a.auth, "ppp_username" to a.user, "ppp_passtmp" to a.pass)
    val v6 = listOf(
        "ipv6_wan_apn" to a.apn, "ipv6_ppp_auth_mode" to a.auth, "ipv6_ppp_username" to a.user, "ipv6_ppp_passtmp" to a.pass,
    )
    when (a.pdp) {
        "IP" -> p += v4
        "IPv6" -> p += v6
        else -> { p += v4; p += listOf("dns_mode" to "auto", "prefer_dns_manual" to "", "standby_dns_manual" to ""); p += v6 }
    }
    return setOk("APN_PROC_EX", p)
}

fun ModemApi.useApn(mode: String, index: Int, pdp: String): Boolean {
    val p = mutableListOf("apn_mode" to mode)
    if (mode == "manual") p += listOf("apn_action" to "set_default", "set_default_flag" to "1", "pdp_type" to pdp, "index" to index.toString())
    return setOk("APN_PROC_EX", p)
}

fun ModemApi.deleteApn(index: Int) =
    setOk("APN_PROC_EX", listOf("apn_action" to "delete", "apn_mode" to "manual", "index" to index.toString()))

// ---- data limit (status_traffic_alert) ----

fun ModemApi.dataLimit(): JSONObject = get(
    "data_volume_limit_switch,data_volume_limit_unit,data_volume_limit_size,data_volume_alert_percent," +
        "monthly_tx_bytes,monthly_rx_bytes,monthly_time,traffic_alinated_delta",
)

/** byData: size is "amount_unit" with unit 1 = MB, 1024 = GB; otherwise size is hours. */
fun ModemApi.setDataLimit(on: Boolean, byData: Boolean, size: String, alert: String): Boolean {
    val p = mutableListOf("data_volume_limit_switch" to if (on) "1" else "0")
    if (on) p += listOf(
        "data_volume_limit_unit" to if (byData) "data" else "time",
        "data_volume_limit_size" to size,
        "data_volume_alert_percent" to alert,
    )
    return setOk("DATA_LIMIT_SETTING", p)
}

/** Set this month's used amount (MB) or time (hours), as "calibrate" on the modem page. */
fun ModemApi.calibrate(byData: Boolean, value: String) = setOk(
    "FLOW_CALIBRATION_MANUAL",
    listOf("calibration_way" to if (byData) "data" else "time", "time" to if (byData) "0" else value, "data" to if (byData) value else "0"),
)

// ---------------- Wi-Fi ----------------

/** service.setWifiBasicMultiSSIDSwitch: when turning off, only wifiEnabled is sent. */
fun ModemApi.setWifiOn(on: Boolean, multiSsid: String): Boolean =
    if (!on) setOk("SET_WIFI_INFO", listOf("wifiEnabled" to "0"))
    else setOk("SET_WIFI_INFO", listOf("m_ssid_enable" to multiSsid.ifEmpty { "0" }, "wifiEnabled" to "1"))

/** wifi_advance. */
fun ModemApi.wifiAdvance(): JSONObject =
    get("WirelessMode,CountryCode,Channel,HT_MCS,wifi_band,wifi_11n_cap,MAX_Access_num,m_MAX_Access_num,MAX_Station_num,wifi_sta_connection,WscModeOption")

fun ModemApi.setWifiAdvance(mode: String, country: String, channel: String, rate: String, max: String, mMax: String) = setOk(
    "SET_WIFI_INFO",
    listOf(
        "wifiMode" to mode, "countryCode" to country, "MAX_Access_num" to max, "m_MAX_Access_num" to mMax,
        "selectedChannel" to channel, "abg_rate" to rate,
    ),
)

/** wifi_sleep_mode. */
fun ModemApi.sleepAndRange(): JSONObject = get("Sleep_interval,wifi_coverage")

fun ModemApi.setSleep(minutes: String) = setOk("SET_WIFI_SLEEP_INFO", listOf("sysIdleTimeToSleep" to minutes))

fun ModemApi.setRange(mode: String) = setOk("SET_WIFI_COVERAGE", listOf("wifi_coverage" to mode))

/** wifi_mac_filter. ACL_mode: 0 off, 1 only listed may join, 2 listed are blocked. */
fun ModemApi.macFilter(): JSONObject =
    get("ACL_mode,wifi_mac_black_list,wifi_hostname_black_list,wifi_cur_state,user_ip_addr,client_mac_address,wifi_mac_white_list")

fun ModemApi.setMacFilter(mode: String, macs: List<String>, names: List<String> = emptyList()): Boolean {
    val p = mutableListOf("ACL_mode" to mode)
    when (mode) {
        "1" -> p += "wifi_mac_white_list" to macs.joinToString(";")
        "2" -> {
            p += "macFilteringMode" to "2"
            p += "wifi_mac_black_list" to macs.joinToString(";")
            p += "wifi_hostname_black_list" to names.joinToString(";")
        }
    }
    return setOk("WIFI_MAC_FILTER", p)
}

/** wifi_station_info: block = add to the black list (keeps the other blocked ones). */
fun ModemApi.blockDevice(d: Device, block: Boolean): Boolean {
    val f = macFilter()
    val macs = f.s("wifi_mac_black_list").split(";").filter { it.isNotBlank() }.toMutableList()
    val raw = f.s("wifi_hostname_black_list")
    val names = (if (raw.isEmpty()) mutableListOf() else raw.split(";").toMutableList()).take(macs.size).toMutableList()
    while (names.size < macs.size) names += ""
    val i = macs.indexOfFirst { it.equals(d.mac, ignoreCase = true) }
    if (block && i < 0) { macs += d.mac; names += d.name.ifEmpty { d.mac } }
    if (!block && i >= 0) { macs.removeAt(i); names.removeAt(i) }
    return setMacFilter("2", macs, names)
}

fun ModemApi.renameDevice(mac: String, name: String) = setOk("EDIT_HOSTNAME", listOf("mac" to mac, "hostname" to name))

/** wifi_wps: PBC (press the button) or PIN. */
fun ModemApi.wps(): JSONObject = get("wifi_wps_index,WscModeOption,AuthMode,wifi_cur_state,EncrypType,wps_mode,WPS_SSID,m_ssid_enable,SSID1,HideSSID")

fun ModemApi.startWps(ssid: String, pin: String?): Boolean {
    val p = mutableListOf("WPS_SSID" to ssid, "wps_mode" to if (pin == null) "PBC" else "PIN", "wifi_wps_index" to "1")
    if (pin != null) p += "wps_pin" to pin
    return setOk("WIFI_WPS_SET", p)
}

// ---------------- router (firewall, LAN) ----------------

fun ModemApi.lan(): JSONObject = get("lan_ipaddr,lan_netmask,mac_address,dhcpEnabled,dhcpStart,dhcpEnd,dhcpLease_hour")

/** adm_lan: the modem restarts after this. */
fun ModemApi.setLan(ip: String, mask: String, dhcp: Boolean, start: String, end: String, lease: String): Boolean {
    val p = mutableListOf("lanIp" to ip, "lanNetmask" to mask, "lanDhcpType" to if (dhcp) "SERVER" else "DISABLE")
    if (dhcp) p += listOf("dhcpStart" to start, "dhcpEnd" to end, "dhcpLease" to lease)
    p += "dhcp_reboot_flag" to "1"
    return setOk("DHCP_SETTING", p)
}

fun ModemApi.dns(): JSONObject = get("dns_manual_enable,dhcpDns")

fun ModemApi.setDns(manual: String, dns: String) = setOk("DNS_SETTING", listOf("dns_manual_enable" to manual, "dhcpDns" to dns))

/** One stored firewall rule: its slot number and the comma separated fields. */
data class Rule(val index: Int, val parts: List<String>) {
    fun at(i: Int) = parts.getOrElse(i) { "" }
}

private fun rules(d: JSONObject, prefix: String, offset: Int = 0) =
    (0..9).mapNotNull { i -> d.s("$prefix$i").takeIf { it.isNotEmpty() }?.let { Rule(i + offset, it.split(",")) } }

private fun ids(list: List<Int>) = list.joinToString(";") + ";"

// Port forwarding: ip, port start, port end, protocol, comment.
fun ModemApi.portForward(): Pair<String, List<Rule>> {
    val d = get("PortForwardEnable," + (0..9).joinToString(",") { "PortForwardRules_$it" })
    return d.s("PortForwardEnable") to rules(d, "PortForwardRules_")
}
fun ModemApi.setPortForwardOn(on: Boolean) = setOk("VIRTUAL_SERVER", listOf("PortForwardEnable" to if (on) "1" else "0"))
fun ModemApi.addPortForward(ip: String, from: String, to: String, proto: String, comment: String) = setOk(
    "FW_FORWARD_ADD",
    listOf("ipAddress" to ip, "portStart" to from, "portEnd" to to, "protocol" to proto, "comment" to comment),
)
fun ModemApi.deletePortForward(idx: List<Int>) = setOk("FW_FORWARD_DEL", listOf("delete_id" to ids(idx)))

// Port filter: src ip/mask, ?, src port from, to, dest ip, ?, dest port from, to, protocol, action, comment, mac.
fun ModemApi.portFilter(): Triple<String, String, List<Rule>> {
    val v4 = (0..9).joinToString(",") { "IPPortFilterRules_$it" }
    val v6 = (0..9).joinToString(",") { "IPPortFilterRulesv6_$it" }
    val d = get("IPPortFilterEnable,DefaultFirewallPolicy,$v4,$v6")
    return Triple(d.s("IPPortFilterEnable"), d.s("DefaultFirewallPolicy"), rules(d, "IPPortFilterRules_") + rules(d, "IPPortFilterRulesv6_", 10))
}
fun ModemApi.setPortFilterBasic(on: Boolean, policy: String) = setOk(
    "BASIC_SETTING", listOf("portFilterEnabled" to if (on) "1" else "0", "defaultFirewallPolicy" to policy),
)
/** The rule action is always the opposite of the default policy (firewall_port_filter). */
fun ModemApi.addPortFilter(
    ipv6: Boolean, mac: String, destIp: String, srcIp: String, dFrom: String, dTo: String,
    sFrom: String, sTo: String, policy: String, proto: String, comment: String,
) = setOk(
    "ADD_IP_PORT_FILETER_V4V6",
    listOf(
        "ip_version" to if (ipv6) "ipv6" else "ipv4", "mac_address" to mac, "dip_address" to destIp, "sip_address" to srcIp,
        "dFromPort" to dFrom, "dToPort" to dTo, "sFromPort" to sFrom, "sToPort" to sTo,
        "action" to if (policy == "1") "Accept" else "Drop",
        "protocol" to proto, "comment" to comment,
    ),
)
fun ModemApi.deletePortFilter(idx: List<Int>) = setOk(
    "DEL_IP_PORT_FILETER_V4V6",
    listOf(
        "delete_id_v6" to idx.filter { it >= 10 }.map { it - 10 }.let { if (it.isEmpty()) "" else ids(it) },
        "delete_id" to idx.filter { it < 10 }.let { if (it.isEmpty()) "" else ids(it) },
    ),
)

// Port mapping: dest ip, source port, dest port, protocol, comment.
fun ModemApi.portMap(): Pair<String, List<Rule>> {
    val d = get("PortMapEnable," + (0..9).joinToString(",") { "PortMapRules_$it" })
    return d.s("PortMapEnable") to rules(d, "PortMapRules_")
}
fun ModemApi.setPortMapOn(on: Boolean) = setOk("ADD_PORT_MAP", listOf("portMapEnabled" to if (on) "1" else "0"))
fun ModemApi.addPortMap(from: String, ip: String, to: String, proto: String, comment: String) = setOk(
    "ADD_PORT_MAP",
    listOf("portMapEnabled" to "1", "fromPort" to from, "ip_address" to ip, "toPort" to to, "protocol" to proto, "comment" to comment),
)
fun ModemApi.deletePortMap(idx: List<Int>) = setOk("DEL_PORT_MAP", listOf("delete_id" to ids(idx)))

// Speed limit per device: ip, download, upload, comment.
fun ModemApi.rateLimit(): Pair<String, List<Rule>> {
    val d = get("RateLimitEnable," + (0..9).joinToString(",") { "RateLimitRules_$it" })
    return d.s("RateLimitEnable") to rules(d, "RateLimitRules_")
}
fun ModemApi.setRateLimitOn(on: Boolean) = setOk("ADD_RATE_LIMIT", listOf("RateLimitEnable" to if (on) "1" else "0"))
fun ModemApi.addRateLimit(ip: String, down: String, up: String, comment: String) = setOk(
    "ADD_RATE_LIMIT",
    listOf("RateLimitEnable" to "1", "ip_address" to ip, "download_speed" to down, "upload_speed" to up, "comment" to comment),
)
fun ModemApi.deleteRateLimit(idx: List<Int>) = setOk("DEL_RATE_LIMIT", listOf("delete_id" to ids(idx)))

fun ModemApi.dmz(): JSONObject = get("DMZEnable,DMZIPAddress")
fun ModemApi.setDmz(on: Boolean, ip: String): Boolean {
    val p = mutableListOf("DMZEnabled" to if (on) "1" else "0")
    if (on) p += "DMZIPAddress" to ip
    return setOk("DMZ_SETTING", p)
}

fun ModemApi.upnp(): JSONObject = get("upnpEnabled", multi = false)
fun ModemApi.setUpnp(on: Boolean) = setOk("UPNP_SETTING", listOf("upnp_setting_option" to if (on) "1" else "0"))

fun ModemApi.sysSecurity(): JSONObject = get("RemoteManagement,WANPingFilter")
fun ModemApi.setSysSecurity(remote: Boolean, pingFilter: Boolean) = setOk(
    "FW_SYS",
    listOf("remoteManagementEnabled" to if (remote) "1" else "0", "pingFrmWANFilterEnabled" to if (pingFilter) "1" else "0"),
)

// ---------------- SIM, SMS, USSD, contacts ----------------

fun ModemApi.pin(): JSONObject = get("pinnumber,pin_status,puknumber")

/** adm_pin: turn the PIN on/off (needs the PIN), or change it (needs it on). */
fun ModemApi.setPinOn(on: Boolean, pin: String) = setOk(if (on) "ENABLE_PIN" else "DISABLE_PIN", listOf("OldPinNumber" to pin))
fun ModemApi.changePin(old: String, new: String) = setOk("ENABLE_PIN", listOf("OldPinNumber" to old, "NewPinNumber" to new))
fun ModemApi.enterPin(pin: String) = setOk("ENTER_PIN", listOf("PinNumber" to pin))
fun ModemApi.enterPuk(puk: String, newPin: String) = setOk("ENTER_PUK", listOf("PUKNumber" to puk, "PinNumber" to newPin))

/** sms_set. validity: twelve_hours / one_day / one_week / largest. */
fun ModemApi.smsSettings(): JSONObject {
    val d = get("sms_parameter_info", multi = false)
    val v = when (d.s("sms_para_validity_period").toIntOrNull()) {
        167 -> "one_day"; 173 -> "one_week"; 244, 255 -> "largest"; else -> "twelve_hours"
    }
    return d.put("validity", v)
}
fun ModemApi.setSmsSettings(center: String, validity: String, report: String) = setOk(
    "SET_MESSAGE_CENTER",
    listOf(
        "save_time" to validity, "MessageCenter" to center, "status_save" to report,
        "save_location" to "native", "notCallback" to "true",
    ),
) && smsDone(3)

fun ModemApi.deleteSmsMany(ids: List<String>) =
    setOk("DELETE_SMS", listOf("msg_id" to ids.joinToString(";") + ";", "notCallback" to "true")) && smsDone(6)

fun ModemApi.smsCapacity(): JSONObject = get("sms_capacity_info", multi = false)

sealed class Ussd {
    data class Answer(val text: String, val canReply: Boolean) : Ussd()
    data class Error(val why: String) : Ussd()
}

/** ussd: send (or reply), then follow ussd_write_flag like the page does. */
fun ModemApi.ussd(code: String, reply: Boolean): Ussd {
    val p = if (reply) listOf("USSD_operator" to "ussd_reply", "USSD_reply_number" to code)
    else listOf("USSD_operator" to "ussd_send", "USSD_send_number" to code)
    if (!setOk("USSD_PROCESS", p + ("notCallback" to "true"))) return Ussd.Error("مودم دستور را قبول نکرد")
    repeat(90) {
        when (get("ussd_write_flag", multi = false).s("ussd_write_flag")) {
            "15" -> Thread.sleep(1000)
            "16" -> {
                val d = get("ussd_data_info", multi = false)
                return Ussd.Answer(Codec.ussdText(d.s("ussd_data"), d.s("ussd_dcs")), d.s("ussd_action") == "1")
            }
            "1" -> return Ussd.Error("شبکه سرویس ندارد")
            "3", "4", "unknown" -> return Ussd.Error("جوابی نیامد (زمان تمام شد)")
            "10" -> return Ussd.Error("دوباره امتحان کن")
            "99" -> return Ussd.Error("این کد پشتیبانی نمی‌شود")
            "41" -> return Ussd.Error("این کار پشتیبانی نمی‌شود")
            "2" -> return Ussd.Error("شبکه گفت‌وگو را بست")
            else -> return Ussd.Error("ارسال نشد")
        }
    }
    return Ussd.Error("جوابی نیامد (زمان تمام شد)")
}

fun ModemApi.ussdCancel() = runCatching { setOk("USSD_PROCESS", listOf("USSD_operator" to "ussd_cancel")) }.getOrDefault(false)

/** group: common / family / friend / colleague (only for contacts kept in the modem). */
data class Contact(
    val id: String,
    val name: String,
    val number: String,
    val onSim: Boolean,
    val group: String,
    val home: String = "",
    val office: String = "",
    val email: String = "",
)

/** phonebook: every contact (SIM and modem). */
fun ModemApi.contacts(): List<Contact> {
    val d = get("pbm_data_total", multi = false, extra = "&mem_store=2&page=0&data_per_page=2000&orderBy=name&isAsc=true")
    val arr = d.optJSONArray("pbm_data") ?: JSONArray()
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        Contact(
            id = o.s("pbm_id"),
            name = Codec.ucs2Decode(o.s("pbm_name")),
            number = o.s("pbm_number"),
            onSim = o.s("pbm_location") == "0",
            group = o.s("pbm_group"),
            home = o.s("pbm_anr"),
            office = o.s("pbm_anr1"),
            email = Codec.ucs2Decode(o.s("pbm_email")),
        )
    }
}

/** pbm_capacity_info for the SIM or the modem: used and total. */
fun ModemApi.contactRoom(sim: Boolean): Pair<Int, Int> {
    val d = get("pbm_capacity_info", multi = false, extra = "&pbm_location=" + if (sim) "pbm_sim" else "pbm_native")
    return if (sim) (d.s("pbm_sim_used_record_num").toIntOrNull() ?: 0) to (d.s("pbm_sim_max_record_num").toIntOrNull() ?: 0)
    else (d.s("pbm_dev_used_record_num").toIntOrNull() ?: 0) to (d.s("pbm_dev_max_record_num").toIntOrNull() ?: 0)
}

private fun ModemApi.pbmDone(): Boolean {
    repeat(20) {
        Thread.sleep(500)
        when (get("pbm_write_flag", multi = false).s("pbm_write_flag")) {
            "0" -> return true
            "6", "7", "8", "9", "10", "11", "14" -> return false
        }
    }
    return false
}

/** index -1 = new contact. Moving between SIM and modem sends the old id as delId, like the page. */
fun ModemApi.saveContact(c: Contact, wasOnSim: Boolean?): Boolean {
    val p = mutableListOf(
        "notCallback" to "true", "location" to if (c.onSim) "0" else "1",
        "name" to Codec.ucs2Encode(c.name), "mobilephone_num" to c.number,
    )
    val moved = wasOnSim != null && wasOnSim != c.onSim
    val index = if (moved) "-1" else c.id
    if (c.onSim) p += "edit_index" to index
    else p += listOf(
        "add_index_pc" to index, "homephone_num" to c.home, "officephone_num" to c.office,
        "email" to Codec.ucs2Encode(c.email), "groupchoose" to c.group.ifEmpty { "common" },
    )
    if (moved) p += "delId" to c.id
    return setOk("PBM_CONTACT_ADD", p) && pbmDone()
}

fun ModemApi.deleteContacts(ids: List<String>) = setOk(
    "PBM_CONTACT_DEL", listOf("notCallback" to "true", "del_option" to "delete_num", "delete_id" to ids.joinToString(",")),
) && pbmDone()

/** location 0 = SIM, 1 = modem. */
fun ModemApi.deleteAllContacts(location: Int) = setOk(
    "PBM_CONTACT_DEL", listOf("notCallback" to "true", "del_option" to "delete_all", "del_all_location" to location.toString()),
) && pbmDone()

// ---------------- more SMS (sms_list) ----------------

/** Save as a draft (SAVE_SMS), then wait for sms_cmd 5. */
fun ModemApi.saveDraft(numbers: List<String>, text: String) = setOk(
    "SAVE_SMS",
    listOf(
        "notCallback" to "true", "SMSMessage" to Codec.ucs2Encode(text), "SMSNumber" to numbers.joinToString(";") + ";",
        "Index" to "-1", "encode_type" to "UNICODE", "sms_time" to Codec.smsTime(), "draft_group_id" to "",
    ),
) && smsDone(5)

/** The page sends one message per number, one after the other. */
fun ModemApi.sendSmsMany(numbers: List<String>, text: String): Int = numbers.count { sendSms(it, text) }

fun splitNumbers(raw: String): List<String> =
    raw.split(';', ',', '،', ' ', '\n').map { it.trim() }.filter { it.length >= 3 }.distinct()

// ---------------- SIM lock and PIN at start (sim_abnormal, adm_others) ----------------

fun ModemApi.simState(): JSONObject = get("modem_main_state,pinnumber,puknumber,pin_status")

fun ModemApi.simLock(): JSONObject = get("sim_lock_status,cstm_webui_unlocksim,sim_unlock_nck_time")

fun ModemApi.unlockSim(code: String) = setOk("GORORM_UNLOCK_SIM", listOf("notCallback" to "true", "sim_unlock_code" to code))

// ---------------- other small things ----------------

/** The band the modem is on right now (shown in the modem page's device info). */
fun ModemApi.band(): String = get("GET_RESIDE_BAND", multi = false).s("result").takeIf { it != "band_invalid" } ?: ""

/** Language of the modem's own web page. */
fun ModemApi.webLanguage(): Pair<String, List<String>> {
    val cur = get("Language").s("Language").ifEmpty { "en" }
    val sup = get("support_languages").s("support_languages").split("+").filter { it.isNotBlank() }
    return cur to sup
}
fun ModemApi.setWebLanguage(lang: String) = setOk("SET_WEB_LANGUAGE", listOf("Language" to lang))

/** ota_update settings. */
fun ModemApi.otaSettings(): JSONObject = get("fota_updateMode,fota_updateIntervalDay,fota_allowRoamingUpdate,dm_last_check_time")
fun ModemApi.setOtaSettings(mode: String, days: String, roaming: Boolean) = setOk(
    "SetUpgAutoSetting", listOf("UpgMode" to mode, "UpgIntervalDay" to days, "UpgRoamPermission" to if (roaming) "1" else "0"),
)

// ---------------- device ----------------

/** adm_management: old and new admin password, encoded like the login. */
fun ModemApi.changeAdminPassword(old: String, new: String) =
    setOk("CHANGE_PASSWORD", listOf("newPassword" to secret(new), "oldPassword" to secret(old)))

fun ModemApi.sntp(): JSONObject = get(
    "sntp_year,sntp_month,sntp_day,sntp_hour,sntp_minute,sntp_second,sntp_time_set_mode,sntp_static_server0," +
        "sntp_static_server1,sntp_static_server2,sntp_server0,sntp_server1,sntp_server2,sntp_other_server0," +
        "sntp_other_server1,sntp_other_server2,sntp_timezone,sntp_timezone_index,sntp_dst_enable,ppp_status,sntp_process_result",
)

/** adm_others "apply": zone is "<timezone>_<index>" as in the modem's own list. */
fun ModemApi.setSntp(
    auto: Boolean, s: JSONObject, zone: String, dst: String, y: Int, mo: Int, d: Int, h: Int, mi: Int,
    servers: List<String> = listOf(s.s("sntp_server0"), s.s("sntp_server1"), s.s("sntp_server2")),
    others: List<String> = listOf(s.s("sntp_other_server0"), s.s("sntp_other_server1"), s.s("sntp_other_server2")),
): Boolean {
    val tz = zone.substringBeforeLast('_')
    val idx = zone.substringAfterLast('_', "0")
    val r = set(
        "SNTP",
        listOf(
            "manualsettime" to if (auto) "auto" else "manual",
            "sntp_server1_ip" to servers[0], "sntp_server2_ip" to servers[1], "sntp_server3_ip" to servers[2],
            "sntp_other_server0" to others[0], "sntp_other_server1" to others[1], "sntp_other_server2" to others[2],
            "timezone" to tz, "sntp_timezone_index" to idx, "DaylightEnabled" to dst,
            "time_year" to y.toString(), "time_month" to mo.toString(), "time_day" to d.toString(),
            "time_hour" to h.toString(), "time_minute" to mi.toString(),
        ),
    )
    val res = r.s("result")
    return res == "success" || res == "processing"
}

fun ModemApi.fastBoot(): JSONObject = get("mgmt_quicken_power_on,need_hard_reboot,need_sim_pin")
fun ModemApi.setFastBoot(on: Boolean) =
    setOk("MGMT_CONTROL_POWER_ON_SPEED", listOf("mgmt_quicken_power_on" to if (on) "1" else "0"))

fun ModemApi.keepAlive(): JSONObject = get("keep_alive_enable,ping_detect_url1,ping_detect_url2")
fun ModemApi.setKeepAlive(on: Boolean, u1: String, u2: String) = setOk(
    "KEEP_ALIVE_SET", listOf("keep_alive_enable" to if (on) "1" else "0", "ping_detect_url1" to u1, "ping_detect_url2" to u2),
)

fun ModemApi.ttl(): JSONObject = get("ttl_value")
fun ModemApi.setTtl(v: String) = setOk("TTL_VALUE_SET", listOf("webttl_value" to v))

fun ModemApi.simSwitch(): JSONObject = get(
    "sim_switch_number,sim_auto_switch_enable,sim_current_type,sim_switch_running_detect,sim_default_type,sim_lock_status,cstm_webui_simswitch",
)
fun ModemApi.setSimSwitch(auto: String, card: String, running: String): Boolean {
    val r = set("SIM_SWITCH", listOf("sim_auto_switch_enable" to auto, "sim_default_type" to card, "sim_switch_running_detect" to running))
    return r.s("result") == "success" || r.s("result") == "processing"
}

/** ota_update: "check" then read fota_new_version_state. */
fun ModemApi.checkUpdate(): String {
    if (!ok(set("IF_UPGRADE", listOf("select_op" to "check", "ota_manual_check_roam_state" to "1")))) return "check_failed"
    repeat(90) {
        Thread.sleep(1000)
        val st = get("fota_new_version_state,fota_current_upgrade_state").s("fota_new_version_state")
        when (st) {
            "has_critical", "has_optional", "already_has_pkg", "no_new_version", "check_failed", "bad_network" -> return st
        }
    }
    return "check_failed"
}

/** "1" = download and install the new version (the modem restarts by itself). */
fun ModemApi.acceptUpdate() = setOk("IF_UPGRADE", listOf("select_op" to "1"))

fun ModemApi.factoryReset() = setOk("RESTORE_FACTORY_SETTINGS")

fun ModemApi.deviceInfo(): JSONObject = get(
    "imei,ziccid,imsi,sim_imsi,cr_version,cstm_sw_version,hw_version,mac_address,msisdn,lan_ipaddr,wan_ipaddr," +
        "ipv6_wan_ipaddr,LocalDomain,wifi_coverage,MAX_Access_num",
)
