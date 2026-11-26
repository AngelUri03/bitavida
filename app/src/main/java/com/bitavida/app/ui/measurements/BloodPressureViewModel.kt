package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.BloodPressureRegistrationContext
import com.bitavida.app.domain.model.BloodPressureRevision
import com.bitavida.app.domain.model.ConfirmedBloodPressureOccurrence
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.bloodPressureRegistrationContext
import com.bitavida.app.domain.model.confirmedBloodPressure
import com.bitavida.app.domain.model.registrationContext
import com.bitavida.app.domain.repository.BloodPressureRepository
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.AcceptBloodPressureSensitiveConsentUseCase
import com.bitavida.app.domain.usecase.BloodPressureDeleteResult
import com.bitavida.app.domain.usecase.BloodPressureDraft
import com.bitavida.app.domain.usecase.BloodPressureField
import com.bitavida.app.domain.usecase.BloodPressureSaveResult
import com.bitavida.app.domain.usecase.ConfirmBloodPressureNotPerformedUseCase
import com.bitavida.app.domain.usecase.CorrectBloodPressureMeasurementUseCase
import com.bitavida.app.domain.usecase.CorrectBloodPressureNotPerformedUseCase
import com.bitavida.app.domain.usecase.DeleteBloodPressureMeasurementUseCase
import com.bitavida.app.domain.usecase.GetBloodPressureDecisionRevisionsUseCase
import com.bitavida.app.domain.usecase.GetBloodPressureFollowUpUseCase
import com.bitavida.app.domain.usecase.GetBloodPressureOccurrenceDecisionsUseCase
import com.bitavida.app.domain.usecase.GetBloodPressureRecordsUseCase
import com.bitavida.app.domain.usecase.GetBloodPressureRevisionsUseCase
import com.bitavida.app.domain.usecase.GlucoseFollowUpDraft
import com.bitavida.app.domain.usecase.GlucoseFollowUpSaveResult
import com.bitavida.app.domain.usecase.SaveBloodPressureFollowUpUseCase
import com.bitavida.app.domain.usecase.SaveBloodPressureMeasurementUseCase
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

data class BloodPressureUiState(
    val records: List<MeasurementRecord> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val draft: BloodPressureDraft? = null,
    val editingRecordId: String? = null,
    val selectedRecordId: String? = null,
    val revisions: List<BloodPressureRevision> = emptyList(),
    val revisionsLoading: Boolean = false,
    val deleteConfirmationVisible: Boolean = false,
    val pendingDeleteAfterConsent: Boolean = false,
    val errors: Set<BloodPressureField> = emptySet(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val consentVisible: Boolean = false,
    val consentAccepted: Boolean = false,
    val occurrenceAlreadyRecorded: Boolean = false,
    val followUp: GlucoseFollowUp? = null,
    val occurrenceDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    val selectedOccurrence: GlucoseScheduledOccurrence? = null,
    val occurrenceDecisionRevisions: List<GlucoseOccurrenceDecisionRevision> =
        emptyList(),
    val occurrenceDecisionRevisionsLoading: Boolean = false,
    val occurrenceDecisionSaving: Boolean = false,
    val occurrenceDecisionError: String? = null,
    val pendingFollowUpDraft: GlucoseFollowUpDraft? = null,
    val followUpSaving: Boolean = false,
    val followUpError: String? = null,
) {
    val selectedRecord: MeasurementRecord?
        get() = records.singleOrNull { (id) -> id == selectedRecordId }

    val selectedOccurrenceDecision: GlucoseOccurrenceDecision?
        get() = selectedOccurrence?.let { (occurrenceId) ->
            occurrenceDecisions.singleOrNull {
                it.occurrenceId == occurrenceId
            }
        }
}

sealed interface BloodPressureAction {
    data object Reload : BloodPressureAction
    data class Open(
        val followUp: MeasurementFollowUp? = null,
        val occurrence: ConfirmedBloodPressureOccurrence? = null,
        val correctingDecisionId: String? = null,
    ) : BloodPressureAction
    data class OpenRecord(val recordId: String) : BloodPressureAction
    data class OpenOccurrence(
        val occurrence: GlucoseScheduledOccurrence,
    ) : BloodPressureAction
    data object DismissOccurrence : BloodPressureAction
    data object RegisterSelectedOccurrence : BloodPressureAction
    data class CorrectNotPerformed(val reason: String?) : BloodPressureAction
    data object DismissRecord : BloodPressureAction
    data object CorrectSelectedRecord : BloodPressureAction
    data object RequestDeleteSelectedRecord : BloodPressureAction
    data object CancelDeleteSelectedRecord : BloodPressureAction
    data object ConfirmDeleteSelectedRecord : BloodPressureAction
    data class Systolic(val value: String) : BloodPressureAction
    data class Diastolic(val value: String) : BloodPressureAction
    data class Moment(val value: GlucoseContext) : BloodPressureAction
    data class ActualTime(val value: LocalDateTime) : BloodPressureAction
    data class Note(val value: String) : BloodPressureAction
    data object Save : BloodPressureAction
    data class AcceptConsent(val accepted: Boolean) : BloodPressureAction
    data object ConfirmConsent : BloodPressureAction
    data object DismissConsent : BloodPressureAction
    data object RequestClose : BloodPressureAction
    data object MakeAdditional : BloodPressureAction
    data class SaveFollowUp(
        val draft: GlucoseFollowUpDraft,
    ) : BloodPressureAction
    data class ConfirmNotPerformed(
        val occurrence: GlucoseScheduledOccurrence,
        val reason: String?,
    ) : BloodPressureAction
    data class DeleteFollowUp(val reason: String) : BloodPressureAction
}

enum class BloodPressureEffect {
    SAVED,
    UPDATED,
    DELETED,
    CLOSED,
    FOLLOW_UP_SAVED,
    NOT_PERFORMED_CONFIRMED,
}

class BloodPressureViewModel(
    private val actor: GlucoseActor,
    repository: BloodPressureRepository,
    private val coroutineScopeOverride: CoroutineScope? = null,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val scope get() = coroutineScopeOverride ?: viewModelScope
    private val load = GetBloodPressureRecordsUseCase(repository)
    private val save = SaveBloodPressureMeasurementUseCase(repository, now)
    private val correct = CorrectBloodPressureMeasurementUseCase(repository, now)
    private val delete = DeleteBloodPressureMeasurementUseCase(repository, now)
    private val loadRevisions = GetBloodPressureRevisionsUseCase(repository)
    private val consent = AcceptBloodPressureSensitiveConsentUseCase(repository)
    private val loadFollowUp = GetBloodPressureFollowUpUseCase(repository)
    private val loadDecisions = GetBloodPressureOccurrenceDecisionsUseCase(repository)
    private val loadDecisionRevisions =
        GetBloodPressureDecisionRevisionsUseCase(repository)
    private val correctNotPerformedUseCase =
        CorrectBloodPressureNotPerformedUseCase(repository, now, newId)
    private val saveFollowUp =
        SaveBloodPressureFollowUpUseCase(repository, now, newId)
    private val confirmNotPerformedUseCase =
        ConfirmBloodPressureNotPerformedUseCase(repository, now, newId)
    private val mutableState = MutableStateFlow(BloodPressureUiState())
    val state = mutableState.asStateFlow()
    private val effectChannel = Channel<BloodPressureEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        reload()
    }

    fun onAction(action: BloodPressureAction) {
        if (mutableState.value.saving) return
        when (action) {
            BloodPressureAction.Reload -> reload()
            is BloodPressureAction.Open -> openCapture(action)
            is BloodPressureAction.OpenRecord -> openRecord(action.recordId)
            is BloodPressureAction.OpenOccurrence ->
                openOccurrence(action.occurrence)
            BloodPressureAction.DismissOccurrence -> dismissOccurrence()
            BloodPressureAction.RegisterSelectedOccurrence ->
                registerSelectedOccurrence()
            is BloodPressureAction.CorrectNotPerformed ->
                correctNotPerformed(action.reason)
            BloodPressureAction.DismissRecord -> dismissRecord()
            BloodPressureAction.CorrectSelectedRecord -> correctSelectedRecord()
            BloodPressureAction.RequestDeleteSelectedRecord ->
                mutableState.update {
                    it.copy(deleteConfirmationVisible = true, saveError = null)
                }
            BloodPressureAction.CancelDeleteSelectedRecord ->
                mutableState.update {
                    it.copy(deleteConfirmationVisible = false, saveError = null)
                }
            BloodPressureAction.ConfirmDeleteSelectedRecord ->
                deleteSelectedRecord(acceptConsent = false)
            is BloodPressureAction.Systolic ->
                edit { it.copy(systolic = action.value.filter(Char::isDigit).take(3)) }
            is BloodPressureAction.Diastolic ->
                edit { it.copy(diastolic = action.value.filter(Char::isDigit).take(3)) }
            is BloodPressureAction.Moment ->
                edit { it.copy(moment = action.value) }
            is BloodPressureAction.ActualTime ->
                edit { it.copy(actualAt = action.value) }
            is BloodPressureAction.Note ->
                edit { draft ->
                    if (CaptureLimit.MEASUREMENT_NOTE.acceptsEdit(draft.note, action.value)) draft.copy(note = action.value)
                    else draft
                }
            BloodPressureAction.Save -> persist(acceptConsent = false)
            is BloodPressureAction.AcceptConsent ->
                mutableState.update {
                    it.copy(consentAccepted = action.accepted)
                }
            BloodPressureAction.ConfirmConsent -> confirmConsent()
            BloodPressureAction.DismissConsent ->
                mutableState.update {
                    it.copy(
                        consentVisible = false,
                        consentAccepted = false,
                        pendingDeleteAfterConsent = false,
                        pendingFollowUpDraft = null,
                    )
                }
            BloodPressureAction.RequestClose -> closeCapture()
            BloodPressureAction.MakeAdditional -> makeAdditional()
            is BloodPressureAction.SaveFollowUp ->
                persistFollowUp(action.draft, false)
            is BloodPressureAction.ConfirmNotPerformed ->
                confirmNotPerformed(action.occurrence, action.reason)
            is BloodPressureAction.DeleteFollowUp ->
                deleteFollowUp(action.reason)
        }
    }

    private fun confirmConsent() {
        if (!mutableState.value.consentAccepted) return
        when {
            mutableState.value.pendingDeleteAfterConsent ->
                deleteSelectedRecord(true)
            mutableState.value.pendingFollowUpDraft != null ->
                persistFollowUp(
                    requireNotNull(mutableState.value.pendingFollowUpDraft),
                    true,
                )
            else -> persist(true)
        }
    }

    private fun openCapture(action: BloodPressureAction.Open) {
        val current = mutableState.value
        if (current.loading || current.loadFailed) return
        if (current.draft != null) return
        mutableState.update {
            it.copy(
                draft = BloodPressureDraft(
                    id = newId(),
                    context = bloodPressureRegistrationContext(
                        action.followUp,
                        action.occurrence,
                    ),
                    actualAt = now().truncatedTo(ChronoUnit.MINUTES),
                    moment = action.occurrence?.expectedContext,
                    firstMeasurement = current.records.isEmpty(),
                    correctingDecisionId = action.correctingDecisionId,
                ),
                editingRecordId = null,
                errors = emptySet(),
                saveError = null,
                occurrenceAlreadyRecorded = false,
            )
        }
    }

    private fun openRecord(recordId: String) {
        val record = mutableState.value.records.singleOrNull {
            it.id == recordId
        } ?: return
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
        if (hasDecision) {
            scope.launch { loadOccurrenceDecisionRevisions(occurrence.id) }
        }
    }

    private suspend fun loadOccurrenceDecisionRevisions(
        occurrenceId: String,
    ) {
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
        val historical = measurementFollowUpForOccurrence(
            followUp, occurrence, MeasurementKind.BLOOD_PRESSURE,
        ) ?: return
        val correctingDecisionId = state.selectedOccurrenceDecision?.id
        dismissOccurrence()
        openCapture(
            BloodPressureAction.Open(
                followUp = historical,
                occurrence = occurrence.confirmedBloodPressure(),
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
        val pressure = record.value as MeasurementValue.BloodPressure
        val metadata = requireNotNull(record.bloodPressureMetadata)
        mutableState.update {
            it.copy(
                draft = BloodPressureDraft(
                    id = record.id,
                    context = metadata.registrationContext(),
                    actualAt = record.occurredAt,
                    systolic = pressure.systolic,
                    diastolic = pressure.diastolic,
                    moment = metadata.actualContext,
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

    private fun edit(
        transform: (BloodPressureDraft) -> BloodPressureDraft,
    ) {
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
        val current = mutableState.value
        val draft = current.draft ?: return
        val correcting = current.editingRecordId != null
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
        result: BloodPressureSaveResult,
        correcting: Boolean,
    ) {
        when (result) {
            is BloodPressureSaveResult.Stored -> {
                mutableState.update {
                    it.copy(
                        records = (
                            it.records.filterNot { record ->
                                record.id == result.record.id
                            } + result.record
                            ).sortedByDescending(
                                MeasurementRecord::occurredAt,
                            ),
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
                        BloodPressureEffect.UPDATED
                    } else {
                        BloodPressureEffect.SAVED
                    },
                )
            }
            is BloodPressureSaveResult.AlreadyRecorded ->
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
            is BloodPressureSaveResult.Invalid ->
                mutableState.update {
                    it.copy(saving = false, errors = result.fields)
                }
            BloodPressureSaveResult.ConsentRequired ->
                mutableState.update {
                    it.copy(
                        saving = false,
                        consentVisible = true,
                        consentAccepted = false,
                    )
                }
            BloodPressureSaveResult.AuthorizationRequired ->
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
                    BloodPressureDeleteResult.Deleted -> {
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
                                saving = false,
                                consentVisible = false,
                                consentAccepted = false,
                            )
                        }
                        effectChannel.send(BloodPressureEffect.DELETED)
                    }
                    BloodPressureDeleteResult.ConsentRequired ->
                        mutableState.update {
                            it.copy(
                                saving = false,
                                consentVisible = true,
                                consentAccepted = false,
                                pendingDeleteAfterConsent = true,
                            )
                        }
                    BloodPressureDeleteResult.AuthorizationRequired ->
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
                if (acceptConsent) {
                    consent(actor, System.currentTimeMillis())
                }
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
                        effectChannel.send(
                            BloodPressureEffect.FOLLOW_UP_SAVED,
                        )
                    }
                    is GlucoseFollowUpSaveResult.Invalid ->
                        mutableState.update {
                            it.copy(
                                followUpSaving = false,
                                followUpError =
                                    "Revisa la indicación, la frecuencia, los horarios y los avisos.",
                            )
                        }
                    GlucoseFollowUpSaveResult.ConsentRequired ->
                        mutableState.update {
                            it.copy(
                                followUpSaving = false,
                                pendingFollowUpDraft = draft,
                                consentVisible = true,
                                consentAccepted = false,
                            )
                        }
                    GlucoseFollowUpSaveResult.AuthorizationRequired ->
                        mutableState.update {
                            it.copy(
                                followUpSaving = false,
                                followUpError =
                                    "Se necesita una autorización vigente.",
                            )
                        }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        followUpSaving = false,
                        followUpError =
                            "No se pudo guardar el seguimiento.",
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
                val decision = confirmNotPerformedUseCase(
                    actor,
                    occurrence,
                    reason,
                )
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
                if (
                    mutableState.value.selectedOccurrence?.id ==
                    occurrence.id
                ) {
                    loadOccurrenceDecisionRevisions(occurrence.id)
                }
                effectChannel.send(
                    BloodPressureEffect.NOT_PERFORMED_CONFIRMED,
                )
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
        val decision =
            mutableState.value.selectedOccurrenceDecision ?: return
        if (mutableState.value.occurrenceDecisionSaving) return
        mutableState.update {
            it.copy(
                occurrenceDecisionSaving = true,
                occurrenceDecisionError = null,
            )
        }
        scope.launch {
            try {
                val corrected = correctNotPerformedUseCase(
                    actor,
                    decision,
                    reason,
                )
                mutableState.update {
                    it.copy(
                        occurrenceDecisions = (
                            it.occurrenceDecisions.filterNot { current ->
                                current.occurrenceId ==
                                    corrected.occurrenceId
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
            as? BloodPressureRegistrationContext.Scheduled
            ?: return
        edit {
            it.copy(
                id = newId(),
                context = BloodPressureRegistrationContext.Additional(
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
        effectChannel.trySend(BloodPressureEffect.CLOSED)
    }

    companion object {
        fun factory(
            actor: GlucoseActor,
            repository: BloodPressureRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T {
                    require(
                        modelClass.isAssignableFrom(
                            BloodPressureViewModel::class.java,
                        ),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return BloodPressureViewModel(actor, repository) as T
                }
            }
    }
}
