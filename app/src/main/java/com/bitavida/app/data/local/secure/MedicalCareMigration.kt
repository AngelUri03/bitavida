package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MedicalCareMigration2To3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        statements.forEach(connection::execSQL)
    }
}

private val statements = listOf(
    """
    CREATE TABLE IF NOT EXISTS medical_indications (
        id TEXT NOT NULL,
        personUnderCareId TEXT NOT NULL,
        origin TEXT NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,
        createdByActorId TEXT NOT NULL,
        contentMode TEXT NOT NULL,
        currentRevisionNumber INTEGER NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(createdByActorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_medical_indications_personUnderCareId ON medical_indications (personUnderCareId)",
    "CREATE INDEX IF NOT EXISTS index_medical_indications_createdByActorId ON medical_indications (createdByActorId)",
    """
    CREATE TABLE IF NOT EXISTS medical_indication_revisions (
        id TEXT NOT NULL,
        indicationId TEXT NOT NULL,
        revisionNumber INTEGER NOT NULL,
        originalText TEXT NOT NULL,
        indicatedOnEpochDay INTEGER NOT NULL,
        capturedAtEpochMillis INTEGER NOT NULL,
        declaredProfessionalOrSource TEXT,
        validFromEpochDay INTEGER,
        validUntilEpochDay INTEGER,
        status TEXT NOT NULL,
        changeKind TEXT NOT NULL,
        changeReason TEXT,
        authorActorId TEXT NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(indicationId) REFERENCES medical_indications(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(authorActorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_medical_indication_revisions_indicationId ON medical_indication_revisions (indicationId)",
    "CREATE INDEX IF NOT EXISTS index_medical_indication_revisions_authorActorId ON medical_indication_revisions (authorActorId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_medical_indication_revisions_indicationId_revisionNumber ON medical_indication_revisions (indicationId, revisionNumber)",
    """
    CREATE TABLE IF NOT EXISTS medication_plans (
        id TEXT NOT NULL,
        personUnderCareId TEXT NOT NULL,
        medicalIndicationId TEXT NOT NULL,
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
    "CREATE INDEX IF NOT EXISTS index_medication_plans_personUnderCareId ON medication_plans (personUnderCareId)",
    "CREATE INDEX IF NOT EXISTS index_medication_plans_medicalIndicationId ON medication_plans (medicalIndicationId)",
    "CREATE INDEX IF NOT EXISTS index_medication_plans_createdByActorId ON medication_plans (createdByActorId)",
    """
    CREATE TABLE IF NOT EXISTS medication_plan_revisions (
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
    "CREATE INDEX IF NOT EXISTS index_medication_plan_revisions_medicationPlanId ON medication_plan_revisions (medicationPlanId)",
    "CREATE INDEX IF NOT EXISTS index_medication_plan_revisions_authorActorId ON medication_plan_revisions (authorActorId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_medication_plan_revisions_medicationPlanId_revisionNumber ON medication_plan_revisions (medicationPlanId, revisionNumber)",
    """
    CREATE TABLE IF NOT EXISTS scheduled_doses (
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
    "CREATE INDEX IF NOT EXISTS index_scheduled_doses_medicationPlanRevisionId ON scheduled_doses (medicationPlanRevisionId)",
    "CREATE INDEX IF NOT EXISTS index_scheduled_doses_configuredByActorId ON scheduled_doses (configuredByActorId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_scheduled_doses_medicationPlanRevisionId_localTimeMinutes ON scheduled_doses (medicationPlanRevisionId, localTimeMinutes)",
)
