package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Entity(tableName = "care_profile_name_revisions",
    foreignKeys = [ForeignKey(entity = CareRelationshipEntity::class, parentColumns = ["profileId"], childColumns = ["profileId"])],
    indices = [Index(value = ["profileId"])])
data class CareProfileNameRevisionEntity(
    @PrimaryKey val operationId: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val previousCaregiverName: String,
    val previousPersonName: String,
    val caregiverName: String,
    val personName: String,
    val capturedAtEpochMillis: Long,
)

internal val CareProfileNameMigration24To25 = object : Migration(24, 25) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS care_profile_name_revisions (operationId TEXT NOT NULL, profileId TEXT NOT NULL, caregiverId TEXT NOT NULL, personUnderCareId TEXT NOT NULL, previousCaregiverName TEXT NOT NULL, previousPersonName TEXT NOT NULL, caregiverName TEXT NOT NULL, personName TEXT NOT NULL, capturedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(operationId), FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_care_profile_name_revisions_profileId ON care_profile_name_revisions(profileId)")
    }
}
