package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.GlucoseFakeRepository
import com.bitavida.app.domain.usecase.GlucoseFollowUpDraft
import com.bitavida.app.domain.usecase.GlucoseFollowUpSlotDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds

class GlucoseViewModelTest {
    private val now = LocalDateTime.of(2026, 11, 16, 9, 30)
    private val actor = GlucoseActor(
        "profile-test",
        "caregiver-test",
        "person-test",
        authorizationCurrent = true,
    )

    @Test fun firstAndSubsequentMeasurementsShareCaptureAndCommitBeforeClosing() = runBlocking {
        val repo = GlucoseFakeRepository()
        val vm = GlucoseViewModel(actor, repo, this, { now })
        yield()
        vm.onAction(GlucoseAction.Open())
        assertTrue(vm.state.value.draft!!.firstMeasurement)
        fill(vm)
        vm.onAction(GlucoseAction.Save)
        yield()
        assertNull(vm.state.value.draft)
        assertEquals(1, vm.state.value.records.size)
        assertEquals(GlucoseEffect.SAVED, withTimeout(1.seconds) { vm.effects.first() })
        vm.onAction(GlucoseAction.Open())
        assertFalse(vm.state.value.draft!!.firstMeasurement)
        assertEquals("", vm.state.value.draft!!.reading)
        assertNull(vm.state.value.draft!!.moment)
    }

    @Test fun doubleTapAndBackDuringCommitDoNotDuplicateOrDiscard() = runBlocking {
        val repo = GlucoseFakeRepository()
        val release = CompletableDeferred<Unit>()
        repo.beforeCreate = { release.await() }
        val vm = GlucoseViewModel(actor, repo, this, { now })
        yield()
        vm.onAction(GlucoseAction.Open())
        fill(vm)
        vm.onAction(GlucoseAction.Save)
        vm.onAction(GlucoseAction.Save)
        vm.onAction(GlucoseAction.RequestClose)
        yield()
        assertTrue(vm.state.value.saving)
        assertNotNull(vm.state.value.draft)
        assertEquals(1, repo.creates)
        release.complete(Unit)
        yield()
        assertEquals(1, repo.records.size)
        assertNull(vm.state.value.draft)
    }

    @Test fun failedWritePreservesExactDraftUntilDirectClose() = runBlocking {
        val repo = GlucoseFakeRepository()
        val vm = GlucoseViewModel(actor, repo, this, { now })
        yield()
        vm.onAction(GlucoseAction.Open())
        fill(vm)
        vm.onAction(GlucoseAction.Note("Nota ficticia"))
        val draft = vm.state.value.draft
        repo.fail = true
        vm.onAction(GlucoseAction.Save)
        yield()
        assertEquals(draft, vm.state.value.draft)
        assertNotNull(vm.state.value.saveError)
        assertTrue(vm.state.value.records.isEmpty())
        vm.onAction(GlucoseAction.RequestClose)
        assertNull(vm.state.value.draft)
        assertNull(vm.state.value.saveError)
        assertTrue(repo.records.isEmpty())
        assertEquals(GlucoseEffect.CLOSED, withTimeout(1.seconds) { vm.effects.first() })
    }

    @Test fun decliningConsentPreservesInputAndAcceptingCommits() = runBlocking {
        val repo = GlucoseFakeRepository().apply { consent = false }
        val vm = GlucoseViewModel(actor, repo, this, { now })
        yield()
        vm.onAction(GlucoseAction.Open())
        fill(vm)
        vm.onAction(GlucoseAction.Save)
        yield()
        assertTrue(vm.state.value.consentVisible)
        assertEquals(0, repo.creates)
        vm.onAction(GlucoseAction.DismissConsent)
        assertEquals("128", vm.state.value.draft!!.reading)
        vm.onAction(GlucoseAction.Save)
        yield()
        vm.onAction(GlucoseAction.AcceptConsent(accepted = true))
        vm.onAction(GlucoseAction.ConfirmConsent)
        yield()
        assertEquals(1, repo.records.size)
        assertNull(vm.state.value.draft)
    }

    @Test fun duplicateScheduleRequiresExplicitConversionToAdditional() = runBlocking {
        val repo = GlucoseFakeRepository()
        val plan = MeasurementFollowUp(MeasurementKind.CAPILLARY_GLUCOSE, "Configurado", id = "plan-test")
        val occurrence = ConfirmedGlucoseOccurrence("slot-test", "plan-test", now.minusHours(2), GlucoseContext.FASTING)
        val vm = GlucoseViewModel(actor, repo, this, { now })
        yield()
        vm.onAction(GlucoseAction.Open(plan, occurrence))
        assertEquals(GlucoseContext.FASTING, vm.state.value.draft!!.moment)
        assertEquals(now, vm.state.value.draft!!.actualAt) // actual time is independent
        fill(vm)
        vm.onAction(GlucoseAction.Save)
        yield()
        with(repo.records.single()) {
            assertEquals(
                GlucoseContext.AFTER_MEAL,
                (value as MeasurementValue.Glucose).context,
            )
            assertEquals(
                occurrence.id,
                glucoseMetadata!!.scheduledOccurrenceId,
            )
        }
        vm.onAction(GlucoseAction.Open(plan, occurrence))
        fill(vm)
        vm.onAction(GlucoseAction.Save)
        yield()
        assertTrue(vm.state.value.occurrenceAlreadyRecorded)
        assertEquals(1, repo.records.size)
        vm.onAction(GlucoseAction.MakeAdditional)
        assertTrue(vm.state.value.draft!!.context is GlucoseRegistrationContext.Additional)
        vm.onAction(GlucoseAction.Save)
        yield()
        assertEquals(2, repo.records.size)
        assertNull(repo.records.last().glucoseMetadata!!.scheduledOccurrenceId)
    }

    @Test fun loadFailureIsAnErrorNotAFabricatedFirstMeasurement() = runBlocking {
        val repo = GlucoseFakeRepository().apply { fail = true }
        val vm = GlucoseViewModel(actor, repo, this, { now })
        yield()
        assertTrue(vm.state.value.loadFailed)
        vm.onAction(GlucoseAction.Open())
        assertNull(vm.state.value.draft)
        repo.fail = false
        vm.onAction(GlucoseAction.Reload)
        yield()
        assertFalse(vm.state.value.loadFailed)
        vm.onAction(GlucoseAction.Open())
        assertNotNull(vm.state.value.draft)
    }

    @Test
    fun followUpChangesPublishOnlyAfterCommitAndIgnoreDuplicateDeactivation() =
        runBlocking {
            val repo = GlucoseFakeRepository()
            val creationRelease = CompletableDeferred<Unit>()
            repo.beforeFollowUpWrite = { creationRelease.await() }
            val vm = GlucoseViewModel(actor, repo, this, { now })
            yield()

            vm.onAction(GlucoseAction.SaveFollowUp(followUpDraft()))
            yield()

            assertTrue(vm.state.value.followUpSaving)
            assertNull(vm.state.value.followUp)
            creationRelease.complete(Unit)
            yield()

            assertFalse(vm.state.value.followUpSaving)
            assertTrue(requireNotNull(vm.state.value.followUp).currentRevision.active)
            assertEquals(
                GlucoseEffect.FOLLOW_UP_SAVED,
                withTimeout(1.seconds) { vm.effects.first() },
            )

            val removalRelease = CompletableDeferred<Unit>()
            repo.beforeFollowUpWrite = { removalRelease.await() }
            vm.onAction(GlucoseAction.DeleteFollowUp(""))
            vm.onAction(GlucoseAction.DeleteFollowUp(""))
            yield()

            assertTrue(vm.state.value.followUpSaving)
            assertTrue(requireNotNull(vm.state.value.followUp).currentRevision.active)
            removalRelease.complete(Unit)
            yield()

            val removed = requireNotNull(vm.state.value.followUp)
            assertFalse(vm.state.value.followUpSaving)
            assertFalse(removed.currentRevision.active)
            assertEquals(2, removed.revisions.size)
            assertEquals(2, repo.followUpWrites)
            assertEquals(
                GlucoseEffect.FOLLOW_UP_SAVED,
                withTimeout(1.seconds) { vm.effects.first() },
            )
        }

    @Test
    fun failedFollowUpCommitKeepsPersistedStateAndReportsTheError() =
        runBlocking {
            val repo = GlucoseFakeRepository()
            val vm = GlucoseViewModel(actor, repo, this, { now })
            yield()
            vm.onAction(GlucoseAction.SaveFollowUp(followUpDraft()))
            yield()
            withTimeout(1.seconds) { vm.effects.first() }
            val active = requireNotNull(vm.state.value.followUp)

            repo.beforeFollowUpWrite = {
                error("Falla ficticia de almacenamiento")
            }
            vm.onAction(GlucoseAction.DeleteFollowUp(""))
            yield()

            assertFalse(vm.state.value.followUpSaving)
            assertEquals(active, vm.state.value.followUp)
            assertTrue(requireNotNull(vm.state.value.followUp).currentRevision.active)
            assertEquals(
                "No se pudo guardar el seguimiento.",
                vm.state.value.followUpError,
            )
            assertEquals(1, repo.followUpWrites)
        }

    @Test
    fun historicalOccurrenceActionsRegisterConfirmAndCorrectWithoutDuplication() =
        runBlocking {
            val repo = GlucoseFakeRepository()
            val vm = GlucoseViewModel(actor, repo, this, { now })
            yield()
            vm.onAction(GlucoseAction.SaveFollowUp(followUpDraft()))
            yield()
            val occurrence = requireNotNull(vm.state.value.followUp)
                .occurrencesOn(now.toLocalDate())
                .single()

            vm.onAction(GlucoseAction.OpenOccurrence(occurrence))
            vm.onAction(GlucoseAction.RegisterSelectedOccurrence)
            assertNull(vm.state.value.selectedOccurrence)
            assertTrue(
                vm.state.value.draft?.context is
                    GlucoseRegistrationContext.Scheduled,
            )
            vm.onAction(GlucoseAction.RequestClose)

            vm.onAction(GlucoseAction.OpenOccurrence(occurrence))
            vm.onAction(
                GlucoseAction.ConfirmNotPerformed(
                    occurrence,
                    "Motivo ficticio",
                ),
            )
            yield()
            assertEquals(1, vm.state.value.occurrenceDecisions.size)
            assertEquals(1, repo.occurrenceDecisions.size)

            vm.onAction(GlucoseAction.CorrectNotPerformed("Motivo corregido"))
            yield()
            assertEquals(
                "Motivo corregido",
                vm.state.value.selectedOccurrenceDecision?.reason,
            )
            assertEquals(
                2,
                repo.loadOccurrenceDecisionRevisions(actor, occurrence.id).size,
            )

            vm.onAction(GlucoseAction.RegisterSelectedOccurrence)
            assertEquals(
                repo.occurrenceDecisions.single().id,
                vm.state.value.draft?.correctingDecisionId,
            )
            fill(vm)
            vm.onAction(GlucoseAction.Save)
            yield()

            assertEquals(1, vm.state.value.records.size)
            assertEquals(
                GlucoseOccurrenceDecisionRevisionOperation
                    .CORRECTED_TO_RECORDED,
                repo.loadOccurrenceDecisionRevisions(
                    actor,
                    occurrence.id,
                ).last().operation,
            )
        }

    @Test
    fun correctionAndDeletionRefreshActiveRecordsImmediately() = runBlocking {
        val repo = GlucoseFakeRepository()
        val vm = GlucoseViewModel(actor, repo, this, { now }) { "record-test" }
        yield()
        vm.onAction(GlucoseAction.Open())
        fill(vm)
        vm.onAction(GlucoseAction.Save)
        yield()

        vm.onAction(GlucoseAction.OpenRecord("record-test"))
        yield()
        assertEquals(1, vm.state.value.revisions.size)
        vm.onAction(GlucoseAction.CorrectSelectedRecord)
        vm.onAction(GlucoseAction.Reading("142"))
        vm.onAction(GlucoseAction.Save)
        yield()

        assertEquals(
            "142",
            (vm.state.value.records.single().value as MeasurementValue.Glucose).reading,
        )
        assertNull(vm.state.value.draft)

        vm.onAction(GlucoseAction.OpenRecord("record-test"))
        yield()
        assertEquals(2, vm.state.value.revisions.size)
        vm.onAction(GlucoseAction.RequestDeleteSelectedRecord)
        assertTrue(vm.state.value.deleteConfirmationVisible)
        vm.onAction(GlucoseAction.ConfirmDeleteSelectedRecord)
        yield()

        assertTrue(vm.state.value.records.isEmpty())
        assertTrue(
            repo.loadRevisions(actor, "record-test").last().operation ==
                GlucoseRevisionOperation.DELETED,
        )
    }

    @Test
    fun calendarCanCorrectAnOldDeclarationAfterDeactivationWithoutReactivatingFollowUp() = runBlocking {
        val repo = GlucoseFakeRepository()
        val vm = GlucoseViewModel(actor, repo, this, { now })
        yield()
        vm.onAction(GlucoseAction.SaveFollowUp(followUpDraft()))
        yield()
        val occurrence = requireNotNull(vm.state.value.followUp)
            .occurrencesOn(now.toLocalDate()).single()
        vm.onAction(GlucoseAction.OpenOccurrence(occurrence))
        vm.onAction(GlucoseAction.ConfirmNotPerformed(occurrence, "Motivo ficticio"))
        yield()
        vm.onAction(GlucoseAction.DeleteFollowUp(""))
        yield()
        assertFalse(requireNotNull(vm.state.value.followUp).currentRevision.active)

        vm.onAction(GlucoseAction.OpenOccurrence(occurrence))
        yield()
        vm.onAction(GlucoseAction.RegisterSelectedOccurrence)
        assertNotNull(vm.state.value.draft)
        assertEquals(repo.occurrenceDecisions.single().id, vm.state.value.draft?.correctingDecisionId)
        fill(vm)
        vm.onAction(GlucoseAction.Save)
        yield()

        assertEquals(1, vm.state.value.records.size)
        assertEquals(occurrence.id, vm.state.value.records.single().glucoseMetadata?.scheduledOccurrenceId)
        assertFalse(requireNotNull(vm.state.value.followUp).currentRevision.active)
        assertEquals(
            GlucoseOccurrenceDecisionRevisionOperation.CORRECTED_TO_RECORDED,
            repo.loadOccurrenceDecisionRevisions(actor, occurrence.id).last().operation,
        )
    }

    private fun followUpDraft() = GlucoseFollowUpDraft(
        followUpId = "follow-up-test",
        indication = "Medir según indicación ficticia",
        frequency = GlucoseFollowUpFrequency.DAILY,
        effectiveFrom = now.toLocalDate(),
        remindersEnabled = false,
        slots = listOf(
            GlucoseFollowUpSlotDraft(
                id = "slot-test",
                timingType = GlucoseFollowUpTimingType.EXACT_TIME,
                time = LocalTime.of(7, 0),
                context = GlucoseContext.FASTING,
                reminderEnabled = true,
                reminderTime = LocalTime.of(7, 0),
                useSameTimeForReminder = true,
            ),
        ),
    )

    private fun fill(vm: GlucoseViewModel) {
        vm.onAction(GlucoseAction.Reading("128"))
        vm.onAction(GlucoseAction.Moment(GlucoseContext.AFTER_MEAL))
    }
}
