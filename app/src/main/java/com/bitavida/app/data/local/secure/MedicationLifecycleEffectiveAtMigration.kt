package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MedicationLifecycleEffectiveAtMigration7To8 =
    object : Migration(7, 8) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE medication_plan_revisions " +
                    "ADD COLUMN lifecycleEffectiveAtEpochMillis INTEGER DEFAULT NULL",
            )
        }
    }
