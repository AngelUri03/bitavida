package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val GlucoseMigration10To11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS glucose_measurements (
                id TEXT NOT NULL PRIMARY KEY,
                profileId TEXT NOT NULL,
                caregiverId TEXT NOT NULL,
                personUnderCareId TEXT NOT NULL,
                reading TEXT NOT NULL,
                actualContext TEXT NOT NULL,
                actualAt TEXT NOT NULL,
                capturedAt TEXT NOT NULL,
                origin TEXT NOT NULL,
                entryKind TEXT NOT NULL,
                followUpId TEXT,
                scheduledOccurrenceId TEXT,
                scheduledAt TEXT,
                expectedContext TEXT,
                note TEXT,
                FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(caregiverId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_glucose_measurements_profileId ON glucose_measurements(profileId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_glucose_measurements_caregiverId ON glucose_measurements(caregiverId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_glucose_measurements_personUnderCareId ON glucose_measurements(personUnderCareId)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_glucose_measurements_personUnderCareId_scheduledOccurrenceId ON glucose_measurements(personUnderCareId, scheduledOccurrenceId)")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS glucose_audit (
                id TEXT NOT NULL PRIMARY KEY,
                recordId TEXT NOT NULL,
                actorId TEXT NOT NULL,
                operation TEXT NOT NULL,
                capturedAt TEXT NOT NULL,
                FOREIGN KEY(recordId) REFERENCES glucose_measurements(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(actorId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_glucose_audit_recordId ON glucose_audit(recordId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_glucose_audit_actorId ON glucose_audit(actorId)")
    }
}
