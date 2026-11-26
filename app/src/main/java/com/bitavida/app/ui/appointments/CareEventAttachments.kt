package com.bitavida.app.ui.appointments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bitavida.app.data.appointments.CareEventPhotoStore
import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.repository.CareEventActor
import com.bitavida.app.ui.measurements.MeasurementPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Images are read from private storage only when presented. */
@Composable
fun CareEventAttachments(actor: CareEventActor, event: CareEvent) {
    val context = LocalContext.current
    val store = remember(context) { CareEventPhotoStore(context.applicationContext) }
    var ids by remember(event.id, event.revision) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember(event.id, event.revision) { mutableStateOf<String?>(null) }
    var openId by remember(event.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(actor, event.id, event.revision) {
        try { ids = store.list(actor, event.id) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "No se pudieron consultar las fotografías." }
    }
    if (ids.isNotEmpty() || error != null) {
        MeasurementPanel {
            Text("Fotografías adjuntas", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ids.forEachIndexed { index, id ->
                    Surface(
                        onClick = { openId = id },
                        modifier = Modifier.size(88.dp).semantics(mergeDescendants = true) {
                            contentDescription = "Ver fotografía ${index + 1}"
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        CareEventPhotoImage(id, true, Modifier.fillMaxSize()) { store.read(actor, event.id, id) }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    openId?.let { id ->
        CareEventPhotoDialog(id, onDismiss = { openId = null }) { store.read(actor, event.id, id) }
    }
}

/** Thumbnails are decoded at bounded resolution; byte arrays are cleared immediately after use. */
@Composable
internal fun CareEventPhotoImage(key: String, thumbnail: Boolean, modifier: Modifier = Modifier,
    load: suspend () -> ByteArray?) {
    val bitmap by produceState<Bitmap?>(null, key, thumbnail) {
        try {
            val bytes = load()
            value = if (bytes == null) null else try {
                withContext(Dispatchers.Default) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    val target = if (thumbnail) 160 else 1600
                    while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            } finally { bytes.fill(0) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { value = null }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Fotografía adjunta", modifier = modifier) }
        ?: Box(modifier)
}

@Composable
internal fun CareEventPhotoDialog(key: String, onDismiss: () -> Unit, load: suspend () -> ByteArray?) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fotografía adjunta", style = MaterialTheme.typography.titleMedium)
                CareEventPhotoImage(key, false, Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp), load)
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Cerrar")
                }
            }
        }
    }
}
