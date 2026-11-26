package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.bitavida.app.data.reminders.toDomain
import com.bitavida.app.data.reminders.toEntity
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.CareReminderAuthorizationException
import com.bitavida.app.domain.usecase.CareReminderCommand
import com.bitavida.app.domain.usecase.CareReminderProblem
import com.bitavida.app.domain.usecase.CareReminderValidationException
import com.bitavida.app.domain.usecase.applyCareReminderCommand
import java.time.LocalDateTime

data class PersistedReminderReportHistory(
    val current: List<CareReminderEntity>,
    val revisions: List<CareReminderRevisionEntity>,
)

@Dao
abstract class CareReminderDao {
    @Query("""
        SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profile AND r.caregiverId = :actor AND r.personUnderCareId = :person
        AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
            AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId
            AND c.status = 'ACTIVE')
    """)
    abstract suspend fun currentRelationship(profile: String, actor: String, person: String): Int

    @Query("SELECT * FROM care_reminders WHERE profileId = :profile AND personUnderCareId = :person")
    abstract suspend fun records(profile: String, person: String): List<CareReminderEntity>

    @Query("SELECT * FROM care_reminder_revisions WHERE operationId = :operationId LIMIT 1")
    abstract suspend fun operation(operationId: String): CareReminderRevisionEntity?

    @Query("SELECT * FROM care_reminder_revisions WHERE reminderId = :id ORDER BY snapshot_revision")
    abstract suspend fun revisions(id: String): List<CareReminderRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: CareReminderEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun update(entity: CareReminderEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRevision(entity: CareReminderRevisionEntity)

    @Transaction
    open suspend fun load(actor: CareReminderActor): List<CareReminderEntity> {
        authorize(actor)
        return records(actor.profileId, actor.personUnderCareId)
    }

    /** Read-only, authorized snapshot with the recorded operation revisions. */
    @Transaction
    open suspend fun loadReportHistory(actor: CareReminderActor): PersistedReminderReportHistory {
        authorize(actor)
        val current = records(actor.profileId, actor.personUnderCareId)
        return PersistedReminderReportHistory(current, current.flatMap { revisions(it.id) })
    }

    @Transaction
    open suspend fun apply(
        actor: CareReminderActor,
        command: CareReminderCommand,
        requestFingerprint: String,
        now: LocalDateTime,
    ): CareReminderEntity {
        authorize(actor)
        operation(command.operationId)?.let { previous ->
            if (previous.requestFingerprint != requestFingerprint || previous.reminderId != command.id ||
                previous.actorId != actor.caregiverId || previous.snapshot.profileId != actor.profileId ||
                previous.snapshot.personUnderCareId != actor.personUnderCareId) {
                throw CareReminderValidationException(CareReminderProblem.CONFLICT)
            }
            return previous.snapshot
        }
        // The limit and expected revision are checked inside the same transaction as both writes.
        val next = applyCareReminderCommand(records(actor.profileId, actor.personUnderCareId).map { it.toDomain() },
            actor.personUnderCareId, command, now).toEntity(actor)
        if (command is CareReminderCommand.Create) insert(next)
        else check(update(next) == 1)
        insertRevision(CareReminderRevisionEntity(command.operationId, next.id, actor.caregiverId,
            when (command) {
                is CareReminderCommand.Create -> "CREATED"
                is CareReminderCommand.Edit -> "EDITED"
                is CareReminderCommand.Postpone -> "POSTPONED"
                is CareReminderCommand.Close -> command.status.name
            }, requestFingerprint, next))
        return next
    }

    private suspend fun authorize(actor: CareReminderActor) {
        if (!actor.authorizationCurrent ||
            currentRelationship(actor.profileId, actor.caregiverId, actor.personUnderCareId) != 1) {
            throw CareReminderAuthorizationException()
        }
    }
}
