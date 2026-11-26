package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val WeightMigration16To17 =
    object : Migration(16, 17) {
        override suspend fun migrate(connection: SQLiteConnection) {
            statements.forEach(connection::execSQL)
        }
    }

private val statements = listOf(
    """
    CREATE TABLE IF NOT EXISTS weight_measurements (
        id TEXT NOT NULL PRIMARY KEY,
        profileId TEXT NOT NULL,
        caregiverId TEXT NOT NULL,
        personUnderCareId TEXT NOT NULL,
        kilograms TEXT NOT NULL,
        heightMeters TEXT,
        actualContext TEXT NOT NULL,
        actualAt TEXT NOT NULL,
        capturedAt TEXT NOT NULL,
        origin TEXT NOT NULL,
        entryKind TEXT NOT NULL,
        followUpId TEXT,
        scheduledOccurrenceId TEXT,
        scheduledAt TEXT,
        expectedContext TEXT,
        note TEXT,
        deletedAt TEXT,
        deletedBy TEXT,
        FOREIGN KEY(profileId) REFERENCES care_relationships(profileId)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(caregiverId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS index_weight_measurements_profileId
    ON weight_measurements(profileId)
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS index_weight_measurements_caregiverId
    ON weight_measurements(caregiverId)
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS
    index_weight_measurements_personUnderCareId
    ON weight_measurements(personUnderCareId)
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    index_weight_measurements_personUnderCareId_scheduledOccurrenceId
    ON weight_measurements(personUnderCareId, scheduledOccurrenceId)
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS weight_audit (
        id TEXT NOT NULL PRIMARY KEY,
        recordId TEXT NOT NULL,
        actorId TEXT NOT NULL,
        operation TEXT NOT NULL,
        capturedAt TEXT NOT NULL,
        FOREIGN KEY(recordId) REFERENCES weight_measurements(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(actorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS index_weight_audit_recordId
    ON weight_audit(recordId)
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS index_weight_audit_actorId
    ON weight_audit(actorId)
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS weight_revisions (
        id TEXT NOT NULL PRIMARY KEY,
        recordId TEXT NOT NULL,
        revisionNumber INTEGER NOT NULL,
        actorId TEXT NOT NULL,
        operation TEXT NOT NULL,
        kilograms TEXT NOT NULL,
        heightMeters TEXT,
        actualContext TEXT NOT NULL,
        actualAt TEXT NOT NULL,
        note TEXT,
        capturedAt TEXT NOT NULL,
        FOREIGN KEY(recordId) REFERENCES weight_measurements(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(actorId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    index_weight_revisions_recordId_revisionNumber
    ON weight_revisions(recordId, revisionNumber)
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS index_weight_revisions_actorId
    ON weight_revisions(actorId)
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS weight_follow_ups (
        id TEXT NOT NULL PRIMARY KEY,
        profileId TEXT NOT NULL,
        caregiverId TEXT NOT NULL,
        personUnderCareId TEXT NOT NULL,
        createdAt TEXT NOT NULL,
        createdByActorId TEXT NOT NULL,
        currentRevisionNumber INTEGER NOT NULL
    )
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    index_weight_follow_ups_personUnderCareId
    ON weight_follow_ups(personUnderCareId)
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS weight_follow_up_revisions (
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
        FOREIGN KEY(followUpId) REFERENCES weight_follow_ups(id)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    index_weight_follow_up_revisions_followUpId_revisionNumber
    ON weight_follow_up_revisions(followUpId, revisionNumber)
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS weight_follow_up_slots (
        id TEXT NOT NULL PRIMARY KEY,
        logicalId TEXT NOT NULL,
        followUpRevisionId TEXT NOT NULL,
        localTimeMinute INTEGER NOT NULL,
        timingType TEXT NOT NULL,
        windowEndMinute INTEGER,
        endsNextDay INTEGER NOT NULL,
        suggestedContext TEXT NOT NULL,
        reminderEnabled INTEGER NOT NULL,
        reminderMinute INTEGER,
        reminderNextDay INTEGER NOT NULL,
        FOREIGN KEY(followUpRevisionId)
            REFERENCES weight_follow_up_revisions(id)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS
    index_weight_follow_up_slots_followUpRevisionId
    ON weight_follow_up_slots(followUpRevisionId)
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    index_weight_follow_up_slots_followUpRevisionId_localTimeMinute
    ON weight_follow_up_slots(followUpRevisionId, localTimeMinute)
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS weight_occurrence_decisions (
        id TEXT NOT NULL PRIMARY KEY,
        followUpId TEXT NOT NULL,
        followUpRevisionId TEXT NOT NULL,
        occurrenceId TEXT NOT NULL,
        scheduledAt TEXT NOT NULL,
        kind TEXT NOT NULL,
        reason TEXT,
        capturedAt TEXT NOT NULL,
        actorId TEXT NOT NULL,
        FOREIGN KEY(followUpId) REFERENCES weight_follow_ups(id)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS
    index_weight_occurrence_decisions_followUpId
    ON weight_occurrence_decisions(followUpId)
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    index_weight_occurrence_decisions_occurrenceId
    ON weight_occurrence_decisions(occurrenceId)
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS
    weight_occurrence_decision_revisions (
        id TEXT NOT NULL PRIMARY KEY,
        decisionId TEXT NOT NULL,
        occurrenceId TEXT NOT NULL,
        revisionNumber INTEGER NOT NULL,
        operation TEXT NOT NULL,
        reason TEXT,
        capturedAt TEXT NOT NULL,
        actorId TEXT NOT NULL,
        FOREIGN KEY(decisionId)
            REFERENCES weight_occurrence_decisions(id)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS
    index_weight_occurrence_decision_revisions_decisionId
    ON weight_occurrence_decision_revisions(decisionId)
    """.trimIndent(),
    """
    CREATE UNIQUE INDEX IF NOT EXISTS
    index_weight_occurrence_decision_revisions_occurrenceId_revisionNumber
    ON weight_occurrence_decision_revisions(
        occurrenceId,
        revisionNumber
    )
    """.trimIndent(),
)
