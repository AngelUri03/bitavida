package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CareEventsProjectionTest {
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)
    private fun event(id: String, date: LocalDate?, hour: Int? = null,
        category: CareEventCategory = CareEventCategory.GENERAL,
        kind: CareEventKind = CareEventKind.APPOINTMENT) = CareEvent(
        id, "persona", CareEventDraft(kind = kind, title = "Evento", status = CareEventStatus.CONFIRMED,
            category = category, scheduledDate = date, scheduledAt = if (date != null && hour != null) date.atTime(hour, 0) else null),
        now, now, 1)

    @Test fun monthGridStartsMondayAndHandlesLeapYear() {
        val february = monthDays(YearMonth.of(2024, 2))
        assertEquals(3, february.indexOf(LocalDate.of(2024, 2, 1)))
        assertTrue(february.contains(LocalDate.of(2024, 2, 29)))
        assertEquals(0, february.size % 7)
        assertFalse(monthDays(YearMonth.of(2023, 2)).contains(LocalDate.of(2023, 3, 1)))
        assertEquals(6, monthDays(YearMonth.of(2026, 11)).indexOf(LocalDate.of(2026, 11, 1)))
    }

    @Test fun movingMonthClampsDayAndCrossesYear() {
        assertEquals(LocalDate.of(2024, 2, 29), moveMonth(LocalDate.of(2024, 1, 31), 1))
        assertEquals(LocalDate.of(2027, 1, 15), moveMonth(LocalDate.of(2026, 12, 15), 1))
        assertEquals(LocalDate.of(2025, 12, 15), moveMonth(LocalDate.of(2026, 1, 15), -1))
    }

    @Test fun selectedDateReturnsAllEventsInTimeOrderAndExcludesUndated() {
        val date = LocalDate.of(2026, 11, 24)
        val events = listOf(event("sin hora", date), event("tarde", date, 16),
            event("temprano", date, 7), event("pendiente", null), event("otro", date.plusDays(1)))
        assertEquals(listOf("temprano", "tarde", "sin hora"), eventsOnDate(events, date).map { it.id })
        assertTrue(eventsOnDate(events, date.plusDays(2)).isEmpty())
    }

    @Test fun nextAppointmentAndOthersUseExactDateTimeAndNeverFilterByPurpose() {
        val date = LocalDate.of(2026, 11, 24)
        val clock = date.atTime(10, 30)
        val near = event("near", date, 11)
        val study = event("study", date, 12, kind = CareEventKind.STUDY)
            .let { it.copy(draft = it.draft.copy(status = CareEventStatus.SCHEDULED, purpose = CareEventPurpose.STUDY)) }
        val pastToday = event("past", date, 9)
        val dateOnly = event("legacy", date.plusDays(2))
        val noDate = event("undated", null)
        val future = event("future", date.plusDays(1), 9)
        assertEquals(listOf("near", "study", "future"),
            upcomingAppointments(listOf(future, study, noDate, pastToday, dateOnly, near), clock).map { it.id })
        assertEquals(listOf("legacy", "past", "undated"),
            appointmentHistory(listOf(future, study, noDate, pastToday, dateOnly, near), clock).map { it.id })
    }

    @Test fun historyGroupsDescendingByMonthAndKeepsUndatedRecords() {
        val clock = LocalDateTime.of(2026, 12, 1, 10, 0)
        val older = event("old", LocalDate.of(2026, 10, 30), 9)
        val recent = event("recent", LocalDate.of(2026, 11, 23), 10)
        val otherNovember = event("nov", LocalDate.of(2026, 11, 4), 12)
        val undated = event("undated", null)
        val future = event("future", LocalDate.of(2026, 12, 4), 12)
        val groups = appointmentHistoryGroups(listOf(older, undated, future, recent, otherNovember), clock)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), null), groups.map { it.first })
        assertEquals(listOf("recent", "nov"), groups[0].second.map { it.id })
        assertEquals(listOf("old"), groups[1].second.map { it.id })
        assertEquals(listOf("undated"), groups[2].second.map { it.id })
    }

    @Test fun legacyRequestedEventWithActualTimeIsNotHiddenFromTheAgenda() {
        val date = LocalDate.of(2026, 11, 24)
        val legacy = event("legacy", date, 11, kind = CareEventKind.STUDY).let {
            it.copy(draft = it.draft.copy(status = CareEventStatus.REQUESTED))
        }
        assertEquals(listOf(legacy), upcomingAppointments(listOf(legacy), date.atTime(10, 0)))
        assertEquals(listOf(legacy), eventsOnDate(listOf(legacy), date))
    }

    @Test fun boundaryMovesEventToHistoryWithoutChangingItsStatus() {
        val date = LocalDate.of(2026, 11, 24)
        val original = event("event", date, 10)
        assertTrue(isFutureAppointment(original, date.atTime(9, 59)))
        assertFalse(isFutureAppointment(original, date.atTime(10, 0)))
        assertTrue(isHistoricalAppointment(original, date.atTime(10, 0)))
        assertEquals(CareEventStatus.CONFIRMED, original.draft.status)
    }

    @Test fun cancelledOrPreviouslyCompletedEventsStayAccessibleAsHistory() {
        val date = LocalDate.of(2026, 11, 24)
        val current = event("one", date, 10)
        val cancelled = current.copy(id = "cancelled", draft = current.draft.copy(status = CareEventStatus.CANCELLED))
        val completed = current.copy(id = "completed", draft = current.draft.copy(status = CareEventStatus.COMPLETED))
        assertTrue(upcomingAppointments(listOf(cancelled, completed), date.atStartOfDay()).isEmpty())
        assertEquals(setOf("cancelled", "completed"),
            appointmentHistory(listOf(cancelled, completed), date.atStartOfDay()).map { it.id }.toSet())
    }
}
