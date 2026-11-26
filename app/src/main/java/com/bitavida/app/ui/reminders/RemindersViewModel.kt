package com.bitavida.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.education.ReminderTitleAssistance
import com.bitavida.app.domain.education.ReminderEducationCategory
import com.bitavida.app.domain.education.ReminderSuggestion
import com.bitavida.app.domain.education.EducationTokenizer
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.CareReminderRepository
import com.bitavida.app.domain.usecase.*
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.validation.fitsTextLimit
import com.bitavida.app.domain.validation.limitTextCharacters
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RemindersPage { MAIN, PREVIOUS }
enum class ReminderSheet { DETAIL, FORM, POSTPONE, DISCARD, SUGGESTIONS }

data class ReminderForm(
    val id: String,
    val operationId: String,
    val title: String,
    val details: String,
    val scheduledAt: LocalDateTime,
    val notificationsEnabled: Boolean,
    val editingRevision: Int? = null,
    val detailsExpanded: Boolean = false,
    val titleAssistanceEnabled: Boolean = true,
)

data class RemindersUiState(
    val records: List<CareReminder> = emptyList(),
    val personUnderCareId: String = "",
    val page: RemindersPage = RemindersPage.MAIN,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val selectedId: String? = null,
    val sheet: ReminderSheet? = null,
    val form: ReminderForm? = null,
    val error: String? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val similarReminders: List<CareReminder> = emptyList(),
    val viewingSimilar: Boolean = false,
    val assistanceLoading: Boolean = false,
    val assistance: ReminderTitleAssistance? = null,
    val assistanceMessage: String? = null,
    val ideaCatalog: List<ReminderIdeaGroup> = emptyList(),
    val ideaCategory: ReminderEducationCategory? = null,
    val ideaSearch: String = "",
) {
    val active: List<CareReminder> get() = activeCareReminders(records, personUnderCareId)
    val previous: List<CareReminder> get() = previousCareReminders(records, personUnderCareId)
    val selected: CareReminder? get() = records.singleOrNull { it.id == selectedId }
    val atLimit: Boolean get() = active.size >= MAX_ACTIVE_CARE_REMINDERS
    val groups: List<CareReminderGroup> get() = groupedCareReminders(records, personUnderCareId, now)
    val todayCarousel: List<CareReminder> get() = careRemindersForTodayCarousel(active, now)
    val ideaRows: List<ReminderSuggestion> get() = matchingReminderIdeas(ideaCatalog, ideaCategory, ideaSearch)
}

sealed interface RemindersAction {
    data object Reload : RemindersAction
    data object OpenMain : RemindersAction
    data object OpenPrevious : RemindersAction
    data object Create : RemindersAction
    data class Open(val id: String) : RemindersAction
    data object Edit : RemindersAction
    data object CreateAnother : RemindersAction
    data object Postpone : RemindersAction
    data object Complete : RemindersAction
    data object RequestDiscard : RemindersAction
    data object ConfirmDiscard : RemindersAction
    data object DismissSheet : RemindersAction
    data object BackToDetail : RemindersAction
    data class ChangeForm(val value: ReminderForm) : RemindersAction
    data object Save : RemindersAction
    data object SavePostponement : RemindersAction
    data class QuickComplete(val id: String) : RemindersAction
    data class QuickPostpone(val id: String) : RemindersAction
    data class ApplyPostponePreset(val preset: ReminderPostponePreset) : RemindersAction
    data class SetDatePreset(val preset: ReminderDatePreset) : RemindersAction
    data object SaveAnyway : RemindersAction
    data class ViewSimilar(val id: String) : RemindersAction
    data object ReturnToDraft : RemindersAction
    data object HelpOrganize : RemindersAction
    data object TitleEditingFinished : RemindersAction
    data object IgnoreTitleSuggestion : RemindersAction
    data object OpenIdeas : RemindersAction
    data class SelectIdeaCategory(val category: ReminderEducationCategory) : RemindersAction
    data class SetIdeaSearch(val text: String) : RemindersAction
    data class UseIdea(val title: String) : RemindersAction
    data class OpenOptions(val id: String) : RemindersAction
    data class RequestSuggestions(val text: String = "Dame ideas de recordatorios para el cuidado") : RemindersAction
    data class UseSuggestion(val title: String) : RemindersAction
    data object UseSuggestedTitle : RemindersAction
    data class Tick(val now: LocalDateTime) : RemindersAction
}

/** One retained state supplies Plan, history and the independent Mi día carousel. Drafts stay in memory. */
class RemindersViewModel(
    repository: CareReminderRepository,
    val actor: CareReminderActor,
    private val reconcile: suspend () -> Boolean = { true },
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
    private val scopeOverride: CoroutineScope? = null,
    private val assistTitle: suspend (String) -> ReminderTitleAssistance = {
        ReminderTitleAssistance(message = "La ayuda no está disponible en este momento. Puedes escribir tu recordatorio.")
    },
    ideaCatalog: List<ReminderIdeaGroup> = emptyList(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        RemindersUiState(personUnderCareId = actor.personUnderCareId, now = nowProvider(), ideaCatalog = ideaCatalog),
    )
    val state = mutableState.asStateFlow()
    private val scope get() = scopeOverride ?: viewModelScope
    private val manage = ManageCareReminders(repository)
    private val getReminders = GetCareReminders(repository)
    private var loadInFlight = false
    private var reloadPending = false
    private var loadEpoch = 0L
    private var assistanceJob: Job? = null
    private var assistanceEpoch = 0L
    private var lastAssistedTitle: String? = null
    private var ignoredTitle: String? = null

    init { reload() }

    fun onAction(action: RemindersAction) {
        if (mutableState.value.saving) return
        val current = mutableState.value
        when (action) {
            RemindersAction.Reload -> reload()
            RemindersAction.OpenMain -> {
                invalidateAssistance()
                mutableState.update { it.copy(page = RemindersPage.MAIN, sheet = null, form = null, error = null,
                    viewingSimilar = false, similarReminders = emptyList(), assistance = null, assistanceLoading = false,
                    assistanceMessage = null, ideaSearch = "") }
            }
            RemindersAction.OpenPrevious -> mutableState.update { it.copy(page = RemindersPage.PREVIOUS, error = null) }
            RemindersAction.Create -> openForm(null)
            is RemindersAction.Open -> openDetail(action.id)
            is RemindersAction.OpenOptions -> openDetail(action.id)
            RemindersAction.Edit -> current.selected?.takeIf { it.status == CareReminderStatus.PENDING }?.let { openForm(it) }
            RemindersAction.CreateAnother -> openForm(current.selected, duplicate = true)
            RemindersAction.Postpone -> current.selected?.let(::openPostponement)
            is RemindersAction.QuickPostpone -> current.active.firstOrNull { it.id == action.id }?.let(::openPostponement)
            is RemindersAction.ApplyPostponePreset -> {
                val record = current.selected ?: return
                if (current.sheet != ReminderSheet.POSTPONE || record.status != CareReminderStatus.PENDING) return
                val requestedAt = nowProvider()
                persist(CareReminderCommand.Postpone(UUID.randomUUID().toString(), record.id, record.revision,
                    reminderPostponementTime(action.preset, requestedAt)))
            }
            is RemindersAction.SetDatePreset -> {
                val form = current.form ?: return
                val date = nowProvider().toLocalDate().plusDays(if (action.preset == ReminderDatePreset.TOMORROW) 1 else 0)
                onAction(RemindersAction.ChangeForm(form.copy(scheduledAt = LocalDateTime.of(date, form.scheduledAt.toLocalTime()))))
            }
            RemindersAction.Complete -> close(CareReminderStatus.DONE)
            is RemindersAction.QuickComplete -> current.active.firstOrNull { it.id == action.id }?.let {
                close(CareReminderStatus.DONE, it)
            }
            RemindersAction.RequestDiscard -> mutableState.update { it.copy(sheet = ReminderSheet.DISCARD, error = null) }
            RemindersAction.ConfirmDiscard -> close(CareReminderStatus.DISCARDED)
            RemindersAction.DismissSheet -> {
                if (current.viewingSimilar) returnToDraft()
                else if (current.sheet == ReminderSheet.SUGGESTIONS && current.form != null) {
                    invalidateAssistance()
                    mutableState.update { it.copy(sheet = ReminderSheet.FORM, assistanceLoading = false,
                        assistance = null, assistanceMessage = null) }
                } else {
                    invalidateAssistance()
                    mutableState.update { it.copy(sheet = null, form = null, selectedId = null, error = null,
                        viewingSimilar = false, similarReminders = emptyList(), assistanceLoading = false,
                        assistance = null, assistanceMessage = null, ideaSearch = "") }
                }
            }
            RemindersAction.BackToDetail -> mutableState.update { it.copy(sheet = ReminderSheet.DETAIL, form = null,
                error = null, similarReminders = emptyList()) }
            is RemindersAction.ChangeForm -> {
                val old = current.form ?: return
                if (action.value.title != old.title && !CaptureLimit.REMINDER_TITLE.acceptsEdit(old.title, action.value.title)) return
                if (action.value.details != old.details && !CaptureLimit.REMINDER_DETAILS.acceptsEdit(old.details, action.value.details)) return
                val updated = action.value.copy(id = old.id, operationId = old.operationId, editingRevision = old.editingRevision,
                    titleAssistanceEnabled = old.titleAssistanceEnabled || action.value.title != old.title,
                    notificationsEnabled = false)
                val changedTitle = updated.title != old.title
                if (changedTitle) { invalidateAssistance(); ignoredTitle = null; lastAssistedTitle = null }
                mutableState.update { it.copy(form = updated, error = null, similarReminders = emptyList(),
                    assistance = if (changedTitle) null else it.assistance,
                    assistanceMessage = if (changedTitle) null else it.assistanceMessage,
                    assistanceLoading = if (changedTitle) false else it.assistanceLoading) }
            }
            RemindersAction.Save -> save(postponement = false)
            RemindersAction.SaveAnyway -> if (current.similarReminders.isNotEmpty()) save(postponement = false, allowSimilar = true)
            RemindersAction.SavePostponement -> save(postponement = true)
            is RemindersAction.ViewSimilar -> if (current.form != null && current.similarReminders.any { it.id == action.id }) {
                mutableState.update { it.copy(selectedId = action.id, sheet = ReminderSheet.DETAIL, viewingSimilar = true) }
            }
            RemindersAction.ReturnToDraft -> returnToDraft()
            RemindersAction.HelpOrganize -> if (current.form?.title.isNullOrBlank()) openIdeas() else finishTitle(explicit = true)
            RemindersAction.TitleEditingFinished -> finishTitle(explicit = false)
            RemindersAction.IgnoreTitleSuggestion -> {
                ignoredTitle = current.form?.title?.let(EducationTokenizer::normalize)
                invalidateAssistance()
                mutableState.update { it.copy(assistance = null, assistanceMessage = null, assistanceLoading = false) }
            }
            RemindersAction.OpenIdeas -> openIdeas()
            is RemindersAction.SelectIdeaCategory -> mutableState.update {
                it.copy(ideaCategory = action.category, ideaSearch = "")
            }
            is RemindersAction.SetIdeaSearch -> mutableState.update { it.copy(ideaSearch = limitTextCharacters(action.text, CaptureLimit.SEARCH.maximum)) }
            is RemindersAction.UseIdea -> useIdea(action.title)
            is RemindersAction.RequestSuggestions -> {
                if (current.form != null || openForm(null)) {
                    openIdeas()
                    if (action.text.isNotBlank() && action.text != "Dame ideas de recordatorios para el cuidado") {
                        mutableState.update { it.copy(ideaSearch = limitTextCharacters(action.text, CaptureLimit.SEARCH.maximum)) }
                    }
                }
            }
            is RemindersAction.UseSuggestion -> {
                if (current.form != null || openForm(null)) useIdea(action.title)
            }
            RemindersAction.UseSuggestedTitle -> current.assistance?.suggestedTitle?.let(::useIdea)
            is RemindersAction.Tick -> mutableState.update { it.copy(now = action.now) }
        }
    }

    /** Card and overflow are two entry points to the same state and the same modal content. */
    private fun openDetail(id: String) {
        if (mutableState.value.records.none { it.id == id }) return
        invalidateAssistance()
        mutableState.update {
            it.copy(selectedId = id, sheet = ReminderSheet.DETAIL, form = null, error = null,
                viewingSimilar = false, similarReminders = emptyList(), assistance = null,
                assistanceLoading = false, assistanceMessage = null)
        }
    }

    private fun openPostponement(record: CareReminder) {
        if (record.status != CareReminderStatus.PENDING) return
        invalidateAssistance()
        mutableState.update { it.copy(selectedId = record.id, sheet = ReminderSheet.POSTPONE,
            form = record.asForm().copy(scheduledAt = nextReminderTime(nowProvider())), error = null,
            viewingSimilar = false, similarReminders = emptyList(), assistance = null, assistanceLoading = false,
            assistanceMessage = null) }
    }

    private fun returnToDraft() {
        val form = mutableState.value.form ?: return
        mutableState.update { it.copy(sheet = ReminderSheet.FORM, viewingSimilar = false,
            selectedId = form.id.takeIf { form.editingRevision != null }, error = null) }
    }

    private fun invalidateAssistance() {
        assistanceEpoch++
        assistanceJob?.cancel()
        assistanceJob = null
    }

    private fun finishTitle(explicit: Boolean) {
        val current = mutableState.value
        val form = current.form ?: return
        if (current.sheet != ReminderSheet.FORM || current.viewingSimilar) return
        val normalized = EducationTokenizer.normalize(form.title)
        mutableState.update { it.copy(similarReminders =
            similarActiveCareReminders(form.title, it.records, actor.personUnderCareId, form.id)) }
        if (!form.titleAssistanceEnabled || normalized.isBlank() ||
            (!explicit && (normalized == ignoredTitle || normalized == lastAssistedTitle))) return
        lastAssistedTitle = normalized
        requestAssistance(form.title, explicit)
    }

    private fun openIdeas() {
        val current = mutableState.value
        val form = current.form ?: return
        val category = current.assistance?.category
            ?: current.ideaCatalog.firstOrNull { group ->
                matchingReminderIdeas(listOf(group), group.category, form.title).isNotEmpty()
            }?.category
            ?: current.ideaCatalog.firstOrNull()?.category
        val related = matchingReminderIdeas(current.ideaCatalog, category, form.title)
        invalidateAssistance()
        mutableState.update { it.copy(sheet = ReminderSheet.SUGGESTIONS, ideaCategory = category,
            ideaSearch = limitTextCharacters(form.title.takeIf { _ -> related.isNotEmpty() }.orEmpty(), CaptureLimit.SEARCH.maximum),
            assistanceLoading = false, assistanceMessage = null) }
    }

    private fun useIdea(title: String) {
        val current = mutableState.value
        val form = current.form ?: return
        if (title.isBlank() || !fitsTextLimit(title, CaptureLimit.REMINDER_TITLE.maximum)) return
        invalidateAssistance()
        lastAssistedTitle = EducationTokenizer.normalize(title)
        ignoredTitle = null
        val updated = form.copy(title = title)
        mutableState.update { it.copy(form = updated, sheet = ReminderSheet.FORM,
            ideaSearch = "", assistance = null, assistanceMessage = null, assistanceLoading = false,
            similarReminders = similarActiveCareReminders(title, it.records, actor.personUnderCareId, form.id)) }
    }

    private fun requestAssistance(text: String, explicit: Boolean) {
        invalidateAssistance()
        val epoch = assistanceEpoch
        mutableState.update { it.copy(assistanceLoading = true, assistance = null, assistanceMessage = null) }
        assistanceJob = scope.launch {
            try {
                val answer = assistTitle(text)
                if (epoch == assistanceEpoch && mutableState.value.form?.title == text) mutableState.update {
                    val compact = answer.copy(suggestions = emptyList(), relatedTopics = emptyList(), message = null)
                    val helpful = compact.suggestedTitle != null || compact.clarifications.isNotEmpty()
                    it.copy(assistanceLoading = false, assistance = compact.takeIf { _ -> helpful },
                        assistanceMessage = if (explicit && !helpful)
                            "Describe una acción concreta, por ejemplo: Comprar pilas" else null)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (epoch == assistanceEpoch) mutableState.update {
                    it.copy(assistanceLoading = false, assistance = null,
                        assistanceMessage = if (explicit) "Describe una acción concreta, por ejemplo: Comprar pilas" else null)
                }
            }
        }
    }

    private fun reload() {
        if (loadInFlight) {
            reloadPending = true
            // An external completion can make the in-flight snapshot obsolete.
            loadEpoch++
            return
        }
        loadInFlight = true
        val epoch = ++loadEpoch
        mutableState.update { it.copy(loading = true, error = null, loadFailed = false) }
        scope.launch {
            try {
                val loaded = getReminders(actor)
                if (epoch == loadEpoch) mutableState.update {
                    it.copy(records = loaded, loading = false, loadFailed = false, now = nowProvider())
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (epoch == loadEpoch) mutableState.update {
                    it.copy(loading = false, loadFailed = true, error = "No pudimos cargar los recordatorios. Intenta de nuevo.")
                }
            } finally {
                loadInFlight = false
                if (reloadPending) {
                    reloadPending = false
                    reload()
                }
            }
        }
    }

    private fun openForm(record: CareReminder?, duplicate: Boolean = false): Boolean {
        val editing = record != null && !duplicate
        if (!editing && mutableState.value.atLimit) {
            mutableState.update { it.copy(error = "Ya tienes 10 recordatorios activos. Marca uno como hecho o descártalo para crear otro.") }
            return false
        }
        val form = if (editing) record.asForm() else ReminderForm(
            id = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString(),
            title = record?.title.orEmpty(), details = record?.details.orEmpty(),
            scheduledAt = nextReminderTime(nowProvider()), notificationsEnabled = false,
            titleAssistanceEnabled = !duplicate || record?.status == CareReminderStatus.PENDING,
        )
        invalidateAssistance()
        lastAssistedTitle = null
        ignoredTitle = null
        mutableState.update { it.copy(sheet = ReminderSheet.FORM, form = form, error = null,
            ideaSearch = "", ideaCategory = null,
            selectedId = record?.id.takeIf { editing }, viewingSimilar = false, similarReminders = emptyList(),
            assistanceLoading = false, assistance = null, assistanceMessage = null) }
        return true
    }

    private fun save(postponement: Boolean, allowSimilar: Boolean = false) {
        val current = mutableState.value
        val form = current.form ?: return
        if (!postponement && !allowSimilar) {
            val similar = similarActiveCareReminders(form.title, current.records, actor.personUnderCareId, form.id)
            if (similar.isNotEmpty()) {
                mutableState.update { it.copy(similarReminders = similar, error = null) }
                return
            }
        }
        val command = if (postponement) {
            CareReminderCommand.Postpone(form.operationId, form.id, form.editingRevision ?: return, form.scheduledAt)
        } else if (form.editingRevision != null) {
            CareReminderCommand.Edit(form.operationId, form.id, form.editingRevision,
                CareReminderDraft(form.title, form.details, form.scheduledAt, notificationsEnabled = false))
        } else {
            CareReminderCommand.Create(form.operationId, form.id,
                CareReminderDraft(form.title, form.details, form.scheduledAt, notificationsEnabled = false))
        }
        persist(command)
    }

    private fun close(status: CareReminderStatus, record: CareReminder? = mutableState.value.selected) {
        record ?: return
        if (record.status != CareReminderStatus.PENDING) return
        persist(CareReminderCommand.Close(UUID.randomUUID().toString(), record.id, record.revision, status))
    }

    private fun persist(command: CareReminderCommand) {
        invalidateAssistance()
        mutableState.update { it.copy(saving = true, error = null, assistanceLoading = false) }
        scope.launch {
            try {
                val saved = manage(actor, command, nowProvider())
                // An older reload must never replace a newer committed edit or completion.
                loadEpoch++
                // Commit remains the source of truth while retiring notices left by an older APK.
                mutableState.update {
                    val records = it.records.filterNot { record -> record.id == saved.id } + saved
                    it.copy(records = records, loading = false, loadFailed = false,
                        saving = false, sheet = null, form = null, selectedId = null, now = nowProvider(),
                        similarReminders = emptyList(), viewingSimilar = false, assistance = null, assistanceMessage = null,
                        ideaSearch = "")
                }
                try { reconcile() } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { /* A later app entry retries technical notice retirement. */ }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (validation: CareReminderValidationException) {
                mutableState.update { it.copy(saving = false, error = reminderProblemMessage(validation.problem)) }
            } catch (_: Exception) {
                mutableState.update { it.copy(saving = false, error = "No pudimos guardar el cambio. Tu información anterior se conserva. Intenta de nuevo.") }
            }
        }
    }
}

private fun CareReminder.asForm() = ReminderForm(
    id, UUID.randomUUID().toString(), title, details.orEmpty(), scheduledAt,
    notificationsEnabled = false, editingRevision = revision, detailsExpanded = false,
)

internal fun nextReminderTime(now: LocalDateTime): LocalDateTime =
    now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)

internal fun reminderProblemMessage(problem: CareReminderProblem): String = when (problem) {
    CareReminderProblem.TITLE_REQUIRED -> "Escribe qué necesitas recordar."
    CareReminderProblem.TITLE_TOO_LONG -> "El título admite hasta 100 caracteres."
    CareReminderProblem.DETAILS_TOO_LONG -> "Los detalles admiten hasta 500 caracteres."
    CareReminderProblem.FUTURE_REQUIRED -> "Elige una fecha y hora futuras."
    CareReminderProblem.ACTIVE_LIMIT -> "Ya tienes 10 recordatorios activos. Marca uno como hecho o descártalo para crear otro."
    CareReminderProblem.CONFLICT -> "Este recordatorio cambió. Cierra esta vista y vuelve a abrirlo."
    CareReminderProblem.CLOSED -> "Este recordatorio ya está cerrado."
    CareReminderProblem.NOT_FOUND -> "Este recordatorio ya no está disponible. Vuelve a cargar la lista."
    CareReminderProblem.INVALID_OPERATION -> "No pudimos aplicar este cambio. Intenta de nuevo."
}
