package com.bitavida.app.ui.demo

import com.bitavida.app.domain.model.ObservationOccurrence
import com.bitavida.app.domain.repository.NotificationHistoryItem
import java.security.MessageDigest

/** A candidate must originate from the authorized patient's current, read-only projection. */
internal data class NotificationEventCandidate(
    val personUnderCareId: String,
    val event: CareCalendarEvent,
)

/**
 * Resolve a protected notification receipt only by the exact token used by its scheduler.
 * A missing, stale or ambiguous identity is not a destination; titles are never used for matching.
 * Callers must obtain [candidates] from an authorized patient snapshot, not from an external intent.
 */
internal fun resolveNotificationTarget(
    item: NotificationHistoryItem,
    personUnderCareId: String,
    candidates: List<NotificationEventCandidate>,
): CareCalendarEvent? {
    val token = item.relatedToken?.takeIf { it.isNotBlank() } ?: return null
    if (personUnderCareId.isBlank()) return null
    val matches = candidates.asSequence()
        .filter { it.personUnderCareId == personUnderCareId }
        .mapNotNull { candidate ->
            val event = candidate.event
            val occurrenceId = when (val ref = event.reference) {
                is TodayMedicationItem -> if (event.kind == TodayActivityKind.MEDICATION) ref.id else null
                is TodayMeasurementItem -> if (event.kind == TodayActivityKind.MEASUREMENT) ref.occurrence.id else null
                is ObservationOccurrence -> if (event.kind == TodayActivityKind.OBSERVATION) ref.id else null
                is TodayResolvedActivity -> when {
                    ref.spontaneous || ref.kind != event.kind -> null
                    ref.kind == TodayActivityKind.MEDICATION -> ref.id
                    ref.kind == TodayActivityKind.OBSERVATION -> ref.id
                    ref.kind == TodayActivityKind.MEASUREMENT && ref.measurementKind != null -> {
                        val prefix = ref.measurementKind.name + ":"
                        ref.id.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotBlank() }
                    }
                    else -> null
                }
                else -> null
            } ?: return@mapNotNull null
            val expected = when {
                item.module == "MEDICATIONS" && event.kind == TodayActivityKind.MEDICATION ->
                    medicationNoticeToken(occurrenceId)
                item.module == "MEASUREMENTS" && event.kind == TodayActivityKind.MEASUREMENT -> occurrenceId
                item.module == "OBSERVATIONS" && event.kind == TodayActivityKind.OBSERVATION -> occurrenceId
                else -> return@mapNotNull null
            }
            if (expected == token) event else null
        }
        .distinct()
        .toList()
    return matches.singleOrNull()
}

private fun medicationNoticeToken(occurrenceId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest((occurrenceId + ":primary").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
