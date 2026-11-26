package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MedicationOccurrenceLinkMigration6To7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE medication_intake_revisions ADD COLUMN scheduledOccurrenceId TEXT DEFAULT NULL",
        )
    }
}
