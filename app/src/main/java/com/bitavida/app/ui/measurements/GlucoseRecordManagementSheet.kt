package com.bitavida.app.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseRevision
import com.bitavida.app.domain.model.GlucoseRevisionOperation
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.format.DateTimeFormatter

@Composable
internal fun GlucoseRecordManagementSheet(
    state: GlucoseUiState,
    motionAllowed: Boolean,
    onAction: (GlucoseAction) -> Unit,
) {
    val record = state.selectedRecord ?: return
    MedicationLifecycleSheetFrame(
        medication = null,
        title = if (state.deleteConfirmationVisible) {
            "Eliminar registro"
        } else {
            "Detalle de la medición"
        },
        subtitle = if (state.deleteConfirmationVisible) {
            "Esta acción no se puede deshacer desde la aplicación."
        } else {
            null
        },
        saving = state.saving,
        motionAllowed = motionAllowed,
        onDismiss = { onAction(GlucoseAction.DismissRecord) },
    ) {
        if (state.deleteConfirmationVisible) {
            DeleteGlucoseRecordConfirmation(
                record = record,
                saving = state.saving,
                motionAllowed = motionAllowed,
                error = state.saveError,
                onConfirm = {
                    onAction(GlucoseAction.ConfirmDeleteSelectedRecord)
                },
                onBack = {
                    onAction(GlucoseAction.CancelDeleteSelectedRecord)
                },
            )
        } else {
            GlucoseRecordDetails(
                record = record,
                revisions = state.revisions,
                loading = state.revisionsLoading,
                error = state.saveError,
                enabled = !state.saving,
                onCorrect = {
                    onAction(GlucoseAction.CorrectSelectedRecord)
                },
                onDelete = {
                    onAction(GlucoseAction.RequestDeleteSelectedRecord)
                },
            )
        }
    }
}

@Composable
private fun GlucoseRecordDetails(
    record: MeasurementRecord,
    revisions: List<GlucoseRevision>,
    loading: Boolean,
    error: String?,
    enabled: Boolean,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
) {
    val glucose = record.value as MeasurementValue.Glucose
    MeasurementPanel {
        SectionTitle(glucose.readingLabel(), small = false)
        glucose.context?.let { SecondaryText(it.captureLabel()) }
        SecondaryText(
            record.occurredAt.format(
                DateTimeFormatter.ofPattern(
                    "d MMM yyyy · h:mm a",
                    measurementLocale,
                ),
            ),
        )
        record.glucoseMetadata?.note?.let { SecondaryText(it) }
    }
    MeasurementPanel {
        MeasurementAction(
            icon = R.drawable.ic_v2_edit,
            title = "Corregir registro",
            description = "Conserva la versión anterior y registra la corrección.",
            onClick = { if (enabled) onCorrect() },
        )
        ActionDivider()
        MeasurementAction(
            icon = R.drawable.ic_v2_close,
            title = "Eliminar registro",
            description = "Lo retira de cálculos y gráficas, conservando la trazabilidad.",
            onClick = { if (enabled) onDelete() },
        )
    }
    SectionTitle("Historial de modificaciones", small = true)
    when {
        loading -> CircularProgressIndicator()
        revisions.isEmpty() -> SecondaryText("Aún no hay revisiones disponibles.")
        else -> MeasurementPanel {
            revisions.sortedByDescending(GlucoseRevision::revisionNumber)
                .forEachIndexed { index, revision ->
                    if (index > 0) ActionDivider()
                    GlucoseRevisionRow(revision)
                }
        }
    }
    error?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun GlucoseRevisionRow(revision: GlucoseRevision) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
        Text(
            when (revision.operation) {
                GlucoseRevisionOperation.CREATED -> "Registro original"
                GlucoseRevisionOperation.CORRECTED -> "Corrección ${revision.revisionNumber - 1}"
                GlucoseRevisionOperation.DELETED -> "Eliminación lógica"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            revision.readingLabel(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        SecondaryText(
            revision.actualAt.format(
                DateTimeFormatter.ofPattern(
                    "d MMM yyyy · h:mm a",
                    measurementLocale,
                ),
            ) + " · " + revision.context.captureLabel(),
        )
        revision.note?.let { SecondaryText(it) }
        SecondaryText(
            "Registrado el " + revision.capturedAt.format(
                DateTimeFormatter.ofPattern(
                    "d MMM yyyy · h:mm a",
                    measurementLocale,
                ),
            ),
        )
    }
}

@Composable
private fun DeleteGlucoseRecordConfirmation(
    record: MeasurementRecord,
    saving: Boolean,
    motionAllowed: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val glucose = record.value as MeasurementValue.Glucose
    MeasurementPanel {
        SectionTitle(glucose.readingLabel(), small = true)
        SecondaryText(
            record.occurredAt.format(
                DateTimeFormatter.ofPattern(
                    "d MMM yyyy · h:mm a",
                    measurementLocale,
                ),
            ),
        )
        SecondaryText(
            "El registro dejará de aparecer en estadísticas, gráfica e historial activo. " +
                "La versión eliminada permanecerá en la auditoría cifrada.",
        )
    }
    error?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    MedicationLifecyclePrimaryAction(
        label = "Eliminar registro",
        icon = R.drawable.ic_v2_close,
        enabled = !saving,
        saving = saving,
        destructive = true,
        motionAllowed = motionAllowed,
        onClick = onConfirm,
    )
    MedicationLifecycleCancelAction(
        enabled = !saving,
        label = "Volver al detalle",
        onClick = onBack,
    )
}

private fun MeasurementValue.Glucose.readingLabel(): String =
    reading + if (reading.uppercase() in setOf("HI", "LO")) "" else " mg/dL"

private fun GlucoseRevision.readingLabel(): String =
    reading + if (reading.uppercase() in setOf("HI", "LO")) "" else " mg/dL"
