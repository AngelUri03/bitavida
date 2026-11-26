package com.bitavida.app.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.measurements.CaptureError
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing

@Composable
internal fun NotesSheets(
    state: NotesUiState,
    onAction: (NotesAction) -> Unit,
    readImage: suspend (String) -> ByteArray?,
) {
    val sheet = state.sheet ?: return
    val motion = rememberBitavidaMotionAllowed(null)
    val title = when (sheet) {
        NotesSheet.FORM -> if (state.form?.editingRevision != null) "Editar nota" else "Nueva nota"
        NotesSheet.DETAIL -> state.selected?.title ?: "Nota"
        NotesSheet.DELETE -> "¿Eliminar nota?"
    }
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = scheme.copy(outlineVariant = scheme.outline, surfaceTint = scheme.primary)) {
        MedicationLifecycleSheetFrame(
            medication = null, title = title, saving = state.saving, motionAllowed = motion,
            onDismiss = { onAction(NotesAction.DismissSheet) },
            contentHorizontalPadding = BitavidaSpacing.Large,
        ) {
            AnimatedContent(
                targetState = sheet,
                transitionSpec = {
                    if (motion) fadeIn(tween(BitavidaFluidMotion.Selection)) togetherWith
                        fadeOut(tween(BitavidaFluidMotion.Selection / 2))
                    else EnterTransition.None togetherWith ExitTransition.None
                },
                label = "notes_sheet",
            ) { displayed ->
                val dispatch: (NotesAction) -> Unit = { if (displayed == state.sheet) onAction(it) }
                when (displayed) {
                    NotesSheet.FORM -> state.form?.let { NoteFormContent(it, state, dispatch, readImage, motion) }
                    NotesSheet.DETAIL -> NoteDetailContent(state, dispatch, readImage, motion)
                    NotesSheet.DELETE -> Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                        state.selected?.let {
                            Text(it.title, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("Dejará de aparecer en tus notas.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.error?.let { CaptureError(it) }
                        MedicationLifecyclePrimaryAction(
                            "Eliminar nota", R.drawable.ic_v2_close, !state.saving, state.saving, true, motion,
                        ) { dispatch(NotesAction.ConfirmDelete) }
                        CurrentDetailButton(
                            kind = DetailButtonKind.OUTLINED, strongOutline = true,
                            modifier = Modifier.fillMaxWidth(), enabled = !state.saving,
                            onClick = { dispatch(NotesAction.CancelDelete) }, motionAllowedOverride = motion,
                        ) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoteFormContent(
    form: NoteForm,
    state: NotesUiState,
    onAction: (NotesAction) -> Unit,
    readImage: suspend (String) -> ByteArray?,
    motion: Boolean,
) {
    val enabled = !state.saving && !state.imagePreparing
    Column(
        Modifier.fillMaxWidth().testTag("note_form"),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
    ) {
        BitavidaLimitedTextField(
            value = form.title, onValueChange = { if (!state.saving) onAction(NotesAction.ChangeTitle(it)) },
            limit = CaptureLimit.NOTE_TITLE,
            label = { Text("Título") }, isError = state.titleError, maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("note_title"),
        )
        BitavidaLimitedTextField(
            value = form.content, onValueChange = { if (!state.saving) onAction(NotesAction.ChangeContent(it)) },
            limit = CaptureLimit.NOTE_CONTENT, label = { Text("Contenido (opcional)") },
            minLines = 3, maxLines = 10, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("note_content"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            Text("Imagen (opcional)", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            form.imageId?.let { id ->
                NotesImage(id, readImage, description = "Vista previa de la imagen seleccionada", maximumHeight = 160.dp)
            }
            val camera: @Composable (Modifier) -> Unit = { modifier ->
                NoteSecondaryButton("Tomar foto", R.drawable.ic_observation_photo, enabled, modifier, motion) {
                    onAction(NotesAction.RequestImage(NotesPhotoSource.CAMERA))
                }
            }
            val gallery: @Composable (Modifier) -> Unit = { modifier ->
                NoteSecondaryButton(if (form.imageId == null) "Elegir imagen" else "Cambiar imagen",
                    R.drawable.ic_v2_file, enabled, modifier, motion) {
                    onAction(NotesAction.RequestImage(NotesPhotoSource.GALLERY))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f) {
                    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                        camera(Modifier.fillMaxWidth())
                        gallery(Modifier.fillMaxWidth())
                    }
                } else Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    camera(Modifier.weight(1f).fillMaxHeight())
                    gallery(Modifier.weight(1f).fillMaxHeight())
                }
            }
            if (form.imageId != null) {
                NoteSecondaryButton("Quitar imagen", R.drawable.ic_v2_close, enabled,
                    Modifier.fillMaxWidth(), motion) { onAction(NotesAction.RemoveImage) }
            }
            if (state.imagePreparing) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    Text("Preparando imagen…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.error?.let { CaptureError(it) }
        MedicationLifecyclePrimaryAction(
            "Guardar nota", R.drawable.ic_v2_file, enabled, state.saving, false, motion,
        ) { onAction(NotesAction.Save) }
    }
}

@Composable
internal fun NoteDetailContent(
    state: NotesUiState,
    onAction: (NotesAction) -> Unit,
    readImage: suspend (String) -> ByteArray?,
    motion: Boolean,
) {
    val note = state.selected ?: return
    Column(
        Modifier.fillMaxWidth().testTag("note_detail"),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        note.legacyIndicationId?.let {
            Text("Nota conservada · Indicación médica transcrita",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        note.imageId?.let { NotesImage(it, readImage) }
        note.content?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        state.error?.let { CaptureError(it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        CurrentDetailButton(
            kind = DetailButtonKind.FILLED, onClick = { onAction(NotesAction.Edit) },
            enabled = !state.saving, modifier = Modifier.fillMaxWidth(), motionAllowedOverride = motion,
        ) {
            MeasurementIcon(R.drawable.ic_v2_edit, 18, LocalContentColor.current)
            Spacer(Modifier.width(BitavidaSpacing.Small))
            Text("Editar")
        }
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED, strongOutline = true, destructive = true,
            onClick = { onAction(NotesAction.RequestDelete) }, enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(), motionAllowedOverride = motion,
        ) {
            MeasurementIcon(R.drawable.ic_v2_close, 18, LocalContentColor.current)
            Spacer(Modifier.width(BitavidaSpacing.Small))
            Text("Eliminar nota")
        }
    }
}

@Composable
private fun NoteSecondaryButton(
    label: String,
    icon: Int,
    enabled: Boolean,
    modifier: Modifier,
    motion: Boolean,
    onClick: () -> Unit,
) {
    CurrentDetailButton(
        kind = DetailButtonKind.OUTLINED, strongOutline = true, onClick = onClick, enabled = enabled,
        modifier = modifier, motionAllowedOverride = motion,
        contentPadding = PaddingValues(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.Small),
    ) {
        MeasurementIcon(icon, 18, LocalContentColor.current)
        Spacer(Modifier.width(BitavidaSpacing.Small))
        Text(label)
    }
}
