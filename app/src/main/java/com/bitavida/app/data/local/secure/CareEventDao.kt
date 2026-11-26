package com.bitavida.app.data.local.secure

import androidx.room3.*
import com.bitavida.app.data.appointments.toDomain
import com.bitavida.app.data.appointments.toEntity
import com.bitavida.app.domain.repository.*
import com.bitavida.app.domain.usecase.*
import java.time.LocalDateTime

@Dao
abstract class CareEventDao {
    @Query("""SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profile AND r.caregiverId = :actor AND r.personUnderCareId = :person
        AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
        AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId AND c.status = 'ACTIVE')""")
    abstract suspend fun currentRelationship(profile: String, actor: String, person: String): Int
    @Query("""SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profile AND r.caregiverId = :actor AND r.personUnderCareId = :person
        AND EXISTS (SELECT 1 FROM sensitive_data_consents s WHERE s.profileId = r.profileId
            AND s.caregiverId = r.caregiverId AND s.personUnderCareId = r.personUnderCareId
            AND s.documentVersion = :version AND s.status = 'ACTIVE')
        AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
            AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId AND c.status = 'ACTIVE')""")
    abstract suspend fun sensitiveAuthorized(profile: String, actor: String, person: String, version: String): Int

    @Query("SELECT * FROM care_events WHERE profileId = :profile AND personUnderCareId = :person")
    abstract suspend fun records(profile: String, person: String): List<CareEventEntity>
    @Query("SELECT * FROM care_event_revisions WHERE operationId = :operationId LIMIT 1")
    abstract suspend fun operation(operationId: String): CareEventRevisionEntity?
    @Query("SELECT * FROM care_event_revisions WHERE eventId = :id ORDER BY snapshot_revision")
    abstract suspend fun revisions(id: String): List<CareEventRevisionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insert(entity: CareEventEntity)
    @Update(onConflict = OnConflictStrategy.ABORT) abstract suspend fun update(entity: CareEventEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertRevision(entity: CareEventRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertPhoto(photo: CareEventPhotoEntity)
    @Query("SELECT COUNT(*) FROM care_event_photos WHERE eventId = :event AND profileId = :profile AND personUnderCareId = :person AND removedAtRevision IS NULL")
    protected abstract suspend fun photoCount(event: String, profile: String, person: String): Int
    @Query("UPDATE care_event_photos SET removedAtRevision = :revision WHERE id = :id AND eventId = :event AND profileId = :profile AND personUnderCareId = :person AND (removedAtRevision IS NULL OR removedAtRevision = :revision)")
    protected abstract suspend fun markPhotoRemoved(id: String, event: String, profile: String, person: String, revision: Int): Int
    @Query("SELECT COUNT(*) FROM care_event_photos WHERE id = :id AND eventId = :event AND profileId = :profile AND personUnderCareId = :person")
    protected abstract suspend fun existingPhoto(id: String, event: String, profile: String, person: String): Int
    @Query("SELECT displayOrder FROM care_event_photos WHERE id = :id AND eventId = :event AND profileId = :profile AND personUnderCareId = :person LIMIT 1")
    protected abstract suspend fun photoOrder(id: String, event: String, profile: String, person: String): Long?
    @Query("SELECT COALESCE(MAX(displayOrder), -1) + 1 FROM care_event_photos WHERE eventId = :event AND profileId = :profile AND personUnderCareId = :person")
    protected abstract suspend fun nextPhotoOrder(event: String, profile: String, person: String): Long

    @Transaction
    open suspend fun load(actor: CareEventActor): List<CareEventEntity> {
        authorize(actor)
        return records(actor.profileId, actor.personUnderCareId)
    }

    @Transaction
    open suspend fun history(actor: CareEventActor, id: String): List<CareEventRevisionEntity> {
        authorize(actor)
        if (records(actor.profileId, actor.personUnderCareId).none { it.id == id }) return emptyList()
        return revisions(id).filter { it.snapshot.profileId == actor.profileId && it.snapshot.personUnderCareId == actor.personUnderCareId }
    }

    @Transaction
    open suspend fun apply(actor: CareEventActor, command: CareEventCommand, fingerprint: String, now: LocalDateTime): CareEventEntity {
        authorize(actor)
        if (sensitiveAuthorized(actor.profileId, actor.caregiverId, actor.personUnderCareId,
                com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION) != 1)
            throw CareEventSensitiveConsentException()
        operation(command.operationId)?.let { previous ->
            if (previous.requestFingerprint != fingerprint || previous.eventId != command.id ||
                previous.actorId != actor.caregiverId || previous.snapshot.profileId != actor.profileId ||
                previous.snapshot.personUnderCareId != actor.personUnderCareId)
                throw CareEventValidationException(CareEventProblem.CONFLICT)
            return previous.snapshot
        }
        val next = applyCareEventCommand(records(actor.profileId, actor.personUnderCareId).map { it.toDomain() },
            actor.personUnderCareId, command, now).toEntity(actor)
        if (command is CareEventCommand.Create) insert(next) else check(update(next) == 1)
        insertRevision(CareEventRevisionEntity(command.operationId, next.id, actor.caregiverId,
            if (command is CareEventCommand.Create) "CREATED" else "REVISED", fingerprint, next))
        return next
    }

    /** The event revision and all selected images commit or roll back together. */
    @Transaction
    open suspend fun applyWithPhotos(actor: CareEventActor, command: CareEventCommand, fingerprint: String,
        now: LocalDateTime, photos: List<CareEventPhotoInput>, removedPhotoIds: Set<String> = emptySet()): CareEventEntity {
        require(photos.size <= 5 && photos.map { it.id }.distinct().size == photos.size)
        require(photos.all { it.id.isNotBlank() && it.jpeg.isNotEmpty() && it.jpeg.size <= CARE_EVENT_MAX_PHOTO_BYTES })
        require(removedPhotoIds.size <= 20 && removedPhotoIds.none(String::isBlank))
        val replacements = photos.mapNotNull { it.replacesId }
        require(replacements.distinct().size == replacements.size && replacements.all { it in removedPhotoIds })
        val event = apply(actor, command, fingerprint, now)
        removedPhotoIds.forEach { photoId ->
            require(markPhotoRemoved(photoId, event.id, actor.profileId, actor.personUnderCareId, event.revision) == 1)
        }
        photos.forEach { photo ->
            if (existingPhoto(photo.id, event.id, actor.profileId, actor.personUnderCareId) == 0) {
                require(photoCount(event.id, actor.profileId, actor.personUnderCareId) < 5)
                val order = if (photo.replacesId != null)
                    requireNotNull(photoOrder(photo.replacesId, event.id, actor.profileId, actor.personUnderCareId))
                else nextPhotoOrder(event.id, actor.profileId, actor.personUnderCareId)
                insertPhoto(CareEventPhotoEntity(photo.id, event.id, actor.profileId, actor.personUnderCareId,
                    actor.caregiverId, event.revision, System.currentTimeMillis(), photo.jpeg, displayOrder = order))
            }
        }
        return event
    }

    private suspend fun authorize(actor: CareEventActor) {
        if (!actor.authorizationCurrent || currentRelationship(actor.profileId, actor.caregiverId, actor.personUnderCareId) != 1)
            throw CareEventAuthorizationException()
    }
}
