package com.bitavida.app.data.local.secure

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        TechnicalProbeEntity::class,
        CaregiverEntity::class,
        PersonUnderCareEntity::class,
        CareRelationshipEntity::class,
        ConsentRecordEntity::class,
        LegalAcceptanceEntity::class,
        MedicalIndicationEntity::class,
        MedicalIndicationRevisionEntity::class,
        MedicationPlanEntity::class,
        MedicationPlanRevisionEntity::class,
        ScheduledDoseEntity::class,
        SensitiveDataConsentEntity::class,
        MedicationConfigurationEntity::class,
        MedicationAdministrationEntity::class,
        MedicationIntakeRecordEntity::class,
        MedicationIntakeRevisionEntity::class,
        GlucoseMeasurementEntity::class,
        GlucoseAuditEntity::class,
        GlucoseRevisionEntity::class,
        GlucoseFollowUpEntity::class,
        GlucoseFollowUpRevisionEntity::class,
        GlucoseFollowUpSlotEntity::class,
        GlucoseOccurrenceDecisionEntity::class,
        GlucoseOccurrenceDecisionRevisionEntity::class,
        BloodPressureMeasurementEntity::class,
        BloodPressureAuditEntity::class,
        BloodPressureRevisionEntity::class,
        BloodPressureFollowUpEntity::class,
        BloodPressureFollowUpRevisionEntity::class,
        BloodPressureFollowUpSlotEntity::class,
        BloodPressureOccurrenceDecisionEntity::class,
        BloodPressureOccurrenceDecisionRevisionEntity::class,
        WeightMeasurementEntity::class,
        WeightTargetEntity::class,
        WeightAuditEntity::class,
        WeightRevisionEntity::class,
        WeightFollowUpEntity::class,
        WeightFollowUpRevisionEntity::class,
        WeightFollowUpSlotEntity::class,
        WeightOccurrenceDecisionEntity::class,
        WeightOccurrenceDecisionRevisionEntity::class,
        ObservationFollowUpEntity::class,
        ObservationFollowUpRevisionEntity::class,
        ObservationRecordEntity::class,
        ObservationPhotoEntity::class,
        ObservationRecordPhotoEntity::class,
        ObservationRecordRevisionEntity::class,
        CareReminderEntity::class,
        CareReminderRevisionEntity::class,
        NoteEntity::class,
        NoteRevisionEntity::class,
        NoteImageEntity::class,
        CareEventEntity::class,
        CareEventRevisionEntity::class,
        CareEventPhotoEntity::class,
        NotificationDeliveryEntity::class,
        CareProfileNameRevisionEntity::class,
    ],
    version = 33,
    exportSchema = true,
)
abstract class SecureSpikeDatabase : RoomDatabase() {
    abstract fun technicalProbeDao(): TechnicalProbeDao

    abstract fun careProfileDao(): CareProfileDao

    abstract fun medicalCareDao(): MedicalCareDao

    abstract fun glucoseDao(): GlucoseDao

    abstract fun glucoseFollowUpDao(): GlucoseFollowUpDao

    abstract fun bloodPressureDao(): BloodPressureDao

    abstract fun bloodPressureFollowUpDao(): BloodPressureFollowUpDao

    abstract fun weightDao(): WeightDao

    abstract fun weightFollowUpDao(): WeightFollowUpDao

    abstract fun observationDao(): ObservationDao

    abstract fun careReminderDao(): CareReminderDao

    abstract fun noteDao(): NoteDao

    abstract fun careEventDao(): CareEventDao

    abstract fun careEventPhotoDao(): CareEventPhotoDao

    abstract fun notificationDeliveryDao(): NotificationDeliveryDao
}
