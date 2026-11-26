package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CareEventAgendaTest {
    @Test fun includesOnlyConfirmedScheduledAndCurrentPatientWithoutDuplicates() {
        val date = LocalDate.of(2026, 11, 21)
        val now = date.atTime(9, 0)
        fun event(id: String, person: String, kind: CareEventKind, status: CareEventStatus,
            dateTime: LocalDateTime? = null, dateOnly: LocalDate? = null) =
            CareEvent(id, person, CareEventDraft(kind, "Ejemplo", status = status,
                suggestedDate = date, scheduledAt = dateTime, scheduledDate = dateOnly), now, now, 1)
        val confirmed = event("one", "p", CareEventKind.APPOINTMENT, CareEventStatus.CONFIRMED, now)
        val study = event("two", "p", CareEventKind.STUDY, CareEventStatus.SCHEDULED, dateOnly = date)
        val noTime = event("seven", "p", CareEventKind.APPOINTMENT, CareEventStatus.CONFIRMED, dateOnly = date)
        val events = listOf(confirmed, confirmed, study, noTime,
            event("three", "p", CareEventKind.REVIEW_TO_SCHEDULE, CareEventStatus.TO_SCHEDULE),
            event("four", "p", CareEventKind.APPOINTMENT, CareEventStatus.COMPLETED, now),
            event("five", "other", CareEventKind.APPOINTMENT, CareEventStatus.CONFIRMED, now),
            event("six", "p", CareEventKind.STUDY, CareEventStatus.REQUESTED, dateOnly = date))
        assertEquals(setOf("one", "two", "seven"), careEventsForDay(events, "p", date).map { it.id }.toSet())
        assertEquals(3, careEventsForDay(events, "p", date).size)
        assertEquals(emptyList<CareEvent>(), careEventsForDay(events, "p", date.plusDays(1)))
    }
}
