package com.bitavida.app.data.local.secure

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        TechnicalProbeEntity::class,
        CaregiverEntity::class,
        PersonUnderCareEntity::class,
        CareRelationshipEntity::class,
        ConsentRecordEntity::class,
        LegalAcceptanceEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SecureSpikeDatabase : RoomDatabase() {
    abstract fun technicalProbeDao(): TechnicalProbeDao

    abstract fun careProfileDao(): CareProfileDao
}
