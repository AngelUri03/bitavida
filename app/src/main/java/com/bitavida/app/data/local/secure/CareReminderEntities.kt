package com.bitavida.app.data.local.secure

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "care_reminders",
    foreignKeys = [
        ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["caregiverId"]),
        ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
    ],
    indices = [Index("profileId"), Index("caregiverId"), Index("personUnderCareId")],
)
data class CareReminderEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val title: String,
    val details: String?,
    val scheduledAt: String,
    val notificationsEnabled: Boolean,
    val status: String,
    val createdAt: String,
    val modifiedAt: String,
    val closedAt: String?,
    val revision: Int,
    val originallyScheduledAt: String,
)

/** Each confirmed operation retains its complete snapshot; no history UI or physical deletion. */
@Entity(
    tableName = "care_reminder_revisions",
    foreignKeys = [
        ForeignKey(entity = CareReminderEntity::class, parentColumns = ["id"], childColumns = ["reminderId"]),
        ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["actorId"]),
    ],
    indices = [Index(value = ["reminderId", "snapshot_revision"], unique = true), Index("actorId")],
)
data class CareReminderRevisionEntity(
    @PrimaryKey val operationId: String,
    val reminderId: String,
    val actorId: String,
    val changeKind: String,
    val requestFingerprint: String,
    @Embedded(prefix = "snapshot_") val snapshot: CareReminderEntity,
)
