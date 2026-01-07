package me.syncrex.goldv2ray.util

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.util.Base64
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.util.Log
import android.util.Patterns
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.AppConfig.ANG_PACKAGE
import me.syncrex.goldv2ray.AppConfig.LOOPBACK
import java.io.IOException
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.net.URI
import java.net.InetAddress

object Utils {
    private val IPV4_REGEX =
        Regex("^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$")
    private val IPV6_REGEX = Regex("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$")

    /**
     * convert string to editalbe for kotlin
     *
     * @param text
     * @return
     */
    fun getEditable(text: String?): Editable {
        return Editable.Factory.getInstance().newEditable(text.orEmpty())
    }

    /**
     * find value in array position
     */
    fun arrayFind(array: Array<out String>, value: String): Int {
        return array.indexOf(value)
    }

    fun parseInt(str: String?, default: Int = 0): Int {
        return str?.toIntOrNull() ?: default
    }

    /**
     * get text from clipboard
     */
    fun getClipboard(context: Context): String {
        return try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cmb.primaryClip?.getItemAt(0)?.text.toString()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to get clipboard content", e)
            ""
        }
    }

    /**
     * set text to clipboard
     */
    fun setClipboard(context: Context, content: String) {
        try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(null, content)
            cmb.setPrimaryClip(clipData)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to set clipboard content", e)
        }
    }

    /**
     * base64 decode
     */
    fun decode(text: String?): String {
        return tryDecodeBase64(text) ?: text?.trimEnd('=')?.let { tryDecodeBase64(it) }.orEmpty()
    }

    fun tryDecodeBase64(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(Charsets.UTF_8)
        } catch (e: Exception) {

            Log.e(AppConfig.TAG, "Failed to decode standard base64", e)
        }
        try {
            return Base64.decode(text, Base64.NO_WRAP.or(Base64.URL_SAFE)).toString(Charsets.UTF_8)
        } catch (e: Exception) {

            Log.e(AppConfig.TAG, "Failed to decode URL-safe base64", e)
        }
        return null
    }

    /**
     * base64 encode
     */
    fun encode(text: String, removePadding : Boolean = false): String {
        return try {
            var encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            if (removePadding) {
                encoded = encoded.trimEnd('=')
            }
            encoded
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to encode text to base64", e)
            ""
        }
    }

    /**
     * is ip address
     */
    fun isIpAddress(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        try {
            var addr = value.trim()
            if (addr.isEmpty()) return false
            //CIDR
            if (addr.contains("/")) {
                val arr = addr.split("/")
                if (arr.size == 2 && arr[1].toIntOrNull() != null && arr[1].toInt() > -1) {
                    addr = arr[0]
                }
            }
            // "::ffff:192.168.173.22"
            // "[::ffff:192.168.173.22]:80"
            if (addr.startsWith("::ffff:") && '.' in addr) {
                addr = addr.drop(7)
            } else if (addr.startsWith("[::ffff:") && '.' in addr) {
                addr = addr.drop(8).replace("]", "")
            }
            // addr = addr.toLowerCase()
            val octets = addr.split('.')
            if (octets.size == 4) {
                if (octets[3].contains(":")) {
                    addr = addr.substring(0, addr.indexOf(":"))
                }
                return isIpv4Address(addr)
            }
            // Ipv6addr [2001:abc::123]:8080
            return isIpv6Address(addr)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to validate IP address", e)
            return false
        }
    }

    fun isPureIpAddress(value: String): Boolean {
        return isIpv4Address(value) || isIpv6Address(value)
    }

    fun isDomainName(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false

        // Must not be an IP address and must be a valid URL format
        return !isPureIpAddress(input) && isValidUrl(input)
    }

    private fun isIpv4Address(value: String): Boolean {
        return IPV4_REGEX.matches(value)
    }

    private fun isIpv6Address(value: String): Boolean {
        var addr = value
        if (addr.startsWith("[") && addr.endsWith("]")) {
            addr = addr.drop(1).dropLast(1)
        }
        return IPV6_REGEX.matches(addr)
    }

    fun isCoreDNSAddress(s: String): Boolean {
        return s.startsWith("https") ||
                s.startsWith("tcp") ||
                s.startsWith("quic") ||
                s == "localhost"
    }

    /**
     * is valid url
     */
    fun isValidUrl(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        return try {
            Patterns.WEB_URL.matcher(value).matches() ||
                    Patterns.DOMAIN_NAME.matcher(value).matches() ||
                    URLUtil.isValidUrl(value)
        } catch (e: Exception) {
        Log.e(AppConfig.TAG, "Failed to validate URL", e)
        false
        }
    }

    fun openUri(context: Context, uriString: String) {
        try {
            val uri = uriString.toUri()
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to open URI", e)
        }
    }

    /**
     * uuid
     */
    fun getUuid(): String {
        return try {
            UUID.randomUUID().toString().replace("-", "")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to generate UUID", e)
            ""
        }
    }

    fun urlDecode(url: String): String {
        return try {
            URLDecoder.decode(url, Charsets.UTF_8.toString())
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to decode URL", e)
            url
        }
    }

    fun urlEncode(url: String): String {
        return try {
            URLEncoder.encode(url, Charsets.UTF_8.toString()).replace("+", "%20")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to encode URL", e)
            url
        }
    }


    /**
     * readTextFromAssets
     */
    fun readTextFromAssets(context: Context?, fileName: String): String {
        if (context == null) return ""
        return try {
            context.assets.open(fileName).use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to read asset file: $fileName", e)
            ""
            }
    }

    fun userAssetPath(context: Context?): String {
        if (context == null) return ""
        return try {
            context.getExternalFilesDir(AppConfig.DIR_ASSETS)?.absolutePath
                ?: context.getDir(AppConfig.DIR_ASSETS, 0).absolutePath
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to get user asset path", e)
            ""
        }
    }

    fun getDeviceIdForXUDPBaseKey(): String {
        return try {
            val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
            Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to generate device ID", e)
            ""
        }
    }

    fun getDarkModeStatus(context: Context): Boolean {
        return context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK != UI_MODE_NIGHT_NO
    }

    fun getIpv6Address(address: String?): String {
        if (address.isNullOrEmpty()) return ""
        return if (isIpv6Address(address) && !address.contains('[') && !address.contains(']')) {
            "[$address]"
        } else {
            address
        }
    }

    fun getSysLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        LocaleList.getDefault()[0]
    } else {
        Locale.getDefault()
    }

    fun fixIllegalUrl(str: String): String {
        return str.replace(" ", "%20")
            .replace("|","%7C")
    }

    fun findFreePort(ports: List<Int>): Int {
        for (port in ports) {
            try {
                return ServerSocket(port).use { it.localPort }
            } catch (ex: IOException) {
                continue  // try next port
            }
        }

        // if the program gets here, no port in the range was found
        throw IOException("no free port found")
    }

    fun isValidSubUrl(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        try {
            if (URLUtil.isHttpsUrl(value)) return true

            if (URLUtil.isHttpUrl(value)) {
                if (value.contains(LOOPBACK)) return true

                //Check private ip address
                val uri = URI(fixIllegalUrl(value))
                if (isIpAddress(uri.host)) {
                    AppConfig.PRIVATE_IP_LIST.forEach {
                        if (isIpInCidr(uri.host, it)) return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to validate subscription URL", e)
        }
        return false
    }

    private fun inetAddressToLong(ip: InetAddress): Long {
        val bytes = ip.address
        var result: Long = 0
        for (i in bytes.indices) {
            result = result shl 8 or (bytes[i].toInt() and 0xff).toLong()
        }
        return result
    }

    fun isIpInCidr(ip: String, cidr: String): Boolean {
        try {
            if (!isIpAddress(ip)) return false

            // Parse CIDR (e.g., "192.168.1.0/24")
            val (cidrIp, prefixLen) = cidr.split("/")
            val prefixLength = prefixLen.toInt()

            // Convert IP and CIDR's IP portion to Long
            val ipLong = inetAddressToLong(InetAddress.getByName(ip))
            val cidrIpLong = inetAddressToLong(InetAddress.getByName(cidrIp))

            // Calculate subnet mask (e.g., /24 → 0xFFFFFF00)
            val mask = if (prefixLength == 0) 0L else (-1L shl (32 - prefixLength))

            // Check if they're in the same subnet
            return (ipLong and mask) == (cidrIpLong and mask)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to check if IP is in CIDR", e)
            return false
        }
    }

    fun receiverFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.RECEIVER_EXPORTED
    } else {
        ContextCompat.RECEIVER_NOT_EXPORTED
    }

    //GOLDV2RAY
    fun isXray(): Boolean = ANG_PACKAGE.startsWith("me.syncrex.goldv2ray")

    fun stripSmartMarker(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return text.removePrefix(AppConfig.SMART_MARKER).trim()
    }

    //GOLDV2RAY END
}

