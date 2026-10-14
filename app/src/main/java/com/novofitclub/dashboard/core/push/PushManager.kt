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

    fun createChannel() {
        val sound = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.register_sound}")
        val channel = NotificationChannel(CHANNEL_ID, "Ventas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso cada vez que se registra una venta nueva"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        private const val KEY_UPLOADED = "uploaded_token"
    }
}
