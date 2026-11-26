package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "glucose_measurements",
    foreignKeys = [
        ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["caregiverId"]),
        ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
    ],
    indices = [
        Index("profileId"), Index("caregiverId"), Index("personUnderCareId"),
        Index(value = ["personUnderCareId", "scheduledOccurrenceId"], unique = true),
    ],
)
data class GlucoseMeasurementEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val reading: String,
    val actualContext: String,
    val actualAt: String,
    val capturedAt: String,
    val origin: String,
    val entryKind: String,
    val followUpId: String?,
    val scheduledOccurrenceId: String?,
    val scheduledAt: String?,
    val expectedContext: String?,
    val note: String?,
    val deletedAt: String? = null,
    val deletedBy: String? = null,
)

@Entity(
    tableName = "glucose_audit",
    foreignKeys = [
        ForeignKey(entity = GlucoseMeasurementEntity::class, parentColumns = ["id"], childColumns = ["recordId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["actorId"]),
    ],
    indices = [Index("recordId"), Index("actorId")],
)
data class GlucoseAuditEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val actorId: String,
    val operation: String,
    val capturedAt: String,
)

@Entity(
    tableName = "glucose_revisions",
    foreignKeys = [
        ForeignKey(
            entity = GlucoseMeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["actorId"],
        ),
    ],
    indices = [
        Index(value = ["recordId", "revisionNumber"], unique = true),
        Index("actorId"),
    ],
)
data class GlucoseRevisionEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val actorId: String,
    val operation: String,
    val reading: String,
    val actualContext: String,
    val actualAt: String,
    val note: String?,
    val capturedAt: String,
)
