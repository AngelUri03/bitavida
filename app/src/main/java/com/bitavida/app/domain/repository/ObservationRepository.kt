package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.ObservationFollowUp
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationSnapshot

interface ObservationRepository {
    suspend fun hasSensitiveConsent(actor: GlucoseActor): Boolean
    suspend fun acceptSensitiveConsent(actor: GlucoseActor, acceptedAtEpochMillis: Long)
    suspend fun load(actor: GlucoseActor): ObservationSnapshot
    /** Stable record and occurrence identities make retries idempotent. */
    suspend fun saveRecord(actor: GlucoseActor, record: ObservationRecord): ObservationRecord
    /** A correction never overwrites the original; the stable revision id makes retries idempotent. */
    suspend fun appendRecordCorrection(
        actor: GlucoseActor,
        revision: com.bitavida.app.domain.model.ObservationRecordRevision,
        expectedRevisionNumber: Int,
    ): com.bitavida.app.domain.model.ObservationRecordRevision =
        throw UnsupportedOperationException("Audited corrections unavailable.")
    /** Zero creates; subsequent writes append one revision and preserve every earlier snapshot. */
    suspend fun appendFollowUp(
        actor: GlucoseActor,
        followUp: ObservationFollowUp,
        expectedRevisionNumber: Int,
    ): ObservationFollowUp
}
