package me.syncrex.goldv2ray.plugin

import android.content.Intent
import android.content.pm.PackageManager
import me.syncrex.goldv2ray.AngApplication

class PluginList : ArrayList<Plugin>() {
    init {
        addAll(
            AngApplication.application.packageManager.queryIntentContentProviders(
                Intent(PluginContract.ACTION_NATIVE_PLUGIN), PackageManager.GET_META_DATA)
                .filter { it.providerInfo.exported }.map { NativePlugin(it) })
    }

    val lookup = mutableMapOf<String, Plugin>().apply {
        for (plugin in this@PluginList.toList()) {
            fun check(old: Plugin?) {
                if (old != null && old != plugin) {
                    this@PluginList.remove(old)
                }
                /* if (old != null && old !== plugin) {
                     val packages = this@PluginList.filter { it.id == plugin.id }
                         .joinToString { it.packageName }
                     val message = "Conflicting plugins found from: $packages"
                     Toast.makeText(SagerNet.application, message, Toast.LENGTH_LONG).show()
                     throw IllegalStateException(message)
                 }*/
            }
            check(put(plugin.id, plugin))
        }
    }
}