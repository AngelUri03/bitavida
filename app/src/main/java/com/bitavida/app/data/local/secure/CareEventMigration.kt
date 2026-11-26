package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive only; existing protected records are preserved. */
internal val CareEventMigration22To23 = object : Migration(22, 23) {
    override suspend fun migrate(connection: SQLiteConnection) {
        CareEventSchemaStatements.forEach(connection::execSQL)
    }
}

internal val CareEventReminderMigration25To26 = object : Migration(25, 26) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE care_events ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE care_event_revisions ADD COLUMN snapshot_reminderEnabled INTEGER NOT NULL DEFAULT 0")
    }
}

internal val CareEventDetailsMigration27To28 = object : Migration(27, 28) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for ((table, prefix) in listOf("care_events" to "", "care_event_revisions" to "snapshot_")) {
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}category TEXT NOT NULL DEFAULT 'GENERAL'")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}specialty TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}institution TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}office TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}reminderMinutesBefore INTEGER NOT NULL DEFAULT 60")
        }
    }
}

/** Additive fields are present in both the live row and every immutable snapshot. */
internal val CareEventWizardMigration28To29 = object : Migration(28, 29) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for ((table, prefix) in listOf("care_events" to "", "care_event_revisions" to "snapshot_")) {
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}purpose TEXT NOT NULL DEFAULT 'LEGACY'")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}timeMode TEXT NOT NULL DEFAULT 'LEGACY'")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}duration TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}modality TEXT NOT NULL DEFAULT 'IN_PERSON'")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}professional TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}reference TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}contactInformation TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}otherPreparation TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}preparations TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}reminderOffsetsMinutes TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}showInToday INTEGER NOT NULL DEFAULT 1")
        }
    }
}

/** Preserve the chosen catalog identity in the current event and every historical revision. */
internal val CareEventCatalogMigration29To30 = object : Migration(29, 30) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE care_events ADD COLUMN verifiedUnitId TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE care_event_revisions ADD COLUMN snapshot_verifiedUnitId TEXT NOT NULL DEFAULT ''")
    }
}

/** Preserve all old classifications unchanged; ambiguous visits require explicit clarification. */
internal val CareEventConsultationMigration32To33 = object : Migration(32, 33) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for ((table, prefix) in listOf("care_events" to "", "care_event_revisions" to "snapshot_")) {
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}consultationType TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
            connection.execSQL("ALTER TABLE $table ADD COLUMN ${prefix}isFollowUp INTEGER NOT NULL DEFAULT 0")
        }
    }
}

internal val CareEventSchemaStatements = listOf(
    "CREATE TABLE IF NOT EXISTS care_events (id TEXT NOT NULL, profileId TEXT NOT NULL, caregiverId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, transcribedIndication TEXT NOT NULL, suggestedDate TEXT, scheduledAt TEXT, scheduledDate TEXT, location TEXT NOT NULL, instructions TEXT NOT NULL, caregiverNotes TEXT NOT NULL, status TEXT NOT NULL, changeReason TEXT NOT NULL, createdAt TEXT NOT NULL, modifiedAt TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(caregiverId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE TABLE IF NOT EXISTS care_event_revisions (operationId TEXT NOT NULL, eventId TEXT NOT NULL, actorId TEXT NOT NULL, changeKind TEXT NOT NULL, requestFingerprint TEXT NOT NULL, snapshot_id TEXT NOT NULL, snapshot_profileId TEXT NOT NULL, snapshot_caregiverId TEXT NOT NULL, snapshot_personUnderCareId TEXT NOT NULL, snapshot_kind TEXT NOT NULL, snapshot_title TEXT NOT NULL, snapshot_transcribedIndication TEXT NOT NULL, snapshot_suggestedDate TEXT, snapshot_scheduledAt TEXT, snapshot_scheduledDate TEXT, snapshot_location TEXT NOT NULL, snapshot_instructions TEXT NOT NULL, snapshot_caregiverNotes TEXT NOT NULL, snapshot_status TEXT NOT NULL, snapshot_changeReason TEXT NOT NULL, snapshot_createdAt TEXT NOT NULL, snapshot_modifiedAt TEXT NOT NULL, snapshot_revision INTEGER NOT NULL, PRIMARY KEY(operationId), FOREIGN KEY(eventId) REFERENCES care_events(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(actorId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE INDEX IF NOT EXISTS index_care_events_profileId ON care_events(profileId)",
    "CREATE INDEX IF NOT EXISTS index_care_events_caregiverId ON care_events(caregiverId)",
    "CREATE INDEX IF NOT EXISTS index_care_events_personUnderCareId ON care_events(personUnderCareId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_care_event_revisions_eventId_snapshot_revision ON care_event_revisions(eventId, snapshot_revision)",
    "CREATE INDEX IF NOT EXISTS index_care_event_revisions_actorId ON care_event_revisions(actorId)",
)
