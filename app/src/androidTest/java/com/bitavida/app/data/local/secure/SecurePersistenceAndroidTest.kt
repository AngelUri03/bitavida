package com.bitavida.app.data.local.secure

import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecurePersistenceAndroidTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val config = SecureStorageConfig(
        databaseName = "secure-persistence-instrumented-test.db",
        wrappedKeyFileName = "secure-persistence-instrumented-test-key.bin",
        wrappingKeyAlias = "com.bitavida.app.test.secure-persistence",
    )

    private var initializer = SecureDatabaseInitializer(config)

    @Before
    fun cleanBefore() {
        deleteTestStorage()
    }

    @After
    fun cleanAfter() {
        runBlocking { initializer.close() }
        deleteTestStorage()
    }

    @Test
    fun encryptedDatabaseWritesReadsReopensAndRejectsOtherReaders() = runBlocking {
        val expected = TechnicalProbeEntity(1L, "fictitious-technical-record")
        val database = initializer.open(context)
        database.technicalProbeDao().save(expected)
        assertEquals(expected, database.technicalProbeDao().findById(expected.id))

        initializer.close()
        initializer = SecureDatabaseInitializer(config)
        val reopened = initializer.open(context)
        assertEquals(expected, reopened.technicalProbeDao().findById(expected.id))
        initializer.close()

        val databaseFile = context.getDatabasePath(config.databaseName)
        val originalLength = databaseFile.length()

        val wrongKeyDatabase = Room.databaseBuilder(
            context,
            SecureSpikeDatabase::class.java,
            databaseFile.absolutePath,
        )
            .setDriver(SQLCipherDriver(ByteArray(32) { 0x5A }, null, null))
            .build()
        assertThrows(Exception::class.java) {
            runBlocking { wrongKeyDatabase.technicalProbeDao().count() }
        }
        wrongKeyDatabase.close()
        assertEquals(originalLength, databaseFile.length())

        val plaintextProbeFile = File(
            context.cacheDir,
            "secure-persistence-plaintext-probe.db",
        )
        databaseFile.copyTo(plaintextProbeFile, overwrite = true)
        try {
            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                SQLiteDatabase.openDatabase(
                    plaintextProbeFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { plaintext ->
                    plaintext.rawQuery("SELECT count(*) FROM sqlite_master", null).use {
                        it.moveToFirst()
                    }
                }
            }
        } finally {
            SQLiteDatabase.deleteDatabase(plaintextProbeFile)
        }
        assertEquals(originalLength, databaseFile.length())
    }

    @Test
    fun missingKeystoreKeyFailsWithoutDeletingEncryptedData() = runBlocking {
        val database = initializer.open(context)
        database.technicalProbeDao().save(
            TechnicalProbeEntity(2L, "fictitious-preservation-check"),
        )
        initializer.close()

        val databaseFile = context.getDatabasePath(config.databaseName)
        val originalLength = databaseFile.length()
        keyStore().deleteEntry(config.wrappingKeyAlias)

        initializer = SecureDatabaseInitializer(config)
        assertThrows(WrappingKeyUnavailableException::class.java) {
            runBlocking { initializer.open(context) }
        }
        assertTrue(databaseFile.exists())
        assertEquals(originalLength, databaseFile.length())
    }

    private fun deleteTestStorage() {
        context.deleteDatabase(config.databaseName)
        File(context.noBackupFilesDir, config.wrappedKeyFileName).delete()
        keyStore().deleteEntry(config.wrappingKeyAlias)
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
