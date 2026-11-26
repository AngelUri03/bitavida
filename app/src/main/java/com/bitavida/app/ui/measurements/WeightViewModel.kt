package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.validation.CaptureLimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.WeightRegistrationContext
import com.bitavida.app.domain.model.WeightRevision
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.model.ConfirmedWeightOccurrence
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.weightRegistrationContext
import com.bitavida.app.domain.model.confirmedWeight
import com.bitavida.app.domain.model.registrationContext
import com.bitavida.app.domain.repository.WeightRepository
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.AcceptWeightSensitiveConsentUseCase
import com.bitavida.app.domain.usecase.WeightDeleteResult
import com.bitavida.app.domain.usecase.WeightDraft
import com.bitavida.app.domain.usecase.WeightField
import com.bitavida.app.domain.usecase.WeightSaveResult
import com.bitavida.app.domain.usecase.ConfirmWeightNotPerformedUseCase
import com.bitavida.app.domain.usecase.CorrectWeightMeasurementUseCase
import com.bitavida.app.domain.usecase.CorrectWeightNotPerformedUseCase
import com.bitavida.app.domain.usecase.DeleteWeightMeasurementUseCase
import com.bitavida.app.domain.usecase.DeleteWeightTargetUseCase
import com.bitavida.app.domain.usecase.GetWeightDecisionRevisionsUseCase
import com.bitavida.app.domain.usecase.GetWeightFollowUpUseCase
import com.bitavida.app.domain.usecase.GetWeightOccurrenceDecisionsUseCase
import com.bitavida.app.domain.usecase.GetWeightRecordsUseCase
import com.bitavida.app.domain.usecase.GetWeightRevisionsUseCase
import com.bitavida.app.domain.usecase.GetWeightTargetUseCase
import com.bitavida.app.domain.usecase.GlucoseFollowUpDraft
import com.bitavida.app.domain.usecase.GlucoseFollowUpSaveResult
import com.bitavida.app.domain.usecase.SaveWeightFollowUpUseCase
import com.bitavida.app.domain.usecase.SaveWeightMeasurementUseCase
import com.bitavida.app.domain.usecase.SaveWeightTargetUseCase
import com.bitavida.app.domain.usecase.WeightTargetMutationResult
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

data class WeightUiState(
    val records: List<MeasurementRecord> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val draft: WeightDraft? = null,
    val editingRecordId: String? = null,
    val selectedRecordId: String? = null,
    val revisions: List<WeightRevision> = emptyList(),
    val revisionsLoading: Boolean = false,
    val deleteConfirmationVisible: Boolean = false,
    val pendingDeleteAfterConsent: Boolean = false,
    val errors: Set<WeightField> = emptySet(),
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
    val target: WeightTarget? = null,
    val targetSheetVisible: Boolean = false,
    val targetDraft: String = "",
    val targetSaving: Boolean = false,
    val targetError: String? = null,
    val pendingTargetDraft: String? = null,
    val pendingTargetRemoval: Boolean = false,
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

sealed interface WeightAction {
    data object Reload : WeightAction
    data class Open(
        val followUp: MeasurementFollowUp? = null,
        val occurrence: ConfirmedWeightOccurrence? = null,
        val correctingDecisionId: String? = null,
    ) : WeightAction
    data class OpenRecord(val recordId: String) : WeightAction
    data class OpenOccurrence(
        val occurrence: GlucoseScheduledOccurrence,
    ) : WeightAction
    data object DismissOccurrence : WeightAction
    data object RegisterSelectedOccurrence : WeightAction
    data class CorrectNotPerformed(val reason: String?) : WeightAction
    data object DismissRecord : WeightAction
    data object CorrectSelectedRecord : WeightAction
    data object RequestDeleteSelectedRecord : WeightAction
    data object CancelDeleteSelectedRecord : WeightAction
    data object ConfirmDeleteSelectedRecord : WeightAction
    data class Kilograms(val value: String) : WeightAction
    data class ActualTime(val value: LocalDateTime) : WeightAction
    data class Note(val value: String) : WeightAction
    data object Save : WeightAction
    data class AcceptConsent(val accepted: Boolean) : WeightAction
    data object ConfirmConsent : WeightAction
    data object DismissConsent : WeightAction
    data object RequestClose : WeightAction
    data object MakeAdditional : WeightAction
    data object OpenTarget : WeightAction
    data object DismissTarget : WeightAction
    data class TargetKilograms(val value: String) : WeightAction
    data object SaveTarget : WeightAction
    data object RemoveTarget : WeightAction
    data class SaveFollowUp(
        val draft: GlucoseFollowUpDraft,
    ) : WeightAction
    data class ConfirmNotPerformed(
        val occurrence: GlucoseScheduledOccurrence,
        val reason: String?,
    ) : WeightAction
    data class DeleteFollowUp(val reason: String) : WeightAction
}

enum class WeightEffect {
    SAVED,
    UPDATED,
    DELETED,
    CLOSED,
    FOLLOW_UP_SAVED,
    NOT_PERFORMED_CONFIRMED,
}

class WeightViewModel(
    private val actor: GlucoseActor,
    repository: WeightRepository,
    private val coroutineScopeOverride: CoroutineScope? = null,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val scope get() = coroutineScopeOverride ?: viewModelScope
    private val load = GetWeightRecordsUseCase(repository)
    private val save = SaveWeightMeasurementUseCase(repository, now)
    private val correct = CorrectWeightMeasurementUseCase(repository, now)
    private val delete = DeleteWeightMeasurementUseCase(repository, now)
    private val loadRevisions = GetWeightRevisionsUseCase(repository)
    private val loadTarget = GetWeightTargetUseCase(repository)
    private val saveTarget = SaveWeightTargetUseCase(repository)
    private val deleteTarget = DeleteWeightTargetUseCase(repository)
    private val consent = AcceptWeightSensitiveConsentUseCase(repository)
    private val loadFollowUp = GetWeightFollowUpUseCase(repository)
    private val loadDecisions = GetWeightOccurrenceDecisionsUseCase(repository)
    private val loadDecisionRevisions =
        GetWeightDecisionRevisionsUseCase(repository)
    private val correctNotPerformedUseCase =
        CorrectWeightNotPerformedUseCase(repository, now, newId)
    private val saveFollowUp =
        SaveWeightFollowUpUseCase(repository, now, newId)
    private val confirmNotPerformedUseCase =
        ConfirmWeightNotPerformedUseCase(repository, now, newId)
    private val mutableState = MutableStateFlow(WeightUiState())
    val state = mutableState.asStateFlow()
    private val effectChannel = Channel<WeightEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        reload()
    }

    fun onAction(action: WeightAction) {
        if (mutableState.value.saving || mutableState.value.targetSaving) return
        when (action) {
            WeightAction.Reload -> reload()
            is WeightAction.Open -> openCapture(action)
            is WeightAction.OpenRecord -> openRecord(action.recordId)
            is WeightAction.OpenOccurrence ->
                openOccurrence(action.occurrence)
            WeightAction.DismissOccurrence -> dismissOccurrence()
            WeightAction.RegisterSelectedOccurrence ->
                registerSelectedOccurrence()
            is WeightAction.CorrectNotPerformed ->
                correctNotPerformed(action.reason)
            WeightAction.DismissRecord -> dismissRecord()
            WeightAction.CorrectSelectedRecord -> correctSelectedRecord()
            WeightAction.RequestDeleteSelectedRecord ->
                mutableState.update {
                    it.copy(deleteConfirmationVisible = true, saveError = null)
                }
            WeightAction.CancelDeleteSelectedRecord ->
                mutableState.update {
                    it.copy(deleteConfirmationVisible = false, saveError = null)
                }
            WeightAction.ConfirmDeleteSelectedRecord ->
                deleteSelectedRecord(acceptConsent = false)
            is WeightAction.Kilograms ->
                edit { it.copy(kilograms = action.value.asWeightInput()) }
            is WeightAction.ActualTime ->
                edit { it.copy(actualAt = action.value) }
            is WeightAction.Note ->
                edit { draft ->
                    if (CaptureLimit.MEASUREMENT_NOTE.acceptsEdit(draft.note, action.value)) draft.copy(note = action.value)
                    else draft
                }
            WeightAction.Save -> persist(acceptConsent = false)
            is WeightAction.AcceptConsent ->
                mutableState.update {
                    it.copy(consentAccepted = action.accepted)
                }
            WeightAction.ConfirmConsent -> confirmConsent()
            WeightAction.DismissConsent ->
                mutableState.update {
                    it.copy(
                        consentVisible = false,
                        consentAccepted = false,
                        pendingDeleteAfterConsent = false,
                        pendingFollowUpDraft = null,
                        pendingTargetDraft = null,
                        pendingTargetRemoval = false,
                    )
                }
            WeightAction.RequestClose -> closeCapture()
            WeightAction.MakeAdditional -> makeAdditional()
            WeightAction.OpenTarget -> openTarget()
            WeightAction.DismissTarget -> dismissTarget()
            is WeightAction.TargetKilograms ->
                mutableState.update {
                    it.copy(
                        targetDraft = action.value.asWeightInput(),
                        targetError = null,
                    )
                }
            WeightAction.SaveTarget -> persistTarget(acceptConsent = false)
            WeightAction.RemoveTarget -> removeTarget(acceptConsent = false)
            is WeightAction.SaveFollowUp ->
                persistFollowUp(action.draft, false)
            is WeightAction.ConfirmNotPerformed ->
                confirmNotPerformed(action.occurrence, action.reason)
            is WeightAction.DeleteFollowUp ->
                deleteFollowUp(action.reason)
        }
    }

    private fun confirmConsent() {
        if (!mutableState.value.consentAccepted) return
        when {
            mutableState.value.pendingTargetRemoval ->
                removeTarget(true)
            mutableState.value.pendingTargetDraft != null ->
                persistTarget(true)
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

    private fun openTarget() {
        val current = mutableState.value
        if (current.loading || current.loadFailed) return
        mutableState.update {
            it.copy(
                targetSheetVisible = true,
                targetDraft = current.target?.kilograms
                    ?.stripTrailingZeros()
                    ?.toPlainString()
                    .orEmpty(),
                targetError = null,
                pendingTargetDraft = null,
                pendingTargetRemoval = false,
            )
        }
    }

    private fun dismissTarget() {
        mutableState.update {
            it.copy(
                targetSheetVisible = false,
                targetDraft = "",
                targetError = null,
                targetSaving = false,
                pendingTargetDraft = null,
                pendingTargetRemoval = false,
                consentVisible = false,
                consentAccepted = false,
            )
        }
    }

    private fun persistTarget(acceptConsent: Boolean) {
        val current = mutableState.value
        if (!current.targetSheetVisible) return
        val draft = if (acceptConsent) {
            current.pendingTargetDraft ?: current.targetDraft
        } else {
            current.targetDraft
        }
        mutableState.update {
            it.copy(targetSaving = true, targetError = null)
        }
        scope.launch {
            try {
                if (acceptConsent) {
                    consent(actor, System.currentTimeMillis())
                }
                when (val result = saveTarget(actor, draft)) {
                    is WeightTargetMutationResult.Stored ->
                        mutableState.update {
                            it.copy(
                                target = result.target,
                                targetSheetVisible = false,
                                targetDraft = "",
                                targetSaving = false,
                                targetError = null,
                                pendingTargetDraft = null,
                                pendingTargetRemoval = false,
                                consentVisible = false,
                                consentAccepted = false,
                            )
                        }
                    WeightTargetMutationResult.Invalid ->
                        mutableState.update {
                            it.copy(
                                targetSaving = false,
                                targetError =
                                    "Ingresa un peso objetivo válido en kg.",
                            )
                        }
                    WeightTargetMutationResult.ConsentRequired ->
                        mutableState.update {
                            it.copy(
                                targetSaving = false,
                                pendingTargetDraft = draft,
                                pendingTargetRemoval = false,
                                consentVisible = true,
                                consentAccepted = false,
                            )
                        }
                    WeightTargetMutationResult.AuthorizationRequired ->
                        mutableState.update {
                            it.copy(
                                targetSaving = false,
                                targetError =
                                    "Se necesita una autorización vigente para guardar.",
                            )
                        }
                    WeightTargetMutationResult.Removed -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        targetSaving = false,
                        targetError =
                            "No se pudo guardar el peso objetivo. Vuelve a intentarlo.",
                    )
                }
            }
        }
    }

    private fun removeTarget(acceptConsent: Boolean) {
        val current = mutableState.value
        if (!current.targetSheetVisible || current.target == null) return
        mutableState.update {
            it.copy(targetSaving = true, targetError = null)
        }
        scope.launch {
            try {
                if (acceptConsent) {
                    consent(actor, System.currentTimeMillis())
                }
                when (deleteTarget(actor)) {
                    WeightTargetMutationResult.Removed ->
                        mutableState.update {
                            it.copy(
                                target = null,
                                targetSheetVisible = false,
                                targetDraft = "",
                                targetSaving = false,
                                targetError = null,
                                pendingTargetDraft = null,
                                pendingTargetRemoval = false,
                                consentVisible = false,
                                consentAccepted = false,
                            )
                        }
                    WeightTargetMutationResult.ConsentRequired ->
                        mutableState.update {
                            it.copy(
                                targetSaving = false,
                                pendingTargetDraft = null,
                                pendingTargetRemoval = true,
                                consentVisible = true,
                                consentAccepted = false,
                            )
                        }
                    WeightTargetMutationResult.AuthorizationRequired ->
                        mutableState.update {
                            it.copy(
                                targetSaving = false,
                                targetError =
                                    "Se necesita una autorización vigente para quitarlo.",
                            )
                        }
                    WeightTargetMutationResult.Invalid,
                    is WeightTargetMutationResult.Stored,
                    -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        targetSaving = false,
                        targetError =
                            "No se pudo quitar el peso objetivo. Vuelve a intentarlo.",
                    )
                }
            }
        }
    }

    private fun openCapture(action: WeightAction.Open) {
        val current = mutableState.value
        if (current.loading || current.loadFailed) return
        if (current.draft != null) return
        mutableState.update {
            it.copy(
                draft = WeightDraft(
                    id = newId(),
                    context = weightRegistrationContext(
                        action.followUp,
                        action.occurrence,
                    ),
                    actualAt = now().truncatedTo(ChronoUnit.MINUTES),
                    heightMeters = current.records.asSequence()
                        .mapNotNull { (it.value as? MeasurementValue.Weight)?.heightMeters }
                        .firstOrNull(),
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
            followUp, occurrence, MeasurementKind.WEIGHT,
        ) ?: return
        val correctingDecisionId = state.selectedOccurrenceDecision?.id
        dismissOccurrence()
        openCapture(
            WeightAction.Open(
                followUp = historical,
                occurrence = occurrence.confirmedWeight(),
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
        val weight = record.value as MeasurementValue.Weight
        val metadata = requireNotNull(record.weightMetadata)
        mutableState.update {
            it.copy(
                draft = WeightDraft(
                    id = record.id,
                    context = metadata.registrationContext(),
                    actualAt = record.occurredAt,
                    kilograms = weight.kilograms.stripTrailingZeros().toPlainString(),
                    heightMeters = weight.heightMeters,
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
        transform: (WeightDraft) -> WeightDraft,
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
                val target = loadTarget(actor)
                val followUp = loadFollowUp(actor)
                val decisions = followUp?.let {
                    loadDecisions(actor, it.id)
                }.orEmpty()
                mutableState.update {
                    it.copy(
                        records = records,
                        target = target,
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
        result: WeightSaveResult,
        correcting: Boolean,
    ) {
        when (result) {
            is WeightSaveResult.Stored -> {
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
                        WeightEffect.UPDATED
                    } else {
                        WeightEffect.SAVED
                    },
                )
            }
            is WeightSaveResult.AlreadyRecorded ->
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
            is WeightSaveResult.Invalid ->
                mutableState.update {
                    it.copy(saving = false, errors = result.fields)
                }
            WeightSaveResult.ConsentRequired ->
                mutableState.update {
                    it.copy(
                        saving = false,
                        consentVisible = true,
                        consentAccepted = false,
                    )
                }
            WeightSaveResult.AuthorizationRequired ->
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
                    WeightDeleteResult.Deleted -> {
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
                        effectChannel.send(WeightEffect.DELETED)
                    }
                    WeightDeleteResult.ConsentRequired ->
                        mutableState.update {
                            it.copy(
                                saving = false,
                                consentVisible = true,
                                consentAccepted = false,
                                pendingDeleteAfterConsent = true,
                            )
                        }
                    WeightDeleteResult.AuthorizationRequired ->
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
                            WeightEffect.FOLLOW_UP_SAVED,
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
                    WeightEffect.NOT_PERFORMED_CONFIRMED,
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
            as? WeightRegistrationContext.Scheduled
            ?: return
        edit {
            it.copy(
                id = newId(),
                context = WeightRegistrationContext.Additional(
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
        effectChannel.trySend(WeightEffect.CLOSED)
    }

    private fun String.asWeightInput(): String {
        val normalized = replace(',', '.')
            .filter { it.isDigit() || it == '.' }
        val firstSeparator = normalized.indexOf('.')
        val integral = if (firstSeparator < 0) normalized else normalized.take(firstSeparator)
        val decimals = if (firstSeparator < 0) "" else normalized.drop(firstSeparator + 1)
            .filter(Char::isDigit)
            .take(2)
        return when {
            firstSeparator < 0 -> integral.take(3)
            else -> "${integral.take(3)}.$decimals"
        }
    }

    companion object {
        fun factory(
            actor: GlucoseActor,
            repository: WeightRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T {
                    require(
                        modelClass.isAssignableFrom(
                            WeightViewModel::class.java,
                        ),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return WeightViewModel(actor, repository) as T
                }
            }
    }
}
