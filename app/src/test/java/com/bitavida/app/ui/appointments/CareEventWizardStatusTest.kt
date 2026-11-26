package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareEventCommand
import com.bitavida.app.domain.usecase.*
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CareEventWizardStatusTest {
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val draft = CareEventDraft(CareEventKind.APPOINTMENT, "Ejemplo",
        status = CareEventStatus.CONFIRMED, scheduledAt = now.plusDays(1))

    @Test fun attendanceCannotBeDeclaredByEditingTheAppointment() {
        val original = CareEvent("event", "person", draft, now, now, 1)
        val command = CareEventCommand.Revise("op", "event", 1, draft.copy(status = CareEventStatus.COMPLETED))
        val error = runCatching { applyCareEventCommand(listOf(original), "person", command, now) }
            .exceptionOrNull() as CareEventValidationException
        assertEquals(CareEventProblem.INVALID_STATE, error.problem)
    }

    @Test fun historicalEventCannotBeRewrittenIntoTheFuture() {
        val original = CareEvent("event", "person", draft.copy(scheduledAt = now.minusMinutes(1)), now, now, 1)
        val command = CareEventCommand.Revise("op", "event", 1, draft)
        val error = runCatching { applyCareEventCommand(listOf(original), "person", command, now) }
            .exceptionOrNull() as CareEventValidationException
        assertEquals(CareEventProblem.HISTORICAL_READ_ONLY, error.problem)
    }

    @Test fun creationRejectsPastAndPresentTimes() {
        listOf(now, now.minusMinutes(1)).forEach { date ->
            val command = CareEventCommand.Create("op", "event", draft.copy(scheduledAt = date))
            val error = runCatching { applyCareEventCommand(emptyList(), "person", command, now) }
                .exceptionOrNull() as CareEventValidationException
            assertEquals(CareEventProblem.PAST_TIME, error.problem)
        }
    }
}
