package com.bitavida.app.data.measurements

import android.content.Context
import com.bitavida.app.data.local.secure.GlucoseAuditEntity
import com.bitavida.app.data.local.secure.GlucoseFollowUpEntity
import com.bitavida.app.data.local.secure.GlucoseFollowUpRevisionEntity
import com.bitavida.app.data.local.secure.GlucoseFollowUpSlotEntity
import com.bitavida.app.data.local.secure.GlucoseMeasurementEntity
import com.bitavida.app.data.local.secure.GlucoseOccurrenceDecisionEntity
import com.bitavida.app.data.local.secure.GlucoseOccurrenceDecisionRevisionEntity
import com.bitavida.app.data.local.secure.GlucoseRevisionEntity
import com.bitavida.app.data.local.secure.PersistedGlucoseFollowUp
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.data.medical.EncryptedMedicalCareRepository
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseEntryKind
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpSlot
import com.bitavida.app.domain.model.GlucoseFollowUpTimingType
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionKind
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionRevisionOperation
import com.bitavida.app.domain.model.GlucoseRecordMetadata
import com.bitavida.app.domain.model.GlucoseRevision
import com.bitavida.app.domain.model.GlucoseRevisionOperation
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.GlucoseAuthorizationException
import com.bitavida.app.domain.repository.GlucoseRepository
import com.bitavida.app.domain.repository.GlucoseStorageException
import com.bitavida.app.platform.reminder.GlucoseReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EncryptedGlucoseRepository(context: Context) : GlucoseRepository {
    private val appContext = context.applicationContext
    private val medical = EncryptedMedicalCareRepository(appContext)

    private companion object {
        val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reminderMutex = Mutex()
    }

    override suspend fun load(actor: GlucoseActor): List<MeasurementRecord> =
        storage {
            SecureDatabaseProvider.open(appContext)
                .glucoseDao()
                .load(actor.profileId, actor.personUnderCareId)
                .map(GlucoseMeasurementEntity::toDomain)
        }

    override suspend fun loadRevisions(
        actor: GlucoseActor,
        recordId: String,
    ): List<GlucoseRevision> = storage {
        SecureDatabaseProvider.open(appContext)
            .glucoseDao()
            .loadRevisions(
                actor.profileId,
                actor.personUnderCareId,
                recordId,
            )
            .map(GlucoseRevisionEntity::toDomain)
    }

    override suspend fun hasSensitiveConsent(actor: GlucoseActor): Boolean =
        storage {
            medical.hasActiveSensitiveDataConsent(
                actor.profileId,
                actor.caregiverId,
                actor.personUnderCareId,
                GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
        }

    override suspend fun acceptSensitiveConsent(
        actor: GlucoseActor,
        acceptedAtEpochMillis: Long,
    ) = storage {
        if (!actor.authorizationCurrent) {
            throw GlucoseAuthorizationException()
        }
        if (!hasSensitiveConsent(actor)) {
            medical.recordSensitiveDataConsent(
                actor.profileId,
                actor.caregiverId,
                actor.personUnderCareId,
                GLUCOSE_SENSITIVE_CONSENT_VERSION,
                acceptedAtEpochMillis,
            )
        }
    }

    override suspend fun create(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord = createRecord(actor, record, null)

    override suspend fun createCorrectingNotPerformed(
        actor: GlucoseActor,
        record: MeasurementRecord,
        decisionRevision: GlucoseOccurrenceDecisionRevision,
    ): MeasurementRecord = createRecord(actor, record, decisionRevision)

    private suspend fun createRecord(
        actor: GlucoseActor,
        record: MeasurementRecord,
        decisionRevision: GlucoseOccurrenceDecisionRevision?,
    ): MeasurementRecord = storage {
        requireAuthorized(actor)
        val entity = record.toEntity(actor)
        val audit = entity.audit(
            id = record.id,
            operation = GlucoseRevisionOperation.CREATED,
            capturedAt = record.capturedAt,
        )
        val revision = entity.revision(
            id = record.id + ":revision:1",
            revisionNumber = 1,
            operation = GlucoseRevisionOperation.CREATED,
            capturedAt = record.capturedAt,
        )
        SecureDatabaseProvider.open(appContext)
            .glucoseDao()
            .create(
                entity = entity,
                audit = audit,
                revision = revision,
                consentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                decisionRevision = decisionRevision?.toEntity(),
            )
            .toDomain()
    }

    override suspend fun revise(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord = storage {
        requireAuthorized(actor)
        val entity = record.toEntity(actor)
        val operationId = UUID.randomUUID().toString()
        SecureDatabaseProvider.open(appContext)
            .glucoseDao()
            .revise(
                entity = entity,
                audit = entity.audit(
                    id = operationId,
                    operation = GlucoseRevisionOperation.CORRECTED,
                    capturedAt = record.capturedAt,
                ),
                revision = entity.revision(
                    id = operationId,
                    revisionNumber = 0,
                    operation = GlucoseRevisionOperation.CORRECTED,
                    capturedAt = record.capturedAt,
                ),
                consentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
            .toDomain()
    }

    override suspend fun softDelete(
        actor: GlucoseActor,
        recordId: String,
        deletedAt: LocalDateTime,
    ) = storage {
        requireAuthorized(actor)
        val operationId = UUID.randomUUID().toString()
        SecureDatabaseProvider.open(appContext)
            .glucoseDao()
            .softDelete(
                profileId = actor.profileId,
                actorId = actor.caregiverId,
                personId = actor.personUnderCareId,
                recordId = recordId,
                deletedAt = deletedAt.toString(),
                audit = GlucoseAuditEntity(
                    id = operationId,
                    recordId = recordId,
                    actorId = actor.caregiverId,
                    operation = GlucoseRevisionOperation.DELETED.name,
                    capturedAt = deletedAt.toString(),
                ),
                revision = GlucoseRevisionEntity(
                    id = operationId,
                    recordId = recordId,
                    revisionNumber = 0,
                    actorId = actor.caregiverId,
                    operation = GlucoseRevisionOperation.DELETED.name,
                    reading = "",
                    actualContext = GlucoseContext.OTHER.name,
                    actualAt = deletedAt.toString(),
                    note = null,
                    capturedAt = deletedAt.toString(),
                ),
                consentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
    }

    override suspend fun loadFollowUp(actor: GlucoseActor): GlucoseFollowUp? = storage {
        SecureDatabaseProvider.open(appContext).glucoseFollowUpDao()
            .load(actor.profileId, actor.personUnderCareId)
            ?.toDomain()
    }

    override suspend fun createFollowUp(
        actor: GlucoseActor,
        followUp: GlucoseFollowUp,
    ): GlucoseFollowUp = storage {
        requireAuthorized(actor)
        val revision = followUp.revisions.single()
        val database = SecureDatabaseProvider.open(appContext)
        database.glucoseFollowUpDao().create(
            followUp.toEntity(actor),
            revision.toEntity(),
            revision.slots.map { it.toEntity(revision.id) },
        )
        val stored = requireNotNull(
            database.glucoseFollowUpDao().load(actor.profileId, actor.personUnderCareId),
        ).toDomain()
        reconcileRemindersAfterCommit(actor)
        stored
    }

    override suspend fun appendFollowUpRevision(
        actor: GlucoseActor,
        followUpId: String,
        expectedRevisionNumber: Int,
        revision: GlucoseFollowUpRevision,
    ): GlucoseFollowUp = storage {
        requireAuthorized(actor)
        val database = SecureDatabaseProvider.open(appContext)
        database.glucoseFollowUpDao().appendRevision(
            actor.profileId,
            actor.personUnderCareId,
            followUpId,
            expectedRevisionNumber,
            revision.toEntity(),
            revision.slots.map { it.toEntity(revision.id) },
        )
        val stored = requireNotNull(
            database.glucoseFollowUpDao().load(actor.profileId, actor.personUnderCareId),
        ).toDomain()
        reconcileRemindersAfterCommit(actor)
        stored
    }

    override suspend fun loadOccurrenceDecisions(
        actor: GlucoseActor,
        followUpId: String,
    ): List<GlucoseOccurrenceDecision> = storage {
        SecureDatabaseProvider.open(appContext).glucoseFollowUpDao()
            .decisions(actor.profileId, actor.personUnderCareId, followUpId)
            .map(GlucoseOccurrenceDecisionEntity::toDomain)
    }

    override suspend fun confirmOccurrenceDecision(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
    ): GlucoseOccurrenceDecision = storage {
        requireAuthorized(actor)
        SecureDatabaseProvider.open(appContext).glucoseFollowUpDao()
            .confirmDecision(
                entity = decision.toEntity(),
                revision = decision.initialRevision().toEntity(),
            )
            .toDomain()
    }

    override suspend fun correctOccurrenceDecision(
        actor: GlucoseActor,
        revision: GlucoseOccurrenceDecisionRevision,
    ): GlucoseOccurrenceDecision = storage {
        requireAuthorized(actor)
        SecureDatabaseProvider.open(appContext).glucoseFollowUpDao()
            .correctDecision(revision.toEntity())
            .toDomain()
    }

    override suspend fun loadOccurrenceDecisionRevisions(
        actor: GlucoseActor,
        occurrenceId: String,
    ): List<GlucoseOccurrenceDecisionRevision> = storage {
        SecureDatabaseProvider.open(appContext).glucoseFollowUpDao()
            .decisionRevisions(
                actor.profileId,
                actor.personUnderCareId,
                occurrenceId,
            )
            .map(GlucoseOccurrenceDecisionRevisionEntity::toDomain)
    }

    private fun requireAuthorized(actor: GlucoseActor) {
        if (!actor.authorizationCurrent) {
            throw GlucoseAuthorizationException()
        }
    }

    private fun reconcileRemindersAfterCommit(actor: GlucoseActor) {
        val context = appContext
        reminderScope.launch {
            reminderMutex.withLock {
                try {
                    val latest = SecureDatabaseProvider.open(context)
                        .glucoseFollowUpDao()
                        .load(actor.profileId, actor.personUnderCareId)
                        ?.toDomain()
                        ?: return@withLock
                    GlucoseReminderScheduler(context).reconcile(latest)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The committed follow-up remains authoritative; system events retry reconciliation.
                }
            }
        }
    }

    private suspend fun <T> storage(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: GlucoseAuthorizationException) {
                throw error
            } catch (error: Exception) {
                throw GlucoseStorageException(error)
            }
        }
}

private fun MeasurementRecord.toEntity(
    actor: GlucoseActor,
): GlucoseMeasurementEntity {
    val glucose = value as MeasurementValue.Glucose
    val metadata = requireNotNull(glucoseMetadata)
    return GlucoseMeasurementEntity(
        id = id,
        profileId = actor.profileId,
        caregiverId = actor.caregiverId,
        personUnderCareId = actor.personUnderCareId,
        reading = glucose.reading,
        actualContext = requireNotNull(glucose.context).name,
        actualAt = occurredAt.toString(),
        capturedAt = capturedAt.toString(),
        origin = "CAREGIVER_DECLARED",
        entryKind = metadata.entryKind.name,
        followUpId = metadata.followUpId,
        scheduledOccurrenceId = metadata.scheduledOccurrenceId,
        scheduledAt = metadata.scheduledAt?.toString(),
        expectedContext = metadata.expectedContext?.name,
        note = metadata.note,
    )
}

private fun GlucoseMeasurementEntity.audit(
    id: String,
    operation: GlucoseRevisionOperation,
    capturedAt: LocalDateTime,
) = GlucoseAuditEntity(
    id = id,
    recordId = this.id,
    actorId = caregiverId,
    operation = operation.name,
    capturedAt = capturedAt.toString(),
)

private fun GlucoseMeasurementEntity.revision(
    id: String,
    revisionNumber: Int,
    operation: GlucoseRevisionOperation,
    capturedAt: LocalDateTime,
) = GlucoseRevisionEntity(
    id = id,
    recordId = this.id,
    revisionNumber = revisionNumber,
    actorId = caregiverId,
    operation = operation.name,
    reading = reading,
    actualContext = actualContext,
    actualAt = actualAt,
    note = note,
    capturedAt = capturedAt.toString(),
)

internal fun GlucoseMeasurementEntity.toDomain() = MeasurementRecord(
    id = id,
    value = MeasurementValue.Glucose(
        reading,
        GlucoseContext.valueOf(actualContext),
    ),
    occurredAt = LocalDateTime.parse(actualAt),
    capturedAt = LocalDateTime.parse(capturedAt),
    glucoseMetadata = GlucoseRecordMetadata(
        entryKind = GlucoseEntryKind.valueOf(entryKind),
        followUpId = followUpId,
        scheduledOccurrenceId = scheduledOccurrenceId,
        scheduledAt = scheduledAt?.let(LocalDateTime::parse),
        expectedContext = expectedContext?.let(GlucoseContext::valueOf),
        note = note,
    ),
)

private fun GlucoseFollowUp.toEntity(actor: GlucoseActor) = GlucoseFollowUpEntity(
    id = id,
    profileId = actor.profileId,
    caregiverId = actor.caregiverId,
    personUnderCareId = actor.personUnderCareId,
    createdAt = createdAt.toString(),
    createdByActorId = createdByActorId,
    currentRevisionNumber = currentRevision.revisionNumber,
)

private fun GlucoseFollowUpRevision.toEntity() = GlucoseFollowUpRevisionEntity(
    id = id,
    followUpId = followUpId,
    revisionNumber = revisionNumber,
    indication = indication,
    frequency = frequency.name,
    weekdaysMask = weekdays.fold(0) { mask, day -> mask or (1 shl day.ordinal) },
    everyNDaysInterval = everyNDaysInterval,
    everyNDaysReferenceEpochDay = everyNDaysReferenceDate?.toEpochDay(),
    startsOnEpochDay = startsOn.toEpochDay(),
    endsOnEpochDay = endsOn?.toEpochDay(),
    effectiveFromEpochDay = effectiveFrom.toEpochDay(),
    remindersEnabled = remindersEnabled,
    changeKind = changeKind.name,
    changeReason = changeReason,
    capturedAt = capturedAt.toString(),
    authorActorId = authorActorId,
)

private fun GlucoseFollowUpSlot.toEntity(revisionId: String) = GlucoseFollowUpSlotEntity(
    id = "$revisionId:$id",
    logicalId = id,
    followUpRevisionId = revisionId,
    localTimeMinute = time.toSecondOfDay() / 60,
    timingType = timingType.name,
    windowEndMinute = windowEnd?.toSecondOfDay()?.div(60),
    endsNextDay = endsNextDay,
    suggestedContext = suggestedContext.name,
    reminderEnabled = reminderEnabled,
    reminderMinute = reminderTime?.toSecondOfDay()?.div(60),
    reminderNextDay = reminderNextDay,
)

private fun PersistedGlucoseFollowUp.toDomain(): GlucoseFollowUp {
    val slotsByRevision = slots.groupBy(GlucoseFollowUpSlotEntity::followUpRevisionId)
    return GlucoseFollowUp(
        id = followUp.id,
        personUnderCareId = followUp.personUnderCareId,
        createdAt = LocalDateTime.parse(followUp.createdAt),
        createdByActorId = followUp.createdByActorId,
        revisions = revisions.map { entity ->
            entity.toDomain(slotsByRevision[entity.id].orEmpty())
        },
    )
}

private fun GlucoseFollowUpRevisionEntity.toDomain(
    slots: List<GlucoseFollowUpSlotEntity>,
) = GlucoseFollowUpRevision(
    id = id,
    followUpId = followUpId,
    revisionNumber = revisionNumber,
    indication = indication,
    frequency = GlucoseFollowUpFrequency.valueOf(frequency),
    weekdays = DayOfWeek.entries.filterTo(mutableSetOf()) {
        (weekdaysMask and (1 shl it.ordinal)) != 0
    },
    everyNDaysInterval = everyNDaysInterval,
    everyNDaysReferenceDate = everyNDaysReferenceEpochDay?.let(LocalDate::ofEpochDay),
    startsOn = LocalDate.ofEpochDay(startsOnEpochDay),
    endsOn = endsOnEpochDay?.let(LocalDate::ofEpochDay),
    effectiveFrom = LocalDate.ofEpochDay(effectiveFromEpochDay),
    slots = slots.map { it.toDomain() },
    remindersEnabled = remindersEnabled,
    changeKind = GlucoseFollowUpChangeKind.valueOf(changeKind),
    changeReason = changeReason,
    capturedAt = LocalDateTime.parse(capturedAt),
    authorActorId = authorActorId,
)

private fun GlucoseFollowUpSlotEntity.toDomain() = GlucoseFollowUpSlot(
    id = logicalId,
    timingType = GlucoseFollowUpTimingType.valueOf(timingType),
    time = LocalTime.ofSecondOfDay(localTimeMinute * 60L),
    windowEnd = windowEndMinute?.let {
        LocalTime.ofSecondOfDay(it * 60L)
    },
    endsNextDay = endsNextDay,
    suggestedContext = GlucoseContext.valueOf(suggestedContext),
    reminderEnabled = reminderEnabled,
    reminderTime = reminderMinute?.let { LocalTime.ofSecondOfDay(it * 60L) },
    reminderNextDay = reminderNextDay,
)

private fun GlucoseOccurrenceDecision.toEntity() = GlucoseOccurrenceDecisionEntity(
    id = id,
    followUpId = followUpId,
    followUpRevisionId = followUpRevisionId,
    occurrenceId = occurrenceId,
    scheduledAt = scheduledAt.toString(),
    kind = kind.name,
    reason = reason,
    capturedAt = capturedAt.toString(),
    actorId = actorId,
)

private fun GlucoseOccurrenceDecision.initialRevision() =
    GlucoseOccurrenceDecisionRevision(
        id = "$id:revision:1",
        decisionId = id,
        occurrenceId = occurrenceId,
        revisionNumber = 1,
        operation = GlucoseOccurrenceDecisionRevisionOperation.CONFIRMED,
        reason = reason,
        capturedAt = capturedAt,
        actorId = actorId,
    )

private fun GlucoseOccurrenceDecisionRevision.toEntity() =
    GlucoseOccurrenceDecisionRevisionEntity(
        id = id,
        decisionId = decisionId,
        occurrenceId = occurrenceId,
        revisionNumber = revisionNumber,
        operation = operation.name,
        reason = reason,
        capturedAt = capturedAt.toString(),
        actorId = actorId,
    )

private fun GlucoseOccurrenceDecisionRevisionEntity.toDomain() =
    GlucoseOccurrenceDecisionRevision(
        id = id,
        decisionId = decisionId,
        occurrenceId = occurrenceId,
        revisionNumber = revisionNumber,
        operation = GlucoseOccurrenceDecisionRevisionOperation.valueOf(operation),
        reason = reason,
        capturedAt = LocalDateTime.parse(capturedAt),
        actorId = actorId,
    )

private fun GlucoseOccurrenceDecisionEntity.toDomain() = GlucoseOccurrenceDecision(
    id = id,
    followUpId = followUpId,
    followUpRevisionId = followUpRevisionId,
    occurrenceId = occurrenceId,
    scheduledAt = LocalDateTime.parse(scheduledAt),
    kind = GlucoseOccurrenceDecisionKind.valueOf(kind),
    reason = reason,
    capturedAt = LocalDateTime.parse(capturedAt),
    actorId = actorId,
)

internal fun GlucoseRevisionEntity.toDomain() = GlucoseRevision(
    id = id,
    recordId = recordId,
    revisionNumber = revisionNumber,
    operation = GlucoseRevisionOperation.valueOf(operation),
    reading = reading,
    context = GlucoseContext.valueOf(actualContext),
    actualAt = LocalDateTime.parse(actualAt),
    note = note,
    actorId = actorId,
    capturedAt = LocalDateTime.parse(capturedAt),
)
