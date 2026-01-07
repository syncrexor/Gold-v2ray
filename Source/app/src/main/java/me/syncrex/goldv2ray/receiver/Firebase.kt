package me.syncrex.goldv2ray.receiver

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.syncrex.goldv2ray.R
import me.syncrex.goldv2ray.ui.MainActivity

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class Firebase : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val channelID = getString(R.string.app_name)
        val splash = Intent(applicationContext, MainActivity::class.java)
        splash.addCategory(Intent.CATEGORY_LAUNCHER)
        splash.action = Intent.ACTION_MAIN
        splash.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        @SuppressLint("UnspecifiedImmutableFlag")
        val intent = PendingIntent.getActivity(applicationContext, 0, splash, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(applicationContext, channelID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(message.notification?.title)
            .setContentText(message.notification?.body)
            .setAutoCancel(true)
            .setContentIntent(intent)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelID, applicationContext.packageName, NotificationManager.IMPORTANCE_HIGH)
            builder.setChannelId(channelID)
            manager.createNotificationChannel(channel)
        }
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}