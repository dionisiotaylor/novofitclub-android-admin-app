package com.novofitclub.dashboard.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** A photo ready to upload: the JPEG bytes plus a bitmap for the preview. */
class CapturedPhoto(val jpeg: ByteArray, val preview: Bitmap)

/** Opens the camera or the gallery; see [rememberPhotoCapture]. */
class PhotoCapture internal constructor(
    private val takePicture: () -> Unit,
    private val pickPhotos: () -> Unit,
) {
    fun takePhoto() = takePicture()
    fun pickFromGallery() = pickPhotos()
}

/**
 * Camera and gallery for uploads (Congelados evidence, the MNT access photo),
 * same output as the iOS app's `uploadJPEG()`: upright, at most 1600 px on
 * the longest side, JPEG at 85 %. [multiple] lets the gallery pick up to 10.
 */
@Composable
fun rememberPhotoCapture(multiple: Boolean = false, onPhotos: (List<CapturedPhoto>) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureFile = remember { File(context.cacheDir, "captures").apply { mkdirs() }.resolve("capture.jpg") }
    val captureUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", captureFile) }

    fun deliver(uris: List<Uri>, cleanup: Boolean = false) {
        if (uris.isEmpty()) return
        scope.launch {
            val photos = withContext(Dispatchers.IO) { uris.mapNotNull { prepareUpload(context, it) } }
            if (cleanup) captureFile.delete()
            if (photos.isNotEmpty()) onPhotos(photos)
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) deliver(listOf(captureUri), cleanup = true)
    }
    val gallerySingle = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliver(listOfNotNull(uri))
    }
    val galleryMultiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        deliver(uris)
    }
    return remember {
        PhotoCapture(
            takePicture = { camera.launch(captureUri) },
            pickPhotos = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (multiple) galleryMultiple.launch(request) else gallerySingle.launch(request)
            },
        )
    }
}

private const val MAX_SIDE = 1600
private const val QUALITY = 85

private fun prepareUpload(context: Context, uri: Uri): CapturedPhoto? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null

    // Decode near the target size, then scale exactly.
    var sample = 1
    while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    val upright = if (scale < 1f || rotation != 0f) {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else {
        decoded
    }
    val out = ByteArrayOutputStream()
    upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
    CapturedPhoto(out.toByteArray(), upright)
}.getOrNull()
