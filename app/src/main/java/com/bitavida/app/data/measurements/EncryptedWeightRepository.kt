package com.bitavida.app.data.measurements

import android.content.Context
import com.bitavida.app.data.local.secure.WeightAuditEntity
import com.bitavida.app.data.local.secure.WeightFollowUpEntity
import com.bitavida.app.data.local.secure.WeightFollowUpRevisionEntity
import com.bitavida.app.data.local.secure.WeightFollowUpSlotEntity
import com.bitavida.app.data.local.secure.WeightMeasurementEntity
import com.bitavida.app.data.local.secure.WeightOccurrenceDecisionEntity
import com.bitavida.app.data.local.secure.WeightOccurrenceDecisionRevisionEntity
import com.bitavida.app.data.local.secure.WeightRevisionEntity
import com.bitavida.app.data.local.secure.WeightTargetEntity
import com.bitavida.app.data.local.secure.PersistedWeightFollowUp
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.data.medical.EncryptedMedicalCareRepository
import com.bitavida.app.domain.model.WeightEntryKind
import com.bitavida.app.domain.model.WeightRecordMetadata
import com.bitavida.app.domain.model.WeightRevision
import com.bitavida.app.domain.model.WeightRevisionOperation
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.model.GlucoseContext
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
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.repository.WeightRepository
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.GlucoseAuthorizationException
import com.bitavida.app.domain.repository.GlucoseStorageException
import com.bitavida.app.platform.reminder.WeightReminderScheduler
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

class EncryptedWeightRepository(
    context: Context,
) : WeightRepository {
    private val appContext = context.applicationContext
    private val medical = EncryptedMedicalCareRepository(appContext)

    private companion object {
        val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reminderMutex = Mutex()
    }

    override suspend fun load(actor: GlucoseActor): List<MeasurementRecord> =
        storage {
            SecureDatabaseProvider.open(appContext)
                .weightDao()
                .load(actor.profileId, actor.personUnderCareId)
                .map(WeightMeasurementEntity::toDomain)
        }

    override suspend fun loadTarget(actor: GlucoseActor): WeightTarget? =
        storage {
            SecureDatabaseProvider.open(appContext)
                .weightDao()
                .loadTarget(actor.profileId, actor.personUnderCareId)
                ?.toDomain()
        }

    override suspend fun saveTarget(
        actor: GlucoseActor,
        target: WeightTarget,
    ): WeightTarget = storage {
        requireAuthorized(actor)
        SecureDatabaseProvider.open(appContext)
            .weightDao()
            .saveTarget(
                entity = WeightTargetEntity(
                    personUnderCareId = actor.personUnderCareId,
                    profileId = actor.profileId,
                    caregiverId = actor.caregiverId,
                    kilograms = target.kilograms.toPlainString(),
                ),
                consentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
            .toDomain()
    }

    override suspend fun deleteTarget(actor: GlucoseActor) = storage {
        requireAuthorized(actor)
        SecureDatabaseProvider.open(appContext)
            .weightDao()
            .deleteTarget(
                profileId = actor.profileId,
                actorId = actor.caregiverId,
                personId = actor.personUnderCareId,
                consentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
    }

    override suspend fun loadRevisions(
        actor: GlucoseActor,
        recordId: String,
    ): List<WeightRevision> = storage {
        SecureDatabaseProvider.open(appContext)
            .weightDao()
            .loadRevisions(
                actor.profileId,
                actor.personUnderCareId,
                recordId,
            )
            .map(WeightRevisionEntity::toDomain)
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
        requireAuthorized(actor)
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
        val entity = record.toWeightEntity(actor)
        val audit = entity.audit(
            id = record.id,
            operation = WeightRevisionOperation.CREATED,
            capturedAt = record.capturedAt,
        )
        val revision = entity.revision(
            id = record.id + ":revision:1",
            revisionNumber = 1,
            operation = WeightRevisionOperation.CREATED,
            capturedAt = record.capturedAt,
        )
        SecureDatabaseProvider.open(appContext)
            .weightDao()
            .create(
                entity = entity,
                audit = audit,
                revision = revision,
                consentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
                decisionRevision =
                    decisionRevision?.toWeightEntity(),
            )
            .toDomain()
    }

    override suspend fun revise(
        actor: GlucoseActor,
        record: MeasurementRecord,
    ): MeasurementRecord = storage {
        requireAuthorized(actor)
        val entity = record.toWeightEntity(actor)
        val operationId = UUID.randomUUID().toString()
        SecureDatabaseProvider.open(appContext)
            .weightDao()
            .revise(
                entity = entity,
                audit = entity.audit(
                    operationId,
                    WeightRevisionOperation.CORRECTED,
                    record.capturedAt,
                ),
                revision = entity.revision(
                    operationId,
                    0,
                    WeightRevisionOperation.CORRECTED,
                    record.capturedAt,
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
            .weightDao()
            .softDelete(
                profileId = actor.profileId,
                actorId = actor.caregiverId,
                personId = actor.personUnderCareId,
                recordId = recordId,
                deletedAt = deletedAt.toString(),
                audit = WeightAuditEntity(
                    id = operationId,
                    recordId = recordId,
                    actorId = actor.caregiverId,
                    operation = WeightRevisionOperation.DELETED.name,
                    capturedAt = deletedAt.toString(),
                ),
                revision = WeightRevisionEntity(
                    id = operationId,
                    recordId = recordId,
                    revisionNumber = 0,
                    actorId = actor.caregiverId,
                    operation = WeightRevisionOperation.DELETED.name,
                    kilograms = "0",
                    heightMeters = null,
                    actualContext = GlucoseContext.UNSPECIFIED.name,
                    actualAt = deletedAt.toString(),
                    note = null,
                    capturedAt = deletedAt.toString(),
                ),
                consentVersion = GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
    }

    override suspend fun loadFollowUp(actor: GlucoseActor): GlucoseFollowUp? =
        storage {
            SecureDatabaseProvider.open(appContext)
                .weightFollowUpDao()
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
        database.weightFollowUpDao().create(
            followUp.toWeightEntity(actor),
            revision.toWeightEntity(),
            revision.slots.map {
                it.toWeightEntity(revision.id)
            },
        )
        val stored = requireNotNull(
            database.weightFollowUpDao()
                .load(actor.profileId, actor.personUnderCareId),
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
        database.weightFollowUpDao().appendRevision(
            actor.profileId,
            actor.personUnderCareId,
            followUpId,
            expectedRevisionNumber,
            revision.toWeightEntity(),
            revision.slots.map {
                it.toWeightEntity(revision.id)
            },
        )
        val stored = requireNotNull(
            database.weightFollowUpDao()
                .load(actor.profileId, actor.personUnderCareId),
        ).toDomain()
        reconcileRemindersAfterCommit(actor)
        stored
    }

    override suspend fun loadOccurrenceDecisions(
        actor: GlucoseActor,
        followUpId: String,
    ): List<GlucoseOccurrenceDecision> = storage {
        SecureDatabaseProvider.open(appContext)
            .weightFollowUpDao()
            .decisions(actor.profileId, actor.personUnderCareId, followUpId)
            .map(WeightOccurrenceDecisionEntity::toDomain)
    }

    override suspend fun confirmOccurrenceDecision(
        actor: GlucoseActor,
        decision: GlucoseOccurrenceDecision,
    ): GlucoseOccurrenceDecision = storage {
        requireAuthorized(actor)
        SecureDatabaseProvider.open(appContext)
            .weightFollowUpDao()
            .confirmDecision(
                decision.toWeightEntity(),
                decision.initialRevision().toWeightEntity(),
            )
            .toDomain()
    }

    override suspend fun correctOccurrenceDecision(
        actor: GlucoseActor,
        revision: GlucoseOccurrenceDecisionRevision,
    ): GlucoseOccurrenceDecision = storage {
        requireAuthorized(actor)
        SecureDatabaseProvider.open(appContext)
            .weightFollowUpDao()
            .correctDecision(revision.toWeightEntity())
            .toDomain()
    }

    override suspend fun loadOccurrenceDecisionRevisions(
        actor: GlucoseActor,
        occurrenceId: String,
    ): List<GlucoseOccurrenceDecisionRevision> = storage {
        SecureDatabaseProvider.open(appContext)
            .weightFollowUpDao()
            .decisionRevisions(
                actor.profileId,
                actor.personUnderCareId,
                occurrenceId,
            )
            .map(WeightOccurrenceDecisionRevisionEntity::toDomain)
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
                        .weightFollowUpDao()
                        .load(actor.profileId, actor.personUnderCareId)
                        ?.toDomain()
                        ?: return@withLock
                    WeightReminderScheduler(context).reconcile(latest)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The committed follow-up remains authoritative.
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

private fun WeightTargetEntity.toDomain(): WeightTarget =
    WeightTarget(kilograms.toBigDecimal())

private fun MeasurementRecord.toWeightEntity(
    actor: GlucoseActor,
): WeightMeasurementEntity {
    val (kilograms, heightMeters) = value as MeasurementValue.Weight
    val metadata = requireNotNull(weightMetadata)
    return WeightMeasurementEntity(
        id = id,
        profileId = actor.profileId,
        caregiverId = actor.caregiverId,
        personUnderCareId = actor.personUnderCareId,
        kilograms = kilograms.toPlainString(),
        heightMeters = heightMeters?.toPlainString(),
        actualContext = (
            metadata.actualContext ?: GlucoseContext.UNSPECIFIED
            ).name,
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

private fun WeightMeasurementEntity.audit(
    id: String,
    operation: WeightRevisionOperation,
    capturedAt: LocalDateTime,
) = WeightAuditEntity(
    id = id,
    recordId = this.id,
    actorId = caregiverId,
    operation = operation.name,
    capturedAt = capturedAt.toString(),
)

private fun WeightMeasurementEntity.revision(
    id: String,
    revisionNumber: Int,
    operation: WeightRevisionOperation,
    capturedAt: LocalDateTime,
) = WeightRevisionEntity(
    id = id,
    recordId = this.id,
    revisionNumber = revisionNumber,
    actorId = caregiverId,
    operation = operation.name,
    kilograms = kilograms,
    heightMeters = heightMeters,
    actualContext = actualContext,
    actualAt = actualAt,
    note = note,
    capturedAt = capturedAt.toString(),
)

internal fun WeightMeasurementEntity.toDomain() = MeasurementRecord(
    id = id,
    value = MeasurementValue.Weight(
        kilograms = kilograms.toBigDecimal(),
        heightMeters = heightMeters?.toBigDecimal(),
    ),
    occurredAt = LocalDateTime.parse(actualAt),
    capturedAt = LocalDateTime.parse(capturedAt),
    weightMetadata = WeightRecordMetadata(
        entryKind = WeightEntryKind.valueOf(entryKind),
        followUpId = followUpId,
        scheduledOccurrenceId = scheduledOccurrenceId,
        scheduledAt = scheduledAt?.let(LocalDateTime::parse),
        expectedContext = expectedContext?.let(GlucoseContext::valueOf),
        actualContext = GlucoseContext.valueOf(actualContext).takeUnless {
            it == GlucoseContext.UNSPECIFIED
        },
        note = note,
    ),
)

internal fun WeightRevisionEntity.toDomain() = WeightRevision(
    id = id,
    recordId = recordId,
    revisionNumber = revisionNumber,
    operation = WeightRevisionOperation.valueOf(operation),
    kilograms = kilograms.toBigDecimal(),
    heightMeters = heightMeters?.toBigDecimal(),
    context = GlucoseContext.valueOf(actualContext).takeUnless {
        it == GlucoseContext.UNSPECIFIED
    },
    actualAt = LocalDateTime.parse(actualAt),
    note = note,
    actorId = actorId,
    capturedAt = LocalDateTime.parse(capturedAt),
)

private fun GlucoseFollowUp.toWeightEntity(actor: GlucoseActor) =
    WeightFollowUpEntity(
        id = id,
        profileId = actor.profileId,
        caregiverId = actor.caregiverId,
        personUnderCareId = actor.personUnderCareId,
        createdAt = createdAt.toString(),
        createdByActorId = createdByActorId,
        currentRevisionNumber = currentRevision.revisionNumber,
    )

private fun GlucoseFollowUpRevision.toWeightEntity() =
    WeightFollowUpRevisionEntity(
        id = id,
        followUpId = followUpId,
        revisionNumber = revisionNumber,
        indication = indication,
        frequency = frequency.name,
        weekdaysMask = weekdays.fold(0) { mask, day ->
            mask or (1 shl day.ordinal)
        },
        everyNDaysInterval = everyNDaysInterval,
        everyNDaysReferenceEpochDay =
            everyNDaysReferenceDate?.toEpochDay(),
        startsOnEpochDay = startsOn.toEpochDay(),
        endsOnEpochDay = endsOn?.toEpochDay(),
        effectiveFromEpochDay = effectiveFrom.toEpochDay(),
        remindersEnabled = remindersEnabled,
        changeKind = changeKind.name,
        changeReason = changeReason,
        capturedAt = capturedAt.toString(),
        authorActorId = authorActorId,
    )

private fun GlucoseFollowUpSlot.toWeightEntity(revisionId: String) =
    WeightFollowUpSlotEntity(
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

private fun PersistedWeightFollowUp.toDomain(): GlucoseFollowUp {
    val slotsByRevision = slots.groupBy(
        WeightFollowUpSlotEntity::followUpRevisionId,
    )
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

private fun WeightFollowUpRevisionEntity.toDomain(
    slots: List<WeightFollowUpSlotEntity>,
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
    everyNDaysReferenceDate =
        everyNDaysReferenceEpochDay?.let(LocalDate::ofEpochDay),
    startsOn = LocalDate.ofEpochDay(startsOnEpochDay),
    endsOn = endsOnEpochDay?.let(LocalDate::ofEpochDay),
    effectiveFrom = LocalDate.ofEpochDay(effectiveFromEpochDay),
    slots = slots.map(WeightFollowUpSlotEntity::toDomain),
    remindersEnabled = remindersEnabled,
    changeKind = GlucoseFollowUpChangeKind.valueOf(changeKind),
    changeReason = changeReason,
    capturedAt = LocalDateTime.parse(capturedAt),
    authorActorId = authorActorId,
)

private fun WeightFollowUpSlotEntity.toDomain() =
    GlucoseFollowUpSlot(
        id = logicalId,
        timingType = GlucoseFollowUpTimingType.valueOf(timingType),
        time = LocalTime.ofSecondOfDay(localTimeMinute * 60L),
        windowEnd = windowEndMinute?.let {
            LocalTime.ofSecondOfDay(it * 60L)
        },
        endsNextDay = endsNextDay,
        suggestedContext = GlucoseContext.valueOf(suggestedContext),
        reminderEnabled = reminderEnabled,
        reminderTime = reminderMinute?.let {
            LocalTime.ofSecondOfDay(it * 60L)
        },
        reminderNextDay = reminderNextDay,
    )

private fun GlucoseOccurrenceDecision.toWeightEntity() =
    WeightOccurrenceDecisionEntity(
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

private fun GlucoseOccurrenceDecisionRevision.toWeightEntity() =
    WeightOccurrenceDecisionRevisionEntity(
        id = id,
        decisionId = decisionId,
        occurrenceId = occurrenceId,
        revisionNumber = revisionNumber,
        operation = operation.name,
        reason = reason,
        capturedAt = capturedAt.toString(),
        actorId = actorId,
    )

private fun WeightOccurrenceDecisionRevisionEntity.toDomain() =
    GlucoseOccurrenceDecisionRevision(
        id = id,
        decisionId = decisionId,
        occurrenceId = occurrenceId,
        revisionNumber = revisionNumber,
        operation =
            GlucoseOccurrenceDecisionRevisionOperation.valueOf(operation),
        reason = reason,
        capturedAt = LocalDateTime.parse(capturedAt),
        actorId = actorId,
    )

private fun WeightOccurrenceDecisionEntity.toDomain() =
    GlucoseOccurrenceDecision(
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
