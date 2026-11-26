package com.bitavida.app.ui.reminders

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.education.ReminderSuggestion
import com.bitavida.app.domain.education.ReminderTitleAssistance
import com.bitavida.app.domain.usecase.ReminderPostponePreset
import com.bitavida.app.domain.usecase.ReminderDatePreset
import com.bitavida.app.domain.usecase.CareReminderTemporalGroup
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.CareReminderRepository
import com.bitavida.app.domain.usecase.CareReminderCommand
import com.bitavida.app.domain.usecase.ReminderIdeaGroup
import com.bitavida.app.domain.education.ReminderEducationCategory
import com.bitavida.app.domain.usecase.applyCareReminderCommand
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class RemindersViewModelTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val actor = CareReminderActor("profile-example", "caregiver-example", "person-example", true)

    @Test fun creationAndEditPublishImmediatelyToTheSameActiveProjection() = runBlocking {
        val repository = FakeReminders()
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Comprar insumos")
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals("Comprar insumos", vm.state.value.active.single().title)
        assertNull(vm.state.value.sheet)
        val id = vm.state.value.active.single().id
        vm.onAction(RemindersAction.Open(id))
        vm.onAction(RemindersAction.Edit)
        fill(vm, "Comprar pilas")
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(id, vm.state.value.active.single().id)
        assertEquals(2, vm.state.value.active.single().revision)
        assertEquals("Comprar pilas", vm.state.value.active.single().title)
        assertEquals(1, vm.state.value.records.size)
    }

    @Test fun doubleTapAndDismissDuringCommitPreserveSingleOperation() = runBlocking {
        val repository = FakeReminders()
        val gate = CompletableDeferred<Unit>()
        repository.beforeWrite = { gate.await() }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Preparar documentos")
        vm.onAction(RemindersAction.Save)
        vm.onAction(RemindersAction.Save)
        vm.onAction(RemindersAction.DismissSheet)
        yield()
        assertTrue(vm.state.value.saving)
        assertNotNull(vm.state.value.form)
        assertEquals(1, repository.writes)
        gate.complete(Unit)
        yield()
        assertEquals(1, vm.state.value.active.size)
        assertNull(vm.state.value.form)
    }

    @Test fun failedWriteKeepsDraftAndDismissDiscardsTemporaryChanges() = runBlocking {
        val repository = FakeReminders().apply { fail = true }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Coordinar un traslado")
        val draft = vm.state.value.form
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(draft, vm.state.value.form)
        assertNotNull(vm.state.value.error)
        vm.onAction(RemindersAction.DismissSheet)
        assertNull(vm.state.value.form)
        assertTrue(repository.records.isEmpty())
    }

    @Test fun tenActiveLimitAndExplicitCompletionReleaseASpaceAndRemoveFromCarousel() = runBlocking {
        val repository = FakeReminders().apply {
            records += (1..10).map { reminder(it.toString()) }
        }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        assertNull(vm.state.value.form)
        assertNotNull(vm.state.value.error)
        vm.onAction(RemindersAction.Open("1"))
        vm.onAction(RemindersAction.Complete)
        yield()
        assertEquals(9, vm.state.value.active.size)
        assertEquals(CareReminderStatus.DONE, vm.state.value.previous.single().status)
        assertEquals(now, vm.state.value.previous.single().closedAt)
        vm.onAction(RemindersAction.Create)
        assertNotNull(vm.state.value.form)
    }

    @Test fun discardNeedsExplicitConfirmationThenMovesToPreviousAndReconciles() = runBlocking {
        val repository = FakeReminders().apply { records += reminder("one") }
        var reconciliations = 0
        val vm = RemindersViewModel(repository, actor, reconcile = { reconciliations++; true },
            nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.RequestDiscard)
        assertEquals(CareReminderStatus.PENDING, repository.records.single().status)
        assertEquals(ReminderSheet.DISCARD, vm.state.value.sheet)
        vm.onAction(RemindersAction.ConfirmDiscard)
        yield()
        assertTrue(vm.state.value.active.isEmpty())
        assertEquals(CareReminderStatus.DISCARDED, vm.state.value.previous.single().status)
        assertEquals(1, reconciliations)
    }

    @Test fun postponingUsesSameIdentityAndRejectsPastTime() = runBlocking {
        val repository = FakeReminders().apply { records += reminder("one") }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.Postpone)
        vm.onAction(RemindersAction.ChangeForm(vm.state.value.form!!.copy(scheduledAt = now.minusMinutes(1))))
        vm.onAction(RemindersAction.SavePostponement)
        yield()
        assertNotNull(vm.state.value.error)
        vm.onAction(RemindersAction.ChangeForm(vm.state.value.form!!.copy(scheduledAt = now.plusDays(1))))
        vm.onAction(RemindersAction.SavePostponement)
        yield()
        assertEquals("one", vm.state.value.active.single().id)
        assertEquals(now.plusDays(1), vm.state.value.active.single().scheduledAt)
        assertEquals(now.plusHours(1), vm.state.value.active.single().originallyScheduledAt)
    }

    @Test fun passingTheScheduleNeverCreatesACompletionOrFailure() = runBlocking {
        val repository = FakeReminders().apply { records += reminder("one") }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Tick(now.plusDays(1)))
        assertEquals(CareReminderStatus.PENDING, vm.state.value.active.single().status)
        assertTrue(vm.state.value.active.single().hasPastSchedule(vm.state.value.now))
        assertTrue(vm.state.value.previous.isEmpty())
        assertEquals(0, repository.writes)
    }

    @Test fun retiredNotificationsCannotBeEnabledAndCleanupFailureDoesNotBlockSaving() = runBlocking {
        val repository = FakeReminders()
        var cleanupAttempts = 0
        val vm = RemindersViewModel(repository, actor, reconcile = { cleanupAttempts++; false },
            nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Llamar a un familiar")
        vm.onAction(RemindersAction.ChangeForm(vm.state.value.form!!.copy(notificationsEnabled = true)))
        assertFalse(vm.state.value.form!!.notificationsEnabled)
        vm.onAction(RemindersAction.Save)
        yield()
        assertFalse(vm.state.value.active.single().notificationsEnabled)
        assertEquals(1, cleanupAttempts)
        assertNull(vm.state.value.sheet)
        vm.onAction(RemindersAction.Create)
        assertFalse(vm.state.value.form!!.notificationsEnabled)
    }

    @Test fun returningFromHistoryAndDuplicatingDoNotRestoreAClosedStatus() = runBlocking {
        val repository = FakeReminders().apply {
            records += reminder("old").copy(status = CareReminderStatus.DONE, closedAt = now, revision = 2)
        }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.OpenPrevious)
        assertEquals(RemindersPage.PREVIOUS, vm.state.value.page)
        vm.onAction(RemindersAction.Open("old"))
        vm.onAction(RemindersAction.CreateAnother)
        assertNotEquals("old", vm.state.value.form!!.id)
        assertNull(vm.state.value.form!!.editingRevision)
        assertTrue(vm.state.value.form!!.scheduledAt.isAfter(now))
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(1, vm.state.value.active.size)
        assertEquals(1, vm.state.value.previous.size)
        vm.onAction(RemindersAction.OpenMain)
        assertEquals(RemindersPage.MAIN, vm.state.value.page)
        assertNull(vm.state.value.sheet)
        val reloaded = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        assertEquals(vm.state.value.records, reloaded.state.value.records)
    }

    @Test fun repeatedCompletionWhileSavingProducesOneCloseAndOneHistoryEntry() = runBlocking {
        val repository = FakeReminders().apply { records += reminder("one") }
        val gate = CompletableDeferred<Unit>()
        repository.beforeWrite = { gate.await() }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.Complete)
        vm.onAction(RemindersAction.Complete)
        vm.onAction(RemindersAction.DismissSheet)
        yield()
        assertEquals(1, repository.writes)
        assertTrue(vm.state.value.saving)
        gate.complete(Unit)
        yield()
        assertEquals(1, vm.state.value.previous.size)
        assertTrue(vm.state.value.active.isEmpty())
        assertEquals(2, vm.state.value.previous.single().revision)
    }

    @Test fun legacyNoticeFlagDoesNotAlterLoadedHistoryAndIsRetiredOnlyByExplicitEditing() = runBlocking {
        val legacy = reminder("one").copy(notificationsEnabled = true)
        val historical = reminder("old").copy(notificationsEnabled = true,
            status = CareReminderStatus.DONE, closedAt = now)
        val repository = FakeReminders().apply { records += listOf(legacy, historical) }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        assertEquals(legacy, vm.state.value.active.single())
        assertEquals(historical, vm.state.value.previous.single())
        assertEquals(0, repository.writes)
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.Edit)
        assertFalse(vm.state.value.form!!.notificationsEnabled)
        vm.onAction(RemindersAction.Save)
        yield()
        assertFalse(vm.state.value.active.single().notificationsEnabled)
        assertEquals(historical, vm.state.value.previous.single())
    }

    @Test fun delayedReloadCannotReplaceANewerCommittedEdit() = runBlocking {
        val repository = FakeReminders().apply { records += reminder("one") }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.Edit)
        fill(vm, "Título nuevo ficticio")
        val gate = CompletableDeferred<Unit>()
        repository.beforeLoad = { gate.await() }
        vm.onAction(RemindersAction.Reload)
        yield()
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals("Título nuevo ficticio", vm.state.value.active.single().title)
        gate.complete(Unit)
        yield()
        assertEquals("Título nuevo ficticio", vm.state.value.active.single().title)
        assertEquals(2, vm.state.value.active.single().revision)
        assertFalse(vm.state.value.loading)
    }

    @Test fun quickCompleteUpdatesGroupsHistoryAndCarouselWithoutOpeningDetails() = runBlocking {
        val repository = FakeReminders().apply { records += reminder("one") }
        var reconciled = 0
        val vm = RemindersViewModel(repository, actor, reconcile = { reconciled++; true },
            nowProvider = { now }, scopeOverride = this)
        yield()
        assertEquals(CareReminderTemporalGroup.TODAY, vm.state.value.groups.single().kind)
        vm.onAction(RemindersAction.QuickComplete("one"))
        vm.onAction(RemindersAction.QuickComplete("one"))
        yield()
        assertTrue(vm.state.value.groups.isEmpty())
        assertTrue(vm.state.value.todayCarousel.isEmpty())
        assertEquals(CareReminderStatus.DONE, vm.state.value.previous.single().status)
        assertEquals(1, repository.writes)
        assertEquals(1, reconciled)
        assertNull(vm.state.value.sheet)
    }

    @Test fun quickPostponeUsesActualTapAfterTheSheetHasBeenOpen() = runBlocking {
        var clock = now
        val repository = FakeReminders().apply { records += reminder("one") }
        val vm = RemindersViewModel(repository, actor, nowProvider = { clock }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.QuickPostpone("one"))
        clock = now.plusHours(5).plusSeconds(25)
        vm.onAction(RemindersAction.ApplyPostponePreset(ReminderPostponePreset.FIFTEEN_MINUTES))
        yield()
        assertEquals(clock.plusMinutes(15), vm.state.value.active.single().scheduledAt)
        assertEquals("one", vm.state.value.active.single().id)
        vm.onAction(RemindersAction.QuickPostpone("one"))
        clock = clock.plusDays(1)
        vm.onAction(RemindersAction.ApplyPostponePreset(ReminderPostponePreset.TOMORROW))
        yield()
        assertEquals(clock.plusDays(1), vm.state.value.active.single().scheduledAt)
        assertEquals(CareReminderTemporalGroup.UPCOMING, vm.state.value.groups.single().kind)
    }

    @Test fun duplicatingAnActiveReminderCopiesOnlyDeclaredTextIntoAnUnpersistedNewIdentity() = runBlocking {
        val repository = FakeReminders().apply {
            records += reminder("one").copy(title = "Preparar documentos", details = "Carpeta de ejemplo",
                notificationsEnabled = true)
        }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.CreateAnother)
        assertEquals("Preparar documentos", vm.state.value.form!!.title)
        assertEquals("Carpeta de ejemplo", vm.state.value.form!!.details)
        assertNotEquals("one", vm.state.value.form!!.id)
        assertFalse(vm.state.value.form!!.notificationsEnabled)
        assertNull(vm.state.value.form!!.editingRevision)
        assertEquals(0, repository.writes)
    }

    @Test fun similarTitleWarningCanOpenExistingAndReturnWithoutLosingTheDraftThenContinue() = runBlocking {
        val repository = FakeReminders().apply {
            records += reminder("existing").copy(title = "Comprar pilas para el glucómetro")
        }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Comprar baterías para el glucometro")
        val draft = vm.state.value.form
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(0, repository.writes)
        assertEquals("existing", vm.state.value.similarReminders.single().id)
        vm.onAction(RemindersAction.ViewSimilar("existing"))
        assertTrue(vm.state.value.viewingSimilar)
        assertEquals(draft, vm.state.value.form)
        vm.onAction(RemindersAction.DismissSheet)
        assertEquals(ReminderSheet.FORM, vm.state.value.sheet)
        assertEquals(draft, vm.state.value.form)
        vm.onAction(RemindersAction.SaveAnyway)
        yield()
        assertEquals(1, repository.writes)
        assertEquals(2, vm.state.value.active.size)
    }

    @Test fun editingSelfAndReusingClosedReminderDoNotWarnAboutHistoricalDuplicates() = runBlocking {
        val repository = FakeReminders().apply { records += reminder("one").copy(title = "Preparar documentos") }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.Edit)
        vm.onAction(RemindersAction.ChangeForm(vm.state.value.form!!.copy(details = "Nota ficticia")))
        vm.onAction(RemindersAction.Save)
        yield()
        assertTrue(vm.state.value.similarReminders.isEmpty())
        assertEquals(1, repository.writes)
        vm.onAction(RemindersAction.QuickComplete("one"))
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.CreateAnother)
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(3, repository.writes)
        assertEquals(1, vm.state.value.active.size)
        assertEquals(1, vm.state.value.previous.size)
    }

    @Test fun assistanceIsExplicitLimitedAndNeverModifiesTextOrPersistsUntilConfirmed() = runBlocking {
        val repository = FakeReminders()
        val received = mutableListOf<String>()
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            assistTitle = { text ->
                received += text
                ReminderTitleAssistance(suggestedTitle = "Preparar documentos",
                    suggestions = (1..5).map { ReminderSuggestion("example-" + it, "Idea de ejemplo " + it, "Descripción de ejemplo") })
            })
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "papeles abuelito")
        assertTrue(received.isEmpty())
        vm.onAction(RemindersAction.HelpOrganize)
        yield()
        assertEquals(listOf("papeles abuelito"), received)
        assertEquals("papeles abuelito", vm.state.value.form!!.title)
        assertTrue(vm.state.value.assistance!!.suggestions.isEmpty())
        assertEquals(0, repository.writes)
        val scheduled = vm.state.value.form!!.scheduledAt
        vm.onAction(RemindersAction.UseSuggestedTitle)
        assertEquals("Preparar documentos", vm.state.value.form!!.title)
        assertEquals(scheduled, vm.state.value.form!!.scheduledAt)
        assertEquals(0, repository.writes)
    }

    @Test fun selectedSuggestionOpensADraftAndStillRespectsTheActiveLimit() = runBlocking {
        val repository = FakeReminders()
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            ideaCatalog = listOf(ReminderIdeaGroup(ReminderEducationCategory.DOCUMENTS,
                listOf(ReminderSuggestion("docs", "Preparar documentos", "Revisar tu carpeta")))))
        yield()
        vm.onAction(RemindersAction.RequestSuggestions())
        yield()
        assertEquals(ReminderSheet.SUGGESTIONS, vm.state.value.sheet)
        vm.onAction(RemindersAction.UseIdea(vm.state.value.ideaRows.single().title))
        assertEquals(ReminderSheet.FORM, vm.state.value.sheet)
        assertEquals("Preparar documentos", vm.state.value.form!!.title)
        assertFalse(vm.state.value.form!!.notificationsEnabled)
        assertEquals(0, repository.writes)
        repository.records += (1..10).map { reminder(it.toString()) }
        vm.onAction(RemindersAction.DismissSheet)
        vm.onAction(RemindersAction.Reload)
        yield()
        vm.onAction(RemindersAction.UseSuggestion("Otra propuesta"))
        assertNull(vm.state.value.form)
        assertNotNull(vm.state.value.error)
        assertEquals(0, repository.writes)
    }

    @Test fun relatedSuggestionsReturnToTheSameDraftAndLateResultsCannotReplaceChangedText() = runBlocking {
        val repository = FakeReminders()
        val gate = CompletableDeferred<Unit>()
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            assistTitle = { gate.await(); ReminderTitleAssistance(suggestedTitle = "Resultado tardío") })
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Texto de ejemplo")
        val draft = vm.state.value.form
        vm.onAction(RemindersAction.HelpOrganize)
        yield()
        vm.onAction(RemindersAction.OpenIdeas)
        assertEquals(draft, vm.state.value.form)
        vm.onAction(RemindersAction.DismissSheet)
        assertEquals(ReminderSheet.FORM, vm.state.value.sheet)
        assertEquals(draft, vm.state.value.form)
        fill(vm, "Otro texto")
        gate.complete(Unit)
        yield()
        assertNull(vm.state.value.assistance)
        assertEquals("Otro texto", vm.state.value.form!!.title)
    }

    @Test fun todayTomorrowShortcutsUseTheActualDateAndValidationStillRejectsPast() = runBlocking {
        var clock = now
        val repository = FakeReminders()
        val vm = RemindersViewModel(repository, actor, nowProvider = { clock }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Preparar documentos")
        clock = now.plusDays(1).plusHours(1)
        vm.onAction(RemindersAction.SetDatePreset(ReminderDatePreset.TODAY))
        assertEquals(clock.toLocalDate(), vm.state.value.form!!.scheduledAt.toLocalDate())
        vm.onAction(RemindersAction.Save)
        yield()
        assertNotNull(vm.state.value.error)
        assertEquals(0, repository.records.size)
        vm.onAction(RemindersAction.SetDatePreset(ReminderDatePreset.TOMORROW))
        assertEquals(clock.toLocalDate().plusDays(1), vm.state.value.form!!.scheduledAt.toLocalDate())
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(1, repository.records.size)
    }

    @Test fun finishingTitleClassifiesOnceAndIgnoringPreservesEveryDeclaredField() = runBlocking {
        val repository = FakeReminders()
        var calls = 0
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            assistTitle = { calls++; ReminderTitleAssistance(suggestedTitle = "Comprar pilas para el glucómetro") })
        yield()
        vm.onAction(RemindersAction.Create)
        listOf("P", "Pi", "Pilas del glucometro").forEach { fill(vm, it) }
        assertEquals(0, calls)
        val original = vm.state.value.form!!
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertEquals(1, calls)
        assertEquals(original, vm.state.value.form)
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertEquals(1, calls)
        vm.onAction(RemindersAction.IgnoreTitleSuggestion)
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertEquals(1, calls)
        assertNull(vm.state.value.assistance)
        assertEquals(original, vm.state.value.form)
        fill(vm, "Pilas del aparato")
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertEquals(2, calls)
        assertEquals(0, repository.writes)
    }

    @Test fun acceptingATitleChangesOnlyTheTitleAndAllowsFurtherEditing() = runBlocking {
        val repository = FakeReminders()
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            assistTitle = { ReminderTitleAssistance(suggestedTitle = "Comprar pilas para el glucómetro") })
        yield()
        vm.onAction(RemindersAction.Create)
        vm.onAction(RemindersAction.ChangeForm(vm.state.value.form!!.copy(title = "Pilas del glucometro",
            details = "Nota ficticia", scheduledAt = now.plusDays(3), notificationsEnabled = true)))
        val before = vm.state.value.form!!
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        vm.onAction(RemindersAction.UseSuggestedTitle)
        assertEquals(before.copy(title = "Comprar pilas para el glucómetro"), vm.state.value.form)
        assertEquals(0, repository.writes)
        fill(vm, "Comprar pilas pequeñas para el glucómetro")
        assertEquals("Comprar pilas pequeñas para el glucómetro", vm.state.value.form!!.title)
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(1, repository.writes)
        assertEquals(before.scheduledAt, vm.state.value.active.single().scheduledAt)
        assertFalse(vm.state.value.active.single().notificationsEnabled)
    }

    @Test fun insufficientContextIsSilentUntilHelpIsRequestedAndNeverBlocksOriginalText() = runBlocking {
        val repository = FakeReminders()
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            assistTitle = { ReminderTitleAssistance(message = "Un mensaje educativo que no pertenece al formulario") })
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Test")
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertNull(vm.state.value.assistance)
        assertNull(vm.state.value.assistanceMessage)
        vm.onAction(RemindersAction.HelpOrganize)
        yield()
        assertEquals("Describe una acción concreta, por ejemplo: Comprar pilas", vm.state.value.assistanceMessage)
        assertEquals("Test", vm.state.value.form!!.title)
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals("Test", repository.records.single().title)
    }

    @Test fun ideaSelectionPreservesAnEditingDraftAndNeverCreatesAnotherRecord() = runBlocking {
        val repository = FakeReminders().apply {
            records += reminder("one").copy(title = "Pendiente original", details = "Datos ficticios",
                notificationsEnabled = true)
        }
        var classifierCalls = 0
        val catalog = listOf(
            ReminderIdeaGroup(ReminderEducationCategory.SHOPPING,
                listOf(ReminderSuggestion("pilas", "Comprar pilas", "Idea ficticia"))),
            ReminderIdeaGroup(ReminderEducationCategory.DOCUMENTS,
                listOf(ReminderSuggestion("docs", "Preparar documentos", "Idea ficticia"))))
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            assistTitle = { classifierCalls++; ReminderTitleAssistance() }, ideaCatalog = catalog)
        yield()
        vm.onAction(RemindersAction.Open("one"))
        vm.onAction(RemindersAction.Edit)
        val original = vm.state.value.form!!
        vm.onAction(RemindersAction.OpenIdeas)
        assertEquals(ReminderSheet.SUGGESTIONS, vm.state.value.sheet)
        assertEquals(0, classifierCalls)
        vm.onAction(RemindersAction.SelectIdeaCategory(ReminderEducationCategory.DOCUMENTS))
        assertEquals("Preparar documentos", vm.state.value.ideaRows.single().title)
        vm.onAction(RemindersAction.UseIdea(vm.state.value.ideaRows.single().title))
        assertEquals(ReminderSheet.FORM, vm.state.value.sheet)
        assertEquals(original.copy(title = "Preparar documentos"), vm.state.value.form)
        assertEquals(0, repository.writes)
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(1, repository.records.size)
        assertEquals("one", repository.records.single().id)
        assertEquals(2, repository.records.single().revision)
    }

    @Test fun closingTheIdeaPickerReturnsToTheUnchangedDraftAndDuplicateCheckRunsAtTitleFinish() = runBlocking {
        val repository = FakeReminders().apply {
            records += reminder("existing").copy(title = "Comprar pilas para el glucómetro")
        }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Create)
        fill(vm, "Comprar baterias del glucometro")
        val original = vm.state.value.form
        vm.onAction(RemindersAction.OpenIdeas)
        vm.onAction(RemindersAction.DismissSheet)
        assertEquals(ReminderSheet.FORM, vm.state.value.sheet)
        assertEquals(original, vm.state.value.form)
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertEquals("existing", vm.state.value.similarReminders.single().id)
        assertEquals(0, repository.writes)
        vm.onAction(RemindersAction.SaveAnyway)
        yield()
        assertEquals(2, repository.records.size)
    }

    @Test fun cardAndOverflowOpenTheSameDetailForEveryStatusAndClearTransientDrafts() = runBlocking {
        CareReminderStatus.entries.forEach { status ->
            val record = reminder("unified-" + status.name).copy(
                status = status, closedAt = now.takeIf { status != CareReminderStatus.PENDING })
            val repository = FakeReminders().apply { records += record }
            val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
                assistTitle = { ReminderTitleAssistance(suggestedTitle = "Comprar pilas para el dispositivo") })
            yield()

            vm.onAction(RemindersAction.Create)
            fill(vm, "Pilas del aparato")
            vm.onAction(RemindersAction.HelpOrganize)
            yield()
            assertNotNull(vm.state.value.assistance)
            vm.onAction(RemindersAction.Open(record.id))
            val cardDetail = vm.state.value
            assertEquals(ReminderSheet.DETAIL, cardDetail.sheet)
            assertEquals(record, cardDetail.selected)
            assertNull(cardDetail.form)
            assertNull(cardDetail.assistance)

            vm.onAction(RemindersAction.Create)
            fill(vm, "Pilas del aparato")
            vm.onAction(RemindersAction.HelpOrganize)
            yield()
            assertNotNull(vm.state.value.assistance)
            vm.onAction(RemindersAction.OpenOptions(record.id))
            assertEquals(cardDetail, vm.state.value)
            assertEquals(0, repository.writes)
        }
    }

    @Test fun unifiedHistoricalDetailRemainsReadOnlyAndDuplicatingNeverReopensTheOriginal() = runBlocking {
        listOf(CareReminderStatus.DONE, CareReminderStatus.DISCARDED).forEach { status ->
            val original = reminder("history-" + status.name).copy(
                title = "Preparar documentos ficticios", details = "Nota ficticia",
                scheduledAt = now.minusDays(3), notificationsEnabled = true,
                status = status, closedAt = now)
            val repository = FakeReminders().apply { records += original }
            var assistanceCalls = 0
            val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
                assistTitle = { assistanceCalls++; ReminderTitleAssistance(suggestedTitle = "Otra redacción ficticia") })
            yield()
            vm.onAction(RemindersAction.OpenOptions(original.id))
            vm.onAction(RemindersAction.Edit)
            vm.onAction(RemindersAction.Complete)
            vm.onAction(RemindersAction.Postpone)
            assertEquals(ReminderSheet.DETAIL, vm.state.value.sheet)
            assertNull(vm.state.value.form)
            assertEquals(0, repository.writes)

            vm.onAction(RemindersAction.CreateAnother)
            val draft = vm.state.value.form!!
            assertEquals(ReminderSheet.FORM, vm.state.value.sheet)
            assertNotEquals(original.id, draft.id)
            assertNull(draft.editingRevision)
            assertEquals(original.title, draft.title)
            assertEquals(original.details, draft.details)
            assertTrue(draft.scheduledAt.isAfter(now))
            assertFalse(draft.notificationsEnabled)
            assertFalse(draft.titleAssistanceEnabled)
            vm.onAction(RemindersAction.TitleEditingFinished)
            vm.onAction(RemindersAction.HelpOrganize)
            yield()
            assertEquals(0, assistanceCalls)
            assertNull(vm.state.value.assistance)
            assertEquals(draft, vm.state.value.form)
            assertEquals(listOf(original), repository.records)
            assertEquals(0, repository.writes)

            vm.onAction(RemindersAction.Save)
            yield()
            assertEquals(original, repository.records.single { it.id == original.id })
            val created = vm.state.value.active.single()
            assertEquals(draft.id, created.id)
            assertEquals(original.title, created.title)
            assertEquals(original.details, created.details)
            assertEquals(draft.scheduledAt, created.scheduledAt)
            assertEquals(CareReminderStatus.PENDING, created.status)
            assertNull(created.closedAt)
            assertEquals(1, created.revision)
            assertEquals(1, repository.writes)
            assertEquals(2, repository.records.size)
        }
    }

    @Test fun historicalCopyEnablesTitleAssistanceOnlyAfterTheCaregiverChangesItsTitle() = runBlocking {
        val original = reminder("historical-copy").copy(
            title = "Preparar documentos ficticios", details = "Nota original ficticia",
            status = CareReminderStatus.DONE, closedAt = now, revision = 2,
        )
        val repository = FakeReminders().apply { records += original }
        val receivedTitles = mutableListOf<String>()
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this,
            assistTitle = { title ->
                receivedTitles += title
                ReminderTitleAssistance(suggestedTitle = "Comprar pilas para el dispositivo")
            })
        yield()
        vm.onAction(RemindersAction.Open(original.id))
        vm.onAction(RemindersAction.CreateAnother)
        assertFalse(vm.state.value.form!!.titleAssistanceEnabled)
        vm.onAction(RemindersAction.ChangeForm(vm.state.value.form!!.copy(
            details = "Detalle adicional ficticio",
            scheduledAt = now.plusDays(2),
            notificationsEnabled = true,
        )))
        assertFalse(vm.state.value.form!!.titleAssistanceEnabled)
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertTrue(receivedTitles.isEmpty())
        val beforeTitleChange = vm.state.value.form!!

        fill(vm, "Pilas del aparato")
        assertTrue(vm.state.value.form!!.titleAssistanceEnabled)
        vm.onAction(RemindersAction.TitleEditingFinished)
        yield()
        assertEquals(listOf("Pilas del aparato"), receivedTitles)
        assertEquals("Comprar pilas para el dispositivo", vm.state.value.assistance!!.suggestedTitle)
        assertEquals(beforeTitleChange.copy(title = "Pilas del aparato", titleAssistanceEnabled = true), vm.state.value.form)
        assertEquals(listOf(original), repository.records)
        assertEquals(0, repository.writes)
    }

    @Test fun externalCompletionRefreshQueuedDuringInitialLoadReplacesItsStaleSnapshot() = runBlocking {
        val pending = reminder("completed-externally")
        val completed = pending.copy(status = CareReminderStatus.DONE, closedAt = now, revision = 2)
        val snapshotCaptured = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        var loads = 0
        val repository = FakeReminders().apply {
            records += pending
            beforeLoad = {
                loads++
                if (loads == 1) {
                    snapshotCaptured.complete(Unit)
                    releaseSnapshot.await()
                }
            }
        }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        snapshotCaptured.await()
        assertTrue(vm.state.value.loading)
        repository.records.clear()
        repository.records += completed

        vm.onAction(RemindersAction.Reload)
        vm.onAction(RemindersAction.Reload)
        assertEquals(1, loads)
        releaseSnapshot.complete(Unit)
        withTimeout(1_000) {
            vm.state.first { !it.loading && it.records.singleOrNull() == completed }
        }

        assertEquals(2, loads)
        assertEquals(listOf(completed), vm.state.value.previous)
        assertTrue(vm.state.value.active.isEmpty())
        assertTrue(vm.state.value.groups.isEmpty())
        assertTrue(vm.state.value.todayCarousel.isEmpty())
        assertEquals(listOf(completed), repository.records)
        assertEquals(0, repository.writes)
    }

    @Test fun titleSearchAndIdeaActionsUseUnicodeLimitsWithoutTruncatingLegacyDetails() = runBlocking {
        val legacy = reminder("legacy").copy(details = "x".repeat(800))
        val repository = FakeReminders().apply { records += legacy }
        val vm = RemindersViewModel(repository, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(RemindersAction.Open(legacy.id))
        vm.onAction(RemindersAction.Edit)
        val title = "👨‍👩‍👧‍👦".repeat(100)
        fill(vm, title)
        assertEquals(title, vm.state.value.form!!.title)
        fill(vm, title + "x")
        assertEquals(title, vm.state.value.form!!.title)
        vm.onAction(RemindersAction.UseIdea("x".repeat(101)))
        assertEquals(title, vm.state.value.form!!.title)
        vm.onAction(RemindersAction.SetIdeaSearch("🇲🇽".repeat(101)))
        assertEquals("🇲🇽".repeat(100), vm.state.value.ideaSearch)
        assertEquals(legacy.details, vm.state.value.form!!.details)
        vm.onAction(RemindersAction.Save)
        yield()
        assertEquals(title, vm.state.value.records.single().title)
        assertEquals(legacy.details, vm.state.value.records.single().details)
    }

    private fun fill(vm: RemindersViewModel, title: String) {
        vm.onAction(RemindersAction.ChangeForm(vm.state.value.form!!.copy(title = title)))
    }

    private fun reminder(id: String) = CareReminder(id, actor.personUnderCareId, "Pendiente ficticio " + id,
        null, now.plusHours(1), false, CareReminderStatus.PENDING, now, now, null, 1)

    private class FakeReminders : CareReminderRepository {
        val records = mutableListOf<CareReminder>()
        var beforeWrite: suspend () -> Unit = {}
        var beforeLoad: suspend () -> Unit = {}
        var writes = 0
        var fail = false
        private val operations = mutableMapOf<String, CareReminder>()
        override suspend fun load(actor: CareReminderActor): List<CareReminder> {
            val snapshot = records.toList()
            beforeLoad()
            return snapshot
        }
        override suspend fun apply(actor: CareReminderActor, command: CareReminderCommand, now: LocalDateTime): CareReminder {
            writes++
            beforeWrite()
            if (fail) error("Fictitious storage failure")
            operations[command.operationId]?.let { return it }
            val saved = applyCareReminderCommand(records, actor.personUnderCareId, command, now)
            records.removeAll { it.id == saved.id }
            records += saved
            operations[command.operationId] = saved
            return saved
        }
    }
}
