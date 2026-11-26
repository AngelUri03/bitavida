package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Preserves every existing glucose slot as an exact-time schedule while adding
 * the explicit window metadata required by the shared administration editor.
 */
object GlucoseFollowUpWindowMigration13To14 : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE glucose_follow_up_slots " +
                "ADD COLUMN timingType TEXT NOT NULL DEFAULT 'EXACT_TIME'",
        )
        connection.execSQL(
            "ALTER TABLE glucose_follow_up_slots " +
                "ADD COLUMN windowEndMinute INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE glucose_follow_up_slots " +
                "ADD COLUMN endsNextDay INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            "ALTER TABLE glucose_follow_up_slots " +
                "ADD COLUMN reminderNextDay INTEGER NOT NULL DEFAULT 0",
        )
    }
}
