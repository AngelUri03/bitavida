package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Separates the functional administration identity from the versioned row key.
 * Existing rows keep using their primary key as the functional fallback.
 */
internal val MedicationAdministrationLogicalIdMigration8To9 =
    object : Migration(8, 9) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE medication_administrations " +
                    "ADD COLUMN logicalId TEXT DEFAULT NULL",
            )
        }
    }
