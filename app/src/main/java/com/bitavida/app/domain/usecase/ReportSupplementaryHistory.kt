package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.model.CareReminderStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Versions retained by each module. No image content or identifiers are exported. */
data class ReportNoteHistory(val current: Note, val revisions: List<Note>)
data class ReportReminderHistory(val current: CareReminder, val revisions: List<CareReminder>)

/**
 * Each module is read transactionally. This object does not by itself guarantee an
 * atomic database snapshot together with medication, measurement or appointment data.
 */
data class ReportSupplementaryHistory(
    val notes: List<ReportNoteHistory> = emptyList(),
    val reminders: List<ReportReminderHistory> = emptyList(),
)

/** Projection by persisted identity and capture time; missing history is disclosed, not invented. */
internal fun supplementaryReportRows(
    start: LocalDate,
    end: LocalDate,
    cutoff: LocalDateTime,
    personId: String,
    history: ReportSupplementaryHistory,
): List<FollowUpReportRow> = buildList {
    fun inPeriod(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("es-MX"))
    fun noteText(note: Note): String = buildList {
        note.content?.takeIf(String::isNotBlank)?.let(::add)
        if (note.imageId != null) add("Fotografía adjunta; no se incluye en el informe")
        if (isEmpty()) add("Sin contenido adicional registrado")
    }.joinToString(" · ")
    fun reminderText(reminder: CareReminder): String = buildList {
        add(when (reminder.status) {
            CareReminderStatus.PENDING -> "Pendiente"
            CareReminderStatus.DONE -> "Realizado"
            CareReminderStatus.DISCARDED -> "Descartado"
        })
        reminder.details?.takeIf(String::isNotBlank)?.let(::add)
        reminder.closedAt?.let { add("Cierre registrado: ${it.format(dateTime)}") }
    }.joinToString(" · ")

    history.notes.filter { it.current.personUnderCareId == personId }.distinctBy { it.current.id }.forEach { item ->
        val versions = item.revisions.filter { it.personUnderCareId == personId && it.id == item.current.id }
            .distinctBy { it.revision }.sortedBy { it.revision }
        val known = versions.filter { !it.modifiedAt.isAfter(cutoff) }
        val selected = known.lastOrNull()
            ?: item.current.takeIf { versions.isEmpty() && !it.modifiedAt.isAfter(cutoff) }
        if (selected == null) {
            if (!item.current.createdAt.isAfter(cutoff))
                add(FollowUpReportRow("Notas", maxOf(item.current.createdAt, start.atStartOfDay()), "Nota de cuidado",
                    "La versión histórica de esta nota no está disponible al corte.",
                    "Información declarada por el cuidador", sourceId = "note-unknown:${item.current.id}"))
        } else {
            if (selected.deletedAt == null &&
                (inPeriod(selected.createdAt.toLocalDate()) || inPeriod(selected.modifiedAt.toLocalDate()))) {
                val whenRelevant = if (inPeriod(selected.createdAt.toLocalDate())) selected.createdAt else selected.modifiedAt
                val detail = noteText(selected) +
                    if (versions.isEmpty()) " · Historial previo no disponible" else ""
                add(FollowUpReportRow("Notas", whenRelevant, selected.title, detail,
                    "Información declarada por el cuidador", sourceId = "note:${selected.id}",
                    revisionNumber = selected.revision))
            }
            known.zipWithNext().forEach { (previous, next) ->
                if (inPeriod(next.modifiedAt.toLocalDate())) {
                    val previousText = noteText(previous)
                    val nextText = if (next.deletedAt != null) "Nota eliminada" else noteText(next)
                    add(FollowUpReportRow("Auditoría y correcciones", next.modifiedAt, next.title,
                        "Nota anterior: $previousText · Registro posterior: $nextText",
                        "Cambio registrado por el cuidador", sourceId = "note-revision:${next.id}:${next.revision}",
                        revisionNumber = next.revision))
                }
            }
        }
    }
    history.reminders.filter { it.current.personUnderCareId == personId }.distinctBy { it.current.id }.forEach { item ->
        val versions = item.revisions.filter { it.personUnderCareId == personId && it.id == item.current.id }
            .distinctBy { it.revision }.sortedBy { it.revision }
        val known = versions.filter { !it.modifiedAt.isAfter(cutoff) }
        val selected = known.lastOrNull()
            ?: item.current.takeIf { versions.isEmpty() && !it.modifiedAt.isAfter(cutoff) }
        if (selected == null) {
            if (!item.current.createdAt.isAfter(cutoff) && inPeriod(item.current.originallyScheduledAt.toLocalDate()))
                add(FollowUpReportRow("Recordatorios", item.current.originallyScheduledAt, "Recordatorio de cuidado",
                    "La versión histórica de este recordatorio no está disponible al corte.",
                    "Información declarada por el cuidador", sourceId = "reminder-unknown:${item.current.id}"))
        } else {
            if (inPeriod(selected.scheduledAt.toLocalDate())) {
                val detail = reminderText(selected) +
                    if (versions.isEmpty()) " · Historial previo no disponible" else ""
                add(FollowUpReportRow("Recordatorios", selected.scheduledAt, selected.title,
                    detail, "Información declarada por el cuidador",
                    sourceId = "reminder:${selected.id}", revisionNumber = selected.revision))
            }
            known.zipWithNext().forEach { (previous, next) ->
                if (inPeriod(next.modifiedAt.toLocalDate()))
                    add(FollowUpReportRow("Auditoría y correcciones", next.modifiedAt, next.title,
                        "Programado anteriormente: ${previous.scheduledAt.format(dateTime)} · ${reminderText(previous)} · " +
                            "Registro posterior: ${next.scheduledAt.format(dateTime)} · ${reminderText(next)}",
                        "Cambio registrado por el cuidador", sourceId = "reminder-revision:${next.id}:${next.revision}",
                        revisionNumber = next.revision))
            }
        }
    }
}
