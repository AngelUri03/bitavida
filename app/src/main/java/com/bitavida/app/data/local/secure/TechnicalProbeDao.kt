package com.bitavida.app.data.local.secure

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface TechnicalProbeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(record: TechnicalProbeEntity)

    @Query("SELECT * FROM technical_probe WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TechnicalProbeEntity?

    @Query("SELECT COUNT(*) FROM technical_probe")
    suspend fun count(): Int
}
