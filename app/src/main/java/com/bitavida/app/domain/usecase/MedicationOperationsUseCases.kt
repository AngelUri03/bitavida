package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationIntakeChangeKind
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationIntakeRevision
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.pendingReactivation
import com.bitavida.app.domain.model.reactivationIssue
import com.bitavida.app.domain.model.treatmentStatusAt
import com.bitavida.app.domain.repository.MedicalCareRepository
import com.bitavida.app.domain.repository.MedicalCareConcurrentChangeException
import com.bitavida.app.domain.validation.hasMedicationIntakeReason
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class ChangeMedicationLifecycleCommand(
    val medicationPlanId: String,
    val personUnderCareId: String,
    val authorActorId: String,
    val targetStatus: MedicationTreatmentStatus,
    val effectiveAt: LocalDateTime,
    val reason: String,
    val careProfileId: String? = null,
    val sensitiveConsentVersion: String? = null,
)

internal const val MEDICATION_INTAKE_OPERATION_PREFIX = "medication-intake-operation:"

data class RegisterMedicationIntakeCommand(
    val medicationPlanId: String,
    val personUnderCareId: String,
    val authorActorId: String,
    val outcome: MedicationIntakeOutcome,
    val scheduledAt: LocalDateTime,
    val actualAt: LocalDateTime?,
    val actualAmount: MedicationAmount?,
    val observation: String?,
    val careProfileId: String? = null,
    val sensitiveConsentVersion: String? = null,
    val scheduledOccurrenceId: String? = null,
    /** Stable UUID for one user action; retries reuse it, distinct actions create a new one. */
    val operationId: String? = null,
)

data class ReviseMedicationIntakeCommand(
    val intakeRecordId: String,
    val personUnderCareId: String,
    val authorActorId: String,
    val outcome: MedicationIntakeOutcome,
    val scheduledAt: LocalDateTime,
    val actualAt: LocalDateTime?,
    val actualAmount: MedicationAmount?,
    val observation: String?,
    val reason: String,
    val careProfileId: String? = null,
    val sensitiveConsentVersion: String? = null,
    val scheduledOccurrenceId: String? = null,
)

class ChangeMedicationLifecycleUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(
        command: ChangeMedicationLifecycleCommand,
    ): MedicalCareMutationResult<MedicationPlanRevision> {
        val overview = repository.load(command.personUnderCareId)
        val plan = overview.medicationPlans
            .singleOrNull { it.id == command.medicationPlanId }
            ?: return MedicalCareMutationResult.NotFound
        if (
            plan.contentMode ==
                com.bitavida.app.domain.model.ClinicalContentMode
                    .REAL_CLINICAL_DATA &&
            !repository.hasActiveSensitiveDataConsent(
                profileId = command.careProfileId.orEmpty(),
                caregiverId = command.authorActorId,
                personUnderCareId = command.personUnderCareId,
                documentVersion =
                    command.sensitiveConsentVersion.orEmpty(),
            )
        ) {
            return MedicalCareMutationResult.Invalid(
                realClinicalOperationBlocked(),
            )
        }
        val current = plan.currentRevision
        val currentConfiguration = current.configuration
            ?: return MedicalCareMutationResult.Invalid(
                setOf(
                    MedicalCareValidationError(
                        MedicalCareField.MEDICATION_DATES,
                        MedicalCareValidationIssue.INVALID_VALUE,
                    ),
                ),
            )
        val capturedAt = nowEpochMillis()
        val currentStatus = plan.treatmentStatusAt(capturedAt, zoneId)
        val pending = plan.pendingReactivation(capturedAt, zoneId)
        val cancellingScheduledReactivation =
            command.targetStatus == MedicationTreatmentStatus.SUSPENDED &&
                currentStatus == MedicationTreatmentStatus.SUSPENDED &&
                pending != null
        val reasonRequired =
            command.targetStatus != MedicationTreatmentStatus.ACTIVE &&
                !cancellingScheduledReactivation
        if (
            (reasonRequired && command.reason.isBlank()) ||
            command.reason.length > 500
        ) {
            return MedicalCareMutationResult.Invalid(
                setOf(
                    MedicalCareValidationError(
                        MedicalCareField.CHANGE_REASON,
                        if (command.reason.isBlank()) {
                            MedicalCareValidationIssue.REQUIRED
                        } else {
                            MedicalCareValidationIssue.TOO_LONG
                        },
                    ),
                ),
            )
        }
        val effectiveAt = command.effectiveAt.atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val validationErrors = buildSet {
            if (
                command.targetStatus == MedicationTreatmentStatus.ACTIVE &&
                effectiveAt < capturedAt - 60_000L
            ) {
                add(
                    MedicalCareValidationError(
                        MedicalCareField.MEDICATION_DATES,
                        MedicalCareValidationIssue.INVALID_DATE_RANGE,
                    ),
                )
            }
            if (
                command.targetStatus == MedicationTreatmentStatus.ACTIVE &&
                pending?.effectiveAtEpochMillis == effectiveAt
            ) {
                add(
                    MedicalCareValidationError(
                        MedicalCareField.VALIDITY,
                        MedicalCareValidationIssue.INVALID_VALUE,
                    ),
                )
            }
            if (
                command.targetStatus == MedicationTreatmentStatus.ACTIVE &&
                currentStatus != MedicationTreatmentStatus.SUSPENDED
            ) {
                add(
                    MedicalCareValidationError(
                        MedicalCareField.VALIDITY,
                        MedicalCareValidationIssue.INVALID_VALUE,
                    ),
                )
            }
            if (
                command.targetStatus == MedicationTreatmentStatus.SUSPENDED &&
                currentStatus == MedicationTreatmentStatus.SUSPENDED &&
                !cancellingScheduledReactivation
            ) {
                add(
                    MedicalCareValidationError(
                        MedicalCareField.VALIDITY,
                        MedicalCareValidationIssue.INVALID_VALUE,
                    ),
                )
            }
            if (command.targetStatus == MedicationTreatmentStatus.ACTIVE) {
                val indication = plan.medicalIndicationId?.let { indicationId ->
                    overview.indications.singleOrNull { it.id == indicationId }
                }
                if (plan.reactivationIssue(indication, command.effectiveAt) != null) {
                    add(
                        MedicalCareValidationError(
                            MedicalCareField.VALIDITY,
                            MedicalCareValidationIssue.INVALID_DATE_RANGE,
                        ),
                    )
                }
            }
        }
        if (validationErrors.isNotEmpty()) {
            return MedicalCareMutationResult.Invalid(validationErrors)
        }
        val status = command.targetStatus
        val kind = when (status) {
            MedicationTreatmentStatus.ACTIVE -> if (pending == null) {
                MedicationPlanChangeKind.REACTIVATED
            } else {
                MedicationPlanChangeKind.REACTIVATION_RESCHEDULED
            }
            MedicationTreatmentStatus.SUSPENDED ->
                if (cancellingScheduledReactivation) {
                    MedicationPlanChangeKind.REACTIVATION_CANCELLED
                } else {
                    MedicationPlanChangeKind.SUSPENDED
                }
            MedicationTreatmentStatus.COMPLETED ->
                MedicationPlanChangeKind.COMPLETED
            MedicationTreatmentStatus.REMOVED ->
                MedicationPlanChangeKind.REMOVED
        }
        val revisionId = newId()
        val revision = MedicationPlanRevision(
            id = revisionId,
            medicationPlanId = plan.id,
            revisionNumber = current.revisionNumber + 1,
            medicationName = current.medicationName,
            doseAsIndicated = current.doseAsIndicated,
            unitOrPresentation = current.unitOrPresentation,
            frequencyAsIndicated = current.frequencyAsIndicated,
            startsOn = current.startsOn,
            endsOn = current.endsOn,
            originalAdditionalInstructions =
                current.originalAdditionalInstructions,
            scheduledDoses = current.scheduledDoses.map {
                it.copy(
                    id = newId(),
                    medicationPlanRevisionId = revisionId,
                    configuredAtEpochMillis = capturedAt,
                    configuredByActorId = command.authorActorId,
                )
            },
            capturedAtEpochMillis = capturedAt,
            authorActorId = command.authorActorId,
            changeKind = kind,
            changeReason = command.reason.trim().ifBlank { null },
            lifecycleEffectiveAtEpochMillis = effectiveAt,
            configuration = currentConfiguration.copy(status = status),
        )
        repository.appendMedicationPlanRevision(
            medicationPlanId = plan.id,
            expectedRevisionNumber = current.revisionNumber,
            revision = revision,
        )
        return MedicalCareMutationResult.Stored(revision)
    }
}

class FinalizeExpiredMedicationPlansUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(personUnderCareId: String): Int {
        val now = nowEpochMillis()
        val overview = repository.load(personUnderCareId)
        var finalizedCount = 0
        overview.medicationPlans.forEach { plan ->
            val current = plan.currentRevision
            val configuration = current.configuration ?: return@forEach
            val endsOn = configuration.endsOn ?: current.endsOn ?: return@forEach
            val effectiveAt = endsOn.plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            if (now < effectiveAt) return@forEach
            if (
                plan.treatmentStatusAt(now, zoneId) in setOf(
                    MedicationTreatmentStatus.COMPLETED,
                    MedicationTreatmentStatus.REMOVED,
                )
            ) {
                return@forEach
            }
            val revisionId = newId()
            val revision = current.copy(
                id = revisionId,
                revisionNumber = current.revisionNumber + 1,
                scheduledDoses = current.scheduledDoses.map { dose ->
                    dose.copy(
                        id = newId(),
                        medicationPlanRevisionId = revisionId,
                        configuredAtEpochMillis = now,
                    )
                },
                capturedAtEpochMillis = now,
                changeKind = MedicationPlanChangeKind.COMPLETED,
                changeReason = AUTOMATIC_COMPLETION_REASON,
                lifecycleEffectiveAtEpochMillis = effectiveAt,
                configuration = configuration.copy(
                    status = MedicationTreatmentStatus.COMPLETED,
                ),
            )
            repository.appendMedicationPlanRevision(
                medicationPlanId = plan.id,
                expectedRevisionNumber = current.revisionNumber,
                revision = revision,
            )
            finalizedCount += 1
        }
        return finalizedCount
    }

    private companion object {
        const val AUTOMATIC_COMPLETION_REASON = "Vigencia concluida"
    }
}

class RegisterMedicationIntakeUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(
        command: RegisterMedicationIntakeCommand,
    ): MedicalCareMutationResult<MedicationIntakeRecord> {
        validateIntake(
            outcome = command.outcome,
            actualAt = command.actualAt,
            actualAmount = command.actualAmount,
            observation = command.observation,
        ).takeIf(Set<MedicalCareValidationError>::isNotEmpty)?.let {
            return MedicalCareMutationResult.Invalid(it)
        }
        val overview = repository.load(command.personUnderCareId)
        val plan = overview.medicationPlans
            .singleOrNull { it.id == command.medicationPlanId }
            ?: return MedicalCareMutationResult.NotFound
        if (
            plan.contentMode ==
                com.bitavida.app.domain.model.ClinicalContentMode
                    .REAL_CLINICAL_DATA &&
            !repository.hasActiveSensitiveDataConsent(
                profileId = command.careProfileId.orEmpty(),
                caregiverId = command.authorActorId,
                personUnderCareId = command.personUnderCareId,
                documentVersion =
                    command.sensitiveConsentVersion.orEmpty(),
            )
        ) {
            return MedicalCareMutationResult.Invalid(
                realClinicalOperationBlocked(),
            )
        }
        val asNeeded = plan.currentRevision.configuration?.frequency ==
            com.bitavida.app.domain.model.MedicationFrequencyKind.AS_NEEDED
        if (asNeeded && !hasMedicationIntakeReason(command.observation)) {
            return MedicalCareMutationResult.Invalid(setOf(
                MedicalCareValidationError(
                    MedicalCareField.INTAKE_OBSERVATION, MedicalCareValidationIssue.REQUIRED,
                ),
            ))
        }
        val operationId = command.operationId
        if (operationId != null && runCatching { UUID.fromString(operationId).toString() }.getOrNull() != operationId) {
            return MedicalCareMutationResult.Invalid(setOf(
                MedicalCareValidationError(MedicalCareField.VALIDITY, MedicalCareValidationIssue.INVALID_VALUE),
            ))
        }
        val stableRecordId = operationId?.let { MEDICATION_INTAKE_OPERATION_PREFIX + it }
        if (stableRecordId != null) {
            val existing = overview.medicationIntakes.firstOrNull { it.id == stableRecordId }
            if (existing != null) {
                val original = existing.revisions.first()
                fun LocalDateTime.minutePrecision() = withSecond(0).withNano(0)
                val same = existing.medicationPlanId == command.medicationPlanId &&
                    existing.createdByActorId == command.authorActorId && original.authorActorId == command.authorActorId &&
                    original.outcome == command.outcome &&
                    original.scheduledAt.minutePrecision() == command.scheduledAt.minutePrecision() &&
                    original.actualAt?.minutePrecision() == command.actualAt?.minutePrecision() &&
                    original.actualAmount == command.actualAmount &&
                    original.observation == command.observation.nullIfBlank() &&
                    original.scheduledOccurrenceId == command.scheduledOccurrenceId
                if (!same) return MedicalCareMutationResult.Invalid(setOf(
                    MedicalCareValidationError(MedicalCareField.VALIDITY, MedicalCareValidationIssue.INVALID_VALUE),
                ))
                return MedicalCareMutationResult.Stored(existing)
            }
        }
        // A shared occasion does not establish that two submissions are the same
        // operation: separate physical administrations may legitimately answer it.
        // Only an explicit operation ID can make a retry idempotent.
        val now = nowEpochMillis()
        val recordId = stableRecordId ?: newId()
        val revision = MedicationIntakeRevision(
            id = newId(),
            intakeRecordId = recordId,
            revisionNumber = 1,
            outcome = command.outcome,
            scheduledAt = command.scheduledAt,
            scheduledOccurrenceId = command.scheduledOccurrenceId,
            actualAt = command.actualAt,
            actualAmount = command.actualAmount,
            observation = command.observation.nullIfBlank(),
            changeKind = MedicationIntakeChangeKind.REGISTERED,
            changeReason = null,
            capturedAtEpochMillis = now,
            authorActorId = command.authorActorId,
        )
        val record = MedicationIntakeRecord(
            id = recordId,
            medicationPlanId = command.medicationPlanId,
            createdAtEpochMillis = now,
            createdByActorId = command.authorActorId,
            revisions = listOf(revision),
        )
        repository.createMedicationIntake(record)
        // The DAO may have observed a concurrent retry with the same operation ID.
        // Return the committed record rather than a freshly constructed timestamp.
        val committed = if (stableRecordId == null) record else repository.load(command.personUnderCareId)
            .medicationIntakes.single { it.id == stableRecordId }
        return MedicalCareMutationResult.Stored(committed)
    }
}

class ReviseMedicationIntakeUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(
        command: ReviseMedicationIntakeCommand,
        expectedRevisionNumber: Int? = null,
    ): MedicalCareMutationResult<MedicationIntakeRevision> {
        val errors = validateIntake(
            outcome = command.outcome,
            actualAt = command.actualAt,
            actualAmount = command.actualAmount,
            observation = command.observation,
        ) + when {
            command.reason.isBlank() -> setOf(
                MedicalCareValidationError(
                    MedicalCareField.CHANGE_REASON,
                    MedicalCareValidationIssue.REQUIRED,
                ),
            )
            command.reason.length > 500 -> setOf(
                MedicalCareValidationError(
                    MedicalCareField.CHANGE_REASON,
                    MedicalCareValidationIssue.TOO_LONG,
                ),
            )
            else -> emptySet()
        }
        if (errors.isNotEmpty()) return MedicalCareMutationResult.Invalid(errors)
        val record = repository.load(command.personUnderCareId)
            .medicationIntakes.singleOrNull { it.id == command.intakeRecordId }
            ?: return MedicalCareMutationResult.NotFound
        if (expectedRevisionNumber != null &&
            record.currentRevision.revisionNumber != expectedRevisionNumber
        ) throw MedicalCareConcurrentChangeException()
        val plan = repository.load(command.personUnderCareId)
            .medicationPlans.singleOrNull {
                it.id == record.medicationPlanId
            } ?: return MedicalCareMutationResult.NotFound
        if (
            plan.contentMode ==
                com.bitavida.app.domain.model.ClinicalContentMode
                    .REAL_CLINICAL_DATA &&
            !repository.hasActiveSensitiveDataConsent(
                profileId = command.careProfileId.orEmpty(),
                caregiverId = command.authorActorId,
                personUnderCareId = command.personUnderCareId,
                documentVersion =
                    command.sensitiveConsentVersion.orEmpty(),
            )
        ) {
            return MedicalCareMutationResult.Invalid(
                realClinicalOperationBlocked(),
            )
        }
        if (plan.currentRevision.configuration?.frequency ==
            com.bitavida.app.domain.model.MedicationFrequencyKind.AS_NEEDED &&
            !hasMedicationIntakeReason(command.observation)
        ) {
            return MedicalCareMutationResult.Invalid(setOf(
                MedicalCareValidationError(
                    MedicalCareField.INTAKE_OBSERVATION, MedicalCareValidationIssue.REQUIRED,
                ),
            ))
        }
        val current = record.currentRevision
        val revision = MedicationIntakeRevision(
            id = newId(),
            intakeRecordId = record.id,
            revisionNumber = current.revisionNumber + 1,
            outcome = command.outcome,
            scheduledAt = command.scheduledAt,
            scheduledOccurrenceId = command.scheduledOccurrenceId ?: current.scheduledOccurrenceId,
            actualAt = command.actualAt,
            actualAmount = command.actualAmount,
            observation = command.observation.nullIfBlank(),
            changeKind = if (
                command.outcome == MedicationIntakeOutcome.OMITTED ||
                command.outcome == MedicationIntakeOutcome.REFUSED ||
                command.outcome == MedicationIntakeOutcome.UNCONFIRMED
            ) {
                MedicationIntakeChangeKind.JUSTIFIED
            } else {
                MedicationIntakeChangeKind.CORRECTED
            },
            changeReason = command.reason.trim(),
            capturedAtEpochMillis = nowEpochMillis(),
            authorActorId = command.authorActorId,
        )
        repository.appendMedicationIntakeRevision(
            recordId = record.id,
            expectedRevisionNumber = current.revisionNumber,
            revision = revision,
        )
        return MedicalCareMutationResult.Stored(revision)
    }
}

private fun realClinicalOperationBlocked() = setOf(
    MedicalCareValidationError(
        MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT,
        MedicalCareValidationIssue.REAL_CLINICAL_CAPTURE_BLOCKED,
    ),
)

private fun validateIntake(
    outcome: MedicationIntakeOutcome,
    actualAt: LocalDateTime?,
    actualAmount: MedicationAmount?,
    observation: String?,
): Set<MedicalCareValidationError> = buildSet {
    if (
        outcome in setOf(
            MedicationIntakeOutcome.COMPLETED,
            MedicationIntakeOutcome.PARTIAL,
        ) &&
        actualAt == null
    ) {
        add(
            MedicalCareValidationError(
                MedicalCareField.INTAKE_ACTUAL_TIME,
                MedicalCareValidationIssue.REQUIRED,
            ),
        )
    }
    if (
        actualAmount != null &&
        outcome !in setOf(
            MedicationIntakeOutcome.COMPLETED,
            MedicationIntakeOutcome.PARTIAL,
        )
    ) {
        add(
            MedicalCareValidationError(
                MedicalCareField.INTAKE_AMOUNT,
                MedicalCareValidationIssue.INVALID_VALUE,
            ),
        )
    }
    if (observation != null && observation.length > 1_000) {
        add(
            MedicalCareValidationError(
                MedicalCareField.INTAKE_OBSERVATION,
                MedicalCareValidationIssue.TOO_LONG,
            ),
        )
    }
}

private fun String?.nullIfBlank(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)
