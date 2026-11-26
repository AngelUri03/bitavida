package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpSlot
import com.bitavida.app.domain.model.GlucoseFollowUpTimingType
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionKind
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevisionOperation
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.glucoseMeasurementContexts
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.GlucoseRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.bitavida.app.domain.validation.fitsTextLimit
import com.bitavida.app.domain.validation.CaptureLimit

data class GlucoseFollowUpSlotDraft(
    val id: String,
    val timingType: GlucoseFollowUpTimingType? = null,
    val time: LocalTime? = null,
    val windowEnd: LocalTime? = null,
    val endsNextDay: Boolean = false,
    val context: GlucoseContext? = null,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime? = null,
    val reminderNextDay: Boolean = false,
    val useSameTimeForReminder: Boolean? = null,
)

data class GlucoseFollowUpDraft(
    val followUpId: String,
    val indication: String = "",
    val frequency: GlucoseFollowUpFrequency? = null,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val everyNDaysInterval: Int = 0,
    val effectiveFrom: LocalDate,
    val remindersEnabled: Boolean? = null,
    val slots: List<GlucoseFollowUpSlotDraft>,
    val changeKind: GlucoseFollowUpChangeKind = GlucoseFollowUpChangeKind.CREATED,
    val changeReason: String = "",
    val expectedRevisionNumber: Int = 0,
)

enum class GlucoseFollowUpField {
    INDICATION,
    FREQUENCY,
    WEEKDAYS,
    INTERVAL,
    SLOTS,
    REMINDERS,
    CHANGE_REASON,
}

fun validateGlucoseFollowUpDraft(
    draft: GlucoseFollowUpDraft,
    allowedContexts: List<GlucoseContext> = glucoseMeasurementContexts,
    originalRevision: GlucoseFollowUpRevision? = null,
): Set<GlucoseFollowUpField> = buildSet {
    if (draft.indication.isBlank() || (!fitsTextLimit(draft.indication, CaptureLimit.CARE_INSTRUCTION.maximum, originalRevision?.indication))) {
        add(GlucoseFollowUpField.INDICATION)
    }
    if (!fitsTextLimit(draft.changeReason, CaptureLimit.CHANGE_REASON.maximum, originalRevision?.changeReason)) {
        add(GlucoseFollowUpField.CHANGE_REASON)
    }
    if (draft.frequency == null) {
        add(GlucoseFollowUpField.FREQUENCY)
    }
    if (
        draft.frequency == GlucoseFollowUpFrequency.SPECIFIC_DAYS &&
        draft.weekdays.isEmpty()
    ) {
        add(GlucoseFollowUpField.WEEKDAYS)
    }
    if (
        draft.frequency == GlucoseFollowUpFrequency.EVERY_N_DAYS &&
        draft.everyNDaysInterval !in 1..365
    ) {
        add(GlucoseFollowUpField.INTERVAL)
    }
    if (
        draft.slots.isEmpty() ||
        draft.slots.size > 12 ||
        draft.slots.any { !glucoseFollowUpSlotIsComplete(it) } ||
        draft.slots.any { it.context !in allowedContexts } ||
        glucoseFollowUpSchedulesConflict(draft.slots)
    ) {
        add(GlucoseFollowUpField.SLOTS)
    }
    if (draft.remindersEnabled == null) {
        add(GlucoseFollowUpField.REMINDERS)
    }
}

sealed interface GlucoseFollowUpSaveResult {
    data class Stored(val followUp: GlucoseFollowUp) : GlucoseFollowUpSaveResult
    data class Invalid(
        val fields: Set<GlucoseFollowUpField>,
    ) : GlucoseFollowUpSaveResult
    data object ConsentRequired : GlucoseFollowUpSaveResult
    data object AuthorizationRequired : GlucoseFollowUpSaveResult
}

class GetGlucoseFollowUpUseCase(
    private val repository: GlucoseRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor) = repository.loadFollowUp(actor)
}

class GetGlucoseOccurrenceDecisionsUseCase(
    private val repository: GlucoseRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, followUpId: String) =
        repository.loadOccurrenceDecisions(actor, followUpId)
}

class GetGlucoseOccurrenceDecisionRevisionsUseCase(
    private val repository: GlucoseRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, occurrenceId: String) =
        repository.loadOccurrenceDecisionRevisions(actor, occurrenceId)
}

class SaveGlucoseFollowUpUseCase(
    private val repository: GlucoseRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: GlucoseFollowUpDraft,
    ): GlucoseFollowUpSaveResult {
        if (!actor.isUsableForFollowUp()) {
            return GlucoseFollowUpSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return GlucoseFollowUpSaveResult.ConsentRequired
        }
        val originalRevision = if (draft.expectedRevisionNumber > 0)
            repository.loadFollowUp(actor)?.takeIf { it.id == draft.followUpId }?.currentRevision else null
        val errors = validateGlucoseFollowUpDraft(draft, originalRevision = originalRevision)
        if (errors.isNotEmpty()) return GlucoseFollowUpSaveResult.Invalid(errors)
        val capturedAt = now()
        val revisionNumber = draft.expectedRevisionNumber + 1
        val revision = draft.toRevision(
            actor = actor,
            revisionNumber = revisionNumber,
            capturedAt = capturedAt,
            newId = newId,
        )
        val stored = if (draft.expectedRevisionNumber == 0) {
            repository.createFollowUp(
                actor,
                GlucoseFollowUp(
                    id = draft.followUpId,
                    personUnderCareId = actor.personUnderCareId,
                    createdAt = capturedAt,
                    createdByActorId = actor.caregiverId,
                    revisions = listOf(revision),
                ),
            )
        } else {
            repository.appendFollowUpRevision(
                actor,
                draft.followUpId,
                draft.expectedRevisionNumber,
                revision,
            )
        }
        return GlucoseFollowUpSaveResult.Stored(stored)
    }
}

class ConfirmGlucoseNotPerformedUseCase(
    private val repository: GlucoseRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        occurrence: GlucoseScheduledOccurrence,
        reason: String?,
    ): GlucoseOccurrenceDecision {
        require(actor.isUsableForFollowUp())
        require(reason == null || fitsTextLimit(reason, CaptureLimit.CHANGE_REASON.maximum))
        require(repository.hasSensitiveConsent(actor))
        return repository.confirmOccurrenceDecision(
            actor,
            GlucoseOccurrenceDecision(
                id = newId(),
                followUpId = occurrence.followUpId,
                followUpRevisionId = occurrence.followUpRevisionId,
                occurrenceId = occurrence.id,
                scheduledAt = occurrence.scheduledAt,
                kind = GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
                reason = reason?.trim()?.takeIf(String::isNotEmpty),
                capturedAt = now(),
                actorId = actor.caregiverId,
            ),
        )
    }
}

class CorrectGlucoseNotPerformedUseCase(
    private val repository: GlucoseRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
        reason: String?,
    ): GlucoseOccurrenceDecision {
        require(actor.isUsableForFollowUp())
        require(reason == null || fitsTextLimit(reason, CaptureLimit.CHANGE_REASON.maximum, decision.reason))
        require(repository.hasSensitiveConsent(actor))
        return repository.correctOccurrenceDecision(
            actor,
            GlucoseOccurrenceDecisionRevision(
                id = newId(),
                decisionId = decision.id,
                occurrenceId = decision.occurrenceId,
                revisionNumber = 1,
                operation = GlucoseOccurrenceDecisionRevisionOperation.CORRECTED,
                reason = reason?.trim()?.takeIf(String::isNotEmpty),
                capturedAt = now(),
                actorId = actor.caregiverId,
            ),
        )
    }
}

fun GlucoseFollowUp.toDraft(
    changeKind: GlucoseFollowUpChangeKind,
    effectiveFrom: LocalDate,
): GlucoseFollowUpDraft {
    val revision = currentRevision
    return GlucoseFollowUpDraft(
        followUpId = id,
        indication = revision.indication,
        frequency = revision.frequency,
        weekdays = revision.weekdays,
        everyNDaysInterval = revision.everyNDaysInterval ?: 0,
        effectiveFrom = effectiveFrom,
        remindersEnabled = revision.remindersEnabled,
        slots = revision.slots.map {
            GlucoseFollowUpSlotDraft(
                id = it.id,
                timingType = it.timingType,
                time = it.time,
                windowEnd = it.windowEnd,
                endsNextDay = it.endsNextDay,
                context = it.suggestedContext,
                reminderEnabled = it.reminderEnabled,
                reminderTime = it.reminderTime,
                reminderNextDay = it.reminderNextDay,
                useSameTimeForReminder =
                    if (it.timingType == GlucoseFollowUpTimingType.EXACT_TIME) {
                        it.reminderTime == it.time
                    } else {
                        null
                    },
            )
        },
        changeKind = changeKind,
        expectedRevisionNumber = revision.revisionNumber,
    )
}

internal fun GlucoseFollowUpDraft.toRevision(
    actor: GlucoseActor,
    revisionNumber: Int,
    capturedAt: LocalDateTime,
    newId: () -> String,
) = GlucoseFollowUpRevision(
    id = newId(),
    followUpId = followUpId,
    revisionNumber = revisionNumber,
    indication = indication.trim(),
    frequency = requireNotNull(frequency),
    weekdays = weekdays,
    everyNDaysInterval = everyNDaysInterval.takeIf {
        frequency == GlucoseFollowUpFrequency.EVERY_N_DAYS
    },
    everyNDaysReferenceDate = effectiveFrom.takeIf {
        frequency == GlucoseFollowUpFrequency.EVERY_N_DAYS
    },
    startsOn = effectiveFrom,
    endsOn = null,
    effectiveFrom = effectiveFrom,
    slots = slots.sortedBy { it.time }.map {
        GlucoseFollowUpSlot(
            id = it.id,
            timingType = requireNotNull(it.timingType),
            time = requireNotNull(it.time),
            windowEnd = it.windowEnd,
            endsNextDay = it.endsNextDay,
            suggestedContext = requireNotNull(it.context),
            reminderEnabled = true,
            reminderTime = requireNotNull(it.reminderTime),
            reminderNextDay = it.reminderNextDay,
        )
    },
    remindersEnabled = requireNotNull(remindersEnabled),
    changeKind = changeKind,
    changeReason = changeReason.trim().takeIf(String::isNotEmpty),
    capturedAt = capturedAt,
    authorActorId = actor.caregiverId,
)

fun glucoseFollowUpSlotIsComplete(
    slot: GlucoseFollowUpSlotDraft,
): Boolean = with(slot) {
    val start = time ?: return false
    val reminder = reminderTime ?: return false
    if (context == null || timingType == null) {
        return false
    }
    return when (timingType) {
        GlucoseFollowUpTimingType.EXACT_TIME ->
            windowEnd == null &&
                !endsNextDay &&
                !reminderNextDay &&
                useSameTimeForReminder != null &&
                (!useSameTimeForReminder || reminder == start)
        GlucoseFollowUpTimingType.TIME_WINDOW -> {
            if (useSameTimeForReminder != null) return false
            val end = windowEnd ?: return false
            val validWindow = end != start && (endsNextDay || end > start)
            val reminderMinute = reminder.toSecondOfDay() / 60 +
                if (reminderNextDay) MINUTES_PER_DAY else 0
            val startMinute = start.toSecondOfDay() / 60
            val endMinute = end.toSecondOfDay() / 60 +
                if (endsNextDay) MINUTES_PER_DAY else 0
            validWindow && reminderMinute in startMinute..endMinute
        }
    }
}

fun glucoseFollowUpSchedulesConflict(
    slots: List<GlucoseFollowUpSlotDraft>,
): Boolean {
    if (slots.any { !glucoseFollowUpSlotIsComplete(it) }) return false
    val segments = slots.map { slot -> slot.id to slot.occupiedSegments() }
    return segments.indices.any { first ->
        (first + 1 until segments.size).any { second ->
            segments[first].second.any { left ->
                segments[second].second.any { right ->
                    left.first <= right.last && right.first <= left.last
                }
            }
        }
    }
}

private fun GlucoseFollowUpSlotDraft.occupiedSegments(): List<IntRange> {
    val startMinute = requireNotNull(time).toSecondOfDay() / 60
    if (timingType == GlucoseFollowUpTimingType.EXACT_TIME) {
        return listOf(startMinute..startMinute)
    }
    val endMinute = requireNotNull(windowEnd).toSecondOfDay() / 60
    return if (endsNextDay) {
        listOf(startMinute until MINUTES_PER_DAY, 0..endMinute)
    } else {
        listOf(startMinute..endMinute)
    }
}

private const val MINUTES_PER_DAY = 24 * 60

private fun GlucoseActor.isUsableForFollowUp(): Boolean =
    authorizationCurrent &&
        listOf(profileId, caregiverId, personUnderCareId).none(String::isBlank)
