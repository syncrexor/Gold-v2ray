package me.syncrex.goldv2ray.fmt

import android.text.TextUtils
import android.util.Log
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.dto.V2rayConfig.OutboundBean
import me.syncrex.goldv2ray.dto.VmessQRCode
import me.syncrex.goldv2ray.extension.idnHost
import me.syncrex.goldv2ray.extension.isNotNullEmpty
import me.syncrex.goldv2ray.util.JsonUtil
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.util.Utils
import me.syncrex.goldv2ray.dto.NetworkType
import me.syncrex.goldv2ray.handler.V2rayConfigManager
import java.net.URI
import kotlin.text.orEmpty

object VmessFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        if (str.indexOf('?') > 0 && str.indexOf('&') > 0) {
            return parseVmessStd(str)
        }

        var allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.VMESS)

        var result = str.replace(EConfigType.VMESS.protocolScheme, "")
        result = Utils.decode(result)
        if (TextUtils.isEmpty(result)) {
            Log.w(AppConfig.TAG, "Toast decoding failed")
            return null
        }
        val vmessQRCode = JsonUtil.fromJson(result, VmessQRCode::class.java)
        // Although VmessQRCode fields are non null, looks like Gson may still create null fields
        if (TextUtils.isEmpty(vmessQRCode.add)
            || TextUtils.isEmpty(vmessQRCode.port)
            || TextUtils.isEmpty(vmessQRCode.id)
            || TextUtils.isEmpty(vmessQRCode.net)
        ) {
            Log.w(AppConfig.TAG, "Toast incorrect protocol")
            return null
        }

        config.remarks = vmessQRCode.ps
        config.server = vmessQRCode.add
        config.serverPort = vmessQRCode.port
        config.password = vmessQRCode.id
        config.method = if (TextUtils.isEmpty(vmessQRCode.scy)) AppConfig.DEFAULT_SECURITY else vmessQRCode.scy

        config.network = vmessQRCode.net ?: NetworkType.TCP.type
        config.headerType = vmessQRCode.type
        config.host = vmessQRCode.host
        config.path = vmessQRCode.path

        when (NetworkType.fromString(config.network)) {
            NetworkType.KCP -> {
                config.seed = vmessQRCode.path
            }

            //NetworkType.QUIC -> {
            //    config.quicSecurity = vmessQRCode.host
            //    config.quicKey = vmessQRCode.path
            //}

            NetworkType.GRPC -> {
                config.mode = vmessQRCode.type
                config.serviceName = vmessQRCode.path
                config.authority = vmessQRCode.host
            }
            else -> {}
        }
        config.security = vmessQRCode.tls
        config.insecure = allowInsecure
        config.sni = vmessQRCode.sni
        config.fingerPrint = vmessQRCode.fp
        config.alpn = vmessQRCode.alpn

        return config
    }

    fun toUri(config: ProfileItem): String {
        val vmessQRCode = VmessQRCode()

        vmessQRCode.v = "2"
        vmessQRCode.ps = config.remarks
        vmessQRCode.add = config.server.orEmpty()
        vmessQRCode.port = config.serverPort.orEmpty()
        vmessQRCode.id = config.password.orEmpty()
        vmessQRCode.scy = config.method.orEmpty()
        vmessQRCode.aid = "0"

        vmessQRCode.net = config.network.orEmpty()
        vmessQRCode.type = config.headerType.orEmpty()
        when (NetworkType.fromString(config.network)) {
            NetworkType.KCP -> {
                vmessQRCode.path = config.seed.orEmpty()
            }

            //NetworkType.QUIC -> {
            //    vmessQRCode.host = config.quicSecurity.orEmpty()
            //    vmessQRCode.path = config.quicKey.orEmpty()
            //}

            NetworkType.GRPC -> {
                vmessQRCode.type = config.mode.orEmpty()
                vmessQRCode.path = config.serviceName.orEmpty()
                vmessQRCode.host = config.authority.orEmpty()
            }

            else -> {}
        }
        config.host.let { if (it.isNotNullEmpty()) vmessQRCode.host = it.orEmpty() }
        config.path.let { if (it.isNotNullEmpty()) vmessQRCode.path = it.orEmpty() }

        vmessQRCode.tls = config.security.orEmpty()
        vmessQRCode.sni = config.sni.orEmpty()
        vmessQRCode.fp = config.fingerPrint.orEmpty()
        vmessQRCode.alpn = config.alpn.orEmpty()

        val json = JsonUtil.toJson(vmessQRCode)
        return Utils.encode(json)
    }

    fun parseVmessStd(str: String): ProfileItem? {
        val allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.VMESS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = getQueryParam(uri)

        config.remarks = Utils.urlDecode(uri.fragment.orEmpty()).let { if (it.isEmpty()) "none" else it }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo
        config.method = AppConfig.DEFAULT_SECURITY

        getItemFormQuery(config, queryParam, allowInsecure)

        return config
    }


    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = V2rayConfigManager.createInitOutbound(EConfigType.VMESS)

        outboundBean?.settings?.vnext?.first()?.let { vnext ->
            vnext.address = getServerAddress(profileItem)
            vnext.port = profileItem.serverPort.orEmpty().toInt()
            vnext.users[0].id = profileItem.password.orEmpty()
            vnext.users[0].security = profileItem.method
        }

        outboundBean?.streamSettings?.xhttpSettings?.mode = profileItem.xhttpMode

        val sni = outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }

}