package com.bitavida.app.ui.medical

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.usecase.MedicalCareField
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.ui.components.BitavidaPickerDragHandle
import com.bitavida.app.ui.components.BitavidaStatusPill
import com.bitavida.app.ui.components.rememberBitavidaDateTimePickerController
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val intakeSheetLocale = Locale.forLanguageTag("es-MX")
private val intakeSheetDateFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", intakeSheetLocale)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicationIntakeSheet(
    draft: MedicationIntakeDraft,
    medication: MedicationPlan,
    errors: Set<MedicalCareField>,
    saving: Boolean,
    motionAllowed: Boolean,
    onChange: (MedicationIntakeDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dateTimePicker = rememberBitavidaDateTimePickerController()
    val selectorActive = dateTimePicker.isActive
    BackHandler(enabled = selectorActive) { dateTimePicker.dismiss() }
    val scheduledDate = runCatching { LocalDate.parse(draft.scheduledDate) }.getOrNull()
    val scheduledTime = runCatching { LocalTime.parse(draft.scheduledTime) }.getOrNull()
    val actualTime = runCatching { LocalTime.parse(draft.actualTime) }.getOrNull()
    val schedule = remember(medication, scheduledDate) {
        scheduledDate?.let { medicationPlanSnapshotForDay(medication, it) } ?: medication
    }
    val occurrences = remember(schedule, scheduledDate) {
        scheduledDate?.let { medicationOccurrencesForDay(schedule, it) }.orEmpty()
    }
    val occurrence = remember(
        occurrences,
        draft.scheduledOccurrenceId,
        scheduledDate,
        scheduledTime,
    ) {
        occurrences.firstOrNull { it.id == draft.scheduledOccurrenceId }
            ?: occurrences.singleOrNull {
                it.startsAt.toLocalDate() == scheduledDate &&
                    it.startsAt.toLocalTime() == scheduledTime
            }
    }
    val isTaken = draft.outcome in setOf(
        MedicationIntakeOutcome.COMPLETED,
        MedicationIntakeOutcome.PARTIAL,
    )
    val isNotTaken = draft.outcome in setOf(
        MedicationIntakeOutcome.OMITTED,
        MedicationIntakeOutcome.REFUSED,
    )
    val pendingOutcome = !draft.outcomeExplicitlySelected
    val title = if (draft.outcomeExplicitlySelected && isNotTaken) {
        "Editar nota o motivo"
    } else {
        "Otra hora o nota"
    }
    val statusLabel = intakeSheetStatusLabel(
        draft = draft,
        occurrence = occurrence,
        scheduledDate = scheduledDate,
        scheduledTime = scheduledTime,
    )
    val dateAndReference = intakeSheetDateAndReference(
        draft = draft,
        occurrence = occurrence,
        scheduledDate = scheduledDate,
        scheduledTime = scheduledTime,
        context = context,
    )
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    var optionalExpanded by rememberSaveable(
        draft.recordId,
        draft.scheduledOccurrenceId,
    ) {
        mutableStateOf(
            draft.observation.isNotBlank() ||
                draft.asNeededEntry ||
                draft.expandNoteOnOpen ||
                (draft.outcomeExplicitlySelected && isNotTaken),
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = schedule.currentRevision.configuration

    ModalBottomSheet(
        onDismissRequest = {
            when {
                selectorActive -> dateTimePicker.dismiss()
                !saving -> onDismiss()
            }
        },
        sheetState = sheetState,
        sheetGesturesEnabled = !selectorActive,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            if (selectorActive) {
                BitavidaPickerDragHandle()
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.width(72.dp).height(5.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {}
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    start = BitavidaSpacing.ScreenHorizontal,
                    end = BitavidaSpacing.ScreenHorizontal,
                    bottom = BitavidaSpacing.Default,
                ),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        ) {
            if (selectorActive) {
                dateTimePicker.Content()
            } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_v2_close),
                            contentDescription = "Cerrar sin guardar",
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    MedicationListProductIcon(
                        configuration?.form,
                        Modifier.padding(10.dp).size(28.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                    ) {
                        Text(
                            medication.currentRevision.medicationName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        BitavidaStatusPill(
                            text = statusLabel,
                            isPositive = draft.outcomeExplicitlySelected && isTaken,
                        )
                    }
                    Text(
                        dateAndReference,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (draft.recordId == null && !draft.asNeededEntry) {
                IntakeOutcomeChoice(
                    selectedOutcome = draft.outcome.takeIf {
                        draft.outcomeExplicitlySelected
                    },
                    saving = saving,
                    onTaken = {
                        optionalExpanded = draft.observation.isNotBlank()
                        onChange(
                            draft.copy(
                                outcome = MedicationIntakeOutcome.COMPLETED,
                                outcomeExplicitlySelected = true,
                            ),
                        )
                    },
                    onNotTaken = {
                        optionalExpanded = true
                        onChange(
                            draft.copy(
                                outcome = MedicationIntakeOutcome.OMITTED,
                                outcomeExplicitlySelected = true,
                            ),
                        )
                    },
                )
                if (
                    pendingOutcome &&
                    MedicalCareField.INTAKE_OUTCOME in errors
                ) {
                    Text(
                        "Elige si se tomó o no se tomó antes de registrar.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (draft.asNeededEntry || (draft.outcomeExplicitlySelected && isTaken)) {
                ActualIntakeTimeSurface(
                    actualTime = actualTime,
                    enabled = !saving,
                    onClick = {
                        showMedicationTimePicker(
                            dateTimePicker,
                            actualTime?.let { MedicationTime(it.hour, it.minute) },
                        ) { selected ->
                            onChange(
                                draft.copy(
                                    actualTime = LocalTime.of(
                                        selected.hour,
                                        selected.minute,
                                    ).toString(),
                                ),
                            )
                        }
                    },
                )
                if (MedicalCareField.INTAKE_ACTUAL_TIME in errors) {
                    Text(
                        "Selecciona la hora real de la toma.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (draft.asNeededEntry) {
                IntakeObservationField(
                    draft = draft,
                    label = "¿Por qué fue necesaria la toma?",
                    required = true,
                    errors = errors,
                    onChange = onChange,
                )
            } else {
                val noteLabel = if (
                    draft.outcomeExplicitlySelected && isNotTaken
                ) {
                    "Motivo de la omisión"
                } else {
                    "Nota"
                }
                IntakeOptionalNoteToggle(
                    label = if (draft.observation.isBlank()) {
                        if (isNotTaken) "Añadir motivo" else "Añadir nota"
                    } else {
                        if (isNotTaken) "Editar motivo" else "Editar nota"
                    },
                    expanded = optionalExpanded,
                    hasText = draft.observation.isNotBlank(),
                    enabled = !saving,
                    onClick = { optionalExpanded = !optionalExpanded },
                )
                AnimatedVisibility(
                    visible = optionalExpanded,
                    enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                    exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
                ) {
                    IntakeObservationField(
                        draft = draft,
                        label = "$noteLabel (opcional)",
                        required = false,
                        errors = errors,
                        onChange = onChange,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onConfirm,
                enabled = !saving && draft.outcomeExplicitlySelected,
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = BitavidaSpacing.PrimaryControlHeight),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    when {
                        saving -> "Guardando…"
                        draft.recordId == null -> "Registrar toma"
                        else -> "Guardar cambios"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            }
        }
    }
}

@Composable
private fun IntakeOutcomeChoice(
    selectedOutcome: MedicationIntakeOutcome?,
    saving: Boolean,
    onTaken: () -> Unit,
    onNotTaken: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Text(
            "¿Qué ocurrió con esta toma?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Elige un resultado para registrarla. La hora y la nota no la confirman por sí solas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onTaken,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) {
            Text(
                if (
                    selectedOutcome == MedicationIntakeOutcome.COMPLETED ||
                    selectedOutcome == MedicationIntakeOutcome.PARTIAL
                ) {
                    "✓ Se tomó"
                } else {
                    "Se tomó"
                },
            )
        }
        OutlinedButton(
            onClick = onNotTaken,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) {
            Text(
                if (
                    selectedOutcome == MedicationIntakeOutcome.OMITTED ||
                    selectedOutcome == MedicationIntakeOutcome.REFUSED
                ) {
                    "✓ No se tomó"
                } else {
                    "No se tomó"
                },
            )
        }
    }
}

@Composable
private fun ActualIntakeTimeSurface(
    actualTime: LocalTime?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Editar hora real de la toma"
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(BitavidaSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SheetIcon(R.drawable.ic_v2_clock, "Hora real")
                    Text(
                        "Hora real de la toma",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    actualTime?.let {
                        MedicationTime(it.hour, it.minute).displayLabel(context)
                    } ?: "Elegir hora",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Box(
                    modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    SheetIcon(R.drawable.ic_v2_edit, null)
                }
            }
        }
    }
}

@Composable
private fun IntakeOptionalNoteToggle(
    label: String,
    expanded: Boolean,
    hasText: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = BitavidaSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetIcon(R.drawable.ic_v2_file, null)
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SheetIcon(
            if (hasText || expanded) R.drawable.ic_v2_edit else R.drawable.ic_v2_plus,
            if (expanded) "Contraer campo" else "Abrir campo",
        )
    }
}

@Composable
private fun IntakeObservationField(
    draft: MedicationIntakeDraft,
    label: String,
    required: Boolean,
    errors: Set<MedicalCareField>,
    onChange: (MedicationIntakeDraft) -> Unit,
) {
    val hasError = MedicalCareField.INTAKE_OBSERVATION in errors
    BitavidaLimitedTextField(
        value = draft.observation,
        limit = com.bitavida.app.domain.validation.CaptureLimit.OBSERVATION,
        onValueChange = { onChange(draft.copy(observation = it)) },
        label = { Text(label) },
        minLines = 2,
        isError = hasError,
        supportingText = if (required && hasError) {
            { Text("Escribe por qué fue necesaria la toma.") }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SheetIcon(resource: Int, description: String?) {
    Image(
        painter = painterResource(resource),
        contentDescription = description,
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
    )
}

private fun intakeSheetStatusLabel(
    draft: MedicationIntakeDraft,
    occurrence: UpcomingMedicationAdministration?,
    scheduledDate: LocalDate?,
    scheduledTime: LocalTime?,
): String {
    if (!draft.outcomeExplicitlySelected) {
        val scheduledAt = occurrence?.startsAt ?: if (
            scheduledDate != null && scheduledTime != null
        ) {
            LocalDateTime.of(scheduledDate, scheduledTime)
        } else {
            null
        }
        return if (scheduledAt?.isAfter(LocalDateTime.now()) == true) {
            "Programada"
        } else {
            "Sin confirmar"
        }
    }
    return when (draft.outcome) {
        MedicationIntakeOutcome.COMPLETED,
        MedicationIntakeOutcome.PARTIAL,
        -> "Se tomó"
        MedicationIntakeOutcome.OMITTED,
        MedicationIntakeOutcome.REFUSED,
        -> "No se tomó"
        MedicationIntakeOutcome.UNCONFIRMED -> "Sin confirmar"
    }
}

private fun intakeSheetDateAndReference(
    draft: MedicationIntakeDraft,
    occurrence: UpcomingMedicationAdministration?,
    scheduledDate: LocalDate?,
    scheduledTime: LocalTime?,
    context: android.content.Context,
): String {
    val date = scheduledDate?.format(intakeSheetDateFormatter)
        ?.replaceFirstChar { it.titlecase(intakeSheetLocale) }
        ?: "Fecha no disponible"
    if (draft.asNeededEntry) return date
    val reference = when {
        occurrence?.activityRelation != null -> {
            occurrence.activityRelation.displayName(occurrence.customActivity) +
                " · Aviso a las " +
                MedicationTime(
                    occurrence.startsAt.hour,
                    occurrence.startsAt.minute,
                ).displayLabel(context)
        }
        occurrence?.endsAt != null -> {
            "Ventana " +
                MedicationTime(
                    occurrence.startsAt.hour,
                    occurrence.startsAt.minute,
                ).displayLabel(context) +
                " – " +
                MedicationTime(
                    occurrence.endsAt.hour,
                    occurrence.endsAt.minute,
                ).displayLabel(context) +
                if (occurrence.startsAt.toLocalDate() != occurrence.endsAt.toLocalDate()) {
                    " · día siguiente"
                } else {
                    ""
                }
        }
        scheduledTime != null -> {
            "Prevista a las " +
                MedicationTime(scheduledTime.hour, scheduledTime.minute)
                    .displayLabel(context)
        }
        else -> null
    }
    return listOfNotNull(date, reference).joinToString(" · ")
}
