package me.syncrex.goldv2ray.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import es.dmoral.toasty.Toasty
import me.syncrex.goldv2ray.AngApplication
import org.json.JSONObject
import java.io.Serializable
import java.net.URI
import java.net.URLConnection

/**
 * Some extensions
 */

val Context.v2RayApplication: AngApplication?
    get() = applicationContext as? AngApplication

fun Context.toast(message: Int) {
    Toasty.normal(this, message).show()
}

fun Context.toast(message: CharSequence) {
    Toasty.normal(this, message).show()
}
fun JSONObject.putOpt(pair: Pair<String, Any?>) {
    put(pair.first, pair.second)
}
fun JSONObject.putOpt(pairs: Map<String, Any?>) {
    pairs.forEach { put(it.key, it.value) }
}

fun Context.toastSuccess(message: Int) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastSuccess(message: CharSequence) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastError(message: Int) {
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastError(message: CharSequence) {
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}

const val THRESHOLD = 1000L
const val DIVISOR = 1024.0

// Extension function to convert Long byte value to a string with speed units
fun Long.toSpeedString(): String = this.toTrafficString() + "/s"

// Extension function to convert Long byte value to a string with traffic units
fun Long.toTrafficString(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= THRESHOLD && unitIndex < units.size - 1) {
        size /= DIVISOR
        unitIndex++
    }
    return String.format("%.1f %s", size, units[unitIndex])
}

val URLConnection.responseLength: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        contentLengthLong
    } else {
        contentLength.toLong()
    }

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "").orEmpty()

/*
fun String.removeWhiteSpace(): String = replace("\\s+".toRegex(), "")

val Context.isNetworkConnected: Boolean
    get() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            manager.getNetworkCapabilities(manager.activeNetwork)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        it.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ||
                        it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } ?: false
        else
            @Suppress("DEPRECATION")
            manager.activeNetworkInfo?.isConnectedOrConnecting == true
    }*/
fun String?.removeWhiteSpace(): String? = this?.replace(" ", "")

fun String.toLongEx(): Long = toLongOrNull() ?: 0

fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
            if (onetime) context.unregisterReceiver(this)
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            })
        }
    }

inline fun <reified T : java.io.Serializable> Bundle.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializable(key) as? T
}

inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializableExtra(key) as? T
}

fun CharSequence?.isNotNullEmpty(): Boolean = (this != null && this.isNotEmpty())

fun String.concatUrl(vararg paths: String): String {
    val builder = StringBuilder(this.trimEnd('/'))

    paths.forEach { path ->
        val trimmedPath = path.trim('/')
        if (trimmedPath.isNotEmpty()) {
            builder.append('/').append(trimmedPath)
        }
    }

    return builder.toString()
}