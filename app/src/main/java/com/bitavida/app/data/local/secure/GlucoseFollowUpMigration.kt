package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object GlucoseFollowUpMigration12To13 : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS glucose_follow_ups (
                id TEXT NOT NULL PRIMARY KEY,
                profileId TEXT NOT NULL,
                caregiverId TEXT NOT NULL,
                personUnderCareId TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                createdByActorId TEXT NOT NULL,
                currentRevisionNumber INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_glucose_follow_ups_personUnderCareId " +
                "ON glucose_follow_ups(personUnderCareId)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS glucose_follow_up_revisions (
                id TEXT NOT NULL PRIMARY KEY,
                followUpId TEXT NOT NULL,
                revisionNumber INTEGER NOT NULL,
                indication TEXT NOT NULL,
                frequency TEXT NOT NULL,
                weekdaysMask INTEGER NOT NULL,
                everyNDaysInterval INTEGER,
                everyNDaysReferenceEpochDay INTEGER,
                startsOnEpochDay INTEGER NOT NULL,
                endsOnEpochDay INTEGER,
                effectiveFromEpochDay INTEGER NOT NULL,
                remindersEnabled INTEGER NOT NULL,
                changeKind TEXT NOT NULL,
                changeReason TEXT,
                capturedAt TEXT NOT NULL,
                authorActorId TEXT NOT NULL,
                FOREIGN KEY(followUpId) REFERENCES glucose_follow_ups(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_glucose_follow_up_revisions_followUpId_revisionNumber " +
                "ON glucose_follow_up_revisions(followUpId, revisionNumber)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS glucose_follow_up_slots (
                id TEXT NOT NULL PRIMARY KEY,
                logicalId TEXT NOT NULL,
                followUpRevisionId TEXT NOT NULL,
                localTimeMinute INTEGER NOT NULL,
                suggestedContext TEXT NOT NULL,
                reminderEnabled INTEGER NOT NULL,
                reminderMinute INTEGER,
                FOREIGN KEY(followUpRevisionId)
                    REFERENCES glucose_follow_up_revisions(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_glucose_follow_up_slots_followUpRevisionId " +
                "ON glucose_follow_up_slots(followUpRevisionId)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_glucose_follow_up_slots_followUpRevisionId_localTimeMinute " +
                "ON glucose_follow_up_slots(followUpRevisionId, localTimeMinute)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS glucose_occurrence_decisions (
                id TEXT NOT NULL PRIMARY KEY,
                followUpId TEXT NOT NULL,
                followUpRevisionId TEXT NOT NULL,
                occurrenceId TEXT NOT NULL,
                scheduledAt TEXT NOT NULL,
                kind TEXT NOT NULL,
                reason TEXT,
                capturedAt TEXT NOT NULL,
                actorId TEXT NOT NULL,
                FOREIGN KEY(followUpId) REFERENCES glucose_follow_ups(id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_glucose_occurrence_decisions_followUpId " +
                "ON glucose_occurrence_decisions(followUpId)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_glucose_occurrence_decisions_occurrenceId " +
                "ON glucose_occurrence_decisions(occurrenceId)",
        )
    }
}
