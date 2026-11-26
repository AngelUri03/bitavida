package com.bitavida.app.ui.appointments

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bitavida.app.data.appointments.CareEventPhotoStore
import com.bitavida.app.platform.observations.ObservationCameraFiles
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame

/** Draft additions and removals are committed with the event; existing encrypted images are never erased. */
@Composable
internal fun CareEventDraftPhotos(state: CareEventsState, onAction: (CareEventsAction) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { CareEventPhotoStore(context.applicationContext) }
    val motion = rememberBitavidaMotionAllowed(null)
    var selectedId by remember { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var replacingId by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraToken by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraReplacement by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingReplacement by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val visibleIds = visibleCareEventPhotoIds(state.existingPhotoIds, state.removedPhotoIds, state.pendingPhotos)
    suspend fun photoBytes(id: String): ByteArray? {
        state.pendingPhotos.find { it.id == id }?.let { return it.jpeg.copyOf() }
        val actor = state.photoActor ?: return null
        val eventId = state.editing?.id ?: return null
        return store.read(actor, eventId, id)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val owner = pendingOwner
        val replacement = pendingReplacement
        pendingOwner = null
        pendingReplacement = null
        if (uri != null && owner != null && owner == state.draftId) {
            onAction(CareEventsAction.AddDraftPhoto(uri, replacingId = replacement))
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val token = cameraToken
        val owner = cameraOwner
        val replacement = cameraReplacement
        cameraToken = null
        cameraOwner = null
        cameraReplacement = null
        val uri = token?.let { ObservationCameraFiles.uri(context, it) }
        if (captured && uri != null && owner != null && owner == state.draftId) {
            onAction(CareEventsAction.AddDraftPhoto(uri, token, replacement))
        } else ObservationCameraFiles.delete(context, token)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fotografías adjuntas · ${visibleIds.size} de 5", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        if (!state.photoListReady || state.photoPreparing) LinearProgressIndicator(Modifier.fillMaxWidth())
        visibleIds.forEachIndexed { index, id ->
            OutlinedButton(onClick = { selectedId = id }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                enabled = !state.saving && !state.photoPreparing) {
                CareEventPhotoImage(id, true, Modifier.size(56.dp)) { photoBytes(id) }
                Spacer(Modifier.width(12.dp))
                Text("Fotografía ${index + 1}", modifier = Modifier.weight(1f))
            }
        }
        OutlinedButton(onClick = { replacingId = null; showSource = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = state.photoListReady && !state.saving && !state.photoPreparing && visibleIds.size < 5) {
            Text("Añadir fotografía")
        }
        if (visibleIds.size >= 5) Text(
            if (visibleIds.size > 5) "Conservas fotografías anteriores. Retira las necesarias para añadir otra."
            else "Alcanzaste el máximo de cinco fotografías.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    selectedId?.let { id ->
        MedicationLifecycleSheetFrame(medication = null, title = "Fotografía adjunta", saving = state.saving,
            motionAllowed = motion, onDismiss = { selectedId = null }, skipPartiallyExpanded = false) {
            CareEventPhotoImage(id, true, Modifier.fillMaxWidth().height(120.dp)) { photoBytes(id) }
            OutlinedButton(onClick = { selectedId = null; previewId = id },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver fotografía") }
            OutlinedButton(onClick = { selectedId = null; replacingId = id; showSource = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = visibleIds.size <= 5 && !state.saving && !state.photoPreparing) { Text("Reemplazar fotografía") }
            OutlinedButton(onClick = { selectedId = null; onAction(CareEventsAction.RemoveDraftPhoto(id)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !state.saving && !state.photoPreparing) { Text("Quitar fotografía") }
        }
    }
    if (showSource) {
        MedicationLifecycleSheetFrame(medication = null, title = "Añadir fotografía", saving = state.saving,
            motionAllowed = motion, onDismiss = { showSource = false }, skipPartiallyExpanded = false) {
            OutlinedButton(onClick = {
                showSource = false
                pendingOwner = state.draftId
                pendingReplacement = replacingId
                picker.launch("image/*")
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Elegir de galería") }
            OutlinedButton(onClick = {
                showSource = false
                try {
                    cameraError = null
                    val capture = ObservationCameraFiles.create(context)
                    cameraToken = capture.token
                    cameraOwner = state.draftId
                    cameraReplacement = replacingId
                    camera.launch(capture.uri)
                } catch (_: Exception) {
                    ObservationCameraFiles.delete(context, cameraToken)
                    cameraToken = null
                    cameraOwner = null
                    cameraReplacement = null
                    cameraError = "No se pudo abrir la cámara."
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tomar fotografía") }
        }
    }
    previewId?.let { id ->
        CareEventPhotoDialog(id, onDismiss = { previewId = null }) { photoBytes(id) }
    }
}
