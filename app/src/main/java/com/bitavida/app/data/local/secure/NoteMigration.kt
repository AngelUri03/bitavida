package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive upgrade. The source indication tables and every medical revision remain untouched. */
internal val NoteMigration21To22 = object : Migration(21, 22) {
    override suspend fun migrate(connection: SQLiteConnection) {
        NoteSchemaStatements.forEach(connection::execSQL)
        NoteLegacyImportStatements.forEach(connection::execSQL)
    }
}

internal val NoteSchemaStatements = listOf(
    "CREATE TABLE IF NOT EXISTS note_images (id TEXT NOT NULL, profileId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, jpeg BLOB NOT NULL, importedAt TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE INDEX IF NOT EXISTS index_note_images_profileId ON note_images(profileId)",
    "CREATE INDEX IF NOT EXISTS index_note_images_personUnderCareId ON note_images(personUnderCareId)",
    "CREATE TABLE IF NOT EXISTS notes (id TEXT NOT NULL, profileId TEXT NOT NULL, caregiverId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, title TEXT NOT NULL, content TEXT, imageId TEXT, createdAt TEXT NOT NULL, modifiedAt TEXT NOT NULL, revision INTEGER NOT NULL, deletedAt TEXT, legacyIndicationId TEXT, PRIMARY KEY(id), FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(caregiverId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(imageId) REFERENCES note_images(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE INDEX IF NOT EXISTS index_notes_profileId ON notes(profileId)",
    "CREATE INDEX IF NOT EXISTS index_notes_caregiverId ON notes(caregiverId)",
    "CREATE INDEX IF NOT EXISTS index_notes_personUnderCareId ON notes(personUnderCareId)",
    "CREATE INDEX IF NOT EXISTS index_notes_imageId ON notes(imageId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_notes_profileId_legacyIndicationId ON notes(profileId, legacyIndicationId)",
    "CREATE TABLE IF NOT EXISTS note_revisions (operationId TEXT NOT NULL, noteId TEXT NOT NULL, actorId TEXT NOT NULL, changeKind TEXT NOT NULL, requestFingerprint TEXT NOT NULL, snapshot_id TEXT NOT NULL, snapshot_profileId TEXT NOT NULL, snapshot_caregiverId TEXT NOT NULL, snapshot_personUnderCareId TEXT NOT NULL, snapshot_title TEXT NOT NULL, snapshot_content TEXT, snapshot_imageId TEXT, snapshot_createdAt TEXT NOT NULL, snapshot_modifiedAt TEXT NOT NULL, snapshot_revision INTEGER NOT NULL, snapshot_deletedAt TEXT, snapshot_legacyIndicationId TEXT, PRIMARY KEY(operationId), FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(actorId) REFERENCES caregivers(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_note_revisions_noteId_snapshot_revision ON note_revisions(noteId, snapshot_revision)",
    "CREATE INDEX IF NOT EXISTS index_note_revisions_actorId ON note_revisions(actorId)",
    "CREATE INDEX IF NOT EXISTS index_note_revisions_snapshot_imageId ON note_revisions(snapshot_imageId)",
)

/** One-time copies of everything previously visible under Otras indicaciones; future clinical entries are not imported. */
internal val NoteLegacyImportStatements = listOf(
    """
    INSERT INTO notes (id, profileId, caregiverId, personUnderCareId, title, content, imageId, createdAt, modifiedAt, revision, deletedAt, legacyIndicationId)
    SELECT 'legacy-indication:' || relationship.profileId || ':' || indication.id,
        relationship.profileId, relationship.caregiverId, indication.personUnderCareId,
        CASE WHEN length(trim(current.originalText)) = 0 THEN 'Nota conservada'
            ELSE substr(trim(replace(replace(current.originalText, char(13), ' '), char(10), ' ')), 1, 120) END,
        current.originalText || char(10) || char(10) ||
        'Información conservada del registro anterior.' || char(10) ||
        'Procedencia: Indicación médica transcrita.' || char(10) ||
        'Fecha indicada: ' || date(current.indicatedOnEpochDay * 86400, 'unixepoch') || char(10) ||
        'Estado de origen: ' || CASE current.status
            WHEN 'ACTIVE' THEN 'Vigente' WHEN 'SUSPENDED' THEN 'Suspendida'
            WHEN 'COMPLETED' THEN 'Completada' ELSE current.status END ||
        CASE WHEN current.declaredProfessionalOrSource IS NULL THEN ''
            ELSE char(10) || 'Fuente declarada: ' || current.declaredProfessionalOrSource END ||
        CASE WHEN current.validFromEpochDay IS NULL THEN ''
            ELSE char(10) || 'Vigente desde: ' || date(current.validFromEpochDay * 86400, 'unixepoch') END ||
        CASE WHEN current.validUntilEpochDay IS NULL THEN ''
            ELSE char(10) || 'Vigente hasta: ' || date(current.validUntilEpochDay * 86400, 'unixepoch') END ||
        CASE WHEN current.changeReason IS NULL THEN ''
            ELSE char(10) || 'Motivo del último cambio: ' || current.changeReason END ||
        char(10) || 'Medicamentos vinculados en el registro de origen: ' ||
            (SELECT COUNT(*) FROM medication_plans plan WHERE plan.medicalIndicationId = indication.id),
        NULL,
        strftime('%Y-%m-%dT%H:%M:%f', indication.createdAtEpochMillis / 1000.0, 'unixepoch', 'localtime'),
        strftime('%Y-%m-%dT%H:%M:%f', max(indication.createdAtEpochMillis, current.capturedAtEpochMillis) / 1000.0, 'unixepoch', 'localtime'),
        1, NULL, indication.id
    FROM medical_indications indication
    JOIN medical_indication_revisions current ON current.indicationId = indication.id
        AND current.revisionNumber = indication.currentRevisionNumber
    JOIN care_relationships relationship ON relationship.personUnderCareId = indication.personUnderCareId
    """.trimIndent(),
    """
    INSERT INTO note_revisions (operationId, noteId, actorId, changeKind, requestFingerprint, snapshot_id, snapshot_profileId, snapshot_caregiverId, snapshot_personUnderCareId, snapshot_title, snapshot_content, snapshot_imageId, snapshot_createdAt, snapshot_modifiedAt, snapshot_revision, snapshot_deletedAt, snapshot_legacyIndicationId)
    SELECT 'legacy-note-import:' || id, id, caregiverId, 'IMPORTED', 'legacy-note-import:' || id,
        id, profileId, caregiverId, personUnderCareId, title, content, imageId, createdAt, modifiedAt, revision, deletedAt, legacyIndicationId
    FROM notes WHERE legacyIndicationId IS NOT NULL
    """.trimIndent(),
)
