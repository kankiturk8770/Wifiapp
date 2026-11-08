package cafe.kankipro.modem

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plain HTTP to the modem, always over Wi-Fi.
 * The modem's Wi-Fi often has no internet, and then Android sends requests over mobile data,
 * where 192.168.0.1 does not exist. So every request is opened on the Wi-Fi network itself.
 */
class HttpTransport(ctx: Context) : Transport {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val cookies = linkedMapOf<String, String>()

    @Suppress("DEPRECATION")
    private fun wifi(): Network? = cm.allNetworks.firstOrNull {
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    override fun send(host: String, path: String, form: String?): String {
        val url = URL("http://$host$path")
        val c = (wifi()?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        try {
            c.connectTimeout = 5000
            c.readTimeout = 10000
            c.useCaches = false
            c.setRequestProperty("Referer", "http://$host/index.html")
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            synchronized(cookies) {
                if (cookies.isNotEmpty()) {
                    c.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            if (form != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            c.headerFields["Set-Cookie"]?.forEach { line ->
                val pair = line.substringBefore(';')
                val k = pair.substringBefore('=').trim()
                if (k.isNotEmpty()) synchronized(cookies) { cookies[k] = pair.substringAfter('=', "") }
            }
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code >= 400) throw ApiError("HTTP $code")
            return body
        } finally {
            c.disconnect()
        }
    }
}
