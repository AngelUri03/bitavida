package com.bitavida.app.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.validation.limitTextCharacters
import com.bitavida.app.domain.validation.textCharacterCount
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.measurements.CaptureError
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.measurements.SecondaryText
import com.bitavida.app.ui.measurements.SectionTitle
import com.bitavida.app.ui.medical.CareSectionHeader
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.theme.BitavidaSpacing

/** Plan owns the destination; this route owns its single draft, media coordinator and modal. */
@Composable
fun NotesRoute(
    viewModel: NotesViewModel,
    patientName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val back = {
        if (!state.saving) {
            if (state.sheet != null) viewModel.onAction(NotesAction.DismissSheet) else onBack()
        }
    }
    BackHandler { back() }
    NotesPhotoCoordinator(viewModel)
    NotesScreen(state, viewModel::onAction, back, patientName, modifier, viewModel::imageBytes)
    NotesSheets(state, viewModel::onAction, viewModel::imageBytes)
}

@Composable
fun NotesScreen(
    state: NotesUiState,
    onAction: (NotesAction) -> Unit,
    onBack: () -> Unit,
    patientName: String,
    modifier: Modifier = Modifier,
    readImage: suspend (String) -> ByteArray? = { null },
) {
    val scroll = rememberLazyStaggeredGridState()
    val motion = rememberBitavidaMotionAllowed(null)
    val duration = if (motion) BitavidaFluidMotion.StateTransition else 0
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize()) {
        val singleColumn = maxWidth < 360.dp || fontScale >= 1.5f
        val minimumCardWidth = if (singleColumn) maxWidth else 150.dp * fontScale.coerceAtLeast(1f)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minimumCardWidth),
            state = scroll,
            modifier = Modifier.fillMaxSize().testTag("notes_grid"),
            contentPadding = PaddingValues(
                horizontal = BitavidaSpacing.ScreenHorizontal,
                vertical = BitavidaSpacing.Default,
            ),
            verticalItemSpacing = BitavidaSpacing.Medium,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                CareSectionHeader(
                    title = "Notas", person = patientName, iconRes = R.drawable.ic_v2_file,
                    subtitle = "Apuntes e información del cuidado",
                    motionAllowed = motion, backEnabled = !state.saving, onBack = onBack,
                )
            }
            if (state.loading) item(key = "loading", span = StaggeredGridItemSpan.FullLine) {
                SecondaryText("Cargando notas…")
            }
            state.error?.takeIf { state.sheet == null }?.let { error ->
                item(key = "error", span = StaggeredGridItemSpan.FullLine) {
                    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                        CaptureError(error)
                        if (state.loadFailed) CurrentDetailButton(
                            kind = DetailButtonKind.OUTLINED, strongOutline = true,
                            onClick = { onAction(NotesAction.Reload) },
                        ) { Text("Reintentar carga") }
                    }
                }
            }
            if (!state.loading && !state.loadFailed) {
                if (state.notes.isEmpty()) {
                    item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                        MeasurementPanel {
                            SectionTitle("Un espacio para tus apuntes", small = true)
                            SecondaryText("Guarda información útil del cuidado en notas de texto o con una imagen.")
                            NewNoteButton(!state.saving, { onAction(NotesAction.Create) }, "Crear primera nota")
                        }
                    }
                } else {
                    item(key = "new", span = StaggeredGridItemSpan.FullLine) {
                        NewNoteButton(!state.saving, { onAction(NotesAction.Create) })
                    }
                    items(state.notes, key = { it.id }, contentType = { "note" }) { note ->
                        NoteCard(
                            note, readImage,
                            onClick = { if (!state.saving) onAction(NotesAction.Open(note.id)) },
                            modifier = Modifier.animateContentSize(tween(duration)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewNoteButton(enabled: Boolean, onClick: () -> Unit, label: String = "Nueva nota") {
    CurrentDetailButton(
        kind = DetailButtonKind.FILLED, onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("notes_create"),
    ) {
        MeasurementIcon(R.drawable.ic_v2_plus, 18, androidx.compose.material3.LocalContentColor.current)
        Spacer(Modifier.width(BitavidaSpacing.Small))
        Text(label)
    }
}

@Composable
internal fun NoteCard(
    note: Note,
    readImage: suspend (String) -> ByteArray?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MeasurementPanel(
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget).testTag("note_" + note.id),
        onClick = onClick, contentPadding = BitavidaSpacing.Medium,
        contentSpacing = BitavidaSpacing.Small, borderAlpha = 0.8f,
    ) {
        note.imageId?.let {
            NotesImage(it, readImage, thumbnail = true, description = "Imagen de la nota")
        }
        Text(
            note.title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        note.content?.takeIf(String::isNotBlank)?.let { body ->
            val preview = if (textCharacterCount(body) > 280) limitTextCharacters(body, 280).trimEnd() + "…" else body
            Text(
                preview, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
