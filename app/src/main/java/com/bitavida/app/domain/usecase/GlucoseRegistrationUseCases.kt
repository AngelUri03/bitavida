package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevisionOperation
import com.bitavida.app.domain.model.GlucoseRegistrationContext
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.glucoseMeasurementContexts
import com.bitavida.app.domain.model.metadata
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.GlucoseRepository
import java.time.LocalDateTime
import com.bitavida.app.domain.validation.fitsTextLimit
import com.bitavida.app.domain.validation.CaptureLimit

data class GlucoseDraft(
    val id: String,
    val context: GlucoseRegistrationContext,
    val actualAt: LocalDateTime,
    val reading: String = "",
    val moment: GlucoseContext? = null,
    val note: String = "",
    val firstMeasurement: Boolean = false,
    val correctingDecisionId: String? = null,
)

enum class GlucoseField { READING, MOMENT, ACTUAL_TIME, NOTE, SCHEDULED_REFERENCE }

fun validateGlucoseDraft(draft: GlucoseDraft, now: LocalDateTime, originalNote: String? = null): Set<GlucoseField> = buildSet {
    val reading = draft.reading.trim()
    val numeric = reading.replace(',', '.').toBigDecimalOrNull()
    if (reading.length > 40 || !(reading.uppercase() in setOf("HI", "LO") ||
        (reading.matches(Regex("[0-9]+([.,][0-9]+)?")) && numeric != null && numeric.toDouble().isFinite()))
    ) add(GlucoseField.READING)
    if (draft.moment !in glucoseMeasurementContexts) add(GlucoseField.MOMENT)
    if (draft.actualAt > now) add(GlucoseField.ACTUAL_TIME)
    if (!fitsTextLimit(draft.note, CaptureLimit.MEASUREMENT_NOTE.maximum, originalNote)) add(GlucoseField.NOTE)
    val scheduled = draft.context as? GlucoseRegistrationContext.Scheduled
    if (
        scheduled != null &&
        (
            scheduled.occurrence.id.isBlank() ||
                scheduled.occurrence.followUpId.isBlank()
            )
    ) {
        add(GlucoseField.SCHEDULED_REFERENCE)
    }
    if (
        draft.correctingDecisionId != null &&
        (
            draft.correctingDecisionId.isBlank() ||
                scheduled == null
            )
    ) {
        add(GlucoseField.SCHEDULED_REFERENCE)
    }
}

sealed interface GlucoseSaveResult {
    data class Stored(val record: MeasurementRecord) : GlucoseSaveResult
    data class AlreadyRecorded(val record: MeasurementRecord) : GlucoseSaveResult
    data class Invalid(val fields: Set<GlucoseField>) : GlucoseSaveResult
    data object ConsentRequired : GlucoseSaveResult
    data object AuthorizationRequired : GlucoseSaveResult
}

class GetGlucoseRecordsUseCase(private val repository: GlucoseRepository) {
    suspend operator fun invoke(actor: GlucoseActor) = repository.load(actor)
}

class SaveGlucoseMeasurementUseCase(
    private val repository: GlucoseRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(actor: GlucoseActor, draft: GlucoseDraft): GlucoseSaveResult {
        val capturedAt = now()
        val errors = validateGlucoseDraft(draft, capturedAt)
        if (errors.isNotEmpty()) return GlucoseSaveResult.Invalid(errors)
        if (!actor.authorizationCurrent || listOf(actor.profileId, actor.caregiverId, actor.personUnderCareId).any { it.isBlank() }) {
            return GlucoseSaveResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) return GlucoseSaveResult.ConsentRequired
        val record = draft.toRecord(capturedAt)
        val stored = draft.correctingDecisionId?.let { decisionId ->
            val occurrence =
                (draft.context as GlucoseRegistrationContext.Scheduled)
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
        return if (stored.id == record.id) {
            GlucoseSaveResult.Stored(stored)
        } else {
            GlucoseSaveResult.AlreadyRecorded(stored)
        }
    }
}

class CorrectGlucoseMeasurementUseCase(
    private val repository: GlucoseRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        draft: GlucoseDraft,
    ): GlucoseSaveResult {
        val capturedAt = now()
        if (!actor.isUsable()) return GlucoseSaveResult.AuthorizationRequired
        if (!repository.hasSensitiveConsent(actor)) {
            return GlucoseSaveResult.ConsentRequired
        }
        val originalNote = repository.load(actor).singleOrNull { it.id == draft.id }?.glucoseMetadata?.note
        val errors = validateGlucoseDraft(draft, capturedAt, originalNote)
        if (errors.isNotEmpty()) return GlucoseSaveResult.Invalid(errors)
        return GlucoseSaveResult.Stored(
            repository.revise(actor, draft.toRecord(capturedAt, originalNote)),
        )
    }
}

sealed interface GlucoseDeleteResult {
    data object Deleted : GlucoseDeleteResult
    data object ConsentRequired : GlucoseDeleteResult
    data object AuthorizationRequired : GlucoseDeleteResult
}

class DeleteGlucoseMeasurementUseCase(
    private val repository: GlucoseRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        recordId: String,
    ): GlucoseDeleteResult {
        if (!actor.isUsable()) {
            return GlucoseDeleteResult.AuthorizationRequired
        }
        if (!repository.hasSensitiveConsent(actor)) {
            return GlucoseDeleteResult.ConsentRequired
        }
        repository.softDelete(actor, recordId, now())
        return GlucoseDeleteResult.Deleted
    }
}

class GetGlucoseRevisionsUseCase(
    private val repository: GlucoseRepository,
) {
    suspend operator fun invoke(actor: GlucoseActor, recordId: String) =
        repository.loadRevisions(actor, recordId)
}

class AcceptGlucoseSensitiveConsentUseCase(
    private val repository: GlucoseRepository,
) {
    suspend operator fun invoke(
        actor: GlucoseActor,
        acceptedAtEpochMillis: Long,
    ) {
        check(actor.authorizationCurrent)
        repository.acceptSensitiveConsent(actor, acceptedAtEpochMillis)
    }
}

private fun GlucoseDraft.toRecord(capturedAt: LocalDateTime, originalNote: String? = null): MeasurementRecord {
    val normalizedReading = reading.trim().let {
        if (it.uppercase() in setOf("HI", "LO")) it.uppercase() else it
    }
    return MeasurementRecord(
        id = id,
        value = MeasurementValue.Glucose(normalizedReading, moment),
        occurredAt = actualAt,
        capturedAt = capturedAt,
        glucoseMetadata = context.metadata(
            if (note == originalNote) originalNote else note.trim().takeIf(String::isNotEmpty),
        ),
    )
}

private fun GlucoseActor.isUsable(): Boolean =
    authorizationCurrent &&
        listOf(profileId, caregiverId, personUnderCareId)
            .none(String::isBlank)
