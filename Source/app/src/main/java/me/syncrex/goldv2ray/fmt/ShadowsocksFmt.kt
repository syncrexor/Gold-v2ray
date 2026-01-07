package me.syncrex.goldv2ray.fmt

import android.util.Log
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.dto.NetworkType
import me.syncrex.goldv2ray.extension.idnHost
import me.syncrex.goldv2ray.util.Utils
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.dto.V2rayConfig.OutboundBean
import me.syncrex.goldv2ray.handler.V2rayConfigManager
import java.net.URI

object ShadowsocksFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        return parseSip002(str) ?: parseLegacy(str)
    }

    fun parseSip002(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null
        if (uri.port <= 0) return null
        if (uri.userInfo.isNullOrEmpty()) return null

        config.remarks = Utils.urlDecode(uri.fragment.orEmpty()).let { if (it.isEmpty()) "none" else it }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()

        val result = if (uri.userInfo.contains(":")) {
            uri.userInfo.split(":", limit = 2)
        } else {
            Utils.decode(uri.userInfo).split(":", limit = 2)
        }
        if (result.count() == 2) {
            config.method = result.first()
            config.password = result.last()
        }

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)

            if (queryParam["plugin"]?.contains("obfs=http") == true) {
                val queryPairs = HashMap<String, String>()
                for (pair in queryParam["plugin"]?.split(";") ?: listOf()) {
                    val idx = pair.split("=")
                    if (idx.count() == 2) {
                        queryPairs.put(idx.first(), idx.last())
                    }
                }
                config.network = NetworkType.TCP.type
                config.headerType = "http"
                config.host = queryPairs["obfs-host"]
                config.path = queryPairs["path"]
            }
        }
        return config
    }

    fun parseLegacy(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)
        var result = str.replace(EConfigType.SHADOWSOCKS.protocolScheme, "")
        val indexSplit = result.indexOf("#")
        if (indexSplit > 0) {
            try {
                config.remarks =
                    Utils.urlDecode(result.substring(indexSplit + 1, result.length))
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to decode remarks in SS legacy URL", e)
            }

            result = result.substring(0, indexSplit)
        }

        //part decode
        val indexS = result.indexOf("@")
        result = if (indexS > 0) {
            Utils.decode(result.substring(0, indexS)) + result.substring(
                indexS,
                result.length
            )
        } else {
            Utils.decode(result)
        }

        val legacyPattern = "^(.+?):(.*)@(.+?):(\\d+?)/?$".toRegex()
        val match = legacyPattern.matchEntire(result) ?: return null

        config.server = match.groupValues[3].removeSurrounding("[", "]")
        config.serverPort = match.groupValues[4]
        config.password = match.groupValues[2]
        config.method = match.groupValues[1].lowercase()

        return config
    }

    fun toUri(config: ProfileItem): String {
        val pw = "${config.method}:${config.password}"

        return toUri(config, Utils.encode(pw, true), null)
    }

    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = V2rayConfigManager.createInitOutbound(EConfigType.SHADOWSOCKS)
        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.password = profileItem.password
            server.method = profileItem.method
        }

        val sni = outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }
}