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
abstract class GlucoseDao {
    // Includes logically deleted records so a report can reconstruct an earlier cutoff.
    @Query("SELECT * FROM glucose_measurements WHERE profileId = :profileId AND personUnderCareId = :personId ORDER BY id")
    abstract suspend fun loadAllForReport(profileId: String, personId: String): List<GlucoseMeasurementEntity>

    @Query("""
        SELECT r.* FROM glucose_revisions r
        INNER JOIN glucose_measurements m ON m.id = r.recordId
        WHERE m.profileId = :profileId AND m.personUnderCareId = :personId
        ORDER BY r.recordId, r.revisionNumber
    """)
    abstract suspend fun loadAllRevisionsForReport(profileId: String, personId: String): List<GlucoseRevisionEntity>

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
            OR (NOT EXISTS (SELECT 1 FROM glucose_measurements m
                WHERE m.profileId = r.profileId AND m.personUnderCareId = r.personUnderCareId)
              AND NOT EXISTS (SELECT 1 FROM glucose_follow_ups f
                WHERE f.profileId = r.profileId AND f.personUnderCareId = r.personUnderCareId)))
    """)
    protected abstract suspend fun reportAuthorized(profileId: String, actorId: String, personId: String, version: String): Int

    @Transaction
    open suspend fun loadReportHistory(profileId: String, personId: String, actorId: String, version: String):
        Pair<List<GlucoseMeasurementEntity>, List<GlucoseRevisionEntity>> {
        if (reportAuthorized(profileId, actorId, personId, version) != 1) throw GlucoseAuthorizationException()
        return loadAllForReport(profileId, personId) to loadAllRevisionsForReport(profileId, personId)
    }

    @Query(
        """
        SELECT * FROM glucose_measurements
        WHERE profileId = :profileId
        AND personUnderCareId = :personId
        AND deletedAt IS NULL
        ORDER BY actualAt DESC
        """,
    )
    abstract suspend fun load(
        profileId: String,
        personId: String,
    ): List<GlucoseMeasurementEntity>

    @Query(
        """
        SELECT * FROM glucose_revisions
        WHERE recordId = :recordId
        AND EXISTS (
            SELECT 1 FROM glucose_measurements m
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
    ): List<GlucoseRevisionEntity>

    @Query(
        """
        SELECT * FROM glucose_measurements
        WHERE personUnderCareId = :personId
        AND (
            id = :id OR
            (:occurrenceId IS NOT NULL AND scheduledOccurrenceId = :occurrenceId)
        )
        LIMIT 1
        """,
    )
    protected abstract suspend fun existing(
        personId: String,
        id: String,
        occurrenceId: String?,
    ): GlucoseMeasurementEntity?

    @Query(
        """
        SELECT * FROM glucose_measurements
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
    ): GlucoseMeasurementEntity?

    @Query(
        "SELECT COALESCE(MAX(revisionNumber), 0) FROM glucose_revisions WHERE recordId = :recordId",
    )
    protected abstract suspend fun latestRevisionNumber(recordId: String): Int

    @Query(
        """
        SELECT * FROM glucose_occurrence_decisions
        WHERE occurrenceId = :occurrenceId
        LIMIT 1
        """,
    )
    protected abstract suspend fun occurrenceDecision(
        occurrenceId: String,
    ): GlucoseOccurrenceDecisionEntity?

    @Query(
        """
        SELECT COUNT(*) FROM glucose_occurrence_decision_revisions
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
    protected abstract suspend fun insertRecord(entity: GlucoseMeasurementEntity)

    @Update
    protected abstract suspend fun updateRecord(entity: GlucoseMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAudit(entity: GlucoseAuditEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRevision(entity: GlucoseRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOccurrenceDecisionRevision(
        entity: GlucoseOccurrenceDecisionRevisionEntity,
    )

    @Transaction
    open suspend fun create(
        entity: GlucoseMeasurementEntity,
        audit: GlucoseAuditEntity,
        revision: GlucoseRevisionEntity,
        consentVersion: String,
        decisionRevision: GlucoseOccurrenceDecisionRevisionEntity? = null,
    ): GlucoseMeasurementEntity {
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
        entity: GlucoseMeasurementEntity,
        audit: GlucoseAuditEntity,
        revision: GlucoseRevisionEntity,
        consentVersion: String,
    ): GlucoseMeasurementEntity {
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
        audit: GlucoseAuditEntity,
        revision: GlucoseRevisionEntity,
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
                reading = current.reading,
                actualContext = current.actualContext,
                actualAt = current.actualAt,
                note = current.note,
            ),
        )
    }

    private suspend fun requireAuthorized(
        entity: GlucoseMeasurementEntity,
        consentVersion: String,
    ) {
        if (
            authorized(
                entity.profileId,
                entity.caregiverId,
                entity.personUnderCareId,
                consentVersion,
            ) != 1
        ) {
            throw GlucoseAuthorizationException()
        }
    }
}
