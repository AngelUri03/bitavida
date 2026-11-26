package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.WeightRegistrationContext
import com.bitavida.app.domain.model.WeightRevision
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionKind
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevisionOperation
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.metadata
import com.bitavida.app.domain.repository.WeightRepository
import com.bitavida.app.domain.repository.GlucoseActor
import java.math.BigDecimal
import java.time.LocalDateTime
import com.bitavida.app.domain.validation.fitsTextLimit
import com.bitavida.app.domain.validation.CaptureLimit

data class WeightDraft(
    val id: String,
    val context: WeightRegistrationContext,
    val actualAt: LocalDateTime,
    val kilograms: String = "",
    val heightMeters: BigDecimal? = null,
    val note: String = "",
    val firstMeasurement: Boolean = false,
    val correctingDecisionId: String? = null,
)

enum class WeightField {
    KILOGRAMS,
    ACTUAL_TIME,
    NOTE,
    SCHEDULED_REFERENCE,
}

fun validateWeightDraft(
    draft: WeightDraft,
    now: LocalDateTime,
    originalNote: String? = null,
): Set<WeightField> = buildSet {
    if (!draft.kilograms.isValidWeightValue()) {
        add(WeightField.KILOGRAMS)
    }
    if (draft.actualAt.isAfter(now)) add(WeightField.ACTUAL_TIME)
    if (!fitsTextLimit(draft.note, CaptureLimit.MEASUREMENT_NOTE.maximum, originalNote)) add(WeightField.NOTE)
    val scheduled = draft.context as? WeightRegistrationContext.Scheduled
    if (
        (scheduled != null) &&
        (
            scheduled.occurrence.id.isBlank() ||
                scheduled.occurrence.followUpId.isBlank()
            )
    ) {
        add(WeightField.SCHEDULED_REFERENCE)
    }
    if (
        (draft.correctingDecisionId != null) &&
        (
            draft.correctingDecisionId.isBlank() ||
                scheduled == null
            )
    ) {
        add(WeightField.SCHEDULED_REFERENCE)
    }
}

private fun String.isValidWeightValue(): Boolean {
    val normalized = trim().replace(',', '.')
    val value = normalized.toBigDecimalOrNull() ?: return false
    return value > BigDecimal.ZERO && value.scale().coerceAtLeast(0) <= 2
}

sealed interface WeightSaveResult {
    data class Stored(
        val record: MeasurementRecord,
    ) : WeightSaveResult

    data class AlreadyRecorded(
        val record: MeasurementRecord,
    ) : WeightSaveResult

    data class Invalid(
        val fields: Set<WeightField>,
    ) : WeightSaveResult

    data object ConsentRequired : WeightSaveResult
    data object AuthorizationRequired : WeightSaveResult
}

class GetWeightRecordsUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor) = repository.load(actor)
}

sealed interface WeightTargetMutationResult {
    data class Stored(
        val target: WeightTarget,
    ) : WeightTargetMutationResult

    data object Removed : WeightTargetMutationResult
    data object Invalid : WeightTargetMutationResult
    data object ConsentRequired : WeightTargetMutationResult
    data object AuthorizationRequired : WeightTargetMutationResult
}

class GetWeightTargetUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor): WeightTarget? =
        repository.loadTarget(actor)
}

class SaveWeightTargetUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        kilograms: String,
    ): WeightTargetMutationResult {
        val normalized = kilograms.trim().replace(',', '.')
        val value = normalized.toBigDecimalOrNull()
        if (
            value == null ||
            value <= BigDecimal.ZERO ||
            value.scale().coerceAtLeast(0) > 2
        ) {
            return WeightTargetMutationResult.Invalid
        }
        if (!actor.isUsableForWeight()) {
            return WeightTargetMutationResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return WeightTargetMutationResult.ConsentRequired
        }
        val target = WeightTarget(value.stripTrailingZeros())
        return WeightTargetMutationResult.Stored(
            repository.saveTarget(actor, target),
        )
    }
}

class DeleteWeightTargetUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
    ): WeightTargetMutationResult {
        if (!actor.isUsableForWeight()) {
            return WeightTargetMutationResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return WeightTargetMutationResult.ConsentRequired
        }
        repository.deleteTarget(actor)
        return WeightTargetMutationResult.Removed
    }
}

class SaveWeightMeasurementUseCase(
    private val repository: WeightRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: WeightDraft,
    ): WeightSaveResult {
        val capturedAt = now()
        val errors = validateWeightDraft(draft, capturedAt)
        if (errors.isNotEmpty()) return WeightSaveResult.Invalid(errors)
        if (!actor.isUsableForWeight()) {
            return WeightSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return WeightSaveResult.ConsentRequired
        }
        val record = draft.toRecord(capturedAt)
        val existing = draft.correctingDecisionId?.let { decisionId ->
            val (occurrence) =
                draft.context as WeightRegistrationContext.Scheduled
            repository.createCorrectingNotPerformed(
                actor = actor,
                record = record,
                decisionRevision = GlucoseOccurrenceDecisionRevision(
                    id = "$decisionId:recorded:${record.id}",
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
            WeightSaveResult.Stored(existing)
        } else {
            WeightSaveResult.AlreadyRecorded(existing)
        }
    }
}

class CorrectWeightMeasurementUseCase(
    private val repository: WeightRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: WeightDraft,
    ): WeightSaveResult {
        if (!actor.isUsableForWeight()) {
            return WeightSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return WeightSaveResult.ConsentRequired
        }
        val originalNote = repository.load(actor).singleOrNull { it.id == draft.id }?.weightMetadata?.note
        val errors = validateWeightDraft(draft, now(), originalNote)
        if (errors.isNotEmpty()) return WeightSaveResult.Invalid(errors)
        return WeightSaveResult.Stored(
            repository.revise(actor, draft.toRecord(now(), originalNote)),
        )
    }
}

sealed interface WeightDeleteResult {
    data object Deleted : WeightDeleteResult
    data object ConsentRequired : WeightDeleteResult
    data object AuthorizationRequired : WeightDeleteResult
}

class DeleteWeightMeasurementUseCase(
    private val repository: WeightRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        recordId: String,
    ): WeightDeleteResult {
        if (!actor.isUsableForWeight()) {
            return WeightDeleteResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return WeightDeleteResult.ConsentRequired
        }
        repository.softDelete(actor, recordId, now())
        return WeightDeleteResult.Deleted
    }
}

class GetWeightRevisionsUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, recordId: String):
        List<WeightRevision> = repository.loadRevisions(actor, recordId)
}

class AcceptWeightSensitiveConsentUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        acceptedAtEpochMillis: Long,
    ) {
        require(actor.authorizationCurrent)
        repository.acceptSensitiveConsent(actor, acceptedAtEpochMillis)
    }
}

class GetWeightFollowUpUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor) =
        repository.loadFollowUp(actor)
}

class GetWeightOccurrenceDecisionsUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, followUpId: String) =
        repository.loadOccurrenceDecisions(actor, followUpId)
}

class GetWeightDecisionRevisionsUseCase(
    private val repository: WeightRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, occurrenceId: String) =
        repository.loadOccurrenceDecisionRevisions(actor, occurrenceId)
}

class SaveWeightFollowUpUseCase(
    private val repository: WeightRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: GlucoseFollowUpDraft,
    ): GlucoseFollowUpSaveResult {
        val normalizedDraft = draft.copy(
            slots = draft.slots.map { slot ->
                slot.copy(context = GlucoseContext.UNSPECIFIED)
            },
        )
        if (!actor.isUsableForWeight()) {
            return GlucoseFollowUpSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return GlucoseFollowUpSaveResult.ConsentRequired
        }
        val originalRevision = if (normalizedDraft.expectedRevisionNumber > 0)
            repository.loadFollowUp(actor)?.takeIf { it.id == normalizedDraft.followUpId }?.currentRevision else null
        val errors = validateGlucoseFollowUpDraft(
            normalizedDraft,
            allowedContexts = listOf(GlucoseContext.UNSPECIFIED),
            originalRevision = originalRevision,
        )
        if (errors.isNotEmpty()) return GlucoseFollowUpSaveResult.Invalid(errors)
        val capturedAt = now()
        val revisionNumber = normalizedDraft.expectedRevisionNumber + 1
        val revision = normalizedDraft.toRevision(
            actor = actor,
            revisionNumber = revisionNumber,
            capturedAt = capturedAt,
            newId = newId,
        )
        val stored = if (normalizedDraft.expectedRevisionNumber == 0) {
            repository.createFollowUp(
                actor,
                GlucoseFollowUp(
                    id = normalizedDraft.followUpId,
                    personUnderCareId = actor.personUnderCareId,
                    createdAt = capturedAt,
                    createdByActorId = actor.caregiverId,
                    revisions = listOf(revision),
                ),
            )
        } else {
            repository.appendFollowUpRevision(
                actor = actor,
                followUpId = normalizedDraft.followUpId,
                expectedRevisionNumber = normalizedDraft.expectedRevisionNumber,
                revision = revision,
            )
        }
        return GlucoseFollowUpSaveResult.Stored(stored)
    }
}

class ConfirmWeightNotPerformedUseCase(
    private val repository: WeightRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        occurrence: GlucoseScheduledOccurrence,
        reason: String?,
    ): GlucoseOccurrenceDecision {
        require(actor.isUsableForWeight())
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

class CorrectWeightNotPerformedUseCase(
    private val repository: WeightRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val newId: () -> String,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
        reason: String?,
    ): GlucoseOccurrenceDecision {
        require(actor.isUsableForWeight())
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

private fun WeightDraft.toRecord(capturedAt: LocalDateTime, originalNote: String? = null):
    MeasurementRecord = MeasurementRecord(
        id = id,
        value = MeasurementValue.Weight(
            kilograms = kilograms.trim().replace(',', '.').toBigDecimal(),
            heightMeters = heightMeters?.takeIf { it > BigDecimal.ZERO },
        ),
        occurredAt = actualAt,
        capturedAt = capturedAt,
        weightMetadata = context.metadata(
            note = if (note == originalNote) originalNote else note.trim().takeIf(String::isNotEmpty),
        ),
    )

private fun GlucoseActor.isUsableForWeight(): Boolean =
    authorizationCurrent &&
        profileId.isNotBlank() &&
        caregiverId.isNotBlank() &&
        personUnderCareId.isNotBlank()
