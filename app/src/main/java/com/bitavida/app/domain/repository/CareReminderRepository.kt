package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.usecase.CareReminderCommand
import java.time.LocalDateTime

data class CareReminderActor(
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val authorizationCurrent: Boolean,
)

interface CareReminderRepository {
    suspend fun load(actor: CareReminderActor): List<CareReminder>
    /** Validates authorization, current revision, active limit and operation identity atomically. */
    suspend fun apply(actor: CareReminderActor, command: CareReminderCommand, now: LocalDateTime): CareReminder
}

class CareReminderAuthorizationException : Exception("Care reminder operation is not authorized.")
class CareReminderStorageException(cause: Throwable? = null) :
    Exception("Care reminder storage unavailable.", cause)
