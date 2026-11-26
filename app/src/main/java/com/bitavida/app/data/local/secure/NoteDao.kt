package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.bitavida.app.data.notes.toDomain
import com.bitavida.app.data.notes.toEntity
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteAuthorizationException
import com.bitavida.app.domain.usecase.NoteCommand
import com.bitavida.app.domain.usecase.NoteProblem
import com.bitavida.app.domain.usecase.NoteValidationException
import com.bitavida.app.domain.usecase.applyNoteCommand
import java.time.LocalDateTime

data class PersistedNoteReportHistory(
    val current: List<NoteEntity>,
    val revisions: List<NoteRevisionEntity>,
)

@Dao
abstract class NoteDao {
    @Query("""
        SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profile AND r.caregiverId = :actor AND r.personUnderCareId = :person
        AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
            AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId
            AND c.status = 'ACTIVE')
    """)
    abstract suspend fun currentRelationship(profile: String, actor: String, person: String): Int

    @Query("SELECT * FROM notes WHERE profileId = :profile AND personUnderCareId = :person")
    abstract suspend fun records(profile: String, person: String): List<NoteEntity>

    @Query("SELECT * FROM note_revisions WHERE operationId = :operationId LIMIT 1")
    abstract suspend fun operation(operationId: String): NoteRevisionEntity?

    @Query("SELECT * FROM note_revisions WHERE noteId = :id ORDER BY snapshot_revision")
    abstract suspend fun revisions(id: String): List<NoteRevisionEntity>

    @Query("SELECT * FROM note_images WHERE id = :id AND profileId = :profile AND personUnderCareId = :person LIMIT 1")
    abstract suspend fun image(id: String, profile: String, person: String): NoteImageEntity?

    @Query("SELECT COUNT(*) FROM note_images WHERE id = :id AND profileId = :profile AND personUnderCareId = :person")
    abstract suspend fun ownsImage(id: String, profile: String, person: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: NoteEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun update(entity: NoteEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRevision(entity: NoteRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertImage(entity: NoteImageEntity)

    @Query("""
        DELETE FROM note_images WHERE id = :id AND profileId = :profile AND personUnderCareId = :person
        AND NOT EXISTS (SELECT 1 FROM notes n WHERE n.imageId = note_images.id)
        AND NOT EXISTS (SELECT 1 FROM note_revisions r WHERE r.snapshot_imageId = note_images.id)
    """)
    abstract suspend fun discardUnlinkedImage(id: String, profile: String, person: String)

    @Query("""
        DELETE FROM note_images WHERE profileId = :profile AND personUnderCareId = :person AND importedAt < :before
        AND NOT EXISTS (SELECT 1 FROM notes n WHERE n.imageId = note_images.id)
        AND NOT EXISTS (SELECT 1 FROM note_revisions r WHERE r.snapshot_imageId = note_images.id)
    """)
    abstract suspend fun discardAbandonedImages(profile: String, person: String, before: String)

    @Transaction
    open suspend fun load(actor: NoteActor): List<NoteEntity> {
        authorize(actor)
        return records(actor.profileId, actor.personUnderCareId)
    }

    /** Read-only, authorized snapshot including logically deleted records and their revisions. */
    @Transaction
    open suspend fun loadReportHistory(actor: NoteActor): PersistedNoteReportHistory {
        authorize(actor)
        val current = records(actor.profileId, actor.personUnderCareId)
        return PersistedNoteReportHistory(current, current.flatMap { revisions(it.id) })
    }

    @Transaction
    open suspend fun apply(actor: NoteActor, command: NoteCommand, fingerprint: String, now: LocalDateTime): NoteEntity {
        authorize(actor)
        operation(command.operationId)?.let { previous ->
            if (previous.requestFingerprint != fingerprint || previous.noteId != command.id ||
                previous.actorId != actor.caregiverId || previous.snapshot.profileId != actor.profileId ||
                previous.snapshot.personUnderCareId != actor.personUnderCareId) {
                throw NoteValidationException(NoteProblem.CONFLICT)
            }
            return previous.snapshot
        }
        val next = applyNoteCommand(records(actor.profileId, actor.personUnderCareId).map { it.toDomain() },
            actor.personUnderCareId, command, now)
        next.imageId?.let {
            if (ownsImage(it, actor.profileId, actor.personUnderCareId) != 1) {
                throw NoteValidationException(NoteProblem.INVALID_IMAGE)
            }
        }
        val entity = next.toEntity(actor)
        if (command is NoteCommand.Create) insert(entity) else check(update(entity) == 1)
        insertRevision(NoteRevisionEntity(command.operationId, next.id, actor.caregiverId,
            when (command) {
                is NoteCommand.Create -> "CREATED"
                is NoteCommand.Edit -> "EDITED"
                is NoteCommand.Delete -> "DELETED"
            }, fingerprint, entity))
        return entity
    }

    @Transaction
    open suspend fun storeImage(actor: NoteActor, image: NoteImageEntity) {
        authorize(actor)
        require(image.profileId == actor.profileId && image.personUnderCareId == actor.personUnderCareId)
        require(image.jpeg.isNotEmpty() && image.jpeg.size <= 512 * 1024)
        insertImage(image)
    }

    @Transaction
    open suspend fun readImage(actor: NoteActor, id: String): ByteArray? {
        authorize(actor)
        return image(id, actor.profileId, actor.personUnderCareId)?.jpeg
    }

    @Transaction
    open suspend fun discardImage(actor: NoteActor, id: String) {
        authorize(actor)
        discardUnlinkedImage(id, actor.profileId, actor.personUnderCareId)
    }

    @Transaction
    open suspend fun pruneImages(actor: NoteActor, before: String) {
        authorize(actor)
        discardAbandonedImages(actor.profileId, actor.personUnderCareId, before)
    }

    private suspend fun authorize(actor: NoteActor) {
        if (!actor.authorizationCurrent ||
            currentRelationship(actor.profileId, actor.caregiverId, actor.personUnderCareId) != 1) throw NoteAuthorizationException()
    }
}
