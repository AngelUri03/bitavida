package com.bitavida.app.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom

/**
 * Disposable feasibility probe only. It never opens the application database or its key.
 *
 * This test does not implement a backup. In particular, success does not establish
 * access to the app's Android Keystore key or prove restoration of a real database.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherSnapshotFixtureTest {
    @Test
    fun exportsEncryptedSnapshotFromReadOnlySourceWhileWriterKeepsWalOpen() {
        // Instrumentation runs as the target UID; the test package's own
        // cache directory need not exist or be accessible to that process.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = Files.createTempDirectory(
            context.cacheDir.toPath(),
            "sqlcipher-snapshot-fixture-",
        ).toFile()
        val source = File(marker, "source.db")
        val copy = File(marker, "copy.db")
        val sourceKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val copyKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val blob = ByteArray(16_384) { (it % 251).toByte() }
        // Do not run a default corruption handler that could delete any file.
        val preserveFiles = DatabaseErrorHandler { _, _ -> }
        try {
            System.loadLibrary("sqlcipher")
            open(source, sourceKey, SQLiteDatabase.CREATE_IF_NECESSARY, preserveFiles).use { writer ->
                writer.execSQL("PRAGMA auto_vacuum = FULL")
                assertEquals(1, scalarInt(writer, "PRAGMA auto_vacuum"))
                assertTrue(writer.enableWriteAheadLogging())
                writer.execSQL("CREATE TABLE fixture (id INTEGER PRIMARY KEY, payload BLOB NOT NULL)")
                writer.execSQL("CREATE TABLE fixture_audit (fixture_id INTEGER NOT NULL)")
                writer.execSQL(
                    "CREATE TRIGGER fixture_trigger AFTER INSERT ON fixture " +
                        "BEGIN INSERT INTO fixture_audit VALUES (NEW.id); END",
                )
                writer.execSQL("PRAGMA user_version = 37")
                writer.execSQL("INSERT INTO fixture(id, payload) VALUES (?, ?)", arrayOf(1, blob))
                val wal = File(source.path + "-wal")
                assertTrue("The fixture writer must still have an active WAL", wal.isFile && wal.length() > 0)

                // Verify the existing source can be opened without write access.
                open(source, sourceKey, SQLiteDatabase.OPEN_READONLY, preserveFiles).use { reader ->
                    assertEquals(37, reader.version)
                    assertEquals(1, scalarInt(reader, "SELECT count(*) FROM fixture"))
                    assertThrows(Exception::class.java) {
                        reader.execSQL("INSERT INTO fixture(id, payload) VALUES (99, X'00')")
                    }
                }

                // Make a new encrypted main DB. Attach only the fixture source via
                // SQLite's mode=ro URI, and prove that writes through the alias fail.
                open(copy, copyKey, SQLiteDatabase.CREATE_IF_NECESSARY, preserveFiles).use { target ->
                    val sourceUri = source.toURI().toASCIIString() + "?mode=ro"
                    target.execSQL(
                        "ATTACH DATABASE ? AS source KEY ?",
                        arrayOf(sourceUri, sourceKey),
                    )
                    assertThrows(Exception::class.java) {
                        target.execSQL("INSERT INTO source.fixture(id, payload) VALUES (98, X'00')")
                    }
                    val sourceVersion = scalarInt(target, "PRAGMA source.user_version")
                    val sourceAutoVacuum = scalarInt(target, "PRAGMA source.auto_vacuum")
                    assertEquals(1, sourceAutoVacuum)
                    // Keep an uncommitted write open while exporting the last
                    // committed WAL state, then commit it only after export.
                    writer.beginTransactionNonExclusive()
                    try {
                        writer.execSQL(
                            "INSERT INTO fixture(id, payload) VALUES (?, ?)",
                            arrayOf(2, byteArrayOf(42)),
                        )
                        // sqlcipher_export does not copy these PRAGMAs.
                        target.execSQL("PRAGMA main.auto_vacuum = $sourceAutoVacuum")
                        target.rawQuery("SELECT sqlcipher_export('main', 'source')", emptyArray()).use {
                            assertTrue(it.moveToFirst())
                        }
                        target.execSQL("PRAGMA main.user_version = $sourceVersion")
                        SnapshotContentVerifier.verify(target)
                        // Verify that corruption of even one BLOB is detected.
                        target.execSQL("UPDATE main.fixture SET payload = X'00' WHERE id = 1")
                        assertThrows(IllegalStateException::class.java) {
                            SnapshotContentVerifier.verify(target)
                        }
                        target.execSQL(
                            "UPDATE main.fixture SET payload = ? WHERE id = 1",
                            arrayOf(blob),
                        )
                        SnapshotContentVerifier.verify(target)
                        target.execSQL("DETACH DATABASE source")
                        writer.setTransactionSuccessful()
                    } finally {
                        writer.endTransaction()
                    }
                }
                assertEquals(2, scalarInt(writer, "SELECT count(*) FROM fixture"))
            }

            open(copy, copyKey, SQLiteDatabase.OPEN_READONLY, preserveFiles).use { snapshot ->
                assertEquals(37, snapshot.version)
                assertEquals(1, scalarInt(snapshot, "PRAGMA auto_vacuum"))
                assertEquals(1, scalarInt(snapshot, "SELECT count(*) FROM fixture"))
                assertEquals(1, scalarInt(snapshot, "SELECT count(*) FROM fixture_audit"))
                snapshot.rawQuery("SELECT payload FROM fixture WHERE id = 1", emptyArray()).use {
                    assertTrue(it.moveToFirst())
                    assertArrayEquals(blob, it.getBlob(0))
                }
                snapshot.rawQuery("PRAGMA integrity_check", emptyArray()).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("ok", it.getString(0))
                }
                assertEquals(
                    1,
                    scalarInt(snapshot, "SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name='fixture_trigger'"),
                )
            }
            assertThrows(Exception::class.java) {
                open(copy, sourceKey, SQLiteDatabase.OPEN_READONLY, preserveFiles).use {
                    scalarInt(it, "SELECT count(*) FROM sqlite_master")
                }
            }
            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    copy.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                ).use {
                    it.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                }
            }
        } finally {
            sourceKey.fill(0)
            copyKey.fill(0)
            // Only delete files whose names this fixture creates. Never recurse
            // through the target app's cache or remove any pre-existing file.
            for (fixtureFile in listOf(source, copy)) {
                for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                    File(fixtureFile.path + suffix).delete()
                }
            }
            marker.delete()
        }
    }

    @Test
    fun detectsConcurrentCommittedWriteWithReadOnlyDataVersionObserver() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(
            context.cacheDir.toPath(),
            "sqlcipher-version-fixture-",
        ).toFile()
        val file = File(directory, "fixture.db")
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val preserveFiles = DatabaseErrorHandler { _, _ -> }
        try {
            System.loadLibrary("sqlcipher")
            open(file, key, SQLiteDatabase.CREATE_IF_NECESSARY, preserveFiles).use { writer ->
                assertTrue(writer.enableWriteAheadLogging())
                writer.execSQL("CREATE TABLE fixture (id INTEGER PRIMARY KEY)")
                writer.execSQL("INSERT INTO fixture VALUES (1)")
                // Do not enable WAL on the observer: SQLCipher Android then has
                // a one-connection pool, as required by PRAGMA data_version.
                open(file, key, SQLiteDatabase.OPEN_READONLY, preserveFiles).use { observer ->
                    val before = scalarInt(observer, "PRAGMA data_version")
                    assertEquals(before, scalarInt(observer, "PRAGMA data_version"))
                    val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                    try {
                        executor.submit {
                            writer.execSQL("INSERT INTO fixture VALUES (2)")
                        }.get(15, java.util.concurrent.TimeUnit.SECONDS)
                    } finally {
                        executor.shutdownNow()
                    }
                    val after = scalarInt(observer, "PRAGMA data_version")
                    assertTrue("A separate committed write must be detected", after != before)
                    assertEquals(2, scalarInt(observer, "SELECT count(*) FROM fixture"))
                }
            }
        } finally {
            key.fill(0)
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                File(file.path + suffix).delete()
            }
            directory.delete()
        }
    }

    @Test
    fun independentlyWrappedKeyCanReopenEncryptedFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "recovery-fixture-").toFile()
        val source = File(directory, "source.db")
        val output = File(directory, "output")
        check(output.mkdir())
        val sourceKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val preserveFiles = DatabaseErrorHandler { _, _ -> }
        try {
            System.loadLibrary("sqlcipher")
            val pair = java.security.KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048)
            }.generateKeyPair()
            open(source, sourceKey, SQLiteDatabase.CREATE_IF_NECESSARY, preserveFiles).use { writer ->
                writer.execSQL("PRAGMA auto_vacuum = FULL")
                writer.execSQL("CREATE TABLE fixture (id INTEGER PRIMARY KEY, payload BLOB NOT NULL)")
                writer.execSQL("PRAGMA user_version = 37")
                writer.execSQL("INSERT INTO fixture VALUES (?, ?)", arrayOf(1, byteArrayOf(1, 2, 3)))
            }
            IndependentSnapshotExporter.create(source, sourceKey, output, pair.public)
            val unwrap = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            unwrap.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                pair.private,
                javax.crypto.spec.OAEPParameterSpec(
                    "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256,
                    javax.crypto.spec.PSource.PSpecified.DEFAULT,
                ),
            )
            val recoveredKey = unwrap.doFinal(File(output, "recovery-key.rsa").readBytes())
            try {
                open(File(output, "snapshot.db"), recoveredKey, SQLiteDatabase.OPEN_READONLY, preserveFiles)
                    .use { snapshot ->
                        assertEquals(37, snapshot.version)
                        assertEquals(1, scalarInt(snapshot, "PRAGMA auto_vacuum"))
                        snapshot.rawQuery("SELECT payload FROM fixture", emptyArray()).use {
                            assertTrue(it.moveToFirst())
                            assertArrayEquals(byteArrayOf(1, 2, 3), it.getBlob(0))
                        }
                    }
            } finally {
                recoveredKey.fill(0)
            }
        } finally {
            sourceKey.fill(0)
            for (file in listOf(source, File(output, "snapshot.db"))) {
                for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                    File(file.path + suffix).delete()
                }
            }
            File(output, "recovery-key.rsa").delete()
            output.delete()
            directory.delete()
        }
    }

    private fun open(
        file: File,
        key: ByteArray,
        flags: Int,
        handler: DatabaseErrorHandler,
    ): SQLiteDatabase = SQLiteDatabase.openDatabase(file.absolutePath, key, null, flags, handler, null)

    private fun scalarInt(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, emptyArray()).use {
            check(it.moveToFirst())
            it.getInt(0)
        }
}
