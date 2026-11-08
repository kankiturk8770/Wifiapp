package cafe.kankipro.modem

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URLDecoder

/** A modem with believable data, so every screen can be drawn and looked at. */
private class ShowModem : Transport {
    private val v = mapOf(
        "loginfo" to "ok", "modem_main_state" to "modem_init_complete", "pin_status" to "0", "network_type" to "LTE",
        "sub_network_type" to "LTE", "network_provider" to "Turkcell", "network_provider_spn" to "Turkcell", "signalbar" to "4",
        "lte_rsrp" to "-87", "detail_cell_sinr" to "14", "detail_cell_rsrq" to "-9", "detail_cell_rssi" to "-61", "detail_cell_pci" to "312",
        "detail_cell_id" to "88213", "ppp_status" to "ppp_connected", "battery_charging" to "0", "battery_vol_percent" to "76",
        "realtime_tx_thrpt" to "48230", "realtime_rx_thrpt" to "2361000", "realtime_time" to "5421", "realtime_tx_bytes" to "48200000",
        "realtime_rx_bytes" to "812000000", "monthly_tx_bytes" to "1200000000", "monthly_rx_bytes" to "21500000000", "monthly_time" to "302000",
        "sms_dev_unread_num" to "2", "sta_count" to "3", "m_sta_count" to "0", "wifi_cur_state" to "1", "SSID1" to "Kanki-Home",
        "lan_ipaddr" to "192.168.0.1", "lan_netmask" to "255.255.255.0", "wan_ipaddr" to "10.44.18.201", "imei" to "861234567890123",
        "msisdn" to "+905321234567", "ziccid" to "8990011234567890123", "cr_version" to "BD_UFI_V1.0.2", "hw_version" to "MF800-V2",
        "AuthMode" to "WPA2PSK", "WPAPSK1_encode" to "S2Fua2kxMjM0NTY=", "HideSSID" to "0", "MAX_Access_num" to "10", "MAX_Station_num" to "32",
        "WirelessMode" to "4", "CountryCode" to "TR", "Channel" to "6", "HT_MCS" to "0", "WscModeOption" to "0",
        "Sleep_interval" to "10", "wifi_coverage" to "long_mode", "ACL_mode" to "2", "wifi_mac_black_list" to "AA:BB:CC:11:22:33",
        "wifi_hostname_black_list" to "Neighbor-TV", "client_mac_address" to "D4:61:9D:12:34:56",
        "net_select" to "NETWORK_auto", "net_select_mode" to "auto_select", "apn_mode" to "manual", "index" to "0",
        "APN_configtmp0" to "Turkcell(\$)internet(\$)auto(\$)*99#(\$)none(\$)(\$)(\$)IP(\$)auto(\$)(\$)auto(\$)(\$)",
        "APN_configtmp1" to "Work(\$)corp.apn(\$)manual(\$)*99#(\$)chap(\$)user(\$)pass(\$)IPv4v6(\$)auto(\$)(\$)auto(\$)(\$)",
        "data_volume_limit_switch" to "1", "data_volume_limit_unit" to "data", "data_volume_limit_size" to "50_1024", "data_volume_alert_percent" to "85",
        "dhcpEnabled" to "1", "dhcpStart" to "192.168.0.100", "dhcpEnd" to "192.168.0.200", "dhcpLease_hour" to "24h", "mac_address" to "34:4B:50:00:00:01",
        "PortForwardEnable" to "1", "PortForwardRules_0" to "192.168.0.120,25565,25565,TCP&UDP,Minecraft",
        "IPPortFilterEnable" to "0", "DefaultFirewallPolicy" to "0",
        "RateLimitEnable" to "1", "RateLimitRules_0" to "192.168.0.105,2048,512,Kids tablet",
        "DMZEnable" to "0", "RemoteManagement" to "0", "WANPingFilter" to "1",
        "pinnumber" to "3", "puknumber" to "10", "sntp_time_set_mode" to "auto", "sntp_year" to "2026", "sntp_month" to "11", "sntp_day" to "8",
        "sntp_hour" to "5", "sntp_minute" to "12", "sntp_timezone" to "<+0330>-3:30<+0430>,J80/0,J264/0", "sntp_timezone_index" to "0", "sntp_dst_enable" to "0",
        "mgmt_quicken_power_on" to "1", "keep_alive_enable" to "1", "ping_detect_url1" to "8.8.8.8", "ping_detect_url2" to "1.1.1.1",
        "Language" to "fa", "support_languages" to "en+fa+ar", "fota_updateMode" to "1", "fota_updateIntervalDay" to "7",
        "fota_allowRoamingUpdate" to "0", "dm_last_check_time" to "2026-11-07 21:40", "sntp_static_server0" to "time.nist.gov",
        "sntp_static_server1" to "pool.ntp.org", "sntp_server0" to "pool.ntp.org", "sntp_server1" to "Other", "sntp_other_server1" to "ir.pool.ntp.org",
        "m_ssid_enable" to "1", "m_SSID" to "Kanki-Guest", "m_AuthMode" to "WPA2PSK", "m_WPAPSK1_encode" to "Z3Vlc3QxMjM0", "m_MAX_Access_num" to "5",
        "vpn_state" to "open", "connect_status" to "connected", "vpn_type" to "l2tp", "vpn_server_ip" to "vpn.example.com", "vpn_name" to "kanki",
        "wifi_band" to "b", "wifi_11n_cap" to "1",
        "wifi_sta_connection" to "1", "sta_ip_status" to "connect", "EX_SSID1" to "Hotel-Lobby", "EX_wifi_profile" to "p1", "pswan_priority" to "1", "wifiwan_priority" to "2",
        "wifi_profile" to "p1,0,1,4,Hotel-Lobby,WPA2PSK,TKIPCCMP,lobby2026,0,AA:BB:CC:00:11:22;p2,0,0,2,Home-5G,WPAPSKWPA2PSK,TKIPCCMP,homepass1,0,0F:00:00:00:00:00",
        "sd_card_state" to "1", "sdcard_mode_option" to "1", "HTTP_SHARE_STATUS" to "Enabled", "HTTP_SHARE_WR_AUTH" to "readWrite", "HTTP_SHARE_FILE" to "/mmc2",
        "apn_index" to "0", "pdp_type" to "IP",
        "ussd_enable" to "1", "cstm_webui_dns" to "1", "dns_manual_enable" to "0",
    )

    private fun q(s: String) = s.split("&").filter { it.contains('=') }.associate {
        val (k, x) = it.split("=", limit = 2); URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(x, "UTF-8")
    }

    override fun send(host: String, path: String, form: String?): String {
        if (form != null) {
            return if (form.contains("HTTPSHARE_ENTERFOLD")) """{"result":{"totalRecord":"3","fileInfo":[{"fileName":"Music","attribute":"document","size":"0","lastUpdateTime":"2026-11-01"},{"fileName":"Photos","attribute":"document","size":"0","lastUpdateTime":"2026-10-20"},{"fileName":"notes.pdf","attribute":"file","size":"1830000","lastUpdateTime":"2026-11-07"}]}}"""
            else """{"result":"success"}"""
        }
        val cmd = q(path.substringAfter('?'))["cmd"] ?: return "{}"
        return when (cmd) {
            "station_list" -> JSONObject().put("station_list", JSONArray(
                """[{"mac_addr":"D4:61:9D:12:34:56","hostname":"Galaxy-S23","ip_addr":"192.168.0.101","rx":"812000000","duration":"5300"},
                    {"mac_addr":"F0:18:98:AA:10:20","hostname":"MacBook-Air","ip_addr":"192.168.0.102","rx":"2410000000","duration":"18400"},
                    {"mac_addr":"3C:22:FB:01:02:03","hostname":"--","ip_addr":"192.168.0.105","rx":"64000000","duration":"620"}]""")).toString()
            "hostNameList" -> """{"devices":[{"mac":"F0:18:98:AA:10:20","hostname":"لپ‌تاپ کار"}]}"""
            "sms_data_total" -> JSONObject().put("messages", JSONArray(listOf(
                sms("21", "+90532", "Paketinizden 2 GB kaldi. Turkcell", "1"),
                sms("20", "+989121234567", "سلام، رسیدی؟ خبر بده", "1"),
                sms("19", "+989121234567", "آره رسیدم، مرسی", "2"),
                sms("18", "*140#", "Bakiye 120 TL", "0"),
                sms("17", "+905551112233", "Toplanti 3'te", "3"),
            ))).toString()
            "pbm_data_total" -> """{"pbm_data":[{"pbm_id":"1","pbm_name":"${Codec.ucs2Encode("مامان")}","pbm_number":"+989121112233","pbm_location":"1"},
                {"pbm_id":"2","pbm_name":"${Codec.ucs2Encode("Turkcell")}","pbm_number":"532","pbm_location":"0"}]}"""
            "ConnectionMode" -> """{"connectionMode":"auto_dial","autoConnectWhenRoaming":"off"}"""
            "sms_parameter_info" -> """{"sms_para_sca":"+905329000111","sms_para_validity_period":"167","sms_para_status_report":"0"}"""
            "sms_capacity_info" -> """{"sms_nv_total":"100","sms_nv_rev_total":"12","sms_nv_send_total":"4","sms_nv_draftbox_total":"0","sms_sim_total":"40","sms_sim_rev_total":"2"}"""
            "upnpEnabled" -> """{"upnpEnabled":"1"}"""
            "childGroupList" -> """{"devices":[{"mac":"3C:22:FB:01:02:03","hostname":"تبلت بچه"}]}"""
            "time_limited" -> """{"time_limited":"0+8,9,10,11,16,17,18,19;1+16,17,18,19;6+8,9,10,11,12,13,14,15,16,17,18,19,20"}"""
            "site_white_list" -> """{"siteList":[{"id":"1","name":"آپارات","site":"www.aparat.com"}]}"""
            "HTTPSHARE_GETCARD_VALUE" -> """{"sd_card_total_size":"947200","sd_card_avi_space":"612000"}"""
            "websURLFilters" -> """{"websURLFilters":"instagram.com;tiktok.com"}"""
            "get_support_band" -> """{"GSM850":"0","GSM900":"0","GSM1800":"0","GSM1900":"0","WB1":"1","WB2":"0","WB3":"0","WB4":"0","WB5":"0","WB8":"0","LTEB1":"1","LTEB2":"0","LTEB3":"1","LTEB4":"0","LTEB5":"0","LTEB6":"0","LTEB7":"1","LTEB8":"0","LTEB12":"1","LTEB13":"0","LTEB17":"0","LTEB18":"0","LTEB19":"0","LTEB20":"1","LTEB25":"0","LTEB26":"0","LTEB27":"0","LTEB28":"0","LTEB29":"0","LTEB34":"0","LTEB38":"0","LTEB39":"0","LTEB40":"1","LTEB41":"0","LTEB66":"0"}"""
            "set_band_list" -> """{"set_band_list":""}"""
            else -> JSONObject().apply { cmd.split(",").forEach { k -> put(k, v[k] ?: "") } }.toString()
        }
    }

    private fun sms(id: String, n: String, t: String, tag: String) =
        JSONObject().put("id", id).put("number", n).put("content", Codec.ucs2Encode(t)).put("tag", tag).put("date", "26,11,08,0${id.last()},1$id,00,+12")
}

class Shots {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(softButtons = false),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 1.0,
    )

    private fun state(loggedIn: Boolean = true): AppState {
        val app = Application()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply { isAccessible = true }.invoke(app, paparazzi.context)
        val fake = ShowModem()
        return AppState(app, fake).apply {
            ioDispatcher = Dispatchers.Unconfined
            eager = true
            options = JSONObject(File("src/main/assets/modem_set.json").readText())
            this.loggedIn = loggedIn
            if (loggedIn) {
                status = JSONObject(fake.send("", "/x?cmd=" + ModemApi.STATUS_CMDS.joinToString(","), null))
                extra = JSONObject("""{"ussd_enable":"1","cstm_webui_dns":"1"}""")
            }
        }
    }

    // Paparazzi has no Activity; file pickers only need something to register with.
    private val noResults = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
        }
    }

    private fun shot(st: AppState, ui: @Composable (AppState) -> Unit = { App(it) }) = paparazzi.snapshot {
        androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides noResults) { Theme { ui(st) } }
    }

    @Test fun login() = shot(state(loggedIn = false))
    @Test fun home() = shot(state())
    @Test fun wifi() = shot(state().apply { tab = Tab.WIFI })
    @Test fun devices() = shot(state().apply { tab = Tab.DEVICES })
    @Test fun sms() = shot(state().apply { tab = Tab.SMS })
    @Test fun settings() = shot(state().apply { tab = Tab.SETTINGS })

    private fun page(name: String) = shot(state().apply { open(name) })
    @Test fun pageConn() = page("conn")
    @Test fun pageNet() = page("net")
    @Test fun pageApn() = page("apn")
    @Test fun pageLimit() = page("limit")
    @Test fun pageWifiAdv() = page("wifiadv")
    @Test fun pageSleep() = page("sleep")
    @Test fun pageMac() = page("macfilter")
    @Test fun pageWps() = page("wps")
    @Test fun pageLan() = page("lan")
    @Test fun pageForward() = page("forward")
    @Test fun pageFilter() = page("filter")
    @Test fun pageRate() = page("rate")
    @Test fun pagePin() = page("pin")
    @Test fun pageSmsSet() = page("smsset")
    @Test fun pageUssd() = page("ussd")
    @Test fun pageContacts() = page("contacts")
    @Test fun pageAbout() = page("about")
    @Test fun pageTime() = page("time")
    @Test fun pagePassword() = page("password")
    @Test fun pageUpdate() = page("update")
    @Test fun pageLang() = page("lang")
    @Test fun pageGuest() = page("guest")
    @Test fun pageBands() = page("bands")
    @Test fun pageVpn() = page("vpn")
    @Test fun pageUrl() = page("urlfilter")
    @Test fun pageApStation() = page("apstation")
    @Test fun pageParental() = page("parental")
    @Test fun pageSd() = page("sd")
    @Test fun pageQuick() = page("quick")
    @Test fun homeSimPin() = shot(state().apply { status = JSONObject(status.toString()).put("modem_main_state", "modem_waitpin").put("pinnumber", "3") })
}
