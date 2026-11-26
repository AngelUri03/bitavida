package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class FollowUpReportPeriodTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val patient = "patient"
    private val overview = MedicalCareOverview(emptyList(), emptyList())

    private fun event(id: String, day: LocalDate, status: CareEventStatus, person: String = patient) =
        CareEvent(id, person, CareEventDraft(CareEventKind.APPOINTMENT, "Consulta ficticia",
            scheduledDate = day, status = status, purpose = CareEventPurpose.MEDICAL_CONSULTATION,
            consultationType = CareConsultationType.FAMILY_MEDICINE, isFollowUp = true), today.atStartOfDay(), today.atStartOfDay(), 1)

    private fun reminder(id: String, status: CareReminderStatus, closed: LocalDate?) =
        CareReminder(id, patient, "Recordatorio ficticio", null, today.minusDays(30).atStartOfDay(),
            false, status, today.minusDays(31).atStartOfDay(), today.atStartOfDay(),
            closed?.atStartOfDay(), 1)

    @Test fun monthlyPeriodAlwaysIncludesTheWholeMonth() {
        assertEquals(FollowUpPeriod(today.withDayOfMonth(1), today.withDayOfMonth(30)),
            monthlyFollowUpPeriod(YearMonth.from(today)))
        assertEquals(LocalDate.of(2028, 2, 29), monthlyFollowUpPeriod(YearMonth.of(2028, 2)).end)
    }

    @Test fun noHistoryUsesCurrentMonthAndNeverInventsAConsultation() {
        val first = firstFollowUpEventDate(patient, overview, emptyList(), emptyList(),
            listOf(reminder("pending", CareReminderStatus.PENDING, null)),
            listOf(event("future", today.plusDays(2), CareEventStatus.CONFIRMED)), today)
        assertNull(first)
        val choices = consultationFollowUpPeriods(first, emptyList(), patient, today)
        assertEquals(ConsultationIntervalKind.CURRENT_MONTH, choices.selected.kind)
        assertEquals(monthlyFollowUpPeriod(YearMonth.from(today)), choices.selected.period)
    }

    @Test fun firstActualEventExcludesPendingFutureAndNotPerformedObservations() {
        val observation = ObservationRecord("obs", "fictional", patient, today.minusDays(10), null,
            ObservationTimePrecision.UNKNOWN, ObservationResult.NOT_PERFORMED, emptyMap(), "",
            createdAt = today.atStartOfDay(), actorId = "caregiver")
        val actual = MeasurementRecord("reading", MeasurementValue.Glucose("110"),
            today.minusDays(2).atTime(10, 0), today.atStartOfDay())
        val future = MeasurementRecord("future", MeasurementValue.Glucose("100"),
            today.plusDays(1).atStartOfDay(), today.atStartOfDay())
        val first = firstFollowUpEventDate(patient, overview, listOf(actual, future), listOf(observation),
            listOf(reminder("done", CareReminderStatus.DONE, today.minusDays(3)),
                reminder("pending", CareReminderStatus.PENDING, null)),
            listOf(event("cancelled", today.minusDays(20), CareEventStatus.CANCELLED),
                event("foreign", today.minusDays(25), CareEventStatus.COMPLETED, "other")), today)
        assertEquals(today.minusDays(3), first)
    }

    @Test fun noConsultationUsesFirstEventToToday() {
        val first = today.minusDays(5)
        val result = consultationFollowUpPeriods(first, emptyList(), patient, today).selected
        assertEquals(ConsultationIntervalKind.NO_APPOINTMENTS, result.kind)
        assertEquals(FollowUpPeriod(first, today), result.period)
    }

    @Test fun singleFutureConsultationIsConfirmedNotCompleted() {
        val first = today.minusDays(5)
        val next = today.plusDays(20)
        val result = consultationFollowUpPeriods(first,
            listOf(event("future", next, CareEventStatus.CONFIRMED)), patient, today).selected
        assertEquals(ConsultationIntervalKind.FIRST_CONFIRMED, result.kind)
        assertEquals(FollowUpPeriod(first, next), result.period)
        assertEquals(next, result.nextConsultationDate)
    }

    @Test fun selectsLastCompletedIntervalAndOffersUpcomingOneWithoutCancelledAppointments() {
        val first = today.minusDays(40)
        val a = event("a", today.minusDays(30), CareEventStatus.COMPLETED)
        val b = event("b", today.minusDays(10), CareEventStatus.COMPLETED)
        val cancelled = event("cancelled", today.minusDays(2), CareEventStatus.CANCELLED)
        val next = event("next", today.plusDays(12), CareEventStatus.CONFIRMED)
        val choices = consultationFollowUpPeriods(first, listOf(a, b, cancelled, next), patient, today)
        assertEquals(ConsultationIntervalKind.BETWEEN_COMPLETED, choices.selected.kind)
        assertEquals(FollowUpPeriod(a.draft.scheduledDate!!, b.draft.scheduledDate!!), choices.selected.period)
        assertTrue(choices.intervals.any { it.kind == ConsultationIntervalKind.IN_PROGRESS &&
            it.period == FollowUpPeriod(b.draft.scheduledDate!!, next.draft.scheduledDate!!) &&
            it.nextConsultationDate == next.draft.scheduledDate })
        assertFalse(choices.intervals.any { it.startEventId == "cancelled" || it.endEventId == "cancelled" })
    }

    @Test fun completedSingleConsultationEndsAtItsRecordedDate() {
        val first = today.minusDays(12)
        val completed = event("one", today.minusDays(2), CareEventStatus.COMPLETED)
        val result = consultationFollowUpPeriods(first, listOf(completed), patient, today).selected
        assertEquals(ConsultationIntervalKind.FIRST_COMPLETED, result.kind)
        assertEquals(FollowUpPeriod(first, today.minusDays(2)), result.period)
    }

    @Test fun ongoingIntervalIsAvailableEvenWithoutNextAppointment() {
        val first = today.minusDays(12)
        val completed = event("one", today.minusDays(2), CareEventStatus.COMPLETED)
        val result = consultationFollowUpPeriods(first, listOf(completed), patient, today)
        assertEquals(ConsultationIntervalKind.FIRST_COMPLETED, result.selected.kind)
        val ongoing = result.intervals.single { it.kind == ConsultationIntervalKind.IN_PROGRESS }
        assertEquals(FollowUpPeriod(today.minusDays(2), today), ongoing.period)
        assertNull(ongoing.nextConsultationDate)
    }

    @Test fun futureFactIsNotReportedAsCompletedButConfirmedAppointmentIsKept() {
        val period = monthlyFollowUpPeriod(YearMonth.from(today))
        val now = today.atTime(12, 0)
        val readings = listOf(
            MeasurementRecord("past", MeasurementValue.Glucose("100"), now.minusHours(1), now),
            MeasurementRecord("future", MeasurementValue.Glucose("120"), now.plusHours(1), now),
        )
        val events = listOf(
            event("future-confirmed", today.plusDays(2), CareEventStatus.CONFIRMED),
            event("future-completed", today.plusDays(3), CareEventStatus.COMPLETED),
        )
        val report = buildFollowUpReport(period.start, period.end, patient, overview, readings,
            emptyList(), events, now = now)
        assertEquals(period.end, report.end)
        assertEquals(listOf("past"), report.measurements.map { it.id })
        assertEquals(1, report.summaries.single { it.label == "Lecturas de glucosa" }.count)
        assertEquals(1, report.summaries.single { it.label == "Citas" }.count)
        assertTrue(report.rows.any { it.section == "Citas" && it.detail.contains("Confirmada") })
        assertFalse(report.rows.any { it.section == "Citas" && it.detail.contains("Realizada") })
    }

    @Test fun excludesAmbiguousAndUnmarkedOrNonConsultationEvents() {
        val first = today.minusDays(30)
        val base = event("valid", today.minusDays(2), CareEventStatus.COMPLETED)
        val ambiguous = base.copy(id = "ambiguous", draft = base.draft.copy(
            consultationType = CareConsultationType.UNSPECIFIED, isFollowUp = true))
        val nonFollowUp = base.copy(id = "unmarked", draft = base.draft.copy(isFollowUp = false))
        val study = base.copy(id = "study", draft = base.draft.copy(kind = CareEventKind.STUDY))
        val choices = consultationFollowUpPeriods(first, listOf(ambiguous, nonFollowUp, study), patient, today)
        assertEquals(ConsultationIntervalKind.NO_APPOINTMENTS, choices.selected.kind)
        assertEquals(FollowUpPeriod(first, today), choices.selected.period)
    }

    @Test fun initialListIsBoundedAndPrioritizesUpcomingAndRecentCompletedIntervals() {
        val appointments = (1..5).map { index ->
            event("completed-$index", today.minusDays((6 - index).toLong() * 10), CareEventStatus.COMPLETED)
        }
        val next = event("next", today.plusDays(10), CareEventStatus.CONFIRMED)
        val choices = consultationFollowUpPeriods(today.minusDays(70), appointments + next, patient, today)
        val shown = visibleConsultationPeriodIndices(choices).map { choices.intervals[it] }
        assertEquals(3, shown.size)
        assertEquals(ConsultationIntervalKind.IN_PROGRESS, shown.first().kind)
        assertEquals(next.draft.scheduledDate, shown.first().period.end)
        assertTrue(shown.drop(1).all { it.kind == ConsultationIntervalKind.BETWEEN_COMPLETED })
        assertTrue(shown[1].period.end >= shown[2].period.end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedCustomPeriod() {
        FollowUpPeriod(today, today.minusDays(1))
    }
}
