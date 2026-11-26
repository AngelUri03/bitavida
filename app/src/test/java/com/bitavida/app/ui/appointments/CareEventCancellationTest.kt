package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareEventCommand
import com.bitavida.app.domain.usecase.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CareEventCancellationTest {
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val original = CareEvent(
        id = "ejemplo", personUnderCareId = "persona",
        draft = CareEventDraft(kind = CareEventKind.APPOINTMENT, title = "Consulta de ejemplo",
            status = CareEventStatus.CONFIRMED, scheduledAt = now.plusDays(2),
            reminderEnabled = true, reminderOffsetsMinutes = setOf(120, 1440)),
        createdAt = now, modifiedAt = now, revision = 1,
    )

    @Test fun cancelledRevisionPreservesDetailsAndDoesNotProduceNotices() {
        val draft = original.draft.copy(status = CareEventStatus.CANCELLED)
        val cancelled = applyCareEventCommand(listOf(original), "persona",
            CareEventCommand.Revise("cancelar", original.id, original.revision, draft), now)
        assertEquals(original.id, cancelled.id)
        assertEquals(2, cancelled.revision)
        assertEquals(CareEventStatus.CANCELLED, cancelled.draft.status)
        assertEquals(original.draft.scheduledAt, cancelled.draft.scheduledAt)
        assertEquals(original.draft.reminderOffsetsMinutes, cancelled.draft.reminderOffsetsMinutes)
        assertTrue(careEventReminders(cancelled).isEmpty())
        assertEquals(CareEventStatus.CONFIRMED, original.draft.status)
        assertFalse(isFutureAppointment(cancelled, now))
    }

    @Test fun committedCancellationReturnsHomeAndUpdatesAgendaAndHistory() {
        val other = original.copy(id = "siguiente", draft = original.draft.copy(scheduledAt = now.plusDays(3)))
        val cancelled = original.copy(revision = 2,
            draft = original.draft.copy(status = CareEventStatus.CANCELLED))
        val state = CareEventsState(events = listOf(original, other), selected = original,
            history = listOf(original), saving = true, cancellationGeneration = 4)

        val result = careEventCancelledState(state, cancelled)

        assertNull(result.selected)
        assertTrue(result.history.isEmpty())
        assertFalse(result.saving)
        assertEquals(5L, result.cancellationGeneration)
        assertEquals(listOf(other), upcomingAppointments(result.events, now))
        assertEquals(listOf(cancelled), appointmentHistory(result.events, now))
        assertTrue(eventsOnDate(result.events, original.draft.scheduledAt!!.toLocalDate()).isEmpty())
        assertEquals(original.draft.scheduledAt, cancelled.draft.scheduledAt)
        assertTrue(careEventReminders(cancelled).isEmpty())
    }

    @Test fun pastEventCannotBeCancelledRetroactively() {
        val old = original.copy(draft = original.draft.copy(scheduledAt = now.minusMinutes(1)))
        val problem = runCatching {
            applyCareEventCommand(listOf(old), "persona",
                CareEventCommand.Revise("cancelar", old.id, old.revision,
                    old.draft.copy(status = CareEventStatus.CANCELLED)), now)
        }.exceptionOrNull() as CareEventValidationException
        assertEquals(CareEventProblem.HISTORICAL_READ_ONLY, problem.problem)
    }
}
