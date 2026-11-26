package com.bitavida.app.ui.observations

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.projectObservationOccurrences
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.CurrentIndicationCardFrame
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.TodayRowFrame
import com.bitavida.app.ui.medical.TodayStatusIcon
import com.bitavida.app.ui.medical.TodayStatusVisual
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val detailLocale = Locale.forLanguageTag("es-MX")
private val detailDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(detailLocale)
private val detailTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(detailLocale)

/** The detail uses the same factual, daily and indication surfaces as the approved measurement details. */
@Composable
internal fun ObservationDetailContent(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    onScheduled: (ObservationOccurrence) -> Unit,
    now: LocalDateTime = LocalDateTime.now(),
) {
    val id = state.selectedObservationId ?: return
    val definition = ObservationCatalog.get(id)
    val storedFollowUp = state.snapshot.followUps.firstOrNull { it.observationId == id }
    val activeFollowUp = storedFollowUp?.takeIf { it.schedule.currentRevision.active }
    val latest = latestObservationRecord(id, state.snapshot.records)
    ObservationDetailIdentity(definition, observationFollowUpLabel(activeFollowUp, now.toLocalDate()))
    MeasurementPanel(
        color = MaterialTheme.bitavidaColors.paleBlue,
        modifier = Modifier.testTag("observation_latest").animateContentSize(
            tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
        ),
    ) {
        SectionTitle("Última observación", small = true)
        if (latest == null) {
            Text("Aún sin registros", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
            SecondaryText("El primer registro conservará lo que notaste, sin necesidad de configurar un seguimiento.")
        } else {
            Text(latest.result.label(), style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
            SecondaryText(latest.occurredOn.format(detailDate) + " · " + observationTimeLabel(latest))
            val summary = observationSummary(latest)
            if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (latest.localPhotoIds.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeasurementIcon(R.drawable.ic_observation_photo, 16)
                    Text(
                        "Fotografía adjunta",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        MeasurementButton(if (latest == null) "Registrar primera observación" else "Registrar observación",
            { onAction(ObservationsAction.Register(id)) }, Modifier.fillMaxWidth(),
            enabled = !state.saving, icon = R.drawable.ic_v2_plus)
    }
    if (activeFollowUp != null) {
        ObservationTodayRegister(state, activeFollowUp, now, motionAllowed, onAction, onScheduled)
    }
    activeFollowUp?.schedule?.currentRevision?.takeIf {
        observationIndicationIsCurrent(it, now.toLocalDate())
    }?.let { revision ->
        ObservationCurrentIndication(
            revision = revision,
            expansionKey = activeFollowUp.schedule.id + ":" + revision.id,
            motionAllowed = motionAllowed,
        )
    }
    ObservationManagement(definition.id, activeFollowUp, state.saving, motionAllowed, onAction)
}

/** Shares the medication card and its numbered programming pattern without adapting clinical data. */
@Composable
internal fun ObservationCurrentIndication(
    revision: GlucoseFollowUpRevision,
    expansionKey: String,
    motionAllowed: Boolean,
) {
    CurrentIndicationCardFrame(
        expansionKey = expansionKey,
        title = "Indicación vigente",
        summary = observationFrequencyLabel(revision) + " · " + revision.slots.size +
            if (revision.slots.size == 1) " revisión" else " revisiones",
        reminderLabel = if (revision.remindersEnabled) "Avisos del celular configurados" else "Avisos del celular desactivados",
        motionAllowed = motionAllowed,
        modifier = Modifier.testTag("observation_current_indication"),
        detailContent = { ObservationProgrammingDetails(revision) },
    )
}

@Composable
internal fun ObservationProgrammingDetails(revision: GlucoseFollowUpRevision) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(BitavidaSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            MeasurementIcon(R.drawable.ic_v2_file, 20)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text("Indicación médica transcrita", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(revision.indication, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    // Same numbered row as GlucoseProgrammingDetails; disabled alerts do not require a reminder time.
    revision.slots.sortedBy { it.time }.forEachIndexed { index, slot ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.bitavidaColors.paleBlue,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(observationSlotLabel(slot), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(observationReminderLabel(revision, slot), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ObservationDetailIdentity(definition: ObservationDefinition, status: String? = null) {
    val identity: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
            Text(definition.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
            SecondaryText(definition.description)
            status?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            BitavidaIconContainer(painterResource(observationIcon(definition.category)), null,
                containerColor = MaterialTheme.bitavidaColors.paleBlue)
            identity()
        }
    } else Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.Top) {
        BitavidaIconContainer(painterResource(observationIcon(definition.category)), null,
            containerColor = MaterialTheme.bitavidaColors.paleBlue)
        Box(Modifier.weight(1f)) { identity() }
    }
}

@Composable
private fun ObservationTodayRegister(
    state: ObservationsUiState,
    followUp: ObservationFollowUp,
    now: LocalDateTime,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    onScheduled: (ObservationOccurrence) -> Unit,
) {
    val occurrences = projectObservationOccurrences(now.toLocalDate(), listOf(followUp), state.snapshot.records, now)
    var expandedId by rememberSaveable(followUp.schedule.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(occurrences) {
        if (expandedId == null || occurrences.none { it.id == expandedId }) {
            expandedId = occurrences.firstOrNull {
                it.record == null && it.state == ObservationOccurrenceState.UNCONFIRMED
            }?.id ?: occurrences.firstOrNull()?.id
        }
    }
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    SectionTitle("Registro de hoy")
    Surface(Modifier.fillMaxWidth().testTag("observation_today"), shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(6.dp)) {
            if (occurrences.isEmpty()) Text("Sin revisiones programadas para hoy.",
                Modifier.padding(BitavidaSpacing.Medium), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            occurrences.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
                val record = event.record
                val notPerformed = record?.result == ObservationResult.NOT_PERFORMED
                val status = when {
                    notPerformed -> "No realizada"
                    record != null -> "Registrada"
                    event.state == ObservationOccurrenceState.UPCOMING -> "Revisión programada"
                    else -> "Sin confirmar"
                }
                TodayRowFrame(
                    expanded = expandedId == event.id,
                    duration = duration,
                    onSelect = { expandedId = event.id.takeUnless { expandedId == it } },
                    actions = {
                        if (record == null) {
                            CurrentDetailButton(DetailButtonKind.FILLED, { onScheduled(event) },
                                modifier = Modifier.fillMaxWidth(), enabled = !state.saving) { Text("Registrar revisión") }
                            CurrentDetailButton(DetailButtonKind.OUTLINED,
                                { onAction(ObservationsAction.NotPerformed(event)) },
                                modifier = Modifier.fillMaxWidth(), enabled = !state.saving, strongOutline = true) {
                                Text("No se realizó")
                            }
                        } else {
                            if (notPerformed) SecondaryText("Esta declaración no indica ausencia de cambios.")
                            CurrentDetailButton(DetailButtonKind.OUTLINED,
                                { onAction(ObservationsAction.CorrectRecord(record.id)) },
                                modifier = Modifier.fillMaxWidth(), enabled = !state.saving) {
                                Text(if (notPerformed) "Corregir declaración" else "Corregir observación")
                            }
                            CurrentDetailButton(DetailButtonKind.TEXT,
                                { onAction(ObservationsAction.ViewRecord(record.id)) },
                                modifier = Modifier.fillMaxWidth()) { Text("Consultar registro") }
                        }
                    },
                ) {
                    val large = LocalDensity.current.fontScale >= 1.5f
                    val heading: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                            verticalAlignment = Alignment.Top) {
                            TodayStatusIcon(when {
                                notPerformed -> TodayStatusVisual.NEGATIVE
                                record != null -> TodayStatusVisual.COMPLETE
                                event.state == ObservationOccurrenceState.UPCOMING -> TodayStatusVisual.FUTURE
                                else -> TodayStatusVisual.PENDING
                            })
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                                Text(status, style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(record?.let { if (notPerformed) "Confirmada por la persona cuidadora" else it.result.label() }
                                    ?: "El resultado se confirma al registrar.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (record != null && !notPerformed) SecondaryText("Hora real: " + observationTimeLabel(record))
                            }
                        }
                    }
                    if (large) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                        Text(observationOccurrenceTime(event), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        heading()
                    } else {
                        Text(observationOccurrenceTime(event), Modifier.weight(.36f),
                            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Box(Modifier.weight(.64f)) { heading() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObservationManagement(id: String, followUp: ObservationFollowUp?, saving: Boolean,
    motionAllowed: Boolean, onAction: (ObservationsAction) -> Unit) {
    SectionTitle("Seguimiento y consultas", small = true)
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column {
            if (followUp == null) {
                ObservationManagementAction(R.drawable.ic_v2_clock, "Configurar seguimiento", !saving) {
                    onAction(ObservationsAction.Configure(id))
                }
            } else {
                ObservationManagementAction(R.drawable.ic_v2_edit, "Corregir datos", !saving) {
                    onAction(ObservationsAction.Configure(id, startStep = 1))
                }
                ActionDivider()
                ObservationManagementAction(R.drawable.ic_v2_stethoscope, "Cambiar indicación médica", !saving) {
                    onAction(ObservationsAction.Configure(id, indicationChange = true))
                }
            }
            ActionDivider()
            ObservationManagementAction(R.drawable.ic_v2_history, "Historial de cambios", !saving) {
                onAction(ObservationsAction.Changes(id))
            }
            ActionDivider()
            ObservationManagementAction(R.drawable.ic_v2_calendar, "Historial de observaciones", !saving) {
                onAction(ObservationsAction.History)
            }
        }
    }
    if (followUp != null) {
        CurrentDetailButton(
            kind = DetailButtonKind.OUTLINED,
            onClick = { onAction(ObservationsAction.RequestSuspend(id)) },
            modifier = Modifier.fillMaxWidth().testTag("observation_delete_follow_up"),
            enabled = !saving,
            destructive = true,
            motionAllowedOverride = motionAllowed,
        ) {
            MeasurementIcon(R.drawable.ic_medication_delete, 20, LocalContentColor.current)
            Spacer(Modifier.width(BitavidaSpacing.Small))
            Text("Eliminar seguimiento")
        }
    }
}

/** Use the approved button at large font sizes so long action names keep their full word width. */
@Composable
private fun ObservationManagementAction(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    if (LocalDensity.current.fontScale >= 1.5f) {
        MeasurementButton(label, onClick, Modifier.fillMaxWidth(), MeasurementButtonStyle.TEXT,
            icon = icon, enabled = enabled)
    } else {
        MeasurementManagementRow(icon, label, enabled, onClick = onClick)
    }
}

@Composable
internal fun ObservationRecordDetailContent(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    photoContent: @Composable (String) -> Unit = {},
) {
    val record = state.snapshot.records.firstOrNull { it.id == state.selectedRecordId }
    if (record == null) {
        InformationCard("Registro no disponible", "No se pudo encontrar esta observación. Vuelve al historial o reintenta la carga.")
        return
    }
    val definition = ObservationCatalog.get(record.observationId)
    ObservationDetailIdentity(definition)
    MeasurementPanel(modifier = Modifier.testTag("observation_record_detail")
        .animateContentSize(tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0))) {
        SectionTitle(record.result.label(), small = true)
        ObservationRecordFacts(record, photoContent)
        MeasurementButton(if (record.result == ObservationResult.NOT_PERFORMED) "Corregir declaración" else "Corregir observación",
            { onAction(ObservationsAction.CorrectRecord(record.id)) }, Modifier.fillMaxWidth(),
            style = MeasurementButtonStyle.OUTLINED, enabled = !state.saving, icon = R.drawable.ic_v2_edit)
        SecondaryText("Toda corrección conserva el registro anterior y solicita un motivo.")
    }
    SectionTitle("Historial de correcciones")
    val revisions = state.snapshot.recordRevisions.filter { it.recordId == record.id }.sortedBy { it.revisionNumber }
    if (revisions.none { it.revisionNumber > 1 }) {
        InformationCard("Sin correcciones", "Este registro conserva su declaración original.")
    } else revisions.forEach { revision ->
        MeasurementPanel {
            SectionTitle(if (revision.revisionNumber == 1) "Registro original" else "Corrección " + (revision.revisionNumber - 1), small = true)
            Text(revision.snapshot.result.label(), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            SecondaryText("Captura: " + revision.correctedAt.toLocalDate().format(detailDate) + " · " +
                revision.correctedAt.toLocalTime().format(detailTime))
            revision.reason?.takeIf(String::isNotBlank)?.let { SecondaryText("Motivo: " + it) }
            ObservationRecordFacts(revision.snapshot, photoContent)
        }
    }
}

@Composable
private fun ObservationRecordFacts(record: ObservationRecord, photoContent: @Composable (String) -> Unit) {
    val definition = ObservationCatalog.get(record.observationId)
    SecondaryText("Fecha real: " + record.occurredOn.format(detailDate) + " · " + observationTimeLabel(record))
    SecondaryText(if (record.occurrenceId == null) "Observación espontánea" else "Revisión programada")
    record.scheduledAt?.let {
        SecondaryText("Fecha programada: " + it.toLocalDate().format(detailDate) + " · " + it.toLocalTime().format(detailTime))
    }
    definition.fields.forEach { field ->
        val values = record.answers[field.id].orEmpty().filter(String::isNotBlank)
        if (values.isNotEmpty()) {
            SectionTitle(field.label, small = true)
            Text(values.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (record.description.isNotBlank()) {
        SectionTitle("Descripción de la persona cuidadora", small = true)
        Text(record.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    record.localPhotoIds.forEach { photoContent(it) }
    SecondaryText("Registrada el " + record.createdAt.toLocalDate().format(detailDate) + " · " +
        record.createdAt.toLocalTime().format(detailTime))
}

internal fun latestObservationRecord(id: String, records: List<ObservationRecord>): ObservationRecord? =
    records.filter { it.observationId == id && it.result != ObservationResult.NOT_PERFORMED }
        .maxWithOrNull(compareBy<ObservationRecord> { it.occurredOn }
            .thenBy { it.occurredAt ?: LocalTime.MIN }.thenBy { it.createdAt }.thenBy { it.id })

internal fun observationSummary(record: ObservationRecord): String = record.description.takeIf(String::isNotBlank)
    ?: ObservationCatalog.get(record.observationId).fields.flatMap { record.answers[it.id].orEmpty() }
        .filter(String::isNotBlank).joinToString(" · ")

internal fun observationTimeLabel(record: ObservationRecord): String = when (record.timePrecision) {
    ObservationTimePrecision.UNKNOWN -> "Hora desconocida"
    ObservationTimePrecision.APPROXIMATE -> "Aproximadamente " + record.occurredAt?.format(detailTime).orEmpty()
    ObservationTimePrecision.EXACT -> record.occurredAt?.format(detailTime).orEmpty()
}

internal fun observationIcon(category: ObservationCategory): Int = when (category) {
    ObservationCategory.FEET -> R.drawable.ic_v2_foot
    ObservationCategory.DIGESTION -> R.drawable.ic_v2_droplet
    ObservationCategory.MOBILITY -> R.drawable.ic_v2_foot
    ObservationCategory.OTHER -> R.drawable.ic_v2_file
    ObservationCategory.WELLBEING -> R.drawable.ic_v2_clipboard
    ObservationCategory.VISION, ObservationCategory.PHYSICAL, ObservationCategory.GENERAL -> R.drawable.ic_v2_stethoscope
}

internal fun observationFollowUpLabel(followUp: ObservationFollowUp?, today: LocalDate): String {
    val revision = followUp?.schedule?.currentRevision ?: return "Sin seguimiento programado"
    return when {
        !revision.active -> "Sin seguimiento programado"
        !observationIndicationIsCurrent(revision, today) -> "Sin seguimiento activo"
        else -> "Seguimiento activo"
    }
}

/** Existing temporal limits remain effective for legacy revisions without exposing a validity editor. */
internal fun observationIndicationIsCurrent(revision: GlucoseFollowUpRevision, today: LocalDate): Boolean =
    revision.active && revision.startsOn <= today && revision.endsOn?.isBefore(today) != true

private fun observationFrequencyLabel(revision: GlucoseFollowUpRevision): String = when (revision.frequency) {
    GlucoseFollowUpFrequency.DAILY -> "Todos los días"
    GlucoseFollowUpFrequency.SPECIFIC_DAYS -> revision.weekdays.sortedBy { it.value }
        .joinToString { it.getDisplayName(java.time.format.TextStyle.SHORT, detailLocale) }
    GlucoseFollowUpFrequency.EVERY_N_DAYS -> "Cada " + revision.everyNDaysInterval + " días"
}

internal fun observationReminderLabel(revision: GlucoseFollowUpRevision, slot: GlucoseFollowUpSlot): String =
    if (!revision.remindersEnabled || !slot.reminderEnabled) "Sin aviso para este horario"
    else slot.reminderTime?.let {
        "Aviso · " + it.format(detailTime) + if (slot.reminderNextDay) " · día siguiente" else ""
    } ?: "Sin hora de aviso"

private fun observationSlotLabel(slot: GlucoseFollowUpSlot): String = slot.time.format(detailTime) +
    (slot.windowEnd?.let { " – " + it.format(detailTime) + if (slot.endsNextDay) " · día siguiente" else "" } ?: "")

private fun observationOccurrenceTime(event: ObservationOccurrence): String = event.scheduledAt.toLocalTime().format(detailTime) +
    (event.windowEndAt?.let { " – " + it.toLocalTime().format(detailTime) + if (it.toLocalDate() > event.scheduledAt.toLocalDate()) " (+1 día)" else "" } ?: "")
