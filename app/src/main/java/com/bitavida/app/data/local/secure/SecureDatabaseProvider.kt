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
                .addMigrations(CareProfileMigration1To2)
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
