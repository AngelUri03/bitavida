package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val GlucoseOccurrenceDecisionRevisionMigration14To15 =
    object : Migration(14, 15) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS glucose_occurrence_decision_revisions (
                    id TEXT NOT NULL PRIMARY KEY,
                    decisionId TEXT NOT NULL,
                    occurrenceId TEXT NOT NULL,
                    revisionNumber INTEGER NOT NULL,
                    operation TEXT NOT NULL,
                    reason TEXT,
                    capturedAt TEXT NOT NULL,
                    actorId TEXT NOT NULL,
                    FOREIGN KEY(decisionId)
                        REFERENCES glucose_occurrence_decisions(id)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_glucose_occurrence_decision_revisions_decisionId
                ON glucose_occurrence_decision_revisions(decisionId)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                index_glucose_occurrence_decision_revisions_occurrenceId_revisionNumber
                ON glucose_occurrence_decision_revisions(
                    occurrenceId,
                    revisionNumber
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT OR IGNORE INTO glucose_occurrence_decision_revisions (
                    id,
                    decisionId,
                    occurrenceId,
                    revisionNumber,
                    operation,
                    reason,
                    capturedAt,
                    actorId
                )
                SELECT
                    id || ':revision:1',
                    id,
                    occurrenceId,
                    1,
                    'CONFIRMED',
                    reason,
                    capturedAt,
                    actorId
                FROM glucose_occurrence_decisions
                """.trimIndent(),
            )
        }
    }
