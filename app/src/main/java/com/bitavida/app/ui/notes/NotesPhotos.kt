package com.bitavida.app.ui.notes

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.data.notes.NoteImageStore
import com.bitavida.app.platform.observations.ObservationCameraFiles
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.BitavidaSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reuses the approved external camera/FileProvider and zero-permission system photo picker. */
@Composable
internal fun NotesPhotoCoordinator(viewModel: NotesViewModel) {
    val context = LocalContext.current
    val store = remember(context) { NoteImageStore(context.applicationContext) }
    var pendingValues by rememberSaveable { mutableStateOf<List<String>?>(null) }

    fun pending(): PendingNotesPhoto? = pendingValues?.let(PendingNotesPhoto::restore)

    suspend fun cleanCamera(request: PendingNotesPhoto, knownUri: Uri? = null) {
        if (request.request.source != NotesPhotoSource.CAMERA) return
        withContext(Dispatchers.IO) {
            try {
                val uri = knownUri ?: request.cameraToken?.let { ObservationCameraFiles.uri(context, it) }
                if (uri != null) runCatching {
                    context.revokeUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed URI lookup must still release the private camera file.
            } finally {
                runCatching { ObservationCameraFiles.delete(context, request.cameraToken) }
            }
        }
    }

    fun finish(request: PendingNotesPhoto, galleryUri: Uri? = null, shouldImport: Boolean = true) {
        pendingValues = null
        viewModel.launchPhotoWork {
            var imported: String? = null
            var resolvedUri = galleryUri
            var attached = false
            try {
                if (request.request.source == NotesPhotoSource.CAMERA) {
                    resolvedUri = withContext(Dispatchers.IO) {
                        request.cameraToken?.let { ObservationCameraFiles.uri(context, it) }
                    }
                }
                if (shouldImport && resolvedUri != null && viewModel.isImageRequestCurrent(request.request)) {
                    imported = store.importImage(viewModel.actor, resolvedUri)
                    attached = viewModel.acceptImportedImage(request.request, imported)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                viewModel.finishImageRequest(request.request,
                    "No se pudo agregar la imagen. Puedes reintentar o guardar sin ella.")
            } finally {
                if (!attached && imported != null) runCatching { store.discard(viewModel.actor, imported) }
                try { cleanCamera(request, resolvedUri) }
                finally { viewModel.finishImageRequest(request.request) }
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val request = pending()
        pendingValues = null
        if (request?.request?.source == NotesPhotoSource.GALLERY) finish(request, uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val request = pending()
        pendingValues = null
        if (request?.request?.source == NotesPhotoSource.CAMERA) finish(request, shouldImport = captured)
    }

    LaunchedEffect(viewModel) {
        viewModel.photoRequests.collect { request ->
            if (!viewModel.isImageRequestCurrent(request)) return@collect
            if (pendingValues != null) {
                viewModel.finishImageRequest(request, "Termina la selección de imagen antes de volver a intentarlo.")
                return@collect
            }
            // Finite camera setup survives screen disposal long enough to release any created file.
            viewModel.launchPhotoWork {
                var createdCamera: PendingNotesPhoto? = null
                try {
                    when (request.source) {
                        NotesPhotoSource.GALLERY -> {
                            if (!viewModel.isImageRequestCurrent(request)) return@launchPhotoWork
                            pendingValues = PendingNotesPhoto(request).savedValues()
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        NotesPhotoSource.CAMERA -> {
                            val capture = withContext(Dispatchers.IO) { ObservationCameraFiles.create(context) }
                            createdCamera = PendingNotesPhoto(request, capture.token)
                            if (!viewModel.isImageRequestCurrent(request)) {
                                cleanCamera(createdCamera, capture.uri)
                                return@launchPhotoWork
                            }
                            pendingValues = createdCamera.savedValues()
                            camera.launch(capture.uri)
                        }
                    }
                } catch (_: ActivityNotFoundException) {
                    createdCamera?.let { cleanCamera(it) }
                    pendingValues = null
                    viewModel.finishImageRequest(request, "No hay una aplicación disponible para obtener la imagen.")
                } catch (_: SecurityException) {
                    createdCamera?.let { cleanCamera(it) }
                    pendingValues = null
                    viewModel.finishImageRequest(request, "Android no permitió abrir la cámara o el selector de imágenes.")
                } catch (_: Exception) {
                    createdCamera?.let { cleanCamera(it) }
                    pendingValues = null
                    viewModel.finishImageRequest(request, "No se pudo iniciar la selección de imagen.")
                }
            }
        }
    }
}

private data class NoteBitmapState(val bitmap: Bitmap? = null, val failed: Boolean = false)

/** Decodes only composed images, sampled on IO; no unbounded global bitmap cache. */
@Composable
internal fun NotesImage(
    id: String,
    readImage: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = false,
    description: String = "Imagen de la nota",
    maximumHeight: Dp = 360.dp,
    cropThumbnailToBounds: Boolean = false,
) {
    val maxEdge = if (thumbnail) 512 else 1280
    val currentReader by rememberUpdatedState(readImage)
    val decoded by produceState(NoteBitmapState(), id, maxEdge) {
        val bitmap = try {
            withContext(Dispatchers.IO) {
                val bytes = currentReader(id) ?: return@withContext null
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
                    options.inSampleSize = 1
                    while (options.outWidth / options.inSampleSize > maxEdge ||
                        options.outHeight / options.inSampleSize > maxEdge) options.inSampleSize *= 2
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } finally { bytes.fill(0) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
        value = NoteBitmapState(bitmap, bitmap == null)
    }
    val bitmap = decoded.bitmap
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        if (bitmap != null) {
            val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            if (thumbnail) {
                Image(bitmap.asImageBitmap(), description,
                    if (cropThumbnailToBounds) Modifier.fillMaxSize() else
                        Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.75f, 1.8f)),
                    contentScale = ContentScale.Crop)
            } else BoxWithConstraints(Modifier.fillMaxWidth()) {
                val height = (maxWidth / ratio).coerceIn(96.dp, maximumHeight.coerceAtLeast(96.dp))
                Image(bitmap.asImageBitmap(), description,
                    Modifier.fillMaxWidth().height(height), contentScale = ContentScale.Fit)
            }
        } else if (thumbnail && cropThumbnailToBounds) {
            Box(Modifier.fillMaxSize().semantics {
                contentDescription = if (decoded.failed) "Imagen no disponible" else "Cargando imagen"
            }, contentAlignment = Alignment.Center) {
                MeasurementIcon(R.drawable.ic_observation_photo, 20, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (thumbnail) 80.dp else 112.dp)
                    .padding(BitavidaSpacing.Medium)
                    .semantics { contentDescription = if (decoded.failed) "Imagen no disponible" else "Cargando imagen" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                MeasurementIcon(R.drawable.ic_observation_photo, 20, MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (decoded.failed) "Imagen no disponible" else "Cargando imagen…",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
