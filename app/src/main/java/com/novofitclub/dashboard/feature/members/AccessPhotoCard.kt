package com.novofitclub.dashboard.feature.members

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.WPApiException
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.ui.CapturedPhoto
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.rememberPhotoCapture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One member's turnstile state, as `mnt-torniquete/v1/members/{id}` returns it — the "Torniquete MNT" box on the wp-admin member screen. */
private data class AccessStatus(
    val mntId: String?,
    val syncOk: Boolean?,
    val syncMessage: String,
    val syncedAt: String,
    val photoUrl: String?,
    /** The photo as uploaded, full size — shown when the thumbnail is tapped. */
    val photoFullUrl: String?,
    val photoPending: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
) {
    companion object {
        fun from(record: JSONObject): AccessStatus {
            val photo = record.obj("photo")
            val validation = record.obj("validation")
            fun strings(key: String) = validation?.arr(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } } }.orEmpty()
            val url = photo?.firstString("url")
            return AccessStatus(
                mntId = record.firstString("mnt_id"),
                syncOk = when (record.firstString("sync_status")) {
                    "ok" -> true
                    "error" -> false
                    else -> null
                },
                syncMessage = record.firstString("sync_message").orEmpty(),
                syncedAt = record.firstString("synced_at").orEmpty(),
                photoUrl = url,
                photoFullUrl = photo?.firstString("full_url") ?: url,
                photoPending = photo?.bool("pending") ?: false,
                errors = strings("errors"),
                warnings = strings("warnings"),
            )
        }
    }
}

/**
 * "Foto de acceso" for a member: take or pick the face photo the turnstile
 * recognizes, see whether the member is synced with MNT, and sync by hand.
 * Works on its own — the photo is uploaded (and sent to the turnstile) as
 * soon as it's chosen, independent of the surrounding form's Guardar.
 */
@Composable
fun AccessPhotoCard(memberId: String, api: WPApiClient) {
    val scope = rememberCoroutineScope()
    val basePath = "mnt-torniquete/v1/members/$memberId"
    var status by remember { mutableStateOf<AccessStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isWorking by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showFullPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(memberId) {
        try {
            status = AccessStatus.from(api.getObject(basePath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: WPApiException.Server) {
            unavailable = if (e.status == 404) "El sitio todavía no tiene la conexión con el torniquete instalada." else e.localizedMessage
        } catch (e: Exception) {
            unavailable = e.localizedMessage
        }
        isLoading = false
    }

    fun work(fallback: String, request: suspend () -> JSONObject) {
        scope.launch {
            isWorking = true
            try {
                val response = request()
                status = AccessStatus.from(response)
                result = (response.firstString("message") ?: fallback) to (response.bool("ok") == false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = (e.localizedMessage ?: "Error.") to true
            }
            isWorking = false
        }
    }

    // Uploads and syncs in one request (`?sync=1`), so reception sees right away whether MNT accepted the member.
    val capture = rememberPhotoCapture { photos: List<CapturedPhoto> ->
        val photo = photos.firstOrNull() ?: return@rememberPhotoCapture
        work("Foto guardada.") { api.uploadBytes("$basePath/photo", photo.jpeg, "image/jpeg", mapOf("sync" to "1")) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp)) {
        SectionLabel("Foto de acceso")
        val current = status
        val unavailableText = unavailable
        when {
            isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NovoColors.accent) }
            unavailableText != null -> Text(unavailableText, style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
            current != null -> {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NovoColors.panel)
                            .clickable(enabled = current.photoFullUrl != null) { showFullPhoto = true },
                    ) {
                        if (current.photoUrl != null) {
                            AsyncImage(model = current.photoUrl, contentDescription = "Foto de acceso", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Icon(
                                Icons.Filled.OpenInFull,
                                contentDescription = "Ver la foto en alta resolución",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(5.dp)
                                    .size(10.dp),
                            )
                        } else {
                            Icon(Icons.Outlined.AccountBox, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(44.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            current.mntId?.let { "En el torniquete (ID $it)" } ?: "Todavía no está en el torniquete",
                            style = NovoFonts.semibold(14),
                        )
                        if (current.syncOk != null && current.syncMessage.isNotEmpty()) {
                            Text((if (current.syncOk) "✅ " else "❌ ") + current.syncMessage, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                        }
                        if (current.syncedAt.isNotEmpty()) {
                            Text(current.syncedAt, style = NovoFonts.body(11).copy(color = NovoColors.textSecondary))
                        }
                        if (current.photoPending) {
                            Text("Foto nueva, falta enviarla al torniquete.", style = NovoFonts.body(12).copy(color = NovoColors.statusExpiring))
                        }
                    }
                }

                if (current.errors.isNotEmpty() || current.warnings.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        current.errors.forEach { Text("❌ $it", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary)) }
                        current.warnings.forEach { Text("⚠️ $it", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary)) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoButton("Tomar foto", Icons.Outlined.PhotoCamera, !isWorking, Modifier.weight(1f), capture::takePhoto)
                    PhotoButton("Elegir", Icons.Outlined.PhotoLibrary, !isWorking, Modifier.weight(1f), capture::pickFromGallery)
                }
                PrimaryButton(
                    "Sincronizar con torniquete",
                    onClick = { work("Listo.") { api.postObject("$basePath/sync", JSONObject()) } },
                    loading = isWorking,
                )
                result?.let { (message, isError) ->
                    Text(message, style = NovoFonts.medium(13).copy(color = if (isError) NovoColors.accent else NovoColors.statusActive))
                }
            }
        }
    }

    val full = status?.photoFullUrl
    if (showFullPhoto && full != null) FullPhotoViewer(full) { showFullPhoto = false }
}

@Composable
private fun PhotoButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, NovoColors.accent.copy(alpha = if (enabled) 1f else 0.4f)),
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(18.dp))
        Text("  $text", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
    }
}

/** The access photo full size, full screen: pinch or double-tap to zoom, drag to look around while zoomed, × to close. */
@Composable
fun FullPhotoViewer(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = url,
                contentDescription = "Foto de acceso",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}
