package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareEventReminderTest {
    private val time = LocalDateTime.of(2026, 11, 21, 10, 0)
    private fun event(draft: CareEventDraft) = CareEvent("event", "person", draft, time, time, 1)

    @Test fun confirmedAppointmentWithExplicitOptInUsesOneHourBefore() {
        val draft = CareEventDraft(CareEventKind.APPOINTMENT, "Cita ficticia", scheduledAt = time,
            status = CareEventStatus.CONFIRMED, reminderEnabled = true)
        assertEquals(time.minusHours(1), careEventReminderAt(event(draft)))
        assertEquals(time.minusMinutes(30), careEventReminderAt(event(draft.copy(reminderMinutesBefore = 30))))
        assertNull(careEventReminderAt(event(draft.copy(reminderEnabled = false))))
        assertNull(careEventReminderAt(event(draft.copy(status = CareEventStatus.CANCELLED))))
        assertNull(careEventReminderAt(event(draft.copy(status = CareEventStatus.COMPLETED))))
    }

    @Test fun studiesNeedScheduledStatusAndActualTime() {
        val draft = CareEventDraft(CareEventKind.STUDY, "Estudio ficticio", scheduledAt = time,
            status = CareEventStatus.SCHEDULED, reminderEnabled = true)
        assertEquals(time.minusHours(1), careEventReminderAt(event(draft)))
        assertNull(careEventReminderAt(event(draft.copy(scheduledAt = null))))
        assertNull(careEventReminderAt(event(draft.copy(status = CareEventStatus.PERFORMED))))
    }

    @Test fun multipleOffsetsProduceDistinctChronologicalReminders() {
        val draft = CareEventDraft(CareEventKind.APPOINTMENT, "Cita ficticia", scheduledAt = time,
            status = CareEventStatus.CONFIRMED, reminderEnabled = true,
            reminderOffsetsMinutes = setOf(30, 120, 1440))
        assertEquals(
            listOf(CareEventReminder(1440, time.minusDays(1)),
                CareEventReminder(120, time.minusHours(2)),
                CareEventReminder(30, time.minusMinutes(30))),
            careEventReminders(event(draft)),
        )
        assertEquals(emptyList<CareEventReminder>(), careEventReminders(event(draft.copy(reminderEnabled = false))))
        assertEquals(emptyList<CareEventReminder>(), careEventReminders(event(draft.copy(status = CareEventStatus.CANCELLED))))
        assertEquals(emptyList<CareEventReminder>(), careEventReminders(event(draft.copy(status = CareEventStatus.COMPLETED))))
        assertEquals(emptyList<CareEventReminder>(), careEventReminders(event(draft.copy(scheduledAt = null, scheduledDate = time.toLocalDate()))))
    }

    @Test fun legacySingleReminderKeepsItsConfiguredOffset() {
        val draft = CareEventDraft(CareEventKind.STUDY, "Estudio ficticio", scheduledAt = time,
            status = CareEventStatus.SCHEDULED, reminderEnabled = true, reminderMinutesBefore = 120)
        assertEquals(listOf(CareEventReminder(120, time.minusHours(2))), careEventReminders(event(draft)))
        assertEquals(emptyList<CareEventReminder>(), careEventReminders(event(draft.copy(status = CareEventStatus.PERFORMED))))
    }

    @Test fun independentTokensAndClaimsPreserveLegacyIdempotency() {
        val legacy = event(CareEventDraft(CareEventKind.APPOINTMENT, "Cita ficticia", scheduledAt = time,
            status = CareEventStatus.CONFIRMED, reminderEnabled = true))
        val legacyReminder = careEventReminders(legacy).single()
        val legacyClaim = "appointment_" + java.util.UUID.nameUUIDFromBytes(
            (legacy.id + "|" + time).toByteArray(Charsets.UTF_8),
        )
        assertEquals(legacy.id, careEventReminderAlarmToken(legacy, legacyReminder))
        assertEquals(legacyClaim, careEventReminderDeliveryKey(legacy, 60))

        val modern = legacy.copy(draft = legacy.draft.copy(reminderOffsetsMinutes = setOf(30, 120, 1440)))
        val reminders = careEventReminders(modern)
        val tokens = reminders.map { careEventReminderAlarmToken(modern, it) }
        val claims = reminders.map { careEventReminderDeliveryKey(modern, it.minutesBefore) }
        assertEquals(3, tokens.toSet().size)
        assertEquals(3, claims.toSet().size)
        org.junit.Assert.assertTrue(tokens.none { it.contains(legacy.id) })
        assertEquals(tokens, careEventReminders(modern).map { careEventReminderAlarmToken(modern, it) })

        val rescheduled = modern.copy(draft = modern.draft.copy(scheduledAt = time.plusDays(1)))
        org.junit.Assert.assertNotEquals(tokens.first(),
            careEventReminderAlarmToken(rescheduled, careEventReminders(rescheduled).first()))
        assertEquals(careEventReminderDeliveryKey(legacy, 30), careEventReminderDeliveryKey(modern, 30))
    }

    @Test fun suggestedReviewNeverProducesAnAlarm() {
        val draft = CareEventDraft(CareEventKind.REVIEW_TO_SCHEDULE, "Revisión ficticia",
            suggestedDate = time.toLocalDate(), status = CareEventStatus.TO_SCHEDULE, reminderEnabled = true)
        assertNull(careEventReminderAt(event(draft)))
    }
}
