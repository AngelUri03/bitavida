package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive: no existing tables, rows, clinical records or revision histories are modified. */
internal val CareReminderMigration20To21 = object : Migration(20, 21) {
    override suspend fun migrate(connection: SQLiteConnection) {
        CareReminderSchemaStatements.forEach(connection::execSQL)
    }
}

internal val CareReminderSchemaStatements = listOf(
    "CREATE TABLE IF NOT EXISTS care_reminders (id TEXT NOT NULL, profileId TEXT NOT NULL, caregiverId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, title TEXT NOT NULL, details TEXT, scheduledAt TEXT NOT NULL, notificationsEnabled INTEGER NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL, modifiedAt TEXT NOT NULL, closedAt TEXT, revision INTEGER NOT NULL, originallyScheduledAt TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(caregiverId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE TABLE IF NOT EXISTS care_reminder_revisions (operationId TEXT NOT NULL, reminderId TEXT NOT NULL, actorId TEXT NOT NULL, changeKind TEXT NOT NULL, requestFingerprint TEXT NOT NULL, snapshot_id TEXT NOT NULL, snapshot_profileId TEXT NOT NULL, snapshot_caregiverId TEXT NOT NULL, snapshot_personUnderCareId TEXT NOT NULL, snapshot_title TEXT NOT NULL, snapshot_details TEXT, snapshot_scheduledAt TEXT NOT NULL, snapshot_notificationsEnabled INTEGER NOT NULL, snapshot_status TEXT NOT NULL, snapshot_createdAt TEXT NOT NULL, snapshot_modifiedAt TEXT NOT NULL, snapshot_closedAt TEXT, snapshot_revision INTEGER NOT NULL, snapshot_originallyScheduledAt TEXT NOT NULL, PRIMARY KEY(operationId), FOREIGN KEY(reminderId) REFERENCES care_reminders(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(actorId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE INDEX IF NOT EXISTS index_care_reminders_profileId ON care_reminders(profileId)",
    "CREATE INDEX IF NOT EXISTS index_care_reminders_caregiverId ON care_reminders(caregiverId)",
    "CREATE INDEX IF NOT EXISTS index_care_reminders_personUnderCareId ON care_reminders(personUnderCareId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_care_reminder_revisions_reminderId_snapshot_revision ON care_reminder_revisions(reminderId, snapshot_revision)",
    "CREATE INDEX IF NOT EXISTS index_care_reminder_revisions_actorId ON care_reminder_revisions(actorId)",
)
