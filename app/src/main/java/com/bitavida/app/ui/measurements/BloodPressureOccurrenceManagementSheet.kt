package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaLimitedTextField

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevisionOperation
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.format.DateTimeFormatter

@Composable
internal fun BloodPressureOccurrenceManagementSheet(
    state: BloodPressureUiState,
    motionAllowed: Boolean,
    onAction: (BloodPressureAction) -> Unit,
    startNotPerformed: Boolean = false,
) {
    val occurrence = state.selectedOccurrence ?: return
    val decision = state.selectedOccurrenceDecision
    var reason by remember(occurrence.id, decision?.capturedAt) {
        mutableStateOf(decision?.reason.orEmpty())
    }
    var confirming by remember(occurrence.id, startNotPerformed) {
        mutableStateOf(startNotPerformed)
    }
    var correctingReason by remember(occurrence.id) {
        mutableStateOf(false)
    }
    val duration = if (motionAllowed) {
        BitavidaFluidMotion.StateTransition
    } else {
        0
    }
    MedicationLifecycleSheetFrame(
        medication = null,
        title = if (decision == null && startNotPerformed) {
            "No se realizó"
        } else if (decision == null) {
            "Medición sin confirmar"
        } else {
            "Medición no realizada"
        },
        saving = state.occurrenceDecisionSaving,
        motionAllowed = motionAllowed,
        onDismiss = {
            onAction(BloodPressureAction.DismissOccurrence)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .animateContentSize(tween(duration)),
            verticalArrangement =
                Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            MeasurementPanel {
                SectionTitle(
                    occurrence.scheduledAt.format(
                        DateTimeFormatter.ofPattern(
                            "h:mm a", measurementLocale,
                        ),
                    ),
                    small = false,
                )
                SecondaryText(
                    occurrence.suggestedContext.captureLabel(),
                )
                SecondaryText(
                    occurrence.scheduledAt.format(
                        DateTimeFormatter.ofPattern(
                            "d MMM yyyy", measurementLocale,
                        ),
                    ),
                )
                SecondaryText(
                    if (decision == null && startNotPerformed) {
                        "Registra lo que ocurrió con esta medición programada."
                    } else if (decision == null) {
                        "La hora ya pasó y todavía no se confirmó qué ocurrió."
                    } else {
                        "Declaración registrada por la persona cuidadora."
                    },
                )
            }
            when {
                decision == null && !confirming -> {
                    MedicationLifecyclePrimaryAction(
                        label = "Registrar medición",
                        icon = R.drawable.ic_v2_plus,
                        enabled = !state.occurrenceDecisionSaving,
                        saving = false,
                        destructive = false,
                        motionAllowed = motionAllowed,
                    ) {
                        onAction(
                            BloodPressureAction.RegisterSelectedOccurrence,
                        )
                    }
                    OutlinedButton(
                        onClick = { confirming = true },
                        enabled = !state.occurrenceDecisionSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = BitavidaSpacing.MinimumTouchTarget,
                        ),
                    ) {
                        Text("No se realizó")
                    }
                }
                decision != null && !correctingReason -> {
                    MedicationLifecyclePrimaryAction(
                        label = "Corregir a realizada",
                        icon = R.drawable.ic_v2_plus,
                        enabled = !state.occurrenceDecisionSaving,
                        saving = false,
                        destructive = false,
                        motionAllowed = motionAllowed,
                    ) {
                        onAction(
                            BloodPressureAction.RegisterSelectedOccurrence,
                        )
                    }
                    OutlinedButton(
                        onClick = { correctingReason = true },
                        enabled = !state.occurrenceDecisionSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(
                            min = BitavidaSpacing.MinimumTouchTarget,
                        ),
                    ) {
                        Text("Corregir motivo")
                    }
                }
                else -> {
                    BitavidaLimitedTextField(
                        value = reason,
                limit = CaptureLimit.CHANGE_REASON,
                        onValueChange = {
                            reason = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Motivo opcional") },
                        minLines = 2,
                        maxLines = 4,
                        enabled = !state.occurrenceDecisionSaving,
                    )
                    MedicationLifecyclePrimaryAction(
                        label = if (decision == null) {
                            "Confirmar que no se realizó"
                        } else {
                            "Guardar corrección"
                        },
                        icon = if (decision == null) {
                            R.drawable.ic_v2_close
                        } else {
                            R.drawable.ic_v2_edit
                        },
                        enabled =
                            !state.occurrenceDecisionSaving &&
                                (
                                    decision == null ||
                                        reason.trim().ifEmpty {
                                            null
                                        } != decision.reason
                                    ),
                        saving = state.occurrenceDecisionSaving,
                        destructive = false,
                        motionAllowed = motionAllowed,
                    ) {
                        if (decision == null) {
                            onAction(
                                BloodPressureAction.ConfirmNotPerformed(
                                    occurrence,
                                    reason,
                                ),
                            )
                        } else {
                            onAction(
                                BloodPressureAction.CorrectNotPerformed(
                                    reason,
                                ),
                            )
                        }
                    }
                    MedicationLifecycleCancelAction(
                        enabled = !state.occurrenceDecisionSaving,
                        label = if (startNotPerformed) "Cancelar" else "Volver a las opciones",
                        onClick = {
                            if (startNotPerformed) {
                                onAction(BloodPressureAction.DismissOccurrence)
                            } else if (decision == null) {
                                confirming = false
                            } else {
                                correctingReason = false
                            }
                        },
                    )
                }
            }
            if (decision != null) {
                SectionTitle(
                    "Historial de la declaración",
                    small = true,
                )
                when {
                    state.occurrenceDecisionRevisionsLoading ->
                        CircularProgressIndicator()
                    state.occurrenceDecisionRevisions.isEmpty() ->
                        SecondaryText(
                            "Aún no hay revisiones disponibles.",
                        )
                    else -> MeasurementPanel {
                        state.occurrenceDecisionRevisions
                            .sortedByDescending(
                                GlucoseOccurrenceDecisionRevision::
                                    revisionNumber,
                            )
                            .forEachIndexed { index, revision ->
                                if (index > 0) ActionDivider()
                                PressureDecisionRevisionRow(
                                    revision,
                                )
                            }
                    }
                }
            }
            state.occurrenceDecisionError?.let {
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
private fun PressureDecisionRevisionRow(
    revision: GlucoseOccurrenceDecisionRevision,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(
            when (revision.operation) {
                GlucoseOccurrenceDecisionRevisionOperation.CONFIRMED ->
                    "Declaración original"
                GlucoseOccurrenceDecisionRevisionOperation.CORRECTED ->
                    "Corrección " +
                        (revision.revisionNumber - 1)
                GlucoseOccurrenceDecisionRevisionOperation
                    .CORRECTED_TO_RECORDED ->
                    "Corregida a realizada"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        SecondaryText(
            if (
                revision.operation ==
                GlucoseOccurrenceDecisionRevisionOperation
                    .CORRECTED_TO_RECORDED
            ) {
                "Se vinculó una medición real a este horario."
            } else {
                revision.reason?.let { "Motivo: $it" } ?: "Sin motivo"
            },
        )
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
