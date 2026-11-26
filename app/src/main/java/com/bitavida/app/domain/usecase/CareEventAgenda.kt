package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import java.time.LocalDate

/** Read-only day projection: suggested dates are never treated as confirmed appointments. */
fun careEventsForDay(events: List<CareEvent>, personUnderCareId: String, day: LocalDate): List<CareEvent> =
    events.asSequence()
        .filter { it.personUnderCareId == personUnderCareId && it.draft.showInToday }
        .distinctBy { it.id }
        .filter { event ->
            when (event.draft.kind) {
                CareEventKind.REVIEW_TO_SCHEDULE -> false
                CareEventKind.APPOINTMENT -> event.draft.status == CareEventStatus.CONFIRMED &&
                    (event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate) == day
                CareEventKind.STUDY -> event.draft.status == CareEventStatus.SCHEDULED &&
                    (event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate) == day
            }
        }
        .sortedWith(compareBy<CareEvent> { it.draft.scheduledAt?.toLocalTime() }.thenBy { it.id })
        .toList()

/** Calendar history uses the recorded schedule and explicit status; suggested dates are excluded. */
fun careCalendarEventsForDay(events: List<CareEvent>, personUnderCareId: String, day: LocalDate): List<CareEvent> =
    events.asSequence().filter { it.personUnderCareId == personUnderCareId }.distinctBy { it.id }
        .filter { event ->
            val dated = (event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate) == day
            dated && when (event.draft.kind) {
                CareEventKind.REVIEW_TO_SCHEDULE -> false
                CareEventKind.APPOINTMENT -> event.draft.status in setOf(CareEventStatus.CONFIRMED, CareEventStatus.COMPLETED, CareEventStatus.CANCELLED)
                CareEventKind.STUDY -> event.draft.status in setOf(CareEventStatus.SCHEDULED, CareEventStatus.PERFORMED, CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)
            }
        }.sortedWith(compareBy<CareEvent> { it.draft.scheduledAt?.toLocalTime() }.thenBy { it.id }).toList()
