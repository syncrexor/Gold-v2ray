package me.syncrex.goldv2ray.receiver;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Objects;
import me.syncrex.goldv2ray.R;
import me.syncrex.goldv2ray.ui.MainActivity;

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
public class Firebase extends FirebaseMessagingService
{
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message)
    {
        super.onMessageReceived(message);

        String channelID = String.valueOf(R.string.app_name);
        Intent splash = new Intent(getApplicationContext() , MainActivity.class) ;
        splash.addCategory(Intent.CATEGORY_LAUNCHER);
        splash.setAction(Intent.ACTION_MAIN);
        splash.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        @SuppressLint("UnspecifiedImmutableFlag")
        PendingIntent intent = PendingIntent.getActivity(getApplicationContext() , 0 , splash , 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext() , channelID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(Objects.requireNonNull(message.getNotification()).getTitle())
                .setContentText(message.getNotification().getBody())
                .setAutoCancel(true)
                .setContentIntent(intent);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel(channelID , getApplicationContext().getPackageName() , NotificationManager.IMPORTANCE_HIGH);
            builder.setChannelId(channelID);
            assert manager != null;
            manager.createNotificationChannel(channel);
        }
        assert manager != null;
        manager.notify((int) System.currentTimeMillis() , builder.build());
    }
}