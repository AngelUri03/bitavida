package com.bitavida.app.data.local.secure

import com.bitavida.app.domain.validation.fitsTextLimit
import com.bitavida.app.domain.validation.CaptureLimit

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

data class PersistedWeightFollowUp(
    val followUp: WeightFollowUpEntity,
    val revisions: List<WeightFollowUpRevisionEntity>,
    val slots: List<WeightFollowUpSlotEntity>,
)

@Dao
abstract class WeightFollowUpDao {
    @Query(
        """
        SELECT * FROM weight_follow_ups
        WHERE profileId = :profileId
          AND personUnderCareId = :personId
        LIMIT 1
        """,
    )
    abstract suspend fun followUp(
        profileId: String,
        personId: String,
    ): WeightFollowUpEntity?

    @Query(
        """
        SELECT r.* FROM weight_follow_up_revisions r
        INNER JOIN weight_follow_ups f ON f.id = r.followUpId
        WHERE f.profileId = :profileId
          AND f.personUnderCareId = :personId
          AND r.followUpId = :followUpId
        ORDER BY r.revisionNumber
        """,
    )
    abstract suspend fun revisions(
        profileId: String,
        personId: String,
        followUpId: String,
    ): List<WeightFollowUpRevisionEntity>

    @Query(
        """
        SELECT s.* FROM weight_follow_up_slots s
        INNER JOIN weight_follow_up_revisions r
            ON r.id = s.followUpRevisionId
        INNER JOIN weight_follow_ups f ON f.id = r.followUpId
        WHERE f.profileId = :profileId
          AND f.personUnderCareId = :personId
          AND r.followUpId = :followUpId
        ORDER BY r.revisionNumber, s.localTimeMinute
        """,
    )
    abstract suspend fun slots(
        profileId: String,
        personId: String,
        followUpId: String,
    ): List<WeightFollowUpSlotEntity>

    @Query(
        """
        SELECT d.* FROM weight_occurrence_decisions d
        INNER JOIN weight_follow_ups f ON f.id = d.followUpId
        WHERE f.profileId = :profileId
          AND f.personUnderCareId = :personId
          AND d.followUpId = :followUpId
        ORDER BY d.scheduledAt DESC
        """,
    )
    abstract suspend fun decisions(
        profileId: String,
        personId: String,
        followUpId: String,
    ): List<WeightOccurrenceDecisionEntity>

    @Query(
        """
        SELECT r.* FROM weight_occurrence_decision_revisions r
        INNER JOIN weight_occurrence_decisions d
            ON d.id = r.decisionId
        INNER JOIN weight_follow_ups f ON f.id = d.followUpId
        WHERE f.profileId = :profileId
          AND f.personUnderCareId = :personId
          AND r.occurrenceId = :occurrenceId
        ORDER BY r.revisionNumber
        """,
    )
    abstract suspend fun decisionRevisions(
        profileId: String,
        personId: String,
        occurrenceId: String,
    ): List<WeightOccurrenceDecisionRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertFollowUp(entity: WeightFollowUpEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRevision(
        entity: WeightFollowUpRevisionEntity,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSlots(
        entities: List<WeightFollowUpSlotEntity>,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertDecision(
        entity: WeightOccurrenceDecisionEntity,
    ): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDecisionRevision(
        entity: WeightOccurrenceDecisionRevisionEntity,
    )

    @Query(
        """
        UPDATE weight_occurrence_decisions
        SET reason = :reason,
            capturedAt = :capturedAt,
            actorId = :actorId
        WHERE id = :decisionId
        """,
    )
    abstract suspend fun updateDecision(
        decisionId: String,
        reason: String?,
        capturedAt: String,
        actorId: String,
    ): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM weight_occurrence_decision_revisions
        WHERE occurrenceId = :occurrenceId
        """,
    )
    abstract suspend fun decisionRevisionCount(occurrenceId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM weight_measurements
        WHERE scheduledOccurrenceId = :occurrenceId
          AND deletedAt IS NULL
        """,
    )
    abstract suspend fun activeRecordCount(occurrenceId: String): Int

    @Query(
        """
        SELECT * FROM weight_occurrence_decisions
        WHERE occurrenceId = :occurrenceId
        LIMIT 1
        """,
    )
    abstract suspend fun decisionForOccurrence(
        occurrenceId: String,
    ): WeightOccurrenceDecisionEntity?

    @Query(
        """
        UPDATE weight_follow_ups
        SET currentRevisionNumber = :nextRevision
        WHERE id = :followUpId
          AND profileId = :profileId
          AND personUnderCareId = :personId
          AND currentRevisionNumber = :expectedRevision
        """,
    )
    abstract suspend fun advanceRevision(
        profileId: String,
        personId: String,
        followUpId: String,
        expectedRevision: Int,
        nextRevision: Int,
    ): Int

    @Transaction
    open suspend fun load(
        profileId: String,
        personId: String,
    ): PersistedWeightFollowUp? {
        val followUp = followUp(profileId, personId) ?: return null
        return PersistedWeightFollowUp(
            followUp = followUp,
            revisions = revisions(profileId, personId, followUp.id),
            slots = slots(profileId, personId, followUp.id),
        )
    }

    @Transaction
    open suspend fun create(
        followUp: WeightFollowUpEntity,
        revision: WeightFollowUpRevisionEntity,
        slots: List<WeightFollowUpSlotEntity>,
    ) {
        check(revision.revisionNumber == 1)
        require(fitsTextLimit(revision.indication, CaptureLimit.CARE_INSTRUCTION.maximum))
        require(revision.changeReason == null || fitsTextLimit(revision.changeReason, CaptureLimit.CHANGE_REASON.maximum))
        insertFollowUp(followUp)
        insertRevision(revision)
        insertSlots(slots)
    }

    @Transaction
    open suspend fun appendRevision(
        profileId: String,
        personId: String,
        followUpId: String,
        expectedRevision: Int,
        revision: WeightFollowUpRevisionEntity,
        slots: List<WeightFollowUpSlotEntity>,
    ) {
        val original = revisions(profileId, personId, followUpId).singleOrNull { it.revisionNumber == expectedRevision }
        require(fitsTextLimit(revision.indication, CaptureLimit.CARE_INSTRUCTION.maximum, original?.indication))
        require(revision.changeReason == null || fitsTextLimit(revision.changeReason, CaptureLimit.CHANGE_REASON.maximum, original?.changeReason))
        val advanced = advanceRevision(
            profileId = profileId,
            personId = personId,
            followUpId = followUpId,
            expectedRevision = expectedRevision,
            nextRevision = revision.revisionNumber,
        )
        check(advanced == 1) { "Weight follow-up changed concurrently." }
        insertRevision(revision)
        insertSlots(slots)
    }

    @Transaction
    open suspend fun confirmDecision(
        entity: WeightOccurrenceDecisionEntity,
        revision: WeightOccurrenceDecisionRevisionEntity,
    ): WeightOccurrenceDecisionEntity {
        check(activeRecordCount(entity.occurrenceId) == 0) {
            "Scheduled occurrence already has a reading."
        }
        require(entity.reason == null || fitsTextLimit(entity.reason, CaptureLimit.CHANGE_REASON.maximum))
        val inserted = insertDecision(entity)
        return if (inserted == -1L) {
            requireNotNull(decisionForOccurrence(entity.occurrenceId))
        } else {
            insertDecisionRevision(
                revision.copy(
                    decisionId = entity.id,
                    occurrenceId = entity.occurrenceId,
                    revisionNumber = 1,
                ),
            )
            entity
        }
    }

    @Transaction
    open suspend fun correctDecision(
        revision: WeightOccurrenceDecisionRevisionEntity,
    ): WeightOccurrenceDecisionEntity {
        check(activeRecordCount(revision.occurrenceId) == 0) {
            "Scheduled occurrence already has a reading."
        }
        val current = requireNotNull(
            decisionForOccurrence(revision.occurrenceId),
        )
        require(revision.reason == null || fitsTextLimit(revision.reason, CaptureLimit.CHANGE_REASON.maximum, current.reason))
        val nextRevision = decisionRevisionCount(revision.occurrenceId) + 1
        check(
            updateDecision(
                decisionId = current.id,
                reason = revision.reason,
                capturedAt = revision.capturedAt,
                actorId = revision.actorId,
            ) == 1,
        )
        insertDecisionRevision(
            revision.copy(
                decisionId = current.id,
                revisionNumber = nextRevision,
            ),
        )
        return current.copy(
            reason = revision.reason,
            capturedAt = revision.capturedAt,
            actorId = revision.actorId,
        )
    }
}
