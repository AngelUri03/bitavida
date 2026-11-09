package com.bitavida.app.data.local.secure

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [TechnicalProbeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SecureSpikeDatabase : RoomDatabase() {
    abstract fun technicalProbeDao(): TechnicalProbeDao
}
