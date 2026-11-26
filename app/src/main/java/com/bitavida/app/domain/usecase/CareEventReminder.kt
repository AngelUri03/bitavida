package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import java.time.LocalDateTime

data class CareEventReminder(val minutesBefore: Int, val at: LocalDateTime)

/** Only explicitly enabled, timed and scheduled events may produce notices. */
fun careEventReminders(event: CareEvent): List<CareEventReminder> {
    val draft = event.draft
    if (!draft.reminderEnabled) return emptyList()
    val eligible = when (draft.kind) {
        CareEventKind.APPOINTMENT -> draft.status == CareEventStatus.CONFIRMED
        CareEventKind.STUDY -> draft.status == CareEventStatus.SCHEDULED
        CareEventKind.REVIEW_TO_SCHEDULE -> false
    }
    val scheduled = draft.scheduledAt?.takeIf { eligible } ?: return emptyList()
    // Empty set means the event predates multiple reminders; retain its configured single offset.
    val offsets = draft.reminderOffsetsMinutes.ifEmpty { setOf(draft.reminderMinutesBefore) }
    return offsets.asSequence().filter { it in 1..10080 }.distinct()
        .map { CareEventReminder(it, scheduled.minusMinutes(it.toLong())) }
        .sortedBy { it.at }.toList()
}

/** Compatibility for existing consumers of a single reminder. */
fun careEventReminderAt(event: CareEvent): LocalDateTime? =
    careEventReminders(event).firstOrNull()?.at

/** Stable opaque identifiers keep historical deliveries idempotent across upgrades. */
fun careEventReminderDeliveryKey(event: CareEvent, minutesBefore: Int): String {
    val suffix = if (minutesBefore == 60) "" else "|$minutesBefore"
    val source = event.id + "|" + event.draft.scheduledAt + suffix
    return "appointment_" + java.util.UUID.nameUUIDFromBytes(source.toByteArray(Charsets.UTF_8))
}

/** Legacy alarms used the event id; multi-reminder alarms use one opaque token per schedule. */
fun careEventReminderAlarmToken(event: CareEvent, reminder: CareEventReminder): String =
    if (event.draft.reminderOffsetsMinutes.isEmpty()) event.id
    else "appointment_" + java.util.UUID.nameUUIDFromBytes(
        ("appointment|" + event.id + "|" + event.draft.scheduledAt + "|" + reminder.minutesBefore)
            .toByteArray(Charsets.UTF_8),
    )

