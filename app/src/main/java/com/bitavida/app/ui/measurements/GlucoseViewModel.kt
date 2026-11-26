package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.ConfirmedGlucoseOccurrence
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseRegistrationContext
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.GlucoseRevision
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.confirmed
import com.bitavida.app.domain.model.glucoseRegistrationContext
import com.bitavida.app.domain.model.registrationContext
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.GlucoseRepository
import com.bitavida.app.domain.usecase.AcceptGlucoseSensitiveConsentUseCase
import com.bitavida.app.domain.usecase.CorrectGlucoseMeasurementUseCase
import com.bitavida.app.domain.usecase.CorrectGlucoseNotPerformedUseCase
import com.bitavida.app.domain.usecase.DeleteGlucoseMeasurementUseCase
import com.bitavida.app.domain.usecase.GetGlucoseRecordsUseCase
import com.bitavida.app.domain.usecase.GetGlucoseRevisionsUseCase
import com.bitavida.app.domain.usecase.ConfirmGlucoseNotPerformedUseCase
import com.bitavida.app.domain.usecase.GetGlucoseFollowUpUseCase
import com.bitavida.app.domain.usecase.GetGlucoseOccurrenceDecisionRevisionsUseCase
import com.bitavida.app.domain.usecase.GetGlucoseOccurrenceDecisionsUseCase
import com.bitavida.app.domain.usecase.GlucoseDeleteResult
import com.bitavida.app.domain.usecase.GlucoseDraft
import com.bitavida.app.domain.usecase.GlucoseFollowUpDraft
import com.bitavida.app.domain.usecase.GlucoseFollowUpSaveResult
import com.bitavida.app.domain.usecase.GlucoseField
import com.bitavida.app.domain.usecase.GlucoseSaveResult
import com.bitavida.app.domain.usecase.SaveGlucoseFollowUpUseCase
import com.bitavida.app.domain.usecase.SaveGlucoseMeasurementUseCase
import com.bitavida.app.domain.usecase.toDraft
import com.bitavida.app.domain.usecase.measurementFollowUpForOccurrence
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlucoseUiState(
    val records: List<MeasurementRecord> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val draft: GlucoseDraft? = null,
    val editingRecordId: String? = null,
    val selectedRecordId: String? = null,
    val revisions: List<GlucoseRevision> = emptyList(),
    val revisionsLoading: Boolean = false,
    val deleteConfirmationVisible: Boolean = false,
    val pendingDeleteAfterConsent: Boolean = false,
    val errors: Set<GlucoseField> = emptySet(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val consentVisible: Boolean = false,
    val consentAccepted: Boolean = false,
    val occurrenceAlreadyRecorded: Boolean = false,
    val followUp: GlucoseFollowUp? = null,
    val occurrenceDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    val selectedOccurrence: GlucoseScheduledOccurrence? = null,
    val occurrenceDecisionRevisions: List<GlucoseOccurrenceDecisionRevision> = emptyList(),
    val occurrenceDecisionRevisionsLoading: Boolean = false,
    val occurrenceDecisionSaving: Boolean = false,
    val occurrenceDecisionError: String? = null,
    val pendingFollowUpDraft: GlucoseFollowUpDraft? = null,
    val followUpSaving: Boolean = false,
    val followUpError: String? = null,
) {
    val selectedRecord: MeasurementRecord?
        get() = records.singleOrNull { it.id == selectedRecordId }

    val selectedOccurrenceDecision: GlucoseOccurrenceDecision?
        get() = selectedOccurrence?.let { occurrence ->
            occurrenceDecisions.singleOrNull {
                it.occurrenceId == occurrence.id
            }
        }
}

sealed interface GlucoseAction {
    data object Reload : GlucoseAction

    /** Only a confirmed occurrence from the host may associate a reading with a schedule. */
    data class Open(
        val followUp: MeasurementFollowUp? = null,
        val occurrence: ConfirmedGlucoseOccurrence? = null,
        val correctingDecisionId: String? = null,
    ) : GlucoseAction

    data class OpenRecord(val recordId: String) : GlucoseAction
    data class OpenOccurrence(val occurrence: GlucoseScheduledOccurrence) : GlucoseAction
    data object DismissOccurrence : GlucoseAction
    data object RegisterSelectedOccurrence : GlucoseAction
    data class CorrectNotPerformed(val reason: String?) : GlucoseAction
    data object DismissRecord : GlucoseAction
    data object CorrectSelectedRecord : GlucoseAction
    data object RequestDeleteSelectedRecord : GlucoseAction
    data object CancelDeleteSelectedRecord : GlucoseAction
    data object ConfirmDeleteSelectedRecord : GlucoseAction
    data class Reading(val value: String) : GlucoseAction
    data class Moment(val value: GlucoseContext) : GlucoseAction
    data class ActualTime(val value: LocalDateTime) : GlucoseAction
    data class Note(val value: String) : GlucoseAction
    data object Save : GlucoseAction
    data class AcceptConsent(val accepted: Boolean) : GlucoseAction
    data object ConfirmConsent : GlucoseAction
    data object DismissConsent : GlucoseAction
    data object RequestClose : GlucoseAction
    data object MakeAdditional : GlucoseAction
    data class SaveFollowUp(val draft: GlucoseFollowUpDraft) : GlucoseAction
    data class ConfirmNotPerformed(
        val occurrence: GlucoseScheduledOccurrence,
        val reason: String?,
    ) : GlucoseAction
    data class DeleteFollowUp(val reason: String) : GlucoseAction
}

enum class GlucoseEffect {
    SAVED,
    UPDATED,
    DELETED,
    CLOSED,
    FOLLOW_UP_SAVED,
    NOT_PERFORMED_CONFIRMED,
}

class GlucoseViewModel(
    private val actor: GlucoseActor,
    repository: GlucoseRepository,
    private val coroutineScopeOverride: CoroutineScope? = null,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val scope get() = coroutineScopeOverride ?: viewModelScope
    private val load = GetGlucoseRecordsUseCase(repository)
    private val save = SaveGlucoseMeasurementUseCase(repository, now)
    private val correct = CorrectGlucoseMeasurementUseCase(repository, now)
    private val delete = DeleteGlucoseMeasurementUseCase(repository, now)
    private val loadRevisions = GetGlucoseRevisionsUseCase(repository)
    private val consent = AcceptGlucoseSensitiveConsentUseCase(repository)
    private val loadFollowUp = GetGlucoseFollowUpUseCase(repository)
    private val loadDecisions = GetGlucoseOccurrenceDecisionsUseCase(repository)
    private val loadDecisionRevisions =
        GetGlucoseOccurrenceDecisionRevisionsUseCase(repository)
    private val correctNotPerformedUseCase =
        CorrectGlucoseNotPerformedUseCase(repository, now, newId)
    private val saveFollowUp = SaveGlucoseFollowUpUseCase(repository, now, newId)
    private val confirmNotPerformedUseCase = ConfirmGlucoseNotPerformedUseCase(
        repository,
        now,
        newId,
    )
    private val mutableState = MutableStateFlow(GlucoseUiState())
    val state = mutableState.asStateFlow()
    private val effectChannel = Channel<GlucoseEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        reload()
    }

    fun onAction(action: GlucoseAction) {
        if (mutableState.value.saving) return
        when (action) {
            GlucoseAction.Reload -> reload()
            is GlucoseAction.Open -> openCapture(action)
            is GlucoseAction.OpenRecord -> openRecord(action.recordId)
            is GlucoseAction.OpenOccurrence -> openOccurrence(action.occurrence)
            GlucoseAction.DismissOccurrence -> dismissOccurrence()
            GlucoseAction.RegisterSelectedOccurrence -> registerSelectedOccurrence()
            is GlucoseAction.CorrectNotPerformed ->
                correctNotPerformed(action.reason)
            GlucoseAction.DismissRecord -> dismissRecord()
            GlucoseAction.CorrectSelectedRecord -> correctSelectedRecord()
            GlucoseAction.RequestDeleteSelectedRecord ->
                mutableState.update {
                    it.copy(deleteConfirmationVisible = true, saveError = null)
                }
            GlucoseAction.CancelDeleteSelectedRecord ->
                mutableState.update {
                    it.copy(deleteConfirmationVisible = false, saveError = null)
                }
            GlucoseAction.ConfirmDeleteSelectedRecord ->
                deleteSelectedRecord(acceptConsent = false)
            is GlucoseAction.Reading ->
                edit { it.copy(reading = action.value.take(40)) }
            is GlucoseAction.Moment -> edit { it.copy(moment = action.value) }
            is GlucoseAction.ActualTime ->
                edit { it.copy(actualAt = action.value) }
            is GlucoseAction.Note ->
                edit { draft ->
                    if (CaptureLimit.MEASUREMENT_NOTE.acceptsEdit(draft.note, action.value)) draft.copy(note = action.value)
                    else draft
                }
            GlucoseAction.Save -> persist(acceptConsent = false)
            is GlucoseAction.AcceptConsent ->
                mutableState.update {
                    it.copy(consentAccepted = action.accepted)
                }
            GlucoseAction.ConfirmConsent -> {
                if (mutableState.value.consentAccepted) {
                    when {
                        mutableState.value.pendingDeleteAfterConsent ->
                            deleteSelectedRecord(acceptConsent = true)
                        mutableState.value.pendingFollowUpDraft != null ->
                            persistFollowUp(
                                requireNotNull(mutableState.value.pendingFollowUpDraft),
                                acceptConsent = true,
                            )
                        else -> persist(acceptConsent = true)
                    }
                }
            }
            GlucoseAction.DismissConsent ->
                mutableState.update {
                    it.copy(
                        consentVisible = false,
                        consentAccepted = false,
                        pendingDeleteAfterConsent = false,
                        pendingFollowUpDraft = null,
                    )
                }
            GlucoseAction.RequestClose -> closeCapture()
            GlucoseAction.MakeAdditional -> makeAdditional()
            is GlucoseAction.SaveFollowUp -> persistFollowUp(action.draft, false)
            is GlucoseAction.ConfirmNotPerformed ->
                confirmNotPerformed(action.occurrence, action.reason)
            is GlucoseAction.DeleteFollowUp -> deleteFollowUp(action.reason)
        }
    }

    private fun openCapture(action: GlucoseAction.Open) {
        val state = mutableState.value
        if (state.loading || state.loadFailed || state.draft != null) return
        val draft = GlucoseDraft(
            id = newId(),
            context = glucoseRegistrationContext(
                action.followUp,
                action.occurrence,
            ),
            actualAt = now().truncatedTo(ChronoUnit.MINUTES),
            moment = action.occurrence?.expectedContext,
            firstMeasurement = state.records.isEmpty(),
            correctingDecisionId = action.correctingDecisionId,
        )
        mutableState.update {
            it.copy(
                draft = draft,
                editingRecordId = null,
                errors = emptySet(),
                saveError = null,
                occurrenceAlreadyRecorded = false,
            )
        }
    }

    private fun openRecord(recordId: String) {
        val record = mutableState.value.records
            .singleOrNull { it.id == recordId }
            ?: return
        mutableState.update {
            it.copy(
                selectedRecordId = record.id,
                revisions = emptyList(),
                revisionsLoading = true,
                deleteConfirmationVisible = false,
                saveError = null,
            )
        }
        scope.launch {
            try {
                val revisions = loadRevisions(actor, record.id)
                mutableState.update { state ->
                    if (state.selectedRecordId == record.id) {
                        state.copy(
                            revisions = revisions,
                            revisionsLoading = false,
                        )
                    } else {
                        state
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { state ->
                    if (state.selectedRecordId == record.id) {
                        state.copy(
                            revisionsLoading = false,
                            saveError =
                                "No se pudo consultar el historial de cambios.",
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun openOccurrence(occurrence: GlucoseScheduledOccurrence) {
        val hasDecision = mutableState.value.occurrenceDecisions.any {
            it.occurrenceId == occurrence.id
        }
        mutableState.update {
            it.copy(
                selectedOccurrence = occurrence,
                occurrenceDecisionRevisions = emptyList(),
                occurrenceDecisionRevisionsLoading = hasDecision,
                occurrenceDecisionError = null,
            )
        }
        if (!hasDecision) return
        scope.launch { loadOccurrenceDecisionRevisions(occurrence.id) }
    }

    private suspend fun loadOccurrenceDecisionRevisions(occurrenceId: String) {
        try {
            val revisions = loadDecisionRevisions(actor, occurrenceId)
            mutableState.update { state ->
                if (state.selectedOccurrence?.id == occurrenceId) {
                    state.copy(
                        occurrenceDecisionRevisions = revisions,
                        occurrenceDecisionRevisionsLoading = false,
                    )
                } else {
                    state
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.update { state ->
                if (state.selectedOccurrence?.id == occurrenceId) {
                    state.copy(
                        occurrenceDecisionRevisionsLoading = false,
                        occurrenceDecisionError =
                            "No se pudo consultar el historial de esta declaración.",
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun dismissOccurrence() {
        mutableState.update {
            it.copy(
                selectedOccurrence = null,
                occurrenceDecisionRevisions = emptyList(),
                occurrenceDecisionRevisionsLoading = false,
                occurrenceDecisionSaving = false,
                occurrenceDecisionError = null,
            )
        }
    }

    private fun registerSelectedOccurrence() {
        val state = mutableState.value
        val occurrence = state.selectedOccurrence ?: return
        val followUp = state.followUp ?: return
        val historical = measurementFollowUpForOccurrence(followUp, occurrence) ?: return
        val correctingDecisionId = state.selectedOccurrenceDecision?.id
        dismissOccurrence()
        openCapture(
            GlucoseAction.Open(
                followUp = historical,
                occurrence = occurrence.confirmed(),
                correctingDecisionId = correctingDecisionId,
            ),
        )
    }

    private fun dismissRecord() {
        mutableState.update {
            it.copy(
                selectedRecordId = null,
                revisions = emptyList(),
                revisionsLoading = false,
                deleteConfirmationVisible = false,
                saveError = null,
            )
        }
    }

    private fun correctSelectedRecord() {
        val record = mutableState.value.selectedRecord ?: return
        val glucose = record.value as MeasurementValue.Glucose
        val metadata = requireNotNull(record.glucoseMetadata)
        mutableState.update {
            it.copy(
                draft = GlucoseDraft(
                    id = record.id,
                    context = metadata.registrationContext(),
                    actualAt = record.occurredAt,
                    reading = glucose.reading,
                    moment = glucose.context,
                    note = metadata.note.orEmpty(),
                ),
                editingRecordId = record.id,
                selectedRecordId = null,
                revisions = emptyList(),
                deleteConfirmationVisible = false,
                errors = emptySet(),
                saveError = null,
            )
        }
    }

    private fun edit(transform: (GlucoseDraft) -> GlucoseDraft) {
        mutableState.update { state ->
            state.draft?.let {
                state.copy(
                    draft = transform(it),
                    errors = emptySet(),
                    saveError = null,
                    occurrenceAlreadyRecorded = false,
                )
            } ?: state
        }
    }

    private fun reload() {
        if (mutableState.value.draft != null) return
        mutableState.update {
            it.copy(loading = true, loadFailed = false)
        }
        scope.launch {
            try {
                val records = load(actor)
                val followUp = loadFollowUp(actor)
                val decisions = followUp?.let {
                    loadDecisions(actor, it.id)
                }.orEmpty()
                mutableState.update {
                    it.copy(
                        records = records,
                        followUp = followUp,
                        occurrenceDecisions = decisions,
                        loading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(loading = false, loadFailed = true)
                }
            }
        }
    }

    private fun persist(acceptConsent: Boolean) {
        val state = mutableState.value
        val draft = state.draft ?: return
        val correcting = state.editingRecordId != null
        mutableState.update {
            it.copy(saving = true, saveError = null)
        }
        scope.launch {
            try {
                if (acceptConsent) {
                    consent(actor, System.currentTimeMillis())
                }
                val result = if (correcting) {
                    correct(actor, draft)
                } else {
                    save(actor, draft)
                }
                handleSaveResult(result, correcting)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        saving = false,
                        saveError =
                            "No se pudo guardar. Tu captura sigue aquí; vuelve a intentarlo.",
                    )
                }
            }
        }
    }

    private suspend fun handleSaveResult(
        result: GlucoseSaveResult,
        correcting: Boolean,
    ) {
        when (result) {
            is GlucoseSaveResult.Stored -> {
                mutableState.update {
                    it.copy(
                        records = (
                            it.records.filterNot { record ->
                                record.id == result.record.id
                            } + result.record
                            ).sortedByDescending(MeasurementRecord::occurredAt),
                        draft = null,
                        editingRecordId = null,
                        saving = false,
                        consentVisible = false,
                        consentAccepted = false,
                        errors = emptySet(),
                    )
                }
                effectChannel.send(
                    if (correcting) {
                        GlucoseEffect.UPDATED
                    } else {
                        GlucoseEffect.SAVED
                    },
                )
            }
            is GlucoseSaveResult.AlreadyRecorded ->
                mutableState.update {
                    it.copy(
                        saving = false,
                        consentVisible = false,
                        occurrenceAlreadyRecorded = true,
                        saveError =
                            "Este horario ya tiene una medición. " +
                                "Puedes conservar esta lectura como adicional.",
                    )
                }
            is GlucoseSaveResult.Invalid ->
                mutableState.update {
                    it.copy(saving = false, errors = result.fields)
                }
            GlucoseSaveResult.ConsentRequired ->
                mutableState.update {
                    it.copy(
                        saving = false,
                        consentVisible = true,
                        consentAccepted = false,
                    )
                }
            GlucoseSaveResult.AuthorizationRequired ->
                mutableState.update {
                    it.copy(
                        saving = false,
                        saveError =
                            "Se necesita una autorización vigente para guardar.",
                    )
                }
        }
    }

    private fun deleteSelectedRecord(acceptConsent: Boolean) {
        val recordId = mutableState.value.selectedRecordId ?: return
        mutableState.update {
            it.copy(saving = true, saveError = null)
        }
        scope.launch {
            try {
                if (acceptConsent) {
                    consent(actor, System.currentTimeMillis())
                }
                when (delete(actor, recordId)) {
                    GlucoseDeleteResult.Deleted -> {
                        mutableState.update {
                            it.copy(
                                records = it.records.filterNot { record ->
                                    record.id == recordId
                                },
                                selectedRecordId = null,
                                revisions = emptyList(),
                                revisionsLoading = false,
                                deleteConfirmationVisible = false,
                                pendingDeleteAfterConsent = false,
                        pendingFollowUpDraft = null,
                                saving = false,
                                consentVisible = false,
                                consentAccepted = false,
                            )
                        }
                        effectChannel.send(GlucoseEffect.DELETED)
                    }
                    GlucoseDeleteResult.ConsentRequired ->
                        mutableState.update {
                            it.copy(
                                saving = false,
                                consentVisible = true,
                                consentAccepted = false,
                                pendingDeleteAfterConsent = true,
                            )
                        }
                    GlucoseDeleteResult.AuthorizationRequired ->
                        mutableState.update {
                            it.copy(
                                saving = false,
                                saveError =
                                    "Se necesita una autorización vigente para eliminar.",
                            )
                        }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        saving = false,
                        saveError =
                            "No se pudo eliminar el registro. Vuelve a intentarlo.",
                    )
                }
            }
        }
    }

    private fun persistFollowUp(
        draft: GlucoseFollowUpDraft,
        acceptConsent: Boolean,
    ) {
        if (mutableState.value.followUpSaving) return
        mutableState.update {
            it.copy(followUpSaving = true, followUpError = null)
        }
        scope.launch {
            try {
                if (acceptConsent) consent(actor, System.currentTimeMillis())
                when (val result = saveFollowUp(actor, draft)) {
                    is GlucoseFollowUpSaveResult.Stored -> {
                        mutableState.update {
                            it.copy(
                                followUp = result.followUp,
                                followUpSaving = false,
                                pendingFollowUpDraft = null,
                                consentVisible = false,
                                consentAccepted = false,
                            )
                        }
                        effectChannel.send(GlucoseEffect.FOLLOW_UP_SAVED)
                    }
                    is GlucoseFollowUpSaveResult.Invalid -> mutableState.update {
                        it.copy(
                            followUpSaving = false,
                            followUpError = "Revisa la indicación, la frecuencia, los horarios y los avisos.",
                        )
                    }
                    GlucoseFollowUpSaveResult.ConsentRequired -> mutableState.update {
                        it.copy(
                            followUpSaving = false,
                            pendingFollowUpDraft = draft,
                            consentVisible = true,
                            consentAccepted = false,
                        )
                    }
                    GlucoseFollowUpSaveResult.AuthorizationRequired -> mutableState.update {
                        it.copy(
                            followUpSaving = false,
                            followUpError = "Se necesita una autorización vigente.",
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        followUpSaving = false,
                        followUpError = "No se pudo guardar el seguimiento.",
                    )
                }
            }
        }
    }

    private fun confirmNotPerformed(
        occurrence: GlucoseScheduledOccurrence,
        reason: String?,
    ) {
        if (mutableState.value.occurrenceDecisionSaving) return
        mutableState.update {
            it.copy(
                occurrenceDecisionSaving = true,
                occurrenceDecisionError = null,
            )
        }
        scope.launch {
            try {
                val decision = confirmNotPerformedUseCase(actor, occurrence, reason)
                mutableState.update {
                    it.copy(
                        occurrenceDecisions = (
                            it.occurrenceDecisions.filterNot { current ->
                                current.occurrenceId == decision.occurrenceId
                            } + decision
                            ),
                        occurrenceDecisionSaving = false,
                        followUpError = null,
                    )
                }
                if (mutableState.value.selectedOccurrence?.id == occurrence.id) {
                    loadOccurrenceDecisionRevisions(occurrence.id)
                }
                effectChannel.send(GlucoseEffect.NOT_PERFORMED_CONFIRMED)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        occurrenceDecisionSaving = false,
                        occurrenceDecisionError =
                            "No se pudo confirmar la medición no realizada.",
                        followUpError =
                            "No se pudo confirmar la medición no realizada.",
                    )
                }
            }
        }
    }

    private fun correctNotPerformed(reason: String?) {
        val decision = mutableState.value.selectedOccurrenceDecision ?: return
        if (mutableState.value.occurrenceDecisionSaving) return
        mutableState.update {
            it.copy(
                occurrenceDecisionSaving = true,
                occurrenceDecisionError = null,
            )
        }
        scope.launch {
            try {
                val corrected = correctNotPerformedUseCase(actor, decision, reason)
                mutableState.update {
                    it.copy(
                        occurrenceDecisions = (
                            it.occurrenceDecisions.filterNot { current ->
                                current.occurrenceId == corrected.occurrenceId
                            } + corrected
                            ),
                        occurrenceDecisionSaving = false,
                    )
                }
                loadOccurrenceDecisionRevisions(corrected.occurrenceId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        occurrenceDecisionSaving = false,
                        occurrenceDecisionError =
                            "No se pudo guardar la corrección. Vuelve a intentarlo.",
                    )
                }
            }
        }
    }

    private fun deleteFollowUp(reason: String) {
        val followUp = mutableState.value.followUp ?: return
        val draft = followUp.toDraft(
            GlucoseFollowUpChangeKind.REMOVED,
            now().toLocalDate(),
        ).copy(changeReason = reason)
        persistFollowUp(draft, false)
    }

    private fun makeAdditional() {
        val scheduled = mutableState.value.draft?.context
            as? GlucoseRegistrationContext.Scheduled
            ?: return
        edit {
            it.copy(
                id = newId(),
                context = GlucoseRegistrationContext.Additional(
                    scheduled.occurrence.followUpId,
                ),
            )
        }
    }

    private fun closeCapture() {
        mutableState.update {
            it.copy(
                draft = null,
                editingRecordId = null,
                errors = emptySet(),
                saveError = null,
                consentVisible = false,
                consentAccepted = false,
                occurrenceAlreadyRecorded = false,
            )
        }
        effectChannel.trySend(GlucoseEffect.CLOSED)
    }

    companion object {
        fun factory(
            actor: GlucoseActor,
            repository: GlucoseRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T {
                    require(
                        modelClass.isAssignableFrom(
                            GlucoseViewModel::class.java,
                        ),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return GlucoseViewModel(actor, repository) as T
                }
            }
    }
}
