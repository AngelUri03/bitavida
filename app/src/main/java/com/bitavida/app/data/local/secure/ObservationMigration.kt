package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive only: the existing 18 schemas and all historical rows remain intact. */
internal val ObservationMigration18To19 = object : Migration(18, 19) {
    override suspend fun migrate(connection: SQLiteConnection) {
        ObservationSchemaStatements.forEach(connection::execSQL)
    }
}

internal val ObservationSchemaStatements = listOf(
    "CREATE TABLE IF NOT EXISTS observation_follow_ups (id TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, caregiverId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, observationId TEXT NOT NULL, createdAt TEXT NOT NULL, currentRevisionNumber INTEGER NOT NULL, FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(caregiverId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE TABLE IF NOT EXISTS observation_follow_up_revisions (id TEXT NOT NULL PRIMARY KEY, followUpId TEXT NOT NULL, revisionNumber INTEGER NOT NULL, actorId TEXT NOT NULL, capturedAt TEXT NOT NULL, payload TEXT NOT NULL, FOREIGN KEY(followUpId) REFERENCES observation_follow_ups(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(actorId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE TABLE IF NOT EXISTS observation_records (id TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, caregiverId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, observationId TEXT NOT NULL, occurredOnEpochDay INTEGER NOT NULL, followUpId TEXT, occurrenceId TEXT, createdAt TEXT NOT NULL, payload TEXT NOT NULL, FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(caregiverId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(followUpId) REFERENCES observation_follow_ups(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE TABLE IF NOT EXISTS observation_photos (id TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, jpeg BLOB NOT NULL, FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE TABLE IF NOT EXISTS observation_record_photos (recordId TEXT NOT NULL, photoId TEXT NOT NULL, PRIMARY KEY(recordId, photoId), FOREIGN KEY(recordId) REFERENCES observation_records(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(photoId) REFERENCES observation_photos(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE INDEX IF NOT EXISTS index_observation_follow_ups_profileId ON observation_follow_ups(profileId)",
    "CREATE INDEX IF NOT EXISTS index_observation_follow_ups_caregiverId ON observation_follow_ups(caregiverId)",
    "CREATE INDEX IF NOT EXISTS index_observation_follow_ups_personUnderCareId ON observation_follow_ups(personUnderCareId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_observation_follow_ups_personUnderCareId_observationId ON observation_follow_ups(personUnderCareId, observationId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_observation_follow_up_revisions_followUpId_revisionNumber ON observation_follow_up_revisions(followUpId, revisionNumber)",
    "CREATE INDEX IF NOT EXISTS index_observation_follow_up_revisions_actorId ON observation_follow_up_revisions(actorId)",
    "CREATE INDEX IF NOT EXISTS index_observation_records_profileId ON observation_records(profileId)",
    "CREATE INDEX IF NOT EXISTS index_observation_records_caregiverId ON observation_records(caregiverId)",
    "CREATE INDEX IF NOT EXISTS index_observation_records_personUnderCareId ON observation_records(personUnderCareId)",
    "CREATE INDEX IF NOT EXISTS index_observation_records_followUpId ON observation_records(followUpId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_observation_records_personUnderCareId_occurrenceId ON observation_records(personUnderCareId, occurrenceId)",
    "CREATE INDEX IF NOT EXISTS index_observation_photos_profileId ON observation_photos(profileId)",
    "CREATE INDEX IF NOT EXISTS index_observation_photos_personUnderCareId ON observation_photos(personUnderCareId)",
    "CREATE INDEX IF NOT EXISTS index_observation_record_photos_photoId ON observation_record_photos(photoId)",
)
