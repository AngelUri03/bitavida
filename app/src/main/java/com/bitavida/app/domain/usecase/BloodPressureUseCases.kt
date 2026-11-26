package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.BloodPressureRegistrationContext
import com.bitavida.app.domain.model.BloodPressureRevision
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionKind
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevisionOperation
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.bloodPressureMeasurementContexts
import com.bitavida.app.domain.model.metadata
import com.bitavida.app.domain.repository.BloodPressureRepository
import com.bitavida.app.domain.repository.GlucoseActor
import java.time.LocalDateTime
import com.bitavida.app.domain.validation.fitsTextLimit
import com.bitavida.app.domain.validation.CaptureLimit

data class BloodPressureDraft(
    val id: String,
    val context: BloodPressureRegistrationContext,
    val actualAt: LocalDateTime,
    val systolic: String = "",
    val diastolic: String = "",
    val moment: GlucoseContext? = null,
    val note: String = "",
    val firstMeasurement: Boolean = false,
    val correctingDecisionId: String? = null,
)

enum class BloodPressureField {
    SYSTOLIC,
    DIASTOLIC,
    MOMENT,
    ACTUAL_TIME,
    NOTE,
    SCHEDULED_REFERENCE,
}

fun validateBloodPressureDraft(
    draft: BloodPressureDraft,
    now: LocalDateTime,
    originalNote: String? = null,
): Set<BloodPressureField> = buildSet {
    if (!draft.systolic.isValidPressureValue()) {
        add(BloodPressureField.SYSTOLIC)
    }
    if (!draft.diastolic.isValidPressureValue()) {
        add(BloodPressureField.DIASTOLIC)
    }
    if (draft.moment !in bloodPressureMeasurementContexts) {
        add(BloodPressureField.MOMENT)
    }
    if (draft.actualAt.isAfter(now)) add(BloodPressureField.ACTUAL_TIME)
    if (!fitsTextLimit(draft.note, CaptureLimit.MEASUREMENT_NOTE.maximum, originalNote)) add(BloodPressureField.NOTE)
    val scheduled = draft.context as? BloodPressureRegistrationContext.Scheduled
    if (
        scheduled != null &&
        (
            scheduled.occurrence.id.isBlank() ||
                scheduled.occurrence.followUpId.isBlank()
            )
    ) {
        add(BloodPressureField.SCHEDULED_REFERENCE)
    }
    if (
        draft.correctingDecisionId != null &&
        (
            draft.correctingDecisionId.isBlank() ||
                scheduled == null
            )
    ) {
        add(BloodPressureField.SCHEDULED_REFERENCE)
    }
}

private fun String.isValidPressureValue(): Boolean =
    length in 1..3 && all(Char::isDigit) && toIntOrNull() in 1..999

sealed interface BloodPressureSaveResult {
    data class Stored(
        val record: MeasurementRecord,
    ) : BloodPressureSaveResult

    data class AlreadyRecorded(
        val record: MeasurementRecord,
    ) : BloodPressureSaveResult

    data class Invalid(
        val fields: Set<BloodPressureField>,
    ) : BloodPressureSaveResult

    data object ConsentRequired : BloodPressureSaveResult
    data object AuthorizationRequired : BloodPressureSaveResult
}

class GetBloodPressureRecordsUseCase(
    private val repository: BloodPressureRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor) = repository.load(actor)
}

class SaveBloodPressureMeasurementUseCase(
    private val repository: BloodPressureRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: BloodPressureDraft,
    ): BloodPressureSaveResult {
        val capturedAt = now()
        val errors = validateBloodPressureDraft(draft, capturedAt)
        if (errors.isNotEmpty()) return BloodPressureSaveResult.Invalid(errors)
        if (!actor.isUsableForBloodPressure()) {
            return BloodPressureSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return BloodPressureSaveResult.ConsentRequired
        }
        val record = draft.toRecord(capturedAt)
        val existing = draft.correctingDecisionId?.let { decisionId ->
            val occurrence =
                (draft.context as BloodPressureRegistrationContext.Scheduled)
                    .occurrence
            repository.createCorrectingNotPerformed(
                actor = actor,
                record = record,
                decisionRevision = GlucoseOccurrenceDecisionRevision(
                    id = "${decisionId}:recorded:${record.id}",
                    decisionId = decisionId,
                    occurrenceId = occurrence.id,
                    revisionNumber = 1,
                    operation =
                        GlucoseOccurrenceDecisionRevisionOperation
                            .CORRECTED_TO_RECORDED,
                    reason = null,
                    capturedAt = capturedAt,
                    actorId = actor.caregiverId,
                ),
            )
        } ?: repository.create(actor, record)
        return if (existing.id == record.id) {
            BloodPressureSaveResult.Stored(existing)
        } else {
            BloodPressureSaveResult.AlreadyRecorded(existing)
        }
    }
}

class CorrectBloodPressureMeasurementUseCase(
    private val repository: BloodPressureRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: BloodPressureDraft,
    ): BloodPressureSaveResult {
        if (!actor.isUsableForBloodPressure()) {
            return BloodPressureSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return BloodPressureSaveResult.ConsentRequired
        }
        val originalNote = repository.load(actor).singleOrNull { it.id == draft.id }?.bloodPressureMetadata?.note
        val errors = validateBloodPressureDraft(draft, now(), originalNote)
        if (errors.isNotEmpty()) return BloodPressureSaveResult.Invalid(errors)
        return BloodPressureSaveResult.Stored(
            repository.revise(actor, draft.toRecord(now(), originalNote)),
        )
    }
}

sealed interface BloodPressureDeleteResult {
    data object Deleted : BloodPressureDeleteResult
    data object ConsentRequired : BloodPressureDeleteResult
    data object AuthorizationRequired : BloodPressureDeleteResult
}

class DeleteBloodPressureMeasurementUseCase(
    private val repository: BloodPressureRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        recordId: String,
    ): BloodPressureDeleteResult {
        if (!actor.isUsableForBloodPressure()) {
            return BloodPressureDeleteResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return BloodPressureDeleteResult.ConsentRequired
        }
        repository.softDelete(actor, recordId, now())
        return BloodPressureDeleteResult.Deleted
    }
}

class GetBloodPressureRevisionsUseCase(
    private val repository: BloodPressureRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, recordId: String):
        List<BloodPressureRevision> = repository.loadRevisions(actor, recordId)
}

class AcceptBloodPressureSensitiveConsentUseCase(
    private val repository: BloodPressureRepository,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        acceptedAtEpochMillis: Long,
    ) {
        require(actor.authorizationCurrent)
        repository.acceptSensitiveConsent(actor, acceptedAtEpochMillis)
    }
}

class GetBloodPressureFollowUpUseCase(
    private val repository: BloodPressureRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor) =
        repository.loadFollowUp(actor)
}

class GetBloodPressureOccurrenceDecisionsUseCase(
    private val repository: BloodPressureRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, followUpId: String) =
        repository.loadOccurrenceDecisions(actor, followUpId)
}

class GetBloodPressureDecisionRevisionsUseCase(
    private val repository: BloodPressureRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, occurrenceId: String) =
        repository.loadOccurrenceDecisionRevisions(actor, occurrenceId)
}

class SaveBloodPressureFollowUpUseCase(
    private val repository: BloodPressureRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: GlucoseFollowUpDraft,
    ): GlucoseFollowUpSaveResult {
        if (!actor.isUsableForBloodPressure()) {
            return GlucoseFollowUpSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return GlucoseFollowUpSaveResult.ConsentRequired
        }
        val originalRevision = if (draft.expectedRevisionNumber > 0)
            repository.loadFollowUp(actor)?.takeIf { it.id == draft.followUpId }?.currentRevision else null
        val errors = validateGlucoseFollowUpDraft(
            draft,
            allowedContexts = bloodPressureMeasurementContexts,
            originalRevision = originalRevision,
        )
        if (errors.isNotEmpty()) return GlucoseFollowUpSaveResult.Invalid(errors)
        val capturedAt = now()
        val revisionNumber = draft.expectedRevisionNumber + 1
        val revision = draft.toRevision(
            actor = actor,
            revisionNumber = revisionNumber,
            capturedAt = capturedAt,
            newId = newId,
        )
        val stored = if (draft.expectedRevisionNumber == 0) {
            repository.createFollowUp(
                actor,
                GlucoseFollowUp(
                    id = draft.followUpId,
                    personUnderCareId = actor.personUnderCareId,
                    createdAt = capturedAt,
                    createdByActorId = actor.caregiverId,
                    revisions = listOf(revision),
                ),
            )
        } else {
            repository.appendFollowUpRevision(
                actor = actor,
                followUpId = draft.followUpId,
                expectedRevisionNumber = draft.expectedRevisionNumber,
                revision = revision,
            )
        }
        return GlucoseFollowUpSaveResult.Stored(stored)
    }
}

class ConfirmBloodPressureNotPerformedUseCase(
    private val repository: BloodPressureRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        occurrence: GlucoseScheduledOccurrence,
        reason: String?,
    ): GlucoseOccurrenceDecision {
        require(actor.isUsableForBloodPressure())
        require(reason == null || fitsTextLimit(reason, CaptureLimit.CHANGE_REASON.maximum))
        require(repository.hasSensitiveConsent(actor))
        return repository.confirmOccurrenceDecision(
            actor,
            GlucoseOccurrenceDecision(
                id = newId(),
                followUpId = occurrence.followUpId,
                followUpRevisionId = occurrence.followUpRevisionId,
                occurrenceId = occurrence.id,
                scheduledAt = occurrence.scheduledAt,
                kind = GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
                reason = reason?.trim()?.takeIf(String::isNotEmpty),
                capturedAt = now(),
                actorId = actor.caregiverId,
            ),
        )
    }
}

class CorrectBloodPressureNotPerformedUseCase(
    private val repository: BloodPressureRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
        reason: String?,
    ): GlucoseOccurrenceDecision {
        require(actor.isUsableForBloodPressure())
        require(reason == null || fitsTextLimit(reason, CaptureLimit.CHANGE_REASON.maximum, decision.reason))
        require(repository.hasSensitiveConsent(actor))
        return repository.correctOccurrenceDecision(
            actor,
            GlucoseOccurrenceDecisionRevision(
                id = newId(),
                decisionId = decision.id,
                occurrenceId = decision.occurrenceId,
                revisionNumber = 1,
                operation = GlucoseOccurrenceDecisionRevisionOperation.CORRECTED,
                reason = reason?.trim()?.takeIf(String::isNotEmpty),
                capturedAt = now(),
                actorId = actor.caregiverId,
            ),
        )
    }
}

private fun BloodPressureDraft.toRecord(capturedAt: LocalDateTime, originalNote: String? = null):
    MeasurementRecord {
    val moment = requireNotNull(moment)
    return MeasurementRecord(
        id = id,
        value = MeasurementValue.BloodPressure(
            systolic = systolic,
            diastolic = diastolic,
        ),
        occurredAt = actualAt,
        capturedAt = capturedAt,
        bloodPressureMetadata = context.metadata(
            moment = moment,
            note = if (note == originalNote) originalNote else note.trim().takeIf(String::isNotEmpty),
        ),
    )
}

private fun GlucoseActor.isUsableForBloodPressure(): Boolean =
    authorizationCurrent &&
        profileId.isNotBlank() &&
        caregiverId.isNotBlank() &&
        personUnderCareId.isNotBlank()
