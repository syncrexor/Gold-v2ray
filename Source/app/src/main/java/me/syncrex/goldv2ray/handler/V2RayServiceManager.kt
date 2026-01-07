package me.syncrex.goldv2ray.handler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.dto.EConfigType
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.extension.toast
import me.syncrex.goldv2ray.service.ServiceControl
import me.syncrex.goldv2ray.service.V2RayProxyOnlyService
import me.syncrex.goldv2ray.service.V2RayVpnService
import me.syncrex.goldv2ray.util.MessageUtil
import me.syncrex.goldv2ray.util.Utils
import java.lang.ref.SoftReference

object V2RayServiceManager {
    private val coreController: CoreController = Libv2ray.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    private var isReceiverRegistered = false //GOLDV2RAY

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            Seq.setContext(value?.get()?.getService()?.applicationContext)
            Libv2ray.initCoreEnv(Utils.userAssetPath(value?.get()?.getService()), Utils.getDeviceIdForXUDPBaseKey())
        }

    fun startVServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        startContextService(context)
        return true
    }

    fun startVService(context: Context, guid: String? = null) {
        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }
        startContextService(context)
    }

    fun stopVService(context: Context) {
        context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    fun isRunning() = coreController.isRunning

    fun getRunningServerName() = Utils.stripSmartMarker(currentConfig?.remarks) //GOLDV2RAY

    private fun startContextService(context: Context) {
        if (coreController.isRunning) {
            return
        }
        val guid = MmkvManager.getSelectServer() ?: return
        val config = MmkvManager.decodeServerConfig(guid) ?: return
        if (config.configType != EConfigType.CUSTOM
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) return
//        val result = V2rayConfigUtil.getV2rayConfig(context, guid)
//        if (!result.status) return

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING) == true) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            context.toast(R.string.toast_services_start)
        }
        val intent = if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: AppConfig.VPN) == AppConfig.VPN) {
            Intent(context.applicationContext, V2RayVpnService::class.java)
        } else {
            Intent(context.applicationContext, V2RayProxyOnlyService::class.java)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            //GOLDV2RAY
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent == null) {
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Error starting V2RayVpnService", e)
                }
            }
        } else {
            context.startService(intent)
        }
    }

    fun startCoreLoop(): Boolean {
        if (coreController.isRunning) {
            return false
        }
        val service = getService() ?: return false
        val guid = MmkvManager.getSelectServer() ?: return false
        val config = MmkvManager.decodeServerConfig(guid) ?: return false
        val result = V2rayConfigManager.getV2rayConfig(service, guid)
        if (!result.status)
            return false

        try {
            //GOLDV2RAY
            if (!isReceiverRegistered) {
                val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
                mFilter.addAction(Intent.ACTION_SCREEN_ON)
                mFilter.addAction(Intent.ACTION_SCREEN_OFF)
                mFilter.addAction(Intent.ACTION_USER_PRESENT)
                ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())
                isReceiverRegistered = true
            }
            //GOLDV2RAY END
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to register broadcast receiver", e)
            return false
        }

        currentConfig = config

        try {
            coreController.startLoop(result.content)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to start Core loop", e)
            return false
        }

        if (coreController.isRunning == false) {
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, "")
            NotificationManager.cancelNotification()
            return false
        }

        try {
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
            NotificationManager.showNotification(currentConfig)
            NotificationManager.startSpeedNotification(currentConfig)

            PluginServiceManager.runPlugin(service, config, result.socksPort)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to startup service", e)
            return false
        }
        return true
    }

    //GOLDV2RAY
    fun stopCoreLoop(
        cancelNotification: Boolean = true,
        unregisterReceiver: Boolean = true
    ): Boolean {
        val service = getService() ?: return false

        if (coreController.isRunning) {
            CoroutineScope(Dispatchers.IO).launch {
                try { coreController.stopLoop() }
                catch (e: Exception) { Log.e(AppConfig.TAG, "Failed to stop V2Ray loop", e) }
            }
        }

        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")

        if (cancelNotification) {
            NotificationManager.cancelNotification()
        } else {
            NotificationManager.stopSpeedNotification(currentConfig)
        }

        if (unregisterReceiver) {
            try {
                service.unregisterReceiver(mMsgReceive)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to unregister broadcast receiver", e)
            }
        }

        PluginServiceManager.stopPlugin()
        return true
    }


    fun queryStats(tag: String, link: String): Long {
        return coreController.queryStats(tag, link)
    }

    private fun measureV2rayDelay() {
        if (coreController.isRunning == false) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val service = getService() ?: return@launch
            var time = -1L
            var errorStr = ""
            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to measure delay with primary URL", e)
                errorStr = e.message?.substringAfter("\":") ?: "empty message"
            }

            if (time == -1L) {
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to measure delay with alternative URL", e)
                    errorStr = e.message?.substringAfter("\":") ?: "empty message"
                }
            }

            var result = if (time >= 0) {
                service.getString(R.string.connection_test_available, time)
            } else {
                service.getString(R.string.connection_test_error, errorStr)
            }

            //GOLDV2RAY
            if      (time in 1..999)     result += "\n" + service.getString(R.string.super_speed)
            else if (time in 1000..1999) result += "\n" + service.getString(R.string.good_speed)
            else if (time in 2000..2999) result += "\n" + service.getString(R.string.norma_speed)
            else if (time > 3000)              result += "\n" + service.getString(R.string.poor_speed)
            //GOLDV2RAY END

            MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_SUCCESS, result)

            // Only fetch IP info if the delay test was successful
            if (time >= 0) {
                SpeedtestManager.getRemoteIPInfo()?.let { ip ->
                    MessageUtil.sendMsg2UI(
                        service,
                        AppConfig.MSG_MEASURE_DELAY_SUCCESS,
                        "$result\n$ip"
                    )
                }
            }
        }
    }

    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    private class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long {
            return 0
        }

        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            // called by go
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to stop service in callback", e)
                -1
            }
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0
        }
    }

    private class ReceiveMessageHandler : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (coreController.isRunning) {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    Log.i(AppConfig.TAG, "Stop Service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    //startV2rayPoint()
                    Log.i(AppConfig.TAG, "Restart Service")
                    serviceControl.stopService()
                    Thread.sleep(500L)
                    startVService(serviceControl.getService())
                }

                //GOLDV2RAY
                AppConfig.MSG_STATE_PAUSE -> {
                    Log.i(AppConfig.TAG, "Pause Service")
                    serviceControl.pauseService()
                }

                AppConfig.MSG_STATE_RESUME -> {
                    Log.i(AppConfig.TAG, "Resume Service")
                    serviceControl.resumeService()
                }
                //GOLDV2RAY END

                AppConfig.MSG_MEASURE_DELAY -> {
                    measureV2rayDelay()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(AppConfig.TAG, "SCREEN_OFF, stop querying stats")
                    NotificationManager.stopSpeedNotification(currentConfig)
                }

                Intent.ACTION_SCREEN_ON -> {
                    Log.i(AppConfig.TAG, "SCREEN_ON, start querying stats")
                    NotificationManager.startSpeedNotification(currentConfig)
                }
            }
        }
    }
}