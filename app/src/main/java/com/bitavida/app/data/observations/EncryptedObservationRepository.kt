package com.bitavida.app.data.observations

import android.content.Context
import com.bitavida.app.data.local.secure.*
import com.bitavida.app.data.medical.EncryptedMedicalCareRepository
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import com.bitavida.app.platform.reminder.ObservationReminderScheduler
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses the one encrypted application database and the existing sensitive-consent contract. */
class EncryptedObservationRepository(context: Context) : ObservationRepository {
    private val appContext = context.applicationContext
    private val medical = EncryptedMedicalCareRepository(appContext)

    override suspend fun hasSensitiveConsent(actor: GlucoseActor): Boolean = storage {
        requireAuthorized(actor)
        medical.hasActiveSensitiveDataConsent(
            actor.profileId, actor.caregiverId, actor.personUnderCareId,
            GLUCOSE_SENSITIVE_CONSENT_VERSION,
        )
    }

    override suspend fun acceptSensitiveConsent(actor: GlucoseActor, acceptedAtEpochMillis: Long) = storage {
        requireAuthorized(actor)
        if (!hasSensitiveConsent(actor)) {
            medical.recordSensitiveDataConsent(actor.profileId, actor.caregiverId,
                actor.personUnderCareId, GLUCOSE_SENSITIVE_CONSENT_VERSION, acceptedAtEpochMillis)
        }
    }

    override suspend fun load(actor: GlucoseActor): ObservationSnapshot = storage {
        requireAuthorized(actor)
        val dao = SecureDatabaseProvider.open(appContext).observationDao()
        dao.load(actor.profileId, actor.caregiverId, actor.personUnderCareId).toDomain()
    }

    override suspend fun saveRecord(actor: GlucoseActor, record: ObservationRecord): ObservationRecord = storage {
        requireAuthorized(actor)
        require(record.personUnderCareId == actor.personUnderCareId && record.actorId == actor.caregiverId)
        require(com.bitavida.app.domain.usecase.validateObservationText(record).isEmpty())
        val entity = ObservationRecordEntity(
            id = record.id, profileId = actor.profileId, caregiverId = actor.caregiverId,
            personUnderCareId = actor.personUnderCareId, observationId = record.observationId,
            occurredOnEpochDay = record.occurredOn.toEpochDay(), followUpId = record.followUpId,
            occurrenceId = record.occurrenceId, createdAt = record.createdAt.toString(),
            payload = ObservationPayloadCodec.encode(record),
        )
        val dao = SecureDatabaseProvider.open(appContext).observationDao()
        val snapshot = dao.load(actor.profileId, actor.caregiverId, actor.personUnderCareId).toDomain()
        snapshot.records.firstOrNull { it.id == record.id || (record.occurrenceId != null && it.occurrenceId == record.occurrenceId) }
            ?.let { return@storage it }
        run {
            record.occurrenceId?.let { id ->
                val scheduled = requireNotNull(record.scheduledAt)
                val event = com.bitavida.app.domain.usecase.projectObservationOccurrences(
                    scheduled.toLocalDate(), snapshot.followUps, snapshot.records,
                ).singleOrNull { it.id == id }
                requireNotNull(event)
                require(event.followUpId == record.followUpId && event.observationId == record.observationId &&
                    event.scheduledAt == scheduled)
            }
        }
        val stored = dao.create(entity, record.localPhotoIds, GLUCOSE_SENSITIVE_CONSENT_VERSION)
        reconcileRemindersAfterCommit(actor)
        ObservationPayloadCodec.decodeRecord(stored.payload)
    }

    override suspend fun appendRecordCorrection(
        actor: GlucoseActor,
        revision: ObservationRecordRevision,
        expectedRevisionNumber: Int,
    ): ObservationRecordRevision = storage {
        requireAuthorized(actor)
        val dao = SecureDatabaseProvider.open(appContext).observationDao()
        val snapshot = dao.load(actor.profileId, actor.caregiverId, actor.personUnderCareId).toDomain()
        snapshot.recordRevisions.firstOrNull { it.id == revision.id }?.let {
            require(it == revision)
            // DAO still revalidates consent inside the idempotent transaction below.
        }
        val original = requireNotNull(snapshot.records.firstOrNull { it.id == revision.recordId })
        require(com.bitavida.app.domain.usecase.validateObservationCorrection(
            original, revision, expectedRevisionNumber, actor, LocalDateTime.now()).isEmpty())
        val stored = dao.correctRecord(actor.profileId, actor.personUnderCareId,
            ObservationRecordRevisionEntity(
                revision.id, revision.recordId, revision.revisionNumber, revision.actorId,
                revision.correctedAt.toString(), requireNotNull(revision.reason),
                ObservationPayloadCodec.encode(revision.snapshot)),
            expectedRevisionNumber, revision.snapshot.localPhotoIds, GLUCOSE_SENSITIVE_CONSENT_VERSION)
        reconcileRemindersAfterCommit(actor)
        ObservationRecordRevision(stored.id, stored.recordId, stored.revisionNumber,
            ObservationPayloadCodec.decodeRecord(stored.payload), stored.reason,
            LocalDateTime.parse(stored.correctedAt), stored.actorId)
    }

    override suspend fun appendFollowUp(
        actor: GlucoseActor,
        followUp: ObservationFollowUp,
        expectedRevisionNumber: Int,
    ): ObservationFollowUp = storage {
        requireAuthorized(actor)
        val schedule = followUp.schedule
        require(schedule.personUnderCareId == actor.personUnderCareId)
        val revision = schedule.currentRevision
        require(revision.authorActorId == actor.caregiverId)
        val dao = SecureDatabaseProvider.open(appContext).observationDao()
        val previous = dao.load(actor.profileId, actor.caregiverId, actor.personUnderCareId)
            .toDomain().followUps.firstOrNull { it.schedule.id == schedule.id }?.schedule?.currentRevision
        require(com.bitavida.app.domain.usecase.validateObservationFollowUpText(revision, previous).isEmpty())
        dao.append(
            ObservationFollowUpEntity(schedule.id, actor.profileId, actor.caregiverId,
                actor.personUnderCareId, followUp.observationId, schedule.createdAt.toString(),
                revision.revisionNumber),
            ObservationFollowUpRevisionEntity(revision.id, schedule.id, revision.revisionNumber,
                revision.authorActorId, revision.capturedAt.toString(), ObservationPayloadCodec.encode(revision)),
            expectedRevisionNumber, GLUCOSE_SENSITIVE_CONSENT_VERSION,
        )
        val stored = dao.load(actor.profileId, actor.caregiverId, actor.personUnderCareId).toDomain().followUps
            .single { it.schedule.id == schedule.id }
        reconcileRemindersAfterCommit(actor)
        stored
    }

    private suspend fun reconcileRemindersAfterCommit(actor: GlucoseActor) {
        try {
            // Await the scheduler's process-wide reconciliation lock before reporting success.
            ObservationReminderScheduler(appContext).reconcileLatest(actor)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The commit remains valid. Startup/system events retry; delivery rechecks current state.
        }
    }

    private fun requireAuthorized(actor: GlucoseActor) {
        if (!actor.authorizationCurrent) throw GlucoseAuthorizationException()
    }

    private suspend fun <T> storage(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: GlucoseAuthorizationException) { throw error }
        catch (error: Exception) { throw ObservationStorageException(error) }
    }

}

class ObservationStorageException(cause: Throwable? = null) : Exception("Observation storage unavailable.", cause)

internal fun PersistedObservations.toDomain(): ObservationSnapshot {
    val byFollowUp = revisions.groupBy { it.followUpId }
    val originals = records.map { entity ->
            ObservationPayloadCodec.decodeRecord(entity.payload).also {
                check(it.id == entity.id && it.personUnderCareId == entity.personUnderCareId &&
                    it.observationId == entity.observationId && it.followUpId == entity.followUpId &&
                    it.occurrenceId == entity.occurrenceId && it.occurredOn.toEpochDay() == entity.occurredOnEpochDay &&
                    it.actorId == entity.caregiverId && it.createdAt.toString() == entity.createdAt)
            }
        }
    val byRecord = recordRevisions.groupBy { it.recordId }
    val history = originals.flatMap { original ->
        val corrections = byRecord[original.id].orEmpty().sortedBy { it.revisionNumber }
        check(corrections.map { it.revisionNumber } == (2..(corrections.size + 1)).toList())
        listOf(ObservationRecordRevision(original.id, original.id, 1, original, null,
            original.createdAt, original.actorId)) + corrections.map { revision ->
            val fact = ObservationPayloadCodec.decodeRecord(revision.payload)
            check(fact.id == original.id && fact.observationId == original.observationId &&
                fact.personUnderCareId == original.personUnderCareId && fact.actorId == original.actorId &&
                fact.createdAt == original.createdAt && fact.followUpId == original.followUpId &&
                fact.occurrenceId == original.occurrenceId && fact.scheduledAt == original.scheduledAt)
            ObservationRecordRevision(revision.id, revision.recordId, revision.revisionNumber,
                fact, revision.reason, LocalDateTime.parse(revision.correctedAt), revision.actorId)
        }
    }
    return ObservationSnapshot(
        records = history.groupBy { it.recordId }.values.map { versions -> versions.last().snapshot },
        recordRevisions = history,
        followUps = followUps.map { entity ->
            ObservationFollowUp(entity.observationId, GlucoseFollowUp(
                id = entity.id, personUnderCareId = entity.personUnderCareId,
                createdAt = LocalDateTime.parse(entity.createdAt), createdByActorId = entity.caregiverId,
                revisions = byFollowUp[entity.id].orEmpty().map { revision ->
                    ObservationPayloadCodec.decodeRevision(revision.payload).also {
                        check(it.id == revision.id && it.followUpId == entity.id &&
                            it.revisionNumber == revision.revisionNumber && it.authorActorId == revision.actorId &&
                            it.capturedAt.toString() == revision.capturedAt)
                    }
                },
            )).also { check(it.schedule.currentRevision.revisionNumber == entity.currentRevisionNumber) }
        },
    )
}
