package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseEntryKind
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpTimingType
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.domain.model.GlucoseRecordMetadata
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.occurrencesOn
import com.bitavida.app.domain.model.projectGlucoseToday
import com.bitavida.app.domain.repository.GlucoseActor
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseFollowUpUseCasesTest {
    private val actor = GlucoseActor(
        profileId = "profile",
        caregiverId = "caregiver",
        personUnderCareId = "person",
        authorizationCurrent = true,
    )
    private val now = LocalDateTime.of(2026, 11, 17, 14, 0)

    @Test
    fun createsScheduleAndProjectsUnconfirmedWithoutInferringOmission() = runBlocking {
        val repository = GlucoseFakeRepository()
        val result = SaveGlucoseFollowUpUseCase(
            repository,
            now = { now },
            newId = ids(),
        )(
            actor,
            draft(),
        ) as GlucoseFollowUpSaveResult.Stored

        val occurrences = result.followUp.occurrencesOn(now.toLocalDate())
        val today = projectGlucoseToday(
            now.toLocalDate(),
            result.followUp,
            emptyList(),
            emptyList(),
        )

        assertEquals(2, occurrences.size)
        assertTrue(today.scheduled.all { it.state == GlucoseOccurrenceState.UNCONFIRMED })
    }

    @Test
    fun linksOneReadingAndKeepsAdditionalReadingIndependent() = runBlocking {
        val repository = GlucoseFakeRepository()
        val followUp = (
            SaveGlucoseFollowUpUseCase(repository, { now }, ids())(
                actor,
                draft(),
            ) as GlucoseFollowUpSaveResult.Stored
            ).followUp
        val occurrence = followUp.occurrencesOn(now.toLocalDate()).first()
        val scheduled = record(
            "scheduled",
            occurrence.scheduledAt.plusMinutes(5),
            GlucoseEntryKind.SCHEDULED,
            occurrence.id,
        )
        val additional = record(
            "additional",
            occurrence.scheduledAt.plusMinutes(30),
            GlucoseEntryKind.ADDITIONAL,
            null,
        )

        val today = projectGlucoseToday(
            now.toLocalDate(),
            followUp,
            listOf(scheduled, additional),
            emptyList(),
        )

        assertEquals(GlucoseOccurrenceState.RECORDED, today.scheduled.first().state)
        assertEquals(listOf(additional), today.additionalRecords)
        assertEquals(GlucoseOccurrenceState.UNCONFIRMED, today.scheduled.last().state)
    }

    @Test
    fun explicitNotPerformedIsIdempotentAndAuditable() = runBlocking {
        val repository = GlucoseFakeRepository()
        val followUp = (
            SaveGlucoseFollowUpUseCase(repository, { now }, ids())(
                actor,
                draft(),
            ) as GlucoseFollowUpSaveResult.Stored
            ).followUp
        val occurrence = followUp.occurrencesOn(now.toLocalDate()).first()
        val useCase = ConfirmGlucoseNotPerformedUseCase(repository, { now }, ids())

        useCase(actor, occurrence, "La persona decidió no realizarla")
        useCase(actor, occurrence, "Segundo intento")
        val original = repository.occurrenceDecisions.single()
        val corrected = CorrectGlucoseNotPerformedUseCase(
            repository = repository,
            now = { now.plusMinutes(5) },
            newId = ids(),
        )(
            actor,
            original,
            "Motivo ficticio corregido",
        )

        assertEquals(1, repository.occurrenceDecisions.size)
        assertEquals("Motivo ficticio corregido", corrected.reason)
        assertEquals(
            2,
            repository.loadOccurrenceDecisionRevisions(actor, occurrence.id).size,
        )
        assertEquals(
            GlucoseOccurrenceState.NOT_PERFORMED,
            projectGlucoseToday(
                now.toLocalDate(),
                followUp,
                emptyList(),
                repository.occurrenceDecisions,
            ).scheduled.first().state,
        )
    }

    @Test
    fun removalAppendsRevisionAndPreservesPriorPeriod() = runBlocking {
        val repository = GlucoseFakeRepository()
        val save = SaveGlucoseFollowUpUseCase(repository, { now }, ids())
        val created = (
            save(actor, draft()) as GlucoseFollowUpSaveResult.Stored
            ).followUp
        val removal = created.toDraft(
            GlucoseFollowUpChangeKind.REMOVED,
            now.toLocalDate(),
        ).copy(changeReason = "La indicación terminó")

        val removed = (
            save(actor, removal) as GlucoseFollowUpSaveResult.Stored
            ).followUp

        assertEquals(2, removed.revisions.size)
        assertFalse(removed.currentRevision.active)
        assertTrue(removed.revisions.first().active)
        assertTrue(removed.occurrencesOn(now.toLocalDate()).isEmpty())
    }

    @Test
    fun newDraftDoesNotInventFrequencySchedulesOrReminderPreference() {
        val empty = GlucoseFollowUpDraft(
            followUpId = "new-follow-up",
            effectiveFrom = now.toLocalDate(),
            slots = emptyList(),
        )
        val errors = validateGlucoseFollowUpDraft(empty)

        assertEquals(null, empty.frequency)
        assertTrue(GlucoseFollowUpField.FREQUENCY in errors)
        assertTrue(GlucoseFollowUpField.SLOTS in errors)
        assertTrue(GlucoseFollowUpField.REMINDERS in errors)
    }

    @Test
    fun scheduleRequiresExplicitNoticeTime() {
        val withoutNotice = draft().copy(
            slots = listOf(
                GlucoseFollowUpSlotDraft(
                    id = "slot-without-notice",
                    timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                    time = LocalTime.of(8, 0),
                    context = GlucoseContext.FASTING,
                    useSameTimeForReminder = true,
                ),
            ),
        )

        assertTrue(
            GlucoseFollowUpField.SLOTS in
                validateGlucoseFollowUpDraft(withoutNotice),
        )
    }

    @Test
    fun glucoseFollowUpRejectsPressureSpecificMoments() {
        val pressureOnlyMoment = draft().copy(
            slots = draft().slots.map { slot ->
                slot.copy(context = GlucoseContext.RESTING)
            },
        )

        assertTrue(
            GlucoseFollowUpField.SLOTS in
                validateGlucoseFollowUpDraft(pressureOnlyMoment),
        )
    }

    @Test
    fun windowScheduleKeepsRangeAndNextDayReminder() = runBlocking {
        val repository = GlucoseFakeRepository()
        val windowDraft = draft().copy(
            slots = listOf(
                GlucoseFollowUpSlotDraft(
                    id = "night-window",
                    timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                    time = LocalTime.of(22, 0),
                    windowEnd = LocalTime.of(1, 0),
                    endsNextDay = true,
                    context = GlucoseContext.BEFORE_SLEEP,
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(0, 30),
                    reminderNextDay = true,
                ),
            ),
        )

        val stored = (
            SaveGlucoseFollowUpUseCase(repository, { now }, ids())(
                actor,
                windowDraft,
            ) as GlucoseFollowUpSaveResult.Stored
            ).followUp
        val occurrence = stored.occurrencesOn(now.toLocalDate()).single()

        assertEquals(GlucoseFollowUpTimingType.TIME_WINDOW, occurrence.timingType)
        assertEquals(
            now.toLocalDate().plusDays(1).atTime(1, 0),
            occurrence.windowEndAt,
        )
        assertEquals(
            now.toLocalDate().plusDays(1).atTime(0, 30),
            occurrence.reminderAt,
        )
    }

    @Test
    fun overlappingExactTimeAndWindowAreRejected() {
        val overlapping = draft().copy(
            slots = listOf(
                GlucoseFollowUpSlotDraft(
                    id = "window",
                    timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                    time = LocalTime.of(7, 0),
                    windowEnd = LocalTime.of(9, 0),
                    context = GlucoseContext.FASTING,
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(8, 0),
                ),
                GlucoseFollowUpSlotDraft(
                    id = "exact",
                    timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                    time = LocalTime.of(8, 0),
                    context = GlucoseContext.BEFORE_MEAL,
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(8, 0),
                    useSameTimeForReminder = true,
                ),
            ),
        )

        assertTrue(
            GlucoseFollowUpField.SLOTS in
                validateGlucoseFollowUpDraft(overlapping),
        )
    }

    @Test
    fun changingExactScheduleToWindowAppendsRevisionAndKeepsNotice() = runBlocking {
        val repository = GlucoseFakeRepository()
        val save = SaveGlucoseFollowUpUseCase(repository, { now }, ids())
        val created = (
            save(actor, draft()) as GlucoseFollowUpSaveResult.Stored
            ).followUp
        val originalSlot = created.toDraft(
            GlucoseFollowUpChangeKind.CORRECTED,
            now.toLocalDate(),
        ).slots.first()
        val changed = created.toDraft(
            GlucoseFollowUpChangeKind.CORRECTED,
            now.toLocalDate(),
        ).copy(
            slots = listOf(
                originalSlot.copy(
                    timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                    time = LocalTime.of(6, 30),
                    windowEnd = LocalTime.of(8, 0),
                    endsNextDay = false,
                    reminderTime = LocalTime.of(7, 15),
                    reminderNextDay = false,
                    useSameTimeForReminder = null,
                ),
            ),
        )

        val stored = (
            save(actor, changed) as GlucoseFollowUpSaveResult.Stored
            ).followUp

        assertEquals(2, stored.revisions.size)
        assertEquals(
            GlucoseFollowUpTimingType.EXACT_TIME,
            stored.revisions.first().slots.first().timingType,
        )
        with(stored.currentRevision.slots.single()) {
            assertEquals(GlucoseFollowUpTimingType.TIME_WINDOW, timingType)
            assertEquals(LocalTime.of(6, 30), time)
            assertEquals(LocalTime.of(8, 0), windowEnd)
            assertEquals(LocalTime.of(7, 15), reminderTime)
            assertEquals(GlucoseContext.FASTING, suggestedContext)
        }
    }

    @Test
    fun editingFrequencyAndSchedulesAppendsCompleteRevision() = runBlocking {
        val repository = GlucoseFakeRepository()
        val save = SaveGlucoseFollowUpUseCase(repository, { now }, ids())
        val created = (
            save(actor, draft()) as GlucoseFollowUpSaveResult.Stored
            ).followUp
        val edited = created.toDraft(
            GlucoseFollowUpChangeKind.CORRECTED,
            now.toLocalDate(),
        ).copy(
            frequency = GlucoseFollowUpFrequency.SPECIFIC_DAYS,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            remindersEnabled = false,
            slots = listOf(
                GlucoseFollowUpSlotDraft(
                    id = "edited-slot",
                    timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                    time = LocalTime.of(9, 15),
                    context = GlucoseContext.AFTER_MEAL,
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(9, 0),
                    useSameTimeForReminder = false,
                ),
            ),
        )

        val stored = (
            save(actor, edited) as GlucoseFollowUpSaveResult.Stored
            ).followUp

        assertEquals(2, stored.revisions.size)
        assertEquals(
            GlucoseFollowUpFrequency.SPECIFIC_DAYS,
            stored.currentRevision.frequency,
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), stored.currentRevision.weekdays)
        assertEquals(LocalTime.of(9, 15), stored.currentRevision.slots.single().time)
        assertEquals(LocalTime.of(9, 0), stored.currentRevision.slots.single().reminderTime)
        assertTrue(stored.currentRevision.slots.single().reminderEnabled)
        assertFalse(stored.currentRevision.remindersEnabled)
        assertEquals(null, stored.currentRevision.changeReason)
        assertEquals(now.toLocalDate(), stored.currentRevision.startsOn)
        assertEquals(null, stored.currentRevision.endsOn)
    }

    private fun draft() = GlucoseFollowUpDraft(
        followUpId = "follow-up",
        indication = "Medir según indicación escrita ficticia",
        frequency = GlucoseFollowUpFrequency.DAILY,
        effectiveFrom = now.toLocalDate(),
        remindersEnabled = true,
        slots = listOf(
            GlucoseFollowUpSlotDraft(
                id = "morning",
                timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                time = LocalTime.of(7, 0),
                context = GlucoseContext.FASTING,
                reminderEnabled = true,
                reminderTime = LocalTime.of(7, 0),
                useSameTimeForReminder = true,
            ),
            GlucoseFollowUpSlotDraft(
                id = "night",
                timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                time = LocalTime.of(22, 0),
                context = GlucoseContext.BEFORE_SLEEP,
                reminderEnabled = true,
                reminderTime = LocalTime.of(22, 0),
                useSameTimeForReminder = true,
            ),
        ),
    )

    private fun record(
        id: String,
        occurredAt: LocalDateTime,
        kind: GlucoseEntryKind,
        occurrenceId: String?,
    ) = MeasurementRecord(
        id = id,
        value = MeasurementValue.Glucose("112", GlucoseContext.FASTING),
        occurredAt = occurredAt,
        capturedAt = now,
        glucoseMetadata = GlucoseRecordMetadata(
            entryKind = kind,
            followUpId = "follow-up",
            scheduledOccurrenceId = occurrenceId,
        ),
    )

    private fun ids(): () -> String {
        var next = 0
        return { "id-" + next++ }
    }
}
