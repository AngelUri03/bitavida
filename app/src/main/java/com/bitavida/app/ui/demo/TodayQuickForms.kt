package com.bitavida.app.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.scheduledOccurrenceId
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.medical.MedicalPlanAction
import com.bitavida.app.ui.medical.MedicalPlanViewModel
import com.bitavida.app.ui.medical.TodayMedicationFeedback
import com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus
import com.bitavida.app.ui.medical.MedicationIntakeSheet
import com.bitavida.app.ui.medical.MedicationSensitiveConsentDialog
import com.bitavida.app.ui.medical.hasDeclaredResponse
import com.bitavida.app.ui.medical.intakesForOccurrence
import com.bitavida.app.ui.medical.medicationOccurrencesForDay
import com.bitavida.app.ui.notes.NotesPhotoCoordinator
import com.bitavida.app.ui.notes.NotesSheets
import com.bitavida.app.ui.notes.NotesViewModel

/** Selection is internal to the existing Plan state; it does not navigate away from Mi día. */
fun registerTodayMedication(
    viewModel: MedicalPlanViewModel,
    occurrence: UpcomingMedicationAdministration,
    outcome: MedicationIntakeOutcome,
) {
    val state = viewModel.state.value
    if (state.isLoading || state.isSaving || state.globalError != null ||
        state.medicationIntakeDraft != null || state.sensitiveConsentDialogVisible) return
    if (outcome !in setOf(MedicationIntakeOutcome.COMPLETED, MedicationIntakeOutcome.OMITTED)) return
    val plan = state.overview.medicationPlans.singleOrNull { it.id == occurrence.medicationPlanId } ?: return
    val occurrences = medicationOccurrencesForDay(plan, occurrence.startsAt.toLocalDate())
    if (occurrences.none { it.id == occurrence.id }) return
    if (intakesForOccurrence(state.overview.intakesFor(plan.id), occurrence, occurrences, plan).any { it.hasDeclaredResponse() }) return
    viewModel.onAction(MedicalPlanAction.OpenMedication(plan.id))
    viewModel.onAction(MedicalPlanAction.QuickRegisterMedicationIntake(occurrence, outcome, fromToday = true))
}

/** Opens the approved capture with this administration selected; it never saves on opening. */
fun openTodayMedicationRegistration(
    viewModel: MedicalPlanViewModel,
    occurrence: UpcomingMedicationAdministration,
    outcome: MedicationIntakeOutcome = MedicationIntakeOutcome.COMPLETED,
    expandNote: Boolean = false,
) {
    viewModel.onAction(MedicalPlanAction.OpenTodayMedicationIntake(occurrence, outcome, expandNote))
}

/** The receipt fixes both the record and revision; another medication's last action cannot be undone. */
fun undoTodayMedication(viewModel: MedicalPlanViewModel, receipt: TodayMedicationFeedback) {
    if (receipt.status != TodayMedicationFeedbackStatus.STORED) return
    val recordId = receipt.recordId ?: return
    val revision = receipt.revisionNumber ?: return
    viewModel.onAction(MedicalPlanAction.UndoTodayMedicationIntake(recordId, revision, receipt.occurrenceId))
}

/** Exact schedule identity/context are copied; the caregiver still enters every actual numeric value. */
fun openTodayMeasurementRegistration(
    kind: MeasurementKind,
    occurrence: GlucoseScheduledOccurrence,
    glucoseViewModel: GlucoseViewModel,
    bloodPressureViewModel: BloodPressureViewModel,
    weightViewModel: WeightViewModel,
) {
    when (kind) {
        MeasurementKind.CAPILLARY_GLUCOSE -> {
            val state = glucoseViewModel.state.value
            if (state.saving || state.occurrenceDecisionSaving || !measurementStillPending(occurrence,
                    state.records.mapNotNull { it.scheduledOccurrenceId() }, state.occurrenceDecisions.map { it.occurrenceId })) return
            glucoseViewModel.onAction(GlucoseAction.OpenOccurrence(occurrence))
            glucoseViewModel.onAction(GlucoseAction.RegisterSelectedOccurrence)
        }
        MeasurementKind.BLOOD_PRESSURE -> {
            val state = bloodPressureViewModel.state.value
            if (state.saving || state.occurrenceDecisionSaving || !measurementStillPending(occurrence,
                    state.records.mapNotNull { it.scheduledOccurrenceId() }, state.occurrenceDecisions.map { it.occurrenceId })) return
            bloodPressureViewModel.onAction(BloodPressureAction.OpenOccurrence(occurrence))
            bloodPressureViewModel.onAction(BloodPressureAction.RegisterSelectedOccurrence)
        }
        MeasurementKind.WEIGHT -> {
            val state = weightViewModel.state.value
            if (state.saving || state.occurrenceDecisionSaving || !measurementStillPending(occurrence,
                    state.records.mapNotNull { it.scheduledOccurrenceId() }, state.occurrenceDecisions.map { it.occurrenceId })) return
            weightViewModel.onAction(WeightAction.OpenOccurrence(occurrence))
            weightViewModel.onAction(WeightAction.RegisterSelectedOccurrence)
        }
    }
}

/** Opens the approved reason/declaration step directly; never persists an omission on the first tap. */
fun openTodayMeasurementNotPerformed(
    kind: MeasurementKind,
    occurrence: GlucoseScheduledOccurrence,
    glucoseViewModel: GlucoseViewModel,
    bloodPressureViewModel: BloodPressureViewModel,
    weightViewModel: WeightViewModel,
) {
    when (kind) {
        MeasurementKind.CAPILLARY_GLUCOSE -> {
            val state = glucoseViewModel.state.value
            if (state.loading || state.loadFailed || state.draft != null || state.saving || state.occurrenceDecisionSaving ||
                !measurementStillPending(occurrence, state.records.mapNotNull { it.scheduledOccurrenceId() },
                    state.occurrenceDecisions.map { it.occurrenceId })) return
            glucoseViewModel.onAction(GlucoseAction.OpenOccurrence(occurrence))
        }
        MeasurementKind.BLOOD_PRESSURE -> {
            val state = bloodPressureViewModel.state.value
            if (state.loading || state.loadFailed || state.draft != null || state.saving || state.occurrenceDecisionSaving ||
                !measurementStillPending(occurrence, state.records.mapNotNull { it.scheduledOccurrenceId() },
                    state.occurrenceDecisions.map { it.occurrenceId })) return
            bloodPressureViewModel.onAction(BloodPressureAction.OpenOccurrence(occurrence))
        }
        MeasurementKind.WEIGHT -> {
            val state = weightViewModel.state.value
            if (state.loading || state.loadFailed || state.draft != null || state.saving || state.occurrenceDecisionSaving ||
                !measurementStillPending(occurrence, state.records.mapNotNull { it.scheduledOccurrenceId() },
                    state.occurrenceDecisions.map { it.occurrenceId })) return
            weightViewModel.onAction(WeightAction.OpenOccurrence(occurrence))
        }
    }
}

private fun measurementStillPending(
    occurrence: GlucoseScheduledOccurrence,
    recorded: List<String>,
    declared: List<String>,
): Boolean = occurrence.id !in recorded && occurrence.id !in declared

/** Only hosts existing surfaces. Shared module states remain the sole source of capture and results. */
@Composable
fun TodayQuickForms(
    planViewModel: MedicalPlanViewModel,
    glucoseViewModel: GlucoseViewModel,
    bloodPressureViewModel: BloodPressureViewModel,
    weightViewModel: WeightViewModel,
    notesViewModel: NotesViewModel,
    motionAllowed: Boolean,
) {
    val plan by planViewModel.state.collectAsState()
    val glucose by glucoseViewModel.state.collectAsState()
    val pressure by bloodPressureViewModel.state.collectAsState()
    val weight by weightViewModel.state.collectAsState()
    val notes by notesViewModel.state.collectAsState()

    // A committed declaration leaves Mi día immediately; history stays in Mediciones.
    LaunchedEffect(glucose.selectedOccurrence?.id, glucose.selectedOccurrenceDecision?.id) {
        if (glucose.selectedOccurrence != null && glucose.selectedOccurrenceDecision != null)
            glucoseViewModel.onAction(GlucoseAction.DismissOccurrence)
    }
    LaunchedEffect(pressure.selectedOccurrence?.id, pressure.selectedOccurrenceDecision?.id) {
        if (pressure.selectedOccurrence != null && pressure.selectedOccurrenceDecision != null)
            bloodPressureViewModel.onAction(BloodPressureAction.DismissOccurrence)
    }
    LaunchedEffect(weight.selectedOccurrence?.id, weight.selectedOccurrenceDecision?.id) {
        if (weight.selectedOccurrence != null && weight.selectedOccurrenceDecision != null)
            weightViewModel.onAction(WeightAction.DismissOccurrence)
    }

    plan.medicationIntakeDraft?.takeIf { !it.inlineEntry || plan.fieldErrors.isNotEmpty() }?.let { draft ->
        plan.selectedMedicationPlan?.let { medication ->
            MedicationIntakeSheet(
                draft, medication, plan.fieldErrors, plan.isSaving, motionAllowed,
                onChange = { planViewModel.onAction(MedicalPlanAction.MedicationIntakeDraftChanged(it)) },
                onConfirm = { planViewModel.onAction(MedicalPlanAction.SaveMedicationIntake) },
                onDismiss = { planViewModel.onAction(MedicalPlanAction.DismissMedicationIntake) },
            )
        }
    }
    MedicationSensitiveConsentDialog(plan, planViewModel::onAction)
    when {
        glucose.draft != null -> GlucoseRegistrationSheet(glucose, motionAllowed, glucoseViewModel::onAction)
        pressure.draft != null -> BloodPressureRegistrationSheet(pressure, motionAllowed, bloodPressureViewModel::onAction)
        weight.draft != null -> WeightRegistrationSheet(weight, motionAllowed, weightViewModel::onAction)
        glucose.selectedOccurrence != null && glucose.selectedOccurrenceDecision == null ->
            GlucoseOccurrenceManagementSheet(glucose, motionAllowed, glucoseViewModel::onAction, startNotPerformed = true)
        pressure.selectedOccurrence != null && pressure.selectedOccurrenceDecision == null ->
            BloodPressureOccurrenceManagementSheet(pressure, motionAllowed, bloodPressureViewModel::onAction, startNotPerformed = true)
        weight.selectedOccurrence != null && weight.selectedOccurrenceDecision == null ->
            WeightOccurrenceManagementSheet(weight, motionAllowed, weightViewModel::onAction, startNotPerformed = true)
    }
    NotesPhotoCoordinator(notesViewModel)
    NotesSheets(notes, notesViewModel::onAction, notesViewModel::imageBytes)
}
