package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Payloads are versioned snapshots inside the existing SQLCipher database. */
@Entity(
    tableName = "observation_follow_ups",
    foreignKeys = [
        ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["caregiverId"]),
        ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
    ],
    indices = [Index("profileId"), Index("caregiverId"), Index("personUnderCareId"),
        Index(value = ["personUnderCareId", "observationId"], unique = true)],
)
data class ObservationFollowUpEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val observationId: String,
    val createdAt: String,
    val currentRevisionNumber: Int,
)

@Entity(
    tableName = "observation_follow_up_revisions",
    foreignKeys = [
        ForeignKey(entity = ObservationFollowUpEntity::class, parentColumns = ["id"], childColumns = ["followUpId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["actorId"]),
    ],
    indices = [Index(value = ["followUpId", "revisionNumber"], unique = true), Index("actorId")],
)
data class ObservationFollowUpRevisionEntity(
    @PrimaryKey val id: String,
    val followUpId: String,
    val revisionNumber: Int,
    val actorId: String,
    val capturedAt: String,
    val payload: String,
)

@Entity(
    tableName = "observation_records",
    foreignKeys = [
        ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["caregiverId"]),
        ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
        ForeignKey(entity = ObservationFollowUpEntity::class, parentColumns = ["id"], childColumns = ["followUpId"]),
    ],
    indices = [Index("profileId"), Index("caregiverId"), Index("personUnderCareId"), Index("followUpId"),
        Index(value = ["personUnderCareId", "occurrenceId"], unique = true)],
)
data class ObservationRecordEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val observationId: String,
    val occurredOnEpochDay: Long,
    val followUpId: String?,
    val occurrenceId: String?,
    val createdAt: String,
    val payload: String,
)

@Entity(
    tableName = "observation_photos",
    foreignKeys = [
        ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
        ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
    ],
    indices = [Index("profileId"), Index("personUnderCareId")],
)
data class ObservationPhotoEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val personUnderCareId: String,
    val jpeg: ByteArray,
)

@Entity(
    tableName = "observation_record_photos",
    primaryKeys = ["recordId", "photoId"],
    foreignKeys = [
        ForeignKey(entity = ObservationRecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"]),
        ForeignKey(entity = ObservationPhotoEntity::class, parentColumns = ["id"], childColumns = ["photoId"]),
    ],
    indices = [Index("photoId")],
)
data class ObservationRecordPhotoEntity(val recordId: String, val photoId: String)

/** Original facts remain in observation_records; corrections are immutable snapshots. */
@Entity(
    tableName = "observation_record_revisions",
    foreignKeys = [
        ForeignKey(entity = ObservationRecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["actorId"]),
    ],
    indices = [Index(value = ["recordId", "revisionNumber"], unique = true), Index("actorId")],
)
data class ObservationRecordRevisionEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val actorId: String,
    val correctedAt: String,
    val reason: String,
    val payload: String,
)
