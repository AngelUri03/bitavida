package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareEventWizardSchedulingTest {
    private val draft = CareEventDraft(kind = CareEventKind.APPOINTMENT, title = "Consulta ficticia",
        status = CareEventStatus.CONFIRMED)

    @Test fun dateAndTimeAreRequiredEvenIfDateIsKnown() {
        assertFalse(validWizardDate(draft))
        assertFalse(validWizardDate(draft.copy(scheduledDate = LocalDate.of(2026, 11, 24))))
        val now = LocalDateTime.of(2026, 11, 21, 10, 0)
        assertTrue(validWizardDate(draft.copy(scheduledAt = now.plusMinutes(1)), now))
        assertFalse(validWizardDate(draft.copy(scheduledAt = now), now))
        assertFalse(validWizardDate(draft.copy(scheduledAt = now.minusMinutes(1)), now))
    }

    @Test fun reminderChoicesCombineAndNoneClearsThem() {
        val oneDay = toggleWizardReminder(draft, 1440)
        val combined = toggleWizardReminder(oneDay, 120)
        assertTrue(combined.reminderEnabled)
        assertEquals(setOf(1440, 120), combined.reminderOffsetsMinutes)
        val thirty = toggleWizardReminder(combined, 30)
        assertEquals(setOf(1440, 120, 30), thirty.reminderOffsetsMinutes)
        val cleared = clearWizardReminders(thirty)
        assertFalse(cleared.reminderEnabled)
        assertTrue(cleared.reminderOffsetsMinutes.isEmpty())
        val restarted = toggleWizardReminder(cleared, 30)
        assertEquals(setOf(30), restarted.reminderOffsetsMinutes)
    }

    @Test fun offsetsAreUnavailableAtOrAfterTheirTriggerTime() {
        val appointment = LocalDateTime.of(2026, 11, 24, 10, 30)
        val now = LocalDateTime.of(2026, 11, 24, 9, 0)
        assertTrue(reminderIsPast(appointment, 120, now))
        assertFalse(reminderIsPast(appointment, 30, now))
        assertTrue(reminderIsPast(appointment, 30, appointment.minusMinutes(30)))
    }
}
