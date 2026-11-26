package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.bitavida.app.domain.repository.GlucoseAuthorizationException
import com.bitavida.app.data.observations.ObservationPayloadCodec
import com.bitavida.app.domain.usecase.validateObservationText
import com.bitavida.app.domain.usecase.fitsObservationTextCapture
import com.bitavida.app.domain.usecase.validateObservationFollowUpText
import com.bitavida.app.domain.validation.CaptureLimit

data class PersistedObservations(
    val records: List<ObservationRecordEntity>,
    val followUps: List<ObservationFollowUpEntity>,
    val revisions: List<ObservationFollowUpRevisionEntity>,
    val recordRevisions: List<ObservationRecordRevisionEntity>,
)

@Dao
abstract class ObservationDao {
    @Query("""
        SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profile AND r.caregiverId = :actor AND r.personUnderCareId = :person
        AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
            AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId
            AND c.status = 'ACTIVE')
    """)
    abstract suspend fun currentRelationship(profile: String, actor: String, person: String): Int

    @Query("SELECT r.* FROM observation_record_revisions r INNER JOIN observation_records b ON b.id = r.recordId WHERE b.profileId = :profile AND b.personUnderCareId = :person ORDER BY r.revisionNumber")
    abstract suspend fun recordRevisions(profile: String, person: String): List<ObservationRecordRevisionEntity>

    @Query("SELECT * FROM observation_record_revisions WHERE id = :id LIMIT 1")
    protected abstract suspend fun recordRevision(id: String): ObservationRecordRevisionEntity?

    @Query("SELECT COALESCE(MAX(revisionNumber), 1) FROM observation_record_revisions WHERE recordId = :recordId")
    protected abstract suspend fun currentRecordVersion(recordId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecordRevision(revision: ObservationRecordRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun retainPhotoLinks(links: List<ObservationRecordPhotoEntity>)

    @Query("SELECT * FROM observation_records WHERE profileId = :profile AND personUnderCareId = :person ORDER BY occurredOnEpochDay DESC, createdAt DESC")
    abstract suspend fun records(profile: String, person: String): List<ObservationRecordEntity>

    @Query("SELECT * FROM observation_follow_ups WHERE profileId = :profile AND personUnderCareId = :person")
    abstract suspend fun followUps(profile: String, person: String): List<ObservationFollowUpEntity>

    @Query("SELECT r.* FROM observation_follow_up_revisions r INNER JOIN observation_follow_ups f ON f.id = r.followUpId WHERE f.profileId = :profile AND f.personUnderCareId = :person ORDER BY r.revisionNumber")
    abstract suspend fun revisions(profile: String, person: String): List<ObservationFollowUpRevisionEntity>

    @Query("SELECT * FROM observation_records WHERE profileId = :profile AND personUnderCareId = :person AND (id = :id OR (:occurrence IS NOT NULL AND occurrenceId = :occurrence)) LIMIT 1")
    protected abstract suspend fun existing(profile: String, person: String, id: String, occurrence: String?): ObservationRecordEntity?

    @Query("SELECT * FROM observation_follow_ups WHERE id = :id AND profileId = :profile AND personUnderCareId = :person LIMIT 1")
    abstract suspend fun followUp(id: String, profile: String, person: String): ObservationFollowUpEntity?

    @Query("SELECT * FROM observation_follow_up_revisions WHERE id = :id LIMIT 1")
    protected abstract suspend fun revision(id: String): ObservationFollowUpRevisionEntity?

    @Query("""
        SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profile AND r.caregiverId = :actor AND r.personUnderCareId = :person
        AND EXISTS (SELECT 1 FROM sensitive_data_consents s WHERE s.profileId = r.profileId
            AND s.caregiverId = r.caregiverId AND s.personUnderCareId = r.personUnderCareId
            AND s.documentVersion = :version AND s.status = 'ACTIVE')
        AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
            AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId AND c.status = 'ACTIVE')
    """)
    abstract suspend fun authorized(profile: String, actor: String, person: String, version: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecord(record: ObservationRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertFollowUp(followUp: ObservationFollowUpEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRevision(revision: ObservationFollowUpRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPhotoLinks(links: List<ObservationRecordPhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPhoto(photo: ObservationPhotoEntity)

    @Query("SELECT * FROM observation_photos WHERE id = :id AND profileId = :profile AND personUnderCareId = :person LIMIT 1")
    abstract suspend fun photo(id: String, profile: String, person: String): ObservationPhotoEntity?

    @Query("""
        SELECT p.* FROM observation_photos p
        INNER JOIN observation_record_photos l ON l.photoId = p.id
        INNER JOIN observation_records r ON r.id = l.recordId
        WHERE r.id = :recordId AND p.id = :photoId
          AND r.profileId = :profile AND r.personUnderCareId = :person
          AND p.profileId = :profile AND p.personUnderCareId = :person LIMIT 1
    """)
    abstract suspend fun linkedPhoto(recordId: String, photoId: String, profile: String, person: String): ObservationPhotoEntity?

    @Query("DELETE FROM observation_photos WHERE id = :id AND profileId = :profile AND personUnderCareId = :person AND NOT EXISTS (SELECT 1 FROM observation_record_photos WHERE photoId = :id)")
    abstract suspend fun discardUnlinkedPhoto(id: String, profile: String, person: String)

    @Query("UPDATE observation_follow_ups SET currentRevisionNumber = :next WHERE id = :id AND profileId = :profile AND personUnderCareId = :person AND currentRevisionNumber = :expected")
    protected abstract suspend fun advance(id: String, profile: String, person: String, expected: Int, next: Int): Int

    @Transaction
    open suspend fun load(profile: String, actor: String, person: String): PersistedObservations {
        if (currentRelationship(profile, actor, person) != 1) throw GlucoseAuthorizationException()
        return PersistedObservations(records(profile, person), followUps(profile, person), revisions(profile, person), recordRevisions(profile, person))
    }

    @Transaction
    open suspend fun create(record: ObservationRecordEntity, photoIds: List<String>, consentVersion: String): ObservationRecordEntity {
        authorize(record.profileId, record.caregiverId, record.personUnderCareId, consentVersion)
        existing(record.profileId, record.personUnderCareId, record.id, record.occurrenceId)?.let {
            require(it.id == record.id || it.occurrenceId == record.occurrenceId)
            return it
        }
        require(validateObservationText(ObservationPayloadCodec.decodeRecord(record.payload)).isEmpty())
        record.followUpId?.let { id ->
            val followUp = requireNotNull(followUp(id, record.profileId, record.personUnderCareId))
            require(followUp.observationId == record.observationId)
        }
        require((record.followUpId == null) == (record.occurrenceId == null))
        require(photoIds.distinct().size == photoIds.size)
        photoIds.forEach { requireNotNull(photo(it, record.profileId, record.personUnderCareId)) }
        insertRecord(record)
        insertPhotoLinks(photoIds.map { ObservationRecordPhotoEntity(record.id, it) })
        return record
    }

    @Transaction
    open suspend fun append(
        followUp: ObservationFollowUpEntity,
        next: ObservationFollowUpRevisionEntity,
        expected: Int,
        consentVersion: String,
    ) {
        authorize(followUp.profileId, followUp.caregiverId, followUp.personUnderCareId, consentVersion)
        revision(next.id)?.let { require(it == next); return }
        require(next.followUpId == followUp.id && next.revisionNumber == expected + 1)
        val current = followUp(followUp.id, followUp.profileId, followUp.personUnderCareId)
        val previous = revisions(followUp.profileId, followUp.personUnderCareId)
            .lastOrNull { it.followUpId == followUp.id }?.let { ObservationPayloadCodec.decodeRevision(it.payload) }
        require(validateObservationFollowUpText(ObservationPayloadCodec.decodeRevision(next.payload), previous).isEmpty())
        if (expected == 0) {
            require(current == null)
            insertFollowUp(followUp)
        } else {
            requireNotNull(current)
            require(current.observationId == followUp.observationId)
            check(advance(followUp.id, followUp.profileId, followUp.personUnderCareId, expected, next.revisionNumber) == 1)
        }
        insertRevision(next)
    }

    @Transaction
    open suspend fun correctRecord(
        profile: String,
        person: String,
        next: ObservationRecordRevisionEntity,
        expected: Int,
        photoIds: List<String>,
        consentVersion: String,
    ): ObservationRecordRevisionEntity {
        authorize(profile, next.actorId, person, consentVersion)
        recordRevision(next.id)?.let { require(it == next); return it }
        require(expected >= 1 && next.revisionNumber == expected + 1)
        require(next.reason.isNotBlank() && fitsObservationTextCapture(next.reason, CaptureLimit.CHANGE_REASON.maximum))
        val original = requireNotNull(existing(profile, person, next.recordId, null))
        check(currentRecordVersion(original.id) == expected)
        val previous = recordRevisions(profile, person).lastOrNull { it.recordId == original.id }?.payload
            ?: original.payload
        require(validateObservationText(ObservationPayloadCodec.decodeRecord(next.payload),
            ObservationPayloadCodec.decodeRecord(previous)).isEmpty())
        require(photoIds.distinct().size == photoIds.size)
        photoIds.forEach { requireNotNull(photo(it, profile, person)) }
        insertRecordRevision(next)
        // Preserve every prior version's photograph; removing it from the latest snapshot is not deletion.
        retainPhotoLinks(photoIds.map { ObservationRecordPhotoEntity(original.id, it) })
        return next
    }

    @Transaction
    open suspend fun storePhoto(photo: ObservationPhotoEntity, actor: String, consentVersion: String) {
        authorize(photo.profileId, actor, photo.personUnderCareId, consentVersion)
        require(photo.jpeg.isNotEmpty() && photo.jpeg.size <= 524_288)
        insertPhoto(photo)
    }

    private suspend fun authorize(profile: String, actor: String, person: String, version: String) {
        if (authorized(profile, actor, person, version) != 1) throw GlucoseAuthorizationException()
    }
}
