package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.CareReminderRepository
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.validation.fitsTextLimit
import java.time.LocalDateTime

sealed interface CareReminderCommand {
    val operationId: String
    val id: String

    data class Create(override val operationId: String, override val id: String, val draft: CareReminderDraft) : CareReminderCommand
    data class Edit(override val operationId: String, override val id: String, val expectedRevision: Int, val draft: CareReminderDraft) : CareReminderCommand
    data class Postpone(override val operationId: String, override val id: String, val expectedRevision: Int, val scheduledAt: LocalDateTime) : CareReminderCommand
    data class Close(override val operationId: String, override val id: String, val expectedRevision: Int, val status: CareReminderStatus) : CareReminderCommand
}

enum class CareReminderProblem { TITLE_REQUIRED, TITLE_TOO_LONG, DETAILS_TOO_LONG, FUTURE_REQUIRED, ACTIVE_LIMIT, NOT_FOUND, CLOSED, CONFLICT, INVALID_OPERATION }
class CareReminderValidationException(val problem: CareReminderProblem) : Exception(problem.name)

class GetCareReminders(private val repository: CareReminderRepository) {
    suspend operator fun invoke(actor: CareReminderActor): List<CareReminder> = repository.load(actor)
}

class ManageCareReminders(private val repository: CareReminderRepository) {
    suspend operator fun invoke(
        actor: CareReminderActor,
        command: CareReminderCommand,
        now: LocalDateTime = LocalDateTime.now(),
    ): CareReminder = repository.apply(actor, command, now)
}

/** Shared by the encrypted transaction and JVM tests. Never classifies titles or clinical intent. */
fun applyCareReminderCommand(
    records: List<CareReminder>,
    personUnderCareId: String,
    command: CareReminderCommand,
    now: LocalDateTime,
): CareReminder {
    fun fail(problem: CareReminderProblem): Nothing = throw CareReminderValidationException(problem)
    if (command.operationId.isBlank() || command.id.isBlank() || personUnderCareId.isBlank()) fail(CareReminderProblem.INVALID_OPERATION)
    val current = records.singleOrNull { it.id == command.id && it.personUnderCareId == personUnderCareId }
    if (command is CareReminderCommand.Create) {
        if (current != null) fail(CareReminderProblem.CONFLICT)
        if (activeCareReminders(records, personUnderCareId).size >= MAX_ACTIVE_CARE_REMINDERS) fail(CareReminderProblem.ACTIVE_LIMIT)
        val draft = command.draft
        if (draft.title.isBlank()) fail(CareReminderProblem.TITLE_REQUIRED)
        if (!fitsTextLimit(draft.title, CaptureLimit.REMINDER_TITLE.maximum)) fail(CareReminderProblem.TITLE_TOO_LONG)
        if (!fitsTextLimit(draft.details.orEmpty(), CaptureLimit.REMINDER_DETAILS.maximum)) fail(CareReminderProblem.DETAILS_TOO_LONG)
        if (!draft.scheduledAt.isAfter(now)) fail(CareReminderProblem.FUTURE_REQUIRED)
        return CareReminder(command.id, personUnderCareId, draft.title.trim(), draft.details?.trim()?.takeIf { it.isNotEmpty() },
            draft.scheduledAt, draft.notificationsEnabled, CareReminderStatus.PENDING, now, now, null, 1)
    }
    val original = current ?: fail(CareReminderProblem.NOT_FOUND)
    if (original.status != CareReminderStatus.PENDING) fail(CareReminderProblem.CLOSED)
    val expected = when (command) {
        is CareReminderCommand.Edit -> command.expectedRevision
        is CareReminderCommand.Postpone -> command.expectedRevision
        is CareReminderCommand.Close -> command.expectedRevision
        is CareReminderCommand.Create -> error("Already handled")
    }
    if (original.revision != expected) fail(CareReminderProblem.CONFLICT)
    // A clock adjustment cannot make the persisted modification precede creation/previous writes.
    val modifiedAt = maxOf(now, original.modifiedAt)
    return when (command) {
        is CareReminderCommand.Edit -> {
            val draft = command.draft
            if (draft.title.isBlank()) fail(CareReminderProblem.TITLE_REQUIRED)
            if (!fitsTextLimit(draft.title, CaptureLimit.REMINDER_TITLE.maximum, original.title)) fail(CareReminderProblem.TITLE_TOO_LONG)
            if (!fitsTextLimit(draft.details.orEmpty(), CaptureLimit.REMINDER_DETAILS.maximum, original.details.orEmpty())) fail(CareReminderProblem.DETAILS_TOO_LONG)
            if (draft.scheduledAt != original.scheduledAt && !draft.scheduledAt.isAfter(now)) fail(CareReminderProblem.FUTURE_REQUIRED)
            original.copy(
                title = if (draft.title == original.title) original.title else draft.title.trim(),
                details = if (draft.details.orEmpty() == original.details.orEmpty()) original.details
                    else draft.details?.trim()?.takeIf { it.isNotEmpty() },
                scheduledAt = draft.scheduledAt, notificationsEnabled = draft.notificationsEnabled,
                modifiedAt = modifiedAt, revision = original.revision + 1)
        }
        is CareReminderCommand.Postpone -> {
            if (!command.scheduledAt.isAfter(now)) fail(CareReminderProblem.FUTURE_REQUIRED)
            original.copy(scheduledAt = command.scheduledAt, modifiedAt = modifiedAt, revision = original.revision + 1)
        }
        is CareReminderCommand.Close -> {
            if (command.status == CareReminderStatus.PENDING) fail(CareReminderProblem.INVALID_OPERATION)
            original.copy(status = command.status, closedAt = modifiedAt, modifiedAt = modifiedAt, revision = original.revision + 1)
        }
        is CareReminderCommand.Create -> error("Already handled")
    }
}

fun activeCareReminders(records: List<CareReminder>, personUnderCareId: String): List<CareReminder> =
    records.filter { it.personUnderCareId == personUnderCareId && it.status == CareReminderStatus.PENDING }
        .distinctBy { it.id }.sortedWith(compareBy<CareReminder> { it.scheduledAt }.thenBy { it.id })

fun previousCareReminders(records: List<CareReminder>, personUnderCareId: String): List<CareReminder> =
    records.filter { it.personUnderCareId == personUnderCareId && it.status != CareReminderStatus.PENDING }
        .distinctBy { it.id }.sortedWith(compareByDescending<CareReminder> { it.closedAt }.thenBy { it.id })
