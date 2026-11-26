package com.bitavida.app.ui.observations

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.ObservationRepository
import com.bitavida.app.domain.usecase.projectObservationOccurrences
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ObservationsViewModelTest {
    private val now = LocalDateTime.of(2026, 11, 18, 14, 0)
    private val actor = GlucoseActor("profile-fictitious", "caregiver-fictitious", "person-fictitious", true)

    @Test fun followUpFilterKeepsSearchAndCollapsesCategoriesWhenDisabled() = runBlocking {
        val vm = ObservationsViewModel(FakeRepository(), actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Query("piel"))
        vm.onAction(ObservationsAction.ToggleCategory(ObservationCategory.FEET))
        vm.onAction(ObservationsAction.ToggleFollowUpFilter)
        assertTrue(vm.state.value.onlyWithFollowUp)
        assertEquals("piel", vm.state.value.query)
        vm.onAction(ObservationsAction.ToggleCategory(ObservationCategory.FEET))
        vm.onAction(ObservationsAction.ToggleFollowUpFilter)
        assertFalse(vm.state.value.onlyWithFollowUp)
        assertTrue(vm.state.value.expanded.isEmpty())
        assertEquals("piel", vm.state.value.query)
    }

    @Test fun spontaneousSavePersistsRealFactWithoutCreatingSchedule() = runBlocking {
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Register("OBS-027"))
        fill(vm)
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertNull(vm.state.value.draft)
        assertEquals(1, repository.snapshot.records.size)
        assertEquals(now.toLocalDate(), repository.snapshot.records.single().occurredOn)
        assertTrue(repository.snapshot.followUps.isEmpty())
        assertEquals(repository.snapshot, vm.state.value.snapshot)
    }

    @Test fun unicodeSearchAndCorrectionReasonsRespectTheirOwnLimits() = runBlocking {
        val vm = ObservationsViewModel(FakeRepository(), actor, this, { now })
        yield()
        val family = "👩‍👩‍👧‍👦"
        vm.onAction(ObservationsAction.Query(family.repeat(101)))
        assertEquals(family.repeat(100), vm.state.value.query)
        vm.onAction(ObservationsAction.CorrectionReason(family.repeat(300)))
        assertEquals(family.repeat(300), vm.state.value.correctionReason)
        vm.onAction(ObservationsAction.CorrectionReason(family.repeat(301)))
        assertEquals(family.repeat(300), vm.state.value.correctionReason)
        vm.onAction(ObservationsAction.CorrectionReason("a" + "\u0301".repeat(32_768)))
        assertEquals(family.repeat(300), vm.state.value.correctionReason)
    }

    @Test fun oversizeNewDescriptionIsRejectedBeforePersistenceAndRemainsAvailableForCorrection() = runBlocking {
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Register("OBS-027"))
        val draft = vm.state.value.draft!!.copy(description = "x".repeat(501))
        vm.onAction(ObservationsAction.ChangeDraft(draft))
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertTrue(repository.snapshot.records.isEmpty())
        assertEquals(draft.description, vm.state.value.draft?.description)
        assertTrue("description" in vm.state.value.errors)
    }

    @Test fun historicalLongTextSurvivesCorrectionOfOtherFields() = runBlocking {
        val original = fictionalRecord().copy(description = "Hecho histórico ".repeat(80),
            answers = mapOf("zone" to listOf("Zona histórica ".repeat(20))))
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(records = listOf(original)) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.CorrectRecord(original.id))
        vm.onAction(ObservationsAction.ChangeDraft(vm.state.value.draft!!.copy(occurredOn = now.toLocalDate().minusDays(1))))
        vm.onAction(ObservationsAction.CorrectionReason("Se corrigió la fecha declarada"))
        vm.onAction(ObservationsAction.Save)
        repeat(4) { yield() }
        assertNull(vm.state.value.draft)
        assertEquals(original.description, repository.snapshot.records.single().description)
        assertEquals(original.answers, repository.snapshot.records.single().answers)
        assertEquals(original, repository.snapshot.recordRevisions.first().snapshot)
    }

    @Test fun failedWritePreservesStableDraftAndRetryDoesNotDuplicate() = runBlocking {
        val repository = FakeRepository().apply { failSave = true }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Register("OBS-027"))
        fill(vm)
        val original = vm.state.value.draft
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertEquals(original, vm.state.value.draft)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.saving)
        repository.failSave = false
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertEquals(listOf(original!!.id, original.id), repository.attemptedIds)
        assertEquals(1, repository.snapshot.records.size)
        assertNull(vm.state.value.draft)
    }

    @Test fun doubleSaveAndBackDuringCommitKeepOneWriteAndDraft() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply { beforeSave = { release.await() } }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Register("OBS-027"))
        fill(vm)
        vm.onAction(ObservationsAction.Save)
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertTrue(vm.state.value.saving)
        vm.onAction(ObservationsAction.CloseDraft)
        assertNotNull(vm.state.value.draft)
        assertEquals(1, repository.attemptedIds.size)
        release.complete(Unit)
        repeat(3) { yield() }
        assertNull(vm.state.value.draft)
        assertEquals(1, repository.snapshot.records.size)
    }

    @Test fun sensitiveConsentIsRequestedOnlyOnExplicitWriteAndRetainsDraft() = runBlocking {
        val repository = FakeRepository().apply { consent = false }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        assertFalse(vm.state.value.consentVisible)
        vm.onAction(ObservationsAction.Register("OBS-027"))
        fill(vm)
        val draftId = vm.state.value.draft!!.id
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertTrue(vm.state.value.consentVisible)
        assertTrue(repository.attemptedIds.isEmpty())
        vm.onAction(ObservationsAction.DismissConsent)
        assertEquals(draftId, vm.state.value.draft!!.id)
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        vm.onAction(ObservationsAction.AcceptConsent(true))
        vm.onAction(ObservationsAction.ConfirmConsent)
        repeat(4) { yield() }
        assertEquals(1, repository.consentAccepts)
        assertEquals(listOf(draftId), repository.snapshot.records.map { it.id })
        assertFalse(vm.state.value.consentVisible)
    }

    @Test fun scheduledReviewKeepsExactAssociationAndRealTimeIndependentOfReminder() = runBlocking {
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(followUps = listOf(followUp())) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        val occurrence = projectObservationOccurrences(now.toLocalDate(), repository.snapshot.followUps, emptyList(), now).single()
        vm.openScheduled(occurrence)
        val draft = vm.state.value.draft!!
        assertEquals(now.toLocalTime(), draft.occurredAt)
        assertEquals(LocalTime.of(8, 0), draft.scheduledAt!!.toLocalTime())
        vm.onAction(ObservationsAction.ChangeDraft(draft.copy(result = ObservationResult.NO_CHANGES,
            answers = mapOf("foot" to listOf("Ambos")))))
        vm.onAction(ObservationsAction.Save)
        repeat(4) { yield() }
        assertEquals(occurrence.id, repository.snapshot.records.single().occurrenceId)
        assertEquals(ObservationResult.NO_CHANGES, repository.snapshot.records.single().result)
        assertEquals(1, repository.snapshot.followUps.size)
    }

    @Test fun guideReturnsToTheSameDraftAndPendingHistoryUsesTheExactOccurrence() = runBlocking {
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(followUps = listOf(followUp())) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()

        vm.onAction(ObservationsAction.Register("OBS-001"))
        val draftId = vm.state.value.draft!!.id
        vm.onAction(ObservationsAction.OpenGuide)
        assertEquals(ObservationsPage.GUIDE, vm.state.value.page)
        vm.onAction(ObservationsAction.Back)
        assertEquals(ObservationsPage.REGISTER, vm.state.value.page)
        assertEquals(draftId, vm.state.value.draft!!.id)
        vm.onAction(ObservationsAction.CloseDraft)

        val occurrence = projectObservationOccurrences(
            now.toLocalDate(),
            repository.snapshot.followUps,
            emptyList(),
            now,
        ).single()
        vm.onAction(ObservationsAction.OpenOccurrence(occurrence))
        assertEquals(ObservationsPage.OCCURRENCE_DETAIL, vm.state.value.page)
        assertEquals(occurrence.id, vm.state.value.selectedOccurrence!!.id)
        vm.onAction(ObservationsAction.NotPerformed(occurrence))
        assertEquals(occurrence.id, vm.state.value.draft!!.occurrenceId)
        assertEquals(ObservationResult.NOT_PERFORMED, vm.state.value.draft!!.result)
        vm.onAction(ObservationsAction.Save)
        repeat(4) { yield() }
        assertEquals(ObservationsPage.OCCURRENCE_DETAIL, vm.state.value.page)
        assertEquals(listOf(occurrence.id), repository.snapshot.records.mapNotNull { it.occurrenceId })

        vm.onAction(ObservationsAction.NotPerformed(occurrence))
        assertEquals(ObservationsPage.RECORD_DETAIL, vm.state.value.page)
        assertEquals(1, repository.snapshot.records.size)
    }

    @Test fun deletionKeepsHistoryAndReconfigurationUsesAnInactiveEditableTemplate() = runBlocking {
        val fact = ObservationRecord("fact", "OBS-027", actor.personUnderCareId, now.toLocalDate(),
            now.toLocalTime(), ObservationTimePrecision.EXACT, ObservationResult.CHANGES, emptyMap(),
            "Detalle ficticio", createdAt = now, actorId = actor.caregiverId)
        val repository = FakeRepository().apply {
            snapshot = ObservationSnapshot(listOf(fact), listOf(followUp()))
        }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.RequestSuspend("OBS-001"))
        vm.onAction(ObservationsAction.SuspendReason("La revisión fue suspendida en este ejemplo ficticio"))
        vm.onAction(ObservationsAction.ConfirmSuspend)
        repeat(4) { yield() }
        assertFalse(vm.state.value.snapshot.followUps.single().schedule.currentRevision.active)
        assertEquals("La revisión fue suspendida en este ejemplo ficticio",
            vm.state.value.snapshot.followUps.single().schedule.currentRevision.changeReason)
        assertNull(vm.state.value.suspensionObservationId)
        assertEquals(2, vm.state.value.snapshot.followUps.single().schedule.revisions.size)
        vm.onAction(ObservationsAction.Configure("OBS-001"))
        val template = requireNotNull(vm.state.value.scheduleDraft).schedule
        assertEquals("Indicación ficticia de revisión", template.indication)
        assertEquals(GlucoseFollowUpFrequency.DAILY, template.frequency)
        assertEquals(listOf(LocalTime.of(8, 0)), template.slots.mapNotNull { it.time })
        assertEquals(false, template.remindersEnabled)
        assertFalse(vm.state.value.snapshot.followUps.single().schedule.currentRevision.active)
        vm.onAction(ObservationsAction.SaveSchedule)
        repeat(4) { yield() }
        val schedule = vm.state.value.snapshot.followUps.single().schedule
        assertTrue(schedule.currentRevision.active)
        assertEquals(3, schedule.revisions.size)
        assertEquals(listOf(fact), vm.state.value.snapshot.records)
        assertEquals(listOf(1, 2), repository.expectedRevisions)
    }

    @Test fun uncertainFollowUpCommitRetriesExactRevisionIdentity() = runBlocking {
        val repository = FakeRepository().apply {
            snapshot = ObservationSnapshot(followUps = listOf(followUp()))
            failAfterFollowUpCommit = true
        }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.RequestSuspend("OBS-001"))
        vm.onAction(ObservationsAction.SuspendReason("Motivo ficticio"))
        vm.onAction(ObservationsAction.ConfirmSuspend)
        repeat(4) { yield() }
        assertNotNull(vm.state.value.suspensionError)
        assertEquals("Motivo ficticio", vm.state.value.suspensionReason)
        assertNotNull(vm.state.value.error)
        assertEquals(1, vm.state.value.snapshot.followUps.single().schedule.revisions.size)
        assertEquals(2, repository.snapshot.followUps.single().schedule.revisions.size)
        vm.onAction(ObservationsAction.ConfirmSuspend)
        repeat(4) { yield() }
        assertNull(vm.state.value.suspensionObservationId)
        assertEquals(2, vm.state.value.snapshot.followUps.single().schedule.revisions.size)
        assertEquals(1, repository.followUpRevisionIds.distinct().size)
        assertEquals(listOf(1, 1), repository.expectedRevisions)
    }

    @Test fun loadErrorIsVisibleAndDoesNotErasePreviouslyLoadedData() = runBlocking {
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(followUps = listOf(followUp())) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        val loaded = vm.state.value.snapshot
        repository.failLoad = true
        vm.onAction(ObservationsAction.Reload)
        yield()
        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.loadError)
        assertEquals(loaded, vm.state.value.snapshot)
        repository.failLoad = false
        vm.onAction(ObservationsAction.Reload)
        yield()
        assertNull(vm.state.value.loadError)
    }

    @Test fun invalidFormRetainsInputWithoutWriting() = runBlocking {
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Register("OBS-027"))
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertTrue(vm.state.value.errors.containsKey("description"))
        assertNotNull(vm.state.value.draft)
        assertTrue(repository.attemptedIds.isEmpty())
    }

    @Test fun allCatalogEntriesOpenDetailBeforeOpeningRegistration() = runBlocking {
        val vm = ObservationsViewModel(FakeRepository(), actor, this, { now })
        yield()
        ObservationCatalog.all.forEach { definition ->
            vm.onAction(ObservationsAction.Open(definition.id))
            assertEquals(ObservationsPage.DETAIL, vm.state.value.page)
            assertEquals(definition.id, vm.state.value.selectedObservationId)
            assertNull(vm.state.value.draft)
            vm.onAction(ObservationsAction.Register(definition.id))
            assertEquals(ObservationsPage.REGISTER, vm.state.value.page)
            assertEquals(definition.id, vm.state.value.draft!!.observationId)
            vm.onAction(ObservationsAction.CloseDraft)
            assertEquals(ObservationsPage.DETAIL, vm.state.value.page)
            vm.onAction(ObservationsAction.Back)
            assertEquals(ObservationsPage.LIBRARY, vm.state.value.page)
        }
    }

    @Test fun collapsedSearchAndCategorySelectionPreserveLibraryNavigation() = runBlocking {
        val vm = ObservationsViewModel(FakeRepository(), actor, this, { now })
        yield()
        assertFalse(vm.state.value.searchExpanded)
        assertTrue(vm.state.value.expanded.isEmpty())
        vm.onAction(ObservationsAction.ToggleSearch)
        vm.onAction(ObservationsAction.Query("Se le duermen los pies"))
        assertTrue(vm.state.value.searchExpanded)
        assertEquals("Se le duermen los pies", vm.state.value.query)
        val first = ObservationCategory.entries.first()
        val second = ObservationCategory.entries[1]
        vm.onAction(ObservationsAction.ToggleCategory(first))
        assertEquals(setOf(first), vm.state.value.expanded)
        vm.onAction(ObservationsAction.ToggleCategory(second))
        assertEquals(setOf(second), vm.state.value.expanded)
        vm.onAction(ObservationsAction.Open("OBS-001"))
        vm.onAction(ObservationsAction.Back)
        assertTrue(vm.state.value.expanded.isEmpty())
        vm.onAction(ObservationsAction.ToggleCategory(first))
        vm.onAction(ObservationsAction.EnterCatalog)
        assertEquals(ObservationsPage.LIBRARY, vm.state.value.page)
        assertTrue(vm.state.value.expanded.isEmpty())
        assertEquals("", vm.state.value.query)
        assertFalse(vm.state.value.searchExpanded)
        vm.onAction(ObservationsAction.ToggleSearch)
        assertTrue(vm.state.value.searchExpanded)
    }

    @Test fun historyAndRecordDetailReturnToTheirActualParent() = runBlocking {
        val record = fictionalRecord()
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(records = listOf(record)) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Open("OBS-001"))
        vm.onAction(ObservationsAction.History)
        vm.onAction(ObservationsAction.Date(now.toLocalDate().minusDays(3)))
        vm.onAction(ObservationsAction.ViewRecord(record.id))
        assertEquals(ObservationsPage.RECORD_DETAIL, vm.state.value.page)
        vm.onAction(ObservationsAction.Back)
        assertEquals(ObservationsPage.HISTORY, vm.state.value.page)
        assertEquals(now.toLocalDate().minusDays(3), vm.state.value.selectedDate)
        vm.onAction(ObservationsAction.Back)
        assertEquals(ObservationsPage.DETAIL, vm.state.value.page)
        assertEquals("OBS-001", vm.state.value.selectedObservationId)
        vm.onAction(ObservationsAction.Changes(record.observationId))
        assertEquals(ObservationsPage.FOLLOW_UP_HISTORY, vm.state.value.page)
        assertTrue(vm.state.value.snapshot.followUps.isEmpty())
        vm.onAction(ObservationsAction.Back)
        assertEquals(ObservationsPage.DETAIL, vm.state.value.page)
    }

    @Test fun guidedScheduleRequiresEachStepAndPreservesSlotsOnIndicationChange() = runBlocking {
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Configure("OBS-001"))
        vm.onAction(ObservationsAction.NextSchedule)
        assertEquals(0, vm.state.value.scheduleStep)
        assertNotNull(vm.state.value.error)
        var draft = vm.state.value.scheduleDraft!!
        vm.onAction(ObservationsAction.Schedule(draft.copy(schedule = draft.schedule.copy(indication = "Indicación ficticia"))))
        vm.onAction(ObservationsAction.NextSchedule)
        assertEquals(1, vm.state.value.scheduleStep)
        draft = vm.state.value.scheduleDraft!!
        vm.onAction(ObservationsAction.Schedule(draft.copy(schedule = draft.schedule.copy(frequency = GlucoseFollowUpFrequency.DAILY))))
        vm.onAction(ObservationsAction.NextSchedule)
        assertEquals(2, vm.state.value.scheduleStep)
        draft = vm.state.value.scheduleDraft!!
        val slot = com.bitavida.app.domain.usecase.GlucoseFollowUpSlotDraft(
            id = "fictional-slot", timingType = GlucoseFollowUpTimingType.EXACT_TIME,
            time = LocalTime.of(16, 0), context = GlucoseContext.UNSPECIFIED,
            reminderEnabled = true, reminderTime = LocalTime.of(16, 0), useSameTimeForReminder = true,
        )
        vm.onAction(ObservationsAction.Schedule(draft.copy(schedule = draft.schedule.copy(slots = listOf(slot)))))
        vm.onAction(ObservationsAction.NextSchedule)
        assertEquals(3, vm.state.value.scheduleStep)
        vm.onAction(ObservationsAction.NextSchedule)
        assertEquals(3, vm.state.value.scheduleStep)
        draft = vm.state.value.scheduleDraft!!
        vm.onAction(ObservationsAction.Schedule(draft.copy(schedule = draft.schedule.copy(remindersEnabled = false))))
        vm.onAction(ObservationsAction.NextSchedule)
        assertEquals(4, vm.state.value.scheduleStep)
        vm.onAction(ObservationsAction.SaveSchedule)
        repeat(4) { yield() }
        assertEquals(ObservationsPage.DETAIL, vm.state.value.page)
        val todayOccurrences = projectObservationOccurrences(
            now.toLocalDate(),
            vm.state.value.snapshot.followUps,
            vm.state.value.snapshot.records,
            now,
        )
        assertEquals(1, todayOccurrences.size)
        assertEquals(todayOccurrences.size, todayOccurrences.distinctBy { it.id }.size)
        vm.onAction(ObservationsAction.Configure("OBS-001", indicationChange = true))
        draft = vm.state.value.scheduleDraft!!
        vm.onAction(ObservationsAction.Schedule(draft.copy(schedule = draft.schedule.copy(indication = "Indicación ficticia actualizada"))))
        vm.onAction(ObservationsAction.SaveSchedule)
        repeat(4) { yield() }
        val followUp = vm.state.value.snapshot.followUps.single()
        assertEquals(2, followUp.schedule.revisions.size)
        assertEquals(GlucoseFollowUpChangeKind.INDICATION_CHANGED, followUp.schedule.currentRevision.changeKind)
        assertEquals(listOf("fictional-slot"), followUp.schedule.currentRevision.slots.map { it.id })
        assertEquals(1, projectObservationOccurrences(now.toLocalDate().plusDays(1),
            listOf(followUp), emptyList(), now).size)
    }

    @Test fun committedFollowUpProjectsTodayBeforeThePeriodicClockTicks() = runBlocking {
        val staleScreenClock = now
        val commitClock = now.plusSeconds(30)
        val currentClock = commitClock
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this) { currentClock }
        yield()

        vm.onAction(ObservationsAction.Configure("OBS-020"))
        val initial = requireNotNull(vm.state.value.scheduleDraft)
        val slots = listOf(
            com.bitavida.app.domain.usecase.GlucoseFollowUpSlotDraft(
                id = "fictional-afternoon", timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                time = LocalTime.of(16, 0), context = GlucoseContext.UNSPECIFIED,
                reminderEnabled = false, reminderTime = LocalTime.of(16, 0),
                useSameTimeForReminder = true,
            ),
            com.bitavida.app.domain.usecase.GlucoseFollowUpSlotDraft(
                id = "fictional-evening", timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                time = LocalTime.of(19, 0), windowEnd = LocalTime.of(20, 0),
                context = GlucoseContext.UNSPECIFIED, reminderEnabled = false,
                reminderTime = LocalTime.of(19, 0), useSameTimeForReminder = null,
            ),
        )
        vm.onAction(ObservationsAction.Schedule(initial.copy(schedule = initial.schedule.copy(
            indication = "Revisar cambios de ánimo según indicación ficticia",
            frequency = GlucoseFollowUpFrequency.DAILY,
            slots = slots,
            remindersEnabled = false,
        ))))
        vm.onAction(ObservationsAction.SaveSchedule)
        repeat(4) { yield() }

        val committed = vm.state.value
        assertEquals(ObservationsPage.DETAIL, committed.page)
        assertEquals(commitClock, committed.snapshotObservedAt)
        assertTrue(
            "The former minute-tick clock reproduces the false empty state",
            projectObservationOccurrences(
                now.toLocalDate(), committed.snapshot.followUps, committed.snapshot.records, staleScreenClock,
            ).isEmpty(),
        )

        val projectionNow = committed.observationProjectionNow(staleScreenClock)
        assertEquals(commitClock, projectionNow)
        val projected = projectObservationOccurrences(
            projectionNow.toLocalDate(), committed.snapshot.followUps, committed.snapshot.records, projectionNow,
        )
        assertEquals(listOf(LocalTime.of(16, 0), LocalTime.of(19, 0)), projected.map { it.scheduledAt.toLocalTime() })
        assertEquals(projected.size, projected.distinctBy { it.id }.size)
    }

    @Test fun committedSpecificDayWithoutTodayOccurrenceRemainsTrulyEmpty() = runBlocking {
        val commitClock = now.plusSeconds(30)
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this) { commitClock }
        yield()

        vm.onAction(ObservationsAction.Configure("OBS-020"))
        val initial = requireNotNull(vm.state.value.scheduleDraft)
        val slot = com.bitavida.app.domain.usecase.GlucoseFollowUpSlotDraft(
            id = "fictional-next-day", timingType = GlucoseFollowUpTimingType.EXACT_TIME,
            time = LocalTime.of(16, 0), context = GlucoseContext.UNSPECIFIED,
            reminderEnabled = false, reminderTime = LocalTime.of(16, 0),
            useSameTimeForReminder = true,
        )
        vm.onAction(ObservationsAction.Schedule(initial.copy(schedule = initial.schedule.copy(
            indication = "Revisar según indicación ficticia",
            frequency = GlucoseFollowUpFrequency.SPECIFIC_DAYS,
            weekdays = setOf(commitClock.toLocalDate().plusDays(1).dayOfWeek),
            slots = listOf(slot),
            remindersEnabled = false,
        ))))
        vm.onAction(ObservationsAction.SaveSchedule)
        repeat(4) { yield() }

        val committed = vm.state.value
        val projectionNow = committed.observationProjectionNow(now)
        assertTrue(projectObservationOccurrences(
            projectionNow.toLocalDate(), committed.snapshot.followUps, committed.snapshot.records, projectionNow,
        ).isEmpty())
        assertEquals(1, projectObservationOccurrences(
            projectionNow.toLocalDate().plusDays(1),
            committed.snapshot.followUps,
            committed.snapshot.records,
            projectionNow,
        ).size)
    }

    @Test fun unfinishedScheduleEditorIsRetainedAndBackClosesItBeforeLeavingTheStep() = runBlocking {
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Configure("OBS-001", startStep = 2))
        val captured = com.bitavida.app.domain.usecase.GlucoseFollowUpSlotDraft(
            "fictional-unfinished-slot", timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
            time = LocalTime.of(10, 0), context = GlucoseContext.UNSPECIFIED,
        )
        vm.onAction(ObservationsAction.ScheduleSlotEditor(captured))
        vm.onAction(ObservationsAction.NextSchedule)
        assertEquals(2, vm.state.value.scheduleStep)
        assertEquals(captured, vm.state.value.scheduleSlotEditor)
        assertNotNull(vm.state.value.error)
        vm.onAction(ObservationsAction.Back)
        assertNull(vm.state.value.scheduleSlotEditor)
        assertEquals(2, vm.state.value.scheduleStep)
        assertTrue(repository.snapshot.followUps.isEmpty())
        vm.onAction(ObservationsAction.Back)
        assertEquals(1, vm.state.value.scheduleStep)
    }

    @Test fun photoRequestKeepsSourceDraftAndReplacementIdentityUntilTheSystemReturns() = runBlocking {
        val vm = ObservationsViewModel(FakeRepository(), actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Register("OBS-002"))
        val existing = vm.state.value.draft!!.copy(localPhotoIds = listOf("fictional-photo"))
        vm.onAction(ObservationsAction.ChangeDraft(existing))
        val request = async { vm.photoRequests.first() }
        vm.requestPhoto(ObservationPhotoSource.CAMERA, replacingPhotoId = "fictional-photo")
        yield()
        assertEquals(
            ObservationPhotoRequest(
                ObservationPhotoSource.CAMERA,
                existing.id,
                replacingPhotoId = "fictional-photo",
            ),
            request.await(),
        )
        vm.onAction(ObservationsAction.PhotoPreparing(true))
        vm.onAction(ObservationsAction.PhotoFailed("Falla ficticia"))
        assertFalse(vm.state.value.photoPreparing)
        assertEquals(existing, vm.state.value.draft)
        assertEquals("Falla ficticia", vm.state.value.error)
    }

    @Test fun descriptionAssistanceRequiresExplicitApplicationAndNeverSaves() = runBlocking {
        val repository = FakeRepository()
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Register("OBS-002"))
        vm.onAction(ObservationsAction.ChangeDraft(vm.state.value.draft!!.copy(
            answers = mapOf("zone" to listOf("pie"), "side" to listOf("Izquierdo"), "lesion" to listOf("Ampolla")))))
        vm.onAction(ObservationsAction.HelpDescribe)
        assertTrue(vm.state.value.descriptionProposal!!.contains("Ampolla", ignoreCase = true))
        assertEquals("", vm.state.value.draft!!.description)
        assertTrue(repository.snapshot.records.isEmpty())
        vm.onAction(ObservationsAction.ApplyDescription)
        assertTrue(vm.state.value.draft!!.description.contains("Ampolla", ignoreCase = true))
        assertNull(vm.state.value.descriptionProposal)
        assertTrue(repository.snapshot.records.isEmpty())
    }

    @Test fun correctingNotPerformedRetainsOriginalAndExactScheduledIdentity() = runBlocking {
        val schedule = followUp()
        val occurrence = projectObservationOccurrences(now.toLocalDate(), listOf(schedule), emptyList(), now).single()
        val original = fictionalRecord().copy(observationId = "OBS-001", result = ObservationResult.NOT_PERFORMED,
            followUpId = occurrence.followUpId, occurrenceId = occurrence.id, scheduledAt = occurrence.scheduledAt)
        val repository = FakeRepository().apply {
            snapshot = ObservationSnapshot(records = listOf(original), followUps = listOf(schedule))
        }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.ViewRecord(original.id))
        vm.onAction(ObservationsAction.CorrectRecord(original.id))
        vm.onAction(ObservationsAction.ChangeDraft(vm.state.value.draft!!.copy(
            result = ObservationResult.NO_CHANGES, answers = mapOf("foot" to listOf("Ambos")))))
        vm.onAction(ObservationsAction.Save)
        repeat(3) { yield() }
        assertTrue("correctionReason" in vm.state.value.errors)
        vm.onAction(ObservationsAction.CorrectionReason("Declaración ficticia corregida"))
        vm.onAction(ObservationsAction.Save)
        repeat(4) { yield() }
        assertNull(vm.state.value.draft)
        assertEquals(1, vm.state.value.snapshot.records.size)
        assertEquals(ObservationResult.NO_CHANGES, vm.state.value.snapshot.records.single().result)
        assertEquals(occurrence.id, vm.state.value.snapshot.records.single().occurrenceId)
        assertEquals(2, vm.state.value.snapshot.recordRevisions.size)
        assertEquals(ObservationResult.NOT_PERFORMED, vm.state.value.snapshot.recordRevisions.first().snapshot.result)
        assertEquals(ObservationsPage.RECORD_DETAIL, vm.state.value.page)
    }

    @Test fun uncertainCorrectionCommitRetriesSameRevisionWithoutAnotherHistoryEntry() = runBlocking {
        val original = fictionalRecord()
        val repository = FakeRepository().apply {
            snapshot = ObservationSnapshot(records = listOf(original))
            failAfterCorrectionCommit = true
        }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.CorrectRecord(original.id))
        vm.onAction(ObservationsAction.ChangeDraft(vm.state.value.draft!!.copy(description = "Detalle ficticio corregido")))
        vm.onAction(ObservationsAction.CorrectionReason("Corrección ficticia"))
        vm.onAction(ObservationsAction.Save)
        repeat(4) { yield() }
        assertNotNull(vm.state.value.draft)
        assertEquals(original, vm.state.value.snapshot.records.single())
        vm.onAction(ObservationsAction.Save)
        repeat(4) { yield() }
        assertNull(vm.state.value.draft)
        assertEquals(2, repository.snapshot.recordRevisions.size)
        assertEquals(2, vm.state.value.snapshot.recordRevisions.size)
        assertEquals(1, repository.correctionOperationIds.distinct().size)
    }

    @Test fun suspensionRequiresReasonAndCancellationWritesNothing() = runBlocking {
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(followUps = listOf(followUp())) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.ToggleFollowUp("OBS-001"))
        assertEquals("OBS-001", vm.state.value.suspensionObservationId)
        assertTrue(vm.state.value.snapshot.followUps.single().schedule.currentRevision.active)
        vm.onAction(ObservationsAction.SuspendReason("   "))
        vm.onAction(ObservationsAction.ConfirmSuspend)
        yield()
        assertNotNull(vm.state.value.suspensionError)
        assertTrue(repository.expectedRevisions.isEmpty())
        vm.onAction(ObservationsAction.SuspendReason("Motivo ficticio"))
        vm.onAction(ObservationsAction.Back)
        assertNull(vm.state.value.suspensionObservationId)
        assertTrue(repository.expectedRevisions.isEmpty())
    }

    @Test fun deletionUpdatesStateWithoutTransientFeedbackOrDuplicateRevision() = runBlocking {
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(followUps = listOf(followUp())) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.RequestSuspend("OBS-001"))
        vm.onAction(ObservationsAction.SuspendReason("  Motivo ficticio con revisión confirmada  "))
        vm.onAction(ObservationsAction.ConfirmSuspend)
        vm.onAction(ObservationsAction.ConfirmSuspend)
        repeat(5) { yield() }
        assertEquals(1, repository.expectedRevisions.size)
        assertEquals("Motivo ficticio con revisión confirmada",
            vm.state.value.snapshot.followUps.single().schedule.currentRevision.changeReason)
        assertEquals(ObservationsPage.DETAIL, vm.state.value.page)
        assertNull(vm.state.value.suspensionObservationId)
        assertFalse(vm.state.value.snapshot.followUps.single().schedule.currentRevision.active)
    }

    @Test fun detailHistoryFocusesExactTypeAndMostRecentFactAcrossDateNavigation() = runBlocking {
        val matching = fictionalRecord().copy(id = "matching", observationId = "OBS-004",
            occurredOn = now.toLocalDate().minusDays(2))
        val older = matching.copy(id = "older", occurredOn = now.toLocalDate().minusDays(6))
        // Same category and a more recent date: neither should change the exact observation filter.
        val other = matching.copy(id = "other", observationId = "OBS-002", occurredOn = now.toLocalDate())
        val repository = FakeRepository().apply { snapshot = ObservationSnapshot(records = listOf(older, matching, other)) }
        val vm = ObservationsViewModel(repository, actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Open("OBS-004"))
        vm.onAction(ObservationsAction.History)
        assertEquals("OBS-004", vm.state.value.focusedObservationId)
        assertEquals(matching.occurredOn, vm.state.value.selectedDate)
        vm.onAction(ObservationsAction.Date(older.occurredOn))
        vm.onAction(ObservationsAction.Filter(ObservationCategory.OTHER))
        assertNull(vm.state.value.category)
        assertEquals("OBS-004", vm.state.value.focusedObservationId)
        vm.onAction(ObservationsAction.ViewRecord(older.id))
        vm.onAction(ObservationsAction.Back)
        assertEquals(ObservationsPage.HISTORY, vm.state.value.page)
        assertEquals(older.occurredOn, vm.state.value.selectedDate)
        assertEquals("OBS-004", vm.state.value.focusedObservationId)
        vm.onAction(ObservationsAction.Back)
        assertEquals(ObservationsPage.DETAIL, vm.state.value.page)
        assertEquals("OBS-004", vm.state.value.selectedObservationId)
        vm.onAction(ObservationsAction.Back)
        vm.onAction(ObservationsAction.History)
        assertEquals(ObservationsPage.HISTORY, vm.state.value.page)
        assertNull(vm.state.value.focusedObservationId)
        assertNull(vm.state.value.category)
        assertEquals(now.toLocalDate(), vm.state.value.selectedDate)
        vm.onAction(ObservationsAction.Back)
        assertEquals(ObservationsPage.LIBRARY, vm.state.value.page)
    }

    @Test fun detailHistoryWithoutRecordsUsesTodayAndKeepsItsExactFilter() = runBlocking {
        val vm = ObservationsViewModel(FakeRepository(), actor, this, { now })
        yield()
        vm.onAction(ObservationsAction.Open("OBS-004"))
        vm.onAction(ObservationsAction.History)
        assertEquals(now.toLocalDate(), vm.state.value.selectedDate)
        assertEquals("OBS-004", vm.state.value.focusedObservationId)
        vm.onAction(ObservationsAction.Date(now.toLocalDate().minusDays(8)))
        assertEquals("OBS-004", vm.state.value.focusedObservationId)
    }

    private fun fictionalRecord() = ObservationRecord(
        "fictional-record", "OBS-027", actor.personUnderCareId, now.toLocalDate(), now.toLocalTime(),
        ObservationTimePrecision.EXACT, ObservationResult.CHANGES, emptyMap(), "Hecho ficticio",
        createdAt = now, actorId = actor.caregiverId,
    )

    private fun fill(vm: ObservationsViewModel) {
        vm.onAction(ObservationsAction.ChangeDraft(vm.state.value.draft!!.copy(description = "Cambio ficticio observado.")))
    }

    private fun followUp(): ObservationFollowUp {
        val createdAt = now.minusDays(1)
        val revision = GlucoseFollowUpRevision(
            id = "revision-fictitious", followUpId = "follow-up-fictitious", revisionNumber = 1,
            indication = "Indicación ficticia de revisión", frequency = GlucoseFollowUpFrequency.DAILY,
            startsOn = now.toLocalDate(), effectiveFrom = now.toLocalDate(),
            slots = listOf(GlucoseFollowUpSlot("slot-fictitious", time = LocalTime.of(8, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false)),
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = createdAt, authorActorId = actor.caregiverId,
        )
        return ObservationFollowUp("OBS-001", GlucoseFollowUp("follow-up-fictitious",
            actor.personUnderCareId, createdAt, actor.caregiverId, listOf(revision)))
    }

    private class FakeRepository : ObservationRepository {
        var snapshot = ObservationSnapshot()
        var consent = true
        var failLoad = false
        var failSave = false
        var consentAccepts = 0
        var beforeSave: (suspend () -> Unit)? = null
        val attemptedIds = mutableListOf<String>()
        val expectedRevisions = mutableListOf<Int>()
        val followUpRevisionIds = mutableListOf<String>()
        var failAfterFollowUpCommit = false
        var failAfterCorrectionCommit = false
        val correctionOperationIds = mutableListOf<String>()
        override suspend fun hasSensitiveConsent(actor: GlucoseActor) = consent
        override suspend fun acceptSensitiveConsent(actor: GlucoseActor, acceptedAtEpochMillis: Long) {
            consent = true; consentAccepts++
        }
        override suspend fun load(actor: GlucoseActor): ObservationSnapshot {
            check(!failLoad) { "Falla ficticia" }
            return snapshot
        }
        override suspend fun saveRecord(actor: GlucoseActor, record: ObservationRecord): ObservationRecord {
            attemptedIds += record.id
            beforeSave?.invoke()
            check(!failSave) { "Falla ficticia" }
            snapshot.records.firstOrNull { it.id == record.id ||
                (record.occurrenceId != null && it.occurrenceId == record.occurrenceId) }?.let { return it }
            snapshot = snapshot.copy(records = snapshot.records + record)
            return record
        }
        override suspend fun appendRecordCorrection(
            actor: GlucoseActor, revision: ObservationRecordRevision, expectedRevisionNumber: Int,
        ): ObservationRecordRevision {
            correctionOperationIds += revision.id
            snapshot.recordRevisions.firstOrNull { it.id == revision.id }?.let { return it }
            val old = snapshot.records.single { it.id == revision.recordId }
            val versions = snapshot.recordRevisions.ifEmpty {
                listOf(ObservationRecordRevision(old.id, old.id, 1, old, null, old.createdAt, old.actorId))
            }
            check((versions.filter { it.recordId == old.id }.maxOfOrNull { it.revisionNumber } ?: 1) == expectedRevisionNumber)
            snapshot = snapshot.copy(records = snapshot.records.filterNot { it.id == old.id } + revision.snapshot,
                recordRevisions = versions + revision)
            if (failAfterCorrectionCommit) {
                failAfterCorrectionCommit = false
                error("Falla ficticia posterior al commit")
            }
            return revision
        }

        override suspend fun appendFollowUp(actor: GlucoseActor, followUp: ObservationFollowUp, expectedRevisionNumber: Int): ObservationFollowUp {
            expectedRevisions += expectedRevisionNumber
            followUpRevisionIds += followUp.schedule.currentRevision.id
            snapshot = snapshot.copy(followUps = snapshot.followUps.filterNot { it.schedule.id == followUp.schedule.id } + followUp)
            if (failAfterFollowUpCommit) {
                failAfterFollowUpCommit = false
                error("Falla ficticia posterior al commit")
            }
            return followUp
        }
    }
}
