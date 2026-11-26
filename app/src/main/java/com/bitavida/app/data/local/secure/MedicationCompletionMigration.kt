package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MedicationCompletionMigration3To4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        medicationCompletionStatements.forEach(connection::execSQL)
    }
}

private val medicationCompletionStatements = listOf(
    "ALTER TABLE scheduled_doses RENAME TO scheduled_doses_v3",
    "ALTER TABLE medication_plan_revisions RENAME TO medication_plan_revisions_v3",
    "ALTER TABLE medication_plans RENAME TO medication_plans_v3",
    """
    CREATE TABLE medication_plans (
        id TEXT NOT NULL,
        personUnderCareId TEXT NOT NULL,
        medicalIndicationId TEXT,
        medicalContentOrigin TEXT NOT NULL,
        scheduleOrigin TEXT NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,
        createdByActorId TEXT NOT NULL,
        contentMode TEXT NOT NULL,
        currentRevisionNumber INTEGER NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(medicalIndicationId) REFERENCES medical_indications(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(createdByActorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    """
    CREATE TABLE medication_plan_revisions (
        id TEXT NOT NULL,
        medicationPlanId TEXT NOT NULL,
        revisionNumber INTEGER NOT NULL,
        medicationName TEXT NOT NULL,
        doseAsIndicated TEXT NOT NULL,
        unitOrPresentation TEXT,
        frequencyAsIndicated TEXT,
        startsOnEpochDay INTEGER NOT NULL,
        endsOnEpochDay INTEGER,
        originalAdditionalInstructions TEXT,
        capturedAtEpochMillis INTEGER NOT NULL,
        authorActorId TEXT NOT NULL,
        changeKind TEXT NOT NULL,
        changeReason TEXT,
        PRIMARY KEY(id),
        FOREIGN KEY(medicationPlanId) REFERENCES medication_plans(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(authorActorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    """
    CREATE TABLE scheduled_doses (
        id TEXT NOT NULL,
        medicationPlanRevisionId TEXT NOT NULL,
        localTimeMinutes INTEGER NOT NULL,
        configuredAtEpochMillis INTEGER NOT NULL,
        configuredByActorId TEXT NOT NULL,
        origin TEXT NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(medicationPlanRevisionId) REFERENCES medication_plan_revisions(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(configuredByActorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    """
    INSERT INTO medication_plans
    SELECT id, personUnderCareId, medicalIndicationId, medicalContentOrigin,
        scheduleOrigin, createdAtEpochMillis, createdByActorId, contentMode,
        currentRevisionNumber
    FROM medication_plans_v3
    """.trimIndent(),
    "INSERT INTO medication_plan_revisions SELECT * FROM medication_plan_revisions_v3",
    "INSERT INTO scheduled_doses SELECT * FROM scheduled_doses_v3",
    "DROP TABLE scheduled_doses_v3",
    "DROP TABLE medication_plan_revisions_v3",
    "DROP TABLE medication_plans_v3",
    "CREATE INDEX index_medication_plans_personUnderCareId ON medication_plans (personUnderCareId)",
    "CREATE INDEX index_medication_plans_medicalIndicationId ON medication_plans (medicalIndicationId)",
    "CREATE INDEX index_medication_plans_createdByActorId ON medication_plans (createdByActorId)",
    "CREATE INDEX index_medication_plan_revisions_medicationPlanId ON medication_plan_revisions (medicationPlanId)",
    "CREATE INDEX index_medication_plan_revisions_authorActorId ON medication_plan_revisions (authorActorId)",
    "CREATE UNIQUE INDEX index_medication_plan_revisions_medicationPlanId_revisionNumber ON medication_plan_revisions (medicationPlanId, revisionNumber)",
    "CREATE INDEX index_scheduled_doses_medicationPlanRevisionId ON scheduled_doses (medicationPlanRevisionId)",
    "CREATE INDEX index_scheduled_doses_configuredByActorId ON scheduled_doses (configuredByActorId)",
    "CREATE UNIQUE INDEX index_scheduled_doses_medicationPlanRevisionId_localTimeMinutes ON scheduled_doses (medicationPlanRevisionId, localTimeMinutes)",
    """
    CREATE TABLE sensitive_data_consents (
        id TEXT NOT NULL,
        profileId TEXT NOT NULL,
        caregiverId TEXT NOT NULL,
        personUnderCareId TEXT NOT NULL,
        documentVersion TEXT NOT NULL,
        acceptedAtEpochMillis INTEGER NOT NULL,
        status TEXT NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(profileId) REFERENCES care_relationships(profileId)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(caregiverId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    "CREATE INDEX index_sensitive_data_consents_profileId ON sensitive_data_consents (profileId)",
    "CREATE INDEX index_sensitive_data_consents_caregiverId ON sensitive_data_consents (caregiverId)",
    "CREATE INDEX index_sensitive_data_consents_personUnderCareId ON sensitive_data_consents (personUnderCareId)",
    "CREATE UNIQUE INDEX index_sensitive_data_consents_profileId_documentVersion ON sensitive_data_consents (profileId, documentVersion)",
    """
    CREATE TABLE medication_configurations (
        id TEXT NOT NULL,
        medicationPlanRevisionId TEXT NOT NULL,
        form TEXT NOT NULL,
        concentrationValue TEXT,
        concentrationUnit TEXT,
        baseAmountValue TEXT NOT NULL,
        baseAmountUnit TEXT NOT NULL,
        frequency TEXT NOT NULL,
        weekdaysMask INTEGER NOT NULL,
        everyNDaysInterval INTEGER,
        everyNDaysReferenceEpochDay INTEGER,
        hourlyInterval INTEGER,
        hourlyMode TEXT,
        hourlyReferenceEpochDay INTEGER,
        hourlyReferenceMinute INTEGER,
        hourlyWindowStartMinute INTEGER,
        hourlyWindowEndMinute INTEGER,
        hourlyWindowEndsNextDay INTEGER NOT NULL,
        hourlyFirstAdministrationMinute INTEGER,
        cycleActiveDays INTEGER,
        cycleRestDays INTEGER,
        cycleReferenceEpochDay INTEGER,
        cycleCount INTEGER,
        asNeededInstruction TEXT,
        asNeededRestrictions TEXT,
        startsOnEpochDay INTEGER NOT NULL,
        endsOnEpochDay INTEGER,
        status TEXT NOT NULL,
        reminderPreference TEXT NOT NULL,
        secondReminderEnabled INTEGER NOT NULL,
        reminderEffectiveState TEXT NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(medicationPlanRevisionId) REFERENCES medication_plan_revisions(id)
            ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE UNIQUE INDEX index_medication_configurations_medicationPlanRevisionId ON medication_configurations (medicationPlanRevisionId)",
    """
    CREATE TABLE medication_administrations (
        id TEXT NOT NULL,
        medicationPlanRevisionId TEXT NOT NULL,
        timing TEXT NOT NULL,
        exactTimeMinute INTEGER,
        windowStartMinute INTEGER,
        windowEndMinute INTEGER,
        windowEndsNextDay INTEGER NOT NULL,
        noticeTimeMinute INTEGER,
        noticeNextDay INTEGER NOT NULL,
        amountOverrideValue TEXT,
        amountOverrideUnit TEXT,
        activityRelation TEXT,
        customActivity TEXT,
        activityNoticeTimeMinute INTEGER,
        PRIMARY KEY(id),
        FOREIGN KEY(medicationPlanRevisionId) REFERENCES medication_plan_revisions(id)
            ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX index_medication_administrations_medicationPlanRevisionId ON medication_administrations (medicationPlanRevisionId)",
)
