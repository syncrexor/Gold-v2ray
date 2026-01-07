package me.syncrex.goldv2ray.plugin

import android.graphics.drawable.Drawable

abstract class Plugin {
    abstract val id: String
    abstract val label: CharSequence
    abstract val version: Int
    abstract val versionName: String
    open val icon: Drawable? get() = null
    open val defaultConfig: String? get() = null
    open val packageName: String get() = ""
    open val directBootAware: Boolean get() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return id == (other as Plugin).id
    }

    override fun hashCode() = id.hashCode()
}