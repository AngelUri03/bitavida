package com.bitavida.app.domain.model

import java.time.LocalDateTime

enum class CareReminderStatus { PENDING, DONE, DISCARDED }

/** A freely declared care errand; passing its scheduled time never changes its status. */
data class CareReminder(
    val id: String,
    val personUnderCareId: String,
    val title: String,
    val details: String?,
    val scheduledAt: LocalDateTime,
    val notificationsEnabled: Boolean,
    val status: CareReminderStatus,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
    val closedAt: LocalDateTime?,
    val revision: Int,
    val originallyScheduledAt: LocalDateTime = scheduledAt,
) {
    fun hasPastSchedule(now: LocalDateTime): Boolean =
        status == CareReminderStatus.PENDING && scheduledAt.isBefore(now)
}

data class CareReminderDraft(
    val title: String,
    val details: String?,
    val scheduledAt: LocalDateTime,
    val notificationsEnabled: Boolean = false,
)

const val MAX_ACTIVE_CARE_REMINDERS = 10
