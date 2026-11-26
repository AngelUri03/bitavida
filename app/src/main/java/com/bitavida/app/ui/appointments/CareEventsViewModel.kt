package com.bitavida.app.ui.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import com.bitavida.app.domain.usecase.CareEventValidationException
import com.bitavida.app.domain.usecase.CareEventReportHistory
import com.bitavida.app.domain.usecase.readCareEventReportHistory
import kotlinx.coroutines.Job
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareEventsState(
    val events: List<CareEvent> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val draft: CareEventDraft? = null,
    val initialDraft: CareEventDraft? = null,
    val editing: CareEvent? = null,
    val selected: CareEvent? = null,
    val history: List<CareEvent> = emptyList(),
    val reportHistory: CareEventReportHistory? = null,
    val reportHistoryLoading: Boolean = false,
    val reportHistoryError: String? = null,
    val cancellationGeneration: Long = 0,
    val operationId: String? = null,
    val draftId: String? = null,
    val consentRequested: Boolean = false,
    val consentAccepted: Boolean = false,
    val pendingPhotos: List<CareEventPhotoInput> = emptyList(),
    val existingPhotoIds: List<String> = emptyList(),
    val photoActor: CareEventActor? = null,
    val removedPhotoIds: Set<String> = emptySet(),
    val photoListReady: Boolean = true,
    val photoPreparing: Boolean = false,
    val photoError: String? = null,
)

sealed interface CareEventsAction {
    data object Reload : CareEventsAction
    data object LoadReportHistory : CareEventsAction
    data class Create(val kind: CareEventKind) : CareEventsAction
    data class SetCategory(val category: CareEventCategory) : CareEventsAction
    data class Open(val id: String) : CareEventsAction
    data class ClassifyHistorical(val id: String, val expectedRevision: Int,
        val consultationType: CareConsultationType, val isFollowUp: Boolean, val specialty: String) : CareEventsAction
    data object Edit : CareEventsAction
    data object Cancel : CareEventsAction
    data class Change(val draft: CareEventDraft) : CareEventsAction
    data object Save : CareEventsAction
    data class AddDraftPhoto(val uri: android.net.Uri, val cameraToken: String? = null, val replacingId: String? = null) : CareEventsAction
    data class RemoveDraftPhoto(val id: String) : CareEventsAction
    data class ChangeConsent(val accepted: Boolean) : CareEventsAction
    data object ConfirmConsent : CareEventsAction
    data object DeclineConsent : CareEventsAction
    data object Dismiss : CareEventsAction
}

/** A new appointment returns to the list; an edit returns to the detail that opened it. */
internal fun careEventSavedState(state: CareEventsState, events: List<CareEvent>, saved: CareEvent?): CareEventsState =
    state.copy(events = events, saving = false, draft = null, initialDraft = null, editing = null,
        selected = if (state.editing != null) saved else null, history = emptyList(),
        operationId = null, draftId = null, error = null)

/** Publish navigation only after the cancellation revision has committed. */
internal fun careEventCancelledState(state: CareEventsState, cancelled: CareEvent): CareEventsState =
    state.copy(events = state.events.map { if (it.id == cancelled.id) cancelled else it },
        selected = null, history = emptyList(), saving = false, error = null,
        cancellationGeneration = state.cancellationGeneration + 1)

internal fun visibleCareEventPhotoIds(existing: List<String>, removed: Set<String>,
    pending: List<CareEventPhotoInput>): List<String> {
    val replacements = pending.filter { it.replacesId != null }.associateBy { it.replacesId }
    return existing.mapNotNull { original ->
        replacements[original]?.id ?: original.takeUnless { it in removed }
    } + pending.filter { it.replacesId == null }.map { it.id }
}

class CareEventsViewModel(private val repository: CareEventRepository, val actor: CareEventActor,
    private val reminderScheduler: com.bitavida.app.platform.reminder.AppointmentReminderScheduler? = null,
    private val photoStore: com.bitavida.app.data.appointments.CareEventPhotoStore? = null) : ViewModel() {
    companion object {
        fun factory(context: android.content.Context, actor: CareEventActor): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(CareEventsViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return CareEventsViewModel(com.bitavida.app.data.appointments.EncryptedCareEventRepository(context.applicationContext), actor,
                        com.bitavida.app.platform.reminder.AppointmentReminderScheduler(context.applicationContext),
                        com.bitavida.app.data.appointments.CareEventPhotoStore(context.applicationContext)) as T
                }
            }
    }
    private val mutableState = MutableStateFlow(CareEventsState())
    val state = mutableState.asStateFlow()
    private var reportHistoryJob: Job? = null
    init { reload() }

    fun onAction(action: CareEventsAction) {
        if (mutableState.value.saving) return
        when (action) {
            CareEventsAction.Reload -> { invalidateReportHistory(); reload() }
            CareEventsAction.LoadReportHistory -> loadReportHistory()
            is CareEventsAction.Create -> {
                clearPendingPhotos()
                val status = when (action.kind) {
                    CareEventKind.REVIEW_TO_SCHEDULE -> CareEventStatus.TO_SCHEDULE
                    CareEventKind.APPOINTMENT -> CareEventStatus.CONFIRMED
                    CareEventKind.STUDY -> CareEventStatus.REQUESTED
                }
                val draft = CareEventDraft(kind = action.kind, title = "", status = status)
                mutableState.update { it.copy(draft = draft, initialDraft = draft,
                    editing = null, selected = null, error = null,
                    operationId = UUID.randomUUID().toString(), draftId = UUID.randomUUID().toString()) }
            }
            is CareEventsAction.SetCategory -> mutableState.update { state ->
                state.copy(draft = state.draft?.copy(category = action.category))
            }
            is CareEventsAction.Open -> {
                val event = mutableState.value.events.singleOrNull { it.id == action.id } ?: return
                mutableState.update { it.copy(selected = event, history = emptyList(), error = null) }
                viewModelScope.launch {
                    try {
                        val versions = repository.history(actor, event.id)
                        mutableState.update { if (it.selected?.id == event.id) it.copy(history = versions) else it }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { mutableState.update { it.copy(error = "No se pudo cargar el historial.") } }
                }
            }
            is CareEventsAction.ClassifyHistorical -> classifyHistorical(action)
            CareEventsAction.Edit -> mutableState.value.selected?.let { event ->
                if (!isFutureAppointment(event, LocalDateTime.now())) return
                clearPendingPhotos()
                val draft = event.draft.copy(changeReason = "")
                mutableState.update { it.copy(draft = draft, initialDraft = draft, editing = event,
                    selected = null, operationId = UUID.randomUUID().toString(), draftId = event.id, error = null,
                    photoListReady = false, photoActor = actor) }
                viewModelScope.launch {
                    try {
                        val ids = photoStore?.list(actor, event.id) ?: emptyList()
                        mutableState.update { if (it.draftId == event.id && it.editing?.id == event.id)
                            it.copy(existingPhotoIds = ids, photoListReady = true) else it }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) {
                        mutableState.update { if (it.draftId == event.id)
                            it.copy(photoError = "No se pudieron cargar las fotografías. Vuelve a abrir la cita.") else it }
                    }
                }
            }
            CareEventsAction.Cancel -> { invalidateReportHistory(); cancelEvent() }
            is CareEventsAction.Change -> mutableState.update { it.copy(draft = action.draft, error = null) }
            CareEventsAction.Dismiss -> {
                clearPendingPhotos()
                mutableState.update { it.copy(draft = null, initialDraft = null, editing = null, selected = null,
                    operationId = null, draftId = null, history = emptyList(), error = null, consentRequested = false, consentAccepted = false) }
            }
            is CareEventsAction.AddDraftPhoto -> addDraftPhoto(action.uri, action.cameraToken, action.replacingId)
            is CareEventsAction.RemoveDraftPhoto -> {
                mutableState.value.pendingPhotos.find { it.id == action.id }?.jpeg?.fill(0)
                mutableState.update {
                    it.copy(pendingPhotos = it.pendingPhotos.filterNot { photo -> photo.id == action.id },
                        removedPhotoIds = if (action.id in it.existingPhotoIds) it.removedPhotoIds + action.id else it.removedPhotoIds,
                        photoError = null)
                }
            }
            CareEventsAction.Save -> { invalidateReportHistory(); save() }
            is CareEventsAction.ChangeConsent -> mutableState.update { it.copy(consentAccepted = action.accepted) }
            CareEventsAction.DeclineConsent -> mutableState.update { it.copy(consentRequested = false, consentAccepted = false) }
            CareEventsAction.ConfirmConsent -> {
                if (!mutableState.value.consentAccepted || !mutableState.value.consentRequested) return
                mutableState.update { it.copy(saving = true) }
                viewModelScope.launch {
                    try {
                        repository.acceptSensitiveConsent(actor, System.currentTimeMillis())
                        mutableState.update { it.copy(saving = false, consentRequested = false, consentAccepted = false) }
                        save()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { mutableState.update { it.copy(saving = false, error = "No se pudo registrar la autorización.") } }
                }
            }
        }
    }

    private fun classifyHistorical(action: CareEventsAction.ClassifyHistorical) {
        val event = mutableState.value.selected ?: return
        if (event.id != action.id || event.revision != action.expectedRevision ||
            event.draft.kind != CareEventKind.APPOINTMENT ||
            event.draft.purpose !in setOf(CareEventPurpose.LEGACY, CareEventPurpose.MEDICAL_CONSULTATION) ||
            event.draft.consultationType != CareConsultationType.UNSPECIFIED ||
            action.consultationType == CareConsultationType.UNSPECIFIED) return
        val draft = event.draft.copy(purpose = CareEventPurpose.MEDICAL_CONSULTATION,
            consultationType = action.consultationType, isFollowUp = action.isFollowUp,
            specialty = action.specialty, changeReason = "Clasificación precisada por la persona cuidadora")
        val command = CareEventCommand.Revise(UUID.randomUUID().toString(), event.id, event.revision, draft)
        invalidateReportHistory()
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = repository.save(actor, command, LocalDateTime.now())
                val events = repository.load(actor)
                val versions = repository.history(actor, saved.id)
                mutableState.update { state ->
                    state.copy(events = events, selected = saved, history = versions,
                        saving = false, error = null)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                mutableState.update { it.copy(saving = false,
                    error = "No se pudo actualizar la clasificación. Revisa la autorización e inténtalo de nuevo.") }
            }
        }
    }

    private fun invalidateReportHistory() {
        reportHistoryJob?.cancel()
        reportHistoryJob = null
        mutableState.update { it.copy(reportHistory = null, reportHistoryLoading = false, reportHistoryError = null) }
    }

    /** Read all revisions only on demand for Informe, never mutate event state or attachments. */
    private fun loadReportHistory() {
        val current = mutableState.value
        if (current.loading || current.saving || current.reportHistoryLoading ||
            (current.reportHistory != null && current.reportHistory.events.sortedBy { it.id } == current.events.sortedBy { it.id }))
            return
        mutableState.update { it.copy(reportHistory = null, reportHistoryLoading = true, reportHistoryError = null) }
        reportHistoryJob = viewModelScope.launch {
            try {
                val snapshot = readCareEventReportHistory(repository, actor)
                mutableState.update { state ->
                    if (state.loading || state.saving) state.copy(reportHistoryLoading = false)
                    else state.copy(events = snapshot.events, reportHistory = snapshot,
                        reportHistoryLoading = false, reportHistoryError = null)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableState.update { it.copy(reportHistory = null, reportHistoryLoading = false,
                    reportHistoryError = "No se pudo cargar el historial de citas y estudios.") }
            }
        }
    }

    private fun clearPendingPhotos() {
        mutableState.value.pendingPhotos.forEach { it.jpeg.fill(0) }
        mutableState.update { it.copy(pendingPhotos = emptyList(), existingPhotoIds = emptyList(),
            removedPhotoIds = emptySet(), photoActor = null, photoListReady = true, photoPreparing = false, photoError = null) }
    }

    override fun onCleared() {
        clearPendingPhotos()
        super.onCleared()
    }

    private fun addDraftPhoto(uri: android.net.Uri, cameraToken: String?, replacingId: String?) {
        val initial = mutableState.value
        val store = photoStore ?: return
        val owner = initial.draftId
        val visibleIds = visibleCareEventPhotoIds(initial.existingPhotoIds, initial.removedPhotoIds, initial.pendingPhotos)
        if (owner == null || !initial.photoListReady || initial.photoPreparing ||
            (replacingId == null && visibleIds.size >= 5) ||
            (replacingId != null && replacingId !in visibleIds) || visibleIds.size > 5) {
            store.discardCameraCapture(cameraToken)
            return
        }
        mutableState.update { it.copy(photoPreparing = true, photoError = null) }
        viewModelScope.launch {
            var bytes: ByteArray? = null
            try {
                bytes = store.prepare(actor, uri)
                if (mutableState.value.draftId == owner) {
                    val prior = mutableState.value.pendingPhotos.find { it.id == replacingId }
                    val originalId = prior?.replacesId ?: replacingId?.takeIf { it in initial.existingPhotoIds }
                    val image = CareEventPhotoInput(UUID.randomUUID().toString(), bytes, originalId)
                    bytes = null
                    mutableState.update { current ->
                        val pending = current.pendingPhotos.toMutableList()
                        val index = pending.indexOfFirst { it.id == replacingId }
                        if (index >= 0) pending[index] = image else pending.add(image)
                        current.copy(pendingPhotos = pending, photoPreparing = false,
                            removedPhotoIds = if (originalId != null)
                                current.removedPhotoIds + originalId else current.removedPhotoIds)
                    }
                    prior?.jpeg?.fill(0)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (mutableState.value.draftId == owner) mutableState.update {
                    it.copy(photoPreparing = false, photoError = if (error is com.bitavida.app.data.images.PrivateImageQualityException)
                        "No se pudo conservar la calidad de la fotografía. Elige otra imagen o recórtala."
                    else "No se pudo preparar la fotografía. Inténtalo de nuevo.")
                }
            } finally {
                bytes?.fill(0)
                store.discardCameraCapture(cameraToken)
            }
        }
    }

    private fun reload() {
        if (mutableState.value.loading && mutableState.value.events.isNotEmpty()) return
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val events = repository.load(actor)
                mutableState.update { it.copy(events = events, loading = false) }
                try { reminderScheduler?.reconcileLatest(actor) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* Retry after the next reload or system event. */ }
            }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, error = "No se pudieron cargar las citas y estudios.") } }
        }
    }

    private fun cancelEvent() {
        val event = mutableState.value.selected ?: return
        if (!isFutureAppointment(event, LocalDateTime.now())) return
        val command = CareEventCommand.Revise(UUID.randomUUID().toString(), event.id, event.revision,
            event.draft.copy(status = CareEventStatus.CANCELLED, changeReason = "Cancelada por la persona cuidadora"))
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val cancelled = if (reminderScheduler != null) {
                    reminderScheduler.commitAndReconcile(actor, event.id) {
                        repository.save(actor, command, LocalDateTime.now())
                    }
                } else repository.save(actor, command, LocalDateTime.now())
                mutableState.update { careEventCancelledState(it, cancelled) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(saving = false, error = "No se pudo cancelar la cita. Inténtalo de nuevo.") } }
        }
    }

    private fun save() {
        val current = mutableState.value
        val draft = current.draft ?: return
        if (current.photoPreparing || !current.photoListReady) return
        val operationId = current.operationId ?: return
        val id = current.draftId ?: return
        val command = current.editing?.let { CareEventCommand.Revise(operationId, id, it.revision, draft) }
            ?: CareEventCommand.Create(operationId, id, draft)
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                if (!repository.hasSensitiveConsent(actor)) {
                    mutableState.update { it.copy(saving = false, consentRequested = true, consentAccepted = false) }
                    return@launch
                }
                if (reminderScheduler != null) {
                    reminderScheduler.commitAndReconcile(actor, id) {
                        repository.saveWithPhotos(actor, command, LocalDateTime.now(), current.pendingPhotos, current.removedPhotoIds)
                    }
                } else {
                    repository.saveWithPhotos(actor, command, LocalDateTime.now(), current.pendingPhotos, current.removedPhotoIds)
                }
                val events = repository.load(actor)
                val saved = events.singleOrNull { it.id == id }
                val returnToDetail = current.editing != null
                clearPendingPhotos()
                mutableState.update { careEventSavedState(it, events, saved) }
                if (returnToDetail && saved != null) {
                    try {
                        val versions = repository.history(actor, saved.id)
                        mutableState.update { if (it.selected?.id == saved.id) it.copy(history = versions) else it }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { mutableState.update { it.copy(error = "No se pudo cargar el historial.") } }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: CareEventSensitiveConsentException) { mutableState.update { it.copy(saving = false, consentRequested = true, consentAccepted = false) } }
            catch (_: CareEventValidationException) { mutableState.update { it.copy(saving = false, error = "Revisa el nombre, los datos y la fecha confirmada.") } }
            catch (_: Exception) { mutableState.update { it.copy(saving = false, error = if (current.pendingPhotos.isNotEmpty())
                "No se pudo guardar la cita y sus fotografías. Conserva la selección e inténtalo de nuevo."
                else "No se pudo guardar. Inténtalo de nuevo.") } }
        }
    }
}
