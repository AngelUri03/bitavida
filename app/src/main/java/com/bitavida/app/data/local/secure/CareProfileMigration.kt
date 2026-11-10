package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val CareProfileMigration1To2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS caregivers (
                id TEXT NOT NULL,
                displayName TEXT NOT NULL,
                declarationSource TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS persons_under_care (
                id TEXT NOT NULL,
                displayName TEXT NOT NULL,
                declaredByCaregiverId TEXT NOT NULL,
                declarationSource TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(declaredByCaregiverId) REFERENCES caregivers(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_persons_under_care_declaredByCaregiverId " +
                "ON persons_under_care (declaredByCaregiverId)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS care_relationships (
                profileId TEXT NOT NULL,
                caregiverId TEXT NOT NULL,
                personUnderCareId TEXT NOT NULL,
                usage TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(profileId),
                FOREIGN KEY(caregiverId) REFERENCES caregivers(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_care_relationships_caregiverId " +
                "ON care_relationships (caregiverId)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_care_relationships_personUnderCareId " +
                "ON care_relationships (personUnderCareId)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS consent_records (
                id TEXT NOT NULL,
                profileId TEXT NOT NULL,
                caregiverId TEXT NOT NULL,
                personUnderCareId TEXT NOT NULL,
                kind TEXT NOT NULL,
                authorizationDeclaration TEXT NOT NULL,
                status TEXT NOT NULL,
                recordedAtEpochMillis INTEGER NOT NULL,
                revokedAtEpochMillis INTEGER,
                PRIMARY KEY(id),
                FOREIGN KEY(profileId) REFERENCES care_relationships(profileId)
                    ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(caregiverId) REFERENCES caregivers(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_consent_records_profileId " +
                "ON consent_records (profileId)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_consent_records_caregiverId " +
                "ON consent_records (caregiverId)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_consent_records_personUnderCareId " +
                "ON consent_records (personUnderCareId)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS legal_acceptance_records (
                id TEXT NOT NULL,
                profileId TEXT NOT NULL,
                actorId TEXT NOT NULL,
                document TEXT NOT NULL,
                documentVersion TEXT NOT NULL,
                decision TEXT NOT NULL,
                status TEXT NOT NULL,
                recordedAtEpochMillis INTEGER NOT NULL,
                revokedAtEpochMillis INTEGER,
                PRIMARY KEY(id),
                FOREIGN KEY(profileId) REFERENCES care_relationships(profileId)
                    ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(actorId) REFERENCES caregivers(id)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_legal_acceptance_records_profileId " +
                "ON legal_acceptance_records (profileId)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_legal_acceptance_records_actorId " +
                "ON legal_acceptance_records (actorId)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "index_legal_acceptance_records_document_documentVersion " +
                "ON legal_acceptance_records (document, documentVersion)",
        )
    }
}
