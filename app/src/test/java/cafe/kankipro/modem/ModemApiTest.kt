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
