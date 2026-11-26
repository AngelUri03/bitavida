package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicalIndicationChangeKind
import com.bitavida.app.domain.model.MedicalIndicationRevision
import com.bitavida.app.domain.model.MedicalIndicationStatus
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.ScheduledDose
import com.bitavida.app.domain.model.groupByMedicationIdentity
import com.bitavida.app.domain.model.identityPlansFor
import com.bitavida.app.domain.model.lastTreatmentDate
import com.bitavida.app.domain.model.lifecycleTransitions
import com.bitavida.app.domain.model.normalizeMedicationNameForIdentity
import com.bitavida.app.domain.model.treatmentStatusAt
import com.bitavida.app.domain.repository.MedicalCareRepository
import com.bitavida.app.domain.validation.CaptureLimit
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class GetMedicalCareOverviewUseCase(
    private val repository: MedicalCareRepository,
) {
    suspend operator fun invoke(personUnderCareId: String): MedicalCareOverview =
        repository.load(personUnderCareId)
}

class RegisterMedicalIndicationUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(
        command: RegisterMedicalIndicationCommand,
    ): MedicalCareMutationResult<MedicalIndication> {
        val errors = validateIndication(
            originalText = command.originalText,
            source = command.declaredProfessionalOrSource,
            validFrom = command.validFrom,
            validUntil = command.validUntil,
        ) + validateCapture(command.contentMode, command.fictitiousDataAcknowledged)
        if (errors.isNotEmpty()) return MedicalCareMutationResult.Invalid(errors)

        val now = nowEpochMillis()
        val indicationId = newId()
        val indication = MedicalIndication(
            id = indicationId,
            personUnderCareId = command.personUnderCareId,
            origin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            createdAtEpochMillis = now,
            createdByActorId = command.authorActorId,
            contentMode = command.contentMode,
            revisions = listOf(
                MedicalIndicationRevision(
                    id = newId(),
                    indicationId = indicationId,
                    revisionNumber = 1,
                    originalText = command.originalText.trim(),
                    indicatedOn = command.indicatedOn,
                    capturedAtEpochMillis = now,
                    declaredProfessionalOrSource =
                        command.declaredProfessionalOrSource.nullIfBlank(),
                    validFrom = command.validFrom,
                    validUntil = command.validUntil,
                    status = MedicalIndicationStatus.ACTIVE,
                    changeKind = MedicalIndicationChangeKind.CREATED,
                    changeReason = null,
                    authorActorId = command.authorActorId,
                ),
            ),
        )
        repository.createIndication(indication)
        return MedicalCareMutationResult.Stored(indication)
    }
}

class ReviseMedicalIndicationUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(
        command: ReviseMedicalIndicationCommand,
    ): MedicalCareMutationResult<MedicalIndicationRevision> {
        val errors = validateIndication(
            command.originalText,
            command.declaredProfessionalOrSource,
            command.validFrom,
            command.validUntil,
        ) + validateReason(command.changeReason) +
            validateCapture(command.contentMode, command.fictitiousDataAcknowledged)
        if (errors.isNotEmpty()) return MedicalCareMutationResult.Invalid(errors)

        val indication = repository.load(command.personUnderCareId)
            .indications.singleOrNull { it.id == command.indicationId }
            ?: return MedicalCareMutationResult.NotFound
        if (indication.contentMode != command.contentMode) {
            return MedicalCareMutationResult.Invalid(
                setOf(
                    MedicalCareValidationError(
                        MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT,
                        MedicalCareValidationIssue.REAL_CLINICAL_CAPTURE_BLOCKED,
                    ),
                ),
            )
        }
        val current = indication.currentRevision
        val revision = current.copy(
            id = newId(),
            revisionNumber = current.revisionNumber + 1,
            originalText = command.originalText.trim(),
            indicatedOn = command.indicatedOn,
            capturedAtEpochMillis = nowEpochMillis(),
            declaredProfessionalOrSource =
                command.declaredProfessionalOrSource.nullIfBlank(),
            validFrom = command.validFrom,
            validUntil = command.validUntil,
            changeKind = MedicalIndicationChangeKind.CORRECTED,
            changeReason = command.changeReason.trim(),
            authorActorId = command.authorActorId,
        )
        repository.appendIndicationRevision(indication.id, current.revisionNumber, revision)
        return MedicalCareMutationResult.Stored(revision)
    }
}

class ChangeMedicalIndicationStatusUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(
        command: ChangeMedicalIndicationStatusCommand,
    ): MedicalCareMutationResult<MedicalIndicationRevision> {
        val errors = validateReason(command.changeReason) +
            validateCapture(command.contentMode, command.fictitiousDataAcknowledged)
        if (errors.isNotEmpty()) return MedicalCareMutationResult.Invalid(errors)

        val indication = repository.load(command.personUnderCareId)
            .indications.singleOrNull { it.id == command.indicationId }
            ?: return MedicalCareMutationResult.NotFound
        if (indication.contentMode != command.contentMode) {
            return MedicalCareMutationResult.Invalid(
                setOf(
                    MedicalCareValidationError(
                        MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT,
                        MedicalCareValidationIssue.REAL_CLINICAL_CAPTURE_BLOCKED,
                    ),
                ),
            )
        }
        val current = indication.currentRevision
        if (current.status == command.targetStatus) {
            return MedicalCareMutationResult.Stored(current)
        }
        val revision = current.copy(
            id = newId(),
            revisionNumber = current.revisionNumber + 1,
            capturedAtEpochMillis = nowEpochMillis(),
            status = command.targetStatus,
            changeKind = MedicalIndicationChangeKind.STATUS_CHANGED,
            changeReason = command.changeReason.trim(),
            authorActorId = command.authorActorId,
        )
        repository.appendIndicationRevision(indication.id, current.revisionNumber, revision)
        return MedicalCareMutationResult.Stored(revision)
    }
}

class RegisterMedicationPlanUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(
        command: RegisterMedicationPlanCommand,
    ): MedicalCareMutationResult<MedicationPlan> {
        val captureErrors = if (
            command.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
        ) {
            val authorized = command.careProfileId != null &&
                command.sensitiveConsentVersion != null &&
                repository.hasActiveSensitiveDataConsent(
                    profileId = command.careProfileId,
                    caregiverId = command.authorActorId,
                    personUnderCareId = command.personUnderCareId,
                    documentVersion = command.sensitiveConsentVersion,
                )
            if (authorized) emptySet() else realClinicalCaptureBlocked()
        } else {
            validateCapture(
                command.contentMode,
                command.fictitiousDataAcknowledged,
            )
        }
        val errors = validateMedication(command) + captureErrors
        if (errors.isNotEmpty()) return MedicalCareMutationResult.Invalid(errors)

        val overview = repository.load(command.personUnderCareId)
        val indication = command.medicalIndicationId?.let { indicationId ->
            overview.indications.singleOrNull { it.id == indicationId }
                ?: return MedicalCareMutationResult.NotFound
        }
        if (indication != null && indication.contentMode != command.contentMode) {
            return MedicalCareMutationResult.Invalid(realClinicalCaptureBlocked())
        }
        val sourcePeriod = command.sourceMedicationPlanId?.let { sourceId ->
            overview.medicationPlans.singleOrNull { it.id == sourceId }
                ?: return MedicalCareMutationResult.NotFound
        }
        val requestedIdentity = normalizeMedicationNameForIdentity(
            command.medicationName,
        )
        val identityPlans = if (sourcePeriod != null) {
            overview.medicationPlans.identityPlansFor(sourcePeriod)
        } else {
            overview.medicationPlans.groupByMedicationIdentity()
                .singleOrNull { group ->
                    group.any {
                        normalizeMedicationNameForIdentity(
                            it.currentRevision.medicationName,
                        ) == requestedIdentity
                    }
                }.orEmpty()
        }
        val now = nowEpochMillis()
        val sourceIdentityMatches = sourcePeriod == null ||
            normalizeMedicationNameForIdentity(
                sourcePeriod.currentRevision.medicationName,
            ) == requestedIdentity
        val sourceIsClosed = sourcePeriod == null ||
            sourcePeriod.treatmentStatusAt(now, zoneId) ==
            MedicationTreatmentStatus.COMPLETED ||
            sourcePeriod.lastTreatmentDate(now, zoneId)
                ?.isBefore(
                    Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate(),
                ) == true
        val overlap = identityPlans.any { existing ->
            existing.overlapsMedicationPeriod(
                requestedStart = command.startsOn,
                requestedEnd = command.endsOn,
                nowEpochMillis = now,
                zoneId = zoneId,
            )
        }
        if (
            sourcePeriod?.personUnderCareId?.let {
                it != command.personUnderCareId
            } == true ||
            sourcePeriod?.contentMode?.let { it != command.contentMode } == true ||
            !sourceIdentityMatches ||
            !sourceIsClosed ||
            overlap
        ) {
            return MedicalCareMutationResult.Invalid(
                buildSet {
                    if (overlap) {
                        add(
                            MedicalCareValidationError(
                                MedicalCareField.MEDICATION_DATES,
                                MedicalCareValidationIssue.OVERLAPPING_PERIOD,
                            ),
                        )
                    }
                    if (!sourceIdentityMatches) {
                        add(
                            MedicalCareValidationError(
                                MedicalCareField.MEDICATION_NAME,
                                MedicalCareValidationIssue.INVALID_VALUE,
                            ),
                        )
                    }
                    if (!sourceIsClosed || isEmpty()) {
                        add(
                            MedicalCareValidationError(
                                MedicalCareField.VALIDITY,
                                MedicalCareValidationIssue.INVALID_VALUE,
                            ),
                        )
                    }
                },
            )
        }

        val canonicalSeriesId = identityPlans
            .minWithOrNull(
                compareBy<MedicationPlan>(MedicationPlan::createdAtEpochMillis)
                    .thenBy(MedicationPlan::id),
            )
            ?.medicationSeriesId
        val planId = newId()
        val revisionId = newId()
        val revision = MedicationPlanRevision(
            id = revisionId,
            medicationPlanId = planId,
            revisionNumber = 1,
            medicationName = command.medicationName.trim(),
            doseAsIndicated = command.doseAsIndicated.trim(),
            unitOrPresentation = command.unitOrPresentation.nullIfBlank(),
            frequencyAsIndicated = command.frequencyAsIndicated.nullIfBlank(),
            startsOn = command.startsOn,
            endsOn = command.endsOn,
            originalAdditionalInstructions =
                command.originalAdditionalInstructions.nullIfBlank(),
            scheduledDoses = command.explicitSchedules.distinct().sorted().map { time ->
                ScheduledDose(
                    id = newId(),
                    medicationPlanRevisionId = revisionId,
                    time = time,
                    configuredAtEpochMillis = now,
                    configuredByActorId = command.authorActorId,
                    origin = InformationOrigin.CAREGIVER_RECORD,
                )
            },
            capturedAtEpochMillis = now,
            authorActorId = command.authorActorId,
            changeKind = MedicationPlanChangeKind.CREATED,
            changeReason = null,
            configuration = command.configuration,
        )
        val plan = MedicationPlan(
            id = planId,
            medicationSeriesId = canonicalSeriesId ?: planId,
            personUnderCareId = command.personUnderCareId,
            medicalIndicationId = command.medicalIndicationId,
            medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
            createdAtEpochMillis = now,
            createdByActorId = command.authorActorId,
            contentMode = command.contentMode,
            revisions = listOf(revision),
        )
        repository.createMedicationPlan(plan)
        return MedicalCareMutationResult.Stored(plan)
    }
}

class ReviseMedicationPlanUseCase(
    private val repository: MedicalCareRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(
        command: ReviseMedicationPlanCommand,
    ): MedicalCareMutationResult<MedicationPlanRevision> {
        val captureErrors = if (
            command.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
        ) {
            val authorized = command.careProfileId != null &&
                command.sensitiveConsentVersion != null &&
                repository.hasActiveSensitiveDataConsent(
                    profileId = command.careProfileId,
                    caregiverId = command.authorActorId,
                    personUnderCareId = command.personUnderCareId,
                    documentVersion = command.sensitiveConsentVersion,
                )
            if (authorized) emptySet() else realClinicalCaptureBlocked()
        } else {
            validateCapture(
                command.contentMode,
                command.fictitiousDataAcknowledged,
            )
        }
        val plan = repository.load(command.personUnderCareId)
            .medicationPlans.singleOrNull { it.id == command.medicationPlanId }
            ?: return MedicalCareMutationResult.NotFound
        val current = plan.currentRevision
        val today = Instant.ofEpochMilli(nowEpochMillis())
            .atZone(zoneId)
            .toLocalDate()
        val periodIsClosed =
            current.changeKind == MedicationPlanChangeKind.COMPLETED ||
                current.configuration?.status == MedicationTreatmentStatus.COMPLETED ||
                (current.configuration?.endsOn ?: current.endsOn)
                    ?.isBefore(today) == true
        if (periodIsClosed) {
            return MedicalCareMutationResult.Invalid(
                setOf(
                    MedicalCareValidationError(
                        MedicalCareField.VALIDITY,
                        MedicalCareValidationIssue.INVALID_VALUE,
                    ),
                ),
            )
        }
        val errors = validateMedication(
            command,
            originalName = plan.currentRevision.medicationName,
            originalPresentation = plan.currentRevision.unitOrPresentation,
        ) + validateReason(command.changeReason) + captureErrors
        if (errors.isNotEmpty()) {
            return MedicalCareMutationResult.Invalid(errors)
        }

        if (plan.contentMode != command.contentMode) {
            return MedicalCareMutationResult.Invalid(
                setOf(
                    MedicalCareValidationError(
                        MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT,
                        MedicalCareValidationIssue.REAL_CLINICAL_CAPTURE_BLOCKED,
                    ),
                ),
            )
        }

        val now = nowEpochMillis()
        val revisionId = newId()
        val revision = MedicationPlanRevision(
            id = revisionId,
            medicationPlanId = plan.id,
            revisionNumber = current.revisionNumber + 1,
            medicationName = command.medicationName.trim(),
            doseAsIndicated = command.doseAsIndicated.trim(),
            unitOrPresentation = command.unitOrPresentation.nullIfBlank(),
            frequencyAsIndicated = command.frequencyAsIndicated.nullIfBlank(),
            startsOn = command.startsOn,
            endsOn = command.endsOn,
            originalAdditionalInstructions =
                command.originalAdditionalInstructions.nullIfBlank(),
            scheduledDoses = command.explicitSchedules.distinct().sorted().map { time ->
                ScheduledDose(
                    id = newId(),
                    medicationPlanRevisionId = revisionId,
                    time = time,
                    configuredAtEpochMillis = now,
                    configuredByActorId = command.authorActorId,
                    origin = InformationOrigin.CAREGIVER_RECORD,
                )
            },
            capturedAtEpochMillis = now,
            authorActorId = command.authorActorId,
            changeKind = command.changeKind,
            changeReason = command.changeReason.trim(),
            configuration = command.configuration ?: current.configuration,
        )
        repository.appendMedicationPlanRevision(plan.id, current.revisionNumber, revision)
        return MedicalCareMutationResult.Stored(revision)
    }
}

private fun realClinicalCaptureBlocked() = setOf(
    MedicalCareValidationError(
        MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT,
        MedicalCareValidationIssue.REAL_CLINICAL_CAPTURE_BLOCKED,
    ),
)

private fun validateCapture(
    contentMode: ClinicalContentMode,
    fictitiousDataAcknowledged: Boolean,
): Set<MedicalCareValidationError> {
    if (contentMode == ClinicalContentMode.REAL_CLINICAL_DATA) {
        return realClinicalCaptureBlocked()
    }
    return if (fictitiousDataAcknowledged) {
        emptySet()
    } else {
        setOf(
            MedicalCareValidationError(
                MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT,
                MedicalCareValidationIssue.REQUIRED,
            ),
        )
    }
}

private fun validateIndication(
    originalText: String,
    source: String?,
    validFrom: java.time.LocalDate?,
    validUntil: java.time.LocalDate?,
): Set<MedicalCareValidationError> = buildSet {
    if (originalText.isBlank()) {
        add(MedicalCareValidationError(MedicalCareField.ORIGINAL_INDICATION, MedicalCareValidationIssue.REQUIRED))
    } else if (originalText.length > 4_000) {
        add(MedicalCareValidationError(MedicalCareField.ORIGINAL_INDICATION, MedicalCareValidationIssue.TOO_LONG))
    }
    if (source != null && source.length > 200) {
        add(MedicalCareValidationError(MedicalCareField.DECLARED_SOURCE, MedicalCareValidationIssue.TOO_LONG))
    }
    if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
        add(MedicalCareValidationError(MedicalCareField.VALIDITY, MedicalCareValidationIssue.INVALID_DATE_RANGE))
    }
}

private fun validateMedication(
    command: RegisterMedicationPlanCommand,
): Set<MedicalCareValidationError> = validateMedicationValues(
    command.medicationName,
    command.doseAsIndicated,
    command.unitOrPresentation,
    command.frequencyAsIndicated,
    command.startsOn,
    command.endsOn,
    command.originalAdditionalInstructions,
    command.explicitSchedules,
    command.configuration != null,
)

private fun validateMedication(
    command: ReviseMedicationPlanCommand,
    originalName: String?,
    originalPresentation: String?,
): Set<MedicalCareValidationError> = validateMedicationValues(
    command.medicationName,
    command.doseAsIndicated,
    command.unitOrPresentation,
    command.frequencyAsIndicated,
    command.startsOn,
    command.endsOn,
    command.originalAdditionalInstructions,
    command.explicitSchedules,
    command.configuration != null,
    originalName,
    originalPresentation,
)

private fun validateMedicationValues(
    medicationName: String,
    dose: String,
    unit: String?,
    frequency: String?,
    startsOn: java.time.LocalDate,
    endsOn: java.time.LocalDate?,
    instructions: String?,
    schedules: List<java.time.LocalTime>,
    hasTypedConfiguration: Boolean,
    originalName: String? = null,
    originalPresentation: String? = null,
): Set<MedicalCareValidationError> = buildSet {
    if (medicationName.isBlank()) {
        add(MedicalCareValidationError(MedicalCareField.MEDICATION_NAME, MedicalCareValidationIssue.REQUIRED))
    } else if (!CaptureLimit.MEDICATION_NAME.acceptsStoredOrNew(medicationName, originalName)) {
        add(MedicalCareValidationError(MedicalCareField.MEDICATION_NAME, MedicalCareValidationIssue.TOO_LONG))
    }
    if (dose.isBlank()) {
        add(MedicalCareValidationError(MedicalCareField.DOSE_AS_INDICATED, MedicalCareValidationIssue.REQUIRED))
    } else if (dose.length > 240) {
        add(MedicalCareValidationError(MedicalCareField.DOSE_AS_INDICATED, MedicalCareValidationIssue.TOO_LONG))
    }
    if (unit != null &&
        (unit.length > 160 ||
            (!hasTypedConfiguration &&
                !CaptureLimit.SHORT_LABEL.acceptsStoredOrNew(unit, originalPresentation)))
    ) {
        add(MedicalCareValidationError(MedicalCareField.UNIT_OR_PRESENTATION, MedicalCareValidationIssue.TOO_LONG))
    }
    if (frequency != null && frequency.length > 240) {
        add(MedicalCareValidationError(MedicalCareField.FREQUENCY_AS_INDICATED, MedicalCareValidationIssue.TOO_LONG))
    }
    if (endsOn != null && endsOn.isBefore(startsOn)) {
        add(MedicalCareValidationError(MedicalCareField.MEDICATION_DATES, MedicalCareValidationIssue.INVALID_DATE_RANGE))
    }
    if (instructions != null && instructions.length > 2_000) {
        add(MedicalCareValidationError(MedicalCareField.ADDITIONAL_INSTRUCTIONS, MedicalCareValidationIssue.TOO_LONG))
    }
    if (schedules.isEmpty() && !hasTypedConfiguration) {
        add(MedicalCareValidationError(MedicalCareField.EXPLICIT_SCHEDULES, MedicalCareValidationIssue.REQUIRED))
    } else if (schedules.distinct().size != schedules.size) {
        add(MedicalCareValidationError(MedicalCareField.EXPLICIT_SCHEDULES, MedicalCareValidationIssue.DUPLICATE_SCHEDULE))
    }
}

private fun validateReason(reason: String): Set<MedicalCareValidationError> =
    when {
        reason.isBlank() ->
            setOf(MedicalCareValidationError(MedicalCareField.CHANGE_REASON, MedicalCareValidationIssue.REQUIRED))
        reason.length > 500 ->
            setOf(MedicalCareValidationError(MedicalCareField.CHANGE_REASON, MedicalCareValidationIssue.TOO_LONG))
        else -> emptySet()
    }

private fun MedicationPlan.overlapsMedicationPeriod(
    requestedStart: java.time.LocalDate,
    requestedEnd: java.time.LocalDate?,
    nowEpochMillis: Long,
    zoneId: ZoneId,
): Boolean {
    val configuration = currentRevision.configuration
    val existingStart = configuration?.startsOn ?: currentRevision.startsOn
    val existingEnd = when (
        treatmentStatusAt(nowEpochMillis, zoneId)
    ) {
        MedicationTreatmentStatus.COMPLETED ->
            lastTreatmentDate(nowEpochMillis, zoneId)
        MedicationTreatmentStatus.REMOVED ->
            lifecycleTransitions(zoneId)
                .lastOrNull {
                    it.status == MedicationTreatmentStatus.REMOVED &&
                        it.effectiveAtEpochMillis <= nowEpochMillis
                }
                ?.let { transition ->
                    val moment = Instant.ofEpochMilli(
                        transition.effectiveAtEpochMillis,
                    ).atZone(zoneId).toLocalDateTime()
                    if (moment.toLocalTime() == java.time.LocalTime.MIDNIGHT) {
                        moment.toLocalDate().minusDays(1)
                    } else {
                        moment.toLocalDate()
                    }
                }
        else -> configuration?.endsOn ?: currentRevision.endsOn
    }
    val startsBeforeRequestedEnd = requestedEnd == null ||
        !existingStart.isAfter(requestedEnd)
    val requestedStartsBeforeExistingEnd = existingEnd == null ||
        !requestedStart.isAfter(existingEnd)
    return startsBeforeRequestedEnd && requestedStartsBeforeExistingEnd
}

private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
