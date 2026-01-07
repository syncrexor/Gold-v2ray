package me.syncrex.goldv2ray.plugin

import android.content.pm.ComponentInfo
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import me.syncrex.goldv2ray.AngApplication
import me.syncrex.goldv2ray.plugin.PluginManager.loadString

abstract class ResolvedPlugin(protected val resolveInfo: ResolveInfo) : Plugin() {
    protected abstract val componentInfo: ComponentInfo

    override val id by lazy { componentInfo.loadString(PluginContract.METADATA_KEY_ID)!! }
    override val version by lazy {
        getPackageInfo(componentInfo.packageName).versionCode
    }
    override val versionName: String by lazy {
        getPackageInfo(componentInfo.packageName).versionName!!
    }
    override val label: CharSequence get() = resolveInfo.loadLabel(AngApplication.application.packageManager)
    override val icon: Drawable get() = resolveInfo.loadIcon(AngApplication.application.packageManager)
    override val packageName: String get() = componentInfo.packageName
    override val directBootAware get() = Build.VERSION.SDK_INT < 24 || componentInfo.directBootAware

    fun getPackageInfo(packageName: String) = AngApplication.application.packageManager.getPackageInfo(
        packageName, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    )!!
}