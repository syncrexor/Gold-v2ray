package me.syncrex.goldv2ray.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.syncrex.goldv2ray.AppConfig
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.dto.ProfileItem
import me.syncrex.goldv2ray.extension.toSpeedString
import me.syncrex.goldv2ray.handler.V2RayServiceManager
import me.syncrex.goldv2ray.ui.MainActivity
import me.syncrex.goldv2ray.util.Utils
import kotlin.math.min

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_PENDING_INTENT_PAUSE_V2RAY = 6 //GOLDV2RAY
    private const val NOTIFICATION_PENDING_INTENT_RESUME_V2RAY = 9 //GOLDV2RAY
    private const val NOTIFICATION_ICON_THRESHOLD = 3000

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null


    fun startSpeedNotification(currentConfig: ProfileItem?) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (speedNotificationJob != null || V2RayServiceManager.isRunning() == false) return

        lastQueryTime = System.currentTimeMillis()
        var lastZeroSpeed = false
        val outboundTags = currentConfig?.getAllOutboundTags()
        outboundTags?.remove(AppConfig.TAG_DIRECT)

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val queryTime = System.currentTimeMillis()
                val sinceLastQueryInSeconds = (queryTime - lastQueryTime) / 1000.0
                var proxyTotal = 0L
                val text = StringBuilder()
                outboundTags?.forEach {
                    val up = V2RayServiceManager.queryStats(it, AppConfig.UPLINK)
                    val down = V2RayServiceManager.queryStats(it, AppConfig.DOWNLINK)
                    if (up + down > 0) {
                        appendSpeedString(text, it, up / sinceLastQueryInSeconds, down / sinceLastQueryInSeconds)
                        proxyTotal += up + down
                    }
                }
                val directUplink = V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.UPLINK)
                val directDownlink = V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK)
                val zeroSpeed = proxyTotal == 0L && directUplink == 0L && directDownlink == 0L
                if (!zeroSpeed || !lastZeroSpeed) {
                    if (proxyTotal == 0L) {
                        appendSpeedString(text, outboundTags?.firstOrNull(), 0.0, 0.0)
                    }
                    appendSpeedString(
                        text, AppConfig.TAG_DIRECT, directUplink / sinceLastQueryInSeconds,
                        directDownlink / sinceLastQueryInSeconds
                    )
                    updateNotification(text.toString(), proxyTotal, directDownlink + directUplink)
                }
                lastZeroSpeed = zeroSpeed
                lastQueryTime = queryTime
                delay(3000)
            }
        }
    }

    //GOLDV2RAY
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return
        val title = Utils.stripSmartMarker(currentConfig?.remarks)

        mBuilder = buildBaseBuilder(service, title)
            .addAction(R.drawable.ic_delete_24dp, service.getString(R.string.pause), buildPausePendingIntent(service))
            .addAction(R.drawable.ic_delete_24dp, service.getString(R.string.notification_action_stop_v2ray), buildStopPendingIntent(service))

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    fun cancelNotification() {
        val service = getService() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            service.stopForeground(true)
        }

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    fun stopSpeedNotification(currentConfig: ProfileItem?) {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification(Utils.stripSmartMarker(currentConfig?.remarks), 0, 0) //GOLDV2RAY
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val channelName = AppConfig.RAY_NG_CHANNEL_NAME
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.DKGRAY
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText) // Emui4.1 need content text even if style is set as BigTextStyle
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.substring(0, min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    private fun getService(): Service? {
        return V2RayServiceManager.serviceControl?.get()?.getService()
    }

    //GOLDV2RAY
    fun showRunningNotification(title: String?) {
        val service = getService() ?: return

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            service,
            NOTIFICATION_PENDING_INTENT_CONTENT,
            startMainIntent,
            flags
        )

        val pauseIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_PAUSE)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            service,
            NOTIFICATION_PENDING_INTENT_PAUSE_V2RAY,
            pauseIntent,
            flags
        )

        val stopIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            service,
            NOTIFICATION_PENDING_INTENT_STOP_V2RAY,
            stopIntent,
            flags
        )

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel() else ""

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title ?: service.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.pause),
                pausePendingIntent
            )
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopPendingIntent
            )

        service.startForeground(NOTIFICATION_ID, mBuilder!!.build())
    }

    fun showPausedNotification(title: String?) {
        val service = getService() ?: return

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            service,
            NOTIFICATION_PENDING_INTENT_CONTENT,
            startMainIntent,
            flags
        )

        val resumeIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_RESUME)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            service,
            NOTIFICATION_PENDING_INTENT_RESUME_V2RAY,
            resumeIntent,
            flags
        )

        val stopIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            service,
            NOTIFICATION_PENDING_INTENT_STOP_V2RAY,
            stopIntent,
            flags
        )

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel() else ""

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title ?: service.getString(R.string.app_name))
            .setContentText(service.getString(R.string.pause))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.resume),
                resumePendingIntent
            )
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopPendingIntent
            )

        service.startForeground(NOTIFICATION_ID, mBuilder!!.build())
    }

    private fun buildBaseBuilder(service: Service, title: String?): NotificationCompat.Builder {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags
        )

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else ""

        return NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title ?: service.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
    }

    private fun buildStopPendingIntent(service: Service): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else PendingIntent.FLAG_UPDATE_CURRENT

        val i = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }
        return PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, i, flags)
    }

    private fun buildPausePendingIntent(service: Service): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else PendingIntent.FLAG_UPDATE_CURRENT

        val i = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_PAUSE)
        }
        return PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_PAUSE_V2RAY, i, flags)
    }

    private fun buildResumePendingIntent(service: Service): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else PendingIntent.FLAG_UPDATE_CURRENT

        val i = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_RESUME)
        }
        return PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESUME_V2RAY, i, flags)
    }
    //GOLDV2RAY END
}