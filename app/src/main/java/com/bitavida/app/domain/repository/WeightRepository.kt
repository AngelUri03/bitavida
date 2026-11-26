package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.WeightRevision
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.MeasurementRecord
import java.time.LocalDateTime

interface WeightRepository {
    suspend fun load(actor: GlucoseActor): List<MeasurementRecord>
    suspend fun loadTarget(actor: GlucoseActor): WeightTarget?
    suspend fun saveTarget(
        actor: GlucoseActor,
        target: WeightTarget,
    ): WeightTarget
    suspend fun deleteTarget(actor: GlucoseActor)
    suspend fun hasSensitiveConsent(actor: GlucoseActor): Boolean
    suspend fun acceptSensitiveConsent(
        actor: GlucoseActor,
        acceptedAtEpochMillis: Long,
    )

    suspend fun create(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord

    suspend fun createCorrectingNotPerformed(
        actor: GlucoseActor,
        record: MeasurementRecord,
        decisionRevision: GlucoseOccurrenceDecisionRevision,
    ): MeasurementRecord

    suspend fun revise(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord

    suspend fun softDelete(
        actor: GlucoseActor,
        recordId: String,
        deletedAt: LocalDateTime,
    )

    suspend fun loadRevisions(
        actor: GlucoseActor,
        recordId: String,
    ): List<WeightRevision>

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
