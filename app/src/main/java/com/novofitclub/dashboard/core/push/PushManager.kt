package com.novofitclub.dashboard.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.novofitclub.dashboard.R
import com.novofitclub.dashboard.core.net.WPApiClient
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Android counterpart of the iOS NotificationManager: creates the "ventas"
 * channel (with the cash-register sound), fetches the FCM token and uploads
 * it to novo-push/v1/register-token so the plugin can push a new-sale alert.
 */
class PushManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("novo_push", Context.MODE_PRIVATE)

    var lastError: String? = null
        private set

    /** False when the build has no google-services.json (push is then simply off). */
    val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    val needsPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /**
     * "ventas" (cash register, new sales for admins), "nuevo_cliente" (the
     * trumpet: a new member's first monthly plan) and "horario" (the alarm:
     * a coach's reminders before each shift, activity and cita, and changes
     * to their citas). On Android 8+ the sound belongs to the channel and
     * can't be changed once created, so each sound has its own channel.
     */
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            channel(CHANNEL_ID, "Ventas", "Aviso cada vez que se registra una venta nueva", R.raw.register_sound),
        )
        manager.createNotificationChannel(
            channel(SCHEDULE_CHANNEL_ID, "Horario", "Aviso 5 minutos antes de cada turno, actividad y cita, y cuando cambian tus citas", R.raw.alarma),
        )
        manager.createNotificationChannel(
            channel(NEW_MEMBER_CHANNEL_ID, "Cliente nuevo", "Trompeta cuando un cliente nuevo paga su primera mensualidad", R.raw.trompet),
        )
    }

    private fun channel(id: String, name: String, about: String, soundRes: Int) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = about
            setSound(
                Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$soundRes"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

    /**
     * Idempotent: safe to call on every launch — only hits the API when the
     * token changed since the last successful upload.
     */
    suspend fun registerToken(api: WPApiClient, token: String? = null) {
        if (!isConfigured) return
        try {
            val current = token ?: FirebaseMessaging.getInstance().token.await()
            if (current == prefs.getString(KEY_UPLOADED, null)) return
            api.postJson(
                "novo-push/v1/register-token",
                JSONObject().put("token", current).put("platform", "android"),
            )
            prefs.edit().putString(KEY_UPLOADED, current).apply()
            lastError = null
        } catch (e: Exception) {
            lastError = e.localizedMessage
        }
    }

    /** Forget the uploaded token so the next sign-in re-registers it. */
    fun reset() {
        prefs.edit().remove(KEY_UPLOADED).apply()
    }

    companion object {
        const val CHANNEL_ID = "ventas"
        /** Must match NOVO_PUSH_SCHEDULE_CHANNEL in novo-push.php. */
        const val SCHEDULE_CHANNEL_ID = "horario"
        /** Must match NOVO_PUSH_NEW_MEMBER_CHANNEL in novo-push.php. */
        const val NEW_MEMBER_CHANNEL_ID = "nuevo_cliente"
        val CHANNEL_IDS = setOf(CHANNEL_ID, SCHEDULE_CHANNEL_ID, NEW_MEMBER_CHANNEL_ID)
        private const val KEY_UPLOADED = "uploaded_token"
    }
}
