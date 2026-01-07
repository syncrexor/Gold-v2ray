package me.syncrex.goldv2ray.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import me.syncrex.goldv2ray.AppConfig.MSG_MEASURE_CONFIG
import me.syncrex.goldv2ray.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import me.syncrex.goldv2ray.AppConfig.MSG_MEASURE_CONFIG_SUCCESS
import me.syncrex.goldv2ray.util.MessageUtil
import me.syncrex.goldv2ray.handler.SpeedtestManager
import me.syncrex.goldv2ray.util.Utils
import go.Seq
import kotlinx.coroutines.*
import libv2ray.Libv2ray
import java.util.concurrent.Executors
import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.extension.serializable
import me.syncrex.goldv2ray.handler.MmkvManager
import me.syncrex.goldv2ray.handler.PluginServiceManager
import me.syncrex.goldv2ray.handler.V2rayConfigManager

class V2RayTestService : Service() {
    private val realTestScope by lazy { CoroutineScope(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()) }

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Libv2ray.initCoreEnv(Utils.userAssetPath(this), Utils.getDeviceIdForXUDPBaseKey())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("key", 0)) {
            MSG_MEASURE_CONFIG -> {
                val guid = intent.serializable<String>("content") ?: ""
                realTestScope.launch {
                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))
                }
            }
            MSG_MEASURE_CONFIG_CANCEL -> {
                realTestScope.coroutineContext[Job]?.cancelChildren()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.HYSTERIA2) {
            val delay = PluginServiceManager.realPingHy2(this, config)
            return delay
        } else {
            val configResult = V2rayConfigManager.getV2rayConfig4Speedtest(this, guid)
            if (!configResult.status) {
                return retFailure
            }
            return SpeedtestManager.realPing(configResult.content)
        }
    }
}