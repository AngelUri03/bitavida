package com.bitavida.app.data.local.secure

import com.bitavida.app.domain.validation.fitsTextLimit
import com.bitavida.app.domain.validation.CaptureLimit

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.bitavida.app.domain.repository.GlucoseAuthorizationException

@Dao
abstract class WeightDao {
    // Includes logically deleted records so a report can reconstruct an earlier cutoff.
    @Query("SELECT * FROM weight_measurements WHERE profileId = :profileId AND personUnderCareId = :personId ORDER BY id")
    abstract suspend fun loadAllForReport(profileId: String, personId: String): List<WeightMeasurementEntity>

    @Query("""
        SELECT r.* FROM weight_revisions r
        INNER JOIN weight_measurements m ON m.id = r.recordId
        WHERE m.profileId = :profileId AND m.personUnderCareId = :personId
        ORDER BY r.recordId, r.revisionNumber
    """)
    abstract suspend fun loadAllRevisionsForReport(profileId: String, personId: String): List<WeightRevisionEntity>

    // A profile without sensitive consent may still export other modules only if
    // this module contains no protected records or follow-up configuration.
    @Query("""
        SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profileId AND r.caregiverId = :actorId AND r.personUnderCareId = :personId
          AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
            AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId AND c.status = 'ACTIVE')
          AND (EXISTS (SELECT 1 FROM sensitive_data_consents s WHERE s.profileId = r.profileId
            AND s.caregiverId = r.caregiverId AND s.personUnderCareId = r.personUnderCareId
            AND s.documentVersion = :version AND s.status = 'ACTIVE')
            OR (NOT EXISTS (SELECT 1 FROM weight_measurements m
                WHERE m.profileId = r.profileId AND m.personUnderCareId = r.personUnderCareId)
              AND NOT EXISTS (SELECT 1 FROM weight_follow_ups f
                WHERE f.profileId = r.profileId AND f.personUnderCareId = r.personUnderCareId)))
    """)
    protected abstract suspend fun reportAuthorized(profileId: String, actorId: String, personId: String, version: String): Int

    @Transaction
    open suspend fun loadReportHistory(profileId: String, personId: String, actorId: String, version: String):
        Pair<List<WeightMeasurementEntity>, List<WeightRevisionEntity>> {
        if (reportAuthorized(profileId, actorId, personId, version) != 1) throw GlucoseAuthorizationException()
        return loadAllForReport(profileId, personId) to loadAllRevisionsForReport(profileId, personId)
    }

    @Query(
        """
        SELECT * FROM weight_measurements
        WHERE profileId = :profileId
          AND personUnderCareId = :personId
          AND deletedAt IS NULL
        ORDER BY actualAt DESC
        """,
    )
    abstract suspend fun load(
        profileId: String,
        personId: String,
    ): List<WeightMeasurementEntity>

    @Query(
        """
        SELECT * FROM weight_targets
        WHERE profileId = :profileId
          AND personUnderCareId = :personId
        LIMIT 1
        """,
    )
    abstract suspend fun loadTarget(
        profileId: String,
        personId: String,
    ): WeightTargetEntity?

    @Query(
        """
        SELECT * FROM weight_revisions
        WHERE recordId = :recordId
          AND EXISTS (
            SELECT 1 FROM weight_measurements m
            WHERE m.id = :recordId
              AND m.profileId = :profileId
              AND m.personUnderCareId = :personId
          )
        ORDER BY revisionNumber DESC
        """,
    )
    abstract suspend fun loadRevisions(
        profileId: String,
        personId: String,
        recordId: String,
    ): List<WeightRevisionEntity>

    @Query(
        """
        SELECT * FROM weight_measurements
        WHERE personUnderCareId = :personId
          AND (
            id = :id OR
            (:occurrenceId IS NOT NULL AND
                scheduledOccurrenceId = :occurrenceId)
          )
        LIMIT 1
        """,
    )
    protected abstract suspend fun existing(
        personId: String,
        id: String,
        occurrenceId: String?,
    ): WeightMeasurementEntity?

    @Query(
        """
        SELECT * FROM weight_measurements
        WHERE profileId = :profileId
          AND personUnderCareId = :personId
          AND id = :id
        LIMIT 1
        """,
    )
    protected abstract suspend fun record(
        profileId: String,
        personId: String,
        id: String,
    ): WeightMeasurementEntity?

    @Query(
        """
        SELECT COALESCE(MAX(revisionNumber), 0)
        FROM weight_revisions
        WHERE recordId = :recordId
        """,
    )
    protected abstract suspend fun latestRevisionNumber(recordId: String): Int

    @Query(
        """
        SELECT * FROM weight_occurrence_decisions
        WHERE occurrenceId = :occurrenceId
        LIMIT 1
        """,
    )
    protected abstract suspend fun occurrenceDecision(
        occurrenceId: String,
    ): WeightOccurrenceDecisionEntity?

    @Query(
        """
        SELECT COUNT(*)
        FROM weight_occurrence_decision_revisions
        WHERE occurrenceId = :occurrenceId
        """,
    )
    protected abstract suspend fun occurrenceDecisionRevisionCount(
        occurrenceId: String,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profileId
          AND r.caregiverId = :actorId
          AND r.personUnderCareId = :personId
          AND EXISTS (
            SELECT 1 FROM sensitive_data_consents s
            WHERE s.profileId = r.profileId
              AND s.caregiverId = r.caregiverId
              AND s.personUnderCareId = r.personUnderCareId
              AND s.documentVersion = :version
              AND s.status = 'ACTIVE'
          )
          AND EXISTS (
            SELECT 1 FROM consent_records c
            WHERE c.profileId = r.profileId
              AND c.caregiverId = r.caregiverId
              AND c.personUnderCareId = r.personUnderCareId
              AND c.status = 'ACTIVE'
          )
        """,
    )
    protected abstract suspend fun authorized(
        profileId: String,
        actorId: String,
        personId: String,
        version: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecord(
        entity: WeightMeasurementEntity,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertTarget(
        entity: WeightTargetEntity,
    )

    @Query(
        """
        DELETE FROM weight_targets
        WHERE profileId = :profileId
          AND personUnderCareId = :personId
        """,
    )
    protected abstract suspend fun deleteTargetEntity(
        profileId: String,
        personId: String,
    )

    @Update
    protected abstract suspend fun updateRecord(
        entity: WeightMeasurementEntity,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAudit(
        entity: WeightAuditEntity,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRevision(
        entity: WeightRevisionEntity,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOccurrenceDecisionRevision(
        entity: WeightOccurrenceDecisionRevisionEntity,
    )

    @Transaction
    open suspend fun saveTarget(
        entity: WeightTargetEntity,
        consentVersion: String,
    ): WeightTargetEntity {
        requireAuthorized(
            entity.profileId,
            entity.caregiverId,
            entity.personUnderCareId,
            consentVersion,
        )
        upsertTarget(entity)
        return entity
    }

    @Transaction
    open suspend fun deleteTarget(
        profileId: String,
        actorId: String,
        personId: String,
        consentVersion: String,
    ) {
        requireAuthorized(
            profileId,
            actorId,
            personId,
            consentVersion,
        )
        deleteTargetEntity(profileId, personId)
    }

    @Transaction
    open suspend fun create(
        entity: WeightMeasurementEntity,
        audit: WeightAuditEntity,
        revision: WeightRevisionEntity,
        consentVersion: String,
        decisionRevision:
            WeightOccurrenceDecisionRevisionEntity? = null,
    ): WeightMeasurementEntity {
        requireAuthorized(entity, consentVersion)
        existing(
            entity.personUnderCareId,
            entity.id,
            entity.scheduledOccurrenceId,
        )?.let { return it }
        require(entity.note == null || fitsTextLimit(entity.note, CaptureLimit.MEASUREMENT_NOTE.maximum))
        require(decisionRevision?.reason == null || fitsTextLimit(requireNotNull(decisionRevision.reason), CaptureLimit.CHANGE_REASON.maximum))
        val decision = entity.scheduledOccurrenceId?.let {
            occurrenceDecision(it)
        }
        check((decision == null) == (decisionRevision == null)) {
            "An explicit decision requires an auditable correction."
        }
        if ((decision != null) && (decisionRevision != null)) {
            check(decision.kind == "NOT_PERFORMED")
            check(decision.id == decisionRevision.decisionId)
            check(decision.followUpId == entity.followUpId)
            check(decision.occurrenceId == entity.scheduledOccurrenceId)
        }
        insertRecord(entity)
        insertAudit(audit)
        insertRevision(revision)
        if ((decision != null) && (decisionRevision != null)) {
            insertOccurrenceDecisionRevision(
                decisionRevision.copy(
                    decisionId = decision.id,
                    occurrenceId = decision.occurrenceId,
                    revisionNumber =
                        occurrenceDecisionRevisionCount(decision.occurrenceId) + 1,
                ),
            )
        }
        return entity
    }

    @Transaction
    open suspend fun revise(
        entity: WeightMeasurementEntity,
        audit: WeightAuditEntity,
        revision: WeightRevisionEntity,
        consentVersion: String,
    ): WeightMeasurementEntity {
        requireAuthorized(entity, consentVersion)
        val current = requireNotNull(
            record(entity.profileId, entity.personUnderCareId, entity.id),
        )
        check(current.deletedAt == null)
        require(entity.note == null || fitsTextLimit(entity.note, CaptureLimit.MEASUREMENT_NOTE.maximum, current.note))
        val nextRevision = latestRevisionNumber(entity.id) + 1
        val updated = entity.copy(
            profileId = current.profileId,
            caregiverId = current.caregiverId,
            personUnderCareId = current.personUnderCareId,
            origin = current.origin,
            deletedAt = null,
            deletedBy = null,
        )
        updateRecord(updated)
        insertAudit(audit)
        insertRevision(revision.copy(revisionNumber = nextRevision))
        return updated
    }

    @Transaction
    open suspend fun softDelete(
        profileId: String,
        actorId: String,
        personId: String,
        recordId: String,
        deletedAt: String,
        audit: WeightAuditEntity,
        revision: WeightRevisionEntity,
        consentVersion: String,
    ) {
        if (
            authorized(profileId, actorId, personId, consentVersion) != 1
        ) {
            throw GlucoseAuthorizationException()
        }
        val current = requireNotNull(record(profileId, personId, recordId))
        if (current.deletedAt != null) return
        val nextRevision = latestRevisionNumber(recordId) + 1
        updateRecord(
            current.copy(
                deletedAt = deletedAt,
                deletedBy = actorId,
            ),
        )
        insertAudit(audit)
        insertRevision(
            revision.copy(
                revisionNumber = nextRevision,
                kilograms = current.kilograms,
                heightMeters = current.heightMeters,
                actualContext = current.actualContext,
                actualAt = current.actualAt,
                note = current.note,
            ),
        )
    }

    private suspend fun requireAuthorized(
        entity: WeightMeasurementEntity,
        consentVersion: String,
    ) {
        requireAuthorized(
            entity.profileId,
            entity.caregiverId,
            entity.personUnderCareId,
            consentVersion,
        )
    }

    private suspend fun requireAuthorized(
        profileId: String,
        actorId: String,
        personId: String,
        consentVersion: String,
    ) {
        if (
            authorized(
                profileId,
                actorId,
                personId,
                consentVersion,
            ) != 1
        ) {
            throw GlucoseAuthorizationException()
        }
    }
}
