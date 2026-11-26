package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ObservationFollowUp
import java.time.LocalDateTime

/** Display state of a configured follow-up, independent of its scheduled occurrences. */
enum class ObservationFollowUpDisplayStatus { ACTIVE, UPCOMING }

/**
 * Returns one status per distinct observation belonging to the person.
 * Archived schedules and revisions not yet captured are excluded. A future revision
 * does not hide an already active indication; an explicitly removed schedule is hidden.
 */
fun observationFollowUpStatuses(
    followUps: List<ObservationFollowUp>,
    personUnderCareId: String,
    now: LocalDateTime,
): Map<String, ObservationFollowUpDisplayStatus> {
    val today = now.toLocalDate()
    val statuses = mutableMapOf<String, ObservationFollowUpDisplayStatus>()
    followUps.asSequence()
        .filter { it.schedule.personUnderCareId == personUnderCareId }
        .forEach { followUp ->
            val revisions = followUp.schedule.revisions.filter { !it.capturedAt.isAfter(now) }
            if (revisions.isEmpty() || !revisions.last().active) return@forEach

            val current = revisions.lastOrNull { !it.effectiveFrom.isAfter(today) }
            val active = current != null && current.active &&
                !current.startsOn.isAfter(today) && (current.endsOn == null || !current.endsOn.isBefore(today))
            val upcoming = revisions.any { revision ->
                revision.active && revision.effectiveFrom.isAfter(today) &&
                    revision.revisionNumber > (current?.revisionNumber ?: 0) &&
                    (revision.endsOn == null || !revision.endsOn.isBefore(revision.effectiveFrom))
            }
            val status = when {
                active -> ObservationFollowUpDisplayStatus.ACTIVE
                upcoming -> ObservationFollowUpDisplayStatus.UPCOMING
                else -> null
            }
            if (status != null &&
                (statuses[followUp.observationId] != ObservationFollowUpDisplayStatus.ACTIVE)) {
                statuses[followUp.observationId] = status
            }
        }
    return statuses
}
