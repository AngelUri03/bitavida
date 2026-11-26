package com.bitavida.app.diagnostic

import androidx.room3.executeSQL
import androidx.room3.withWriteTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bitavida.app.data.local.secure.SecureDatabaseInitializer
import com.bitavida.app.data.local.secure.SecureStorageConfig
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a disposable encrypted database with fictitious rows, never the application database. */
@RunWith(AndroidJUnit4::class)
class DebugReportDiagnosticSnapshotTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = SecureStorageConfig(
        databaseName = "debug-diagnostic-snapshot-test.db",
        wrappedKeyFileName = "debug-diagnostic-snapshot-test-key.bin",
        wrappingKeyAlias = "com.bitavida.app.test.debug-diagnostic-snapshot",
    )
    private var initializer = SecureDatabaseInitializer(config)

    @Before fun before() = cleanStorage()

    @After fun after() {
        runBlocking { initializer.close() }
        cleanStorage()
    }

    @Test fun snapshotPreservesRelationshipsOmitsPhotoBytesAndDoesNotWriteToTheDatabase() = runBlocking {
        val database = initializer.open(context)
        database.withWriteTransaction {
            executeSQL("INSERT INTO caregivers (id, displayName, declarationSource, createdAtEpochMillis) VALUES ('caregiver-fake', 'Ficticia', 'SELF', 1)")
            executeSQL("INSERT INTO persons_under_care (id, displayName, declaredByCaregiverId, declarationSource, createdAtEpochMillis) VALUES ('person-fake', 'Ficticio', 'caregiver-fake', 'SELF', 1)")
            executeSQL("INSERT INTO care_relationships (profileId, caregiverId, personUnderCareId, usage, createdAtEpochMillis) VALUES ('profile-fake', 'caregiver-fake', 'person-fake', 'SELF', 1)")
            executeSQL("INSERT INTO observation_photos (id, profileId, personUnderCareId, jpeg) VALUES ('photo-fake', 'profile-fake', 'person-fake', X'01020304')")
        }
        val first = JSONObject(buildDiagnosticSnapshot(database))
        val second = JSONObject(buildDiagnosticSnapshot(database))
        assertTrue(first.getLong("schemaVersion") > 0)
        assertTrue(first.getString("zoneId").isNotBlank())
        val tables = first.getJSONObject("tables")
        val photo = tables.getJSONArray("observation_photos").getJSONObject(0)
        assertEquals("photo-fake", photo.getString("id"))
        assertEquals("profile-fake", photo.getString("profileId"))
        assertFalse(photo.has("jpeg"))
        assertFalse(tables.has("technical_probe"))
        assertTrue(tables.has("medication_intake_revisions"))
        assertEquals(tables.toString(), second.getJSONObject("tables").toString())
    }

    private fun cleanStorage() {
        context.deleteDatabase(config.databaseName)
        File(context.noBackupFilesDir, config.wrappedKeyFileName).delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(config.wrappingKeyAlias)
    }
}
