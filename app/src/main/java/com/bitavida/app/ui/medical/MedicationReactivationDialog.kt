package com.bitavida.app.ui.medical

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationReactivationIssue
import com.bitavida.app.domain.model.reactivationIssue
import com.bitavida.app.domain.usecase.MedicalCareField
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private data class ReactivationBlocker(
    val title: String,
    val message: String,
    val resolution: ReactivationResolution,
)

private enum class ReactivationResolution {
    CORRECT_DATA,
    CHANGE_INDICATION,
}

@Composable
internal fun MedicationReactivationDialog(
    draft: MedicationLifecycleDraft,
    medication: MedicationPlan,
    indication: MedicalIndication?,
    errors: Set<MedicalCareField>,
    saving: Boolean,
    motionAllowed: Boolean,
    hasPendingReactivation: Boolean,
    onChange: (MedicationLifecycleDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCorrectData: () -> Unit,
    onChangeIndication: () -> Unit,
    onCancelScheduled: () -> Unit,
) {
    val selectedDate = runCatching {
        LocalDate.parse(draft.effectiveOn)
    }.getOrNull()
    val selectedTime = runCatching {
        LocalTime.parse(draft.effectiveTime)
    }.getOrNull()
    val selectedAt = if (selectedDate != null && selectedTime != null) {
        LocalDateTime.of(selectedDate, selectedTime)
    } else {
        null
    }
    val validationMoment = if (
        draft.reactivationTiming == MedicationReactivationTiming.NOW
    ) {
        LocalDateTime.now()
    } else {
        selectedAt
    }
    val blocker = medication.reactivationBlocker(indication, validationMoment)
    val invalidSchedule =
        draft.reactivationTiming == MedicationReactivationTiming.SCHEDULED &&
            (
                selectedAt == null ||
                    !selectedAt.isAfter(LocalDateTime.now()) ||
                    MedicalCareField.MEDICATION_DATES in errors
                )
    val canConfirm = blocker == null && !invalidSchedule && !saving
    val transitionDuration =
        if (motionAllowed) BitavidaFluidMotion.StateTransition else 0

    MedicationLifecycleSheetFrame(
        medication = medication,
        title = "Reactivar medicamento",
        saving = saving,
        motionAllowed = motionAllowed,
        onDismiss = onDismiss,
    ) {
        val dateTimePicker = LocalBitavidaDateTimePickerController.current
        Text(
            "Se retomarán las tomas y avisos según la indicación registrada. " +
                "El periodo suspendido permanecerá en el historial.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            ReactivationChoice(
                selected =
                    draft.reactivationTiming ==
                    MedicationReactivationTiming.NOW,
                title = "Reactivar ahora",
                description = "Continuar desde este momento.",
                enabled = !saving,
            ) {
                val now = LocalDateTime.now()
                onChange(
                    draft.copy(
                        reactivationTiming = MedicationReactivationTiming.NOW,
                        effectiveOn = now.toLocalDate().toString(),
                        effectiveTime = now.toLocalTime()
                            .withSecond(0)
                            .withNano(0)
                            .toString(),
                    ),
                )
            }
            ReactivationChoice(
                selected =
                    draft.reactivationTiming ==
                    MedicationReactivationTiming.SCHEDULED,
                title = "Programar reactivación",
                description = "Elegir fecha y hora.",
                enabled = !saving,
            ) {
                onChange(
                    draft.copy(
                        reactivationTiming =
                            MedicationReactivationTiming.SCHEDULED,
                    ),
                )
            }
        }
        AnimatedVisibility(
            visible =
                draft.reactivationTiming ==
                    MedicationReactivationTiming.SCHEDULED,
            enter = expandVertically(tween(transitionDuration)) +
                fadeIn(tween(transitionDuration)),
            exit = shrinkVertically(tween(transitionDuration)) +
                fadeOut(tween(transitionDuration)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    BitavidaSpacing.ExtraSmall,
                ),
            ) {
                Text(
                    "Reactivar desde",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        showMedicationDatePicker(dateTimePicker, selectedDate) {
                            onChange(draft.copy(effectiveOn = it.toString()))
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        selectedDate?.reactivationDateLabel()
                            ?: "Elegir fecha",
                    )
                }
                OutlinedButton(
                    onClick = {
                        showMedicationTimePicker(
                            dateTimePicker,
                            selectedTime?.let {
                                MedicationTime(it.hour, it.minute)
                            },
                        ) {
                            onChange(
                                draft.copy(
                                    effectiveTime = LocalTime.of(
                                        it.hour,
                                        it.minute,
                                    ).toString(),
                                ),
                            )
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        selectedTime?.reactivationTimeLabel()
                            ?: "Elegir hora",
                    )
                }
                if (invalidSchedule) {
                    Text(
                        "Elige una fecha y hora futuras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        blocker?.let {
            ReactivationWarning(
                blocker = it,
                saving = saving,
                onCorrectData = onCorrectData,
                onChangeIndication = onChangeIndication,
            )
        }
        if (hasPendingReactivation) {
            TextButton(
                onClick = onCancelScheduled,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    "Cancelar reactivación programada",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        MedicationLifecyclePrimaryAction(
            label = if (hasPendingReactivation) {
                "Guardar reprogramación"
            } else {
                "Confirmar reactivación"
            },
            icon = R.drawable.ic_v2_clock,
            enabled = canConfirm,
            saving = saving,
            destructive = false,
            motionAllowed = motionAllowed,
            onClick = onConfirm,
        )
        MedicationLifecycleCancelAction(
            enabled = !saving,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun ReactivationWarning(
    blocker: ReactivationBlocker,
    saving: Boolean,
    onCorrectData: () -> Unit,
    onChangeIndication: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                blocker.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                blocker.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(
                onClick = when (blocker.resolution) {
                    ReactivationResolution.CORRECT_DATA -> onCorrectData
                    ReactivationResolution.CHANGE_INDICATION ->
                        onChangeIndication
                },
                enabled = !saving,
            ) {
                Text(
                    when (blocker.resolution) {
                        ReactivationResolution.CORRECT_DATA ->
                            "Corregir datos"
                        ReactivationResolution.CHANGE_INDICATION ->
                            "Cambiar indicación médica"
                    },
                )
            }
        }
    }
}

@Composable
private fun ReactivationChoice(
    selected: Boolean,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .padding(BitavidaSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun MedicationPlan.reactivationBlocker(
    indication: MedicalIndication?,
    effectiveAt: LocalDateTime?,
): ReactivationBlocker? {
    if (effectiveAt == null) return null
    return when (reactivationIssue(indication, effectiveAt)) {
        null -> null
        MedicationReactivationIssue.MISSING_CONFIGURATION -> ReactivationBlocker(
            title = "Faltan datos de la programación",
            message = "Corrige los datos necesarios antes de reactivar.",
            resolution = ReactivationResolution.CORRECT_DATA,
        )
        MedicationReactivationIssue.PLAN_NOT_YET_VALID -> ReactivationBlocker(
            title = "El plan todavía no inicia",
            message = "Elige una reactivación dentro de la vigencia registrada.",
            resolution = ReactivationResolution.CHANGE_INDICATION,
        )
        MedicationReactivationIssue.PLAN_ENDED -> ReactivationBlocker(
            title = "El plan registrado ya terminó",
            message = "Registra una indicación aplicable; Bitavida no extenderá el plan automáticamente.",
            resolution = ReactivationResolution.CHANGE_INDICATION,
        )
        MedicationReactivationIssue.LINKED_INDICATION_MISSING -> ReactivationBlocker(
            title = "No se encontró la indicación vinculada",
            message = "Corrige los datos antes de reactivar.",
            resolution = ReactivationResolution.CORRECT_DATA,
        )
        MedicationReactivationIssue.INDICATION_INACTIVE -> ReactivationBlocker(
            title = "La indicación no está activa",
            message = "Registra una indicación aplicable antes de reactivar.",
            resolution = ReactivationResolution.CHANGE_INDICATION,
        )
        MedicationReactivationIssue.INDICATION_NOT_YET_VALID -> ReactivationBlocker(
            title = "La indicación todavía no está vigente",
            message = "Elige una fecha dentro de su vigencia o actualízala.",
            resolution = ReactivationResolution.CHANGE_INDICATION,
        )
        MedicationReactivationIssue.INDICATION_EXPIRED -> ReactivationBlocker(
            title = "La indicación ya no está vigente",
            message = "Registra una indicación aplicable antes de reactivar.",
            resolution = ReactivationResolution.CHANGE_INDICATION,
        )
    }
}

private fun LocalDate.reactivationDateLabel(): String =
    format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault()),
    )

private fun LocalTime.reactivationTimeLabel(): String =
    format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()),
    )
