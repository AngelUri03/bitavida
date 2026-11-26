package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

/** A receipt of an attempt, never proof that a person saw a notification. */
@Entity(tableName = "notification_deliveries",
    foreignKeys = [ForeignKey(entity = CareRelationshipEntity::class,
        parentColumns = ["profileId"], childColumns = ["profileId"])],
    indices = [Index(value = ["profileId"])])
data class NotificationDeliveryEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val module: String,
    val title: String,
    val message: String,
    val attemptedAtEpochMillis: Long,
    val result: String,
    val relatedToken: String?,
    val readAtEpochMillis: Long?,
    val openedAtEpochMillis: Long?,
)

@Dao
interface NotificationDeliveryDao {
    @Insert
    suspend fun insert(delivery: NotificationDeliveryEntity)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.IGNORE)
    suspend fun claimOnce(delivery: NotificationDeliveryEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM notification_deliveries WHERE id = :id AND profileId = :profileId)")
    suspend fun hasClaim(profileId: String, id: String): Boolean

    @Query("UPDATE notification_deliveries SET result = :result WHERE id = :id AND profileId = :profileId AND result = 'ATTEMPTING'")
    suspend fun finishClaim(profileId: String, id: String, result: String)

    @Query("SELECT * FROM notification_deliveries WHERE profileId = :profileId ORDER BY attemptedAtEpochMillis DESC, id DESC")
    fun observe(profileId: String): Flow<List<NotificationDeliveryEntity>>

    @Query("UPDATE notification_deliveries SET readAtEpochMillis = COALESCE(readAtEpochMillis, :at) WHERE id = :id AND profileId = :profileId")
    suspend fun markRead(profileId: String, id: String, at: Long)

    @Query("UPDATE notification_deliveries SET openedAtEpochMillis = COALESCE(openedAtEpochMillis, :at), readAtEpochMillis = COALESCE(readAtEpochMillis, :at) WHERE id = :id AND profileId = :profileId")
    suspend fun markOpened(profileId: String, id: String, at: Long)

    @Query("UPDATE notification_deliveries SET readAtEpochMillis = :at WHERE profileId = :profileId AND readAtEpochMillis IS NULL")
    suspend fun markAllRead(profileId: String, at: Long)
}

internal val NotificationDeliveryMigration23To24 = object : Migration(23, 24) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS notification_deliveries (id TEXT NOT NULL, profileId TEXT NOT NULL, module TEXT NOT NULL, title TEXT NOT NULL, message TEXT NOT NULL, attemptedAtEpochMillis INTEGER NOT NULL, result TEXT NOT NULL, relatedToken TEXT, readAtEpochMillis INTEGER, openedAtEpochMillis INTEGER, PRIMARY KEY(id), FOREIGN KEY(profileId) REFERENCES care_relationships(profileId) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_notification_deliveries_profileId ON notification_deliveries(profileId)")
    }
}
