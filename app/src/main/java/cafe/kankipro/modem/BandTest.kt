package cafe.kankipro.modem

import android.net.Network
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** What one band measured. null = the modem or the network did not give that number. */
data class BandResult(
    val band: String,
    val reached: Boolean,
    val rsrp: Int? = null,
    val sinr: Double? = null,
    val rsrq: Double? = null,
    val bars: Int? = null,
    val ping: Double? = null,
    val jitter: Double? = null,
    val loss: Double? = null,   // percent
    val mbps: Double? = null,
    val note: String = "",
) {
    /** Lower is better for games: ping, plus how much it jumps, plus lost packets (same idea as Kanki's game score). */
    val score: Double? get() = if (ping == null) null else ping + 3 * (jitter ?: 0.0) + 20 * (loss ?: 0.0)
}

object BandProbe {
    /** Band number from GET_RESIDE_BAND ("LTE Band1" -> 1) and from our names ("LTEB1" -> 1). */
    fun residentNumber(text: String): Int? = Regex("(\\d+)\\s*$").find(text.trim())?.groupValues?.get(1)?.toIntOrNull()
    fun bandNumber(band: String): Int? = Regex("(\\d+)$").find(band)?.groupValues?.get(1)?.toIntOrNull()
    fun isLte(text: String) = text.uppercase().contains("LTE")

    /**
     * Ping without root: time to open a TCP connection (one round trip) through the modem.
     * Each try is one sample; a try that fails or takes over [timeoutMs] counts as lost.
     */
    fun pingSeries(net: Network?, count: Int = 20, host: String = "1.1.1.1", port: Int = 443, timeoutMs: Int = 1500, gapMs: Long = 150): Triple<Double?, Double?, Double> {
        val all = List(count) { pingOnce(net, host, port, timeoutMs).also { Thread.sleep(gapMs) } }
        val (avg, jitter, loss) = summary(all)
        return Triple(avg, jitter, loss ?: 100.0)
    }

    /** One ping (TCP connect time) in ms, or null when it did not answer. */
    fun pingOnce(net: Network?, host: String = "1.1.1.1", port: Int = 443, timeoutMs: Int = 1500): Double? = runCatching {
        Socket().use { s ->
            net?.bindSocket(s)
            val start = System.nanoTime()
            s.connect(InetSocketAddress(host, port), timeoutMs)
            (System.nanoTime() - start) / 1e6
        }
    }.getOrNull()

    /** Average, jitter (mean change between pings) and loss % of samples; null = lost. */
    fun summary(samples: List<Double?>): Triple<Double?, Double?, Double?> {
        if (samples.isEmpty()) return Triple(null, null, null)
        val got = samples.filterNotNull()
        val loss = 100.0 * (samples.size - got.size) / samples.size
        if (got.isEmpty()) return Triple(null, null, loss)
        val jitter = if (got.size < 2) 0.0 else got.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average()
        return Triple(got.average(), jitter, loss)
    }

    /** A short download (about [bytes]) to see the speed; stops after [maxMs]. */
    fun speed(net: Network?, bytes: Int = 3_000_000, maxMs: Long = 8000): Double? = runCatching {
        val url = URL("https://speed.cloudflare.com/__down?bytes=$bytes")
        val c = (net?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 8000
        try {
            val start = System.nanoTime()
            var total = 0L
            val buf = ByteArray(32 * 1024)
            c.inputStream.use { inp ->
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    total += n
                    if ((System.nanoTime() - start) / 1_000_000 > maxMs) break
                }
            }
            val sec = (System.nanoTime() - start) / 1e9
            if (total < 50_000 || sec <= 0) null else total * 8 / sec / 1e6
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
