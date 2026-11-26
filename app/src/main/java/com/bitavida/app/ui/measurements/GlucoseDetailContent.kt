package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.BitavidaLimitedTextField

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.GlucoseTodaySummary
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementReminderStatus
import com.bitavida.app.domain.model.projectGlucoseToday
import com.bitavida.app.domain.usecase.MeasurementSummary
import com.bitavida.app.domain.usecase.projectMeasurementHistoryWeek
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.CurrentIndicationCardFrame
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.MedicationLifecycleCancelAction
import com.bitavida.app.ui.medical.MedicationLifecyclePrimaryAction
import com.bitavida.app.ui.medical.MedicationLifecycleSheetFrame
import com.bitavida.app.ui.medical.TodayRowFrame
import com.bitavida.app.ui.medical.TodayStatusIcon
import com.bitavida.app.ui.medical.TodayStatusVisual
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun GlucoseDetailContent(
    summary: MeasurementSummary,
    records: List<MeasurementRecord>,
    today: LocalDate,
    onRegister: ((MeasurementKind) -> Unit)?,
    onFollowUpAction: ((MeasurementKind, MeasurementFollowUpAction) -> Unit)?,
    onHelp: (() -> Unit)?,
    onHistory: (() -> Unit)? = null,
    glucoseFollowUp: GlucoseFollowUp? = null,
    glucoseDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    onRegisterOccurrence: ((GlucoseScheduledOccurrence) -> Unit)? = null,
    onNotPerformed: ((GlucoseScheduledOccurrence, String?) -> Unit)? = null,
) {
    val presentation = remember(summary, glucoseFollowUp) {
        glucoseDetailPresentation(
            summary = summary,
            hasFollowUpHistory = glucoseFollowUp?.revisions?.isNotEmpty() == true,
        )
    }
    var selectedDateText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = LocalDate.parse(selectedDateText)
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val week = remember(records, weekStart, today) {
        projectMeasurementHistoryWeek(records, MeasurementKind.CAPILLARY_GLUCOSE, weekStart, today)
    }
    GlucoseLatestMeasurement(
        summary = summary,
        presentation = presentation,
        today = today,
        records = records,
        followUp = glucoseFollowUp,
        decisions = glucoseDecisions,
        onRegister = onRegister,
        onRegisterOccurrence = onRegisterOccurrence,
        onNotPerformed = onNotPerformed,
    )
    GlucoseWeekEvaluation(
        week = week,
        selectedDate = selectedDate,
        onSelectDate = { selectedDateText = it.toString() },
        onHistory = onHistory,
    )
    if (presentation.showIndication) {
        summary.followUp?.let {
            GlucoseCurrentIndication(
                followUp = it,
                storedFollowUp = glucoseFollowUp,
            )
        }
    }
    GlucoseManagement(presentation, onFollowUpAction, onHelp)
}

@Composable
private fun GlucoseLatestMeasurement(
    summary: MeasurementSummary,
    presentation: GlucoseDetailPresentation,
    today: LocalDate,
    records: List<MeasurementRecord>,
    followUp: GlucoseFollowUp?,
    decisions: List<GlucoseOccurrenceDecision>,
    onRegister: ((MeasurementKind) -> Unit)?,
    onRegisterOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
    onNotPerformed: ((GlucoseScheduledOccurrence, String?) -> Unit)?,
) {
    var pendingNotPerformed by remember {
        mutableStateOf<GlucoseScheduledOccurrence?>(null)
    }
    var notPerformedReason by rememberSaveable { mutableStateOf("") }
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val todaySummary = remember(today, followUp, records, decisions) {
        projectGlucoseToday(today, followUp, records, decisions)
    }
    MeasurementPanel(
        modifier = Modifier.testTag("glucose_latest_measurement"),
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        SectionTitle("Última medición", small = true)
        val record = summary.latest
        if (record == null) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text(
                    text = "Sin registros",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SecondaryText("Tu primera lectura iniciará este historial.")
            }
        } else {
            val date = if (record.occurredAt.toLocalDate() == today) {
                "Hoy, " + record.occurredAt.format(
                    DateTimeFormatter.ofPattern("HH:mm", measurementLocale),
                )
            } else {
                record.occurredAt.format(measurementDateTime)
            }
            val timestamp: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    MeasurementIcon(R.drawable.ic_v2_clock, 18)
                    SecondaryText(date)
                }
            }
            val context = record.complement()
            val largeText = LocalDensity.current.fontScale >= 1.5f
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if ((!largeText) && (maxWidth >= 300.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(BitavidaSpacing.Default),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(BitavidaSpacing.Small),
                        ) {
                            MeasurementReading(record, prominent = true)
                            timestamp()
                        }
                        context?.let {
                            GlucoseMomentChip(
                                label = it,
                                modifier = Modifier.padding(top = BitavidaSpacing.ExtraSmall),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    ) {
                        context?.let {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                GlucoseMomentChip(it)
                            }
                        }
                        MeasurementReading(record, prominent = true)
                        timestamp()
                    }
                }
            }
        }
        MeasurementButton(
            label = presentation.registerLabel,
            onClick = { onRegister?.invoke(MeasurementKind.CAPILLARY_GLUCOSE) },
            modifier = Modifier.fillMaxWidth().testTag("glucose_register"),
            enabled = onRegister != null,
            icon = R.drawable.ic_v2_plus,
            accessibleLabel = presentation.registerLabel +
                if (onRegister == null) ", captura de glucosa no disponible" else ", glucosa capilar",
        )
        if (onRegister == null) {
            SecondaryText("El registro de glucosa todavía no está disponible.")
        }
    }
    if (presentation.showTodayRegister) {
        GlucoseTodayRegister(
            summary = todaySummary,
            onRegisterOccurrence = onRegisterOccurrence,
        ) { pendingNotPerformed = it }
    }
    pendingNotPerformed?.let { occurrence ->
        val dismissNotPerformed = {
            pendingNotPerformed = null
            notPerformedReason = ""
        }
        MedicationLifecycleSheetFrame(
            medication = null,
            title = "Confirmar que no se realizó",
            subtitle = "Esta decisión solo corresponde a este horario.",
            saving = false,
            motionAllowed = motionAllowed,
            onDismiss = dismissNotPerformed,
        ) {
            SecondaryText(
                "La programación se conservará y ninguna ausencia se registrará sin esta confirmación.",
            )
            BitavidaLimitedTextField(
                value = notPerformedReason,
                limit = CaptureLimit.CHANGE_REASON,
                onValueChange = { notPerformedReason = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Motivo opcional") },
                minLines = 2,
                maxLines = 4,
            )
            MedicationLifecyclePrimaryAction(
                label = "Confirmar no realizada",
                icon = R.drawable.ic_v2_close,
                enabled = onNotPerformed != null,
                saving = false,
                destructive = false,
                motionAllowed = motionAllowed,
            ) {
                onNotPerformed?.invoke(
                    occurrence,
                    notPerformedReason.trim().takeIf(String::isNotEmpty),
                )
                dismissNotPerformed()
            }
            MedicationLifecycleCancelAction(
                enabled = true,
                label = "Conservar sin confirmar",
                onClick = dismissNotPerformed,
            )
        }
    }
}

@Composable
internal fun GlucoseTodayRegister(
    summary: GlucoseTodaySummary,
    onRegisterOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
    showMeasurementContext: Boolean = true,
    onNotPerformed: (GlucoseScheduledOccurrence) -> Unit,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    var selectedOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(summary.scheduled) {
        if (
            (selectedOccurrenceId == null) ||
            summary.scheduled.none { (occurrence) ->
                occurrence.id == selectedOccurrenceId
            }
        ) {
            selectedOccurrenceId = summary.scheduled
                .firstOrNull { (_, state) ->
                    state == GlucoseOccurrenceState.UNCONFIRMED
                }
                ?.occurrence
                ?.id
                ?: summary.scheduled.firstOrNull()?.occurrence?.id
        }
    }
    val registered = summary.scheduled.count { (_, state) ->
        state == GlucoseOccurrenceState.RECORDED
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("Registro de hoy")
        Text(
            text = "$registered de ${summary.scheduled.size} registradas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(6.dp)) {
            if (summary.scheduled.isEmpty()) {
                Text(
                    text = "Sin mediciones programadas para hoy.",
                    modifier = Modifier.padding(BitavidaSpacing.Medium),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            summary.scheduled.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(
                            alpha = 0.45f,
                        ),
                    )
                }
                val (occurrence) = item
                val expanded = selectedOccurrenceId == occurrence.id
                TodayRowFrame(
                    expanded = expanded,
                    duration = duration,
                    onSelect = {
                        selectedOccurrenceId = occurrence.id.takeUnless {
                            expanded
                        }
                    },
                    actions = {
                        GlucoseTodayRowActions(
                            item = item,
                            onRegisterOccurrence = onRegisterOccurrence,
                            onNotPerformed = onNotPerformed,
                        )
                    },
                ) {
                    GlucoseTodayRowHeading(
                        item = item,
                        showMeasurementContext = showMeasurementContext,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.GlucoseTodayRowHeading(
    item: com.bitavida.app.domain.model.GlucoseTodayOccurrence,
    showMeasurementContext: Boolean,
) {
    val record = item.record
    val stateLabel = when (item.state) {
        GlucoseOccurrenceState.RECORDED -> "Registrada"
        GlucoseOccurrenceState.UNCONFIRMED -> "Sin confirmar"
        GlucoseOccurrenceState.NOT_PERFORMED -> "No realizada"
    }
    val detail = when (item.state) {
        GlucoseOccurrenceState.RECORDED -> record?.let {
            it.valueLabel() + " " + it.kind.unit() + " · " +
                it.occurredAt.format(
                    DateTimeFormatter.ofPattern("HH:mm", measurementLocale),
                )
        }.orEmpty()
        GlucoseOccurrenceState.UNCONFIRMED -> if (showMeasurementContext) {
            item.occurrence.suggestedContext.todayLabel()
        } else {
            "Pendiente de confirmar"
        }
        GlucoseOccurrenceState.NOT_PERFORMED ->
            item.decision?.reason?.takeIf(String::isNotBlank)
                ?: "Confirmada explícitamente"
    }
    Text(
        text = item.occurrence.todayScheduleLabel(),
        modifier = Modifier.weight(0.4f),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier = Modifier.weight(0.6f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TodayStatusIcon(
            when (item.state) {
                GlucoseOccurrenceState.RECORDED -> TodayStatusVisual.COMPLETE
                GlucoseOccurrenceState.UNCONFIRMED -> TodayStatusVisual.PENDING
                GlucoseOccurrenceState.NOT_PERFORMED -> TodayStatusVisual.NEGATIVE
            },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GlucoseTodayRowActions(
    item: com.bitavida.app.domain.model.GlucoseTodayOccurrence,
    onRegisterOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
    onNotPerformed: (GlucoseScheduledOccurrence) -> Unit,
) {
    when (item.state) {
        GlucoseOccurrenceState.UNCONFIRMED -> {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = (maxWidth < 260.dp) ||
                    (LocalDensity.current.fontScale >= 1.5f)
                if (compact) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(
                            BitavidaSpacing.ExtraSmall,
                        ),
                    ) {
                        GlucosePendingActionButtons(
                            occurrence = item.occurrence,
                            onRegisterOccurrence = onRegisterOccurrence,
                            onNotPerformed = onNotPerformed,
                            stacked = true,
                        )
                    }
                } else {
                    GlucosePendingActionButtons(
                        occurrence = item.occurrence,
                        onRegisterOccurrence = onRegisterOccurrence,
                        onNotPerformed = onNotPerformed,
                        stacked = false,
                    )
                }
            }
        }
        GlucoseOccurrenceState.RECORDED -> item.record?.let { record ->
            SecondaryText(
                listOfNotNull(
                    record.complement(),
                    "Registrada a las " + record.occurredAt.format(
                        DateTimeFormatter.ofPattern(
                            "HH:mm",
                            measurementLocale,
                        ),
                    ),
                ).joinToString(" · "),
            )
        }
        GlucoseOccurrenceState.NOT_PERFORMED -> {
            SecondaryText(
                item.decision?.reason?.takeIf(String::isNotBlank)
                    ?: "No se realizó; decisión confirmada por la persona cuidadora.",
            )
        }
    }
}

@Composable
private fun GlucosePendingActionButtons(
    occurrence: GlucoseScheduledOccurrence,
    onRegisterOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
    onNotPerformed: (GlucoseScheduledOccurrence) -> Unit,
    stacked: Boolean,
) {
    val register: @Composable (Modifier) -> Unit = { modifier ->
        CurrentDetailButton(
            kind = DetailButtonKind.FILLED,
            onClick = {
                onRegisterOccurrence?.invoke(occurrence)
            },
            enabled = onRegisterOccurrence != null,
            modifier = modifier.heightIn(min = 48.dp),
        ) {
            Text("Registrar")
        }
    }
    val notPerformed: @Composable (Modifier) -> Unit = { modifier ->
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED,
            onClick = { onNotPerformed(occurrence) },
            modifier = modifier.heightIn(min = 48.dp),
            strongOutline = true,
        ) {
            Text("No se realizó")
        }
    }
    if (stacked) {
        register(Modifier.fillMaxWidth())
        notPerformed(Modifier.fillMaxWidth())
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                BitavidaSpacing.Small,
            ),
        ) {
            register(Modifier.weight(1f))
            notPerformed(Modifier.weight(1f))
        }
    }
}

private fun GlucoseScheduledOccurrence.todayScheduleLabel(): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", measurementLocale)
    val start = scheduledAt.format(formatter)
    val end = windowEndAt ?: return start
    return start + " – " + end.format(formatter) +
        if (end.toLocalDate() != scheduledAt.toLocalDate()) {
            " · día siguiente"
        } else {
            ""
        }
}

private fun com.bitavida.app.domain.model.GlucoseContext.todayLabel() = when (this) {
    com.bitavida.app.domain.model.GlucoseContext.FASTING -> "En ayunas"
    com.bitavida.app.domain.model.GlucoseContext.BEFORE_MEAL -> "Antes de comer"
    com.bitavida.app.domain.model.GlucoseContext.AFTER_MEAL -> "Después de comer"
    com.bitavida.app.domain.model.GlucoseContext.BEFORE_SLEEP -> "Antes de dormir"
    com.bitavida.app.domain.model.GlucoseContext.OTHER -> "Otro momento"
    com.bitavida.app.domain.model.GlucoseContext.RESTING -> "En reposo"
    com.bitavida.app.domain.model.GlucoseContext.AFTER_PHYSICAL_ACTIVITY ->
        "Después de actividad física"
    com.bitavida.app.domain.model.GlucoseContext.UPON_WAKING ->
        "Al despertar"
    com.bitavida.app.domain.model.GlucoseContext.UNSPECIFIED -> "Sin contexto"
}

@Composable
private fun GlucoseMomentChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = BitavidaSpacing.Medium,
                vertical = BitavidaSpacing.ExtraSmall,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun GlucoseCurrentIndication(
    followUp: MeasurementFollowUp,
    storedFollowUp: GlucoseFollowUp?,
    expansionKeyFallback: String = "glucose-follow-up",
    testTag: String = "glucose_current_indication",
    showMeasurementContext: Boolean = true,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val reminders = followUp.reminders
    val reminderLabel = when (reminders?.status) {
        MeasurementReminderStatus.DISABLED ->
            "Avisos del celular desactivados"
        MeasurementReminderStatus.CONFIGURED ->
            "Avisos del celular configurados"
        MeasurementReminderStatus.SYSTEM_LIMITED ->
            "Avisos con precisión limitada por el sistema"
        null -> "Sin información de avisos"
    }
    val summary = listOfNotNull(
        followUp.frequency?.takeIf(String::isNotBlank),
        followUp.periodicity?.takeIf(String::isNotBlank),
    ).joinToString(" · ").ifBlank { followUp.summary }

    CurrentIndicationCardFrame(
        expansionKey = storedFollowUp?.id ?: expansionKeyFallback,
        title = if (followUp.suspended) {
            "Indicación del seguimiento"
        } else {
            "Indicación vigente"
        },
        summary = summary,
        reminderLabel = reminderLabel,
        motionAllowed = motionAllowed,
        modifier = Modifier.testTag(testTag),
        supportingContent = {
            if (followUp.suspended) {
                SecondaryText(
                    "Programación desactivada. Los registros se conservan.",
                )
            }
        },
        detailContent = {
            val revision = storedFollowUp?.currentRevision
            if (revision == null) {
                SecondaryText(
                    "No hay una programación estructurada para mostrar.",
                )
            } else {
                GlucoseProgrammingDetails(
                    revision = revision,
                    showMeasurementContext = showMeasurementContext,
                )
            }
        },
    )
}

@Composable
internal fun GlucoseProgrammingDetails(
    revision: com.bitavida.app.domain.model.GlucoseFollowUpRevision,
    showMeasurementContext: Boolean = true,
) {
    Text(
        text = revision.indication,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    revision.slots.forEachIndexed { index, slot ->
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                BitavidaSpacing.Small,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.bitavidaColors.paleBlue,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = slot.indicationScheduleLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showMeasurementContext) {
                    Text(
                        text = slot.suggestedContext.todayLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Aviso · " +
                        requireNotNull(slot.reminderTime).format(
                            DateTimeFormatter.ofPattern(
                                "HH:mm",
                                measurementLocale,
                            ),
                        ) +
                        if (slot.reminderNextDay) {
                            " · día siguiente"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun com.bitavida.app.domain.model.GlucoseFollowUpSlot
    .indicationScheduleLabel(): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", measurementLocale)
    val start = time.format(formatter)
    val end = windowEnd ?: return start
    return start + " – " + end.format(formatter) +
        if (endsNextDay) " · día siguiente" else ""
}

@Composable
internal fun GlucoseManagement(
    presentation: GlucoseDetailPresentation,
    onAction: ((MeasurementKind, MeasurementFollowUpAction) -> Unit)?,
    onHelp: (() -> Unit)?,
    measurementKind: MeasurementKind = MeasurementKind.CAPILLARY_GLUCOSE,
    testTag: String = "glucose_management",
    extraActionIcon: Int? = null,
    extraActionLabel: String? = null,
    onExtraAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            presentation.managementActions.forEachIndexed { index, action ->
                if (
                    (
                        action ==
                            MeasurementFollowUpAction.VIEW_CHANGES
                        ) &&
                    (extraActionIcon != null) &&
                    (extraActionLabel != null)
                ) {
                    if (index > 0) GlucoseActionDivider()
                    MeasurementManagementRow(
                        extraActionIcon,
                        extraActionLabel,
                        onExtraAction != null,
                    ) {
                        onExtraAction?.invoke()
                    }
                    GlucoseActionDivider()
                } else if (index > 0) {
                    GlucoseActionDivider()
                }
                val (icon, label) = when (action) {
                    MeasurementFollowUpAction.CONFIGURE -> R.drawable.ic_v2_clock to "Configurar seguimiento"
                    MeasurementFollowUpAction.CORRECT -> R.drawable.ic_v2_edit to "Corregir datos"
                    MeasurementFollowUpAction.CHANGE_INDICATIONS -> R.drawable.ic_v2_stethoscope to "Cambiar indicación médica"
                    MeasurementFollowUpAction.VIEW_CHANGES -> R.drawable.ic_v2_history to "Historial de cambios"
                    MeasurementFollowUpAction.SUSPEND -> R.drawable.ic_v2_pause to "Desactivar seguimiento"
                }
                MeasurementManagementRow(icon, label, onAction != null) {
                    onAction?.invoke(measurementKind, action)
                }
            }
            if (presentation.showHelp) {
                GlucoseActionDivider()
                MeasurementManagementRow(R.drawable.ic_v2_file, "Guías y ayuda", onHelp != null) { onHelp?.invoke() }
            }
        }
    }
    if (onAction == null) SecondaryText("La configuración y edición del seguimiento aún no están disponibles.")
    if (presentation.showSuspend) {
        MeasurementButton(
            label = "Desactivar seguimiento",
            onClick = { onAction?.invoke(measurementKind, MeasurementFollowUpAction.SUSPEND) },
            modifier = Modifier.fillMaxWidth(),
            style = MeasurementButtonStyle.OUTLINED,
            enabled = onAction != null,
            icon = R.drawable.ic_v2_pause,
        )
    }
}

@Composable
private fun GlucoseActionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = BitavidaSpacing.Medium),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    )
}
