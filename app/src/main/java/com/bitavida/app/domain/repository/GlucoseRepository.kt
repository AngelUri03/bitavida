package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseRevision
import com.bitavida.app.domain.model.MeasurementRecord
import java.time.LocalDateTime

data class GlucoseActor(
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val authorizationCurrent: Boolean,
)

interface GlucoseRepository {
    suspend fun load(actor: GlucoseActor): List<MeasurementRecord>
    suspend fun hasSensitiveConsent(actor: GlucoseActor): Boolean
    suspend fun acceptSensitiveConsent(actor: GlucoseActor, acceptedAtEpochMillis: Long)
    /** Atomically inserts a declared event and its creation audit. */
    suspend fun create(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord

    /**
     * Atomically creates the real reading and appends the audit revision that
     * corrects a previous explicit "not performed" declaration.
     */
    suspend fun createCorrectingNotPerformed(
        actor: GlucoseActor,
        record: MeasurementRecord,
        decisionRevision: GlucoseOccurrenceDecisionRevision,
    ): MeasurementRecord

    /** Replaces only the active snapshot after appending a complete correction revision. */
    suspend fun revise(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord

    /** Excludes the active snapshot while retaining revisions and audit. */
    suspend fun softDelete(
        actor: GlucoseActor,
        recordId: String,
        deletedAt: LocalDateTime,
    )

    suspend fun loadRevisions(
        actor: GlucoseActor,
        recordId: String,
    ): List<GlucoseRevision>

    suspend fun loadFollowUp(actor: GlucoseActor): GlucoseFollowUp?

    suspend fun createFollowUp(
        actor: GlucoseActor,
        followUp: GlucoseFollowUp,
    ): GlucoseFollowUp

    suspend fun appendFollowUpRevision(
        actor: GlucoseActor,
        followUpId: String,
        expectedRevisionNumber: Int,
        revision: GlucoseFollowUpRevision,
    ): GlucoseFollowUp

    suspend fun loadOccurrenceDecisions(
        actor: GlucoseActor,
        followUpId: String,
    ): List<GlucoseOccurrenceDecision>

    suspend fun confirmOccurrenceDecision(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
    ): GlucoseOccurrenceDecision

    suspend fun correctOccurrenceDecision(
        actor: GlucoseActor,
        revision: GlucoseOccurrenceDecisionRevision,
    ): GlucoseOccurrenceDecision

    suspend fun loadOccurrenceDecisionRevisions(
        actor: GlucoseActor,
        occurrenceId: String,
    ): List<GlucoseOccurrenceDecisionRevision>
}

class GlucoseStorageException(cause: Throwable? = null) : Exception("Glucose storage unavailable.", cause)
class GlucoseAuthorizationException : Exception("Glucose operation is not authorized.")
const val GLUCOSE_SENSITIVE_CONSENT_VERSION = "local-health-storage-1.0"
