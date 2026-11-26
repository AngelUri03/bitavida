package com.bitavida.app.ui.medical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicationActivityRelation
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationCycle
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationHourlyIntervalMode
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationIntakeRevision
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderEffectiveState
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.lastActiveDateBeforeCurrentSuspension
import com.bitavida.app.domain.model.lastTreatmentDate
import com.bitavida.app.domain.model.pendingReactivation
import com.bitavida.app.domain.repository.MedicalCareConcurrentChangeException
import com.bitavida.app.domain.repository.MedicalCareIntegrityException
import com.bitavida.app.domain.repository.MedicalCareRepository
import com.bitavida.app.domain.repository.MedicalCareStorageUnavailableException
import com.bitavida.app.domain.usecase.ChangeMedicalIndicationStatusCommand
import com.bitavida.app.domain.usecase.ChangeMedicalIndicationStatusUseCase
import com.bitavida.app.domain.usecase.ChangeMedicationLifecycleCommand
import com.bitavida.app.domain.usecase.ChangeMedicationLifecycleUseCase
import com.bitavida.app.domain.usecase.FinalizeExpiredMedicationPlansUseCase
import com.bitavida.app.domain.usecase.GetMedicalCareOverviewUseCase
import com.bitavida.app.domain.usecase.MedicalCareField
import com.bitavida.app.domain.usecase.MedicalCareMutationResult
import com.bitavida.app.domain.usecase.MedicalCareValidationError
import com.bitavida.app.domain.usecase.MedicalCareValidationIssue
import com.bitavida.app.domain.usecase.RegisterMedicalIndicationCommand
import com.bitavida.app.domain.usecase.RegisterMedicalIndicationUseCase
import com.bitavida.app.domain.usecase.RegisterMedicationIntakeCommand
import com.bitavida.app.domain.usecase.RegisterMedicationIntakeUseCase
import com.bitavida.app.domain.usecase.RegisterMedicationPlanCommand
import com.bitavida.app.domain.usecase.RegisterMedicationPlanUseCase
import com.bitavida.app.domain.usecase.ReviseMedicalIndicationCommand
import com.bitavida.app.domain.usecase.ReviseMedicalIndicationUseCase
import com.bitavida.app.domain.usecase.ReviseMedicationIntakeCommand
import com.bitavida.app.domain.usecase.ReviseMedicationIntakeUseCase
import com.bitavida.app.domain.usecase.ReviseMedicationPlanCommand
import com.bitavida.app.domain.usecase.ReviseMedicationPlanUseCase
import com.bitavida.app.domain.validation.MedicationValueRules
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.domain.validation.hasMedicationIntakeReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class MedicalPlanViewModel(
    private val profile: CareProfile,
    private val repository: MedicalCareRepository,
    private val coroutineScopeOverride: CoroutineScope? = null,
    private val todayMedicationNow: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {
    private val taskScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope
    private val getOverview = GetMedicalCareOverviewUseCase(repository)
    private val registerIndication = RegisterMedicalIndicationUseCase(repository)
    private val reviseIndication = ReviseMedicalIndicationUseCase(repository)
    private val changeStatus = ChangeMedicalIndicationStatusUseCase(repository)
    private val registerMedication = RegisterMedicationPlanUseCase(repository)
    private val reviseMedication = ReviseMedicationPlanUseCase(repository)
    private val finalizeExpiredMedicationPlans =
        FinalizeExpiredMedicationPlansUseCase(repository)
    private val changeMedicationLifecycle =
        ChangeMedicationLifecycleUseCase(repository)
    private val registerMedicationIntake =
        RegisterMedicationIntakeUseCase(repository)
    private val reviseMedicationIntake =
        ReviseMedicationIntakeUseCase(repository)

    private val mutableState = MutableStateFlow(MedicalPlanUiState())
    val state: StateFlow<MedicalPlanUiState> = mutableState.asStateFlow()
    private var pendingTodayMedicationWrite: (() -> Unit)? = null
    private val todayUndoableMedicationRevisions = mutableMapOf<String, Int>()

    init {
        load()
        observeMedicationExpiryBoundary()
    }

    fun onAction(action: MedicalPlanAction) {
        when (action) {
            MedicalPlanAction.Retry -> load()
            MedicalPlanAction.Back -> goBack()
            MedicalPlanAction.OpenOverview -> update {
                copy(
                    screen = MedicalPlanScreen.OVERVIEW,
                    selectedSection = null,
                    selectedIndicationId = null,
                    selectedMedicationPlanId = null,
                    fieldErrors = emptySet(),
                    globalError = null,
                )
            }
            is MedicalPlanAction.OpenSection -> update {
                copy(
                    screen = MedicalPlanScreen.SECTION,
                    selectedSection = action.section,
                    medicationIntakeDraft = null,
                    selectedIndicationId = null,
                    selectedMedicationPlanId = null,
                    fieldErrors = emptySet(),
                    globalError = null,
                )
            }
            MedicalPlanAction.OpenCreateIndication -> update {
                copy(
                    screen = MedicalPlanScreen.INDICATION_EDITOR,
                    editingExisting = false,
                    indicationDraft = IndicationDraft(),
                    fieldErrors = emptySet(),
                    globalError = null,
                )
            }
            is MedicalPlanAction.OpenIndication -> update {
                copy(
                    screen = MedicalPlanScreen.INDICATION_DETAIL,
                    selectedIndicationId = action.id,
                    selectedMedicationPlanId = null,
                    fieldErrors = emptySet(),
                    globalError = null,
                )
            }
            MedicalPlanAction.OpenIndicationHistory -> update {
                copy(screen = MedicalPlanScreen.INDICATION_HISTORY)
            }
            MedicalPlanAction.OpenEditIndication -> openIndicationEditor()
            MedicalPlanAction.OpenCreateMedication -> update {
                copy(
                    screen = MedicalPlanScreen.MEDICATION_EDITOR,
                    editingExisting = false,
                    medicationDraft = MedicationDraft(),
                    fieldErrors = emptySet(),
                    globalError = null,
                )
            }
            MedicalPlanAction.OpenMedicationRegistration -> update {
                copy(
                    screen = MedicalPlanScreen.MEDICATION_REGISTRATION,
                    selectedSection = PlanSection.MEDICATIONS,
                    editingExisting = false,
                    newMedicationPeriodSourcePlanId = null,
                    medicationEditPurpose = null,
                    originalGuidedMedicationDraft = null,
                    editorEntryGuidedMedicationDraft = null,
                    medicationRegistrationStep = MedicationRegistrationStep.NAME,
                    medicationScheduleSubstep =
                        MedicationScheduleSubstep.FREQUENCY,
                    guidedMedicationDraft = GuidedMedicationDraft(),
                    medicationRegistrationErrors = emptySet(),
                    medicationScheduleError = null,
                    recentlyRemovedMedicationAdministration = null,
                    medicationExitConfirmationVisible = false,
                    globalError = null,
                )
            }
            is MedicalPlanAction.OpenMedication -> update {
                copy(
                    screen = MedicalPlanScreen.MEDICATION_DETAIL,
                    selectedMedicationPlanId = action.id,
                    newMedicationPeriodSourcePlanId = null,
                    medicationIntakeDraft = null,
                    selectedMedicationTodayKey = null,
                    medicationTodayDate = null,
                    lastQuickMedicationIntakeId = null,
                    fieldErrors = emptySet(),
                    globalError = null,
                )
            }
            MedicalPlanAction.OpenEditMedication ->
                openMedicationReview(MedicationEditPurpose.CORRECTION)
            MedicalPlanAction.OpenEditMedicationSchedule ->
                openMedicationReview(MedicationEditPurpose.INDICATION_UPDATE)
            MedicalPlanAction.OpenCorrectMedication ->
                openMedicationReview(MedicationEditPurpose.CORRECTION)
            MedicalPlanAction.OpenUpdateMedicationIndication ->
                openMedicationReview(MedicationEditPurpose.INDICATION_UPDATE)
            MedicalPlanAction.StartNewMedicationPeriod ->
                openNewMedicationPeriod()
            MedicalPlanAction.OpenCorrectMedicationFromReactivation ->
                openMedicationEditFromReactivation(
                    MedicationEditPurpose.CORRECTION,
                )
            MedicalPlanAction.OpenUpdateMedicationIndicationFromReactivation ->
                openMedicationEditFromReactivation(
                    MedicationEditPurpose.INDICATION_UPDATE,
                )
            MedicalPlanAction.OpenMedicationManagement -> update {
                copy(
                    screen = MedicalPlanScreen.MEDICATION_MANAGEMENT,
                    fieldErrors = emptySet(),
                    globalError = null,
                )
            }
            MedicalPlanAction.OpenMedicationChanges -> update {
                copy(screen = MedicalPlanScreen.MEDICATION_HISTORY, medicationChangesOnly = true)
            }
            is MedicalPlanAction.SelectMedicationTodayRow -> update {
                copy(selectedMedicationTodayKey = action.key, medicationTodayDate = action.day)
            }
            MedicalPlanAction.OpenMedicationHistory -> update {
                val now = LocalDateTime.now()
                val today = now.toLocalDate()
                val medication = selectedMedicationPlan
                val indicationStatus = medication?.medicalIndicationId?.let { id ->
                    overview.indications.singleOrNull { it.id == id }
                        ?.currentRevision?.status
                }
                val displayState = medication?.displayState(
                    now,
                    indicationStatus,
                )
                val zoneId = ZoneId.systemDefault()
                val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
                val initialDate = when (displayState) {
                    MedicationPlanDisplayState.UPCOMING ->
                        medication.pendingReactivation(nowEpochMillis, zoneId)
                            ?.effectiveAtEpochMillis
                            ?.let {
                                Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
                            }
                            ?: medication.currentRevision.configuration?.startsOn
                            ?: medication.currentRevision.startsOn
                    MedicationPlanDisplayState.SUSPENDED ->
                        medication.lastActiveDateBeforeCurrentSuspension(
                            nowEpochMillis,
                        ) ?: today
                    MedicationPlanDisplayState.COMPLETED ->
                        medication.lastTreatmentDate(
                            nowEpochMillis,
                            zoneId,
                        ) ?: today
                    else -> today
                }
                copy(
                    screen = MedicalPlanScreen.MEDICATION_HISTORY,
                    medicationChangesOnly = false,
                    medicationHistoryMonth = initialDate.withDayOfMonth(1),
                    selectedMedicationHistoryDate = initialDate,
                )
            }
            MedicalPlanAction.OpenRegisterMedicationIntake ->
                openMedicationIntakeEditor(recordId = null, occurrence = null)
            is MedicalPlanAction.OpenMedicationIntakeOptions ->
                openMedicationIntakeEditor(
                    recordId = null,
                    occurrence = action.occurrence,
                )
            is MedicalPlanAction.QuickRegisterMedicationIntake ->
                quickRegisterMedicationIntake(action)
            is MedicalPlanAction.OpenTodayMedicationIntake -> openTodayMedicationIntake(action)
            is MedicalPlanAction.UndoTodayMedicationIntake -> undoTodayMedicationIntake(action)
            is MedicalPlanAction.DismissTodayMedicationFeedback -> {
                val feedback = state.value.todayMedicationFeedback
                if (feedback?.requestId == action.requestId && feedback.status !in setOf(
                        TodayMedicationFeedbackStatus.SAVING, TodayMedicationFeedbackStatus.AWAITING_CONSENT,
                    )
                ) {
                    feedback.recordId?.let(todayUndoableMedicationRevisions::remove)
                    update { copy(todayMedicationFeedback = null) }
                }
            }
            MedicalPlanAction.UndoQuickMedicationIntake ->
                undoQuickMedicationIntake()
            is MedicalPlanAction.OpenEditMedicationIntake ->
                openMedicationIntakeEditor(action.recordId, occurrence = null)
            is MedicalPlanAction.MedicationIntakeDraftChanged -> update {
                copy(
                    medicationIntakeDraft = action.draft,
                    fieldErrors = emptySet(),
                )
            }
            MedicalPlanAction.SaveMedicationIntake -> saveMedicationIntake()
            MedicalPlanAction.DismissMedicationIntake -> {
                if (state.value.medicationIntakeDraft?.fromToday != true || !state.value.isSaving) {
                    pendingTodayMedicationWrite = null
                    update {
                        copy(
                            medicationIntakeDraft = null,
                            fieldErrors = emptySet(),
                        )
                    }
                }
            }
            is MedicalPlanAction.RequestMedicationLifecycle -> update {
                val now = LocalDateTime.now()
                copy(
                    medicationLifecycleDraft = medicationLifecycleDraftFor(
                        status = action.status,
                        plan = selectedMedicationPlan,
                        now = now,
                    ),
                    fieldErrors = emptySet(),
                )
            }
            is MedicalPlanAction.MedicationLifecycleDraftChanged -> update {
                copy(
                    medicationLifecycleDraft = action.draft,
                    fieldErrors = emptySet(),
                )
            }
            MedicalPlanAction.ConfirmMedicationLifecycle ->
                confirmMedicationLifecycle()
            MedicalPlanAction.CancelScheduledMedicationReactivation ->
                cancelScheduledMedicationReactivation()
            MedicalPlanAction.DismissMedicationLifecycle -> update {
                copy(
                    medicationLifecycleDraft = null,
                    fieldErrors = emptySet(),
                )
            }
            MedicalPlanAction.PreviousMedicationHistoryWeek -> update {
                val date = selectedMedicationHistoryDate.minusWeeks(1)
                copy(
                    medicationHistoryMonth = date.withDayOfMonth(1),
                    selectedMedicationHistoryDate = date,
                )
            }
            MedicalPlanAction.NextMedicationHistoryWeek -> update {
                val date = selectedMedicationHistoryDate.plusWeeks(1)
                copy(
                    medicationHistoryMonth = date.withDayOfMonth(1),
                    selectedMedicationHistoryDate = date,
                )
            }
            is MedicalPlanAction.SelectMedicationHistoryDate -> update {
                copy(
                    medicationHistoryMonth = action.date.withDayOfMonth(1),
                    selectedMedicationHistoryDate = action.date,
                )
            }
            is MedicalPlanAction.IndicationDraftChanged -> update {
                copy(indicationDraft = action.draft, fieldErrors = emptySet())
            }
            is MedicalPlanAction.MedicationDraftChanged -> update {
                copy(medicationDraft = action.draft, fieldErrors = emptySet())
            }
            is MedicalPlanAction.GuidedMedicationDraftChanged -> update {
                copy(
                    guidedMedicationDraft = action.draft,
                    medicationRegistrationErrors = emptySet(),
                    medicationRegistrationSaveError = null,
                    medicationScheduleError = null,
                )
            }
            is MedicalPlanAction.ContinueMedicationRegistration ->
                continueMedicationRegistration(action.expectedStep)
            MedicalPlanAction.PreviousMedicationRegistrationStep ->
                previousMedicationRegistrationStep()
            is MedicalPlanAction.EditMedicationRegistrationStep -> update {
                copy(
                    medicationRegistrationStep = action.step,
                    editorEntryGuidedMedicationDraft = guidedMedicationDraft,
                    medicationScheduleSubstep =
                        if (action.step == MedicationRegistrationStep.SCHEDULE) {
                            action.scheduleSubstep
                        } else {
                            medicationScheduleSubstep
                        },
                    medicationRegistrationErrors = emptySet(),
                    medicationRegistrationSaveError = null,
                    medicationScheduleError = null,
                )
            }
            is MedicalPlanAction.UpsertExactMedicationAdministration ->
                upsertExactMedicationAdministration(action)
            is MedicalPlanAction.CommitMedicationWindow ->
                commitMedicationWindow(action.frequency)
            is MedicalPlanAction.AddActivityMedicationAdministration ->
                addActivityMedicationAdministration(action.frequency)
            is MedicalPlanAction.EditMedicationWindow ->
                editMedicationWindow(action.frequency, action.administrationId)
            is MedicalPlanAction.RemoveMedicationAdministration ->
                removeMedicationAdministration(action.frequency, action.administrationId)
            MedicalPlanAction.UndoRemoveMedicationAdministration ->
                undoRemoveMedicationAdministration()
            is MedicalPlanAction.UpdateMedicationAdministration ->
                updateMedicationAdministration(action)
            MedicalPlanAction.SaveGuidedMedication -> saveGuidedMedication()
            MedicalPlanAction.CompleteGuidedMedicationSuccess ->
                completeGuidedMedicationSuccess()
            is MedicalPlanAction.SensitiveConsentChanged -> update {
                copy(sensitiveConsentAccepted = action.accepted)
            }
            MedicalPlanAction.ConfirmSensitiveConsent ->
                confirmSensitiveConsent()
            MedicalPlanAction.DeclineSensitiveConsent -> {
                if (pendingTodayMedicationWrite != null) {
                    pendingTodayMedicationWrite = null
                    failTodayMedication("No se guardó la toma. Sigue pendiente hasta que confirmes su registro.", discardDraft = true)
                } else update {
                    copy(
                        sensitiveConsentDialogVisible = false,
                        sensitiveConsentAccepted = false,
                        isSaving = false,
                    )
                }
            }
            MedicalPlanAction.ContinueEditingMedicationRegistration -> update {
                copy(medicationExitConfirmationVisible = false)
            }
            MedicalPlanAction.DiscardMedicationRegistration ->
                discardMedicationRegistration()
            MedicalPlanAction.SaveIndication -> saveIndication()
            MedicalPlanAction.SaveMedication -> saveMedication()
            is MedicalPlanAction.RequestStatusChange -> update {
                copy(statusChangeDraft = StatusChangeDraft(action.status))
            }
            is MedicalPlanAction.StatusChangeDraftChanged -> update {
                copy(statusChangeDraft = action.draft, fieldErrors = emptySet())
            }
            MedicalPlanAction.ConfirmStatusChange -> confirmStatusChange()
            MedicalPlanAction.DismissStatusChange -> update {
                copy(statusChangeDraft = null, fieldErrors = emptySet())
            }
            MedicalPlanAction.EffectConsumed -> update { copy(effect = null) }
        }
    }

    private fun load() {
        update { copy(isLoading = true, globalError = null) }
        taskScope.launch {
            repositoryCall {
                finalizeExpiredMedicationPlans(profile.personUnderCare.id)
                val overview = getOverview(profile.personUnderCare.id)
                update {
                    copy(
                        overview = overview,
                        isLoading = false,
                        globalError = null,
                    )
                }
            }
        }
    }

    private fun observeMedicationExpiryBoundary() {
        taskScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val zoneId = ZoneId.systemDefault()
                val nextBoundary = now.toLocalDate().plusDays(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                val waitMillis = (nextBoundary - System.currentTimeMillis())
                    .coerceAtLeast(1_000L)
                delay(waitMillis)
                repositoryCall {
                    finalizeExpiredMedicationPlans(profile.personUnderCare.id)
                    val overview = getOverview(profile.personUnderCare.id)
                    update {
                        copy(
                            overview = overview,
                            isLoading = false,
                            globalError = null,
                        )
                    }
                }
            }
        }
    }

    private fun openIndicationEditor() {
        val revision = mutableState.value.selectedIndication?.currentRevision ?: return
        update {
            copy(
                screen = MedicalPlanScreen.INDICATION_EDITOR,
                editingExisting = true,
                indicationDraft = IndicationDraft(
                    originalText = revision.originalText,
                    indicatedOn = revision.indicatedOn.toInputText(),
                    declaredProfessionalOrSource =
                        revision.declaredProfessionalOrSource.orEmpty(),
                    validFrom = revision.validFrom?.toInputText().orEmpty(),
                    validUntil = revision.validUntil?.toInputText().orEmpty(),
                ),
                fieldErrors = emptySet(),
                globalError = null,
            )
        }
    }

    private fun openMedicationEditFromReactivation(
        purpose: MedicationEditPurpose,
    ) {
        val current = mutableState.value
        val plan = current.selectedMedicationPlan ?: return
        val linkedIndicationId = plan.medicalIndicationId
        if (
            purpose == MedicationEditPurpose.INDICATION_UPDATE &&
            linkedIndicationId != null &&
            current.overview.indications.any { it.id == linkedIndicationId }
        ) {
            update {
                copy(
                    selectedIndicationId = linkedIndicationId,
                    medicationLifecycleDraft = null,
                    resumeReactivationAfterEdit = true,
                )
            }
            openIndicationEditor()
        } else {
            openMedicationReview(purpose, resumeReactivation = true)
        }
    }

    private fun openNewMedicationPeriod() {
        val source = mutableState.value.selectedMedicationPlan ?: return
        val configuration = source.currentRevision.configuration ?: return
        if ((configuration.endsOn ?: source.currentRevision.endsOn) == null) return
        val draft = source.currentRevision.toGuidedMedicationDraft(configuration).copy(
            treatmentStartMode = null,
            treatmentStartDate = null,
            treatmentEndDate = null,
            administrationsByFrequency =
                source.currentRevision.toGuidedMedicationDraft(configuration)
                    .administrationsByFrequency
                    .mapValues { (_, administrations) ->
                        administrations.map { administration ->
                            administration.copy(persistedId = null)
                        }
                    },
        )
        update {
            copy(
                screen = MedicalPlanScreen.MEDICATION_REGISTRATION,
                editingExisting = false,
                newMedicationPeriodSourcePlanId = source.id,
                medicationEditPurpose = MedicationEditPurpose.INDICATION_UPDATE,
                originalGuidedMedicationDraft = null,
                editorEntryGuidedMedicationDraft = null,
                medicationRegistrationStep = MedicationRegistrationStep.SCHEDULE,
                medicationScheduleSubstep = MedicationScheduleSubstep.FREQUENCY,
                guidedMedicationDraft = draft,
                medicationRegistrationErrors = emptySet(),
                medicationRegistrationSaveError = null,
                medicationScheduleError = null,
                recentlyRemovedMedicationAdministration = null,
                medicationExitConfirmationVisible = false,
                medicationLifecycleDraft = null,
                resumeReactivationAfterEdit = false,
                fieldErrors = emptySet(),
                globalError = null,
                effect = null,
            )
        }
    }

    private fun openMedicationReview(
        purpose: MedicationEditPurpose,
        resumeReactivation: Boolean = false,
    ) {
        val revision = mutableState.value.selectedMedicationPlan?.currentRevision ?: return
        val configuration = revision.configuration ?: return
        val draft = revision.toGuidedMedicationDraft(configuration)
        update {
            copy(
                screen = MedicalPlanScreen.MEDICATION_REGISTRATION,
                editingExisting = true,
                medicationEditPurpose = purpose,
                originalGuidedMedicationDraft = draft,
                editorEntryGuidedMedicationDraft = null,
                medicationRegistrationStep = MedicationRegistrationStep.REVIEW,
                medicationScheduleSubstep = MedicationScheduleSubstep.FREQUENCY,
                guidedMedicationDraft = draft,
                medicationRegistrationErrors = emptySet(),
                medicationScheduleError = null,
                recentlyRemovedMedicationAdministration = null,
                medicationExitConfirmationVisible = false,
                medicationLifecycleDraft = null,
                resumeReactivationAfterEdit = resumeReactivation,
                fieldErrors = emptySet(),
                globalError = null,
            )
        }
    }

    private fun openMedicationIntakeEditor(
        recordId: String?,
        occurrence: com.bitavida.app.domain.usecase.UpcomingMedicationAdministration?,
    ) {
        val current = mutableState.value
        if (current.isSaving) return
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val plan = current.selectedMedicationPlan ?: return
        val records = current.overview.intakesFor(plan.id)
        val occurrencePlan = occurrence?.let {
            medicationPlanSnapshotForDay(plan, it.startsAt.toLocalDate())
        }
        val record = if (recordId != null) {
            records.singleOrNull { it.id == recordId }
        } else {
            occurrence?.let { selected ->
                intakeForOccurrence(
                    records = records,
                    occurrence = selected,
                    occurrences = medicationOccurrencesForDay(
                        requireNotNull(occurrencePlan),
                        selected.startsAt.toLocalDate(),
                    ),
                    plan = plan,
                )
            }
        }
        if (recordId == null && occurrence != null && intakesForOccurrence(
                records, occurrence, medicationOccurrencesForDay(requireNotNull(occurrencePlan), occurrence.startsAt.toLocalDate()), plan,
            ).size > 1) {
            update { copy(globalError = MedicalPlanGlobalError.AMBIGUOUS_MEDICATION_OCCASION) }
            return
        }
        val existing = record?.currentRevision
        val scheduledAt = existing?.scheduledAt ?: occurrence?.startsAt ?: now
        val scheduledPlan = occurrencePlan
            ?: medicationPlanSnapshotForDay(plan, scheduledAt.toLocalDate())
        val asNeeded = occurrence == null &&
            scheduledPlan.currentRevision.configuration?.frequency ==
            MedicationFrequencyKind.AS_NEEDED
        val configuredAmount = if (record != null) {
            existing?.actualAmount
        } else {
            occurrence?.amount
                ?: scheduledPlan.currentRevision.configuration?.baseAmount.takeIf { asNeeded }
        }
        update {
            copy(
                medicationIntakeDraft = MedicationIntakeDraft(
                    recordId = record?.id,
                    outcome = existing?.outcome ?: MedicationIntakeOutcome.COMPLETED,
                    outcomeExplicitlySelected = record != null || asNeeded,
                    scheduledDate = scheduledAt.toLocalDate().toString(),
                    scheduledTime = scheduledAt.toLocalTime().toString(),
                    actualDate = (
                        existing?.actualAt ?: occurrence?.startsAt ?: now
                    ).toLocalDate().toString(),
                    actualTime = (existing?.actualAt ?: now).toLocalTime().toString(),
                    actualAmountValue = configuredAmount?.value.orEmpty(),
                    actualAmountUnit = configuredAmount?.unit.orEmpty(),
                    observation = existing?.observation.orEmpty(),
                    reason = if (record != null) {
                        "Corrección de la hora, respuesta o nota confirmada por la persona cuidadora"
                    } else "",
                    scheduledOccurrenceKnown = record != null || occurrence != null,
                    inlineEntry = false,
                    asNeededEntry = asNeeded,
                    scheduledOccurrenceId = occurrence?.id ?: existing?.scheduledOccurrenceId,
                ),
                fieldErrors = emptySet(),
            )
        }
    }

    private fun canonicalPendingTodayMedication(
        occurrenceId: String,
        medicationPlanId: String,
        overview: com.bitavida.app.domain.model.MedicalCareOverview = state.value.overview,
    ): com.bitavida.app.domain.usecase.UpcomingMedicationAdministration? {
        val plan = overview.medicationPlans.singleOrNull {
            it.id == medicationPlanId && it.personUnderCareId == profile.personUnderCare.id
        } ?: return null
        // The same projection used by Mi día includes yesterday's windows crossing midnight.
        val occurrences = medicationOccurrencesForDay(plan, todayMedicationNow().toLocalDate())
        val canonical = occurrences.singleOrNull { it.id == occurrenceId } ?: return null
        val indicationStatus = overview.indications.singleOrNull {
            it.id == plan.medicalIndicationId && it.personUnderCareId == profile.personUnderCare.id
        }?.currentRevision?.status
        if (plan.displayState(canonical.startsAt, indicationStatus) != MedicationPlanDisplayState.CURRENT) return null
        val candidates = intakesForOccurrence(overview.intakesFor(plan.id), canonical, occurrences, plan)
        return canonical.takeIf { candidates.size <= 1 && candidates.none { it.hasDeclaredResponse() } }
    }

    private fun isPendingTodayMedication(
        occurrence: com.bitavida.app.domain.usecase.UpcomingMedicationAdministration,
    ): Boolean {
        val current = state.value
        if (current.isLoading || current.isSaving || current.globalError != null ||
            current.medicationIntakeDraft != null || current.sensitiveConsentDialogVisible
        ) return false
        // Reject stale names, quantities or schedules instead of silently recording unseen changes.
        return canonicalPendingTodayMedication(occurrence.id, occurrence.medicationPlanId) == occurrence
    }

    private fun openTodayMedicationIntake(action: MedicalPlanAction.OpenTodayMedicationIntake) {
        if (action.outcome !in setOf(MedicationIntakeOutcome.COMPLETED, MedicationIntakeOutcome.OMITTED) ||
            !isPendingTodayMedication(action.occurrence)
        ) return
        val now = todayMedicationNow().withNano(0)
        onAction(MedicalPlanAction.OpenMedication(action.occurrence.medicationPlanId))
        openMedicationIntakeEditor(recordId = null, occurrence = action.occurrence)
        todayUndoableMedicationRevisions.clear()
        update {
            copy(
                medicationIntakeDraft = medicationIntakeDraft?.copy(
                    outcome = action.outcome,
                    outcomeExplicitlySelected = true,
                    actualDate = now.toLocalDate().toString(),
                    actualTime = now.toLocalTime().toString(),
                    fromToday = true,
                    expandNoteOnOpen = action.expandNote,
                    sourceMedicationPlanRevisionId = action.occurrence.medicationPlanRevisionId,
                    expectedRevisionNumber = medicationIntakeDraft?.recordId?.let { id ->
                        overview.medicationIntakes.singleOrNull { it.id == id }?.currentRevision?.revisionNumber
                    },
                ),
                todayMedicationFeedback = null,
            )
        }
    }

    private fun undoTodayMedicationIntake(action: MedicalPlanAction.UndoTodayMedicationIntake) {
        val current = state.value
        if (current.isLoading || current.isSaving || current.medicationIntakeDraft != null ||
            current.sensitiveConsentDialogVisible
        ) return
        val record = current.overview.medicationIntakes.singleOrNull {
            it.id == action.recordId && it.currentRevision.scheduledOccurrenceId == action.occurrenceId
        } ?: return
        if (todayUndoableMedicationRevisions[record.id] != action.expectedRevisionNumber) return
        if (record.currentRevision.revisionNumber != action.expectedRevisionNumber) {
            todayUndoableMedicationRevisions.remove(record.id)
            update { copy(todayMedicationFeedback = TodayMedicationFeedback(
                UUID.randomUUID().toString(), action.occurrenceId,
                overview.medicationPlans.singleOrNull { it.id == record.medicationPlanId }
                    ?.currentRevision?.medicationName.orEmpty(),
                record.currentRevision.outcome, TodayMedicationFeedbackStatus.FAILED,
                message = "La toma ya cambió. Su información se conserva.",
            )) }
            return
        }
        val revision = record.currentRevision
        val previous = record.revisions.getOrNull(record.revisions.lastIndex - 1)
        update {
            copy(
                selectedMedicationPlanId = record.medicationPlanId,
                globalError = null,
                medicationIntakeDraft = MedicationIntakeDraft(
                    recordId = record.id,
                    outcome = previous?.outcome ?: MedicationIntakeOutcome.UNCONFIRMED,
                    scheduledDate = revision.scheduledAt.toLocalDate().toString(),
                    scheduledTime = revision.scheduledAt.toLocalTime().toString(),
                    actualDate = (previous?.actualAt ?: todayMedicationNow()).toLocalDate().toString(),
                    actualTime = previous?.actualAt?.toLocalTime()?.toString().orEmpty(),
                    actualAmountValue = previous?.actualAmount?.value.orEmpty(),
                    actualAmountUnit = previous?.actualAmount?.unit.orEmpty(),
                    observation = previous?.observation.orEmpty(),
                    reason = "Deshacer registro rápido de Mi día confirmado por la persona cuidadora",
                    undoEntry = true,
                    inlineEntry = true,
                    fromToday = true,
                    scheduledOccurrenceKnown = true,
                    scheduledOccurrenceId = action.occurrenceId,
                    expectedRevisionNumber = action.expectedRevisionNumber,
                ),
            )
        }
        saveMedicationIntake()
    }

    private fun todayFeedback(
        draft: MedicationIntakeDraft,
        status: TodayMedicationFeedbackStatus,
        message: String? = null,
    ): TodayMedicationFeedback {
        val current = state.value
        val previous = current.todayMedicationFeedback?.takeIf {
            it.occurrenceId == draft.scheduledOccurrenceId &&
                it.status in setOf(TodayMedicationFeedbackStatus.SAVING, TodayMedicationFeedbackStatus.AWAITING_CONSENT)
        }
        return TodayMedicationFeedback(
            requestId = previous?.requestId ?: UUID.randomUUID().toString(),
            occurrenceId = draft.scheduledOccurrenceId.orEmpty(),
            medicationName = current.selectedMedicationPlan?.currentRevision?.medicationName.orEmpty(),
            outcome = draft.outcome,
            status = status,
            recordId = draft.recordId,
            revisionNumber = draft.expectedRevisionNumber,
            actualAt = if (draft.outcome in setOf(MedicationIntakeOutcome.COMPLETED, MedicationIntakeOutcome.PARTIAL)) {
                runCatching { LocalDateTime.of(LocalDate.parse(draft.actualDate), LocalTime.parse(draft.actualTime)) }.getOrNull()
            } else null,
            message = message,
        )
    }

    private fun failTodayMedication(message: String, discardDraft: Boolean = false): Boolean {
        val current = state.value
        val draft = current.medicationIntakeDraft?.takeIf { it.fromToday } ?: return false
        if (!discardDraft && current.todayMedicationFeedback?.status !in setOf(
                TodayMedicationFeedbackStatus.SAVING, TodayMedicationFeedbackStatus.AWAITING_CONSENT,
            )
        ) return false
        pendingTodayMedicationWrite = null
        val feedback = todayFeedback(draft, TodayMedicationFeedbackStatus.FAILED, message)
        update {
            copy(
                isSaving = false,
                globalError = null,
                sensitiveConsentDialogVisible = false,
                sensitiveConsentAccepted = false,
                medicationIntakeDraft = if (discardDraft || draft.inlineEntry) null else medicationIntakeDraft,
                todayMedicationFeedback = feedback,
            )
        }
        return true
    }

    private fun quickRegisterMedicationIntake(
        action: MedicalPlanAction.QuickRegisterMedicationIntake,
    ) {
        val current = mutableState.value
        if (current.isSaving ||
            current.selectedMedicationPlanId != action.occurrence.medicationPlanId
        ) return
        val now = if (action.fromToday) todayMedicationNow().withNano(0)
            else LocalDateTime.now().withSecond(0).withNano(0)
        if (action.fromToday && !isPendingTodayMedication(action.occurrence)) return
        if (action.occurrence.startsAt.toLocalDate().isAfter(now.toLocalDate())) return
        val plan = current.selectedMedicationPlan ?: return
        val candidates = intakesForOccurrence(
            current.overview.intakesFor(plan.id), action.occurrence,
            medicationOccurrencesForDay(plan, action.occurrence.startsAt.toLocalDate()), plan,
        )
        if (candidates.size > 1) {
            update { copy(globalError = MedicalPlanGlobalError.AMBIGUOUS_MEDICATION_OCCASION) }
            return
        }
        val existing = candidates.singleOrNull()
        // Repeated callbacks after a completed save must not add another fact or revision.
        if (existing?.currentRevision?.outcome == action.outcome) return
        val recordsActualData = action.outcome == MedicationIntakeOutcome.COMPLETED
        update {
            copy(
                medicationIntakeDraft = MedicationIntakeDraft(
                    recordId = existing?.id,
                    outcome = action.outcome,
                    scheduledDate = action.occurrence.startsAt.toLocalDate().toString(),
                    scheduledTime = action.occurrence.startsAt.toLocalTime().toString(),
                    actualDate = now.toLocalDate().toString(),
                    actualTime = now.toLocalTime().toString(),
                    actualAmountValue = if (recordsActualData) action.occurrence.amount.value else "",
                    actualAmountUnit = if (recordsActualData) action.occurrence.amount.unit else "",
                    observation = existing?.currentRevision?.observation.orEmpty(),
                    reason = if (existing != null) {
                        "Corrección de respuesta: " +
                            if (recordsActualData) "Se tomó" else "No se tomó"
                    } else "",
                    quickEntry = true,
                    scheduledOccurrenceKnown = true,
                    scheduledOccurrenceId = action.occurrence.id,
                    inlineEntry = true,
                    fromToday = action.fromToday,
                    sourceMedicationPlanRevisionId = action.occurrence.medicationPlanRevisionId.takeIf { action.fromToday },
                    expectedRevisionNumber = existing?.currentRevision?.revisionNumber.takeIf { action.fromToday },
                ),
                fieldErrors = emptySet(),
            )
        }
        saveMedicationIntake()
    }

    private fun undoQuickMedicationIntake() {
        val current = mutableState.value
        if (current.isSaving) return
        val record = current.overview.medicationIntakes
            .singleOrNull { it.id == current.lastQuickMedicationIntakeId } ?: return
        val revision = record.currentRevision
        val previous = record.revisions.getOrNull(record.revisions.lastIndex - 1)
        update {
            copy(
                lastQuickMedicationIntakeId = null,
                medicationIntakeDraft = MedicationIntakeDraft(
                    recordId = record.id,
                    outcome = previous?.outcome ?: MedicationIntakeOutcome.UNCONFIRMED,
                    scheduledDate = revision.scheduledAt.toLocalDate().toString(),
                    scheduledTime = revision.scheduledAt.toLocalTime().toString(),
                    actualDate = (previous?.actualAt ?: LocalDateTime.now()).toLocalDate().toString(),
                    actualTime = previous?.actualAt?.toLocalTime()?.toString().orEmpty(),
                    actualAmountValue = previous?.actualAmount?.value.orEmpty(),
                    actualAmountUnit = previous?.actualAmount?.unit.orEmpty(),
                    observation = previous?.observation.orEmpty(),
                    reason = "Deshacer la última respuesta de esta toma",
                    undoEntry = true,
                    scheduledOccurrenceId = revision.scheduledOccurrenceId,
                    scheduledOccurrenceKnown = true,
                    inlineEntry = true,
                ),
            )
        }
        saveMedicationIntake()
    }

    private fun saveMedicationIntake(todayConsentChecked: Boolean = false) {
        val current = mutableState.value
        val enteredDraft = current.medicationIntakeDraft ?: return
        if (current.isSaving) return
        if (enteredDraft.fromToday && !todayConsentChecked) {
            todayUndoableMedicationRevisions.clear()
            val feedback = todayFeedback(enteredDraft, TodayMedicationFeedbackStatus.SAVING)
            update { copy(isSaving = true, globalError = null, todayMedicationFeedback = feedback) }
            taskScope.launch {
                repositoryCall {
                    if (!repository.hasActiveSensitiveDataConsent(
                            profile.id, profile.caregiver.id, profile.personUnderCare.id,
                            SENSITIVE_DATA_CONSENT_VERSION,
                        )
                    ) {
                        pendingTodayMedicationWrite = {
                            if (state.value.todayMedicationFeedback?.requestId == feedback.requestId &&
                                state.value.medicationIntakeDraft?.scheduledOccurrenceId == enteredDraft.scheduledOccurrenceId
                            ) saveMedicationIntake(todayConsentChecked = true)
                        }
                        update {
                            copy(
                                isSaving = false,
                                sensitiveConsentDialogVisible = true,
                                sensitiveConsentAccepted = false,
                                todayMedicationFeedback = feedback.copy(status = TodayMedicationFeedbackStatus.AWAITING_CONSENT),
                            )
                        }
                    } else {
                        update { copy(isSaving = false) }
                        saveMedicationIntake(todayConsentChecked = true)
                    }
                }
            }
            return
        }
        // Only an explicitly selected record can be edited. A second declaration
        // for an occasion is not necessarily a correction of an earlier declaration.
        val draft = enteredDraft
        val planId = current.selectedMedicationPlanId ?: return
        val scheduledDate = draft.scheduledDate.toLocalDateOrNull()
        val scheduledTime = draft.scheduledTime.toLocalTimeOrNull()
        val actualDate = draft.actualDate.toLocalDateOrNull()
        val actualTime = draft.actualTime.toLocalTimeOrNull()
        val localErrors = buildSet {
            if (!draft.outcomeExplicitlySelected) {
                add(MedicalCareField.INTAKE_OUTCOME)
            }
            if (scheduledDate == null || scheduledTime == null) {
                add(MedicalCareField.MEDICATION_DATES)
            }
            if (
                draft.outcome in setOf(
                    MedicationIntakeOutcome.COMPLETED,
                    MedicationIntakeOutcome.PARTIAL,
                ) &&
                (actualDate == null || actualTime == null)
            ) {
                add(MedicalCareField.INTAKE_ACTUAL_TIME)
            }
            if (
                draft.outcome in setOf(
                    MedicationIntakeOutcome.COMPLETED,
                    MedicationIntakeOutcome.PARTIAL,
                ) &&
                draft.actualAmountValue.isNotBlank() &&
                (!draft.actualAmountValue.isPositiveMedicationAmountText() ||
                    draft.actualAmountUnit.isBlank())
            ) {
                add(MedicalCareField.INTAKE_AMOUNT)
            }
            if (!CaptureLimit.INTAKE_AMOUNT.acceptsStoredOrNew(draft.actualAmountValue) ||
                !CaptureLimit.UNIT.acceptsStoredOrNew(draft.actualAmountUnit)
            ) {
                add(MedicalCareField.INTAKE_AMOUNT)
            }
            if ((draft.asNeededEntry && !hasMedicationIntakeReason(draft.observation)) ||
                !CaptureLimit.OBSERVATION.acceptsStoredOrNew(draft.observation)
            ) {
                add(MedicalCareField.INTAKE_OBSERVATION)
            }
            if ((draft.recordId != null && draft.reason.isBlank()) ||
                !CaptureLimit.REASON.acceptsStoredOrNew(draft.reason)
            ) {
                add(MedicalCareField.CHANGE_REASON)
            }
        }
        if (localErrors.isNotEmpty()) {
            update { copy(fieldErrors = localErrors) }
            if (draft.fromToday) failTodayMedication("No se pudo guardar la toma. Revisa la información e inténtalo otra vez.")
            return
        }
        val recordsActualData =
            draft.outcome == MedicationIntakeOutcome.COMPLETED ||
                draft.outcome == MedicationIntakeOutcome.PARTIAL
        val amount = draft.actualAmountValue.trim()
            .takeIf { recordsActualData && it.isNotEmpty() }
            ?.let {
                MedicationAmount(
                    value = it,
                    unit = draft.actualAmountUnit.trim(),
                )
            }
        val actualAt = if (
            recordsActualData &&
            actualDate != null &&
            actualTime != null
        ) {
            LocalDateTime.of(actualDate, actualTime)
        } else {
            null
        }
        update { copy(isSaving = true, fieldErrors = emptySet()) }
        taskScope.launch {
            repositoryCall {
                if (draft.fromToday && !draft.undoEntry) {
                    val latest = getOverview(profile.personUnderCare.id)
                    val committedRetry = draft.recordId == null && latest.medicationIntakes.any {
                        it.id == com.bitavida.app.domain.usecase.MEDICATION_INTAKE_OPERATION_PREFIX + draft.operationId
                    }
                    val canonical = canonicalPendingTodayMedication(
                        draft.scheduledOccurrenceId.orEmpty(), planId, latest,
                    )
                    if (!committedRetry && (canonical == null ||
                        canonical.startsAt != LocalDateTime.of(requireNotNull(scheduledDate), requireNotNull(scheduledTime)) ||
                        canonical.medicationPlanRevisionId != draft.sourceMedicationPlanRevisionId)
                    ) {
                        update { copy(overview = latest) }
                        failTodayMedication("La programación ya cambió o esta toma ya se registró. Revisa Mi día antes de intentarlo otra vez.")
                        return@repositoryCall
                    }
                }
                val result = if (draft.recordId == null) {
                    registerMedicationIntake(
                        RegisterMedicationIntakeCommand(
                            medicationPlanId = planId,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            outcome = draft.outcome,
                            scheduledAt = LocalDateTime.of(
                                requireNotNull(scheduledDate),
                                requireNotNull(scheduledTime),
                            ),
                            actualAt = actualAt,
                            actualAmount = amount,
                            observation = draft.observation,
                            scheduledOccurrenceId = draft.scheduledOccurrenceId,
                            operationId = draft.operationId,
                            careProfileId = profile.id,
                            sensitiveConsentVersion =
                                SENSITIVE_DATA_CONSENT_VERSION,
                        ),
                    )
                } else {
                    reviseMedicationIntake(
                        ReviseMedicationIntakeCommand(
                            intakeRecordId = draft.recordId,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            outcome = draft.outcome,
                            scheduledAt = LocalDateTime.of(
                                requireNotNull(scheduledDate),
                                requireNotNull(scheduledTime),
                            ),
                            actualAt = actualAt,
                            actualAmount = amount,
                            observation = draft.observation,
                            scheduledOccurrenceId = draft.scheduledOccurrenceId,
                            reason = draft.reason,
                            careProfileId = profile.id,
                            sensitiveConsentVersion =
                                SENSITIVE_DATA_CONSENT_VERSION,
                        ),
                        expectedRevisionNumber = draft.expectedRevisionNumber,
                    )
                }
                when (result) {
                    is MedicalCareMutationResult.Invalid ->
                        showValidation(result.errors)
                    MedicalCareMutationResult.NotFound -> showNotFound()
                    is MedicalCareMutationResult.Stored -> {
                        val storedRecordId = (result.value as? MedicationIntakeRecord)?.id ?: draft.recordId
                        val overview = if (draft.fromToday) {
                            val previous = state.value.overview
                            val committed = when (val value = result.value) {
                                is MedicationIntakeRecord -> value
                                is MedicationIntakeRevision -> previous.medicationIntakes.singleOrNull {
                                    it.id == value.intakeRecordId
                                }?.let {
                                    if (it.currentRevision.revisionNumber >= value.revisionNumber) it
                                    else it.copy(revisions = it.revisions + value)
                                }
                                else -> null
                            }
                            if (committed == null) getOverview(profile.personUnderCare.id)
                            else previous.copy(
                                medicationIntakes = previous.medicationIntakes.filterNot { it.id == committed.id } + committed,
                            )
                        } else getOverview(profile.personUnderCare.id)
                        val storedRecord = overview.medicationIntakes.singleOrNull { it.id == storedRecordId }
                        val committedRevision = when (val value = result.value) {
                            is MedicationIntakeRecord -> value.currentRevision
                            is MedicationIntakeRevision -> value
                            else -> null
                        }
                        if (draft.fromToday && storedRecord != null && committedRevision != null) {
                            if (draft.undoEntry) todayUndoableMedicationRevisions.remove(storedRecord.id)
                            else if (storedRecord.currentRevision.id == committedRevision.id) {
                                todayUndoableMedicationRevisions[storedRecord.id] = committedRevision.revisionNumber
                            }
                        }
                        val quickRecordId = if (draft.quickEntry) {
                            storedRecordId
                        } else {
                            null
                        }
                        update {
                            copy(
                                overview = overview,
                                medicationIntakeDraft = null,
                                lastQuickMedicationIntakeId = quickRecordId
                                    ?: lastQuickMedicationIntakeId,
                                todayMedicationFeedback = if (draft.fromToday && storedRecord != null) {
                                    todayFeedback(draft, if (draft.undoEntry) TodayMedicationFeedbackStatus.UNDONE
                                        else TodayMedicationFeedbackStatus.STORED).copy(
                                        recordId = storedRecord.id,
                                        revisionNumber = committedRevision?.revisionNumber,
                                        outcome = committedRevision?.outcome ?: storedRecord.currentRevision.outcome,
                                        actualAt = committedRevision?.actualAt,
                                    )
                                } else todayMedicationFeedback,
                                selectedMedicationTodayKey = if (draft.asNeededEntry && storedRecordId != null) {
                                    "record:$storedRecordId"
                                } else selectedMedicationTodayKey,
                                medicationTodayDate = if (draft.asNeededEntry) {
                                    actualDate ?: medicationTodayDate
                                } else medicationTodayDate,
                                isSaving = false,
                                fieldErrors = emptySet(),
                                effect =
                                    MedicalPlanEffect.MedicationIntakeStored,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun confirmMedicationLifecycle() {
        val current = mutableState.value
        val planId = current.selectedMedicationPlanId ?: return
        val draft = current.medicationLifecycleDraft ?: return
        if (current.isSaving) return
        val now = LocalDateTime.now()
        val selectedDate = draft.effectiveOn.toLocalDateOrNull()
        val selectedTime = runCatching {
            LocalTime.parse(draft.effectiveTime)
        }.getOrNull()
        val effectiveAt = when {
            draft.targetStatus != MedicationTreatmentStatus.ACTIVE -> now
            draft.reactivationTiming == MedicationReactivationTiming.NOW -> now
            selectedDate != null && selectedTime != null ->
                LocalDateTime.of(selectedDate, selectedTime)
            else -> null
        }
        val errors = buildSet {
            if (
                draft.targetStatus != MedicationTreatmentStatus.ACTIVE &&
                draft.reason.isBlank()
            ) {
                add(MedicalCareField.CHANGE_REASON)
            }
            if (
                effectiveAt == null ||
                (
                    draft.targetStatus == MedicationTreatmentStatus.ACTIVE &&
                        draft.reactivationTiming ==
                        MedicationReactivationTiming.SCHEDULED &&
                        !effectiveAt.isAfter(now)
                )
            ) {
                add(MedicalCareField.MEDICATION_DATES)
            }
        }
        if (errors.isNotEmpty()) {
            update { copy(fieldErrors = errors) }
            return
        }
        val removesFromOperations =
            draft.targetStatus == MedicationTreatmentStatus.REMOVED
        update { copy(isSaving = true, fieldErrors = emptySet()) }
        taskScope.launch {
            repositoryCall {
                when (
                    val result = changeMedicationLifecycle(
                        ChangeMedicationLifecycleCommand(
                            medicationPlanId = planId,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            targetStatus = draft.targetStatus,
                            effectiveAt = requireNotNull(effectiveAt),
                            reason = draft.reason,
                            careProfileId = profile.id,
                            sensitiveConsentVersion =
                                SENSITIVE_DATA_CONSENT_VERSION,
                        ),
                    )
                ) {
                    is MedicalCareMutationResult.Invalid ->
                        showValidation(result.errors)
                    MedicalCareMutationResult.NotFound -> showNotFound()
                    is MedicalCareMutationResult.Stored -> {
                        val overview = getOverview(profile.personUnderCare.id)
                        update {
                            copy(
                                overview = overview,
                                screen = if (removesFromOperations) {
                                    MedicalPlanScreen.SECTION
                                } else {
                                    screen
                                },
                                selectedSection = if (removesFromOperations) {
                                    PlanSection.MEDICATIONS
                                } else {
                                    selectedSection
                                },
                                selectedMedicationPlanId = if (removesFromOperations) {
                                    null
                                } else {
                                    selectedMedicationPlanId
                                },
                                medicationLifecycleDraft = null,
                                isSaving = false,
                                fieldErrors = emptySet(),
                                effect =
                                    MedicalPlanEffect.MedicationLifecycleStored,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun cancelScheduledMedicationReactivation() {
        val current = mutableState.value
        val planId = current.selectedMedicationPlanId ?: return
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val pending = current.selectedMedicationPlan?.pendingReactivation(
            now.atZone(zoneId).toInstant().toEpochMilli(),
            zoneId,
        ) ?: return
        if (current.isSaving || pending.effectiveAtEpochMillis <= 0L) return
        update { copy(isSaving = true, fieldErrors = emptySet()) }
        taskScope.launch {
            repositoryCall {
                when (
                    val result = changeMedicationLifecycle(
                        ChangeMedicationLifecycleCommand(
                            medicationPlanId = planId,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            targetStatus = MedicationTreatmentStatus.SUSPENDED,
                            effectiveAt = now,
                            reason = "",
                            careProfileId = profile.id,
                            sensitiveConsentVersion =
                                SENSITIVE_DATA_CONSENT_VERSION,
                        ),
                    )
                ) {
                    is MedicalCareMutationResult.Invalid ->
                        showValidation(result.errors)
                    MedicalCareMutationResult.NotFound -> showNotFound()
                    is MedicalCareMutationResult.Stored -> {
                        val overview = getOverview(profile.personUnderCare.id)
                        update {
                            copy(
                                overview = overview,
                                medicationLifecycleDraft = null,
                                isSaving = false,
                                fieldErrors = emptySet(),
                                effect =
                                    MedicalPlanEffect.MedicationLifecycleStored,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveIndication() {
        val current = mutableState.value
        if (current.isSaving) return
        val parsed = parseIndicationDraft(current.indicationDraft)
        if (parsed.errors.isNotEmpty()) {
            update { copy(fieldErrors = parsed.errors.map { it.field }.toSet()) }
            return
        }
        update { copy(isSaving = true, fieldErrors = emptySet(), globalError = null) }
        taskScope.launch {
            repositoryCall {
                val result = if (current.editingExisting) {
                    val indicationId = current.selectedIndicationId
                        ?: return@repositoryCall showNotFound()
                    reviseIndication(
                        ReviseMedicalIndicationCommand(
                            indicationId = indicationId,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            originalText = current.indicationDraft.originalText,
                            indicatedOn = parsed.indicatedOn!!,
                            declaredProfessionalOrSource =
                                current.indicationDraft.declaredProfessionalOrSource,
                            validFrom = parsed.validFrom,
                            validUntil = parsed.validUntil,
                            changeReason = current.indicationDraft.changeReason,
                            contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
                            fictitiousDataAcknowledged =
                                current.indicationDraft.fictitiousDataAcknowledged,
                        ),
                    )
                } else {
                    registerIndication(
                        RegisterMedicalIndicationCommand(
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            originalText = current.indicationDraft.originalText,
                            indicatedOn = parsed.indicatedOn!!,
                            declaredProfessionalOrSource =
                                current.indicationDraft.declaredProfessionalOrSource,
                            validFrom = parsed.validFrom,
                            validUntil = parsed.validUntil,
                            contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
                            fictitiousDataAcknowledged =
                                current.indicationDraft.fictitiousDataAcknowledged,
                        ),
                    )
                }
                handleIndicationResult(result)
            }
        }
    }

    private suspend fun handleIndicationResult(
        result: MedicalCareMutationResult<*>,
    ) {
        when (result) {
            is MedicalCareMutationResult.Invalid -> showValidation(result.errors)
            MedicalCareMutationResult.NotFound -> showNotFound()
            is MedicalCareMutationResult.Stored -> {
                val indicationId = when (val value = result.value) {
                    is com.bitavida.app.domain.model.MedicalIndication -> value.id
                    is com.bitavida.app.domain.model.MedicalIndicationRevision ->
                        value.indicationId
                    else -> error("Unexpected indication result.")
                }
                val overview = getOverview(profile.personUnderCare.id)
                update {
                    val returnsToReactivation = resumeReactivationAfterEdit &&
                        selectedMedicationPlanId != null
                    copy(
                        overview = overview,
                        selectedIndicationId = indicationId,
                        screen = if (returnsToReactivation) {
                            MedicalPlanScreen.MEDICATION_DETAIL
                        } else {
                            MedicalPlanScreen.INDICATION_DETAIL
                        },
                        medicationLifecycleDraft = if (returnsToReactivation) {
                            medicationLifecycleDraftFor(
                                status = MedicationTreatmentStatus.ACTIVE,
                                plan = overview.medicationPlans.singleOrNull {
                                    it.id == selectedMedicationPlanId
                                },
                                now = LocalDateTime.now(),
                            )
                        } else {
                            null
                        },
                        resumeReactivationAfterEdit = false,
                        isSaving = false,
                        fieldErrors = emptySet(),
                        effect = MedicalPlanEffect.IndicationStored,
                    )
                }
            }
        }
    }

    private fun saveMedication() {
        val current = mutableState.value
        if (current.isSaving) return
        val existingPlan = current.selectedMedicationPlan
            .takeIf { current.editingExisting }
        val existingRevision = existingPlan?.currentRevision
        val existingConfiguration = existingRevision?.configuration
        val draft = current.medicationDraft
        val typedErrors = buildSet {
            if (draft.medicationName.isBlank() ||
                !CaptureLimit.MEDICATION_NAME.acceptsStoredOrNew(
                    draft.medicationName, existingRevision?.medicationName,
                )
            ) {
                add(MedicalCareField.MEDICATION_NAME)
            }
            if (current.editingExisting) {
                if (draft.form == null) {
                    add(MedicalCareField.UNIT_OR_PRESENTATION)
                }
                if (
                    !MedicationValueRules.isValidReplacement(
                        draft.baseAmountValue,
                        existingConfiguration?.baseAmount?.value,
                    ) ||
                    (draft.concentrationValue.isNotEmpty() &&
                        !MedicationValueRules.isValidReplacement(
                            draft.concentrationValue,
                            existingConfiguration?.concentrationValue,
                        ))
                ) {
                    add(MedicalCareField.DOSE_AS_INDICATED)
                }
                if (
                    draft.concentrationValue.isNotEmpty() &&
                    draft.concentrationUnit.isBlank()
                ) {
                    add(MedicalCareField.UNIT_OR_PRESENTATION)
                }
                if (draft.baseAmountUnit.isBlank() ||
                    !CaptureLimit.UNIT.acceptsStoredOrNew(draft.baseAmountUnit) ||
                    !CaptureLimit.UNIT.acceptsStoredOrNew(draft.concentrationUnit)
                ) {
                    add(MedicalCareField.UNIT_OR_PRESENTATION)
                }
                if (draft.changeReason.isBlank()) {
                    add(MedicalCareField.CHANGE_REASON)
                }
            }
        }
        val parsed = if (
            current.editingExisting && existingConfiguration != null
        ) {
            ParsedMedicationDraft(
                startsOn = existingConfiguration.startsOn,
                endsOn = existingConfiguration.endsOn,
                schedules = existingRevision.scheduledDoses.map { it.time },
                errors = emptySet(),
            )
        } else {
            parseMedicationDraft(draft)
        }
        val errors = typedErrors + parsed.errors.map { it.field }
        if (errors.isNotEmpty()) {
            update { copy(fieldErrors = errors) }
            return
        }
        val updatedConfiguration = existingConfiguration?.copy(
            form = requireNotNull(draft.form).let {
                enumValueOf<MedicationProductForm>(it.name)
            },
            concentrationValue =
                draft.concentrationValue.takeIf(String::isNotEmpty),
            concentrationUnit =
                draft.concentrationUnit.trim().takeIf {
                    draft.concentrationValue.isNotEmpty() && it.isNotEmpty()
                },
            baseAmount = MedicationAmount(
                value = draft.baseAmountValue,
                unit = draft.baseAmountUnit.trim(),
            ),
        )
        update {
            copy(
                isSaving = true,
                fieldErrors = emptySet(),
                globalError = null,
            )
        }
        taskScope.launch {
            repositoryCall {
                val result = if (current.editingExisting) {
                    val plan = existingPlan
                        ?: return@repositoryCall showNotFound()
                    val configuration = updatedConfiguration
                        ?: return@repositoryCall showNotFound()
                    reviseMedication(
                        ReviseMedicationPlanCommand(
                            medicationPlanId = plan.id,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            medicationName = draft.medicationName,
                            doseAsIndicated =
                                "${configuration.baseAmount.value} " +
                                    configuration.baseAmount.unit,
                            unitOrPresentation =
                                configuration.form.name,
                            frequencyAsIndicated =
                                existingRevision.frequencyAsIndicated,
                            startsOn = configuration.startsOn,
                            endsOn = configuration.endsOn,
                            originalAdditionalInstructions =
                                draft.originalAdditionalInstructions,
                            explicitSchedules = parsed.schedules,
                            changeReason = draft.changeReason,
                            contentMode = plan.contentMode,
                            fictitiousDataAcknowledged = false,
                            configuration = configuration,
                            careProfileId = profile.id,
                            sensitiveConsentVersion =
                                SENSITIVE_DATA_CONSENT_VERSION,
                            changeKind =
                                com.bitavida.app.domain.model
                                    .MedicationPlanChangeKind
                                    .BASE_DETAILS_CORRECTED,
                        ),
                    )
                } else {
                    val indicationId = current.selectedIndicationId
                        ?: return@repositoryCall showNotFound()
                    registerMedication(
                        RegisterMedicationPlanCommand(
                            medicalIndicationId = indicationId,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            medicationName = draft.medicationName,
                            doseAsIndicated = draft.doseAsIndicated,
                            unitOrPresentation =
                                draft.unitOrPresentation,
                            frequencyAsIndicated =
                                draft.frequencyAsIndicated,
                            startsOn = requireNotNull(parsed.startsOn),
                            endsOn = parsed.endsOn,
                            originalAdditionalInstructions =
                                draft.originalAdditionalInstructions,
                            explicitSchedules = parsed.schedules,
                            contentMode =
                                ClinicalContentMode
                                    .FICTITIOUS_DEVELOPMENT,
                            fictitiousDataAcknowledged =
                                draft.fictitiousDataAcknowledged,
                        ),
                    )
                }
                handleMedicationResult(result)
            }
        }
    }

    private suspend fun handleMedicationResult(
        result: MedicalCareMutationResult<*>,
    ) {
        when (result) {
            is MedicalCareMutationResult.Invalid -> showValidation(result.errors)
            MedicalCareMutationResult.NotFound -> showNotFound()
            is MedicalCareMutationResult.Stored -> {
                val planId = when (val value = result.value) {
                    is com.bitavida.app.domain.model.MedicationPlan -> value.id
                    is com.bitavida.app.domain.model.MedicationPlanRevision ->
                        value.medicationPlanId
                    else -> error("Unexpected medication result.")
                }
                val overview = getOverview(profile.personUnderCare.id)
                update {
                    copy(
                        overview = overview,
                        selectedMedicationPlanId = planId,
                        screen = MedicalPlanScreen.MEDICATION_DETAIL,
                        isSaving = false,
                        fieldErrors = emptySet(),
                        effect = MedicalPlanEffect.MedicationStored,
                    )
                }
            }
        }
    }

    private fun confirmStatusChange() {
        val current = mutableState.value
        val draft = current.statusChangeDraft ?: return
        val indicationId = current.selectedIndicationId ?: return
        if (current.isSaving) return
        update { copy(isSaving = true, fieldErrors = emptySet(), globalError = null) }
        taskScope.launch {
            repositoryCall {
                when (
                    val result = changeStatus(
                        ChangeMedicalIndicationStatusCommand(
                            indicationId = indicationId,
                            personUnderCareId = profile.personUnderCare.id,
                            authorActorId = profile.caregiver.id,
                            targetStatus = draft.targetStatus,
                            changeReason = draft.reason,
                            contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
                            fictitiousDataAcknowledged =
                                draft.fictitiousDataAcknowledged,
                        ),
                    )
                ) {
                    is MedicalCareMutationResult.Invalid -> showValidation(result.errors)
                    MedicalCareMutationResult.NotFound -> showNotFound()
                    is MedicalCareMutationResult.Stored -> {
                        val overview = getOverview(profile.personUnderCare.id)
                        update {
                            copy(
                                overview = overview,
                                statusChangeDraft = null,
                                isSaving = false,
                                fieldErrors = emptySet(),
                                effect = MedicalPlanEffect.StatusStored,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun goBack() {
        update {
            when (screen) {
                MedicalPlanScreen.OVERVIEW -> this
                MedicalPlanScreen.SECTION -> copy(
                    screen = MedicalPlanScreen.OVERVIEW,
                    selectedSection = null,
                    selectedIndicationId = null,
                    selectedMedicationPlanId = null,
                )
                MedicalPlanScreen.INDICATION_DETAIL -> copy(
                    screen = MedicalPlanScreen.OVERVIEW,
                    selectedIndicationId = null,
                    selectedMedicationPlanId = null,
                )
                MedicalPlanScreen.INDICATION_HISTORY -> copy(
                    screen = MedicalPlanScreen.INDICATION_DETAIL,
                )
                MedicalPlanScreen.INDICATION_EDITOR -> {
                    val returnsToReactivation =
                        resumeReactivationAfterEdit &&
                            selectedMedicationPlanId != null
                    copy(
                        screen = when {
                            returnsToReactivation ->
                                MedicalPlanScreen.MEDICATION_DETAIL
                            editingExisting ->
                                MedicalPlanScreen.INDICATION_DETAIL
                            else -> MedicalPlanScreen.OVERVIEW
                        },
                        medicationLifecycleDraft = if (returnsToReactivation) {
                            medicationLifecycleDraftFor(
                                status = MedicationTreatmentStatus.ACTIVE,
                                plan = selectedMedicationPlan,
                                now = LocalDateTime.now(),
                            )
                        } else {
                            null
                        },
                        resumeReactivationAfterEdit = false,
                        fieldErrors = emptySet(),
                    )
                }
                MedicalPlanScreen.MEDICATION_HISTORY -> copy(
                    screen = MedicalPlanScreen.MEDICATION_DETAIL,
                )
                MedicalPlanScreen.MEDICATION_MANAGEMENT -> copy(
                    screen = MedicalPlanScreen.MEDICATION_DETAIL,
                    fieldErrors = emptySet(),
                )
                MedicalPlanScreen.MEDICATION_DETAIL -> if (isCurrentMedicationDetail(LocalDateTime.now())) {
                    copy(
                        screen = MedicalPlanScreen.SECTION,
                        selectedSection = PlanSection.MEDICATIONS,
                        selectedMedicationPlanId = null,
                        medicationIntakeDraft = null,
                    )
                } else copy(
                    screen = if (
                        selectedMedicationPlan?.medicalIndicationId != null
                    ) {
                        MedicalPlanScreen.INDICATION_DETAIL
                    } else {
                        MedicalPlanScreen.SECTION
                    },
                    selectedSection = if (
                        selectedMedicationPlan?.medicalIndicationId == null
                    ) {
                        PlanSection.MEDICATIONS
                    } else {
                        selectedSection
                    },
                    selectedMedicationPlanId = null,
                )

                MedicalPlanScreen.MEDICATION_EDITOR -> copy(
                    screen = if (editingExisting) {
                        MedicalPlanScreen.MEDICATION_DETAIL
                    } else {
                        MedicalPlanScreen.INDICATION_DETAIL
                    },
                    fieldErrors = emptySet(),
                )
                MedicalPlanScreen.MEDICATION_REGISTRATION -> {
                    if (editingExisting) {
                        if (medicationRegistrationStep != MedicationRegistrationStep.REVIEW) {
                            copy(
                                medicationRegistrationStep = MedicationRegistrationStep.REVIEW,
                                guidedMedicationDraft = editorEntryGuidedMedicationDraft
                                    ?: guidedMedicationDraft,
                                editorEntryGuidedMedicationDraft = null,
                                medicationRegistrationErrors = emptySet(),
                                medicationScheduleError = null,
                            )
                        } else if (hasMedicationChanges) {
                            copy(medicationExitConfirmationVisible = true)
                        } else {
                            copy(
                                screen = MedicalPlanScreen.MEDICATION_DETAIL,
                                editingExisting = false,
                                medicationEditPurpose = null,
                                originalGuidedMedicationDraft = null,
                                medicationLifecycleDraft = if (
                                    resumeReactivationAfterEdit
                                ) {
                                    medicationLifecycleDraftFor(
                                        status = MedicationTreatmentStatus.ACTIVE,
                                        plan = selectedMedicationPlan,
                                        now = LocalDateTime.now(),
                                    )
                                } else {
                                    null
                                },
                                resumeReactivationAfterEdit = false,
                                medicationRegistrationErrors = emptySet(),
                                medicationScheduleError = null,
                            )
                        }
                    } else if (medicationRegistrationStep == MedicationRegistrationStep.NAME) {
                        if (guidedMedicationDraft.hasCapturedData()) {
                            copy(medicationExitConfirmationVisible = true)
                        } else {
                            copy(
                                screen = MedicalPlanScreen.SECTION,
                                selectedSection = PlanSection.MEDICATIONS,
                                guidedMedicationDraft = GuidedMedicationDraft(),
                                medicationRegistrationErrors = emptySet(),
                                medicationScheduleError = null,
                                medicationExitConfirmationVisible = false,
                                        )
                        }
                    } else if (
                        medicationRegistrationStep ==
                        MedicationRegistrationStep.SCHEDULE
                    ) {
                        val previousSubstep = when (
                            medicationScheduleSubstep
                        ) {
                            MedicationScheduleSubstep.FREQUENCY -> null
                            MedicationScheduleSubstep.PARAMETERS ->
                                MedicationScheduleSubstep.FREQUENCY
                            MedicationScheduleSubstep.ADMINISTRATIONS ->
                                if (
                                    guidedMedicationDraft.frequency ==
                                    MedicationFrequency.EVERY_DAY
                                ) {
                                    MedicationScheduleSubstep.FREQUENCY
                                } else {
                                    MedicationScheduleSubstep.PARAMETERS
                                }
                            MedicationScheduleSubstep.VALIDITY ->
                                if (
                                    guidedMedicationDraft.frequency in
                                    setOf(
                                        MedicationFrequency.AS_NEEDED,
                                        MedicationFrequency.EVERY_N_HOURS,
                                    )
                                ) {
                                    MedicationScheduleSubstep.PARAMETERS
                                } else {
                                    MedicationScheduleSubstep.ADMINISTRATIONS
                                }
                        }
                        if (previousSubstep == null) {
                            copy(
                                medicationRegistrationStep =
                                    MedicationRegistrationStep.DETAILS,
                                medicationRegistrationErrors = emptySet(),
                                medicationScheduleError = null,
                            )
                        } else {
                            copy(
                                medicationScheduleSubstep = previousSubstep,
                                medicationRegistrationErrors = emptySet(),
                                medicationScheduleError = null,
                            )
                        }
                    } else {
                        copy(
                            medicationRegistrationStep =
                                medicationRegistrationStep.previous(),
                            medicationRegistrationErrors = emptySet(),
                            medicationScheduleError = null,
                        )
                    }
                }
            }
        }
    }

    private fun continueMedicationRegistration(
        expectedStep: MedicationRegistrationStep,
    ) {
        val current = mutableState.value
        if (current.medicationRegistrationStep != expectedStep) return
        if (current.editingExisting && expectedStep != MedicationRegistrationStep.REVIEW) {
            val errors = if (expectedStep == MedicationRegistrationStep.SCHEDULE) {
                validateMedicationScheduleSubstep(
                    current.medicationScheduleSubstep,
                    current.guidedMedicationDraft,
                    current.originalGuidedMedicationDraft,
                )
            } else {
                validateMedicationRegistrationStep(
                    expectedStep,
                    current.guidedMedicationDraft,
                    current.originalGuidedMedicationDraft,
                )
            }
            if (errors.isNotEmpty()) {
                update { copy(medicationRegistrationErrors = errors) }
                return
            }
            update {
                copy(
                    medicationRegistrationStep = MedicationRegistrationStep.REVIEW,
                    editorEntryGuidedMedicationDraft = null,
                    medicationRegistrationErrors = emptySet(),
                    medicationScheduleError = null,
                )
            }
            return
        }
        if (expectedStep == MedicationRegistrationStep.SCHEDULE) {
            continueMedicationScheduleSubstep(current)
            return
        }
        val errors = validateMedicationRegistrationStep(
            current.medicationRegistrationStep,
            current.guidedMedicationDraft,
            current.originalGuidedMedicationDraft,
        )
        if (errors.isNotEmpty()) {
            update { copy(medicationRegistrationErrors = errors) }
            return
        }
        update {
            copy(
                medicationRegistrationStep = medicationRegistrationStep.next(),
                medicationRegistrationErrors = emptySet(),
            )
        }
    }

    private fun continueMedicationScheduleSubstep(
        current: MedicalPlanUiState,
    ) {
        val errors = validateMedicationScheduleSubstep(
            current.medicationScheduleSubstep,
            current.guidedMedicationDraft,
            current.originalGuidedMedicationDraft,
        )
        if (errors.isNotEmpty()) {
            update { copy(medicationRegistrationErrors = errors) }
            return
        }
        val frequency = current.guidedMedicationDraft.frequency
        when (current.medicationScheduleSubstep) {
            MedicationScheduleSubstep.FREQUENCY -> update {
                copy(
                    medicationScheduleSubstep =
                        if (frequency == MedicationFrequency.EVERY_DAY) {
                            MedicationScheduleSubstep.ADMINISTRATIONS
                        } else {
                            MedicationScheduleSubstep.PARAMETERS
                        },
                    medicationRegistrationErrors = emptySet(),
                )
            }
            MedicationScheduleSubstep.PARAMETERS -> update {
                copy(
                    medicationScheduleSubstep =
                        if (
                            frequency in setOf(
                                MedicationFrequency.AS_NEEDED,
                                MedicationFrequency.EVERY_N_HOURS,
                            )
                        ) {
                            MedicationScheduleSubstep.VALIDITY
                        } else {
                            MedicationScheduleSubstep.ADMINISTRATIONS
                        },
                    medicationRegistrationErrors = emptySet(),
                )
            }
            MedicationScheduleSubstep.ADMINISTRATIONS -> update {
                copy(
                    medicationScheduleSubstep =
                        MedicationScheduleSubstep.VALIDITY,
                    medicationRegistrationErrors = emptySet(),
                )
            }
            MedicationScheduleSubstep.VALIDITY -> update {
                copy(
                    medicationRegistrationStep =
                        MedicationRegistrationStep.REMINDERS,
                    medicationRegistrationErrors = emptySet(),
                )
            }
        }
    }

    private fun previousMedicationRegistrationStep() {
        val current = mutableState.value
        if (current.medicationRegistrationStep == MedicationRegistrationStep.NAME) {
            goBack()
        } else {
            update {
                copy(
                    medicationRegistrationStep = medicationRegistrationStep.previous(),
                    medicationRegistrationErrors = emptySet(),
                )
            }
        }
    }

    private fun upsertExactMedicationAdministration(
        action: MedicalPlanAction.UpsertExactMedicationAdministration,
    ) {
        update {
            val draft = guidedMedicationDraft
            if (draft.frequency != action.frequency) return@update this
            val administrations = draft.administrationsFor(action.frequency)
            val existing = administrations.singleOrNull {
                it.id == action.editingAdministrationId
            }
            val id = action.editingAdministrationId ?: draft.nextAdministrationId
            val candidate = MedicationAdministrationDraft(
                id = id,
                persistedId = existing?.persistedId,
                timingType = MedicationTimingType.EXACT_TIME,
                exactTime = action.time,
                exactReminderEnabled = existing?.exactReminderEnabled ?: true,
                amountOverride = existing?.amountOverride,
            )
            val others = administrations.filterNot { it.id == id }
            when {
                others.any {
                    it.timingType == MedicationTimingType.EXACT_TIME &&
                        it.exactTime == action.time
                } -> copy(medicationScheduleError = MedicationScheduleError.DUPLICATE_TIME)
                others.any { it.overlaps(candidate) } ->
                    copy(
                        medicationScheduleError =
                            MedicationScheduleError.OVERLAPPING_SCHEDULE,
                    )
                else -> copy(
                    guidedMedicationDraft = draft.withAdministrations(
                        frequency = action.frequency,
                        administrations = (others + candidate)
                            .sortedBy { it.sortMinute() },
                    ).copy(
                        nextAdministrationId =
                            if (action.editingAdministrationId == null) {
                                draft.nextAdministrationId + 1
                            } else {
                                draft.nextAdministrationId
                            },
                    ),
                    medicationScheduleError = null,
                    recentlyRemovedMedicationAdministration = null,
                )
            }
        }
    }

    private fun commitMedicationWindow(frequency: MedicationFrequency) {
        update {
            val draft = guidedMedicationDraft
            if (draft.frequency != frequency) return@update this
            val editor = draft.windowEditorFor(frequency)
            val start = editor.start
            val end = editor.end
            val reminder = editor.reminder
            val administrations = draft.administrationsFor(frequency)
            val inferredReminderNextDay =
                editor.endsNextDay &&
                    reminder != null &&
                    start != null &&
                    reminder < start
            when {
                start == null || end == null ->
                    copy(medicationScheduleError = MedicationScheduleError.INCOMPLETE_WINDOW)
                !editor.endsNextDay && end < start ->
                    copy(
                        medicationScheduleError =
                            MedicationScheduleError.END_BEFORE_START_REQUIRES_NEXT_DAY,
                    )
                !editor.endsNextDay && end == start ->
                    copy(medicationScheduleError = MedicationScheduleError.INVALID_WINDOW)
                reminder != null && !reminder.isWithinWindow(
                    start = start,
                    end = end,
                    endsNextDay = editor.endsNextDay,
                    reminderNextDay = inferredReminderNextDay,
                ) -> copy(
                    medicationScheduleError =
                        MedicationScheduleError.REMINDER_OUTSIDE_WINDOW,
                )
                else -> {
                    val existing = administrations.singleOrNull {
                        it.id == editor.editingAdministrationId
                    }
                    val id =
                        editor.editingAdministrationId ?: draft.nextAdministrationId
                    val candidate = MedicationAdministrationDraft(
                        id = id,
                        persistedId = existing?.persistedId,
                        timingType = MedicationTimingType.TIME_WINDOW,
                        windowStart = start,
                        windowEnd = end,
                        reminderTime = reminder,
                        endsNextDay = editor.endsNextDay,
                        reminderNextDay = inferredReminderNextDay,
                        amountOverride = existing?.amountOverride,
                        activity = existing?.activity,
                        customActivity = existing?.customActivity.orEmpty(),
                        activityNoticeTime = existing?.activityNoticeTime,
                    )
                    val others = administrations.filterNot { it.id == id }
                    when {
                        others.any {
                            it.timingType == MedicationTimingType.TIME_WINDOW &&
                                it.windowStart == start &&
                                it.windowEnd == end &&
                                it.endsNextDay == editor.endsNextDay
                        } -> copy(
                            medicationScheduleError =
                                MedicationScheduleError.DUPLICATE_TIME,
                        )
                        others.any { it.overlaps(candidate) } -> copy(
                            medicationScheduleError =
                                MedicationScheduleError.OVERLAPPING_SCHEDULE,
                        )
                        else -> copy(
                            guidedMedicationDraft = draft.withAdministrations(
                                frequency = frequency,
                                administrations = (others + candidate)
                                    .sortedBy { it.sortMinute() },
                            ).copy(
                                windowEditorsByFrequency =
                                    draft.windowEditorsByFrequency - frequency,
                                nextAdministrationId =
                                    if (editor.editingAdministrationId == null) {
                                        draft.nextAdministrationId + 1
                                    } else {
                                        draft.nextAdministrationId
                                    },
                            ),
                            medicationScheduleError = null,
                            recentlyRemovedMedicationAdministration = null,
                        )
                    }
                }
            }
        }
    }

    private fun addActivityMedicationAdministration(
        frequency: MedicationFrequency,
    ) {
        update {
            val draft = guidedMedicationDraft
            if (draft.frequency != frequency) return@update this
            val administration = MedicationAdministrationDraft(
                id = draft.nextAdministrationId,
                timingType = MedicationTimingType.ACTIVITY_ONLY,
            )
            copy(
                guidedMedicationDraft = draft.withAdministrations(
                    frequency,
                    draft.administrationsFor(frequency) + administration,
                ).copy(
                    nextAdministrationId = draft.nextAdministrationId + 1,
                ),
                medicationRegistrationErrors = emptySet(),
                medicationScheduleError = null,
                recentlyRemovedMedicationAdministration = null,
            )
        }
    }

    private fun editMedicationWindow(
        frequency: MedicationFrequency,
        administrationId: Int,
    ) {
        update {
            val draft = guidedMedicationDraft
            val administration = draft.administrationsFor(frequency).singleOrNull {
                it.id == administrationId &&
                    it.timingType == MedicationTimingType.TIME_WINDOW
            } ?: return@update this
            copy(
                guidedMedicationDraft = draft.copy(
                    frequency = frequency,
                    timingTypesByFrequency = draft.timingTypesByFrequency +
                        (frequency to MedicationTimingType.TIME_WINDOW),
                    windowEditorsByFrequency = draft.windowEditorsByFrequency +
                        (
                            frequency to MedicationWindowEditor(
                                editingAdministrationId = administration.id,
                                start = administration.windowStart,
                                end = administration.windowEnd,
                                reminder = administration.reminderTime,
                                endsNextDay = administration.endsNextDay,
                                reminderNextDay = administration.reminderNextDay,
                            )
                            ),
                ),
                medicationScheduleError = null,
            )
        }
    }

    private fun removeMedicationAdministration(
        frequency: MedicationFrequency,
        administrationId: Int,
    ) {
        update {
            val draft = guidedMedicationDraft
            val administrations = draft.administrationsFor(frequency)
            val removed = administrations.singleOrNull {
                it.id == administrationId
            } ?: return@update this
            val editor = draft.windowEditorFor(frequency)
            copy(
                guidedMedicationDraft = draft.withAdministrations(
                    frequency = frequency,
                    administrations = administrations.filterNot {
                        it.id == administrationId
                    },
                ).copy(
                    windowEditorsByFrequency =
                        if (editor.editingAdministrationId == administrationId) {
                            draft.windowEditorsByFrequency - frequency
                        } else {
                            draft.windowEditorsByFrequency
                        },
                ),
                medicationScheduleError = null,
                recentlyRemovedMedicationAdministration =
                    RemovedMedicationAdministration(frequency, removed),
            )
        }
    }

    private fun undoRemoveMedicationAdministration() {
        update {
            val removed = recentlyRemovedMedicationAdministration ?: return@update this
            val draft = guidedMedicationDraft
            val administrations = draft.administrationsFor(removed.frequency)
            if (administrations.any { it.id == removed.administration.id }) {
                copy(recentlyRemovedMedicationAdministration = null)
            } else {
                copy(
                    guidedMedicationDraft = draft.withAdministrations(
                        frequency = removed.frequency,
                        administrations = (administrations + removed.administration)
                            .sortedBy { it.sortMinute() },
                    ),
                    medicationScheduleError = null,
                    recentlyRemovedMedicationAdministration = null,
                )
            }
        }
    }

    private fun updateMedicationAdministration(
        action: MedicalPlanAction.UpdateMedicationAdministration,
    ) {
        update {
            val updatedDraft = guidedMedicationDraft
                .withUpdatedAdministration(
                    action.frequency,
                    action.administration,
                ) ?: return@update this
            copy(guidedMedicationDraft = updatedDraft)
        }
    }

    private fun validateGuidedMedicationForSave(current: MedicalPlanUiState): Boolean {
        val invalidStep = listOf(
            MedicationRegistrationStep.NAME,
            MedicationRegistrationStep.FORM,
            MedicationRegistrationStep.DETAILS,
            MedicationRegistrationStep.SCHEDULE,
            MedicationRegistrationStep.REMINDERS,
        ).firstOrNull {
            validateMedicationRegistrationStep(
                it,
                current.guidedMedicationDraft,
                current.originalGuidedMedicationDraft,
            ).isNotEmpty()
        }
        if (invalidStep == null) return true
        val errors = validateMedicationRegistrationStep(
            invalidStep, current.guidedMedicationDraft, current.originalGuidedMedicationDraft,
        )
        update {
            copy(
                medicationRegistrationStep = invalidStep,
                medicationScheduleSubstep =
                    if (MedicationRegistrationField.ADMINISTRATIONS in errors) {
                        MedicationScheduleSubstep.ADMINISTRATIONS
                    } else {
                        medicationScheduleSubstep
                    },
                medicationRegistrationErrors = errors,
            )
        }
        return false
    }

    private fun saveGuidedMedication() {
        val current = mutableState.value
        if (
            current.isSaving ||
            current.effect in setOf(
                MedicalPlanEffect.MedicationStored,
                MedicalPlanEffect.MedicationUpdated,
            ) ||
            (current.editingExisting && !current.hasMedicationChanges)
        ) {
            return
        }
        if (!validateGuidedMedicationForSave(current)) return
        update {
            copy(
                isSaving = true,
                globalError = null,
                medicationRegistrationErrors = emptySet(),
                medicationRegistrationSaveError = null,
            )
        }
        taskScope.launch {
            repositoryCall {
                val hasConsent = repository.hasActiveSensitiveDataConsent(
                    profileId = profile.id,
                    caregiverId = profile.caregiver.id,
                    personUnderCareId = profile.personUnderCare.id,
                    documentVersion = SENSITIVE_DATA_CONSENT_VERSION,
                )
                if (!hasConsent) {
                    update {
                        copy(
                            isSaving = false,
                            sensitiveConsentDialogVisible = true,
                            sensitiveConsentAccepted = false,
                        )
                    }
                    return@repositoryCall
                }
                persistGuidedMedication()
            }
        }
    }

    private fun confirmSensitiveConsent() {
        val current = mutableState.value
        if (!current.sensitiveConsentAccepted || current.isSaving) return
        update { copy(isSaving = true, globalError = null) }
        taskScope.launch {
            repositoryCall {
                if (!repository.hasActiveSensitiveDataConsent(
                        profile.id,
                        profile.caregiver.id,
                        profile.personUnderCare.id,
                        SENSITIVE_DATA_CONSENT_VERSION,
                    )
                ) {
                    repository.recordSensitiveDataConsent(
                        profileId = profile.id,
                        caregiverId = profile.caregiver.id,
                        personUnderCareId = profile.personUnderCare.id,
                        documentVersion = SENSITIVE_DATA_CONSENT_VERSION,
                        acceptedAtEpochMillis = System.currentTimeMillis(),
                    )
                }
                update {
                    copy(
                        sensitiveConsentDialogVisible = false,
                        sensitiveConsentAccepted = false,
                    )
                }
                val todayWrite = pendingTodayMedicationWrite
                if (todayWrite != null) {
                    pendingTodayMedicationWrite = null
                    update {
                        copy(
                            isSaving = false,
                            todayMedicationFeedback = todayMedicationFeedback?.copy(status = TodayMedicationFeedbackStatus.SAVING),
                        )
                    }
                    todayWrite()
                } else persistGuidedMedication()
            }
        }
    }

    private suspend fun persistGuidedMedication() {
        val currentState = mutableState.value
        if (!validateGuidedMedicationForSave(currentState)) {
            update { copy(isSaving = false) }
            return
        }
        val draft = currentState.guidedMedicationDraft
        val sourcePlanId = currentState.newMedicationPeriodSourcePlanId
        val sourcePeriod = sourcePlanId?.let { sourceId ->
            currentState.overview.medicationPlans
                .singleOrNull { it.id == sourceId }
        }
        if (sourcePlanId != null && sourcePeriod == null) {
            showNotFound()
            return
        }
        val configuration = draft.toDomainConfiguration().let { configured ->
            when {
                currentState.editingExisting -> {
                    currentState.selectedMedicationPlan
                        ?.currentRevision
                        ?.configuration
                        ?.let { existing ->
                            configured.copy(status = existing.status)
                        }
                        ?: configured
                }
                sourcePeriod != null -> {
                    val sourceConfiguration =
                        requireNotNull(sourcePeriod.currentRevision.configuration)
                    configured.copy(
                        form = sourceConfiguration.form,
                        concentrationValue = sourceConfiguration.concentrationValue,
                        concentrationUnit = sourceConfiguration.concentrationUnit,
                        status = MedicationTreatmentStatus.ACTIVE,
                    )
                }
                else -> configured
            }
        }
        val amountUnit = draft.amountUnit.effectiveUnit(draft.customAmountUnit)
        val concentration = draft.concentrationValue.takeIf {
            it.isNotEmpty()
        }?.let { value ->
            val unit = draft.concentrationUnit.effectiveUnit(
                draft.customConcentrationUnit,
            )
            "$value $unit".trim()
        }
        val dose = "${draft.amountValue} $amountUnit".trim()
        val presentation = listOfNotNull(
            draft.form?.name,
            concentration,
        ).joinToString(" · ")
        val schedules = configuration.administrations.mapNotNull {
            it.exactTime
        }
        val result = if (
            currentState.editingExisting &&
            currentState.selectedMedicationPlanId != null
        ) {
            reviseMedication(
                ReviseMedicationPlanCommand(
                    medicationPlanId =
                        currentState.selectedMedicationPlanId,
                    personUnderCareId = profile.personUnderCare.id,
                    authorActorId = profile.caregiver.id,
                    medicationName = draft.medicationName,
                    doseAsIndicated = dose,
                    unitOrPresentation = presentation,
                    frequencyAsIndicated =
                        draft.frequency?.displaySummary(),
                    startsOn = configuration.startsOn,
                    endsOn = configuration.endsOn,
                    originalAdditionalInstructions =
                        draft.asNeededInstruction
                            .takeIf { it.isNotBlank() },
                    explicitSchedules = schedules,
                    changeReason = when (currentState.medicationEditPurpose) {
                        MedicationEditPurpose.CORRECTION ->
                            "Corrección de captura declarada por la persona cuidadora"
                        MedicationEditPurpose.INDICATION_UPDATE ->
                            "Nueva indicación médica transcrita; efectiva desde la fecha y hora de esta revisión"
                        null -> "Actualización declarada de indicaciones y programación"
                    },
                    contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                    fictitiousDataAcknowledged = false,
                    configuration = configuration,
                    careProfileId = profile.id,
                    sensitiveConsentVersion =
                        SENSITIVE_DATA_CONSENT_VERSION,
                    changeKind = when (currentState.medicationEditPurpose) {
                        MedicationEditPurpose.CORRECTION ->
                            com.bitavida.app.domain.model.MedicationPlanChangeKind.BASE_DETAILS_CORRECTED
                        MedicationEditPurpose.INDICATION_UPDATE,
                        null,
                        -> com.bitavida.app.domain.model.MedicationPlanChangeKind.SCHEDULE_CORRECTED
                    },
                ),
            )
        } else {
            registerMedication(
                RegisterMedicationPlanCommand(
                    medicalIndicationId = null,
                    personUnderCareId = profile.personUnderCare.id,
                    authorActorId = profile.caregiver.id,
                    medicationName = sourcePeriod?.currentRevision
                        ?.medicationName ?: draft.medicationName,
                    doseAsIndicated = dose,
                    unitOrPresentation = sourcePeriod?.currentRevision
                        ?.unitOrPresentation ?: presentation,
                    frequencyAsIndicated =
                        draft.frequency?.displaySummary(),
                    startsOn = configuration.startsOn,
                    endsOn = configuration.endsOn,
                    originalAdditionalInstructions =
                        draft.asNeededInstruction
                            .takeIf { it.isNotBlank() },
                    explicitSchedules = schedules,
                    contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                    fictitiousDataAcknowledged = false,
                    configuration = configuration,
                    sourceMedicationPlanId =
                        currentState.newMedicationPeriodSourcePlanId,
                    careProfileId = profile.id,
                    sensitiveConsentVersion =
                        SENSITIVE_DATA_CONSENT_VERSION,
                ),
            )
        }
        when (result) {
            is MedicalCareMutationResult.Invalid -> showValidation(result.errors)
            MedicalCareMutationResult.NotFound -> showNotFound()
            is MedicalCareMutationResult.Stored -> {
                val planId = when (val stored = result.value) {
                    is com.bitavida.app.domain.model.MedicationPlan ->
                        stored.id
                    is MedicationPlanRevision -> stored.medicationPlanId
                    else -> return
                }
                val overview = getOverview(profile.personUnderCare.id)
                update {
                    copy(
                        overview = overview,
                        selectedMedicationPlanId = planId,
                        isSaving = false,
                        medicationRegistrationErrors = emptySet(),
                        effect = if (currentState.editingExisting) {
                            MedicalPlanEffect.MedicationUpdated
                        } else {
                            MedicalPlanEffect.MedicationStored
                        },
                    )
                }
            }
        }
    }

    private fun completeGuidedMedicationSuccess() {
        update {
            if (
                screen != MedicalPlanScreen.MEDICATION_REGISTRATION ||
                effect !in setOf(
                    MedicalPlanEffect.MedicationStored,
                    MedicalPlanEffect.MedicationUpdated,
                ) ||
                selectedMedicationPlanId == null
            ) {
                return@update this
            }
            val returnsToReactivation = resumeReactivationAfterEdit
            val returnsToNewPeriod = newMedicationPeriodSourcePlanId != null
            val destination = if (
                editingExisting || returnsToReactivation || returnsToNewPeriod
            ) {
                MedicalPlanScreen.MEDICATION_DETAIL
            } else {
                MedicalPlanScreen.SECTION
            }
            copy(
                screen = destination,
                selectedSection = PlanSection.MEDICATIONS,
                medicationLifecycleDraft = if (returnsToReactivation) {
                    medicationLifecycleDraftFor(
                        status = MedicationTreatmentStatus.ACTIVE,
                        plan = selectedMedicationPlan,
                        now = LocalDateTime.now(),
                    )
                } else {
                    null
                },
                resumeReactivationAfterEdit = false,
                editingExisting = false,
                newMedicationPeriodSourcePlanId = null,
                medicationEditPurpose = null,
                originalGuidedMedicationDraft = null,
                editorEntryGuidedMedicationDraft = null,
                medicationRegistrationStep = MedicationRegistrationStep.NAME,
                medicationScheduleSubstep = MedicationScheduleSubstep.FREQUENCY,
                guidedMedicationDraft = GuidedMedicationDraft(),
                medicationRegistrationErrors = emptySet(),
                medicationScheduleError = null,
                recentlyRemovedMedicationAdministration = null,
            )
        }
    }

    private fun discardMedicationRegistration() {
        update {
            val returnsToReactivation =
                resumeReactivationAfterEdit && selectedMedicationPlanId != null
            val returnsToNewPeriod = newMedicationPeriodSourcePlanId != null
            val destination = if (
                editingExisting || returnsToReactivation || returnsToNewPeriod
            ) {
                MedicalPlanScreen.MEDICATION_DETAIL
            } else {
                MedicalPlanScreen.SECTION
            }
            copy(
                screen = destination,
                selectedSection = PlanSection.MEDICATIONS,
                medicationLifecycleDraft = if (returnsToReactivation) {
                    medicationLifecycleDraftFor(
                        status = MedicationTreatmentStatus.ACTIVE,
                        plan = selectedMedicationPlan,
                        now = LocalDateTime.now(),
                    )
                } else {
                    null
                },
                resumeReactivationAfterEdit = false,
                editingExisting = false,
                newMedicationPeriodSourcePlanId = null,
                medicationEditPurpose = null,
                originalGuidedMedicationDraft = null,
                editorEntryGuidedMedicationDraft = null,
                medicationRegistrationStep = MedicationRegistrationStep.NAME,
                medicationScheduleSubstep =
                    MedicationScheduleSubstep.FREQUENCY,
                guidedMedicationDraft = GuidedMedicationDraft(),
                medicationRegistrationErrors = emptySet(),
                medicationScheduleError = null,
                recentlyRemovedMedicationAdministration = null,
                medicationExitConfirmationVisible = false,
            )
        }
    }

    private fun showValidation(errors: Set<MedicalCareValidationError>) {
        if (state.value.medicationIntakeDraft?.fromToday == true) {
            update { copy(fieldErrors = errors.map { it.field }.toSet()) }
            failTodayMedication("No se pudo guardar la toma. Revisa la información e inténtalo otra vez.")
            return
        }
        update {
            copy(
                isSaving = false,
                fieldErrors = errors.map { it.field }.toSet(),
                medicationRegistrationSaveError = when {
                    errors.any {
                        it.issue == MedicalCareValidationIssue.OVERLAPPING_PERIOD
                    } -> "Ya existe un periodo de este medicamento que coincide con esas fechas. Ajusta la vigencia para evitar el solapamiento."
                    newMedicationPeriodSourcePlanId != null &&
                        errors.any {
                            it.field == MedicalCareField.MEDICATION_DATES
                        } -> "El nuevo periodo debe comenzar después del último día efectivo del periodo anterior."
                    newMedicationPeriodSourcePlanId != null &&
                        errors.any {
                            it.field == MedicalCareField.MEDICATION_NAME
                        } -> "No se pudo conservar la identidad del medicamento. Vuelve al detalle e inténtalo otra vez."
                    newMedicationPeriodSourcePlanId != null &&
                        errors.any {
                            it.field == MedicalCareField.VALIDITY
                        } -> "El periodo anterior todavía no está cerrado. Actualiza el detalle antes de iniciar otro."
                    else -> "No se pudo guardar el medicamento. Revisa la información e inténtalo otra vez."
                },
            )
        }
    }

    private fun showNotFound() {
        if (failTodayMedication("La toma ya cambió. Actualiza Mi día e inténtalo de nuevo.")) return
        update {
            copy(
                isSaving = false,
                globalError = MedicalPlanGlobalError.RECORD_NOT_FOUND,
            )
        }
    }

    private suspend fun repositoryCall(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: MedicalCareConcurrentChangeException) {
            if (failTodayMedication("La toma ya cambió. Su información se conserva.")) {
                try {
                    val overview = getOverview(profile.personUnderCare.id)
                    update { copy(overview = overview) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the last readable snapshot and the failure receipt if refresh is unavailable.
                }
                return
            }
            update {
                copy(
                    isLoading = false,
                    isSaving = false,
                    globalError = MedicalPlanGlobalError.CONCURRENT_CHANGE,
                )
            }
        } catch (error: MedicalCareIntegrityException) {
            if (failTodayMedication("No se pudo guardar la toma. La información anterior se conserva.")) return
            update {
                copy(
                    isLoading = false,
                    isSaving = false,
                    globalError = MedicalPlanGlobalError.INTEGRITY_ERROR,
                )
            }
        } catch (error: MedicalCareStorageUnavailableException) {
            if (failTodayMedication("No se pudo guardar la toma en este momento. Inténtalo de nuevo.")) return
            update {
                copy(
                    isLoading = false,
                    isSaving = false,
                    globalError = MedicalPlanGlobalError.STORAGE_UNAVAILABLE,
                )
            }
        }
    }

    private fun update(transform: MedicalPlanUiState.() -> MedicalPlanUiState) {
        mutableState.value = mutableState.value.transform()
    }

    companion object {
        private const val SENSITIVE_DATA_CONSENT_VERSION =
            "local-health-storage-1.0"

        fun factory(
            profile: CareProfile,
            repository: MedicalCareRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MedicalPlanViewModel(profile, repository) as T
        }
    }
}

internal fun medicationLifecycleDraftFor(
    status: MedicationTreatmentStatus,
    plan: MedicationPlan?,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MedicationLifecycleDraft {
    val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
    val pendingAt = if (status == MedicationTreatmentStatus.ACTIVE) {
        plan?.pendingReactivation(nowEpochMillis, zoneId)
            ?.effectiveAtEpochMillis
            ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDateTime() }
    } else {
        null
    }
    val effectiveAt = pendingAt ?: now
    return MedicationLifecycleDraft(
        targetStatus = status,
        effectiveOn = effectiveAt.toLocalDate().toString(),
        effectiveTime = effectiveAt.toLocalTime()
            .withSecond(0)
            .withNano(0)
            .toString(),
        reactivationTiming = if (pendingAt != null) {
            MedicationReactivationTiming.SCHEDULED
        } else {
            MedicationReactivationTiming.NOW
        },
    )
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(trim()) }.getOrNull()

private fun String.toLocalTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(trim()) }.getOrNull()

private fun MedicationPlanRevision.toGuidedMedicationDraft(
    configuration: MedicationConfiguration,
): GuidedMedicationDraft {
    val frequency = enumValueOf<MedicationFrequency>(configuration.frequency.name)
    val administrations = configuration.administrations.mapIndexed {
            index,
            administration,
        ->
        MedicationAdministrationDraft(
            id = index + 1,
            persistedId = administration.id,
            timingType =
                enumValueOf<MedicationTimingType>(administration.timing.name),
            exactTime = administration.exactTime?.let {
                MedicationTime(it.hour, it.minute)
            },
            windowStart = administration.windowStart?.let {
                MedicationTime(it.hour, it.minute)
            },
            windowEnd = administration.windowEnd?.let {
                MedicationTime(it.hour, it.minute)
            },
            reminderTime = administration.noticeTime?.let {
                MedicationTime(it.hour, it.minute)
            },
            endsNextDay = administration.windowEndsNextDay,
            reminderNextDay = administration.noticeNextDay,
            exactReminderEnabled = administration.reminderEnabled != false,
            amountOverride = administration.amountOverride?.let {
                MedicationAdministrationAmount(it.value, it.unit)
            },
            activity = administration.activityRelation?.let {
                enumValueOf<MedicationActivityOption>(it.name)
            },
            customActivity = administration.customActivity.orEmpty(),
            activityNoticeTime = administration.activityNoticeTime?.let {
                MedicationTime(it.hour, it.minute)
            },
        )
    }
    return GuidedMedicationDraft(
        medicationName = medicationName,
        form = enumValueOf<MedicationForm>(configuration.form.name),
        concentrationValue = configuration.concentrationValue.orEmpty(),
        concentrationUnit = configuration.concentrationUnit.orEmpty(),
        alternativePresentation = unitOrPresentation.orEmpty(),
        amountValue = configuration.baseAmount.value,
        amountUnit = configuration.baseAmount.unit,
        isConcentrationExpanded = configuration.concentrationValue != null,
        frequency = frequency,
        selectedWeekdays = configuration.weekdays.mapTo(mutableSetOf()) {
            enumValueOf<MedicationWeekday>(it.name)
        },
        everyNDaysInterval =
            configuration.everyNDaysInterval?.toString().orEmpty(),
        everyNDaysReferenceDate = configuration.everyNDaysReferenceDate,
        hourlyInterval = configuration.hourlyInterval?.toString().orEmpty(),
        hourlyMode = configuration.hourlyMode?.let {
            enumValueOf<MedicationHourlyMode>(it.name)
        },
        hourlyReferenceDate = configuration.hourlyReferenceDate,
        hourlyReferenceTime = configuration.hourlyReferenceTime?.let {
            MedicationTime(it.hour, it.minute)
        },
        hourlyWindowStart = configuration.hourlyWindowStart?.let {
            MedicationTime(it.hour, it.minute)
        },
        hourlyWindowEnd = configuration.hourlyWindowEnd?.let {
            MedicationTime(it.hour, it.minute)
        },
        hourlyWindowEndsNextDay = configuration.hourlyWindowEndsNextDay,
        hourlyFirstAdministration =
            configuration.hourlyFirstAdministration?.let {
                MedicationTime(it.hour, it.minute)
            },
        hourlyApplicableDaysMode =
            if (configuration.weekdays.isEmpty()) {
                MedicationApplicableDaysMode.EVERY_DAY
            } else {
                MedicationApplicableDaysMode.SPECIFIC_DAYS
            },
        hourlySelectedWeekdays =
            configuration.weekdays.mapTo(mutableSetOf()) {
                enumValueOf<MedicationWeekday>(it.name)
            },
        cycleActiveDays =
            configuration.cycle?.activeDays?.toString().orEmpty(),
        cycleRestDays =
            configuration.cycle?.restDays?.toString().orEmpty(),
        cycleReferenceDate = configuration.cycle?.referenceDate,
        cycleCount =
            configuration.cycle?.numberOfCycles?.toString().orEmpty(),
        asNeededInstruction = configuration.asNeededInstruction.orEmpty(),
        asNeededRestrictions = configuration.asNeededRestrictions.orEmpty(),
        treatmentStartMode = MedicationTreatmentStartMode.SPECIFIC_DATE,
        treatmentStartDate = configuration.startsOn,
        treatmentEndDate = configuration.endsOn,
        reminderOption = enumValueOf<MedicationReminderOption>(
            configuration.reminderPreference.name,
        ),
        secondReminderEnabled = configuration.secondReminderEnabled,
        timingTypesByFrequency = administrations.firstOrNull()?.let {
            mapOf(frequency to it.timingType)
        }.orEmpty(),
        administrationsByFrequency = mapOf(frequency to administrations),
        nextAdministrationId = administrations.size + 1,
    )
}

private fun MedicationRegistrationStep.next(): MedicationRegistrationStep = when (this) {
    MedicationRegistrationStep.NAME -> MedicationRegistrationStep.FORM
    MedicationRegistrationStep.FORM -> MedicationRegistrationStep.DETAILS
    MedicationRegistrationStep.DETAILS -> MedicationRegistrationStep.SCHEDULE
    MedicationRegistrationStep.SCHEDULE -> MedicationRegistrationStep.REMINDERS
    MedicationRegistrationStep.REMINDERS -> MedicationRegistrationStep.REVIEW
    MedicationRegistrationStep.REVIEW -> MedicationRegistrationStep.REVIEW
}

private fun GuidedMedicationDraft.toDomainConfiguration(): MedicationConfiguration {
    val selectedFrequency = requireNotNull(frequency)
    val startDate = when (treatmentStartMode) {
        MedicationTreatmentStartMode.TODAY -> LocalDate.now()
        MedicationTreatmentStartMode.SPECIFIC_DATE ->
            requireNotNull(treatmentStartDate)
        null -> error("Treatment start must be selected before persistence.")
    }
    val activeAdministrations = administrationsFor(selectedFrequency).map { draft ->
        MedicationAdministration(
            id = draft.persistedId ?: UUID.randomUUID().toString(),
            timing = when (draft.timingType) {
                MedicationTimingType.EXACT_TIME ->
                    MedicationAdministrationTiming.EXACT_TIME
                MedicationTimingType.TIME_WINDOW ->
                    MedicationAdministrationTiming.TIME_WINDOW
                MedicationTimingType.ACTIVITY_ONLY ->
                    MedicationAdministrationTiming.ACTIVITY_ONLY
            },
            exactTime = draft.exactTime?.toLocalTime(),
            windowStart = draft.windowStart?.toLocalTime(),
            windowEnd = draft.windowEnd?.toLocalTime(),
            windowEndsNextDay = draft.endsNextDay,
            noticeTime = draft.reminderTime?.toLocalTime(),
            noticeNextDay = draft.reminderNextDay,
            reminderEnabled = when (draft.timingType) {
                MedicationTimingType.EXACT_TIME -> draft.exactReminderEnabled
                MedicationTimingType.TIME_WINDOW,
                MedicationTimingType.ACTIVITY_ONLY,
                -> null
            },
            amountOverride = draft.amountOverride?.let {
                MedicationAmount(it.value, it.unit.trim())
            },
            activityRelation = draft.activity
                .takeIf {
                    draft.timingType == MedicationTimingType.ACTIVITY_ONLY
                }
                ?.let {
                    enumValueOf<MedicationActivityRelation>(it.name)
                },
            customActivity = draft.customActivity.trim()
                .takeIf {
                    draft.timingType == MedicationTimingType.ACTIVITY_ONLY &&
                        it.isNotEmpty()
                },
            activityNoticeTime = draft.activityNoticeTime
                .takeIf {
                    draft.timingType == MedicationTimingType.ACTIVITY_ONLY
                }
                ?.toLocalTime(),
        )
    }
    val selectedDays = when (selectedFrequency) {
        MedicationFrequency.SPECIFIC_DAYS -> selectedWeekdays
        MedicationFrequency.EVERY_N_HOURS ->
            if (
                hourlyApplicableDaysMode ==
                MedicationApplicableDaysMode.SPECIFIC_DAYS
            ) {
                hourlySelectedWeekdays
            } else {
                emptySet()
            }
        else -> emptySet()
    }.mapTo(mutableSetOf()) { java.time.DayOfWeek.valueOf(it.name) }
    val reminder = requireNotNull(reminderOption)
    return MedicationConfiguration(
        form = enumValueOf<MedicationProductForm>(requireNotNull(form).name),
        concentrationValue = concentrationValue.takeIf {
            it.isNotEmpty()
        },
        concentrationUnit = concentrationUnit.effectiveUnit(
            customConcentrationUnit,
        ).takeIf { concentrationValue.isNotEmpty() && it.isNotEmpty() },
        baseAmount = MedicationAmount(
            amountValue,
            amountUnit.effectiveUnit(customAmountUnit),
        ),
        frequency = enumValueOf<MedicationFrequencyKind>(
            selectedFrequency.name,
        ),
        weekdays = selectedDays,
        everyNDaysInterval = everyNDaysInterval.toIntOrNull(),
        everyNDaysReferenceDate = everyNDaysReferenceDate,
        hourlyInterval = hourlyInterval.toIntOrNull(),
        hourlyMode = hourlyMode?.let {
            enumValueOf<MedicationHourlyIntervalMode>(it.name)
        },
        hourlyReferenceDate = hourlyReferenceDate,
        hourlyReferenceTime = hourlyReferenceTime?.toLocalTime(),
        hourlyWindowStart = hourlyWindowStart?.toLocalTime(),
        hourlyWindowEnd = hourlyWindowEnd?.toLocalTime(),
        hourlyWindowEndsNextDay = hourlyWindowEndsNextDay,
        hourlyFirstAdministration =
            hourlyFirstAdministration?.toLocalTime(),
        cycle = if (selectedFrequency == MedicationFrequency.CYCLE) {
            MedicationCycle(
                activeDays = cycleActiveDays.toInt(),
                restDays = cycleRestDays.toInt(),
                referenceDate = requireNotNull(cycleReferenceDate),
                numberOfCycles = cycleCount.toIntOrNull(),
            )
        } else {
            null
        },
        asNeededInstruction = asNeededInstruction.trim().takeIf {
            it.isNotEmpty()
        },
        asNeededRestrictions = asNeededRestrictions.trim().takeIf {
            it.isNotEmpty()
        },
        startsOn = startDate,
        endsOn = treatmentEndDate,
        reminderPreference =
            enumValueOf<MedicationReminderPreference>(reminder.name),
        secondReminderEnabled =
            reminder != MedicationReminderOption.NONE &&
                secondReminderEnabled,
        reminderEffectiveState =
            if (reminder == MedicationReminderOption.NONE) {
                MedicationReminderEffectiveState.NOT_REQUESTED
            } else {
                MedicationReminderEffectiveState
                    .PREFERENCE_RECORDED_NOT_SCHEDULED
            },
        administrations = activeAdministrations,
    )
}

private fun MedicationTime.toLocalTime(): LocalTime =
    LocalTime.of(hour, minute)

private fun MedicationFrequency.displaySummary(): String = when (this) {
    MedicationFrequency.EVERY_DAY -> "Todos los días"
    MedicationFrequency.SPECIFIC_DAYS -> "Días específicos"
    MedicationFrequency.EVERY_N_DAYS -> "Cada cierto número de días"
    MedicationFrequency.EVERY_N_HOURS -> "Cada cierto número de horas"
    MedicationFrequency.CYCLE -> "Por ciclos"
    MedicationFrequency.AS_NEEDED -> "Cuando sea necesario"
}

private fun MedicationRegistrationStep.previous(): MedicationRegistrationStep = when (this) {
    MedicationRegistrationStep.NAME -> MedicationRegistrationStep.NAME
    MedicationRegistrationStep.FORM -> MedicationRegistrationStep.NAME
    MedicationRegistrationStep.DETAILS -> MedicationRegistrationStep.FORM
    MedicationRegistrationStep.SCHEDULE -> MedicationRegistrationStep.DETAILS
    MedicationRegistrationStep.REMINDERS -> MedicationRegistrationStep.SCHEDULE
    MedicationRegistrationStep.REVIEW -> MedicationRegistrationStep.REMINDERS
}

internal fun validateMedicationRegistrationStep(
    step: MedicationRegistrationStep,
    draft: GuidedMedicationDraft,
    original: GuidedMedicationDraft? = null,
): Set<MedicationRegistrationField> = buildSet {
    when (step) {
        MedicationRegistrationStep.NAME -> {
            if (draft.medicationName.isBlank() ||
                !CaptureLimit.MEDICATION_NAME.acceptsStoredOrNew(
                    draft.medicationName, original?.medicationName,
                )
            ) {
                add(MedicationRegistrationField.NAME)
            }
            val concentrationUnit = draft.concentrationUnit.effectiveUnit(
                draft.customConcentrationUnit,
            )
            if ((draft.concentrationValue.isNotBlank() && concentrationUnit.isBlank()) ||
                !CaptureLimit.UNIT.acceptsStoredOrNew(
                    concentrationUnit,
                    original?.concentrationUnit?.effectiveUnit(original.customConcentrationUnit),
                )
            ) {
                add(MedicationRegistrationField.CONCENTRATION_UNIT)
            }
            if (
                draft.concentrationValue.isNotEmpty() &&
                !MedicationValueRules.isValidReplacement(
                    draft.concentrationValue, original?.concentrationValue,
                )
            ) {
                add(MedicationRegistrationField.CONCENTRATION_VALUE)
            }
        }
        MedicationRegistrationStep.FORM -> {
            if (draft.form == null) add(MedicationRegistrationField.FORM)
        }
        MedicationRegistrationStep.DETAILS -> {
            val amountUnit = draft.amountUnit.effectiveUnit(draft.customAmountUnit)
            if (!MedicationValueRules.isValidReplacement(
                    draft.amountValue, original?.amountValue,
                )
            ) {
                add(MedicationRegistrationField.AMOUNT_VALUE)
            }
            if (amountUnit.isBlank() || !CaptureLimit.UNIT.acceptsStoredOrNew(
                    amountUnit,
                    original?.amountUnit?.effectiveUnit(original.customAmountUnit),
                )
            ) {
                add(MedicationRegistrationField.AMOUNT_UNIT)
            }
        }
        MedicationRegistrationStep.SCHEDULE -> {
            addAll(draft.scheduleCaptureLimitErrors(original))
            val frequency = draft.frequency
            if (frequency == null) {
                add(MedicationRegistrationField.FREQUENCY)
            } else {
                when (frequency) {
                    MedicationFrequency.EVERY_DAY -> {
                        if (draft.administrationsFor(frequency).isEmpty()) {
                            add(MedicationRegistrationField.ADMINISTRATIONS)
                        }
                    }
                    MedicationFrequency.SPECIFIC_DAYS -> {
                        if (draft.selectedWeekdays.isEmpty()) {
                            add(MedicationRegistrationField.WEEKDAYS)
                        }
                        if (draft.administrationsFor(frequency).isEmpty()) {
                            add(MedicationRegistrationField.ADMINISTRATIONS)
                        }
                    }
                    MedicationFrequency.EVERY_N_DAYS -> {
                        if (!draft.everyNDaysInterval.isPositiveWholeNumber()) {
                            add(MedicationRegistrationField.INTERVAL_DAYS)
                        }
                        if (draft.everyNDaysReferenceDate == null) {
                            add(MedicationRegistrationField.REFERENCE_DATE)
                        }
                        if (draft.administrationsFor(frequency).isEmpty()) {
                            add(MedicationRegistrationField.ADMINISTRATIONS)
                        }
                    }
                    MedicationFrequency.EVERY_N_HOURS -> {
                        if (!draft.hourlyInterval.isPositiveWholeNumber()) {
                            add(MedicationRegistrationField.INTERVAL_HOURS)
                        }
                        when (draft.hourlyMode) {
                            null -> add(MedicationRegistrationField.HOURLY_MODE)
                            MedicationHourlyMode.CONTINUOUS -> {
                                if (draft.hourlyReferenceDate == null) {
                                    add(MedicationRegistrationField.HOURLY_REFERENCE_DATE)
                                }
                                if (draft.hourlyReferenceTime == null) {
                                    add(MedicationRegistrationField.HOURLY_REFERENCE_TIME)
                                }
                            }
                            MedicationHourlyMode.DAILY_WINDOW -> {
                                val start = draft.hourlyWindowStart
                                val end = draft.hourlyWindowEnd
                                if (
                                    start == null || end == null ||
                                    (!draft.hourlyWindowEndsNextDay && end <= start)
                                ) {
                                    add(MedicationRegistrationField.HOURLY_WINDOW)
                                }
                                val first = draft.hourlyFirstAdministration
                                if (
                                    first == null ||
                                    start == null ||
                                    end == null ||
                                    !first.isWithinWindow(
                                        start = start,
                                        end = end,
                                        endsNextDay =
                                            draft.hourlyWindowEndsNextDay,
                                        reminderNextDay =
                                            draft.hourlyWindowEndsNextDay &&
                                                first < start,
                                    )
                                ) {
                                    add(
                                        MedicationRegistrationField
                                            .HOURLY_FIRST_ADMINISTRATION,
                                    )
                                }
                                if (draft.hourlyApplicableDaysMode == null) {
                                    add(
                                        MedicationRegistrationField
                                            .HOURLY_APPLICABLE_DAYS,
                                    )
                                }
                                if (
                                    draft.hourlyApplicableDaysMode ==
                                    MedicationApplicableDaysMode.SPECIFIC_DAYS &&
                                    draft.hourlySelectedWeekdays.isEmpty()
                                ) {
                                    add(
                                        MedicationRegistrationField
                                            .HOURLY_APPLICABLE_DAYS,
                                    )
                                }
                            }
                        }
                    }
                    MedicationFrequency.CYCLE -> {
                        if (!draft.cycleActiveDays.isPositiveWholeNumber()) {
                            add(MedicationRegistrationField.CYCLE_ACTIVE_DAYS)
                        }
                        if (!draft.cycleRestDays.isPositiveWholeNumber()) {
                            add(MedicationRegistrationField.CYCLE_REST_DAYS)
                        }
                        if (draft.cycleReferenceDate == null) {
                            add(
                                MedicationRegistrationField
                                    .CYCLE_REFERENCE_DATE,
                            )
                        }
                        if (
                            draft.cycleCount.isNotBlank() &&
                            !draft.cycleCount.isPositiveWholeNumber()
                        ) {
                            add(MedicationRegistrationField.TREATMENT_END)
                        }
                        if (draft.administrationsFor(frequency).isEmpty()) {
                            add(MedicationRegistrationField.ADMINISTRATIONS)
                        }
                    }
                    MedicationFrequency.AS_NEEDED -> {
                        if (draft.asNeededInstruction.trim().isEmpty()) {
                            add(
                                MedicationRegistrationField
                                    .AS_NEEDED_INSTRUCTION,
                            )
                        }
                    }
                }
                if (
                    draft.administrationsFor(frequency).any {
                        val amount = it.amountOverride
                        (amount != null &&
                            (!MedicationValueRules.isValidReplacement(
                                amount.value,
                                original?.administrationsFor(frequency)
                                    ?.singleOrNull { saved -> saved.id == it.id }
                                    ?.amountOverride?.value,
                            ) || amount.unit.isBlank())) ||
                            (it.activity == MedicationActivityOption.OTHER &&
                                it.customActivity.isBlank()) ||
                            (it.timingType ==
                                MedicationTimingType.ACTIVITY_ONLY &&
                                (it.activity == null ||
                                    it.activityNoticeTime == null))
                    }
                ) {
                    add(MedicationRegistrationField.ADMINISTRATIONS)
                }
                if (draft.treatmentStartMode == null) {
                    add(MedicationRegistrationField.TREATMENT_START)
                }
                if (
                    draft.treatmentStartMode ==
                    MedicationTreatmentStartMode.SPECIFIC_DATE &&
                    draft.treatmentStartDate == null
                ) {
                    add(MedicationRegistrationField.TREATMENT_START)
                }
                val start = when (draft.treatmentStartMode) {
                    MedicationTreatmentStartMode.TODAY -> LocalDate.now()
                    MedicationTreatmentStartMode.SPECIFIC_DATE ->
                        draft.treatmentStartDate
                    null -> null
                }
                if (
                    start != null &&
                    draft.treatmentEndDate?.isBefore(start) == true
                ) {
                    add(MedicationRegistrationField.TREATMENT_END)
                }
                if (draft.hasContradictoryCycleEnd()) {
                    add(MedicationRegistrationField.TREATMENT_END)
                }
            }
        }
        MedicationRegistrationStep.REMINDERS -> {
            if (
                draft.reminderOption == null ||
                (draft.frequency == MedicationFrequency.AS_NEEDED &&
                    draft.reminderOption != MedicationReminderOption.NONE)
            ) {
                add(MedicationRegistrationField.REMINDER_PREFERENCE)
            }
        }
        MedicationRegistrationStep.REVIEW -> Unit
    }
}

internal fun validateMedicationScheduleSubstep(
    substep: MedicationScheduleSubstep,
    draft: GuidedMedicationDraft,
    original: GuidedMedicationDraft? = null,
): Set<MedicationRegistrationField> = buildSet {
    val frequency = draft.frequency
    val captureErrors = draft.scheduleCaptureLimitErrors(original)
    when (substep) {
        MedicationScheduleSubstep.PARAMETERS ->
            addAll(captureErrors - MedicationRegistrationField.ADMINISTRATIONS)
        MedicationScheduleSubstep.ADMINISTRATIONS ->
            addAll(captureErrors.filter { it == MedicationRegistrationField.ADMINISTRATIONS })
        else -> Unit
    }
    when (substep) {
        MedicationScheduleSubstep.FREQUENCY -> {
            if (frequency == null) add(MedicationRegistrationField.FREQUENCY)
        }
        MedicationScheduleSubstep.PARAMETERS -> when (frequency) {
            null -> add(MedicationRegistrationField.FREQUENCY)
            MedicationFrequency.EVERY_DAY -> Unit
            MedicationFrequency.SPECIFIC_DAYS -> {
                if (draft.selectedWeekdays.isEmpty()) {
                    add(MedicationRegistrationField.WEEKDAYS)
                }
            }
            MedicationFrequency.EVERY_N_DAYS -> {
                if (!draft.everyNDaysInterval.isPositiveWholeNumber()) {
                    add(MedicationRegistrationField.INTERVAL_DAYS)
                }
                if (draft.everyNDaysReferenceDate == null) {
                    add(MedicationRegistrationField.REFERENCE_DATE)
                }
            }
            MedicationFrequency.EVERY_N_HOURS -> {
                if (!draft.hourlyInterval.isPositiveWholeNumber()) {
                    add(MedicationRegistrationField.INTERVAL_HOURS)
                }
                when (draft.hourlyMode) {
                    null -> add(MedicationRegistrationField.HOURLY_MODE)
                    MedicationHourlyMode.CONTINUOUS -> {
                        if (draft.hourlyReferenceDate == null) {
                            add(MedicationRegistrationField.HOURLY_REFERENCE_DATE)
                        }
                        if (draft.hourlyReferenceTime == null) {
                            add(MedicationRegistrationField.HOURLY_REFERENCE_TIME)
                        }
                    }
                    MedicationHourlyMode.DAILY_WINDOW -> {
                        val start = draft.hourlyWindowStart
                        val end = draft.hourlyWindowEnd
                        if (
                            start == null || end == null ||
                            (!draft.hourlyWindowEndsNextDay && end <= start)
                        ) {
                            add(MedicationRegistrationField.HOURLY_WINDOW)
                        }
                        val first = draft.hourlyFirstAdministration
                        if (
                            first == null || start == null || end == null ||
                            !first.isWithinWindow(
                                start = start,
                                end = end,
                                endsNextDay = draft.hourlyWindowEndsNextDay,
                                reminderNextDay =
                                    draft.hourlyWindowEndsNextDay && first < start,
                            )
                        ) {
                            add(
                                MedicationRegistrationField
                                    .HOURLY_FIRST_ADMINISTRATION,
                            )
                        }
                        if (
                            draft.hourlyApplicableDaysMode == null ||
                            (
                                draft.hourlyApplicableDaysMode ==
                                    MedicationApplicableDaysMode.SPECIFIC_DAYS &&
                                    draft.hourlySelectedWeekdays.isEmpty()
                                )
                        ) {
                            add(
                                MedicationRegistrationField
                                    .HOURLY_APPLICABLE_DAYS,
                            )
                        }
                    }
                }
            }
            MedicationFrequency.CYCLE -> {
                if (!draft.cycleActiveDays.isPositiveWholeNumber()) {
                    add(MedicationRegistrationField.CYCLE_ACTIVE_DAYS)
                }
                if (!draft.cycleRestDays.isPositiveWholeNumber()) {
                    add(MedicationRegistrationField.CYCLE_REST_DAYS)
                }
                if (draft.cycleReferenceDate == null) {
                    add(MedicationRegistrationField.CYCLE_REFERENCE_DATE)
                }
                if (
                    draft.cycleCount.isNotBlank() &&
                    !draft.cycleCount.isPositiveWholeNumber()
                ) {
                    add(MedicationRegistrationField.TREATMENT_END)
                }
            }
            MedicationFrequency.AS_NEEDED -> {
                if (draft.asNeededInstruction.trim().isEmpty()) {
                    add(MedicationRegistrationField.AS_NEEDED_INSTRUCTION)
                }
            }
        }
        MedicationScheduleSubstep.ADMINISTRATIONS -> {
            if (frequency == null) {
                add(MedicationRegistrationField.FREQUENCY)
            } else {
                val administrations = draft.administrationsFor(frequency)
                if (
                    frequency in setOf(
                        MedicationFrequency.EVERY_DAY,
                        MedicationFrequency.SPECIFIC_DAYS,
                        MedicationFrequency.EVERY_N_DAYS,
                        MedicationFrequency.CYCLE,
                    ) &&
                    administrations.isEmpty()
                ) {
                    add(MedicationRegistrationField.ADMINISTRATIONS)
                }
                if (
                    administrations.any {
                        val amount = it.amountOverride
                        (amount != null &&
                            (!MedicationValueRules.isValidReplacement(
                                amount.value,
                                original?.administrationsFor(frequency)
                                    ?.singleOrNull { saved -> saved.id == it.id }
                                    ?.amountOverride?.value,
                            ) || amount.unit.isBlank())) ||
                            (it.activity == MedicationActivityOption.OTHER &&
                                it.customActivity.isBlank()) ||
                            (it.timingType == MedicationTimingType.ACTIVITY_ONLY &&
                                (it.activity == null ||
                                    it.activityNoticeTime == null))
                    }
                ) {
                    add(MedicationRegistrationField.ADMINISTRATIONS)
                }
            }
        }
        MedicationScheduleSubstep.VALIDITY -> {
            if (draft.treatmentStartMode == null) {
                add(MedicationRegistrationField.TREATMENT_START)
            }
            if (
                draft.treatmentStartMode ==
                MedicationTreatmentStartMode.SPECIFIC_DATE &&
                draft.treatmentStartDate == null
            ) {
                add(MedicationRegistrationField.TREATMENT_START)
            }
            val start = when (draft.treatmentStartMode) {
                MedicationTreatmentStartMode.TODAY -> LocalDate.now()
                MedicationTreatmentStartMode.SPECIFIC_DATE ->
                    draft.treatmentStartDate
                null -> null
            }
            if (
                (start != null &&
                    draft.treatmentEndDate?.isBefore(start) == true) ||
                draft.hasContradictoryCycleEnd()
            ) {
                add(MedicationRegistrationField.TREATMENT_END)
            }
        }
    }
}

private fun GuidedMedicationDraft.hasContradictoryCycleEnd(): Boolean {
    if (frequency != MedicationFrequency.CYCLE) return false
    val activeDays = cycleActiveDays.trim().toLongOrNull() ?: return false
    val restDays = cycleRestDays.trim().toLongOrNull() ?: return false
    val cycles = cycleCount.trim().toLongOrNull() ?: return false
    val reference = cycleReferenceDate ?: return false
    val end = treatmentEndDate ?: return false
    if (activeDays <= 0 || restDays <= 0 || cycles <= 0) return false
    val expectedEnd = reference.plusDays(
        ((activeDays + restDays) * (cycles - 1)) + activeDays - 1,
    )
    return end != expectedEnd
}

private fun String.effectiveUnit(customUnit: String): String =
    if (this == CUSTOM_UNIT_OPTION) customUnit.trim() else trim()

private fun GuidedMedicationDraft.hasCapturedData(): Boolean =
    medicationName.isNotBlank() ||
        form != null ||
        concentrationValue.isNotBlank() ||
        concentrationUnit.isNotBlank() ||
        customConcentrationUnit.isNotBlank() ||
        alternativePresentation.isNotBlank() ||
        amountValue.isNotBlank() ||
        amountUnit.isNotBlank() ||
        customAmountUnit.isNotBlank() ||
        frequency != null ||
        selectedWeekdays.isNotEmpty() ||
        everyNDaysInterval.isNotBlank() ||
        everyNDaysReferenceDate != null ||
        hourlyInterval.isNotBlank() ||
        hourlyMode != null ||
        hourlyReferenceDate != null ||
        hourlyReferenceTime != null ||
        hourlyWindowStart != null ||
        hourlyWindowEnd != null ||
        hourlyApplicableDaysMode != null ||
        hourlySelectedWeekdays.isNotEmpty() ||
        timingTypesByFrequency.isNotEmpty() ||
        administrationsByFrequency.values.any { it.isNotEmpty() } ||
        windowEditorsByFrequency.values.any { editor ->
            editor.start != null || editor.end != null || editor.reminder != null
        }

private fun GuidedMedicationDraft.administrationsFor(
    frequency: MedicationFrequency,
): List<MedicationAdministrationDraft> =
    administrationsByFrequency[frequency].orEmpty()

private fun GuidedMedicationDraft.windowEditorFor(
    frequency: MedicationFrequency,
): MedicationWindowEditor =
    windowEditorsByFrequency[frequency] ?: MedicationWindowEditor()

private fun GuidedMedicationDraft.withAdministrations(
    frequency: MedicationFrequency,
    administrations: List<MedicationAdministrationDraft>,
): GuidedMedicationDraft = copy(
    administrationsByFrequency = administrationsByFrequency +
        (frequency to administrations),
)

internal fun GuidedMedicationDraft.withUpdatedAdministration(
    frequency: MedicationFrequency,
    administration: MedicationAdministrationDraft,
): GuidedMedicationDraft? {
    val current = administrationsFor(frequency)
    if (current.none { it.id == administration.id }) return null
    return withAdministrations(
        frequency,
        current.map {
            if (it.id == administration.id) administration else it
        },
    )
}

private fun MedicationAdministrationDraft.sortMinute(): Int =
    exactTime?.minuteOfDay ?: windowStart?.minuteOfDay ?: Int.MAX_VALUE

private fun String.isPositiveWholeNumber(): Boolean =
    CaptureLimit.INTEGER.acceptsStoredOrNew(this) &&
        trim().toIntOrNull()?.let { it > 0 } == true

private fun GuidedMedicationDraft.scheduleCaptureLimitErrors(
    original: GuidedMedicationDraft?,
): Set<MedicationRegistrationField> = buildSet {
    when (frequency) {
        MedicationFrequency.EVERY_N_DAYS -> if (
            !CaptureLimit.INTEGER.acceptsStoredOrNew(everyNDaysInterval, original?.everyNDaysInterval)
        ) add(MedicationRegistrationField.INTERVAL_DAYS)
        MedicationFrequency.EVERY_N_HOURS -> if (
            !CaptureLimit.INTEGER.acceptsStoredOrNew(hourlyInterval, original?.hourlyInterval)
        ) add(MedicationRegistrationField.INTERVAL_HOURS)
        MedicationFrequency.CYCLE -> {
            if (!CaptureLimit.INTEGER.acceptsStoredOrNew(cycleActiveDays, original?.cycleActiveDays)) {
                add(MedicationRegistrationField.CYCLE_ACTIVE_DAYS)
            }
            if (!CaptureLimit.INTEGER.acceptsStoredOrNew(cycleRestDays, original?.cycleRestDays)) {
                add(MedicationRegistrationField.CYCLE_REST_DAYS)
            }
            if (!CaptureLimit.CYCLE_COUNT.acceptsStoredOrNew(cycleCount, original?.cycleCount)) {
                add(MedicationRegistrationField.TREATMENT_END)
            }
        }
        MedicationFrequency.AS_NEEDED -> if (
            !CaptureLimit.INSTRUCTION.acceptsStoredOrNew(asNeededInstruction, original?.asNeededInstruction) ||
            !CaptureLimit.INSTRUCTION.acceptsStoredOrNew(asNeededRestrictions, original?.asNeededRestrictions)
        ) add(MedicationRegistrationField.AS_NEEDED_INSTRUCTION)
        else -> Unit
    }
    val activeFrequency = frequency ?: return@buildSet
    administrationsFor(activeFrequency).forEach { administration ->
        val stored = original?.administrationsFor(activeFrequency)
            ?.singleOrNull { it.id == administration.id }
        if (
            !CaptureLimit.UNIT.acceptsStoredOrNew(
                administration.amountOverride?.unit.orEmpty(), stored?.amountOverride?.unit,
            ) ||
            (administration.activity == MedicationActivityOption.OTHER &&
                !CaptureLimit.SHORT_LABEL.acceptsStoredOrNew(
                    administration.customActivity, stored?.customActivity,
                ))
        ) add(MedicationRegistrationField.ADMINISTRATIONS)
    }
}

internal fun MedicationTime.isWithinWindow(
    start: MedicationTime,
    end: MedicationTime,
    endsNextDay: Boolean,
    reminderNextDay: Boolean,
): Boolean {
    val startMinute = start.minuteOfDay
    val endMinute = end.minuteOfDay + if (endsNextDay) MINUTES_PER_DAY else 0
    val reminderMinute = minuteOfDay +
        if (reminderNextDay) MINUTES_PER_DAY else 0
    return reminderMinute in startMinute..endMinute
}

private data class MedicationScheduleSpan(
    val start: Int,
    val end: Int,
    val isPoint: Boolean,
) {
    fun shiftedBy(minutes: Int): MedicationScheduleSpan = copy(
        start = start + minutes,
        end = end + minutes,
    )
}

private fun MedicationAdministrationDraft.toScheduleSpan(): MedicationScheduleSpan? =
    when (timingType) {
        MedicationTimingType.EXACT_TIME -> exactTime?.minuteOfDay?.let { minute ->
            MedicationScheduleSpan(minute, minute, isPoint = true)
        }
        MedicationTimingType.TIME_WINDOW -> {
            val start = windowStart?.minuteOfDay ?: return null
            val end = windowEnd?.minuteOfDay ?: return null
            MedicationScheduleSpan(
                start = start,
                end = end + if (endsNextDay) MINUTES_PER_DAY else 0,
                isPoint = false,
            )
        }
        MedicationTimingType.ACTIVITY_ONLY -> null
    }

private fun MedicationAdministrationDraft.overlaps(
    other: MedicationAdministrationDraft,
): Boolean {
    val first = toScheduleSpan() ?: return false
    val second = other.toScheduleSpan() ?: return false
    return listOf(-MINUTES_PER_DAY, 0, MINUTES_PER_DAY).any { shift ->
        first.overlaps(second.shiftedBy(shift))
    }
}

private fun MedicationScheduleSpan.overlaps(other: MedicationScheduleSpan): Boolean =
    when {
        isPoint && other.isPoint -> start == other.start
        isPoint -> start in other.start..other.end
        other.isPoint -> other.start in start..end
        else -> maxOf(start, other.start) < minOf(end, other.end)
    }

private const val MINUTES_PER_DAY = 24 * 60

internal const val CUSTOM_UNIT_OPTION = "Otra"

private data class ParsedIndicationDraft(
    val indicatedOn: LocalDate?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val errors: Set<MedicalCareValidationError>,
)

private fun parseIndicationDraft(draft: IndicationDraft): ParsedIndicationDraft {
    val errors = mutableSetOf<MedicalCareValidationError>()
    val indicatedOn = draft.indicatedOn.parseRequiredDate(
        MedicalCareField.INDICATED_ON,
        errors,
    )
    val validFrom = draft.validFrom.parseOptionalDate(MedicalCareField.VALIDITY, errors)
    val validUntil = draft.validUntil.parseOptionalDate(MedicalCareField.VALIDITY, errors)
    return ParsedIndicationDraft(indicatedOn, validFrom, validUntil, errors)
}

private data class ParsedMedicationDraft(
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val schedules: List<LocalTime>,
    val errors: Set<MedicalCareValidationError>,
)

private fun parseMedicationDraft(draft: MedicationDraft): ParsedMedicationDraft {
    val errors = mutableSetOf<MedicalCareValidationError>()
    if (!CaptureLimit.BRIEF_NOTE.acceptsStoredOrNew(draft.explicitSchedules)) {
        errors += MedicalCareValidationError(
            MedicalCareField.EXPLICIT_SCHEDULES, MedicalCareValidationIssue.TOO_LONG,
        )
    }
    val startsOn = draft.startsOn.parseRequiredDate(
        MedicalCareField.MEDICATION_DATES,
        errors,
    )
    val endsOn = draft.endsOn.parseOptionalDate(MedicalCareField.MEDICATION_DATES, errors)
    val schedules = if (draft.explicitSchedules.isBlank()) {
        errors += MedicalCareValidationError(
            MedicalCareField.EXPLICIT_SCHEDULES,
            MedicalCareValidationIssue.REQUIRED,
        )
        emptyList()
    } else {
        draft.explicitSchedules.split(",").mapNotNull { value ->
            runCatching { LocalTime.parse(value.trim()) }.getOrElse {
                errors += MedicalCareValidationError(
                    MedicalCareField.EXPLICIT_SCHEDULES,
                    MedicalCareValidationIssue.REQUIRED,
                )
                null
            }
        }
    }
    return ParsedMedicationDraft(startsOn, endsOn, schedules, errors)
}

private fun String.parseRequiredDate(
    field: MedicalCareField,
    errors: MutableSet<MedicalCareValidationError>,
): LocalDate? {
    if (isBlank()) {
        errors += MedicalCareValidationError(field, MedicalCareValidationIssue.REQUIRED)
        return null
    }
    if (!CaptureLimit.DATE.acceptsStoredOrNew(this)) {
        errors += MedicalCareValidationError(field, MedicalCareValidationIssue.TOO_LONG)
        return null
    }
    return runCatching { LocalDate.parse(trim()) }.getOrElse {
        errors += MedicalCareValidationError(field, MedicalCareValidationIssue.REQUIRED)
        null
    }
}

private fun String.parseOptionalDate(
    field: MedicalCareField,
    errors: MutableSet<MedicalCareValidationError>,
): LocalDate? {
    if (isBlank()) return null
    if (!CaptureLimit.DATE.acceptsStoredOrNew(this)) {
        errors += MedicalCareValidationError(field, MedicalCareValidationIssue.TOO_LONG)
        return null
    }
    return runCatching { LocalDate.parse(trim()) }.getOrElse {
        errors += MedicalCareValidationError(field, MedicalCareValidationIssue.REQUIRED)
        null
    }
}
