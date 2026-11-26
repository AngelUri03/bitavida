package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Links independent treatment periods without changing existing plan identities.
 * A null value in an older row is read as that plan's own series identifier.
 */
internal val MedicationPeriodSeriesMigration9To10 =
    object : Migration(9, 10) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE medication_plans " +
                    "ADD COLUMN medicationSeriesId TEXT DEFAULT NULL",
            )
        }
    }
