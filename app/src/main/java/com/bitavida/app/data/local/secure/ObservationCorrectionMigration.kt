package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** No existing fact, photograph or scheduled event is rewritten. */
internal val ObservationCorrectionMigration19To20 = object : Migration(19, 20) {
    override suspend fun migrate(connection: SQLiteConnection) {
        ObservationCorrectionSchemaStatements.forEach(connection::execSQL)
    }
}

internal val ObservationCorrectionSchemaStatements = listOf(
    "CREATE TABLE IF NOT EXISTS observation_record_revisions (id TEXT NOT NULL PRIMARY KEY, recordId TEXT NOT NULL, revisionNumber INTEGER NOT NULL, actorId TEXT NOT NULL, correctedAt TEXT NOT NULL, reason TEXT NOT NULL, payload TEXT NOT NULL, FOREIGN KEY(recordId) REFERENCES observation_records(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(actorId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_observation_record_revisions_recordId_revisionNumber ON observation_record_revisions(recordId, revisionNumber)",
    "CREATE INDEX IF NOT EXISTS index_observation_record_revisions_actorId ON observation_record_revisions(actorId)",
)
