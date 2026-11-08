package cafe.kankipro.modem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

// A fake modem that answers like the real one (js/com.js of the owner's modem).
private class FakeModem(
    val encode: Boolean = true,
    val password: String = "admin",
    val rnum: String = "12345678",
    val imei: String = "861234567890123",
) : Transport {
    val posts = mutableListOf<Map<String, String>>()
    var fails = 0

    private fun form(s: String) = s.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    override fun send(host: String, path: String, form: String?): String {
        if (path == "/js/set.js") return """define(function(){var a={WEBUI_TITLE:"4G",PASSWORD_ENCODE:$encode,HAS_SMS:true};return a})"""
        if (form == null) {
            val q = form(path.substringAfter('?'))
            return when (q["cmd"]) {
                "imei,rnum_js" -> """{"imei":"$imei","rnum_js":"$rnum"}"""
                "psw_fail_num_str,login_lock_time" -> """{"psw_fail_num_str":"${5 - fails}","login_lock_time":"300"}"""
                "sms_cmd_status_info" -> """{"sms_cmd_status_result":"3"}"""
                "sms_data_total" ->
                    """{"messages":[{"id":"7","number":"+98912","content":"0633064406270645","tag":"1","date":"26,11,08,04,42,10,+14"}]}"""
                else -> "{}"
            }
        }
        val f = form(form)
        posts += f
        return when (f["goformId"]) {
            "LOGIN" -> {
                val good = if (encode) Codec.b64(password) else Codec.modemAes(rnum, imei, password)
                if (f["password"] == good) """{"result":"0"}""" else { fails++; """{"result":"3"}""" }
            }
            else -> """{"result":"success"}"""
        }
    }
}

class ModemApiTest {
    @Test fun loginWithBase64LikeThisModem() {
        val m = FakeModem()
        assertEquals(ModemApi.Login.Ok, ModemApi(m).login("admin"))
        assertEquals(Codec.b64("admin"), m.posts.last()["password"])
    }

    @Test fun loginWithAesOnNewerFirmware() {
        val m = FakeModem(encode = false)
        assertEquals(ModemApi.Login.Ok, ModemApi(m).login("admin"))
    }

    @Test fun aesMatchesCryptoJs() {
        // Reference made with: openssl enc -aes-192-cbc -nopad, key/iv from rnum_js + imei + "FFFF..."
        assertEquals("PBDyFYkYef6dsXJU+4aGKA==", Codec.modemAes("12345678", "861234567890123", "admin"))
        assertEquals("admin", Codec.modemAesOpen("12345678", "861234567890123", "PBDyFYkYef6dsXJU+4aGKA=="))
    }

    @Test fun wrongPasswordSaysHowManyLeft() {
        assertEquals(ModemApi.Login.Wrong("4"), ModemApi(FakeModem()).login("nope"))
    }

    @Test fun wifiSaveSendsWhatThePageSends() {
        val m = FakeModem()
        assertTrue(ModemApi(m).saveWifi("Home", "12345678", "WPA2PSK", false, "10", org.json.JSONObject()))
        val p = m.posts.last()
        assertEquals("SET_WIFI_SSID1_SETTINGS", p["goformId"])
        assertEquals(Codec.b64("12345678"), p["passphrase"])
        assertEquals("1", p["security_shared_mode"])
        assertEquals("0", p["broadcastSsidEnabled"])
    }

    @Test fun smsIsReadable() {
        val list = ModemApi(FakeModem()).sms()
        assertEquals("سلام", list[0].text)
        assertTrue(list[0].unread)
        assertEquals("2026/11/08  04:42", list[0].date)
    }

    @Test fun smsSendWaitsForTheModem() {
        assertTrue(ModemApi(FakeModem()).sendSms("0912", "سلام"))
    }

    @Test fun smsTextRoundTrip() {
        val t = "سلام hello ۱۲۳"
        assertEquals(t, Codec.ucs2Decode(Codec.ucs2Encode(t)))
    }
}

/** Records every change sent, answers reads from a map (like the real modem's JSON). */
private class Recorder(val values: Map<String, String> = emptyMap(), val singles: Map<String, String> = emptyMap()) : Transport {
    val posts = mutableListOf<Map<String, String>>()
    private fun form(s: String) = s.split("&").filter { it.contains('=') }.associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }
    override fun send(host: String, path: String, form: String?): String {
        if (path == "/js/set.js") return "PASSWORD_ENCODE:true"
        if (form != null) { posts += form(form); return """{"result":"success"}""" }
        val cmd = form(path.substringAfter('?'))["cmd"]!!
        singles[cmd]?.let { return it }
        return org.json.JSONObject().apply { cmd.split(",").forEach { put(it, values[it] ?: "") } }.toString()
    }
}

class FeaturesTest {
    @Test fun carriersParseLikeThePage() {
        val list = parseCarriers("2,\"Turkcell\",28601,7,1;1,\"Vodafone TR\",28602,2;3,\"Avea\",28603,0,0")
        assertEquals(3, list.size)
        assertEquals(Carrier("Turkcell", "28601", 7, 2, 1), list[0])
        assertEquals("3G", list[1].tech)
        assertEquals(-1, list[1].subAct) // the page fills a missing 5th field with "NON"
    }

    @Test fun apnProfilesAreRead() {
        val m = Recorder(mapOf(
            "APN_configtmp0" to "Turkcell(\$)internet(\$)auto(\$)*99#(\$)NONE(\$)(\$)(\$)IP(\$)auto(\$)(\$)auto(\$)(\$)",
            "APN_configtmp1" to "Work(\$)corp(\$)manual(\$)*99#(\$)CHAP(\$)u(\$)p(\$)IPv4v6(\$)",
            "apn_mode" to "manual", "index" to "1",
        ))
        val info = ModemApi(m).apns()
        assertEquals(2, info.profiles.size)
        assertEquals(Apn("Work", "corp", "chap", "u", "p", "IPv4v6"), info.profiles[1])
        assertEquals(1, info.defaultIndex)
    }

    @Test fun apnSaveSendsBothFamiliesForIpv4v6() {
        val m = Recorder()
        ModemApi(m).saveApn(2, Apn("Work", "corp", "chap", "u", "p", "IPv4v6"))
        val p = m.posts.last()
        assertEquals("APN_PROC_EX", p["goformId"]); assertEquals("save", p["apn_action"]); assertEquals("2", p["index"])
        assertEquals("corp", p["wan_apn"]); assertEquals("corp", p["ipv6_wan_apn"]); assertEquals("*99#", p["wan_dial"])
    }

    @Test fun blockingKeepsOthersAndNames() {
        val m = Recorder(mapOf("wifi_mac_black_list" to "AA:AA:AA:AA:AA:AA", "wifi_hostname_black_list" to "TV"))
        ModemApi(m).blockDevice(Device("Phone", "1.1.1.1", "BB:BB:BB:BB:BB:BB"), true)
        val p = m.posts.last()
        assertEquals("WIFI_MAC_FILTER", p["goformId"]); assertEquals("2", p["ACL_mode"])
        assertEquals("AA:AA:AA:AA:AA:AA;BB:BB:BB:BB:BB:BB", p["wifi_mac_black_list"])
        assertEquals("TV;Phone", p["wifi_hostname_black_list"])
    }

    @Test fun unblockFromEmptyNameList() {
        val m = Recorder(mapOf("wifi_mac_black_list" to "AA:AA:AA:AA:AA:AA", "wifi_hostname_black_list" to ""))
        ModemApi(m).blockDevice(Device("", "", "aa:aa:aa:aa:aa:aa"), false)
        assertEquals("", m.posts.last()["wifi_mac_black_list"])
    }

    @Test fun portFilterDeleteSplitsV4AndV6() {
        val m = Recorder()
        ModemApi(m).deletePortFilter(listOf(1, 12))
        val p = m.posts.last()
        assertEquals("1;", p["delete_id"]); assertEquals("2;", p["delete_id_v6"])
    }

    @Test fun portFilterActionIsOppositeOfPolicy() {
        val m = Recorder()
        ModemApi(m).addPortFilter(false, "", "", "10.0.0.2", "", "", "", "", "0", "TCP", "")
        assertEquals("Drop", m.posts.last()["action"])
        ModemApi(m).addPortFilter(false, "", "", "10.0.0.2", "", "", "", "", "1", "TCP", "")
        assertEquals("Accept", m.posts.last()["action"])
    }

    @Test fun dataLimitFormat() {
        val m = Recorder()
        ModemApi(m).setDataLimit(true, true, "50_1024", "85")
        val p = m.posts.last()
        assertEquals("DATA_LIMIT_SETTING", p["goformId"]); assertEquals("data", p["data_volume_limit_unit"]); assertEquals("50_1024", p["data_volume_limit_size"])
        ModemApi(m).setDataLimit(false, true, "", "")
        assertEquals(null, m.posts.last()["data_volume_limit_unit"])
    }

    @Test fun ussdAnswerIsDecoded() {
        val m = Recorder(singles = mapOf(
            "ussd_write_flag" to """{"ussd_write_flag":"16"}""",
            "ussd_data_info" to """{"ussd_data":"${Codec.ucs2Encode("شارژ: 1000")}","ussd_action":"1","ussd_dcs":"72"}""",
        ))
        val r = ModemApi(m).ussd("*140#", reply = false)
        assertEquals(Ussd.Answer("شارژ: 1000", true), r)
        assertEquals("ussd_send", m.posts.last()["USSD_operator"]); assertEquals("*140#", m.posts.last()["USSD_send_number"])
    }

    @Test fun adminPasswordIsEncodedLikeLogin() {
        val m = Recorder()
        ModemApi(m).changeAdminPassword("old12345", "new12345")
        val p = m.posts.last()
        assertEquals(Codec.b64("old12345"), p["oldPassword"]); assertEquals(Codec.b64("new12345"), p["newPassword"])
    }

    @Test fun wifiOffSendsOnlyTheSwitch() {
        val m = Recorder()
        ModemApi(m).setWifiOn(false, "0")
        assertEquals(mapOf("isTest" to "false", "goformId" to "SET_WIFI_INFO", "wifiEnabled" to "0"), m.posts.last())
    }

    @Test fun sntpZoneIsSplitAtTheLastUnderscore() {
        val m = Recorder()
        ModemApi(m).setSntp(true, org.json.JSONObject(), "<+0330>-3:30<+0430>,J80/0,J264/0_0", "0", 2026, 11, 8, 5, 12)
        val p = m.posts.last()
        assertEquals("<+0330>-3:30<+0430>,J80/0,J264/0", p["timezone"]); assertEquals("0", p["sntp_timezone_index"])
    }

    @Test fun durationsArePersian() {
        assertEquals("1 ساعت و 30 دقیقه", Codec.duration("5421"))
        assertEquals("3 روز و 11 ساعت", Codec.duration("302000"))
    }
}

class SmsWatchTest {
    private fun m(id: String, store: Int = 1) = Sms(id, "+90", "hi", "", "1", store)

    @Test fun onlyNewMessagesAreNotified() {
        val seen = setOf("1:5")
        val fresh = SmsWatch.newOnes(listOf(m("5"), m("6"), m("5", store = 0)), seen)
        // Same id on the SIM is a different message.
        assertEquals(listOf("1:6", "0:5"), fresh.map { SmsWatch.key(it) })
    }
}

class MoreFeaturesTest {
    @Test fun wifiQrIsEscaped() {
        // Raw string: each backslash below is one real backslash.
        assertEquals("""WIFI:T:WPA;S:My\;Net;P:a\:b\\c;;""", wifiQrText("My;Net", """a:b\c""", "WPA2PSK", false))
        assertEquals("WIFI:T:nopass;S:Cafe;H:true;;", wifiQrText("Cafe", "x", "OPEN", true))
    }

    @Test fun numbersAreSplit() {
        assertEquals(listOf("0912", "+90532", "*140#"), splitNumbers("0912; +90532، *140#;0912;1"))
    }

    @Test fun draftIsSavedLikeThePage() {
        val m = Recorder(singles = mapOf("sms_cmd_status_info" to """{"sms_cmd_status_result":"3"}"""))
        assertTrue(ModemApi(m).saveDraft(listOf("0912", "0935"), "سلام"))
        val p = m.posts.last()
        assertEquals("SAVE_SMS", p["goformId"]); assertEquals("0912;0935;", p["SMSNumber"]); assertEquals(Codec.ucs2Encode("سلام"), p["SMSMessage"])
    }

    @Test fun movingAContactToTheSimSendsDelId() {
        val m = Recorder(singles = mapOf("pbm_write_flag" to """{"pbm_write_flag":"0"}"""))
        ModemApi(m).saveContact(Contact("7", "Ali", "0912", onSim = true, group = "family"), wasOnSim = false)
        val p = m.posts.last()
        assertEquals("0", p["location"]); assertEquals("-1", p["edit_index"]); assertEquals("7", p["delId"])
        ModemApi(m).saveContact(Contact("-1", "Sara", "0935", false, "friend", home = "021", email = "s@x.io"), wasOnSim = null)
        val q = m.posts.last()
        assertEquals("friend", q["groupchoose"]); assertEquals("021", q["homephone_num"]); assertEquals(Codec.ucs2Encode("s@x.io"), q["email"]); assertEquals(null, q["delId"])
    }

    @Test fun calibrationSendsMegabytes() {
        val m = Recorder()
        ModemApi(m).calibrate(true, "2048")
        val p = m.posts.last()
        assertEquals("FLOW_CALIBRATION_MANUAL", p["goformId"]); assertEquals("data", p["calibration_way"]); assertEquals("2048", p["data"])
    }
}

class ExtPagesTest {
    @Test fun urlFilterLikeThePage() {
        val m = Recorder(singles = mapOf("websURLFilters" to """{"websURLFilters":"a.com;b.com"}"""))
        val api = ModemApi(m)
        assertEquals(listOf("a.com", "b.com"), api.urlFilters())
        api.addUrlFilter("c.com"); assertEquals("c.com", m.posts.last()["addURLFilter"])
        api.deleteUrlFilters(listOf(0, 1)); assertEquals("0;1;", m.posts.last()["url_filter_delete_id"])
    }

    @Test fun guestSaveEncodesPassword() {
        val m = Recorder()
        ModemApi(m).saveGuest("Guest", "12345678", "WPA2PSK", false, "5", true, "")
        val p = m.posts.last()
        assertEquals("SET_WIFI_SSID2_SETTINGS", p["goformId"]); assertEquals(Codec.b64("12345678"), p["m_WPAPSK1"])
        assertEquals("1", p["m_EncrypType"]); assertEquals("1", p["m_NoForwarding"]); assertEquals("0", p["m_HideSSID"])
    }

    @Test fun bandsDefaultToAllWhenListIsEmpty() {
        val m = Recorder(singles = mapOf(
            "get_support_band" to """{"B1":"1","B3":"1","B7":"0","B20":"1"}""",
            "set_band_list" to """{"set_band_list":""}""",
        ))
        val (all, on) = ModemApi(m).bands()
        assertEquals(listOf("B1", "B3", "B20"), all); assertEquals(all, on)
        ModemApi(m).setBands(listOf("B3", "B20")); assertEquals("B3,B20", m.posts.last()["band_list"])
    }

    @Test fun fiveGhzLetsTheModemPickTheChannel() {
        val m = Recorder()
        ModemApi(m).setWifiAdvance("4", "TR", "6", "54", "10", "0", band = "a")
        val p = m.posts.last()
        assertEquals("auto", p["selectedChannel"]); assertEquals(null, p["abg_rate"]); assertEquals("a", p["wifi_band"])
    }

    @Test fun vpnOpenSendsAllFields() {
        val m = Recorder()
        ModemApi(m).openVpn("u", "p", "1.2.3.4", "l2tp")
        assertEquals(mapOf("isTest" to "false", "goformId" to "GOFORM_OPEN_VPN", "vpn_name" to "u", "vpn_password" to "p", "vpn_server_ip" to "1.2.3.4", "vpn_type" to "l2tp"), m.posts.last())
    }
}

class LastPagesTest {
    @Test fun hotspotsRoundTrip() {
        val m = Recorder(mapOf(
            "wifi_profile" to "p1,0,1,4,Home,WPA2PSK,TKIPCCMP,secret123,0,AA:BB:CC:00:11:22;p2,0,0,2,Cafe,OPEN,NONE,0,0,0F:00:00:00:00:00",
        ))
        val api = ModemApi(m)
        val list = api.hotspots()
        assertEquals(listOf("Home", "Cafe"), list.map { it.ssid })
        assertEquals("secret123", list[0].password); assertEquals("", list[1].password)
        api.deleteHotspot(list[1], list)
        val p = m.posts.last()
        assertEquals("WIFI_SPOT_PROFILE_UPDATE", p["goformId"]); assertEquals("delete", p["action"]); assertEquals("1", p["wifi_profile_num"])
        assertEquals("p1,0,1,4,Home,WPA2PSK,TKIPCCMP,secret123,0,AA:BB:CC:00:11:22", p["wifi_profile"])
    }

    @Test fun timeLimitsRoundTrip() {
        val m = parseTimeLimits("0+8,9,10;3+20,21")
        assertEquals(setOf(8, 9, 10), m[0]); assertEquals(setOf(20, 21), m[3])
        assertEquals("0+8,9,10;3+20,21", timeLimitText(m))
    }

    @Test fun sdListAndDelete() {
        val m = object : Transport {
            val posts = mutableListOf<String>()
            override fun send(host: String, path: String, form: String?): String {
                if (form != null) { posts += URLDecoder.decode(form, "UTF-8")
                    return if (form.contains("HTTPSHARE_ENTERFOLD")) """{"result":{"totalRecord":"2","fileInfo":[{"fileName":"Music","attribute":"document","size":"0","lastUpdateTime":""},{"fileName":"a.mp3","attribute":"file","size":"2048","lastUpdateTime":"2026"}]}}"""
                    else """{"result":"success"}""" }
                return "{}"
            }
        }
        val api = ModemApi(m)
        val (n, files) = api.sdList("/mmc2", 1)!!
        assertEquals(2, n); assertTrue(files[0].folder); assertEquals("a.mp3", files[1].name)
        assertTrue(api.sdDelete("/mmc2", listOf("a.mp3", "b")))
        assertTrue(m.posts.last().contains("name_SD_CARD=a.mp3*b*"))
    }
}

class SdPathTest {
    @Test fun downloadPathIsEncodedPerPart() {
        assertEquals("/mmc2/My%20Music/%D8%B3%D9%84%D8%A7%D9%85.mp3", sdUrlPath("/mmc2/My Music", "سلام.mp3"))
    }
}
