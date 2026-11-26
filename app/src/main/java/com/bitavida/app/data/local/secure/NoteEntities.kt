package com.bitavida.app.data.local.secure

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["caregiverId"]),
        ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
        ForeignKey(entity = NoteImageEntity::class, parentColumns = ["id"], childColumns = ["imageId"]),
    ],
    indices = [Index("profileId"), Index("caregiverId"), Index("personUnderCareId"), Index("imageId"),
        Index(value = ["profileId", "legacyIndicationId"], unique = true)],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val title: String,
    val content: String?,
    val imageId: String?,
    val createdAt: String,
    val modifiedAt: String,
    val revision: Int,
    val deletedAt: String?,
    val legacyIndicationId: String?,
)

@Entity(
    tableName = "note_revisions",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["actorId"]),
    ],
    indices = [Index(value = ["noteId", "snapshot_revision"], unique = true), Index("actorId"), Index("snapshot_imageId")],
)
data class NoteRevisionEntity(
    @PrimaryKey val operationId: String,
    val noteId: String,
    val actorId: String,
    val changeKind: String,
    val requestFingerprint: String,
    @Embedded(prefix = "snapshot_") val snapshot: NoteEntity,
)

/** Re-encoded image bytes live inside the same encrypted private database, never a gallery URI. */
@Entity(
    tableName = "note_images",
    foreignKeys = [
        ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
        ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
    ],
    indices = [Index("profileId"), Index("personUnderCareId")],
)
data class NoteImageEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val personUnderCareId: String,
    val jpeg: ByteArray,
    val importedAt: String,
) {
    override fun equals(other: Any?): Boolean = other is NoteImageEntity && id == other.id &&
        profileId == other.profileId && personUnderCareId == other.personUnderCareId &&
        jpeg.contentEquals(other.jpeg) && importedAt == other.importedAt

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + profileId.hashCode()
        result = 31 * result + personUnderCareId.hashCode()
        result = 31 * result + jpeg.contentHashCode()
        return 31 * result + importedAt.hashCode()
    }
}
