package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareEventCommand
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CareEventWizardValidationTest {
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val scheduled = CareEventDraft(
        kind = CareEventKind.APPOINTMENT, title = "Consulta ficticia",
        status = CareEventStatus.CONFIRMED, scheduledAt = now.plusDays(2),
        purpose = CareEventPurpose.MEDICAL_CONSULTATION, timeMode = CareEventTimeMode.EXACT,
        reminderEnabled = true, reminderOffsetsMinutes = setOf(30, 120, 1440),
    )
    private fun create(draft: CareEventDraft) =
        applyCareEventCommand(emptyList(), "person", CareEventCommand.Create("op", "event", draft), now)
    private fun invalid(draft: CareEventDraft) {
        val error = runCatching { create(draft) }.exceptionOrNull()
        assertEquals(CareEventProblem.INVALID_STATE, (error as CareEventValidationException).problem)
    }

    @Test fun newEventsRequireAnExplicitTimeWithoutChangingLegacyData() {
        for (mode in listOf(CareEventTimeMode.TIME_UNCONFIRMED, CareEventTimeMode.ALL_DAY, CareEventTimeMode.EXACT)) {
            val draft = scheduled.copy(scheduledAt = null, scheduledDate = now.toLocalDate(),
                timeMode = mode, reminderEnabled = false, reminderOffsetsMinutes = emptySet())
            val error = runCatching { create(draft) }.exceptionOrNull() as CareEventValidationException
            assertEquals(CareEventProblem.CONFIRMED_TIME_REQUIRED, error.problem)
        }
        invalid(scheduled.copy(timeMode = CareEventTimeMode.ALL_DAY))
    }

    @Test fun pendingRevisionsKeepIdentityAndPriorVersion() {
        val original = create(scheduled)
        val pending = original.draft.copy(kind = CareEventKind.REVIEW_TO_SCHEDULE,
            status = CareEventStatus.TO_SCHEDULE, scheduledAt = null, scheduledDate = null,
            timeMode = CareEventTimeMode.TO_SCHEDULE, reminderEnabled = false, reminderOffsetsMinutes = emptySet())
        val error = runCatching {
            applyCareEventCommand(listOf(original), "person",
                CareEventCommand.Revise("op2", "event", 1, pending), now.plusMinutes(1))
        }.exceptionOrNull() as CareEventValidationException
        assertEquals(CareEventProblem.CONFIRMED_TIME_REQUIRED, error.problem)
        assertNotNull(original.draft.scheduledAt)
    }

    @Test fun alertsRequireKnownTimeAndAllowedOffsets() {
        invalid(scheduled.copy(reminderOffsetsMinutes = setOf(7)))
        invalid(scheduled.copy(reminderEnabled = false))
        val missingTime = runCatching { create(scheduled.copy(scheduledAt = null, scheduledDate = now.toLocalDate(),
            timeMode = CareEventTimeMode.TIME_UNCONFIRMED)) }.exceptionOrNull() as CareEventValidationException
        assertEquals(CareEventProblem.CONFIRMED_TIME_REQUIRED, missingTime.problem)
        assertEquals(setOf(30, 120, 1440), create(scheduled).draft.reminderOffsetsMinutes)
    }

    @Test fun allNewTextFieldsHavePersistenceLimits() {
        for (draft in listOf(scheduled.copy(professional = "x".repeat(101)),
            scheduled.copy(reference = "x".repeat(201)), scheduled.copy(contactInformation = "x".repeat(201)),
            scheduled.copy(otherPreparation = "x".repeat(501)))) {
            val error = runCatching { create(draft) }.exceptionOrNull() as CareEventValidationException
            assertEquals(CareEventProblem.TEXT_TOO_LONG, error.problem)
        }
    }

    @Test fun todayVisibilityDoesNotRemoveCalendarEvent() {
        val event = create(scheduled.copy(showInToday = false))
        val day = scheduled.scheduledAt!!.toLocalDate()
        assertTrue(careEventsForDay(listOf(event), "person", day).isEmpty())
        assertEquals(listOf(event), careCalendarEventsForDay(listOf(event), "person", day))
    }
}
