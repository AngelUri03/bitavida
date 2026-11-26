package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.ColumnInfo
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "medical_indications",
    foreignKeys = [
        ForeignKey(
            entity = PersonUnderCareEntity::class,
            parentColumns = ["id"],
            childColumns = ["personUnderCareId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdByActorId"],
        ),
    ],
    indices = [Index("personUnderCareId"), Index("createdByActorId")],
)
data class MedicalIndicationEntity(
    @PrimaryKey val id: String,
    val personUnderCareId: String,
    val origin: String,
    val createdAtEpochMillis: Long,
    val createdByActorId: String,
    val contentMode: String,
    val currentRevisionNumber: Int,
)

@Entity(
    tableName = "medical_indication_revisions",
    foreignKeys = [
        ForeignKey(
            entity = MedicalIndicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["indicationId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["authorActorId"],
        ),
    ],
    indices = [
        Index("indicationId"),
        Index("authorActorId"),
        Index(value = ["indicationId", "revisionNumber"], unique = true),
    ],
)
data class MedicalIndicationRevisionEntity(
    @PrimaryKey val id: String,
    val indicationId: String,
    val revisionNumber: Int,
    val originalText: String,
    val indicatedOnEpochDay: Long,
    val capturedAtEpochMillis: Long,
    val declaredProfessionalOrSource: String?,
    val validFromEpochDay: Long?,
    val validUntilEpochDay: Long?,
    val status: String,
    val changeKind: String,
    val changeReason: String?,
    val authorActorId: String,
)

@Entity(
    tableName = "medication_plans",
    foreignKeys = [
        ForeignKey(
            entity = PersonUnderCareEntity::class,
            parentColumns = ["id"],
            childColumns = ["personUnderCareId"],
        ),
        ForeignKey(
            entity = MedicalIndicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicalIndicationId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdByActorId"],
        ),
    ],
    indices = [
        Index("personUnderCareId"),
        Index("medicalIndicationId"),
        Index("createdByActorId"),
    ],
)
data class MedicationPlanEntity(
    @PrimaryKey val id: String,
    val medicationSeriesId: String?,
    val personUnderCareId: String,
    val medicalIndicationId: String?,
    val medicalContentOrigin: String,
    val scheduleOrigin: String,
    val createdAtEpochMillis: Long,
    val createdByActorId: String,
    val contentMode: String,
    val currentRevisionNumber: Int,
)

@Entity(
    tableName = "medication_plan_revisions",
    foreignKeys = [
        ForeignKey(
            entity = MedicationPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationPlanId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["authorActorId"],
        ),
    ],
    indices = [
        Index("medicationPlanId"),
        Index("authorActorId"),
        Index(value = ["medicationPlanId", "revisionNumber"], unique = true),
    ],
)
data class MedicationPlanRevisionEntity(
    @PrimaryKey val id: String,
    val medicationPlanId: String,
    val revisionNumber: Int,
    val medicationName: String,
    val doseAsIndicated: String,
    val unitOrPresentation: String?,
    val frequencyAsIndicated: String?,
    val startsOnEpochDay: Long,
    val endsOnEpochDay: Long?,
    val originalAdditionalInstructions: String?,
    val capturedAtEpochMillis: Long,
    val authorActorId: String,
    val changeKind: String,
    val changeReason: String?,
    val lifecycleEffectiveAtEpochMillis: Long?,
)

@Entity(
    tableName = "scheduled_doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationPlanRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationPlanRevisionId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["configuredByActorId"],
        ),
    ],
    indices = [
        Index("medicationPlanRevisionId"),
        Index("configuredByActorId"),
        Index(value = ["medicationPlanRevisionId", "localTimeMinutes"], unique = true),
    ],
)
data class ScheduledDoseEntity(
    @PrimaryKey val id: String,
    val medicationPlanRevisionId: String,
    val localTimeMinutes: Int,
    val configuredAtEpochMillis: Long,
    val configuredByActorId: String,
    val origin: String,
)

@Entity(
    tableName = "medication_configurations",
    foreignKeys = [
        ForeignKey(
            entity = MedicationPlanRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationPlanRevisionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationPlanRevisionId", unique = true)],
)
data class MedicationConfigurationEntity(
    @PrimaryKey val id: String,
    val medicationPlanRevisionId: String,
    val form: String,
    val concentrationValue: String?,
    val concentrationUnit: String?,
    val baseAmountValue: String,
    val baseAmountUnit: String,
    val frequency: String,
    val weekdaysMask: Int,
    val everyNDaysInterval: Int?,
    val everyNDaysReferenceEpochDay: Long?,
    val hourlyInterval: Int?,
    val hourlyMode: String?,
    val hourlyReferenceEpochDay: Long?,
    val hourlyReferenceMinute: Int?,
    val hourlyWindowStartMinute: Int?,
    val hourlyWindowEndMinute: Int?,
    val hourlyWindowEndsNextDay: Boolean,
    val hourlyFirstAdministrationMinute: Int?,
    val cycleActiveDays: Int?,
    val cycleRestDays: Int?,
    val cycleReferenceEpochDay: Long?,
    val cycleCount: Int?,
    val asNeededInstruction: String?,
    val asNeededRestrictions: String?,
    val startsOnEpochDay: Long,
    val endsOnEpochDay: Long?,
    val status: String,
    val reminderPreference: String,
    val secondReminderEnabled: Boolean,
    val reminderEffectiveState: String,
)

@Entity(
    tableName = "medication_administrations",
    foreignKeys = [
        ForeignKey(
            entity = MedicationPlanRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationPlanRevisionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationPlanRevisionId")],
)
data class MedicationAdministrationEntity(
    @PrimaryKey val id: String,
    val logicalId: String?,
    val medicationPlanRevisionId: String,
    val timing: String,
    val exactTimeMinute: Int?,
    val windowStartMinute: Int?,
    val windowEndMinute: Int?,
    val windowEndsNextDay: Boolean,
    val noticeTimeMinute: Int?,
    val noticeNextDay: Boolean,
    val reminderEnabled: Boolean?,
    val amountOverrideValue: String?,
    val amountOverrideUnit: String?,
    val activityRelation: String?,
    val customActivity: String?,
    val activityNoticeTimeMinute: Int?,
)

@Entity(
    tableName = "medication_intake_records",
    foreignKeys = [
        ForeignKey(
            entity = MedicationPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationPlanId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdByActorId"],
        ),
    ],
    indices = [Index("medicationPlanId"), Index("createdByActorId")],
)
data class MedicationIntakeRecordEntity(
    @PrimaryKey val id: String,
    val medicationPlanId: String,
    val createdAtEpochMillis: Long,
    val createdByActorId: String,
    val currentRevisionNumber: Int,
)

@Entity(
    tableName = "medication_intake_revisions",
    foreignKeys = [
        ForeignKey(
            entity = MedicationIntakeRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["intakeRecordId"],
        ),
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["authorActorId"],
        ),
    ],
    indices = [
        Index("intakeRecordId"),
        Index("authorActorId"),
        Index(value = ["intakeRecordId", "revisionNumber"], unique = true),
    ],
)
data class MedicationIntakeRevisionEntity(
    @PrimaryKey val id: String,
    val intakeRecordId: String,
    val revisionNumber: Int,
    val outcome: String,
    val scheduledEpochDay: Long,
    val scheduledMinute: Int,
    val actualEpochDay: Long?,
    val actualMinute: Int?,
    val actualAmountValue: String?,
    val actualAmountUnit: String?,
    @ColumnInfo(defaultValue = "NULL")
    val scheduledOccurrenceId: String? = null,
    val observation: String?,
    val changeKind: String,
    val changeReason: String?,
    val capturedAtEpochMillis: Long,
    val authorActorId: String,
)
