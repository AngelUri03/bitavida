package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.ObservationOccurrence
import com.bitavida.app.domain.model.ObservationSnapshot
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.validation.CaptureLimit
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Observations activate on explicit confirmation, with no configurable validity.
 * Earlier revisions remain unchanged and continue to reconstruct their own events.
 */
fun observationRevisionForConfirmation(
    draft: GlucoseFollowUpDraft,
    previous: GlucoseFollowUpRevision?,
    actor: GlucoseActor,
    capturedAt: LocalDateTime,
    newId: () -> String,
): GlucoseFollowUpRevision {
    require(previous == null || previous.followUpId == draft.followUpId)
    require(draft.expectedRevisionNumber == (previous?.revisionNumber ?: 0))
    require(draft.changeKind != GlucoseFollowUpChangeKind.REMOVED)
    val activationDate = capturedAt.toLocalDate()
    val revision = draft.copy(effectiveFrom = activationDate).toRevision(
        actor = actor,
        revisionNumber = draft.expectedRevisionNumber + 1,
        capturedAt = capturedAt,
        newId = newId,
    )
    return revision.copy(
        everyNDaysReferenceDate = observationIntervalReference(
            frequency = revision.frequency,
            interval = revision.everyNDaysInterval,
            previous = previous,
            activationDate = activationDate,
        ),
    )
}

/** Explicit reactivation does not backfill slots that preceded this capture. */
fun observationReactivatedRevision(
    previous: GlucoseFollowUpRevision,
    actor: GlucoseActor,
    capturedAt: LocalDateTime,
    newId: () -> String,
): GlucoseFollowUpRevision {
    require(!previous.active)
    val activationDate = capturedAt.toLocalDate()
    return previous.copy(
        id = newId(),
        revisionNumber = previous.revisionNumber + 1,
        startsOn = activationDate,
        endsOn = null,
        effectiveFrom = activationDate,
        everyNDaysReferenceDate = observationIntervalReference(
            frequency = previous.frequency,
            interval = previous.everyNDaysInterval,
            previous = previous,
            activationDate = activationDate,
        ),
        changeKind = GlucoseFollowUpChangeKind.CORRECTED,
        changeReason = "Reactivación del seguimiento",
        capturedAt = capturedAt,
        authorActorId = actor.caregiverId,
    )
}

/** The reason belongs to the appended deletion revision, never to a log. */
fun observationDeletedRevision(
    previous: GlucoseFollowUpRevision,
    reason: String,
    actor: GlucoseActor,
    capturedAt: LocalDateTime,
    newId: () -> String,
): GlucoseFollowUpRevision {
    require(previous.active)
    val declaredReason = reason.trim()
    require(declaredReason.isNotEmpty() && fitsObservationTextCapture(declaredReason, CaptureLimit.CHANGE_REASON.maximum))
    val suspensionDate = capturedAt.toLocalDate()
    return previous.copy(
        id = newId(),
        revisionNumber = previous.revisionNumber + 1,
        startsOn = suspensionDate,
        endsOn = null,
        effectiveFrom = suspensionDate,
        changeKind = GlucoseFollowUpChangeKind.REMOVED,
        changeReason = declaredReason,
        capturedAt = capturedAt,
        authorActorId = actor.caregiverId,
    )
}

/** Backward-compatible name for historical callers; new observation UX uses deletion semantics. */
@Deprecated("Use observationDeletedRevision")
fun observationSuspendedRevision(
    previous: GlucoseFollowUpRevision,
    reason: String,
    actor: GlucoseActor,
    capturedAt: LocalDateTime,
    newId: () -> String,
): GlucoseFollowUpRevision = observationDeletedRevision(previous, reason, actor, capturedAt, newId)

/**
 * Alarm candidates use the same occurrence identities as Mi día and history.
 * A stopped or reconfigured notice never returns merely because the follow-up resumes.
 */
fun projectObservationReminderOccurrences(
    snapshot: ObservationSnapshot,
    now: LocalDateTime,
    daysAhead: Int = 45,
): List<ObservationOccurrence> {
    require(daysAhead in 0..45)
    val eligible = snapshot.followUps.filter {
        it.schedule.currentRevision.active && it.schedule.currentRevision.remindersEnabled
    }
    // Yesterday may still own a future notice for a window crossing midnight.
    return (-1..daysAhead).asSequence().flatMap { offset ->
        projectObservationOccurrences(
            now.toLocalDate().plusDays(offset.toLong()), eligible, snapshot.records, now,
        ).asSequence()
    }.filter { it.reminderAt?.isAfter(now) == true && observationReminderIsCurrent(snapshot, it, now) }
        .sortedBy { it.reminderAt }
        .toList()
}

/** Shared by planning and delivery, without assuming whether the notice is due yet. */
fun observationReminderIsCurrent(
    snapshot: ObservationSnapshot,
    event: ObservationOccurrence,
    now: LocalDateTime,
): Boolean {
    val reminderAt = event.reminderAt ?: return false
    if (event.record != null || snapshot.records.any { it.occurrenceId == event.id }) return false
    val followUp = snapshot.followUps.firstOrNull { it.schedule.id == event.followUpId } ?: return false
    if (followUp.observationId != event.observationId) return false
    val schedule = followUp.schedule
    if (!schedule.currentRevision.active || !schedule.currentRevision.remindersEnabled) return false
    val original = schedule.revisions.firstOrNull { it.id == event.followUpRevisionId } ?: return false
    val originalSlot = original.slots.firstOrNull { it.id == event.slotId } ?: return false
    return schedule.revisions.none { revision ->
        val changedAt = maxOf(revision.effectiveFrom.atStartOfDay(), revision.capturedAt)
        revision.revisionNumber > original.revisionNumber &&
            changedAt <= now && changedAt >= event.scheduledAt && changedAt <= reminderAt &&
            (!revision.active || !revision.remindersEnabled || revision.slots.none { slot ->
                slot.id == originalSlot.id && slot.time == originalSlot.time &&
                    slot.timingType == originalSlot.timingType &&
                    slot.windowEnd == originalSlot.windowEnd &&
                    slot.endsNextDay == originalSlot.endsNextDay &&
                    slot.reminderEnabled && slot.reminderTime == originalSlot.reminderTime &&
                    slot.reminderNextDay == originalSlot.reminderNextDay
            })
    }
}

private fun observationIntervalReference(
    frequency: GlucoseFollowUpFrequency,
    interval: Int?,
    previous: GlucoseFollowUpRevision?,
    activationDate: LocalDate,
): LocalDate? {
    if (frequency != GlucoseFollowUpFrequency.EVERY_N_DAYS) return null
    // Editing text or notices must not shift an established recurrence.
    return previous?.everyNDaysReferenceDate?.takeIf {
        previous.frequency == frequency &&
            previous.everyNDaysInterval == interval &&
            !it.isAfter(activationDate)
    } ?: activationDate
}
