package com.bitavida.app.ui.observations

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.bitavida.app.R
import com.bitavida.app.data.observations.ObservationPhotoStore
import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.platform.observations.ObservationCameraFiles
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.measurements.CaptureError
import com.bitavida.app.ui.measurements.MeasurementButton
import com.bitavida.app.ui.measurements.MeasurementButtonStyle
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.measurements.SecondaryText
import com.bitavida.app.ui.measurements.SectionTitle
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ObservationPhotoCoordinator(viewModel: ObservationsViewModel) {
    val context = LocalContext.current
    val store = remember(context) { ObservationPhotoStore(context.applicationContext) }
    var pendingValues by rememberSaveable { mutableStateOf<List<String>?>(null) }

    fun pending(): PendingObservationPhoto? =
        pendingValues?.let { PendingObservationPhoto.restore(it) }

    fun finish(request: PendingObservationPhoto, uri: Uri) {
        pendingValues = null
        viewModel.launchPhotoWork {
            viewModel.onAction(ObservationsAction.PhotoPreparing(true))
            try {
                val importedId = store.importPhoto(viewModel.actor, uri)
                val current = viewModel.state.value.draft
                val updated = observationWithImportedPhoto(current, request.request, importedId)
                if (updated == null) {
                    store.discard(viewModel.actor, importedId)
                } else {
                    viewModel.onAction(ObservationsAction.ChangeDraft(updated))
                    request.request.replacingPhotoId?.let { store.discard(viewModel.actor, it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                viewModel.onAction(ObservationsAction.PhotoFailed(
                    "No se pudo agregar la fotografía. Puedes reintentar o guardar sin ella.",
                ))
            } finally {
                if (request.request.source == ObservationPhotoSource.CAMERA) {
                    runCatching {
                        context.revokeUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    ObservationCameraFiles.delete(context, request.cameraToken)
                }
                viewModel.onAction(ObservationsAction.PhotoPreparing(false))
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val request = pending()
        pendingValues = null
        if (request?.request?.source == ObservationPhotoSource.GALLERY && uri != null) {
            finish(request, uri)
        } else {
            viewModel.onAction(ObservationsAction.PhotoPreparing(false))
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val request = pending()
        val uri = request?.cameraToken?.let { ObservationCameraFiles.uri(context, it) }
        pendingValues = null
        if (captured && request?.request?.source == ObservationPhotoSource.CAMERA && uri != null) {
            finish(request, uri)
        } else {
            ObservationCameraFiles.delete(context, request?.cameraToken)
            viewModel.onAction(ObservationsAction.PhotoPreparing(false))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.photoRequests.collect { request ->
            if (pendingValues != null || viewModel.state.value.draft?.id != request.draftId) return@collect
            try {
                viewModel.onAction(ObservationsAction.PhotoPreparing(true))
                when (request.source) {
                    ObservationPhotoSource.GALLERY -> {
                        pendingValues = PendingObservationPhoto(request).savedValues()
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    ObservationPhotoSource.CAMERA -> {
                        val capture = ObservationCameraFiles.create(context)
                        pendingValues = PendingObservationPhoto(request, capture.token).savedValues()
                        cameraLauncher.launch(capture.uri)
                    }
                }
            } catch (_: ActivityNotFoundException) {
                val pending = pending()
                pendingValues = null
                ObservationCameraFiles.delete(context, pending?.cameraToken)
                viewModel.onAction(ObservationsAction.PhotoFailed(
                    "No hay una aplicación disponible para obtener la fotografía.",
                ))
            } catch (_: SecurityException) {
                val pending = pending()
                pendingValues = null
                ObservationCameraFiles.delete(context, pending?.cameraToken)
                viewModel.onAction(ObservationsAction.PhotoFailed(
                    "Android no permitió abrir la cámara o el selector de imágenes.",
                ))
            } catch (_: Exception) {
                val pending = pending()
                pendingValues = null
                ObservationCameraFiles.delete(context, pending?.cameraToken)
                viewModel.onAction(ObservationsAction.PhotoFailed(
                    "No se pudo iniciar la selección de fotografía.",
                ))
            }
        }
    }
}

@Composable
internal fun ObservationPhotoSourceActions(
    enabled: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
        val actions: @Composable (Modifier) -> Unit = { modifier ->
            MeasurementButton(
                "Tomar foto",
                onCamera,
                modifier,
                style = MeasurementButtonStyle.OUTLINED,
                enabled = enabled,
                icon = R.drawable.ic_observation_photo,
            )
            MeasurementButton(
                "Elegir de galería",
                onGallery,
                modifier,
                style = MeasurementButtonStyle.OUTLINED,
                enabled = enabled,
                icon = R.drawable.ic_v2_file,
            )
        }
        if (stack) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                actions(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                actions(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
internal fun ObservationPhotos(
    viewModel: ObservationsViewModel,
    draft: ObservationRecord,
    motionAllowed: Boolean = true,
) {
    if (!ObservationCatalog.get(draft.observationId).photoAllowed ||
        draft.result == ObservationResult.NOT_PERFORMED
    ) return
    val state by viewModel.state.collectAsState()
    val duration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    var viewedPhoto by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }

    MeasurementPanel {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.bitavida.app.ui.measurements.MeasurementIcon(R.drawable.ic_observation_photo, 20)
            Column(Modifier.weight(1f)) {
                SectionTitle("Fotografía opcional", small = true)
                SecondaryText("Solo se abre cuando tú lo solicitas; no se analiza ni se envía.")
            }
        }
        AnimatedVisibility(
            visible = draft.localPhotoIds.isEmpty(),
            enter = fadeIn(tween(duration)),
            exit = fadeOut(tween(duration)),
        ) {
            ObservationPhotoSourceActions(
                enabled = !state.saving && !state.photoPreparing,
                onCamera = { viewModel.requestPhoto(ObservationPhotoSource.CAMERA) },
                onGallery = { viewModel.requestPhoto(ObservationPhotoSource.GALLERY) },
            )
        }
        if (state.photoPreparing) SecondaryText("Preparando fotografía…")
        AnimatedVisibility(
            visible = draft.localPhotoIds.isNotEmpty(),
            enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
            exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                draft.localPhotoIds.forEach { id ->
                    ObservationPhotoEditor(
                        viewModel = viewModel,
                        draft = draft,
                        id = id,
                        enabled = !state.saving && !state.photoPreparing,
                        motionAllowed = motionAllowed,
                        onView = { viewedPhoto = id },
                    )
                }
            }
        }
    }
    viewedPhoto?.let { id ->
        ObservationPhotoViewer(viewModel, id, onDismiss = { viewedPhoto = null })
    }
}

@Composable
private fun ObservationPhotoEditor(
    viewModel: ObservationsViewModel,
    draft: ObservationRecord,
    id: String,
    enabled: Boolean,
    motionAllowed: Boolean,
    onView: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { ObservationPhotoStore(context.applicationContext) }
    var failed by remember(id) { mutableStateOf(false) }
    var actionsVisible by rememberSaveable(id) { mutableStateOf(false) }
    val bytes by produceState<ByteArray?>(null, id) {
        try {
            value = store.read(viewModel.actor, id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Surface(
                onClick = onView,
                enabled = enabled,
                modifier = Modifier.size(width = 112.dp, height = 88.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Image(
                    bitmap.asImageBitmap(),
                    "Fotografía seleccionada; pulsa para verla",
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Surface(
                modifier = Modifier.size(width = 112.dp, height = 88.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SecondaryText(if (failed) "No disponible" else "Cargando…")
                }
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                "Fotografía adjunta",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MeasurementButton(
                "Ver fotografía",
                onView,
                Modifier.fillMaxWidth(),
                style = MeasurementButtonStyle.TEXT,
                enabled = enabled && bitmap != null,
            )
        }
        Surface(
            onClick = { actionsVisible = true },
            enabled = enabled,
            modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget)
                .semantics { contentDescription = "Editar fotografía adjunta" },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                com.bitavida.app.ui.measurements.MeasurementIcon(R.drawable.ic_v2_edit, 18)
            }
        }
    }
    if (actionsVisible) {
        ObservationPhotoActionsSheet(
            enabled = enabled,
            motionAllowed = motionAllowed,
            onCamera = {
                actionsVisible = false
                viewModel.requestPhoto(ObservationPhotoSource.CAMERA, id)
            },
            onGallery = {
                actionsVisible = false
                viewModel.requestPhoto(ObservationPhotoSource.GALLERY, id)
            },
            onRemove = {
                actionsVisible = false
                val current = viewModel.state.value.draft
                if (current?.id == draft.id && id in current.localPhotoIds) {
                    viewModel.onAction(
                        ObservationsAction.ChangeDraft(
                            current.copy(localPhotoIds = current.localPhotoIds - id),
                        ),
                    )
                    viewModel.launchPhotoWork {
                        try {
                            store.discard(viewModel.actor, id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            viewModel.onAction(
                                ObservationsAction.PhotoFailed(
                                    "La fotografía se quitó del borrador; no se modificó ningún registro guardado.",
                                ),
                            )
                        }
                    }
                }
            },
            onDismiss = { actionsVisible = false },
        )
    }
    if (failed) CaptureError("No se pudo abrir la fotografía. Puedes reemplazarla o quitarla.")
}

@Composable
private fun ObservationPhotoActionsSheet(
    enabled: Boolean,
    motionAllowed: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    MedicationLifecycleSheetFrame(
        medication = null,
        title = "Editar fotografía",
        saving = false,
        motionAllowed = motionAllowed,
        onDismiss = onDismiss,
    ) {
        MeasurementButton(
            "Tomar otra fotografía",
            onCamera,
            Modifier.fillMaxWidth(),
            style = MeasurementButtonStyle.OUTLINED,
            icon = R.drawable.ic_observation_photo,
            enabled = enabled,
        )
        MeasurementButton(
            "Elegir otra de galería",
            onGallery,
            Modifier.fillMaxWidth(),
            style = MeasurementButtonStyle.OUTLINED,
            icon = R.drawable.ic_observation_photo,
            enabled = enabled,
        )
        MedicationLifecyclePrimaryAction(
            label = "Quitar fotografía",
            icon = R.drawable.ic_medication_delete,
            enabled = enabled,
            saving = false,
            destructive = true,
            motionAllowed = motionAllowed,
            onClick = onRemove,
        )
        MedicationLifecycleCancelAction(enabled = enabled, onClick = onDismiss)
    }
}

@Composable
internal fun ObservationStoredPhoto(
    viewModel: ObservationsViewModel,
    id: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    MeasurementButton(
        "Abrir fotografía adjunta",
        { expanded = true },
        modifier.fillMaxWidth(),
        style = MeasurementButtonStyle.OUTLINED,
        icon = R.drawable.ic_observation_photo,
        accessibleLabel = "Abrir fotografía sensible adjunta a la observación",
    )
    if (expanded) ObservationPhotoViewer(viewModel, id) { expanded = false }
}

@Composable
private fun ObservationPhotoViewer(
    viewModel: ObservationsViewModel,
    id: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { ObservationPhotoStore(context.applicationContext) }
    val bytes by produceState<ByteArray?>(null, id) {
        try {
            value = store.read(viewModel.actor, id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = null
        }
    }
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    var scale by remember(id) { mutableFloatStateOf(1f) }
    var offset by remember(id) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale == 1f) Offset.Zero else offset + panChange
    }
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    var visible by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { visible = true }
    val requestClose: () -> Unit = {
        if (visible) {
            visible = false
            scope.launch {
                if (duration > 0) delay(duration.milliseconds)
                onDismiss()
            }
        }
    }
    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
            val previousStatus = controller?.isAppearanceLightStatusBars
            val previousNavigation = controller?.isAppearanceLightNavigationBars
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
                if (previousNavigation != null) {
                    controller.isAppearanceLightNavigationBars = previousNavigation
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .98f),
                exit = fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = .98f),
            ) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    if (bitmap != null) {
                        Image(
                            bitmap.asImageBitmap(),
                            "Fotografía de la observación. Usa dos dedos para ampliar o desplazar.",
                            Modifier.fillMaxSize()
                                .padding(BitavidaSpacing.Default)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                                .transformable(transform),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            "No se pudo abrir la fotografía.",
                            Modifier.align(Alignment.Center),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Surface(
                        onClick = requestClose,
                        modifier = Modifier.align(Alignment.TopEnd)
                            .padding(BitavidaSpacing.Default)
                            .size(48.dp)
                            .semantics { contentDescription = "Cerrar fotografía" },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.Black.copy(alpha = .72f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = .72f),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            com.bitavida.app.ui.measurements.MeasurementIcon(
                                R.drawable.ic_v2_close,
                                22,
                                Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
