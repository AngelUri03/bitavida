package com.bitavida.app.ui.report

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import com.bitavida.app.R
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.careEventReschedulings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** In-memory only: expansion never modifies a clinical or administrative record. */
@Stable
internal class ReportObservationsState {
    val categories = mutableStateMapOf<ObservationCategory, Boolean>()
    val photos = mutableStateMapOf<String, Boolean>()
    val entries = mutableStateMapOf<String, Boolean>()
    var scheduled by mutableStateOf(false)
    var showAllScheduled by mutableStateOf(false)
}

@Stable
internal class ReportEventsState {
    var category by mutableStateOf(ReportEventCategory.ALL)
    var showAll by mutableStateOf(false)
    var changesExpanded by mutableStateOf(false)
    var showAllChanges by mutableStateOf(false)
    val entries = mutableStateMapOf<String, Boolean>()
}

private val reportLocale = Locale.forLanguageTag("es-MX")
private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", reportLocale)
private val shortTime = DateTimeFormatter.ofPattern("HH:mm", reportLocale)
private fun LocalDate.inReport(report: FollowUpReport) = this >= report.start && this <= report.end

internal fun LazyListScope.reportObservationItems(
    report: FollowUpReport,
    state: ReportObservationsState,
    photoContent: (@Composable (String) -> Unit)? = null,
) {
    val records = report.observations.sortedWith(compareByDescending<ObservationRecord> { it.occurredOn }
        .thenByDescending { it.occurredAt })
    val pending = report.rows.filter { it.section == "Observaciones programadas" }
        .sortedByDescending { it.occurredAt }
    item("obs_summary") {
        ReportDetailBlock {
            Text("Resumen de observaciones", style = MaterialTheme.typography.titleMedium)
            Text("Información declarada por la persona cuidadora.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val indicators = listOf(
                "Con cambios" to records.count { it.result == ObservationResult.CHANGES },
                "Sin cambios" to records.count { it.result == ObservationResult.NO_CHANGES },
                "No realizadas" to records.count { it.result == ObservationResult.NOT_PERFORMED },
                "Sin confirmar" to pending.count { it.detail == "Sin confirmar" },
            )
            indicators.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (label, count) ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(12.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                                Text(count.toString(), style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            val otherEvents = records.count { it.result == ObservationResult.EVENT }
            if (otherEvents > 0) Text("$otherEvents acontecimientos declarados",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val definitions = ObservationCatalog.all.associateBy { it.id }
    ObservationCategory.entries.forEach { category ->
        val group = records.filter { (definitions[it.observationId]?.category ?: ObservationCategory.OTHER) == category }
        if (group.isNotEmpty()) {
            item("obs_category_${category.name}") {
                ReportExpansionHeader(category.title, "${group.size} registros",
                    state.categories[category] == true, icon = observationCategoryIcon(category)) {
                    state.categories[category] = state.categories[category] != true
                }
            }
            if (state.categories[category] == true) {
                items(group, key = { "obs_record_${it.id}" }) { record ->
                    val definition = definitions[record.observationId]
                    val expanded = state.entries[record.id] == true
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(onClick = { state.entries[record.id] = !expanded },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
                            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(definition?.name ?: "Observación registrada", style = MaterialTheme.typography.titleSmall)
                                    Text(observationDate(record), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(observationStatus(record.result), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary)
                                    record.description.takeIf(String::isNotBlank)?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = if (expanded) Int.MAX_VALUE else 3)
                                    }
                                    if (record.localPhotoIds.isNotEmpty()) Text("Fotografía adjunta",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (expanded) {
                                        record.answers.forEach { (fieldId, values) ->
                                            val label = definition?.fields?.firstOrNull { it.id == fieldId }?.label
                                                ?: "Dato adicional"
                                            val additional = values.filter(String::isNotBlank).distinct().filterNot {
                                                record.description.contains(it, ignoreCase = true)
                                            }
                                            if (additional.isNotEmpty()) Text("$label: ${additional.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                ReportChevron(expanded)
                            }
                        }
                        if (expanded && record.localPhotoIds.isNotEmpty() && photoContent != null) {
                            OutlinedButton(onClick = { state.photos[record.id] = state.photos[record.id] != true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                                Text(if (state.photos[record.id] == true) "Ocultar fotografías" else "Ver fotografías")
                            }
                            if (state.photos[record.id] == true) {
                                record.localPhotoIds.forEach { photoId -> photoContent(photoId) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (records.isEmpty() && pending.isEmpty()) item("obs_empty") {
        Text("No hay observaciones para este periodo.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (pending.isNotEmpty()) {
        item("obs_pending_header") {
            ReportExpansionHeader("Revisiones programadas y pendientes", "${pending.size} revisiones", state.scheduled) {
                state.scheduled = !state.scheduled
            }
        }
        if (state.scheduled) {
            val shown = if (state.showAllScheduled) pending else pending.take(3)
            items(shown.size, key = { index -> "obs_pending_$index" }) { index ->
                val row = shown[index]
                ReportDetailBlock {
                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                    Text("${row.occurredAt.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", reportLocale))} · ${row.detail}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (pending.size > 3) item("obs_pending_more") {
                OutlinedButton(onClick = { state.showAllScheduled = !state.showAllScheduled },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                    Text(if (state.showAllScheduled) "Mostrar menos" else "Ver todas las revisiones")
                }
            }
        }
    }
}

private fun observationDate(record: ObservationRecord): String = buildString {
    append(record.occurredOn.format(shortDate))
    record.occurredAt?.let {
        append(" · ").append(it.format(shortTime))
        if (record.timePrecision == ObservationTimePrecision.APPROXIMATE) append(" (aproximada)")
    }
}

private fun observationStatus(result: ObservationResult) = when (result) {
    ObservationResult.CHANGES -> "Cambios declarados"
    ObservationResult.NO_CHANGES -> "Sin cambios declarados"
    ObservationResult.NOT_PERFORMED -> "No se realizó"
    ObservationResult.EVENT -> "Acontecimiento declarado"
}

internal enum class ReportEventCategory(val label: String) {
    ALL("Todas"), CONSULTATIONS("Consultas"), STUDIES("Estudios"),
    PROCEDURES("Procedimientos"), OTHER("Otras")
}

/** Uses only the saved classification; ambiguous legacy consultations remain unspecified. */
internal fun reportEventCategory(draft: CareEventDraft): ReportEventCategory = when (draft.purpose) {
    CareEventPurpose.MEDICAL_CONSULTATION -> ReportEventCategory.CONSULTATIONS
    CareEventPurpose.STUDY -> ReportEventCategory.STUDIES
    CareEventPurpose.PROCEDURE -> ReportEventCategory.PROCEDURES
    CareEventPurpose.ADMINISTRATIVE, CareEventPurpose.OTHER -> ReportEventCategory.OTHER
    CareEventPurpose.LEGACY -> when {
        draft.kind == CareEventKind.STUDY ||
            draft.category in setOf(CareEventCategory.LABORATORY, CareEventCategory.IMAGING) -> ReportEventCategory.STUDIES
        draft.category == CareEventCategory.ADMINISTRATIVE -> ReportEventCategory.OTHER
        else -> ReportEventCategory.CONSULTATIONS
    }
}

private fun reportEventDate(event: CareEvent): LocalDate =
    event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate
        ?: event.draft.suggestedDate ?: event.createdAt.toLocalDate()

private fun eventDateLabel(event: CareEvent): String {
    val draft = event.draft
    return when {
        draft.scheduledAt != null -> "Fecha programada: ${draft.scheduledAt.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", reportLocale))}"
        draft.scheduledDate != null -> "Fecha programada: ${draft.scheduledDate.format(shortDate)} · Hora no indicada"
        draft.suggestedDate != null -> "Fecha sugerida: ${draft.suggestedDate.format(shortDate)} · Sin cita confirmada"
        else -> "Sin fecha confirmada · Registrada el ${event.createdAt.toLocalDate().format(shortDate)}"
    }
}

internal fun reportCareEventsForPeriod(
    report: FollowUpReport,
    events: List<CareEvent>,
    personUnderCareId: String,
    now: LocalDateTime,
): List<CareEvent> = events.asSequence()
    .filter { it.personUnderCareId == personUnderCareId }.distinctBy { it.id }
    .filter { event ->
        val date = event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate
            ?: event.draft.suggestedDate ?: event.createdAt.toLocalDate()
        val performed = event.draft.status in setOf(CareEventStatus.COMPLETED, CareEventStatus.PERFORMED,
            CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)
        date.inReport(report) && (!performed || !(event.draft.scheduledAt ?: date.atStartOfDay()).isAfter(now))
    }.sortedBy { it.draft.scheduledAt?.toLocalDate() ?: it.draft.scheduledDate
        ?: it.draft.suggestedDate ?: it.createdAt.toLocalDate() }.toList()

internal fun LazyListScope.reportCareEventItems(
    report: FollowUpReport,
    events: List<CareEvent>,
    personUnderCareId: String,
    state: ReportEventsState,
    revisionsByEvent: Map<String, List<CareEvent>>,
) {
    val filtered = reportCareEventsForPeriod(report, events, personUnderCareId, LocalDateTime.now())
    item("events_summary") {
        ReportDetailBlock {
            Text("Consultas, estudios y procedimientos del periodo", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(ReportEventCategory.CONSULTATIONS, ReportEventCategory.STUDIES,
                ReportEventCategory.PROCEDURES, ReportEventCategory.OTHER).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { category ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(12.dp)) {
                                Text(category.label, style = MaterialTheme.typography.labelMedium)
                                Text(filtered.count { reportEventCategory(it.draft) == category }.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (filtered.isNotEmpty()) {
                Text(filtered.groupingBy { eventStatus(it.draft.status) }.eachCount()
                    .entries.joinToString(" · ") { (status, count) -> "$status: $count" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item("events_filters") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportEventCategory.entries.filter { it != ReportEventCategory.OTHER ||
                filtered.any { event -> reportEventCategory(event.draft) == it } }.forEach { category ->
                EventFilter(category.label, state.category == category) {
                    state.category = category
                    state.showAll = false
                }
            }
        }
    }
    val selected = filtered.filter { state.category == ReportEventCategory.ALL ||
        reportEventCategory(it.draft) == state.category }
    if (selected.isEmpty()) item("events_empty") {
        Text("No hay acontecimientos de este tipo en el periodo.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val shown = if (state.showAll) selected else selected.take(3)
    shown.groupBy { reportEventDate(it) }.forEach { (date, group) ->
        item("care_date_$date") {
            Text(date.format(shortDate), Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(group, key = { "care_event_${it.id}" }) { event ->
            val draft = event.draft
            val expanded = state.entries[event.id] == true
            Surface(onClick = { state.entries[event.id] = !expanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top) {
                    Icon(painterResource(when (reportEventCategory(draft)) {
                        ReportEventCategory.CONSULTATIONS -> R.drawable.ic_v2_stethoscope
                        ReportEventCategory.STUDIES -> R.drawable.ic_v2_file
                        ReportEventCategory.PROCEDURES -> R.drawable.ic_v2_clipboard
                        else -> R.drawable.ic_v2_calendar
                    }), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(draft.title, style = MaterialTheme.typography.titleSmall)
                        val type = com.bitavida.app.ui.appointments.careEventTypeLabel(draft)
                        val specialty = draft.specialty.takeIf {
                            draft.consultationType == CareConsultationType.SPECIALIST && it.isNotBlank()
                        }
                        Text(listOfNotNull(type, specialty).joinToString(" · "),
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(eventDateLabel(event), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(eventStatus(draft.status), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        listOf(draft.institution, draft.location).filter(String::isNotBlank).distinct()
                            .takeIf { it.isNotEmpty() }?.let {
                                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        if (expanded) {
                            draft.office.takeIf(String::isNotBlank)?.let { Text("Consultorio: $it") }
                            draft.transcribedIndication.takeIf(String::isNotBlank)?.let {
                                Text("Indicación médica transcrita: $it", style = MaterialTheme.typography.bodyMedium)
                            }
                            draft.instructions.takeIf(String::isNotBlank)?.let {
                                Text("Indicaciones adicionales: $it", style = MaterialTheme.typography.bodyMedium)
                            }
                            draft.caregiverNotes.takeIf(String::isNotBlank)?.let {
                                Text("Notas: $it", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    ReportChevron(expanded)
                }
            }
        }
    }
    run {
        val changes = revisionsByEvent.values.asSequence()
            .flatMap { versions -> careEventReschedulings(versions, report.start, report.end).asSequence() }
            .filter { it.revised.personUnderCareId == personUnderCareId && !it.revised.modifiedAt.isAfter(LocalDateTime.now()) }
            .sortedByDescending { it.revised.modifiedAt }.toList()
        if (changes.isNotEmpty()) {
            item("care_reschedulings_header") {
                ReportExpansionHeader("Cambios de programación", "${changes.size} cambios registrados", state.changesExpanded) {
                    state.changesExpanded = !state.changesExpanded
                }
            }
            if (state.changesExpanded) {
                val visibleChanges = if (state.showAllChanges) changes else changes.take(3)
                items(visibleChanges, key = { "care_change_${it.revised.id}_${it.revised.revision}" }) { change ->
                    val before = eventSchedule(change.previous)
                    val after = eventSchedule(change.revised)
                    ReportDetailBlock {
                        Text(change.revised.draft.title, style = MaterialTheme.typography.titleSmall)
                        Text("Programación anterior: $before",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Nueva programación: $after",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Cambio registrado el ${change.revised.modifiedAt.toLocalDate().format(shortDate)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        change.revised.draft.changeReason.takeIf(String::isNotBlank)?.let {
                            Text("Motivo declarado: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (changes.size > 3) item("care_reschedulings_more") {
                    OutlinedButton(onClick = { state.showAllChanges = !state.showAllChanges },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                        Text(if (state.showAllChanges) "Mostrar menos" else "Ver todos los cambios")
                    }
                }
            }
        }
    }
    if (selected.size > 3) item("events_more") {
        OutlinedButton(onClick = { state.showAll = !state.showAll },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
            Text(if (state.showAll) "Mostrar menos" else "Ver todos los acontecimientos")
        }
    }
}

private fun eventSchedule(event: CareEvent): String =
    event.draft.scheduledAt?.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", reportLocale))
        ?: event.draft.scheduledDate?.format(shortDate) ?: "No registrada"

private fun eventStatus(status: CareEventStatus) = when (status) {
    CareEventStatus.TO_SCHEDULE -> "Por agendar"
    CareEventStatus.CONFIRMED -> "Confirmada"
    CareEventStatus.COMPLETED -> "Realizada"
    CareEventStatus.CANCELLED -> "Cancelada"
    CareEventStatus.REQUESTED -> "Solicitado"
    CareEventStatus.STUDY_TO_SCHEDULE -> "Pendiente de programación"
    CareEventStatus.SCHEDULED -> "Programado"
    CareEventStatus.PERFORMED -> "Realizado"
    CareEventStatus.RESULT_PENDING -> "Resultado pendiente"
    CareEventStatus.RESULT_AVAILABLE -> "Resultado disponible"
    CareEventStatus.REVIEWED -> "Revisado en consulta"
}

@Composable
private fun EventFilter(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun ReportDetailBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun ReportExpansionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    icon: Int? = null,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ReportChevron(expanded)
        }
    }
}

@Composable
private fun ReportChevron(expanded: Boolean) {
    val motion = rememberBitavidaMotionAllowed(null)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(if (motion) BitavidaFluidMotion.Selection else 0),
        label = "report_chevron",
    )
    Icon(painterResource(R.drawable.ic_v2_chevron), contentDescription = null,
        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.graphicsLayer { rotationZ = rotation })
}

private fun observationCategoryIcon(category: ObservationCategory): Int = when (category) {
    ObservationCategory.FEET -> R.drawable.ic_v2_foot
    ObservationCategory.PHYSICAL, ObservationCategory.GENERAL -> R.drawable.ic_v2_stethoscope
    ObservationCategory.DIGESTION -> R.drawable.ic_v2_droplet
    ObservationCategory.VISION, ObservationCategory.WELLBEING,
    ObservationCategory.MOBILITY, ObservationCategory.OTHER -> R.drawable.ic_v2_clipboard
}

