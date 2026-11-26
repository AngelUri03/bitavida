package com.bitavida.app.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.bitavida.app.R
import com.bitavida.app.domain.model.BloodPressureRevision
import com.bitavida.app.domain.model.BloodPressureRevisionOperation
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.format.DateTimeFormatter

@Composable
internal fun BloodPressureRecordManagementSheet(
    state: BloodPressureUiState,
    motionAllowed: Boolean,
    onAction: (BloodPressureAction) -> Unit,
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
            "Esta acción conserva la trazabilidad cifrada."
        } else {
            null
        },
        saving = state.saving,
        motionAllowed = motionAllowed,
        onDismiss = {
            onAction(BloodPressureAction.DismissRecord)
        },
    ) {
        val pressure =
            record.value as MeasurementValue.BloodPressure
        if (state.deleteConfirmationVisible) {
            MeasurementPanel {
                SectionTitle(
                    pressure.systolic + " / " +
                        pressure.diastolic + " mmHg",
                    small = true,
                )
                SecondaryText(
                    record.occurredAt.format(
                        DateTimeFormatter.ofPattern(
                            "d MMM yyyy · h:mm a",
                            measurementLocale,
                        ),
                    ),
                )
                SecondaryText(
                    "Dejará de aparecer en estadísticas y gráficas, " +
                        "pero su versión permanecerá en auditoría.",
                )
            }
            state.saveError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            MedicationLifecyclePrimaryAction(
                label = "Eliminar registro",
                icon = R.drawable.ic_v2_close,
                enabled = !state.saving,
                saving = state.saving,
                destructive = true,
                motionAllowed = motionAllowed,
            ) {
                onAction(
                    BloodPressureAction.ConfirmDeleteSelectedRecord,
                )
            }
            MedicationLifecycleCancelAction(
                enabled = !state.saving,
                label = "Volver al detalle",
                onClick = {
                    onAction(
                        BloodPressureAction.CancelDeleteSelectedRecord,
                    )
                },
            )
        } else {
            MeasurementPanel {
                SectionTitle(
                    pressure.systolic + " / " +
                        pressure.diastolic + " mmHg",
                    small = false,
                )
                record.bloodPressureMetadata?.actualContext?.let {
                    SecondaryText(it.captureLabel())
                }
                SecondaryText(
                    record.occurredAt.format(
                        DateTimeFormatter.ofPattern(
                            "d MMM yyyy · h:mm a",
                            measurementLocale,
                        ),
                    ),
                )
                record.bloodPressureMetadata?.note?.let {
                    SecondaryText(it)
                }
            }
            MeasurementPanel {
                MeasurementAction(
                    icon = R.drawable.ic_v2_edit,
                    title = "Corregir registro",
                    description =
                        "Conserva la versión anterior y registra la corrección.",
                    onClick = {
                        if (!state.saving) {
                            onAction(
                                BloodPressureAction.CorrectSelectedRecord,
                            )
                        }
                    },
                )
                ActionDivider()
                MeasurementAction(
                    icon = R.drawable.ic_v2_close,
                    title = "Eliminar registro",
                    description =
                        "Lo retira del historial activo sin perder auditoría.",
                    onClick = {
                        if (!state.saving) {
                            onAction(
                                BloodPressureAction.RequestDeleteSelectedRecord,
                            )
                        }
                    },
                )
            }
            SectionTitle(
                "Historial de modificaciones",
                small = true,
            )
            when {
                state.revisionsLoading ->
                    CircularProgressIndicator()
                state.revisions.isEmpty() ->
                    SecondaryText(
                        "Aún no hay revisiones disponibles.",
                    )
                else -> MeasurementPanel {
                    state.revisions.sortedByDescending(
                        BloodPressureRevision::revisionNumber,
                    ).forEachIndexed { index, revision ->
                        if (index > 0) ActionDivider()
                        BloodPressureRevisionRow(revision)
                    }
                }
            }
            state.saveError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BloodPressureRevisionRow(
    revision: BloodPressureRevision,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(
            when (revision.operation) {
                BloodPressureRevisionOperation.CREATED ->
                    "Registro original"
                BloodPressureRevisionOperation.CORRECTED ->
                    "Corrección " +
                        (revision.revisionNumber - 1)
                BloodPressureRevisionOperation.DELETED ->
                    "Eliminación lógica"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            revision.systolic + " / " +
                revision.diastolic + " mmHg",
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
