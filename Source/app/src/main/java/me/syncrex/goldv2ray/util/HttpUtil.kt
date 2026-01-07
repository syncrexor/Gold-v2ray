package me.syncrex.goldv2ray.util

import android.util.Log
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.AppConfig.LOOPBACK
import me.syncrex.goldv2ray.BuildConfig
import me.syncrex.goldv2ray.util.Utils.encode
import me.syncrex.goldv2ray.util.Utils.urlDecode
import java.io.IOException
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.IDN
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.Inet6Address
import java.net.MalformedURLException
import java.net.URI

object HttpUtil {

    fun toIdnUrl(str: String): String {
        val url = URL(str)
        val host = url.host
        val asciiHost = IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED)
        if (host != asciiHost) {
            return str.replace(host, asciiHost)
        } else {
            return str
        }
    }

    fun toIdnDomain(domain: String): String {
        // Return as is if it's a pure IP address (IPv4 or IPv6)
        if (Utils.isPureIpAddress(domain)) {
            return domain
        }

        // Return as is if already ASCII (English domain or already punycode)
        if (domain.all { it.code < 128 }) {
            return domain
        }

        // Otherwise, convert to ASCII using IDN
        return IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED)
    }

    fun resolveHostToIP(host: String, ipv6Preferred: Boolean = false): List<String> {
        try {
            // If it's already an IP address, return it directly
            if (Utils.isPureIpAddress(host)) {
                return listOf(host)
            }

            // Get all IP addresses
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) {
                return emptyList()
            }

            // Sort addresses based on preference
            val sortedAddresses = if (ipv6Preferred) {
                addresses.sortedWith(compareByDescending { it is Inet6Address })
            } else {
                addresses.sortedWith(compareBy { it is Inet6Address })
            }
            val ipList = sortedAddresses.mapNotNull { it.hostAddress }
            Log.i(AppConfig.TAG, "Resolved IPs for $host: ${ipList.joinToString()}")
            return ipList
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to resolve host to IP", e)
            return emptyList()
        }
    }

    fun getUrlContent(url: String, timeout: Int, httpPort: Int = 0): String? {
        val conn = createProxyConnection(url, httpPort, timeout, timeout) ?: return null
        try {
            return conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
        } finally {
            conn.disconnect()
        }
        return null
    }

    @Throws(IOException::class)
    fun getUrlContentWithUserAgent(url: String?, timeout: Int = 15000, httpPort: Int = 0): String {
        var currentUrl = url
        var redirects = 0
        val maxRedirects = 3

        while (redirects++ < maxRedirects) {
            if (currentUrl == null) continue
            val conn = createProxyConnection(currentUrl, httpPort, timeout, timeout) ?: continue
            conn.setRequestProperty("User-agent", "v2rayNG/${BuildConfig.VERSION_NAME}")
            conn.connect()

            val responseCode = conn.responseCode
            when (responseCode) {
                in 300..399 -> {
                    val location = resolveLocation(conn)
                    conn.disconnect()
                    if (location.isNullOrEmpty()) {
                        throw IOException("Redirect location not found")
                    }
                    currentUrl = location
                    continue
                }

                else -> try {
                    return conn.inputStream.use { it.bufferedReader().readText() }
                } finally {
                    conn.disconnect()
                }
            }
        }
        throw IOException("Too many redirects")
    }

    /**
     * Creates an HttpURLConnection object connected through a proxy.
     *
     * @param urlStr The target URL address.
     * @param ip The IP address of the proxy server.
     * @param port The port of the proxy server.
     * @param connectTimeout The connection timeout in milliseconds (default is 15000 ms).
     * @param readTimeout The read timeout in milliseconds (default is 15000 ms).
     * @param needStream
     * @return Returns a configured HttpURLConnection object, or null if it fails.
     */
    fun createProxyConnection(
        urlStr: String,
        port: Int,
        connectTimeout: Int = 15000,
        readTimeout: Int = 15000,
        needStream: Boolean = false
    ): HttpURLConnection? {

        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            // Create a connection
            conn = if (port == 0) {
                url.openConnection()
            } else {
                url.openConnection(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress(LOOPBACK, port)
                    )
                )
            } as HttpURLConnection

            // Set connection and read timeouts
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            if (!needStream) {
                // Set request headers
                conn.setRequestProperty("Connection", "close")
                // Disable automatic redirects
                conn.instanceFollowRedirects = false
                // Disable caching
                conn.useCaches = false
            }

            //Add Basic Authorization
            url.userInfo?.let {
                conn.setRequestProperty(
                    "Authorization",
                    "Basic ${encode(urlDecode(it))}"
                )
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to create proxy connection", e)
            // If an exception occurs, close the connection and return null
            conn?.disconnect()
            return null
        }
        return conn
    }

    // Returns absolute URL string location header sets
    fun resolveLocation(conn: HttpURLConnection): String? {
        val raw = conn.getHeaderField("Location")?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        // Try check url is relative or absolute
        return try {
            val locUri = URI(raw)
            val baseUri = conn.url.toURI()
            val resolved = if (locUri.isAbsolute) locUri else baseUri.resolve(locUri)
            resolved.toURL().toString()
        } catch (_: Exception) {
            // Fallback: url resolver, also should handles //host/...
            try {
                URL(raw).toString() // absolute with protocol
            } catch (_: MalformedURLException) {
                try {
                    URL(conn.url, raw).toString()
                } catch (_: MalformedURLException) {
                    null
                }
            }
        }
    }
}