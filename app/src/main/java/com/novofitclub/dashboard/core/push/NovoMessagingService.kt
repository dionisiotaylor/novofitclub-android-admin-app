package com.novofitclub.dashboard.core.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.novofitclub.dashboard.MainActivity
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NovoMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val app = application as NovoFitClubApp
        if (app.auth.state.value.isAuthenticated) {
            scope.launch { app.push.registerToken(app.api, token) }
        }
    }

    /**
     * FCM only auto-displays notification messages while the app is in the
     * background; in the foreground we show it ourselves (like the iOS
     * willPresent → [.banner, .sound]).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(this, PushManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_novo)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), built)
    }
}
