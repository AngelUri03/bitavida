package com.bitavida.app.data.local.secure

import android.content.Context
import androidx.room3.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

object SecureDatabaseProvider {
    private val initializer = SecureDatabaseInitializer(ProductionSecureStorageConfig)

    suspend fun open(context: Context): SecureSpikeDatabase = initializer.open(context)
}

internal class SecureDatabaseInitializer(
    private val config: SecureStorageConfig,
) {
    private val mutex = Mutex()

    @Volatile
    private var instance: SecureSpikeDatabase? = null

    suspend fun open(context: Context): SecureSpikeDatabase = withContext(Dispatchers.IO) {
        instance?.let { return@withContext it }
        mutex.lock()
        try {
            instance?.let { return@withContext it }
            createAndVerify(context.applicationContext).also { instance = it }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            instance?.close()
            instance = null
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun createAndVerify(context: Context): SecureSpikeDatabase {
        val databaseFile = context.getDatabasePath(config.databaseName)
        val databaseKey = WrappedDatabaseKeyStore(context, config)
            .loadOrCreate(databaseExists = databaseFile.exists())
        configureSqlCipher()
        val database = try {
            Room.databaseBuilder(
                context = context,
                klass = SecureSpikeDatabase::class.java,
                name = databaseFile.absolutePath,
            )
                .setDriver(SQLCipherDriver(databaseKey, null, null))
                .addMigrations(
                    CareProfileMigration1To2,
                    MedicalCareMigration2To3,
                    MedicationCompletionMigration3To4,
                    MedicationReminderParticipationMigration4To5,
                    MedicationOperationsMigration5To6,
                    MedicationOccurrenceLinkMigration6To7,
                    MedicationLifecycleEffectiveAtMigration7To8,
                    MedicationAdministrationLogicalIdMigration8To9,
                    MedicationPeriodSeriesMigration9To10,
                    GlucoseMigration10To11,
                    GlucoseRevisionMigration11To12,
                    GlucoseFollowUpMigration12To13,
                    GlucoseFollowUpWindowMigration13To14,
                    GlucoseOccurrenceDecisionRevisionMigration14To15,
                    BloodPressureMigration15To16,
                    WeightMigration16To17,
                    WeightTargetMigration17To18,
                    ObservationMigration18To19,
                    ObservationCorrectionMigration19To20,
                    CareReminderMigration20To21,
                    NoteMigration21To22,
                    CareEventMigration22To23,
                    NotificationDeliveryMigration23To24,
                    CareProfileNameMigration24To25,
                    CareEventReminderMigration25To26,
                    CareEventPhotoMigration26To27,
                    CareEventDetailsMigration27To28,
                    CareEventWizardMigration28To29,
                    CareEventCatalogMigration29To30,
                    CareEventPhotoRemovalMigration30To31,
                    CareEventPhotoOrderMigration31To32,
                    CareEventConsultationMigration32To33,
                )
                .build()
        } catch (error: Exception) {
            databaseKey.fill(0)
            throw SecureDatabaseUnavailableException(error)
        }
        return try {
            database.technicalProbeDao().count()
            database
        } catch (error: Exception) {
            database.close()
            throw SecureDatabaseUnavailableException(error)
        }
    }

    private fun configureSqlCipher() {
        synchronized(SqlCipherLoadLock) {
            if (!sqlCipherLoaded) {
                Logger.setTarget(NoopTarget())
                System.loadLibrary("sqlcipher")
                sqlCipherLoaded = true
            }
        }
    }

    companion object {
        private val SqlCipherLoadLock = Any()

        @Volatile
        private var sqlCipherLoaded = false
    }
}
