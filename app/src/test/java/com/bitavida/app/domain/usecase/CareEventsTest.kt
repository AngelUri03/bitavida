package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareEventCommand
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CareEventsTest {
    private val now = LocalDateTime.of(2026, 11, 21, 12, 0)
    private val review = CareEventDraft(CareEventKind.REVIEW_TO_SCHEDULE, "Revisión ficticia",
        suggestedDate = LocalDate.of(2027, 1, 8), status = CareEventStatus.TO_SCHEDULE)

    @Test fun suggestedDateNeverBecomesConfirmedAppointment() {
        val error = runCatching { applyCareEventCommand(emptyList(), "person", CareEventCommand.Create("op", "event", review), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.CONFIRMED_TIME_REQUIRED, (error as CareEventValidationException).problem)
        val legacy = CareEvent("event", "person", review, now, now, 1)
        assertEquals(LocalDate.of(2027, 1, 8), legacy.draft.suggestedDate)
        assertNull(legacy.draft.scheduledAt)
    }

    @Test fun confirmationKeepsIdentityAndRequiresExplicitTime() {
        val original = CareEvent("event", "person", review, now, now, 1)
        val missing = review.copy(kind = CareEventKind.APPOINTMENT, status = CareEventStatus.CONFIRMED)
        val error = runCatching { applyCareEventCommand(listOf(original), "person", CareEventCommand.Revise("op2", "event", 1, missing), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.CONFIRMED_TIME_REQUIRED, (error as CareEventValidationException).problem)
        val actual = now.plusDays(5)
        val saved = applyCareEventCommand(listOf(original), "person", CareEventCommand.Revise("op2", "event", 1, missing.copy(scheduledAt = actual)), now)
        assertEquals("event", saved.id)
        assertEquals(actual, saved.draft.scheduledAt)
        assertEquals(2, saved.revision)
        assertEquals(review, original.draft)
    }

    @Test fun appointmentCanHaveDateWithoutInventedTimeAndReminderRequiresTime() {
        val draft = CareEventDraft(CareEventKind.APPOINTMENT, "Consulta ficticia",
            status = CareEventStatus.CONFIRMED, scheduledDate = now.toLocalDate().plusDays(1))
        val error = runCatching { applyCareEventCommand(emptyList(), "person",
            CareEventCommand.Create("op", "event", draft), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.CONFIRMED_TIME_REQUIRED, (error as CareEventValidationException).problem)
        val legacy = CareEvent("event", "person", draft, now, now, 1)
        assertNull(legacy.draft.scheduledAt)
        assertEquals(draft.scheduledDate, legacy.draft.scheduledDate)
    }

    @Test fun studyCanHaveDateWithoutInventedTime() {
        val draft = CareEventDraft(CareEventKind.STUDY, "Estudio ficticio", status = CareEventStatus.SCHEDULED,
            scheduledDate = now.toLocalDate().plusDays(1))
        val error = runCatching { applyCareEventCommand(emptyList(), "person", CareEventCommand.Create("op", "study", draft), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.CONFIRMED_TIME_REQUIRED, (error as CareEventValidationException).problem)
    }

    @Test fun staleRevisionAndOtherPatientCannotRevise() {
        val original = CareEvent("event", "person", review, now, now, 1)
        val wrongRevision = runCatching { applyCareEventCommand(listOf(original), "person", CareEventCommand.Revise("b", "event", 9, review), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.CONFLICT, (wrongRevision as CareEventValidationException).problem)
        val wrongPerson = runCatching { applyCareEventCommand(listOf(original), "other", CareEventCommand.Revise("b", "event", 1, review), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.NOT_FOUND, (wrongPerson as CareEventValidationException).problem)
    }

    @Test fun invalidCrossTypeAndInconsistentDateAreRejected() {
        val appointment = review.copy(kind = CareEventKind.APPOINTMENT, status = CareEventStatus.CONFIRMED, scheduledAt = now.plusDays(1))
        val event = applyCareEventCommand(emptyList(), "person", CareEventCommand.Create("a", "event", appointment), now)
        val wrong = review.copy(kind = CareEventKind.STUDY, status = CareEventStatus.REQUESTED, scheduledAt = now.plusDays(1))
        assertEquals(CareEventProblem.INVALID_STATE,
            (runCatching { applyCareEventCommand(listOf(event), "person", CareEventCommand.Revise("b", "event", 1, wrong), now) }.exceptionOrNull() as CareEventValidationException).problem)
        val bad = CareEventDraft(CareEventKind.APPOINTMENT, "Cita ficticia", status = CareEventStatus.CONFIRMED,
            scheduledAt = now.plusDays(1), scheduledDate = now.toLocalDate().plusDays(2))
        assertEquals(CareEventProblem.INVALID_STATE,
            (runCatching { applyCareEventCommand(emptyList(), "person", CareEventCommand.Create("c", "other", bad), now) }.exceptionOrNull() as CareEventValidationException).problem)
    }
}
