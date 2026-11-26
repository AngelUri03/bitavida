package com.bitavida.app.data.local.secure

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "care_events", foreignKeys = [
    ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"]),
    ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["caregiverId"]),
    ForeignKey(entity = PersonUnderCareEntity::class, parentColumns = ["id"], childColumns = ["personUnderCareId"]),
], indices = [Index("profileId"), Index("caregiverId"), Index("personUnderCareId")])
data class CareEventEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val kind: String,
    val title: String,
    val transcribedIndication: String,
    val suggestedDate: String?,
    val scheduledAt: String?,
    val scheduledDate: String?,
    val location: String,
    val instructions: String,
    val caregiverNotes: String,
    val status: String,
    val changeReason: String,
    val createdAt: String,
    val modifiedAt: String,
    val revision: Int,
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "'GENERAL'") val category: String = "GENERAL",
    @ColumnInfo(defaultValue = "''") val specialty: String = "",
    @ColumnInfo(defaultValue = "''") val institution: String = "",
    @ColumnInfo(defaultValue = "''") val office: String = "",
    @ColumnInfo(defaultValue = "60") val reminderMinutesBefore: Int = 60,
    @ColumnInfo(defaultValue = "'LEGACY'") val purpose: String = "LEGACY",
    @ColumnInfo(defaultValue = "'LEGACY'") val timeMode: String = "LEGACY",
    @ColumnInfo(defaultValue = "'UNSPECIFIED'") val duration: String = "UNSPECIFIED",
    @ColumnInfo(defaultValue = "'IN_PERSON'") val modality: String = "IN_PERSON",
    @ColumnInfo(defaultValue = "''") val professional: String = "",
    @ColumnInfo(defaultValue = "''") val reference: String = "",
    @ColumnInfo(defaultValue = "''") val contactInformation: String = "",
    @ColumnInfo(defaultValue = "''") val otherPreparation: String = "",
    @ColumnInfo(defaultValue = "''") val preparations: String = "",
    @ColumnInfo(defaultValue = "''") val reminderOffsetsMinutes: String = "",
    @ColumnInfo(defaultValue = "1") val showInToday: Boolean = true,
    @ColumnInfo(defaultValue = "''") val verifiedUnitId: String = "",
    @ColumnInfo(defaultValue = "'UNSPECIFIED'") val consultationType: String = "UNSPECIFIED",
    @ColumnInfo(defaultValue = "0") val isFollowUp: Boolean = false,
)

@Entity(tableName = "care_event_revisions", foreignKeys = [
    ForeignKey(entity = CareEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"]),
    ForeignKey(entity = CaregiverEntity::class, parentColumns = ["id"], childColumns = ["actorId"]),
], indices = [Index(value = ["eventId", "snapshot_revision"], unique = true), Index("actorId")])
data class CareEventRevisionEntity(
    @PrimaryKey val operationId: String,
    val eventId: String,
    val actorId: String,
    val changeKind: String,
    val requestFingerprint: String,
    @Embedded(prefix = "snapshot_") val snapshot: CareEventEntity,
)
