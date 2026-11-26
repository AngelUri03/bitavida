package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationSchedulingTest {
    private val date = LocalDate.of(2026, 11, 18)
    private val now = date.atTime(16, 0)
    private val actor = GlucoseActor("profile-fictional", "caregiver-fictional", "patient-fictional", true)

    @Test fun confirmationActivatesNowWithoutRetroactivePendingOrAnEndDate() {
        val draft = GlucoseFollowUpDraft(
            followUpId = "follow-up-fictional", indication = "Indicación ficticia",
            frequency = GlucoseFollowUpFrequency.DAILY,
            effectiveFrom = date.plusDays(10), remindersEnabled = false,
            slots = slots().map { slotDraft(it) },
        )
        val revision = observationRevisionForConfirmation(draft, null, actor, now) { "revision-1" }
        val followUp = followUp(revision)
        assertEquals(date, revision.startsOn)
        assertEquals(date, revision.effectiveFrom)
        assertNull(revision.endsOn)
        assertEquals(now, revision.capturedAt)
        assertTrue(events(date.minusDays(1), followUp).isEmpty())
        assertEquals(listOf(LocalTime.of(18, 0)), events(date, followUp).map { it.scheduledAt.toLocalTime() })
        assertEquals(ObservationOccurrenceState.UPCOMING, events(date, followUp).single().state)
        assertEquals(2, events(date.plusDays(30), followUp).size)
    }

    @Test fun historicalValidityIsPreservedUntilAnExplicitConfirmationAndNeverRewritten() {
        val legacy = revision().copy(endsOn = date.minusDays(2))
        val original = followUp(legacy)
        assertTrue(events(date, original).isEmpty())
        val historicalDay = date.minusDays(3)
        val historicalEvents = events(historicalDay, original)
        assertEquals(2, historicalEvents.size)

        val next = observationRevisionForConfirmation(
            original.schedule.toDraft(GlucoseFollowUpChangeKind.INDICATION_CHANGED, date),
            legacy, actor, now,
        ) { "revision-2" }
        val changed = append(original, next)
        assertEquals(legacy, changed.schedule.revisions.first())
        assertEquals(date.minusDays(2), changed.schedule.revisions.first().endsOn)
        assertEquals(historicalEvents, events(historicalDay, changed))
        assertNull(next.endsOn)
        assertEquals(1, events(date, changed).size)
        assertEquals(2, events(date.plusYears(1), changed).size)
    }

    @Test fun confirmingLegacyFutureScheduleStartsNowWithoutChangingItsOriginalRevision() {
        val legacy = revision().copy(
            startsOn = date.plusDays(5), effectiveFrom = date.plusDays(5),
            endsOn = date.plusDays(10),
        )
        val original = followUp(legacy)
        assertTrue(events(date, original).isEmpty())
        val next = observationRevisionForConfirmation(
            original.schedule.toDraft(GlucoseFollowUpChangeKind.CORRECTED, date),
            legacy, actor, now,
        ) { "revision-2" }
        assertEquals(date.plusDays(5), original.schedule.currentRevision.startsOn)
        assertEquals(date.plusDays(10), original.schedule.currentRevision.endsOn)
        assertEquals(date, next.startsOn)
        assertNull(next.endsOn)
        assertEquals(1, events(date, append(original, next)).size)
    }

    @Test fun indicationAndNoticeChangesPreserveTheEstablishedIntervalAnchor() {
        val reference = date.minusDays(5)
        val old = revision().copy(
            frequency = GlucoseFollowUpFrequency.EVERY_N_DAYS,
            everyNDaysInterval = 3, everyNDaysReferenceDate = reference,
        )
        val original = followUp(old)
        val draft = original.schedule.toDraft(GlucoseFollowUpChangeKind.INDICATION_CHANGED, date)
            .copy(indication = "Indicación ficticia actualizada", remindersEnabled = true)
        val next = observationRevisionForConfirmation(draft, old, actor, now) { "revision-2" }
        val changed = append(original, next)
        assertEquals(reference, next.everyNDaysReferenceDate)
        assertTrue(events(date, changed).isEmpty())
        assertEquals(events(date.plusDays(1), original).map { it.id },
            events(date.plusDays(1), changed).map { it.id })
        assertEquals(2, events(date.plusDays(1), changed).size)

        val differentInterval = observationRevisionForConfirmation(
            draft.copy(everyNDaysInterval = 2), old, actor, now,
        ) { "revision-2-other" }
        assertEquals(date, differentInterval.everyNDaysReferenceDate)
    }

    @Test fun futureIntervalAnchorIsResetWhenTheCaregiverConfirmsActivation() {
        val future = date.plusDays(5)
        val legacy = revision().copy(
            startsOn = future, effectiveFrom = future,
            frequency = GlucoseFollowUpFrequency.EVERY_N_DAYS,
            everyNDaysInterval = 3, everyNDaysReferenceDate = future,
        )
        val next = observationRevisionForConfirmation(
            followUp(legacy).schedule.toDraft(GlucoseFollowUpChangeKind.CORRECTED, date),
            legacy, actor, now,
        ) { "revision-2" }
        assertEquals(date, next.everyNDaysReferenceDate)
        assertEquals(1, events(date, append(followUp(legacy), next)).size)
    }

    @Test fun confirmingTextChangesKeepsSlotAndOccurrenceIdentitiesWithoutDuplicatingEvents() {
        val old = revision()
        val original = followUp(old)
        val before = events(date, original)
        val fact = ObservationRecord(
            id = "record-fictional", observationId = "OBS-001", personUnderCareId = actor.personUnderCareId,
            occurredOn = date, occurredAt = LocalTime.of(8, 5), timePrecision = ObservationTimePrecision.EXACT,
            result = ObservationResult.NO_CHANGES, answers = mapOf("foot" to listOf("Ambos")),
            description = "", followUpId = original.schedule.id, occurrenceId = before.first().id,
            scheduledAt = before.first().scheduledAt, createdAt = date.atTime(8, 5), actorId = actor.caregiverId,
        )
        val next = observationRevisionForConfirmation(
            original.schedule.toDraft(GlucoseFollowUpChangeKind.INDICATION_CHANGED, date)
                .copy(indication = "Otra indicación ficticia"),
            old, actor, now,
        ) { "revision-2" }
        val after = projectObservationOccurrences(date, listOf(append(original, next)), listOf(fact), now)
        assertEquals(old.slots.map { it.id }, next.slots.map { it.id })
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(after.size, after.distinctBy { it.id }.size)
        assertEquals(fact, after.first().record)
        assertEquals(ObservationOccurrenceState.RECORDED, after.first().state)
        assertEquals(old.id, after.first().followUpRevisionId)
        assertEquals(next.id, after.last().followUpRevisionId)
    }

    @Test fun deletionPreservesReasonAndPastEventsWhileExplicitReconfigurationDoesNotBackfill() {
        val original = followUp(revision())
        val stopped = observationDeletedRevision(
            original.schedule.currentRevision, "  Motivo ficticio declarado  ", actor, now,
        ) { "revision-2" }
        val suspended = append(original, stopped)
        assertEquals("Motivo ficticio declarado", stopped.changeReason)
        assertEquals(GlucoseFollowUpChangeKind.REMOVED, stopped.changeKind)
        assertEquals(listOf(LocalTime.of(8, 0)), events(date, suspended).map { it.scheduledAt.toLocalTime() })
        assertTrue(events(date.plusDays(1), suspended).isEmpty())
        val resumedAt = date.plusDays(1).atTime(16, 0)
        val resumed = observationReactivatedRevision(stopped, actor, resumedAt) { "revision-3" }
        val active = append(suspended, resumed)
        assertEquals("Reactivación del seguimiento", resumed.changeReason)
        assertNull(resumed.endsOn)
        assertEquals(date.plusDays(1), resumed.startsOn)
        assertEquals(listOf(LocalTime.of(18, 0)),
            projectObservationOccurrences(date.plusDays(1), listOf(active), emptyList(), resumedAt)
                .map { it.scheduledAt.toLocalTime() })
        assertEquals(original.schedule.revisions + stopped, active.schedule.revisions.dropLast(1))
    }

    @Test fun intervalReactivationPreservesItsCycleWithoutFillingTheSuspensionPeriod() {
        val reference = date.minusDays(6)
        val original = followUp(revision().copy(
            frequency = GlucoseFollowUpFrequency.EVERY_N_DAYS,
            everyNDaysInterval = 3, everyNDaysReferenceDate = reference,
        ))
        val stopped = observationDeletedRevision(
            original.schedule.currentRevision, "Pausa ficticia declarada", actor, now,
        ) { "revision-2" }
        val suspended = append(original, stopped)
        val resumedAt = date.plusDays(1).atTime(16, 0)
        val resumed = observationReactivatedRevision(stopped, actor, resumedAt) { "revision-3" }
        val active = append(suspended, resumed)
        assertEquals(reference, resumed.everyNDaysReferenceDate)
        assertEquals(listOf(LocalTime.of(8, 0)),
            projectObservationOccurrences(date, listOf(active), emptyList(), resumedAt)
                .map { it.scheduledAt.toLocalTime() })
        assertTrue(projectObservationOccurrences(date.plusDays(1), listOf(active), emptyList(), resumedAt).isEmpty())
        assertTrue(projectObservationOccurrences(date.plusDays(2), listOf(active), emptyList(), resumedAt).isEmpty())
        assertEquals(events(date.plusDays(3), original).map { it.id },
            projectObservationOccurrences(date.plusDays(3), listOf(active), emptyList(), resumedAt).map { it.id })
    }

    @Test fun reactivatingASuspendedLegacyFutureIntervalActivatesNowAndRetainsItsOldLimitsInHistory() {
        val future = date.plusDays(5)
        val legacy = revision().copy(
            startsOn = future, effectiveFrom = future, endsOn = future.plusDays(3),
            frequency = GlucoseFollowUpFrequency.EVERY_N_DAYS,
            everyNDaysInterval = 3, everyNDaysReferenceDate = future,
        )
        val original = followUp(legacy)
        val stopped = observationDeletedRevision(legacy, "Pausa ficticia declarada", actor, now) { "revision-2" }
        val suspended = append(original, stopped)
        val resumedAt = date.plusDays(1).atTime(16, 0)
        val resumed = observationReactivatedRevision(stopped, actor, resumedAt) { "revision-3" }
        val active = append(suspended, resumed)
        assertEquals(legacy, active.schedule.revisions.first())
        assertEquals(resumedAt.toLocalDate(), resumed.everyNDaysReferenceDate)
        assertEquals(resumedAt.toLocalDate(), resumed.startsOn)
        assertNull(resumed.endsOn)
        assertTrue(projectObservationOccurrences(date, listOf(active), emptyList(), resumedAt).isEmpty())
        assertEquals(listOf(LocalTime.of(18, 0)),
            projectObservationOccurrences(resumedAt.toLocalDate(), listOf(active), emptyList(), resumedAt)
                .map { it.scheduledAt.toLocalTime() })
    }

    @Test fun deletionCancelsALateReminderForAPastSlotWithoutRemovingItsHistory() {
        val original = followUp(revision().copy(
            slots = listOf(slots().first().copy(reminderTime = LocalTime.of(20, 0))),
            remindersEnabled = true,
        ))
        val originalEvents = events(date, original)
        val snapshot = ObservationSnapshot(followUps = listOf(original))
        assertEquals(originalEvents.map { it.id },
            projectObservationReminderOccurrences(snapshot, now, daysAhead = 0).map { it.id })

        val stopped = observationDeletedRevision(
            original.schedule.currentRevision, "Eliminación ficticia declarada", actor, now,
        ) { "revision-2" }
        val suspended = append(original, stopped)
        assertTrue(projectObservationReminderOccurrences(
            snapshot.copy(followUps = listOf(suspended)), now, daysAhead = 0,
        ).isEmpty())
        assertEquals(originalEvents, events(date, suspended))
        assertEquals(ObservationOccurrenceState.UNCONFIRMED, events(date, suspended).single().state)
        assertFalse(observationReminderIsCurrent(
            snapshot.copy(followUps = listOf(suspended)), originalEvents.single(), date.atTime(20, 0),
        ))
    }

    @Test fun aNewConfirmedConfigurationDoesNotResurrectTheReminderCanceledByDeletion() {
        val original = followUp(revision().copy(
            slots = listOf(slots().first().copy(reminderTime = LocalTime.of(20, 0))),
            remindersEnabled = true,
        ))
        val stopped = observationDeletedRevision(
            original.schedule.currentRevision, "Eliminación ficticia declarada", actor, now,
        ) { "revision-2" }
        val resumedAt = now.plusHours(1)
        val template = append(original, stopped).schedule.toDraft(
            GlucoseFollowUpChangeKind.CORRECTED,
            resumedAt.toLocalDate(),
        )
        val resumed = observationRevisionForConfirmation(
            template,
            stopped,
            actor,
            resumedAt,
        ) { "revision-3" }
        val active = append(append(original, stopped), resumed)
        val notices = projectObservationReminderOccurrences(
            ObservationSnapshot(followUps = listOf(active)), resumedAt, daysAhead = 1,
        )
        assertEquals(listOf(date.plusDays(1)), notices.map { it.scheduledAt.toLocalDate() })
        assertFalse(observationReminderIsCurrent(
            ObservationSnapshot(followUps = listOf(active)), events(date, original).single(), date.atTime(20, 0),
        ))
        assertEquals(events(date, original),
            projectObservationOccurrences(date, listOf(active), emptyList(), resumedAt))
    }

    @Test fun indicationOnlyChangesRetainALateNoticeAndChangingItsTimeCancelsTheOldOne() {
        val old = revision().copy(
            slots = listOf(slots().first().copy(reminderTime = LocalTime.of(20, 0))),
            remindersEnabled = true,
        )
        val original = followUp(old)
        val draft = original.schedule.toDraft(GlucoseFollowUpChangeKind.INDICATION_CHANGED, date)
            .copy(indication = "Indicación ficticia actualizada")
        val textOnly = observationRevisionForConfirmation(draft, old, actor, now) { "revision-2" }
        val retained = projectObservationReminderOccurrences(
            ObservationSnapshot(followUps = listOf(append(original, textOnly))), now, daysAhead = 0,
        )
        assertEquals(events(date, original).map { it.id }, retained.map { it.id })
        assertTrue(observationReminderIsCurrent(
            ObservationSnapshot(followUps = listOf(append(original, textOnly))),
            retained.single(), date.atTime(20, 0),
        ))

        val changedTime = observationRevisionForConfirmation(
            draft.copy(slots = draft.slots.map { it.copy(reminderTime = LocalTime.of(19, 0)) }),
            old, actor, now,
        ) { "revision-2-time" }
        val notices = projectObservationReminderOccurrences(
            ObservationSnapshot(followUps = listOf(append(original, changedTime))), now, daysAhead = 1,
        )
        assertEquals(listOf(date.plusDays(1).atTime(19, 0)), notices.map { it.reminderAt })
        assertEquals(events(date, original), events(date, append(original, changedTime)))
    }

    @Test fun reminderProjectionRespectsLegacyLimitsAndTheRequestedHorizon() {
        val legacy = revision().copy(
            startsOn = date.plusDays(2), effectiveFrom = date.plusDays(2), endsOn = date.plusDays(3),
            slots = listOf(slots().first()), remindersEnabled = true,
        )
        val original = followUp(legacy)
        val snapshot = ObservationSnapshot(followUps = listOf(original))
        assertTrue(projectObservationReminderOccurrences(snapshot, now, daysAhead = 1).isEmpty())
        assertEquals(listOf(date.plusDays(2), date.plusDays(3)),
            projectObservationReminderOccurrences(snapshot, now).map { it.scheduledAt.toLocalDate() })
        assertEquals(legacy, snapshot.followUps.single().schedule.currentRevision)
        val ended = followUp(revision().copy(endsOn = date.minusDays(1), remindersEnabled = true))
        assertTrue(projectObservationReminderOccurrences(
            ObservationSnapshot(followUps = listOf(ended)), now,
        ).isEmpty())
    }

    @Test fun midnightReconciliationPreservesThePreviousDaysStillFutureWindowNotice() {
        val overnight = followUp(revision().copy(
            slots = listOf(GlucoseFollowUpSlot(
                id = "slot-overnight-fictional", timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                time = LocalTime.of(22, 0), windowEnd = LocalTime.of(2, 0), endsNextDay = true,
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = true,
                reminderTime = LocalTime.of(1, 0), reminderNextDay = true,
            )),
            remindersEnabled = true,
        ))
        val afterMidnight = date.atTime(0, 30)
        val notices = projectObservationReminderOccurrences(
            ObservationSnapshot(followUps = listOf(overnight)), afterMidnight, daysAhead = 0,
        )
        assertEquals(date.minusDays(1).atTime(22, 0), notices.first().scheduledAt)
        assertEquals(date.atTime(1, 0), notices.first().reminderAt)
        assertEquals(notices.size, notices.distinctBy { it.id }.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun suspensionRequiresTheCaregiversDeclaredReason() {
        observationDeletedRevision(revision(), "   ", actor, now) { "revision-2" }
    }

    @Test(expected = IllegalArgumentException::class)
    fun confirmationRejectsAStaleDraftInsteadOfReplacingAnotherRevision() {
        observationRevisionForConfirmation(
            followUp(revision()).schedule.toDraft(GlucoseFollowUpChangeKind.CORRECTED, date)
                .copy(expectedRevisionNumber = 0),
            revision(), actor, now,
        ) { "revision-2" }
    }

    private fun events(day: LocalDate, followUp: ObservationFollowUp) =
        projectObservationOccurrences(day, listOf(followUp), emptyList(), now)

    private fun append(followUp: ObservationFollowUp, revision: GlucoseFollowUpRevision) =
        followUp.copy(schedule = followUp.schedule.copy(revisions = followUp.schedule.revisions + revision))

    private fun followUp(revision: GlucoseFollowUpRevision) = ObservationFollowUp(
        "OBS-001", GlucoseFollowUp(revision.followUpId, actor.personUnderCareId,
            date.minusDays(10).atTime(7, 0), actor.caregiverId, listOf(revision)),
    )

    private fun revision() = GlucoseFollowUpRevision(
        id = "revision-1", followUpId = "follow-up-fictional", revisionNumber = 1,
        indication = "Indicación ficticia", frequency = GlucoseFollowUpFrequency.DAILY,
        startsOn = date.minusDays(10), effectiveFrom = date.minusDays(10),
        slots = slots(), remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
        capturedAt = date.minusDays(10).atTime(7, 0), authorActorId = actor.caregiverId,
    )

    private fun slots() = listOf(8, 18).map { hour ->
        GlucoseFollowUpSlot(
            id = "slot-fictional-" + hour, time = LocalTime.of(hour, 0),
            suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = true,
            reminderTime = LocalTime.of(hour, 0),
        )
    }

    private fun slotDraft(slot: GlucoseFollowUpSlot) = GlucoseFollowUpSlotDraft(
        id = slot.id, timingType = slot.timingType, time = slot.time,
        context = slot.suggestedContext, reminderEnabled = true, reminderTime = slot.reminderTime,
        useSameTimeForReminder = true,
    )
}
