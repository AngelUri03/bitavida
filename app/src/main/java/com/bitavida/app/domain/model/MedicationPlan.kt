package com.bitavida.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class MedicationAmount(
    val value: String,
    val unit: String,
) {
    init {
        require(value.isNotBlank() && value.length <= 40)
        require(unit.isNotBlank() && unit.length <= 80)
    }
}

enum class MedicationProductForm {
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

enum class MedicationFrequencyKind {
    EVERY_DAY,
    SPECIFIC_DAYS,
    EVERY_N_DAYS,
    EVERY_N_HOURS,
    CYCLE,
    AS_NEEDED,
}

enum class MedicationHourlyIntervalMode {
    CONTINUOUS,
    DAILY_WINDOW,
}

enum class MedicationTreatmentStatus {
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    REMOVED,
}

enum class MedicationReminderPreference {
    NONE,
    NORMAL,
    PROMINENT,
}

enum class MedicationReminderEffectiveState {
    NOT_REQUESTED,
    PREFERENCE_RECORDED_NOT_SCHEDULED,
}

enum class MedicationAdministrationTiming {
    EXACT_TIME,
    TIME_WINDOW,
    ACTIVITY_ONLY,
}

enum class MedicationActivityRelation {
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

data class MedicationAdministration(
    val id: String,
    val timing: MedicationAdministrationTiming,
    val exactTime: LocalTime? = null,
    val windowStart: LocalTime? = null,
    val windowEnd: LocalTime? = null,
    val windowEndsNextDay: Boolean = false,
    val noticeTime: LocalTime? = null,
    val noticeNextDay: Boolean = false,
    /** Null keeps the pre-MED-04B.2 behavior; exact administrations were included. */
    val reminderEnabled: Boolean? = null,
    val amountOverride: MedicationAmount? = null,
    val activityRelation: MedicationActivityRelation? = null,
    val customActivity: String? = null,
    val activityNoticeTime: LocalTime? = null,
) {
    init {
        require(id.isNotBlank())
        require(
            (timing == MedicationAdministrationTiming.EXACT_TIME &&
                exactTime != null && windowStart == null && windowEnd == null) ||
                (timing == MedicationAdministrationTiming.TIME_WINDOW &&
                    exactTime == null && windowStart != null && windowEnd != null) ||
                (timing == MedicationAdministrationTiming.ACTIVITY_ONLY &&
                    exactTime == null && windowStart == null && windowEnd == null),
        )
        require(customActivity == null || customActivity.length <= 160)
        require(
            activityRelation == MedicationActivityRelation.OTHER ||
                customActivity.isNullOrBlank(),
        )
    }
}

data class MedicationCycle(
    val activeDays: Int,
    val restDays: Int,
    val referenceDate: LocalDate,
    val numberOfCycles: Int? = null,
) {
    init {
        require(activeDays > 0)
        require(restDays > 0)
        require(numberOfCycles == null || numberOfCycles > 0)
    }
}

data class MedicationConfiguration(
    val form: MedicationProductForm,
    val concentrationValue: String?,
    val concentrationUnit: String?,
    val baseAmount: MedicationAmount,
    val frequency: MedicationFrequencyKind,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val everyNDaysInterval: Int? = null,
    val everyNDaysReferenceDate: LocalDate? = null,
    val hourlyInterval: Int? = null,
    val hourlyMode: MedicationHourlyIntervalMode? = null,
    val hourlyReferenceDate: LocalDate? = null,
    val hourlyReferenceTime: LocalTime? = null,
    val hourlyWindowStart: LocalTime? = null,
    val hourlyWindowEnd: LocalTime? = null,
    val hourlyWindowEndsNextDay: Boolean = false,
    val hourlyFirstAdministration: LocalTime? = null,
    val cycle: MedicationCycle? = null,
    val asNeededInstruction: String? = null,
    val asNeededRestrictions: String? = null,
    val startsOn: LocalDate,
    val endsOn: LocalDate? = null,
    val status: MedicationTreatmentStatus = MedicationTreatmentStatus.ACTIVE,
    val reminderPreference: MedicationReminderPreference =
        MedicationReminderPreference.NONE,
    val secondReminderEnabled: Boolean = false,
    val reminderEffectiveState: MedicationReminderEffectiveState =
        MedicationReminderEffectiveState.NOT_REQUESTED,
    val administrations: List<MedicationAdministration> = emptyList(),
) {
    init {
        require(concentrationValue == null || concentrationValue.length <= 80)
        require(concentrationUnit == null || concentrationUnit.length <= 80)
        require(endsOn == null || !endsOn.isBefore(startsOn))
        require(asNeededInstruction == null || asNeededInstruction.length <= 500)
        require(asNeededRestrictions == null || asNeededRestrictions.length <= 500)
        require(
            reminderPreference != MedicationReminderPreference.NONE ||
                !secondReminderEnabled,
        )
        require(administrations.map { it.id }.distinct().size == administrations.size)
        require(
            frequency == MedicationFrequencyKind.AS_NEEDED ||
                administrations.isNotEmpty() ||
                frequency == MedicationFrequencyKind.EVERY_N_HOURS,
        )
    }
}

data class ScheduledDose(
    val id: String,
    val medicationPlanRevisionId: String,
    val time: LocalTime,
    val configuredAtEpochMillis: Long,
    val configuredByActorId: String,
    val origin: InformationOrigin,
) {
    init {
        require(id.isNotBlank())
        require(medicationPlanRevisionId.isNotBlank())
        require(configuredAtEpochMillis >= 0L)
        require(configuredByActorId.isNotBlank())
        require(origin == InformationOrigin.CAREGIVER_RECORD)
    }
}

enum class MedicationPlanChangeKind {
    CREATED,
    CORRECTED,
    BASE_DETAILS_CORRECTED,
    SCHEDULE_CORRECTED,
    SUSPENDED,
    REACTIVATED,
    REACTIVATION_RESCHEDULED,
    REACTIVATION_CANCELLED,
    COMPLETED,
    REMOVED,
}

data class MedicationPlanRevision(
    val id: String,
    val medicationPlanId: String,
    val revisionNumber: Int,
    val medicationName: String,
    val doseAsIndicated: String,
    val unitOrPresentation: String?,
    val frequencyAsIndicated: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val originalAdditionalInstructions: String?,
    val scheduledDoses: List<ScheduledDose>,
    val capturedAtEpochMillis: Long,
    val authorActorId: String,
    val changeKind: MedicationPlanChangeKind,
    val changeReason: String?,
    val lifecycleEffectiveAtEpochMillis: Long? = null,
    val configuration: MedicationConfiguration? = null,
) {
    init {
        require(id.isNotBlank())
        require(medicationPlanId.isNotBlank())
        require(revisionNumber > 0)
        require(medicationName.isNotBlank() && medicationName.length <= 160)
        require(doseAsIndicated.isNotBlank() && doseAsIndicated.length <= 240)
        require(unitOrPresentation == null || unitOrPresentation.length <= 160)
        require(frequencyAsIndicated == null || frequencyAsIndicated.length <= 240)
        require(endsOn == null || !endsOn.isBefore(startsOn))
        require(originalAdditionalInstructions == null || originalAdditionalInstructions.length <= 2_000)
        require(configuration != null || scheduledDoses.isNotEmpty())
        require(scheduledDoses.all { it.medicationPlanRevisionId == id })
        require(scheduledDoses.map { it.time }.distinct().size == scheduledDoses.size)
        require(capturedAtEpochMillis >= 0L)
        require(authorActorId.isNotBlank())
        require(
            changeKind in setOf(
                MedicationPlanChangeKind.CREATED,
                MedicationPlanChangeKind.REACTIVATED,
                MedicationPlanChangeKind.REACTIVATION_RESCHEDULED,
                MedicationPlanChangeKind.REACTIVATION_CANCELLED,
            ) || !changeReason.isNullOrBlank(),
        )
        require(
            lifecycleEffectiveAtEpochMillis == null ||
                lifecycleEffectiveAtEpochMillis >= 0L,
        )
    }
}

data class MedicationPlan(
    val id: String,
    val medicationSeriesId: String = id,
    val personUnderCareId: String,
    val medicalIndicationId: String?,
    val medicalContentOrigin: InformationOrigin,
    val scheduleOrigin: InformationOrigin,
    val createdAtEpochMillis: Long,
    val createdByActorId: String,
    val contentMode: ClinicalContentMode,
    val revisions: List<MedicationPlanRevision>,
) {
    init {
        require(id.isNotBlank())
        require(medicationSeriesId.isNotBlank())
        require(personUnderCareId.isNotBlank())
        require(medicalIndicationId == null || medicalIndicationId.isNotBlank())
        require(medicalContentOrigin == InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION)
        require(scheduleOrigin == InformationOrigin.CAREGIVER_RECORD)
        require(createdAtEpochMillis >= 0L)
        require(createdByActorId.isNotBlank())
        require(revisions.isNotEmpty())
        require(revisions.all { it.medicationPlanId == id })
        require(revisions.map { it.revisionNumber } == (1..revisions.size).toList())
    }

    val currentRevision: MedicationPlanRevision
        get() = revisions.last()
}

data class MedicalCareOverview(
    val indications: List<MedicalIndication>,
    val medicationPlans: List<MedicationPlan>,
    val medicationIntakes: List<MedicationIntakeRecord> = emptyList(),
) {
    fun plansFor(indicationId: String): List<MedicationPlan> =
        medicationPlans.filter { it.medicalIndicationId == indicationId }

    fun intakesFor(medicationPlanId: String): List<MedicationIntakeRecord> =
        medicationIntakes.filter { it.medicationPlanId == medicationPlanId }
}
