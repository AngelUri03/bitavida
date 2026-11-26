package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds an explicit per-administration reminder choice without changing legacy rows.
 * A null value retains the previous behavior for already stored exact times.
 */
internal val MedicationReminderParticipationMigration4To5 =
    object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE medication_administrations " +
                    "ADD COLUMN reminderEnabled INTEGER",
            )
        }
    }
