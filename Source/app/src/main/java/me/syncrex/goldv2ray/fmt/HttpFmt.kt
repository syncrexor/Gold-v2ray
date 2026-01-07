package me.syncrex.goldv2ray.fmt

import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.dto.V2rayConfig.OutboundBean
import me.syncrex.goldv2ray.extension.isNotNullEmpty
import me.syncrex.goldv2ray.handler.V2rayConfigManager
import kotlin.text.orEmpty

object HttpFmt : FmtBase() {
    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = V2rayConfigManager.createInitOutbound(EConfigType.HTTP)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            if (profileItem.username.isNotNullEmpty()) {
                val socksUsersBean = OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
                socksUsersBean.user = profileItem.username.orEmpty()
                socksUsersBean.pass = profileItem.password.orEmpty()
                server.users = listOf(socksUsersBean)
            }
        }

        return outboundBean
    }


}