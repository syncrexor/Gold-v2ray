package me.syncrex.goldv2ray.fmt

import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.dto.V2rayConfig
import me.syncrex.goldv2ray.util.JsonUtil

object CustomFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val fullConfig = JsonUtil.fromJson(str, V2rayConfig::class.java)
        val outbound = fullConfig.getProxyOutbound()

        config.remarks = fullConfig?.remarks ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort().toString()

        return config
    }
}