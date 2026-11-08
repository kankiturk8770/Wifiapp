package cafe.kankipro.modem

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plain HTTP to the modem, always over Wi-Fi.
 * The modem's Wi-Fi often has no internet, and then Android sends requests over mobile data,
 * where 192.168.0.1 does not exist. So every request is opened on the Wi-Fi network itself.
 */
class HttpTransport(ctx: Context) : Transport, Uploader {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val cookies = linkedMapOf<String, String>()

    /** The Wi-Fi network the modem is on (tests that go through the modem use it too). */
    fun wifiNetwork(): Network? = wifi()

    /**
     * Asks Android to keep the modem's Wi-Fi up even while it has no internet (a band change cuts the
     * modem's internet for a few seconds, and some phones then leave that Wi-Fi). Call the result to let go.
     */
    fun holdWifi(): () -> Unit {
        val cb = object : ConnectivityManager.NetworkCallback() {}
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        return runCatching { cm.requestNetwork(req, cb); { runCatching { cm.unregisterNetworkCallback(cb) }; Unit } }.getOrElse { {} }
    }

    @Suppress("DEPRECATION")
    private fun wifi(): Network? = cm.allNetworks.firstOrNull {
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun open(host: String, path: String): HttpURLConnection {
        val url = URL("http://$host$path")
        val c = (wifi()?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        c.setRequestProperty("Referer", "http://$host/index.html")
        synchronized(cookies) {
            if (cookies.isNotEmpty()) c.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        return c
    }

    /** multipart/form-data, like the modem page's upload forms. */
    override fun upload(host: String, path: String, field: String, fileName: String, bytes: ByteArray, extra: Map<String, String>): String {
        val b = "----kanki" + System.nanoTime()
        val c = open(host, path)
        try {
            c.connectTimeout = 10000
            c.readTimeout = 300000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
            val head = buildString {
                for ((k, v) in extra) append("--$b\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
                append("--$b\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$fileName\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            val tail = "\r\n--$b--\r\n".toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode((head.size + bytes.size + tail.size).toLong())
            c.outputStream.use { it.write(head); it.write(bytes); it.write(tail) }
            val code = c.responseCode
            val body = (if (code >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code >= 400) throw ApiError("HTTP $code")
            return body
        } finally {
            c.disconnect()
        }
    }

    override fun download(host: String, path: String): ByteArray {
        val c = open(host, path)
        try {
            c.connectTimeout = 10000
            c.readTimeout = 120000
            val code = c.responseCode
            if (code >= 400) throw ApiError("HTTP $code")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
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
