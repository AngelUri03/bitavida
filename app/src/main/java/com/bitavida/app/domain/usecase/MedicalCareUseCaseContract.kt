package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicalIndicationStatus
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import java.time.LocalDate
import java.time.LocalTime

enum class MedicalCareField {
    FICTITIOUS_DATA_ACKNOWLEDGEMENT,
    ORIGINAL_INDICATION,
    INDICATED_ON,
    DECLARED_SOURCE,
    VALIDITY,
    CHANGE_REASON,
    MEDICATION_NAME,
    DOSE_AS_INDICATED,
    UNIT_OR_PRESENTATION,
    FREQUENCY_AS_INDICATED,
    MEDICATION_DATES,
    ADDITIONAL_INSTRUCTIONS,
    EXPLICIT_SCHEDULES,
    INTAKE_OUTCOME,
    INTAKE_ACTUAL_TIME,
    INTAKE_AMOUNT,
    INTAKE_OBSERVATION,
}

enum class MedicalCareValidationIssue {
    REQUIRED,
    TOO_LONG,
    INVALID_DATE_RANGE,
    DUPLICATE_SCHEDULE,
    OVERLAPPING_PERIOD,
    REAL_CLINICAL_CAPTURE_BLOCKED,
    INVALID_VALUE,
}

data class MedicalCareValidationError(
    val field: MedicalCareField,
    val issue: MedicalCareValidationIssue,
)

sealed interface MedicalCareMutationResult<out T> {
    data class Stored<T>(val value: T) : MedicalCareMutationResult<T>
    data class Invalid(
        val errors: Set<MedicalCareValidationError>,
    ) : MedicalCareMutationResult<Nothing>
    data object NotFound : MedicalCareMutationResult<Nothing>
}

data class RegisterMedicalIndicationCommand(
    val personUnderCareId: String,
    val authorActorId: String,
    val originalText: String,
    val indicatedOn: LocalDate,
    val declaredProfessionalOrSource: String?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val contentMode: ClinicalContentMode,
    val fictitiousDataAcknowledged: Boolean,
)

data class ReviseMedicalIndicationCommand(
    val indicationId: String,
    val personUnderCareId: String,
    val authorActorId: String,
    val originalText: String,
    val indicatedOn: LocalDate,
    val declaredProfessionalOrSource: String?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val changeReason: String,
    val contentMode: ClinicalContentMode,
    val fictitiousDataAcknowledged: Boolean,
)

data class ChangeMedicalIndicationStatusCommand(
    val indicationId: String,
    val personUnderCareId: String,
    val authorActorId: String,
    val targetStatus: MedicalIndicationStatus,
    val changeReason: String,
    val contentMode: ClinicalContentMode,
    val fictitiousDataAcknowledged: Boolean,
)

data class RegisterMedicationPlanCommand(
    val medicalIndicationId: String?,
    val personUnderCareId: String,
    val authorActorId: String,
    val medicationName: String,
    val doseAsIndicated: String,
    val unitOrPresentation: String?,
    val frequencyAsIndicated: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val originalAdditionalInstructions: String?,
    val explicitSchedules: List<LocalTime>,
    val contentMode: ClinicalContentMode,
    val fictitiousDataAcknowledged: Boolean,
    val configuration: MedicationConfiguration? = null,
    val sourceMedicationPlanId: String? = null,
    val careProfileId: String? = null,
    val sensitiveConsentVersion: String? = null,
)

data class ReviseMedicationPlanCommand(
    val medicationPlanId: String,
    val personUnderCareId: String,
    val authorActorId: String,
    val medicationName: String,
    val doseAsIndicated: String,
    val unitOrPresentation: String?,
    val frequencyAsIndicated: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val originalAdditionalInstructions: String?,
    val explicitSchedules: List<LocalTime>,
    val changeReason: String,
    val contentMode: ClinicalContentMode,
    val fictitiousDataAcknowledged: Boolean,
    val configuration: MedicationConfiguration? = null,
    val careProfileId: String? = null,
    val sensitiveConsentVersion: String? = null,
    val changeKind: MedicationPlanChangeKind =
        MedicationPlanChangeKind.CORRECTED,
)
