package me.syncrex.goldv2ray.plugin

import android.content.pm.ResolveInfo

class NativePlugin(resolveInfo: ResolveInfo) : ResolvedPlugin(resolveInfo) {
    init {
        check(resolveInfo.providerInfo != null)
    }

    override val componentInfo get() = resolveInfo.providerInfo!!
}