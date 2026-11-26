package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "glucose_follow_ups",
    indices = [
        Index(value = ["personUnderCareId"], unique = true),
    ],
)
data class GlucoseFollowUpEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val createdAt: String,
    val createdByActorId: String,
    val currentRevisionNumber: Int,
)

@Entity(
    tableName = "glucose_follow_up_revisions",
    foreignKeys = [
        ForeignKey(
            entity = GlucoseFollowUpEntity::class,
            parentColumns = ["id"],
            childColumns = ["followUpId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["followUpId", "revisionNumber"], unique = true),
    ],
)
data class GlucoseFollowUpRevisionEntity(
    @PrimaryKey val id: String,
    val followUpId: String,
    val revisionNumber: Int,
    val indication: String,
    val frequency: String,
    val weekdaysMask: Int,
    val everyNDaysInterval: Int?,
    val everyNDaysReferenceEpochDay: Long?,
    val startsOnEpochDay: Long,
    val endsOnEpochDay: Long?,
    val effectiveFromEpochDay: Long,
    val remindersEnabled: Boolean,
    val changeKind: String,
    val changeReason: String?,
    val capturedAt: String,
    val authorActorId: String,
)

@Entity(
    tableName = "glucose_follow_up_slots",
    foreignKeys = [
        ForeignKey(
            entity = GlucoseFollowUpRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["followUpRevisionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["followUpRevisionId"]),
        Index(value = ["followUpRevisionId", "localTimeMinute"], unique = true),
    ],
)
data class GlucoseFollowUpSlotEntity(
    @PrimaryKey val id: String,
    val logicalId: String,
    val followUpRevisionId: String,
    val localTimeMinute: Int,
    val timingType: String,
    val windowEndMinute: Int?,
    val endsNextDay: Boolean,
    val suggestedContext: String,
    val reminderEnabled: Boolean,
    val reminderMinute: Int?,
    val reminderNextDay: Boolean,
)

@Entity(
    tableName = "glucose_occurrence_decisions",
    foreignKeys = [
        ForeignKey(
            entity = GlucoseFollowUpEntity::class,
            parentColumns = ["id"],
            childColumns = ["followUpId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["followUpId"]),
        Index(value = ["occurrenceId"], unique = true),
    ],
)
data class GlucoseOccurrenceDecisionEntity(
    @PrimaryKey val id: String,
    val followUpId: String,
    val followUpRevisionId: String,
    val occurrenceId: String,
    val scheduledAt: String,
    val kind: String,
    val reason: String?,
    val capturedAt: String,
    val actorId: String,
)

@Entity(
    tableName = "glucose_occurrence_decision_revisions",
    foreignKeys = [
        ForeignKey(
            entity = GlucoseOccurrenceDecisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["decisionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["decisionId"]),
        Index(value = ["occurrenceId", "revisionNumber"], unique = true),
    ],
)
data class GlucoseOccurrenceDecisionRevisionEntity(
    @PrimaryKey val id: String,
    val decisionId: String,
    val occurrenceId: String,
    val revisionNumber: Int,
    val operation: String,
    val reason: String?,
    val capturedAt: String,
    val actorId: String,
)
