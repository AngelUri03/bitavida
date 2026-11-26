package com.bitavida.app.data.local.secure

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.bitavida.app.domain.repository.CareEventActor
import com.bitavida.app.domain.repository.CareEventAuthorizationException
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION

/** Append-only attachment: creator, event revision and time form the audit record. */
@Entity(tableName = "care_event_photos", foreignKeys = [
    ForeignKey(entity = CareEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"]),
    ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["actorId"]),
], indices = [Index("eventId"), Index("actorId")])
data class CareEventPhotoEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val profileId: String,
    val personUnderCareId: String,
    val actorId: String,
    val eventRevision: Int,
    val addedAtEpochMillis: Long,
    val jpeg: ByteArray,
    val removedAtRevision: Int? = null,
    @ColumnInfo(defaultValue = "0") val displayOrder: Long = 0,
)

@Dao
abstract class CareEventPhotoDao {
    @Query("""SELECT COUNT(*) FROM care_relationships r
        WHERE r.profileId = :profile AND r.caregiverId = :actor AND r.personUnderCareId = :person
        AND EXISTS (SELECT 1 FROM consent_records c WHERE c.profileId = r.profileId
            AND c.caregiverId = r.caregiverId AND c.personUnderCareId = r.personUnderCareId AND c.status = 'ACTIVE')
        AND EXISTS (SELECT 1 FROM sensitive_data_consents s WHERE s.profileId = r.profileId
            AND s.caregiverId = r.caregiverId AND s.personUnderCareId = r.personUnderCareId
            AND s.documentVersion = :version AND s.status = 'ACTIVE')""")
    protected abstract suspend fun authorized(profile: String, actor: String, person: String, version: String): Int

    @Query("SELECT COUNT(*) FROM care_events WHERE id = :event AND profileId = :profile AND personUnderCareId = :person AND revision = :revision")
    protected abstract suspend fun currentEvent(event: String, profile: String, person: String, revision: Int): Int

    @Query("SELECT id FROM care_event_photos WHERE eventId = :event AND profileId = :profile AND personUnderCareId = :person AND removedAtRevision IS NULL ORDER BY displayOrder, addedAtEpochMillis, id")
    protected abstract suspend fun photoIds(event: String, profile: String, person: String): List<String>

    @Query("SELECT jpeg FROM care_event_photos WHERE id = :id AND eventId = :event AND profileId = :profile AND personUnderCareId = :person LIMIT 1")
    protected abstract suspend fun photoBytes(id: String, event: String, profile: String, person: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(photo: CareEventPhotoEntity)

    @Query("SELECT COALESCE(MAX(displayOrder), -1) + 1 FROM care_event_photos WHERE eventId = :event AND profileId = :profile AND personUnderCareId = :person")
    protected abstract suspend fun nextPhotoOrder(event: String, profile: String, person: String): Long

    @Transaction
    open suspend fun list(actor: CareEventActor, event: String): List<String> {
        authorize(actor)
        return photoIds(event, actor.profileId, actor.personUnderCareId)
    }

    @Transaction
    open suspend fun read(actor: CareEventActor, event: String, id: String): ByteArray? {
        authorize(actor)
        return photoBytes(id, event, actor.profileId, actor.personUnderCareId)
    }

    @Transaction
    open suspend fun add(actor: CareEventActor, photo: CareEventPhotoEntity) {
        authorize(actor)
        require(photo.profileId == actor.profileId && photo.personUnderCareId == actor.personUnderCareId &&
            photo.actorId == actor.caregiverId)
        require(currentEvent(photo.eventId, actor.profileId, actor.personUnderCareId, photo.eventRevision) == 1)
        require(photoIds(photo.eventId, actor.profileId, actor.personUnderCareId).size < 5)
        require(photo.jpeg.isNotEmpty() && photo.jpeg.size <= com.bitavida.app.domain.repository.CARE_EVENT_MAX_PHOTO_BYTES)
        insert(photo.copy(displayOrder = nextPhotoOrder(photo.eventId, actor.profileId, actor.personUnderCareId)))
    }

    private suspend fun authorize(actor: CareEventActor) {
        if (!actor.authorizationCurrent ||
            authorized(actor.profileId, actor.caregiverId, actor.personUnderCareId, GLUCOSE_SENSITIVE_CONSENT_VERSION) != 1
        ) throw CareEventAuthorizationException()
    }
}

/** Removal is a revisioned association change; original encrypted image remains available for audit. */
internal val CareEventPhotoRemovalMigration30To31 = object : Migration(30, 31) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE care_event_photos ADD COLUMN removedAtRevision INTEGER")
    }
}

/** Backfill the pre-existing visual order deterministically, including ties in capture time. */
internal val CareEventPhotoOrderMigration31To32 = object : Migration(31, 32) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE care_event_photos ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("""UPDATE care_event_photos SET displayOrder =
            (SELECT COUNT(*) FROM care_event_photos prior
             WHERE prior.eventId = care_event_photos.eventId
             AND prior.profileId = care_event_photos.profileId
             AND prior.personUnderCareId = care_event_photos.personUnderCareId
             AND (prior.addedAtEpochMillis < care_event_photos.addedAtEpochMillis
               OR (prior.addedAtEpochMillis = care_event_photos.addedAtEpochMillis AND prior.id < care_event_photos.id)))""")
    }
}

internal val CareEventPhotoMigration26To27 = object : Migration(26, 27) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS care_event_photos (id TEXT NOT NULL, eventId TEXT NOT NULL, profileId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, actorId TEXT NOT NULL, eventRevision INTEGER NOT NULL, addedAtEpochMillis INTEGER NOT NULL, jpeg BLOB NOT NULL, PRIMARY KEY(id), FOREIGN KEY(eventId) REFERENCES care_events(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(actorId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_care_event_photos_eventId ON care_event_photos(eventId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_care_event_photos_actorId ON care_event_photos(actorId)")
    }
}
