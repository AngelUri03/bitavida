package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndicationStatus
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.identityPlansFor
import com.bitavida.app.domain.usecase.MedicalCareField
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class MedicalPlanScreen {
    OVERVIEW,
    SECTION,
    INDICATION_EDITOR,
    INDICATION_DETAIL,
    INDICATION_HISTORY,
    MEDICATION_EDITOR,
    MEDICATION_DETAIL,
    MEDICATION_MANAGEMENT,
    MEDICATION_HISTORY,
    MEDICATION_REGISTRATION,
}

enum class PlanSection {
    MEDICATIONS,
    MEASUREMENTS,
    OBSERVATIONS,
    CARE_TASKS,
    REMINDERS,
    OTHER_INDICATIONS,
}

data class IndicationDraft(
    val originalText: String = "",
    val indicatedOn: String = "",
    val declaredProfessionalOrSource: String = "",
    val validFrom: String = "",
    val validUntil: String = "",
    val changeReason: String = "",
    val fictitiousDataAcknowledged: Boolean = false,
)

data class MedicationDraft(
    val medicationName: String = "",
    val doseAsIndicated: String = "",
    val unitOrPresentation: String = "",
    val frequencyAsIndicated: String = "",
    val startsOn: String = "",
    val endsOn: String = "",
    val originalAdditionalInstructions: String = "",
    val explicitSchedules: String = "",
    val changeReason: String = "",
    val fictitiousDataAcknowledged: Boolean = false,
    val form: MedicationForm? = null,
    val concentrationValue: String = "",
    val concentrationUnit: String = "",
    val baseAmountValue: String = "",
    val baseAmountUnit: String = "",
)

enum class MedicationRegistrationStep {
    NAME,
    FORM,
    DETAILS,
    SCHEDULE,
    REMINDERS,
    REVIEW,
}

enum class MedicationEditPurpose {
    CORRECTION,
    INDICATION_UPDATE,
}

enum class MedicationScheduleSubstep {
    FREQUENCY,
    PARAMETERS,
    ADMINISTRATIONS,
    VALIDITY,
}

enum class MedicationForm {
    TABLET,
    CAPSULE,
    ORAL_LIQUID,
    DROPS,
    INJECTABLE,
    INHALER,
    TOPICAL,
    PATCH,
    POWDER,
    OTHER,
}

enum class MedicationRegistrationField {
    NAME,
    FORM,
    CONCENTRATION_VALUE,
    CONCENTRATION_UNIT,
    AMOUNT_VALUE,
    AMOUNT_UNIT,
    FREQUENCY,
    WEEKDAYS,
    ADMINISTRATIONS,
    INTERVAL_DAYS,
    REFERENCE_DATE,
    INTERVAL_HOURS,
    HOURLY_MODE,
    HOURLY_REFERENCE_DATE,
    HOURLY_REFERENCE_TIME,
    HOURLY_WINDOW,
    HOURLY_FIRST_ADMINISTRATION,
    HOURLY_APPLICABLE_DAYS,
    CYCLE_ACTIVE_DAYS,
    CYCLE_REST_DAYS,
    CYCLE_REFERENCE_DATE,
    AS_NEEDED_INSTRUCTION,
    TREATMENT_START,
    TREATMENT_END,
    REMINDER_PREFERENCE,
}

enum class MedicationFrequency {
    EVERY_DAY,
    SPECIFIC_DAYS,
    EVERY_N_DAYS,
    EVERY_N_HOURS,
    CYCLE,
    AS_NEEDED,
}

enum class MedicationApplicableDaysMode {
    EVERY_DAY,
    SPECIFIC_DAYS,
}

enum class MedicationHourlyMode {
    CONTINUOUS,
    DAILY_WINDOW,
}

enum class MedicationTreatmentStartMode {
    TODAY,
    SPECIFIC_DATE,
}

enum class MedicationReminderOption {
    NONE,
    NORMAL,
    PROMINENT,
}

enum class MedicationActivityOption {
    BEFORE_BREAKFAST,
    WITH_BREAKFAST,
    AFTER_BREAKFAST,
    BEFORE_LUNCH,
    WITH_LUNCH,
    AFTER_LUNCH,
    BEFORE_DINNER,
    WITH_DINNER,
    AFTER_DINNER,
    BEFORE_SLEEP,
    OTHER,
}

enum class MedicationWeekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

enum class MedicationTimingType {
    EXACT_TIME,
    TIME_WINDOW,
    ACTIVITY_ONLY,
}

data class MedicationTime(
    val hour: Int,
    val minute: Int,
) : Comparable<MedicationTime> {
    val minuteOfDay: Int
        get() = (hour * 60) + minute

    override fun compareTo(other: MedicationTime): Int =
        minuteOfDay.compareTo(other.minuteOfDay)
}

data class MedicationAdministrationAmount(
    val value: String,
    val unit: String,
)

data class MedicationAdministrationDraft(
    val id: Int,
    val persistedId: String? = null,
    val timingType: MedicationTimingType,
    val exactTime: MedicationTime? = null,
    val windowStart: MedicationTime? = null,
    val windowEnd: MedicationTime? = null,
    val reminderTime: MedicationTime? = null,
    val endsNextDay: Boolean = false,
    val reminderNextDay: Boolean = false,
    val exactReminderEnabled: Boolean = true,
    val amountOverride: MedicationAdministrationAmount? = null,
    val activity: MedicationActivityOption? = null,
    val customActivity: String = "",
    val activityNoticeTime: MedicationTime? = null,
)

data class MedicationWindowEditor(
    val editingAdministrationId: Int? = null,
    val start: MedicationTime? = null,
    val end: MedicationTime? = null,
    val reminder: MedicationTime? = null,
    val endsNextDay: Boolean = false,
    val reminderNextDay: Boolean = false,
)

enum class MedicationScheduleError {
    DUPLICATE_TIME,
    INCOMPLETE_WINDOW,
    INVALID_WINDOW,
    END_BEFORE_START_REQUIRES_NEXT_DAY,
    REMINDER_OUTSIDE_WINDOW,
    OVERLAPPING_SCHEDULE,
}

data class GuidedMedicationDraft(
    val medicationName: String = "",
    val form: MedicationForm? = null,
    val concentrationValue: String = "",
    val concentrationUnit: String = "",
    val customConcentrationUnit: String = "",
    val alternativePresentation: String = "",
    val amountValue: String = "",
    val amountUnit: String = "",
    val customAmountUnit: String = "",
    val isConcentrationExpanded: Boolean = false,
    val frequency: MedicationFrequency? = null,
    val selectedWeekdays: Set<MedicationWeekday> = emptySet(),
    val everyNDaysInterval: String = "",
    val everyNDaysReferenceDate: LocalDate? = null,
    val hourlyInterval: String = "",
    val hourlyMode: MedicationHourlyMode? = null,
    val hourlyReferenceDate: LocalDate? = null,
    val hourlyReferenceTime: MedicationTime? = null,
    val hourlyWindowStart: MedicationTime? = null,
    val hourlyWindowEnd: MedicationTime? = null,
    val hourlyWindowEndsNextDay: Boolean = false,
    val hourlyFirstAdministration: MedicationTime? = null,
    val hourlyApplicableDaysMode: MedicationApplicableDaysMode? = null,
    val hourlySelectedWeekdays: Set<MedicationWeekday> = emptySet(),
    val cycleActiveDays: String = "",
    val cycleRestDays: String = "",
    val cycleReferenceDate: LocalDate? = null,
    val cycleCount: String = "",
    val asNeededInstruction: String = "",
    val asNeededRestrictions: String = "",
    val treatmentStartMode: MedicationTreatmentStartMode? = null,
    val treatmentStartDate: LocalDate? = null,
    val treatmentEndDate: LocalDate? = null,
    val reminderOption: MedicationReminderOption? = null,
    val secondReminderEnabled: Boolean = false,
    val timingTypesByFrequency: Map<MedicationFrequency, MedicationTimingType> =
        emptyMap(),
    val administrationsByFrequency:
        Map<MedicationFrequency, List<MedicationAdministrationDraft>> = emptyMap(),
    val windowEditorsByFrequency: Map<MedicationFrequency, MedicationWindowEditor> =
        emptyMap(),
    val nextAdministrationId: Int = 1,
)

data class RemovedMedicationAdministration(
    val frequency: MedicationFrequency,
    val administration: MedicationAdministrationDraft,
)

internal fun String.isPositiveMedicationAmountText(): Boolean {
    val normalized = trim().replace(',', '.')
    if (normalized.isEmpty()) return false
    val fractionParts = normalized.split('/')
    return when (fractionParts.size) {
        1 -> normalized.toBigDecimalOrNull()?.signum() == 1
        2 -> {
            val numerator = fractionParts[0].trim().toBigDecimalOrNull()
            val denominator = fractionParts[1].trim().toBigDecimalOrNull()
            numerator?.signum() == 1 && denominator?.signum() == 1
        }
        else -> false
    }
}

data class StatusChangeDraft(
    val targetStatus: MedicalIndicationStatus,
    val reason: String = "",
    val fictitiousDataAcknowledged: Boolean = false,
)

enum class MedicationReactivationTiming {
    NOW,
    SCHEDULED,
}

data class MedicationLifecycleDraft(
    val targetStatus: MedicationTreatmentStatus,
    val effectiveOn: String = LocalDate.now().toString(),
    val effectiveTime: String = LocalTime.now()
        .withSecond(0)
        .withNano(0)
        .toString(),
    val reactivationTiming: MedicationReactivationTiming =
        MedicationReactivationTiming.NOW,
    val reason: String = "",
)

data class MedicationIntakeDraft(
    val recordId: String? = null,
    /** One identity per explicit registration action; retained across draft edits and retries. */
    val operationId: String = java.util.UUID.randomUUID().toString(),
    val outcome: MedicationIntakeOutcome = MedicationIntakeOutcome.COMPLETED,
    val outcomeExplicitlySelected: Boolean = true,
    val scheduledDate: String = LocalDate.now().toString(),
    val scheduledTime: String = "",
    val actualDate: String = LocalDate.now().toString(),
    val actualTime: String = "",
    val actualAmountValue: String = "",
    val actualAmountUnit: String = "",
    val observation: String = "",
    val reason: String = "",
    val quickEntry: Boolean = false,
    val undoEntry: Boolean = false,
    val scheduledOccurrenceKnown: Boolean = false,
    val inlineEntry: Boolean = false,
    val asNeededEntry: Boolean = false,
    val scheduledOccurrenceId: String? = null,
    val fromToday: Boolean = false,
    val expandNoteOnOpen: Boolean = false,
    val expectedRevisionNumber: Int? = null,
    val sourceMedicationPlanRevisionId: String? = null,
)

enum class TodayMedicationFeedbackStatus {
    SAVING, AWAITING_CONSENT, STORED, UNDONE, FAILED,
}

/** Receipt of a confirmed local mutation; it never substitutes the persisted intake record. */
data class TodayMedicationFeedback(
    val requestId: String,
    val occurrenceId: String,
    val medicationName: String,
    val outcome: MedicationIntakeOutcome,
    val status: TodayMedicationFeedbackStatus,
    val recordId: String? = null,
    val revisionNumber: Int? = null,
    val actualAt: LocalDateTime? = null,
    val message: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class MedicalPlanUiState(
    val screen: MedicalPlanScreen = MedicalPlanScreen.OVERVIEW,
    val selectedSection: PlanSection? = null,
    val overview: MedicalCareOverview = MedicalCareOverview(emptyList(), emptyList()),
    val selectedIndicationId: String? = null,
    val selectedMedicationPlanId: String? = null,
    val editingExisting: Boolean = false,
    val newMedicationPeriodSourcePlanId: String? = null,
    val medicationEditPurpose: MedicationEditPurpose? = null,
    val originalGuidedMedicationDraft: GuidedMedicationDraft? = null,
    val editorEntryGuidedMedicationDraft: GuidedMedicationDraft? = null,
    val indicationDraft: IndicationDraft = IndicationDraft(),
    val medicationDraft: MedicationDraft = MedicationDraft(),
    val medicationRegistrationStep: MedicationRegistrationStep =
        MedicationRegistrationStep.NAME,
    val medicationScheduleSubstep: MedicationScheduleSubstep =
        MedicationScheduleSubstep.FREQUENCY,
    val guidedMedicationDraft: GuidedMedicationDraft = GuidedMedicationDraft(),
    val medicationRegistrationErrors: Set<MedicationRegistrationField> = emptySet(),
    val medicationRegistrationSaveError: String? = null,
    val medicationScheduleError: MedicationScheduleError? = null,
    val recentlyRemovedMedicationAdministration:
        RemovedMedicationAdministration? = null,
    val medicationExitConfirmationVisible: Boolean = false,
    val sensitiveConsentDialogVisible: Boolean = false,
    val sensitiveConsentAccepted: Boolean = false,
    val statusChangeDraft: StatusChangeDraft? = null,
    val medicationLifecycleDraft: MedicationLifecycleDraft? = null,
    val resumeReactivationAfterEdit: Boolean = false,
    val medicationIntakeDraft: MedicationIntakeDraft? = null,
    val lastQuickMedicationIntakeId: String? = null,
    val todayMedicationFeedback: TodayMedicationFeedback? = null,
    val selectedMedicationTodayKey: String? = null,
    val medicationTodayDate: LocalDate? = null,
    val medicationChangesOnly: Boolean = false,
    val medicationHistoryMonth: LocalDate =
        LocalDate.now().withDayOfMonth(1),
    val selectedMedicationHistoryDate: LocalDate = LocalDate.now(),
    val fieldErrors: Set<MedicalCareField> = emptySet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val globalError: MedicalPlanGlobalError? = null,
    val effect: MedicalPlanEffect? = null,
) {
    val selectedIndication
        get() = overview.indications.singleOrNull { it.id == selectedIndicationId }

    val selectedMedicationPlan
        get() = overview.medicationPlans.singleOrNull { it.id == selectedMedicationPlanId }

    val selectedMedicationIdentityPlans
        get() = selectedMedicationPlan?.let {
            overview.medicationPlans.identityPlansFor(it)
        }.orEmpty()

    val hasMedicationChanges: Boolean
        get() = editingExisting &&
            originalGuidedMedicationDraft != null &&
            guidedMedicationDraft != originalGuidedMedicationDraft
}

enum class MedicalPlanGlobalError {
    STORAGE_UNAVAILABLE,
    INTEGRITY_ERROR,
    CONCURRENT_CHANGE,
    AMBIGUOUS_MEDICATION_OCCASION,
    RECORD_NOT_FOUND,
}

sealed interface MedicalPlanEffect {
    data object IndicationStored : MedicalPlanEffect
    data object MedicationStored : MedicalPlanEffect
    data object MedicationUpdated : MedicalPlanEffect
    data object StatusStored : MedicalPlanEffect
    data object MedicationLifecycleStored : MedicalPlanEffect
    data object MedicationIntakeStored : MedicalPlanEffect
}

sealed interface MedicalPlanAction {
    data object Retry : MedicalPlanAction
    data object Back : MedicalPlanAction
    data object OpenOverview : MedicalPlanAction
    data class OpenSection(val section: PlanSection) : MedicalPlanAction
    data object OpenCreateIndication : MedicalPlanAction
    data class OpenIndication(val id: String) : MedicalPlanAction
    data object OpenIndicationHistory : MedicalPlanAction
    data object OpenEditIndication : MedicalPlanAction
    data object OpenCreateMedication : MedicalPlanAction
    data object OpenMedicationRegistration : MedicalPlanAction
    data class OpenMedication(val id: String) : MedicalPlanAction
    data object OpenMedicationHistory : MedicalPlanAction
    data object OpenMedicationChanges : MedicalPlanAction
    data class SelectMedicationTodayRow(val key: String?, val day: LocalDate) : MedicalPlanAction
    data object OpenMedicationManagement : MedicalPlanAction
    data object OpenEditMedication : MedicalPlanAction
    data object OpenEditMedicationSchedule : MedicalPlanAction
    data object OpenCorrectMedication : MedicalPlanAction
    data object OpenUpdateMedicationIndication : MedicalPlanAction
    data object StartNewMedicationPeriod : MedicalPlanAction
    data object OpenCorrectMedicationFromReactivation : MedicalPlanAction
    data object OpenUpdateMedicationIndicationFromReactivation : MedicalPlanAction
    data object OpenRegisterMedicationIntake : MedicalPlanAction
    data class OpenMedicationIntakeOptions(
        val occurrence: UpcomingMedicationAdministration,
    ) : MedicalPlanAction
    data class QuickRegisterMedicationIntake(
        val occurrence: UpcomingMedicationAdministration,
        val outcome: MedicationIntakeOutcome,
        val fromToday: Boolean = false,
    ) : MedicalPlanAction
    data class OpenTodayMedicationIntake(
        val occurrence: UpcomingMedicationAdministration,
        val outcome: MedicationIntakeOutcome = MedicationIntakeOutcome.COMPLETED,
        val expandNote: Boolean = false,
    ) : MedicalPlanAction
    data class UndoTodayMedicationIntake(
        val recordId: String,
        val expectedRevisionNumber: Int,
        val occurrenceId: String,
    ) : MedicalPlanAction
    data class DismissTodayMedicationFeedback(val requestId: String) : MedicalPlanAction
    data object UndoQuickMedicationIntake : MedicalPlanAction
    data class OpenEditMedicationIntake(val recordId: String) : MedicalPlanAction
    data class MedicationIntakeDraftChanged(
        val draft: MedicationIntakeDraft,
    ) : MedicalPlanAction
    data object SaveMedicationIntake : MedicalPlanAction
    data object DismissMedicationIntake : MedicalPlanAction
    data class RequestMedicationLifecycle(
        val status: MedicationTreatmentStatus,
    ) : MedicalPlanAction
    data class MedicationLifecycleDraftChanged(
        val draft: MedicationLifecycleDraft,
    ) : MedicalPlanAction
    data object ConfirmMedicationLifecycle : MedicalPlanAction
    data object CancelScheduledMedicationReactivation : MedicalPlanAction
    data object DismissMedicationLifecycle : MedicalPlanAction
    data object PreviousMedicationHistoryWeek : MedicalPlanAction
    data object NextMedicationHistoryWeek : MedicalPlanAction
    data class SelectMedicationHistoryDate(
        val date: LocalDate,
    ) : MedicalPlanAction
    data class IndicationDraftChanged(val draft: IndicationDraft) : MedicalPlanAction
    data class MedicationDraftChanged(val draft: MedicationDraft) : MedicalPlanAction
    data class GuidedMedicationDraftChanged(
        val draft: GuidedMedicationDraft,
    ) : MedicalPlanAction
    data class ContinueMedicationRegistration(
        val expectedStep: MedicationRegistrationStep,
    ) : MedicalPlanAction
    data object PreviousMedicationRegistrationStep : MedicalPlanAction
    data class EditMedicationRegistrationStep(
        val step: MedicationRegistrationStep,
        val scheduleSubstep: MedicationScheduleSubstep =
            MedicationScheduleSubstep.FREQUENCY,
    ) : MedicalPlanAction
    data class UpsertExactMedicationAdministration(
        val frequency: MedicationFrequency,
        val editingAdministrationId: Int?,
        val time: MedicationTime,
    ) : MedicalPlanAction
    data class CommitMedicationWindow(
        val frequency: MedicationFrequency,
    ) : MedicalPlanAction
    data class AddActivityMedicationAdministration(
        val frequency: MedicationFrequency,
    ) : MedicalPlanAction
    data class EditMedicationWindow(
        val frequency: MedicationFrequency,
        val administrationId: Int,
    ) : MedicalPlanAction
    data class RemoveMedicationAdministration(
        val frequency: MedicationFrequency,
        val administrationId: Int,
    ) : MedicalPlanAction
    data object UndoRemoveMedicationAdministration : MedicalPlanAction
    data class UpdateMedicationAdministration(
        val frequency: MedicationFrequency,
        val administration: MedicationAdministrationDraft,
    ) : MedicalPlanAction
    data object SaveGuidedMedication : MedicalPlanAction
    data object CompleteGuidedMedicationSuccess : MedicalPlanAction
    data class SensitiveConsentChanged(val accepted: Boolean) : MedicalPlanAction
    data object ConfirmSensitiveConsent : MedicalPlanAction
    data object DeclineSensitiveConsent : MedicalPlanAction
    data object ContinueEditingMedicationRegistration : MedicalPlanAction
    data object DiscardMedicationRegistration : MedicalPlanAction
    data object SaveIndication : MedicalPlanAction
    data object SaveMedication : MedicalPlanAction
    data class RequestStatusChange(
        val status: MedicalIndicationStatus,
    ) : MedicalPlanAction
    data class StatusChangeDraftChanged(
        val draft: StatusChangeDraft,
    ) : MedicalPlanAction
    data object ConfirmStatusChange : MedicalPlanAction
    data object DismissStatusChange : MedicalPlanAction
    data object EffectConsumed : MedicalPlanAction
}

internal fun LocalDate.toInputText(): String = toString()
