package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val GlucoseRevisionMigration11To12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE glucose_measurements ADD COLUMN deletedAt TEXT",
        )
        connection.execSQL(
            "ALTER TABLE glucose_measurements ADD COLUMN deletedBy TEXT",
        )
        connection.execSQL("DROP INDEX IF EXISTS index_glucose_audit_recordId")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_glucose_audit_recordId " +
                "ON glucose_audit(recordId)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS glucose_revisions (
                id TEXT NOT NULL PRIMARY KEY,
                recordId TEXT NOT NULL,
                revisionNumber INTEGER NOT NULL,
                actorId TEXT NOT NULL,
                operation TEXT NOT NULL,
                reading TEXT NOT NULL,
                actualContext TEXT NOT NULL,
                actualAt TEXT NOT NULL,
                note TEXT,
                capturedAt TEXT NOT NULL,
                FOREIGN KEY(recordId) REFERENCES glucose_measurements(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(actorId) REFERENCES caregivers(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                index_glucose_revisions_recordId_revisionNumber
            ON glucose_revisions(recordId, revisionNumber)
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_glucose_revisions_actorId
            ON glucose_revisions(actorId)
            """.trimIndent(),
        )
        connection.execSQL(
            """
            INSERT OR IGNORE INTO glucose_revisions (
                id,
                recordId,
                revisionNumber,
                actorId,
                operation,
                reading,
                actualContext,
                actualAt,
                note,
                capturedAt
            )
            SELECT
                id || ':revision:1',
                id,
                1,
                caregiverId,
                'CREATED',
                reading,
                actualContext,
                actualAt,
                note,
                capturedAt
            FROM glucose_measurements
            """.trimIndent(),
        )
    }
}
