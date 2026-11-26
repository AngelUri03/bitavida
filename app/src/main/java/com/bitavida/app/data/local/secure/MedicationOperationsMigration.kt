package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MedicationOperationsMigration5To6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        medicationOperationsStatements.forEach(connection::execSQL)
    }
}

private val medicationOperationsStatements = listOf(
    """
    CREATE TABLE medication_intake_records (
        id TEXT NOT NULL,
        medicationPlanId TEXT NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,
        createdByActorId TEXT NOT NULL,
        currentRevisionNumber INTEGER NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(medicationPlanId) REFERENCES medication_plans(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(createdByActorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    "CREATE INDEX index_medication_intake_records_medicationPlanId ON medication_intake_records (medicationPlanId)",
    "CREATE INDEX index_medication_intake_records_createdByActorId ON medication_intake_records (createdByActorId)",
    """
    CREATE TABLE medication_intake_revisions (
        id TEXT NOT NULL,
        intakeRecordId TEXT NOT NULL,
        revisionNumber INTEGER NOT NULL,
        outcome TEXT NOT NULL,
        scheduledEpochDay INTEGER NOT NULL,
        scheduledMinute INTEGER NOT NULL,
        actualEpochDay INTEGER,
        actualMinute INTEGER,
        actualAmountValue TEXT,
        actualAmountUnit TEXT,
        observation TEXT,
        changeKind TEXT NOT NULL,
        changeReason TEXT,
        capturedAtEpochMillis INTEGER NOT NULL,
        authorActorId TEXT NOT NULL,
        PRIMARY KEY(id),
        FOREIGN KEY(intakeRecordId) REFERENCES medication_intake_records(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(authorActorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    "CREATE INDEX index_medication_intake_revisions_intakeRecordId ON medication_intake_revisions (intakeRecordId)",
    "CREATE INDEX index_medication_intake_revisions_authorActorId ON medication_intake_revisions (authorActorId)",
    "CREATE UNIQUE INDEX index_medication_intake_revisions_intakeRecordId_revisionNumber ON medication_intake_revisions (intakeRecordId, revisionNumber)",
)
