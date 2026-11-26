package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "blood_pressure_measurements",
    foreignKeys = [
        ForeignKey(
            entity = CareRelationshipEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["caregiverId"],
        ),
        ForeignKey(
            entity = PersonUnderCareEntity::class,
            parentColumns = ["id"],
            childColumns = ["personUnderCareId"],
        ),
    ],
    indices = [
        Index("profileId"),
        Index("caregiverId"),
        Index("personUnderCareId"),
        Index(
            value = ["personUnderCareId", "scheduledOccurrenceId"],
            unique = true,
        ),
    ],
)
data class BloodPressureMeasurementEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val systolic: String,
    val diastolic: String,
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
    tableName = "blood_pressure_audit",
    foreignKeys = [
        ForeignKey(
            entity = BloodPressureMeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["actorId"],
        ),
    ],
    indices = [Index("recordId"), Index("actorId")],
)
data class BloodPressureAuditEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val actorId: String,
    val operation: String,
    val capturedAt: String,
)

@Entity(
    tableName = "blood_pressure_revisions",
    foreignKeys = [
        ForeignKey(
            entity = BloodPressureMeasurementEntity::class,
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
data class BloodPressureRevisionEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val actorId: String,
    val operation: String,
    val systolic: String,
    val diastolic: String,
    val actualContext: String,
    val actualAt: String,
    val note: String?,
    val capturedAt: String,
)

@Entity(
    tableName = "blood_pressure_follow_ups",
    indices = [Index(value = ["personUnderCareId"], unique = true)],
)
data class BloodPressureFollowUpEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val createdAt: String,
    val createdByActorId: String,
    val currentRevisionNumber: Int,
)

@Entity(
    tableName = "blood_pressure_follow_up_revisions",
    foreignKeys = [
        ForeignKey(
            entity = BloodPressureFollowUpEntity::class,
            parentColumns = ["id"],
            childColumns = ["followUpId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["followUpId", "revisionNumber"], unique = true),
    ],
)
data class BloodPressureFollowUpRevisionEntity(
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
    tableName = "blood_pressure_follow_up_slots",
    foreignKeys = [
        ForeignKey(
            entity = BloodPressureFollowUpRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["followUpRevisionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("followUpRevisionId"),
        Index(
            value = ["followUpRevisionId", "localTimeMinute"],
            unique = true,
        ),
    ],
)
data class BloodPressureFollowUpSlotEntity(
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
    tableName = "blood_pressure_occurrence_decisions",
    foreignKeys = [
        ForeignKey(
            entity = BloodPressureFollowUpEntity::class,
            parentColumns = ["id"],
            childColumns = ["followUpId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("followUpId"),
        Index(value = ["occurrenceId"], unique = true),
    ],
)
data class BloodPressureOccurrenceDecisionEntity(
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
    tableName = "blood_pressure_occurrence_decision_revisions",
    foreignKeys = [
        ForeignKey(
            entity = BloodPressureOccurrenceDecisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["decisionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("decisionId"),
        Index(
            value = ["occurrenceId", "revisionNumber"],
            unique = true,
        ),
    ],
)
data class BloodPressureOccurrenceDecisionRevisionEntity(
    @PrimaryKey val id: String,
    val decisionId: String,
    val occurrenceId: String,
    val revisionNumber: Int,
    val operation: String,
    val reason: String?,
    val capturedAt: String,
    val actorId: String,
)
