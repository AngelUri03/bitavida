package com.bitavida.app.ui.observations

import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.validation.limitTextCharacters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.ObservationRepository
import com.bitavida.app.domain.usecase.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class ObservationsPage {
    LIBRARY, DETAIL, REGISTER, GUIDE, HISTORY, OCCURRENCE_DETAIL, RECORD_DETAIL,
    FOLLOW_UP, FOLLOW_UP_HISTORY,
}

data class ObservationScheduleDraft(
    val observationId: String,
    val schedule: GlucoseFollowUpDraft,
)

data class ObservationsUiState(
    val loading: Boolean = true,
    val snapshot: ObservationSnapshot = ObservationSnapshot(),
    /** Moment at which [snapshot] was loaded or committed; prevents UI clocks from lagging behind it. */
    val snapshotObservedAt: LocalDateTime = LocalDateTime.MIN,
    val query: String = "",
    val searchExpanded: Boolean = false,
    val onlyWithFollowUp: Boolean = false,
    val expanded: Set<ObservationCategory> = emptySet(),
    val page: ObservationsPage = ObservationsPage.LIBRARY,
    val selectedObservationId: String? = null,
    val selectedRecordId: String? = null,
    val selectedOccurrence: ObservationOccurrence? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val category: ObservationCategory? = null,
    val focusedObservationId: String? = null,
    val draft: ObservationRecord? = null,
    val correctionOriginalId: String? = null,
    val correctionReason: String = "",
    val descriptionProposal: String? = null,
    val scheduleDraft: ObservationScheduleDraft? = null,
    val scheduleStep: Int = 0,
    val scheduleSlotEditor: GlucoseFollowUpSlotDraft? = null,
    val scheduleEditingSlotId: String? = null,
    val historyObservationId: String? = null,
    val suspensionObservationId: String? = null,
    val suspensionReason: String = "",
    val suspensionError: String? = null,
    val saving: Boolean = false,
    val photoPreparing: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val loadError: String? = null,
    val consentVisible: Boolean = false,
    val consentAccepted: Boolean = false,
)

sealed interface ObservationsAction {
    data object Reload : ObservationsAction
    /** A real entry through the Plan catalog, never a simple recomposition. */
    data object EnterCatalog : ObservationsAction
    data object ToggleSearch : ObservationsAction
    data object ToggleFollowUpFilter : ObservationsAction
    data class Query(val value: String) : ObservationsAction
    data class ToggleCategory(val category: ObservationCategory) : ObservationsAction
    data class Open(val observationId: String) : ObservationsAction
    data class Register(val observationId: String) : ObservationsAction
    data object OpenGuide : ObservationsAction
    data class OpenOccurrence(val occurrence: ObservationOccurrence) : ObservationsAction
    data class ViewRecord(val recordId: String) : ObservationsAction
    data class CorrectRecord(val recordId: String) : ObservationsAction
    data class CorrectionReason(val value: String) : ObservationsAction
    data class NotPerformed(val occurrence: ObservationOccurrence) : ObservationsAction
    data object HelpDescribe : ObservationsAction
    data class DescriptionProposal(val value: String) : ObservationsAction
    data object ApplyDescription : ObservationsAction
    data object DismissDescription : ObservationsAction
    data class ChangeDraft(val value: ObservationRecord) : ObservationsAction
    data class PhotoPreparing(val preparing: Boolean) : ObservationsAction
    data class PhotoFailed(val message: String) : ObservationsAction
    data object CloseDraft : ObservationsAction
    data object Save : ObservationsAction
    data object History : ObservationsAction
    data class Date(val value: LocalDate) : ObservationsAction
    data class Filter(val value: ObservationCategory?) : ObservationsAction
    data class Configure(
        val observationId: String,
        val startStep: Int = 0,
        val indicationChange: Boolean = false,
    ) : ObservationsAction
    data class Schedule(val value: ObservationScheduleDraft) : ObservationsAction
    data class ScheduleSlotEditor(val value: GlucoseFollowUpSlotDraft?, val editingId: String? = null) : ObservationsAction
    data class ScheduleStep(val index: Int) : ObservationsAction
    data object NextSchedule : ObservationsAction
    data object PreviousSchedule : ObservationsAction
    data object SaveSchedule : ObservationsAction
    data class ToggleFollowUp(val observationId: String) : ObservationsAction
    data class RequestSuspend(val observationId: String) : ObservationsAction
    data class SuspendReason(val value: String) : ObservationsAction
    data object DismissSuspend : ObservationsAction
    data object ConfirmSuspend : ObservationsAction
    data class Changes(val observationId: String) : ObservationsAction
    data object Back : ObservationsAction
    data class AcceptConsent(val accepted: Boolean) : ObservationsAction
    data object ConfirmConsent : ObservationsAction
    data object DismissConsent : ObservationsAction
}

class ObservationsViewModel(
    private val repository: ObservationRepository,
    val actor: GlucoseActor,
    coroutineScope: CoroutineScope? = null,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val scope: CoroutineScope = coroutineScope ?: viewModelScope
    private val mutable = MutableStateFlow(
        now().let { moment ->
            ObservationsUiState(
                selectedDate = moment.toLocalDate(),
                snapshotObservedAt = moment,
            )
        },
    )
    val state: StateFlow<ObservationsUiState> = mutable.asStateFlow()
    private val photoChannel = kotlinx.coroutines.channels.Channel<ObservationPhotoRequest>(
        kotlinx.coroutines.channels.Channel.BUFFERED,
    )
    val photoRequests = photoChannel.receiveAsFlow()
    private var pendingWrite: (() -> Unit)? = null
    private var returnPage = ObservationsPage.DETAIL
    private data class ParentLocation(val page: ObservationsPage, val observationId: String?, val recordId: String?)
    private var historyParent = ParentLocation(ObservationsPage.LIBRARY, null, null)
    private var recordParent = ParentLocation(ObservationsPage.HISTORY, null, null)
    private var retryFollowUp: Pair<ObservationFollowUp, Int>? = null
    private var retryCorrection: ObservationRecordRevision? = null
    private var correctionExpectedVersion = 1

    init { reload() }

    fun onAction(action: ObservationsAction) {
        if (state.value.saving && action !is ObservationsAction.AcceptConsent) return
        when (action) {
            ObservationsAction.Reload -> reload()
            ObservationsAction.EnterCatalog -> enterCatalog()
            ObservationsAction.ToggleSearch -> update {
                copy(searchExpanded = !searchExpanded, query = if (searchExpanded) "" else query)
            }
            ObservationsAction.ToggleFollowUpFilter -> update {
                copy(onlyWithFollowUp = !onlyWithFollowUp, expanded = emptySet())
            }
            is ObservationsAction.Query -> update { copy(query = limitTextCharacters(action.value, CaptureLimit.SEARCH.maximum)) }
            is ObservationsAction.ToggleCategory -> update {
                copy(expanded = if (action.category in expanded) emptySet() else setOf(action.category))
            }
            is ObservationsAction.Open -> {
                ObservationCatalog.get(action.observationId)
                update {
                    copy(
                        page = ObservationsPage.DETAIL,
                        selectedObservationId = action.observationId,
                        expanded = emptySet(),
                        error = null,
                    )
                }
            }
            is ObservationsAction.Register -> openRegistration(action.observationId)
            ObservationsAction.OpenGuide -> if (state.value.draft != null) {
                update { copy(page = ObservationsPage.GUIDE, error = null) }
            }
            is ObservationsAction.OpenOccurrence -> update {
                copy(
                    page = ObservationsPage.OCCURRENCE_DETAIL,
                    selectedOccurrence = action.occurrence,
                    selectedObservationId = action.occurrence.observationId,
                    error = null,
                )
            }
            is ObservationsAction.ViewRecord -> viewRecord(action.recordId)
            is ObservationsAction.CorrectRecord -> correctRecord(action.recordId)
            is ObservationsAction.CorrectionReason -> {
                retryCorrection = null
                update { if (CaptureLimit.CHANGE_REASON.acceptsEdit(correctionReason, action.value) &&
                fitsObservationTextCapture(action.value, Int.MAX_VALUE, correctionReason))
                copy(correctionReason = action.value, errors = emptyMap()) else this }
            }
            is ObservationsAction.NotPerformed -> {
                openScheduled(action.occurrence)
                update { copy(draft = draft?.copy(result = ObservationResult.NOT_PERFORMED, answers = emptyMap())) }
            }
            ObservationsAction.HelpDescribe -> state.value.draft?.let { record ->
                val proposal = buildObservationDescription(record)
                update { copy(descriptionProposal = proposal.takeIf(String::isNotBlank),
                    error = if (proposal.isBlank()) "Selecciona o escribe lo que observaste para preparar una descripción." else null) }
            }
            is ObservationsAction.DescriptionProposal -> update {
                if (CaptureLimit.OBSERVATION_DESCRIPTION.acceptsEdit(descriptionProposal.orEmpty(), action.value) &&
                    fitsObservationTextCapture(action.value, Int.MAX_VALUE, descriptionProposal))
                    copy(descriptionProposal = action.value, error = null) else this
            }
            ObservationsAction.ApplyDescription -> update {
                val proposed = descriptionProposal ?: return@update this
                if (fitsObservationTextCapture(proposed, CaptureLimit.OBSERVATION_DESCRIPTION.maximum)) {
                    copy(draft = draft?.copy(description = proposed), descriptionProposal = null,
                        errors = emptyMap(), error = null)
                } else copy(error = "Acorta la descripción propuesta antes de usarla.")
            }
            ObservationsAction.DismissDescription -> update { copy(descriptionProposal = null) }
            is ObservationsAction.ChangeDraft -> {
                retryCorrection = null
                update { copy(draft = action.value, errors = emptyMap(), error = null, descriptionProposal = null) }
            }
            is ObservationsAction.PhotoPreparing -> update { copy(photoPreparing = action.preparing) }
            is ObservationsAction.PhotoFailed -> update { copy(photoPreparing = false, error = action.message.take(300)) }
            ObservationsAction.CloseDraft -> closeDraft()
            ObservationsAction.Save -> if (!state.value.photoPreparing) withConsent(::saveRecord)
            ObservationsAction.History -> openHistory()
            is ObservationsAction.Date -> update { copy(selectedDate = action.value) }
            is ObservationsAction.Filter -> if (state.value.focusedObservationId == null) {
                update { copy(category = action.value) }
            }
            is ObservationsAction.Configure -> configure(action)
            is ObservationsAction.Schedule -> {
                retryFollowUp = null
                update { copy(scheduleDraft = action.value, error = null) }
            }
            is ObservationsAction.ScheduleSlotEditor -> {
                retryFollowUp = null
                update { copy(scheduleSlotEditor = action.value, scheduleEditingSlotId = action.editingId, error = null) }
            }
            is ObservationsAction.ScheduleStep -> moveSchedule(action.index)
            ObservationsAction.NextSchedule -> moveSchedule(state.value.scheduleStep + 1)
            ObservationsAction.PreviousSchedule -> back()
            ObservationsAction.SaveSchedule -> withConsent(::saveSchedule)
            is ObservationsAction.ToggleFollowUp -> {
                val followUp = state.value.snapshot.followUps.firstOrNull { it.observationId == action.observationId }
                if (followUp?.schedule?.currentRevision?.active == true) {
                    requestSuspend(action.observationId)
                } else {
                    configure(ObservationsAction.Configure(action.observationId))
                }
            }
            is ObservationsAction.RequestSuspend -> requestSuspend(action.observationId)
            is ObservationsAction.SuspendReason -> {
                retryFollowUp = null
                update { if (CaptureLimit.CHANGE_REASON.acceptsEdit(suspensionReason, action.value) &&
                fitsObservationTextCapture(action.value, Int.MAX_VALUE, suspensionReason))
                copy(suspensionReason = action.value, suspensionError = null, error = null) else this }
            }
            ObservationsAction.DismissSuspend -> dismissSuspend()
            ObservationsAction.ConfirmSuspend -> {
                if (state.value.suspensionReason.isBlank()) {
                    update { copy(suspensionError = "Indica por qué deseas eliminar el seguimiento.") }
                } else withConsent(::suspendFollowUp)
            }
            is ObservationsAction.Changes -> update {
                copy(page = ObservationsPage.FOLLOW_UP_HISTORY, selectedObservationId = action.observationId,
                    historyObservationId = action.observationId, error = null)
            }
            ObservationsAction.Back -> back()
            is ObservationsAction.AcceptConsent -> update { copy(consentAccepted = action.accepted) }
            ObservationsAction.DismissConsent -> {
                pendingWrite = null
                update { copy(consentVisible = false, consentAccepted = false) }
            }
            ObservationsAction.ConfirmConsent -> if (state.value.consentAccepted) {
                update { copy(saving = true, error = null) }
                scope.launch {
                    try {
                        repository.acceptSensitiveConsent(actor, System.currentTimeMillis())
                        update { copy(saving = false, consentVisible = false) }
                        pendingWrite?.also { pendingWrite = null }?.invoke()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        update { copy(saving = false, error = "No se pudo guardar la aceptación. Intenta nuevamente.") }
                    }
                }
            }
        }
    }

    fun openScheduled(occurrence: ObservationOccurrence) {
        if (state.value.saving) return
        val existing = state.value.snapshot.records.firstOrNull { it.occurrenceId == occurrence.id }
        if (existing != null) { viewRecord(existing.id); return }
        returnPage = state.value.page
        retryCorrection = null
        val moment = now()
        update {
            copy(page = ObservationsPage.REGISTER, selectedObservationId = occurrence.observationId,
                draft = newRecord(ObservationCatalog.get(occurrence.observationId), moment).copy(
                    followUpId = occurrence.followUpId, occurrenceId = occurrence.id, scheduledAt = occurrence.scheduledAt),
                correctionOriginalId = null, correctionReason = "", descriptionProposal = null,
                error = null, errors = emptyMap())
        }
    }

    fun requestPhoto(source: ObservationPhotoSource = ObservationPhotoSource.GALLERY, replacingPhotoId: String? = null) {
        val draft = state.value.draft ?: return
        if (state.value.photoPreparing || !ObservationCatalog.get(draft.observationId).photoAllowed) return
        if (replacingPhotoId != null && replacingPhotoId !in draft.localPhotoIds) return
        withConsent {
            if (state.value.draft?.id == draft.id) {
                photoChannel.trySend(ObservationPhotoRequest(source, draft.id, replacingPhotoId))
            }
        }
    }

    /** Keeps private image import alive across Activity recreation without retaining an Activity context. */
    internal fun launchPhotoWork(operation: suspend () -> Unit) {
        viewModelScope.launch { operation() }
    }

    private fun newRecord(definition: ObservationDefinition, moment: LocalDateTime) = ObservationRecord(
        id = UUID.randomUUID().toString(), observationId = definition.id,
        personUnderCareId = actor.personUnderCareId, occurredOn = moment.toLocalDate(),
        occurredAt = moment.toLocalTime().withSecond(0).withNano(0), timePrecision = ObservationTimePrecision.EXACT,
        result = if (definition.eventOnly) ObservationResult.EVENT else ObservationResult.CHANGES,
        answers = emptyMap(), description = "", createdAt = moment, actorId = actor.caregiverId,
    )

    private fun openRegistration(id: String) {
        returnPage = state.value.page
        retryCorrection = null
        update { copy(page = ObservationsPage.REGISTER,
            draft = newRecord(ObservationCatalog.get(id), now()), selectedObservationId = id,
            correctionOriginalId = null, correctionReason = "", descriptionProposal = null,
            error = null, errors = emptyMap()) }
    }

    private fun openHistory() {
        val current = state.value
        if (current.page == ObservationsPage.HISTORY) return
        historyParent = ParentLocation(current.page, current.selectedObservationId, current.selectedRecordId)
        val focus = current.selectedObservationId.takeIf { current.page == ObservationsPage.DETAIL }
        val date = focus?.let { id -> current.snapshot.records.filter { it.observationId == id }
            .maxOfOrNull { it.occurredOn } } ?: now().toLocalDate()
        update { copy(page = ObservationsPage.HISTORY, focusedObservationId = focus,
            category = null, selectedDate = date, error = null) }
    }

    private fun viewRecord(id: String) {
        val record = state.value.snapshot.records.firstOrNull { it.id == id } ?: return
        if (state.value.page != ObservationsPage.RECORD_DETAIL) recordParent = state.value.let {
            ParentLocation(it.page, it.selectedObservationId, it.selectedRecordId)
        }
        update { copy(page = ObservationsPage.RECORD_DETAIL, selectedRecordId = id,
            selectedObservationId = record.observationId, error = null) }
    }

    private fun correctRecord(id: String) {
        val record = state.value.snapshot.records.firstOrNull { it.id == id } ?: return
        returnPage = state.value.page
        correctionExpectedVersion = state.value.snapshot.recordRevisions.filter { it.recordId == id }
            .maxOfOrNull { it.revisionNumber } ?: 1
        retryCorrection = null
        update { copy(page = ObservationsPage.REGISTER, draft = record, correctionOriginalId = id, correctionReason = "",
            descriptionProposal = null, error = null, errors = emptyMap()) }
    }

    private fun closeDraft() {
        retryCorrection = null
        update { copy(page = returnPage, draft = null, correctionOriginalId = null, correctionReason = "",
            descriptionProposal = null, errors = emptyMap(), error = null, photoPreparing = false) }
    }

    private fun enterCatalog() {
        retryFollowUp = null
        retryCorrection = null
        returnPage = ObservationsPage.DETAIL
        update {
            copy(
                page = ObservationsPage.LIBRARY,
                query = "",
                searchExpanded = false,
                onlyWithFollowUp = false,
                expanded = emptySet(),
                selectedObservationId = null,
                selectedRecordId = null,
                selectedOccurrence = null,
                focusedObservationId = null,
                draft = null,
                correctionOriginalId = null,
                correctionReason = "",
                descriptionProposal = null,
                scheduleDraft = null,
                scheduleStep = 0,
                scheduleSlotEditor = null,
                scheduleEditingSlotId = null,
                suspensionObservationId = null,
                suspensionReason = "",
                suspensionError = null,
                errors = emptyMap(),
                error = null,
                photoPreparing = false,
            )
        }
    }

    private fun back() {
        val current = state.value
        if (current.suspensionObservationId != null) { dismissSuspend(); return }
        if (current.page == ObservationsPage.GUIDE && current.draft != null) {
            update { copy(page = ObservationsPage.REGISTER, error = null) }
            return
        }
        if (current.draft != null) { closeDraft(); return }
        when (current.page) {
            ObservationsPage.FOLLOW_UP -> if (current.scheduleSlotEditor != null) {
                update { copy(scheduleSlotEditor = null, scheduleEditingSlotId = null, error = null) }
            } else if (current.scheduleStep > 0) {
                update { copy(scheduleStep = scheduleStep - 1, error = null) }
            } else update { copy(page = ObservationsPage.DETAIL, scheduleDraft = null, error = null) }
            ObservationsPage.HISTORY -> update { copy(page = historyParent.page,
                selectedObservationId = historyParent.observationId, selectedRecordId = historyParent.recordId, error = null) }
            ObservationsPage.OCCURRENCE_DETAIL -> update {
                copy(page = ObservationsPage.HISTORY, selectedOccurrence = null, error = null)
            }
            ObservationsPage.RECORD_DETAIL -> update { copy(page = recordParent.page,
                selectedObservationId = recordParent.observationId, selectedRecordId = recordParent.recordId, error = null) }
            ObservationsPage.FOLLOW_UP_HISTORY -> update { copy(page = ObservationsPage.DETAIL, error = null) }
            else -> update { copy(page = ObservationsPage.LIBRARY, expanded = emptySet(), error = null) }
        }
    }

    private fun reload() {
        scope.launch {
            update { copy(loading = true, error = null, loadError = null) }
            try {
                val snapshot = LoadObservationsUseCase(repository)(actor)
                val observedAt = now()
                update {
                    copy(
                        snapshot = snapshot,
                        snapshotObservedAt = observedAt,
                        loading = false,
                        loadError = null,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = "No se pudieron cargar las observaciones. Reintenta sin borrar información."
                update { copy(loading = false, loadError = message, error = message) }
            }
        }
    }

    private fun withConsent(write: () -> Unit) {
        if (state.value.saving) return
        update { copy(saving = true, error = null) }
        scope.launch {
            try {
                if (repository.hasSensitiveConsent(actor)) {
                    update { copy(saving = false) }; write()
                } else {
                    pendingWrite = write
                    update { copy(saving = false, consentVisible = true, consentAccepted = false, error = null) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                update { copy(saving = false, error = "No se pudo verificar el almacenamiento. Intenta nuevamente.") }
            }
        }
    }

    private fun saveRecord() {
        val record = state.value.draft ?: return
        val original = state.value.snapshot.records.firstOrNull { it.id == state.value.correctionOriginalId }
        val errors = validateObservation(record, now(), original).toMutableMap()
        if (state.value.correctionOriginalId != null && (state.value.correctionReason.isBlank() ||
            !fitsObservationTextCapture(state.value.correctionReason, CaptureLimit.CHANGE_REASON.maximum))) {
            errors["correctionReason"] = "Indica por qué corriges esta declaración (hasta 300 caracteres)."
        }
        if (errors.isNotEmpty()) { update { copy(errors = errors) }; return }
        if (state.value.correctionOriginalId != null) {
            val revision = retryCorrection ?: ObservationRecordRevision(
                UUID.randomUUID().toString(), record.id, correctionExpectedVersion + 1,
                record, state.value.correctionReason.trim(), now(), actor.caregiverId,
            ).also { retryCorrection = it }
            persist {
                CorrectObservationUseCase(repository)(actor, revision, correctionExpectedVersion, now())
                val refreshed = LoadObservationsUseCase(repository)(actor)
                val observedAt = now()
                retryCorrection = null
                update { copy(snapshot = refreshed, snapshotObservedAt = observedAt,
                    draft = null, correctionOriginalId = null, correctionReason = "",
                    descriptionProposal = null, errors = emptyMap(), page = returnPage) }
            }
        } else persist {
            val saved = SaveObservationUseCase(repository)(actor, record, now())
            val observedAt = now()
            update { copy(
                snapshot = snapshot.copy(records = snapshot.records.filterNot { it.id == saved.id } + saved),
                snapshotObservedAt = observedAt,
                draft = null, descriptionProposal = null, errors = emptyMap(), page = returnPage,
            ) }
        }
    }

    private fun configure(action: ObservationsAction.Configure) {
        retryFollowUp = null
        val existing = state.value.snapshot.followUps.firstOrNull { it.observationId == action.observationId }?.schedule
        val today = now().toLocalDate()
        val changeKind = if (action.indicationChange) GlucoseFollowUpChangeKind.INDICATION_CHANGED
            else GlucoseFollowUpChangeKind.CORRECTED
        val draft = existing?.toDraft(changeKind, today)?.let { template ->
            template.copy(
                changeReason = if (action.indicationChange) {
                    "Cambio de indicación médica confirmado"
                } else {
                    "Nueva configuración de seguimiento confirmada"
                },
                // Old disabled-reminder revisions may not contain a technical reminder time.
                // Keep reminders off, but make the editable template valid without activating it.
                slots = template.slots.map { slot ->
                    if (slot.reminderTime != null || slot.time == null) {
                        slot
                    } else {
                        slot.copy(
                            reminderTime = slot.time,
                            reminderNextDay = false,
                            useSameTimeForReminder =
                                if (slot.timingType == GlucoseFollowUpTimingType.EXACT_TIME) true else null,
                        )
                    }
                },
            )
        } ?: GlucoseFollowUpDraft(
            UUID.randomUUID().toString(),
            effectiveFrom = today,
            slots = emptyList(),
        )
        update { copy(page = ObservationsPage.FOLLOW_UP, selectedObservationId = action.observationId,
            error = null, scheduleStep = action.startStep.coerceIn(0, 4),
            scheduleSlotEditor = null, scheduleEditingSlotId = null,
            scheduleDraft = ObservationScheduleDraft(action.observationId, draft)) }
    }

    private fun moveSchedule(target: Int) {
        if (target !in 0..4) return
        val draft = state.value.scheduleDraft ?: return
        if (state.value.scheduleSlotEditor != null && target != state.value.scheduleStep) {
            update { copy(error = "Confirma o cierra el horario que estás capturando.") }; return
        }
        if (target > state.value.scheduleStep) {
            val invalid = (0 until target).firstOrNull { observationScheduleStepError(draft, it) != null }
            if (invalid != null) {
                update { copy(scheduleStep = invalid, error = observationScheduleStepError(draft, invalid)) }
                return
            }
        }
        update { copy(scheduleStep = target, error = null) }
    }

    private fun saveSchedule() {
        retryFollowUp?.let { (followUp, expected) -> persistFollowUp(followUp, expected); return }
        val draft = state.value.scheduleDraft ?: return
        val invalid = (0..3).firstOrNull { observationScheduleStepError(draft, it) != null }
        if (invalid != null) {
            update { copy(scheduleStep = invalid, error = observationScheduleStepError(draft, invalid)) }; return
        }
        val schedule = draft.schedule
        val moment = now()
        val existing = state.value.snapshot.followUps.firstOrNull { it.observationId == draft.observationId }?.schedule
        val revision = observationRevisionForConfirmation(schedule, existing?.currentRevision, actor, moment) {
            UUID.randomUUID().toString()
        }
        val followUp = ObservationFollowUp(draft.observationId, existing?.copy(revisions = existing.revisions + revision)
            ?: GlucoseFollowUp(schedule.followUpId, actor.personUnderCareId, moment, actor.caregiverId, listOf(revision)))
        persistFollowUp(followUp, schedule.expectedRevisionNumber)
    }

    private fun requestSuspend(id: String) {
        val existing = state.value.snapshot.followUps.firstOrNull { it.observationId == id } ?: return
        if (!existing.schedule.currentRevision.active) return
        retryFollowUp = null
        update { copy(suspensionObservationId = id, suspensionReason = "", suspensionError = null, error = null) }
    }

    private fun dismissSuspend() {
        retryFollowUp = null
        update { copy(suspensionObservationId = null, suspensionReason = "", suspensionError = null, error = null) }
    }

    private fun suspendFollowUp() {
        val id = state.value.suspensionObservationId ?: return
        if (state.value.suspensionReason.isBlank() ||
            !fitsObservationTextCapture(state.value.suspensionReason, CaptureLimit.CHANGE_REASON.maximum)) {
            update { copy(suspensionError = "Indica por qué deseas eliminar el seguimiento (hasta 300 caracteres).") }; return
        }
        retryFollowUp?.takeIf { it.first.observationId == id &&
            it.first.schedule.currentRevision.changeKind == GlucoseFollowUpChangeKind.REMOVED
        }?.let { (followUp, expected) -> persistFollowUp(followUp, expected); return }
        val existing = state.value.snapshot.followUps.firstOrNull { it.observationId == id } ?: return
        val current = existing.schedule.currentRevision
        if (!current.active) { dismissSuspend(); return }
        val revision = observationDeletedRevision(current, state.value.suspensionReason, actor, now()) {
            UUID.randomUUID().toString()
        }
        persistFollowUp(existing.copy(schedule = existing.schedule.copy(revisions = existing.schedule.revisions + revision)),
            current.revisionNumber)
    }

    private fun persistFollowUp(followUp: ObservationFollowUp, expected: Int) {
        retryFollowUp = followUp to expected
        persist {
            val saved = SaveObservationFollowUpUseCase(repository)(actor, followUp, expected)
            val observedAt = now()
            retryFollowUp = null
            update { copy(
                snapshot = snapshot.copy(
                    followUps = snapshot.followUps.filterNot { it.observationId == saved.observationId } + saved,
                ),
                snapshotObservedAt = observedAt,
                page = ObservationsPage.DETAIL, selectedObservationId = saved.observationId,
                scheduleDraft = null, scheduleStep = 0,
                scheduleSlotEditor = null, scheduleEditingSlotId = null,
                suspensionObservationId = null, suspensionReason = "", suspensionError = null,
            ) }
        }
    }

    private fun persist(block: suspend () -> Unit) {
        if (state.value.saving) return
        update { copy(saving = true, error = null) }
        scope.launch {
            try {
                block()
                update { copy(saving = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = "No se pudo guardar. Conservamos lo capturado para que puedas reintentar."
                update { copy(saving = false, error = message,
                    suspensionError = message.takeIf { suspensionObservationId != null }) }
            }
        }
    }

    private inline fun update(block: ObservationsUiState.() -> ObservationsUiState) { mutable.value = mutable.value.block() }

    companion object {
        fun factory(repository: ObservationRepository, actor: GlucoseActor): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ObservationsViewModel(repository, actor) as T
            }
    }
}

/**
 * Periodic clocks drive natural time transitions, while persisted state changes advance immediately.
 * This keeps a just-committed revision visible before the next minute or lifecycle tick.
 */
internal fun ObservationsUiState.observationProjectionNow(clockNow: LocalDateTime): LocalDateTime =
    maxOf(clockNow, snapshotObservedAt)

/** Uses the existing scheduling validator; steps only select the relevant errors. */
internal fun observationScheduleStepError(draft: ObservationScheduleDraft, step: Int): String? {
    val fields = validateGlucoseFollowUpDraft(draft.schedule, listOf(GlucoseContext.UNSPECIFIED))
    return when (step) {
        0 -> if (GlucoseFollowUpField.INDICATION in fields) "Transcribe la indicación médica para continuar." else null
        1 -> when {
            fields.any { it in setOf(GlucoseFollowUpField.FREQUENCY, GlucoseFollowUpField.WEEKDAYS, GlucoseFollowUpField.INTERVAL) } ->
                "Elige la frecuencia y completa los días o el intervalo."
            else -> null
        }
        2 -> if (GlucoseFollowUpField.SLOTS in fields) "Completa los horarios, sus ventanas y avisos sin superponerlos." else null
        3 -> if (GlucoseFollowUpField.REMINDERS in fields) "Elige si deseas avisos del celular." else null
        else -> null
    }
}
