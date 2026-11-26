package com.bitavida.app.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.bitavida.app.R
import com.bitavida.app.domain.model.WeightRevision
import com.bitavida.app.domain.model.WeightRevisionOperation
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.format.DateTimeFormatter

@Composable
internal fun WeightRecordManagementSheet(
    state: WeightUiState,
    motionAllowed: Boolean,
    onAction: (WeightAction) -> Unit,
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
            onAction(WeightAction.DismissRecord)
        },
    ) {
        val weight =
            record.value as MeasurementValue.Weight
        if (state.deleteConfirmationVisible) {
            MeasurementPanel {
                SectionTitle(
                    weight.kilograms.stripTrailingZeros()
                        .toPlainString() + " kg",
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
                    WeightAction.ConfirmDeleteSelectedRecord,
                )
            }
            MedicationLifecycleCancelAction(
                enabled = !state.saving,
                label = "Volver al detalle",
                onClick = {
                    onAction(
                        WeightAction.CancelDeleteSelectedRecord,
                    )
                },
            )
        } else {
            MeasurementPanel {
                SectionTitle(
                    weight.kilograms.stripTrailingZeros()
                        .toPlainString() + " kg",
                    small = false,
                )
                SecondaryText(
                    record.occurredAt.format(
                        DateTimeFormatter.ofPattern(
                            "d MMM yyyy · h:mm a",
                            measurementLocale,
                        ),
                    ),
                )
                record.weightMetadata?.note?.let {
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
                                WeightAction.CorrectSelectedRecord,
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
                                WeightAction.RequestDeleteSelectedRecord,
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
                        WeightRevision::revisionNumber,
                    ).forEachIndexed { index, revision ->
                        if (index > 0) ActionDivider()
                        WeightRevisionRow(revision)
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
private fun WeightRevisionRow(
    revision: WeightRevision,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(
            when (revision.operation) {
                WeightRevisionOperation.CREATED ->
                    "Registro original"
                WeightRevisionOperation.CORRECTED ->
                    "Corrección " +
                        (revision.revisionNumber - 1)
                WeightRevisionOperation.DELETED ->
                    "Eliminación lógica"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            revision.kilograms.stripTrailingZeros()
                .toPlainString() + " kg",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        SecondaryText(
            revision.actualAt.format(
                DateTimeFormatter.ofPattern(
                    "d MMM yyyy · h:mm a",
                    measurementLocale,
                ),
            ),
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
