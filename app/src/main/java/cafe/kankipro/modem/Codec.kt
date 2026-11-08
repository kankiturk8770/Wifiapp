package cafe.kankipro.modem

import java.security.MessageDigest
import java.util.Base64

// Small pure helpers (hashes, SMS text, numbers). No Android here, so unit tests can run them.
object Codec {
    private fun hex(algo: String, s: String): String =
        MessageDigest.getInstance(algo).digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun md5(s: String) = hex("MD5", s)
    fun sha256(s: String) = hex("SHA-256", s)
    fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    // The modem keeps SMS text as UCS-2: every character is 4 hex digits.
    fun ucs2Decode(hexText: String): String {
        val h = hexText.trim()
        if (h.isEmpty() || h.length % 4 != 0 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return h
        val sb = StringBuilder()
        var i = 0
        while (i < h.length) {
            sb.append(h.substring(i, i + 4).toInt(16).toChar())
            i += 4
        }
        return sb.toString()
    }

    fun ucs2Encode(text: String): String = text.map { "%04X".format(it.code) }.joinToString("")

    // "26,11,08,04,42,10,+14" -> "2026/11/08  04:42" (the modem's own Gregorian date).
    fun smsDate(raw: String): String {
        val p = raw.split(",")
        if (p.size < 5) return raw
        return "20${p[0]}/${p[1]}/${p[2]}  ${p[3]}:${p[4]}"
    }

    // Time stamp for a new SMS, the format the modem web page sends: "yy;MM;dd;HH;mm;ss;+3.5"
    fun smsTime(now: java.util.Calendar = java.util.Calendar.getInstance()): String {
        val tz = now.get(java.util.Calendar.ZONE_OFFSET) + now.get(java.util.Calendar.DST_OFFSET)
        val hours = tz / 3_600_000.0
        val zone = (if (hours >= 0) "+" else "") + (if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString())
        fun two(n: Int) = "%02d".format(n)
        return listOf(
            two(now.get(java.util.Calendar.YEAR) % 100),
            two(now.get(java.util.Calendar.MONTH) + 1),
            two(now.get(java.util.Calendar.DAY_OF_MONTH)),
            two(now.get(java.util.Calendar.HOUR_OF_DAY)),
            two(now.get(java.util.Calendar.MINUTE)),
            two(now.get(java.util.Calendar.SECOND)),
            zone,
        ).joinToString(";")
    }

    fun bytes(v: String?): String {
        val n = v?.toDoubleOrNull() ?: return "—"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var x = n
        var u = 0
        while (x >= 1024 && u < units.size - 1) { x /= 1024; u++ }
        return if (u == 0) "${x.toLong()} B" else "%.1f %s".format(x, units[u])
    }

    // Live speed: the modem reports bytes per second.
    fun speed(v: String?): String {
        val n = v?.toDoubleOrNull() ?: return "—"
        return bytes((n).toString()) + "/s"
    }

    fun duration(seconds: String?): String {
        val s = seconds?.toLongOrNull() ?: return "—"
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s % 60}s"
    }
}
