package com.bitavida.app.data.local.secure

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val WeightTargetMigration17To18 =
    object : Migration(17, 18) {
        override suspend fun migrate(connection: SQLiteConnection) {
            statements.forEach(connection::execSQL)
        }
    }

private val statements = listOf(
    """
    CREATE TABLE IF NOT EXISTS weight_targets (
        personUnderCareId TEXT NOT NULL PRIMARY KEY,
        profileId TEXT NOT NULL,
        caregiverId TEXT NOT NULL,
        kilograms TEXT NOT NULL,
        FOREIGN KEY(profileId) REFERENCES care_relationships(profileId)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(caregiverId) REFERENCES caregivers(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION,
        FOREIGN KEY(personUnderCareId) REFERENCES persons_under_care(id)
            ON UPDATE NO ACTION ON DELETE NO ACTION
    )
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS index_weight_targets_profileId
    ON weight_targets(profileId)
    """.trimIndent(),
    """
    CREATE INDEX IF NOT EXISTS index_weight_targets_caregiverId
    ON weight_targets(caregiverId)
    """.trimIndent(),
)
