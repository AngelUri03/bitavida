package com.bitavida.app.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.model.ObservationCategory
import com.bitavida.app.domain.usecase.AskBitavidaAi
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.validation.limitTextCharacters
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Route only explicitly named reminder questions to its separate local classifier.
 * Unknown/ambiguous phrases remain with the shared classifier, which can request clarification.
 */
internal fun isExplicitReminderQuestion(question: String): Boolean =
    EducationTokenizer.words(question).any { it == "recordatorio" || it == "recordatorios" }

data class BitavidaAiUiState(
    val question: String = "",
    val group: EducationGroup? = null,
    val navigationContext: EducationGroup? = null,
    val centralContext: Boolean = false,
    val analyzing: Boolean = false,
    val result: EducationResult? = null,
    val requestCount: Int = 0,
    val resultCount: Int = 0,
    val inputLimitReached: Boolean = false,
    val pages: List<AiPageEntry> = listOf(AiPageEntry()),
    val libraryGroup: EducationGroup? = null,
    val librarySearch: String = "",
    val sourceVisibility: Map<Long, Boolean> = emptyMap(),
    val detailsVisibility: Map<Long, Boolean> = emptyMap(),
    val relatedVisibility: Map<Long, Boolean> = emptyMap(),
    val libraryFamily: String? = null,
    val observationCategory: ObservationCategory? = null,
    val reminderCategory: ReminderEducationCategory? = null,
    val drugSections: Map<Long, Set<DrugIntent>> = emptyMap(),
    val procedureSessionActive: Boolean = false,
    val procedureSearch: String = "",
    val procedureCategory: ProcedureCategory? = null,
    val procedureSections: Map<Long, Set<String>> = emptyMap(),
) {
    val careCaptureContext: Boolean get() = navigationContext != EducationGroup.MEDICATIONS
    val medicationContext: Boolean get() = navigationContext == EducationGroup.MEDICATIONS
    val observationContext: Boolean get() = navigationContext == EducationGroup.OBSERVATIONS
    val reminderContext: Boolean get() = navigationContext == EducationGroup.REMINDERS
    val current: AiPageEntry get() = pages.last()
    val canGoBack: Boolean get() = pages.size > 1
    val suggestions: List<EducationTopic>
        get() = when {
            reminderContext -> EducationTopic.entries.filter { it.group == EducationGroup.REMINDERS }.take(3)
            observationContext -> listOf(
                EducationTopic.OBS_001_FEET_REVIEW,
                EducationTopic.OBS_010_THIRST_DRY_MOUTH,
                EducationTopic.OBS_022_FALL,
                EducationTopic.OBSERVATION_GENERAL,
            )
            group == null -> listOf(EducationTopic.PRESSURE_PURPOSE, EducationTopic.GLUCOSE_PURPOSE)
            else -> EducationTopic.entries.filter { it.group == group }.take(2)
        }
}

sealed interface BitavidaAiAction {
    data class QuestionChanged(val value: String) : BitavidaAiAction
    data class Ask(val question: String? = null) : BitavidaAiAction
    data class ReadTopic(val topic: EducationTopic) : BitavidaAiAction
    data class ReadDrug(val id: String, val intent: DrugIntent = DrugIntent.IDENTIFY) : BitavidaAiAction
    data class OpenRegisteredDrug(val name: String) : BitavidaAiAction
    data class SelectDrugFamily(val family: String?) : BitavidaAiAction
    data class SelectObservationCategory(val category: ObservationCategory?) : BitavidaAiAction
    data class SelectReminderCategory(val category: ReminderEducationCategory?) : BitavidaAiAction
    data class ToggleDrugSection(val pageId: Long, val intent: DrugIntent) : BitavidaAiAction
    data class SelectGroup(val group: EducationGroup?) : BitavidaAiAction
    data class NavigationContext(val group: EducationGroup?) : BitavidaAiAction
    data class CentralContext(val enabled: Boolean) : BitavidaAiAction
    data class OpenLibrary(val group: EducationGroup? = null) : BitavidaAiAction
    data class SearchLibrary(val value: String) : BitavidaAiAction
    data class ToggleSource(val pageId: Long) : BitavidaAiAction
    data class ToggleDetails(val pageId: Long) : BitavidaAiAction
    data class ToggleRelated(val pageId: Long) : BitavidaAiAction
    data class StartProcedureSession(val initialIds: List<String> = emptyList()) : BitavidaAiAction
    data object OpenProcedureHome : BitavidaAiAction
    data object OpenProcedureLibrary : BitavidaAiAction
    data class OpenProcedureGuide(val id: String) : BitavidaAiAction
    data class ReadProcedureSection(val id: String, val section: String) : BitavidaAiAction
    data class AskProcedure(val question: String? = null) : BitavidaAiAction
    data class SearchProcedure(val value: String) : BitavidaAiAction
    data class SelectProcedureCategory(val category: ProcedureCategory?) : BitavidaAiAction
    data class ToggleProcedureSection(val pageId: Long, val key: String) : BitavidaAiAction
    data object OpenExplanation : BitavidaAiAction
    data object NewQuestion : BitavidaAiAction
    data object Back : BitavidaAiAction
    data object Clear : BitavidaAiAction
}

enum class BitavidaAiEffect { HIDE_KEYBOARD }

/** All query, result, search and navigation data lives only in this retained session. */
class BitavidaAiViewModel(
    private val ask: AskBitavidaAi,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val scope = scopeOverride ?: viewModelScope
    private val mutableState = MutableStateFlow(BitavidaAiUiState())
    val state = mutableState.asStateFlow()
    private val effectChannel = Channel<BitavidaAiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var request: Job? = null
    private var generation = 0L
    private var nextPageId = 1L
    // Session-only context: never persisted or derived from personal care records.
    private var lastProcedureId: String? = null
    private val scrollPositions = mutableMapOf<Long, Pair<Int, Int>>()
    val catalog by lazy { EducationTopic.entries.map { ask.read(it).entry } }
    val drugs by lazy { ask.drugCatalog() }
    val procedureGuides by lazy { ask.procedureCatalog() }
    fun searchProcedureGuides(query: String): List<ProcedureGuide> = ask.searchProcedures(query)
    fun askProcedureGuide(question: String): ProcedureQuestionResult = ask.askProcedure(question)
    fun registeredProcedureGuides(name: String): List<ProcedureGuide> = ask.registeredProcedures(name)
    fun registeredDrugGuides(name: String): List<DrugEducation> = ask.resolveRegisteredDrug(name)

    fun scrollIndex(id: Long): Int = scrollPositions[id]?.first ?: 0
    fun scrollOffset(id: Long): Int = scrollPositions[id]?.second ?: 0
    fun rememberScroll(id: Long, index: Int, offset: Int) {
        if (state.value.pages.any { it.id == id }) {
            scrollPositions[id] = index.coerceAtLeast(0) to offset.coerceAtLeast(0)
        }
    }

    fun onAction(action: BitavidaAiAction) {
        when (action) {
            is BitavidaAiAction.QuestionChanged -> {
                if (state.value.careCaptureContext && !CaptureLimit.CARE_QUESTION.acceptsEdit(state.value.question, action.value)) return
                if (!state.value.careCaptureContext && action.value.length > EducationTokenizer.MAX_QUESTION_LENGTH) {
                    mutableState.update { it.copy(inputLimitReached = true) }
                } else {
                    cancelRequest()
                    mutableState.update {
                        it.copy(question = action.value, analyzing = false, result = null, inputLimitReached = false)
                    }
                }
            }
            is BitavidaAiAction.Ask -> submit(action.question ?: state.value.question)
            is BitavidaAiAction.ReadTopic -> {
                lastProcedureId = null
                cancelRequest()
                val answer = ask.read(action.topic)
                push(AiPage.DETAIL, answer)
                mutableState.update { it.copy(analyzing = false, result = answer, resultCount = it.resultCount + 1) }
                effectChannel.trySend(BitavidaAiEffect.HIDE_KEYBOARD)
            }
            is BitavidaAiAction.ReadDrug -> {
                lastProcedureId = null
                cancelRequest()
                val answer = ask.readDrug(action.id, action.intent) ?: return
                push(AiPage.DETAIL, answer)
                mutableState.update { it.copy(result = answer, resultCount = it.resultCount + 1) }
                effectChannel.trySend(BitavidaAiEffect.HIDE_KEYBOARD)
            }
            is BitavidaAiAction.OpenRegisteredDrug -> {
                lastProcedureId = null
                cancelRequest()
                val choices = registeredDrugGuides(action.name)
                if (choices.isEmpty()) return
                val result = if (choices.size == 1 && !ask.requiresRegisteredDrugConfirmation(action.name)) {
                    ask.readDrug(choices.single().id)
                } else {
                    EducationResult.DrugOptions(choices,
                        "Confirma la formulación del envase y elige la ficha documentada que deseas conocer.")
                }
                push(if (result is EducationResult.DrugAnswer) AiPage.DETAIL else AiPage.ANSWER, result)
                mutableState.update { it.copy(result = result, analyzing = false) }
            }
            is BitavidaAiAction.SelectDrugFamily -> mutableState.update { it.copy(libraryFamily = action.family) }
            is BitavidaAiAction.SelectObservationCategory -> mutableState.update {
                it.copy(observationCategory = action.category)
            }
            is BitavidaAiAction.SelectReminderCategory -> mutableState.update { it.copy(reminderCategory = action.category) }
            is BitavidaAiAction.ToggleDrugSection -> mutableState.update {
                if (it.pages.none { page -> page.id == action.pageId }) it else {
                    val visible = it.drugSections[action.pageId].orEmpty()
                    val changed = if (action.intent in visible) visible - action.intent else visible + action.intent
                    it.copy(drugSections = it.drugSections + (action.pageId to changed))
                }
            }
            is BitavidaAiAction.SelectGroup -> mutableState.update {
                it.copy(group = action.group, libraryGroup = action.group)
            }
            is BitavidaAiAction.NavigationContext -> {
                if (state.value.navigationContext != action.group) lastProcedureId = null
                mutableState.update {
                    if (it.navigationContext == action.group) it else
                        it.copy(navigationContext = action.group, group = action.group)
                }
            }
            is BitavidaAiAction.CentralContext -> {
                if (state.value.centralContext != action.enabled) lastProcedureId = null
                mutableState.update { it.copy(centralContext = action.enabled) }
            }
            is BitavidaAiAction.OpenLibrary -> {
                cancelRequest()
                mutableState.update {
                    it.copy(
                        libraryGroup = action.group,
                        observationCategory = null,
                        reminderCategory = null,
                        librarySearch = "",
                        analyzing = false,
                    )
                }
                push(AiPage.LIBRARY)
            }
            is BitavidaAiAction.SearchLibrary -> mutableState.update {
                it.copy(librarySearch = if (it.careCaptureContext) limitTextCharacters(action.value, 100) else action.value.take(100))
            }
            is BitavidaAiAction.ToggleSource -> mutableState.update {
                if (it.pages.none { page -> page.id == action.pageId }) it else
                    it.copy(sourceVisibility = it.sourceVisibility + (action.pageId to !(it.sourceVisibility[action.pageId] ?: false)))
            }
            is BitavidaAiAction.ToggleDetails -> mutableState.update {
                val page = it.pages.find { page -> page.id == action.pageId }
                if (page == null) it else it.copy(detailsVisibility = it.detailsVisibility +
                    (action.pageId to !(it.detailsVisibility[action.pageId] ?: (page.page == AiPage.DETAIL && !it.reminderContext))))
            }
            is BitavidaAiAction.ToggleRelated -> mutableState.update {
                if (it.pages.none { page -> page.id == action.pageId }) it else
                    it.copy(relatedVisibility = it.relatedVisibility + (action.pageId to !(it.relatedVisibility[action.pageId] ?: false)))
            }
            is BitavidaAiAction.StartProcedureSession -> {
                if (state.value.procedureSessionActive) return
                cancelRequest()
                scrollPositions.clear()
                val guides = action.initialIds.distinct().mapNotNull { id -> procedureGuides.firstOrNull { it.id == id } }
                lastProcedureId = guides.singleOrNull()?.id
                val root = when (guides.size) {
                    0 -> AiPageEntry(nextPageId++, AiPage.PROCEDURE_HOME)
                    1 -> AiPageEntry(nextPageId++, AiPage.PROCEDURE_DETAIL, procedureId = guides.single().id)
                    else -> AiPageEntry(nextPageId++, AiPage.PROCEDURE_ANSWER,
                        procedureResult = ProcedureQuestionResult.Choices(guides))
                }
                mutableState.value = BitavidaAiUiState(pages = listOf(root), procedureSessionActive = true)
            }
            BitavidaAiAction.OpenProcedureHome -> { cancelRequest(); pushProcedure(AiPage.PROCEDURE_HOME) }
            BitavidaAiAction.OpenProcedureLibrary -> {
                cancelRequest()
                mutableState.update { it.copy(procedureSearch = "", procedureCategory = null) }
                pushProcedure(AiPage.PROCEDURE_LIBRARY)
            }
            is BitavidaAiAction.OpenProcedureGuide -> {
                if (procedureGuides.none { it.id == action.id }) return
                cancelRequest(); pushProcedure(AiPage.PROCEDURE_DETAIL, id = action.id)
            }
            is BitavidaAiAction.ReadProcedureSection -> {
                val guide = procedureGuides.firstOrNull { it.id == action.id } ?: return
                val specific = guide.relatedQuestions.firstOrNull { it.id == action.section }
                val label = specific?.question ?: when (action.section) {
                    "purpose" -> "¿Para qué sirve ${guide.title}?"
                    "preparation" -> "¿Qué preparación necesita ${guide.title}?"
                    "questions" -> "¿Qué puedo preguntar al médico sobre ${guide.title}?"
                    else -> return
                }
                val section = specific?.sectionId ?: action.section
                if (procedureSectionText(guide, section) == null) return
                cancelRequest()
                pushProcedure(AiPage.PROCEDURE_ANSWER, result = ProcedureQuestionResult.Match(guide),
                    question = label, section = section)
            }
            is BitavidaAiAction.AskProcedure -> {
                val question = action.question ?: state.value.question
                if (question.isBlank() || !CaptureLimit.CARE_QUESTION.acceptsStoredOrNew(question)) return
                cancelRequest()
                val guarded = ask.askProcedure(question)
                val followup = octFollowup(question, guarded)
                val result = followup?.first ?: guarded
                mutableState.update { it.copy(question = question, analyzing = false) }
                pushProcedure(AiPage.PROCEDURE_ANSWER, result = result, question = question,
                    section = followup?.second)
                effectChannel.trySend(BitavidaAiEffect.HIDE_KEYBOARD)
            }
            is BitavidaAiAction.SearchProcedure -> mutableState.update {
                it.copy(procedureSearch = limitTextCharacters(action.value, 100))
            }
            is BitavidaAiAction.SelectProcedureCategory -> mutableState.update { it.copy(procedureCategory = action.category) }
            is BitavidaAiAction.ToggleProcedureSection -> mutableState.update {
                if (it.pages.none { page -> page.id == action.pageId }) it else {
                    val sections = it.procedureSections[action.pageId].orEmpty()
                    val updated = if (action.key in sections) sections - action.key else sections + action.key
                    it.copy(procedureSections = it.procedureSections + (action.pageId to updated))
                }
            }
            BitavidaAiAction.OpenExplanation -> { cancelRequest(); push(AiPage.ABOUT) }
            BitavidaAiAction.NewQuestion -> {
                cancelRequest()
                val home = state.value.pages.first()
                scrollPositions.keys.retainAll(setOf(home.id))
                // The explicit "another question" action returns to the composer.
                scrollPositions[home.id] = 0 to 0
                mutableState.update {
                    it.copy(pages = listOf(home), question = "", result = null, analyzing = false, inputLimitReached = false,
                        sourceVisibility = emptyMap(), detailsVisibility = emptyMap(), relatedVisibility = emptyMap(), drugSections = emptyMap(),
                        procedureSections = emptyMap())
                }
            }
            BitavidaAiAction.Back -> {
                cancelRequest()
                mutableState.update {
                    if (!it.canGoBack) it.copy(analyzing = false) else {
                        scrollPositions.remove(it.current.id)
                        val retained = it.pages.dropLast(1)
                        it.copy(pages = retained, result = retained.last().answer, analyzing = false,
                            sourceVisibility = it.sourceVisibility - it.current.id,
                            detailsVisibility = it.detailsVisibility - it.current.id,
                            relatedVisibility = it.relatedVisibility - it.current.id,
                            drugSections = it.drugSections - it.current.id,
                            procedureSections = it.procedureSections - it.current.id)
                    }
                }
                val current = state.value.current
                lastProcedureId = current.procedureId
                    ?: (current.procedureResult as? ProcedureQuestionResult.Match)?.guide?.id
            }
            BitavidaAiAction.Clear -> {
                cancelRequest()
                lastProcedureId = null
                scrollPositions.clear()
                mutableState.update {
                    BitavidaAiUiState(group = it.group, navigationContext = it.navigationContext, centralContext = it.centralContext)
                }
            }
        }
    }

    private fun push(page: AiPage, answer: EducationResult? = null, question: String = "") {
        val entry = AiPageEntry(nextPageId++, page, answer, question)
        mutableState.update {
            val expanded = if (answer is EducationResult.DrugAnswer)
                setOf(if (answer.intent == DrugIntent.IDENTIFY) DrugIntent.PURPOSE else answer.intent) else emptySet()
            it.copy(pages = it.pages + entry, analyzing = false,
                drugSections = it.drugSections + (entry.id to expanded))
        }
    }

    private fun pushProcedure(page: AiPage, id: String? = null,
        result: ProcedureQuestionResult? = null, question: String = "", section: String? = null) {
        val entry = AiPageEntry(nextPageId++, page, submittedQuestion = question,
            procedureId = id, procedureResult = result, procedureSection = section)
        when {
            id != null -> lastProcedureId = id
            result is ProcedureQuestionResult.Match -> lastProcedureId = result.guide.id
            result != null -> lastProcedureId = null
        }
        mutableState.update { it.copy(pages = it.pages + entry, analyzing = false) }
    }

    /** Resolve only a narrowly recognized question against an explicitly opened OCT guide. */
    private fun octFollowup(question: String, guarded: ProcedureQuestionResult): Pair<ProcedureQuestionResult, String>? {
        if (guarded is ProcedureQuestionResult.Urgent || guarded is ProcedureQuestionResult.OutsideScope ||
            guarded is ProcedureQuestionResult.Choices) return null
        val section = octFollowupSection(question) ?: return null
        // A direct question about another guide still belongs to that guide. The sole
        // exception is an explicitly phrased comparison with retinography after OCT.
        val words = EducationTokenizer.words(question).toSet()
        val retinographyComparison = lastProcedureId == "oct" && section == "comparison" &&
            guarded is ProcedureQuestionResult.Match && guarded.guide.id == "retinografia" &&
            "retinografia" in words &&
            words.any { it in setOf("mismo", "misma", "igual", "diferencia", "diferencias", "comparacion", "entre") }
        if (guarded is ProcedureQuestionResult.Match && guarded.guide.id != "oct" && !retinographyComparison) return null
        if (guarded is ProcedureQuestionResult.Unknown && lastProcedureId != "oct") return null
        val oct = procedureGuides.firstOrNull { it.id == "oct" } ?: return null
        return ProcedureQuestionResult.Match(oct) to section
    }

    private fun submit(question: String) {
        if (question.isBlank()) return
        if (state.value.careCaptureContext && !CaptureLimit.CARE_QUESTION.acceptsStoredOrNew(question)) return
        val submittedQuestion = if (state.value.careCaptureContext) question else question.take(EducationTokenizer.MAX_QUESTION_LENGTH)
        cancelRequest()
        val id = generation
        val navigationContext = state.value.navigationContext
        // The general model has no reminder classes. A clearly stated reminder question is
        // dispatched to the existing reminder model and its stricter educational guard.
        val queryContext = navigationContext ?: if (state.value.centralContext && isExplicitReminderQuestion(question))
            EducationGroup.REMINDERS else null
        val contextualSuggestions = if (queryContext == EducationGroup.REMINDERS)
            listOf(EducationTopic.REMINDER_IDEAS, EducationTopic.REMINDER_CLEAR_TITLE, EducationTopic.REMINDER_MODULES)
        else state.value.suggestions.take(3)
        mutableState.update {
            it.copy(
                question = submittedQuestion,
                analyzing = true, result = null, inputLimitReached = false,
                requestCount = it.requestCount + 1,
            )
        }
        effectChannel.trySend(BitavidaAiEffect.HIDE_KEYBOARD)
        request = scope.launch {
            var procedureLookupFailed = false
            var procedureSection: String? = null
            val procedure = try {
                if (state.value.centralContext && navigationContext == null)
                    withContext(worker) {
                        // Safety and ambiguity checks precede any session-context lookup.
                        val guarded = ask.askProcedure(question)
                        val followup = octFollowup(question, guarded)
                        procedureSection = followup?.second
                        followup?.first ?: ask.centralProcedureQuestion(question)
                    }
                else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                procedureLookupFailed = true
                null
            }
            if (procedureLookupFailed) {
                if (id == generation) {
                    val unavailable = EducationResult.Fallback(EducationFallback.UNAVAILABLE)
                    push(AiPage.ANSWER, unavailable, submittedQuestion)
                    mutableState.update { it.copy(analyzing = false, result = unavailable, resultCount = it.resultCount + 1) }
                }
                return@launch
            }
            // Explicit reminder requests keep their existing specialized guard and classifier;
            // an acute emergency still takes precedence over every educational context.
            if (procedure != null && (queryContext != EducationGroup.REMINDERS || procedure is ProcedureQuestionResult.Urgent)) {
                if (id == generation) {
                    pushProcedure(AiPage.PROCEDURE_ANSWER, result = procedure, question = submittedQuestion,
                        section = procedureSection)
                    mutableState.update { it.copy(analyzing = false, result = null, resultCount = it.resultCount + 1) }
                }
                return@launch
            }
            if (id == generation) lastProcedureId = null
            val result = try {
                withContext(worker) { ask(question, queryContext) }.forNavigationContext(
                    queryContext,
                    contextualSuggestions,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                EducationResult.Fallback(EducationFallback.UNAVAILABLE)
            }
            if (id == generation) {
                push(AiPage.ANSWER, result, submittedQuestion)
                mutableState.update {
                    it.copy(analyzing = false, result = result, resultCount = it.resultCount + 1)
                }
            }
        }
    }

    private fun EducationResult.forNavigationContext(
        context: EducationGroup?,
        contextualSuggestions: List<EducationTopic>,
    ): EducationResult {
        if (context != EducationGroup.OBSERVATIONS && context != EducationGroup.REMINDERS) return this
        return when (this) {
            is EducationResult.Answer -> if (entry.topic.group == context) this
                else EducationResult.Fallback(EducationFallback.UNCERTAIN, contextualSuggestions)
            is EducationResult.Fallback -> if (reason != EducationFallback.UNCERTAIN) this else copy(
                suggestions = suggestions.filter { it.group == context }
                    .ifEmpty { contextualSuggestions },
            )
            is EducationResult.DrugAnswer, is EducationResult.DrugOptions ->
                EducationResult.Fallback(EducationFallback.UNCERTAIN, contextualSuggestions)
        }
    }

    private fun cancelRequest() { generation++; request?.cancel(); request = null }

    companion object {
        fun factory(ask: AskBitavidaAi): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(BitavidaAiViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return BitavidaAiViewModel(ask) as T
            }
        }
    }
}
