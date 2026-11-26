package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.repository.CareEventActor
import com.bitavida.app.domain.repository.CareEventRepository
import java.time.LocalDate

/**
 * A read-only, patient-scoped set of current events and their complete recorded revisions.
 * No attachment contents are loaded. This object is intended to live only in memory.
 */
data class CareEventReportHistory(
    val events: List<CareEvent>,
    val revisionsByEvent: Map<String, List<CareEvent>>,
)

class CareEventHistoryChangedException : Exception("No se pudo obtener una instantánea coherente del historial.")

/**
 * Existing repository operations are transactional individually. Read current versions
 * before and after histories, rejecting any concurrent change or incomplete history.
 * Revision numbers are monotonic, so equal snapshots on both reads establish that none
 * of these event identities changed during the interval in which histories were read.
 * Repository authorization/storage errors propagate; an incomplete snapshot is never
 * silently substituted with current values.
 */
suspend fun readCareEventReportHistory(
    repository: CareEventRepository,
    actor: CareEventActor,
    maxAttempts: Int = 3,
): CareEventReportHistory {
    require(maxAttempts > 0)
    repeat(maxAttempts) {
        val before = repository.load(actor).sortedBy { event -> event.id }
        if (before.map { event -> event.id }.distinct().size != before.size ||
            before.any { event -> event.personUnderCareId != actor.personUnderCareId }) {
            throw CareEventHistoryChangedException()
        }
        val histories = before.associate { event -> event.id to repository.history(actor, event.id) }
        val after = repository.load(actor).sortedBy { event -> event.id }
        val complete = before == after && before.all { event ->
            val versions = histories[event.id].orEmpty()
            versions.size == event.revision && versions.lastOrNull() == event &&
                versions.withIndex().all { (index, version) ->
                    version.id == event.id && version.personUnderCareId == actor.personUnderCareId &&
                        version.revision == index + 1
                }
        }
        if (complete) return CareEventReportHistory(before, histories)
    }
    throw CareEventHistoryChangedException()
}

/** Latest recorded version known at the end of a period, if the event already existed. */
fun careEventVersionAtEnd(versions: List<CareEvent>, end: LocalDate): CareEvent? =
    versions.asSequence().filter { !it.modifiedAt.toLocalDate().isAfter(end) }
        .maxByOrNull { it.revision }

/** A documented change of a known scheduled date/time, not a second consultation. */
data class CareEventRescheduling(
    val previous: CareEvent,
    val revised: CareEvent,
)

/**
 * Compare successive versions of the same appointment. Merely adding a previously
 * unknown hour on the same day is not presented as a rescheduling. The timestamp is
 * the recorded change time, not a claim about when the consultation occurred.
 */
fun careEventReschedulings(
    versions: List<CareEvent>,
    start: LocalDate,
    end: LocalDate,
): List<CareEventRescheduling> {
    require(!end.isBefore(start))
    fun date(event: CareEvent) = event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate
    return versions.sortedBy { it.revision }.zipWithNext().mapNotNull { (previous, revised) ->
        val beforeDate = date(previous)
        val afterDate = date(revised)
        val changed = beforeDate != null && afterDate != null &&
            (beforeDate != afterDate ||
                (previous.draft.scheduledAt != null && revised.draft.scheduledAt != null &&
                    previous.draft.scheduledAt != revised.draft.scheduledAt))
        if (previous.id == revised.id && previous.personUnderCareId == revised.personUnderCareId &&
            previous.draft.kind == CareEventKind.APPOINTMENT && revised.draft.kind == CareEventKind.APPOINTMENT &&
            revised.modifiedAt.toLocalDate() in start..end && changed) {
            CareEventRescheduling(previous, revised)
        } else null
    }
}
