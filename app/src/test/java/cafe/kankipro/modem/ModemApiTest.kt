package cafe.kankipro.modem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

// A fake modem that answers like the real web API.
private class FakeModem(
    val ld: String = "",
    val rd: String = "",
    val password: String = "admin",
    val adHash: (String) -> String = { Codec.md5(Codec.md5(it) + "RDX") },
) : Transport {
    val posts = mutableListOf<Map<String, String>>()

    private fun form(s: String) = s.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    override fun send(host: String, path: String, form: String?): String {
        if (form == null) {
            val q = form(path.substringAfter('?'))
            val cmd = q["cmd"]!!
            return when {
                cmd == "LD" -> """{"LD":"$ld"}"""
                cmd.endsWith("RD") -> """{"wa_inner_version":"W1","cr_version":"C1","RD":"$rd"}"""
                cmd == "psw_fail_num_str" -> """{"psw_fail_num_str":"4"}"""
                cmd == "sms_data_total" ->
                    """{"messages":[{"id":"7","number":"+98912","content":"0633064406270645","tag":"1","date":"26,11,08,04,42,10,+14"}]}"""
                else -> "{}"
            }
        }
        val f = form(form)
        posts += f
        return when (f["goformId"]) {
            "LOGIN" -> {
                val good = if (ld.isNotEmpty()) Codec.sha256(Codec.sha256(password).uppercase() + ld).uppercase()
                else Codec.b64(password)
                if (f["password"] == good) """{"result":"0"}""" else """{"result":"3"}"""
            }
            else -> {
                val ok = rd.isEmpty() || f["AD"] == adHash("W1C1")
                if (ok) """{"result":"success"}""" else """{"result":"failure"}"""
            }
        }
    }
}

class ModemApiTest {
    @Test fun newFirmwareLoginUsesLd() {
        val m = FakeModem(ld = "ABC123")
        val api = ModemApi(m)
        assertEquals(ModemApi.Login.Ok, api.login("admin"))
        assertEquals("sha256", api.loginKind)
    }

    @Test fun oldFirmwareLoginUsesBase64() {
        val api = ModemApi(FakeModem())
        assertEquals(ModemApi.Login.Ok, api.login("admin"))
        assertEquals("base64", api.loginKind)
    }

    @Test fun wrongPasswordSaysHowManyLeft() {
        val r = ModemApi(FakeModem(ld = "X")).login("nope")
        assertEquals(ModemApi.Login.Wrong("4"), r)
    }

    @Test fun adIsFoundAndKept() {
        // This modem wants the SHA256 form; md5 fails first, then it works and is kept.
        val m = FakeModem(rd = "RDX", adHash = { Codec.sha256(Codec.sha256(it) + "RDX") })
        val api = ModemApi(m)
        assertTrue(api.reboot())
        assertTrue(api.reboot())
        assertEquals(3, m.posts.size) // 1 failure, 1 success, then straight success
    }

    @Test fun smsIsReadable() {
        val list = ModemApi(FakeModem()).sms()
        assertEquals("سلام", list[0].text)
        assertTrue(list[0].unread)
        assertEquals("2026/11/08  04:42", list[0].date)
    }

    @Test fun smsTextRoundTrip() {
        val t = "سلام hello ۱۲۳"
        assertEquals(t, Codec.ucs2Decode(Codec.ucs2Encode(t)))
    }
}
