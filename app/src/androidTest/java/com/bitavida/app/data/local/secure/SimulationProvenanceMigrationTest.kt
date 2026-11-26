package com.bitavida.app.data.local.secure

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.file.Files
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a new disposable, encrypted test database only.
 * This tests the migration SQL and relational constraints, not Room's schema validation,
 * historical application data or the complete scenario loader.
 */
@RunWith(AndroidJUnit4::class)
class SimulationProvenanceMigrationTest {
    @Test
    fun additiveMigrationPreservesExistingRowsAndRollsBackFailedFixtureLoad() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "simulation-migration-fixture-")
        val db = root.resolve("fixture.db")
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            System.loadLibrary("sqlcipher")
            SQLCipherDriver(key, null, null).open(db.toString()).use { connection ->
                connection.execSQL("PRAGMA foreign_keys = ON")
                connection.execSQL("CREATE TABLE preexisting (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL)")
                connection.execSQL("INSERT INTO preexisting VALUES ('original-fixture', X'010203FF')")
                connection.execSQL("PRAGMA user_version = 33")

                // A failed migration is atomic: no provenance tables survive a rollback.
                connection.execSQL("BEGIN IMMEDIATE")
                SimulationProvenanceMigration33To34.migrate(connection)
                connection.execSQL("ROLLBACK")
                assertEquals(0L, connection.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'simulation_scenarios'"))
                assertEquals(33L, connection.scalar("PRAGMA user_version"))

                connection.execSQL("BEGIN IMMEDIATE")
                SimulationProvenanceMigration33To34.migrate(connection)
                connection.execSQL("PRAGMA user_version = 34")
                connection.execSQL("COMMIT")
                assertEquals(34L, connection.scalar("PRAGMA user_version"))
                assertEquals(1L, connection.scalar("SELECT COUNT(*) FROM preexisting WHERE id = 'original-fixture' AND hex(payload) = '010203FF'"))
                assertEquals(0L, connection.scalar("SELECT COUNT(*) FROM simulation_scenarios"))
                connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }

                // A failed load rolls back the clinical row, scenario marker, and mapping.
                connection.execSQL("BEGIN IMMEDIATE")
                connection.execSQL("INSERT INTO preexisting VALUES ('simulated-fixture', X'04')")
                connection.execSQL("INSERT INTO simulation_scenarios VALUES ('october-2026', 'fixture-manifest', 1)")
                connection.execSQL("INSERT INTO simulation_provenance VALUES ('october-2026', 'medication_plans', 'drug-a', 'plan-1')")
                assertTrue(runCatching {
                    connection.execSQL("INSERT INTO simulation_provenance VALUES ('october-2026', 'medication_plans', 'drug-b', 'plan-1')")
                }.isFailure)
                connection.execSQL("ROLLBACK")
                assertEquals(0L, connection.scalar("SELECT COUNT(*) FROM preexisting WHERE id = 'simulated-fixture'"))
                assertEquals(0L, connection.scalar("SELECT COUNT(*) FROM simulation_scenarios"))
                assertEquals(0L, connection.scalar("SELECT COUNT(*) FROM simulation_provenance"))

                connection.execSQL("BEGIN IMMEDIATE")
                connection.execSQL("INSERT INTO simulation_scenarios VALUES ('october-2026', 'fixture-manifest', 1)")
                connection.execSQL("INSERT INTO simulation_provenance VALUES ('october-2026', 'medication_plans', 'drug-a', '1')")
                connection.execSQL("INSERT INTO simulation_provenance VALUES ('october-2026', 'glucose_measurements', 'measurement-a', '1')")
                connection.execSQL("COMMIT")
                assertEquals(2L, connection.scalar("SELECT COUNT(*) FROM simulation_provenance"))

                // Qualified identity may not be claimed by a second scenario.
                connection.execSQL("INSERT INTO simulation_scenarios VALUES ('other-scenario', 'other-manifest', 2)")
                assertTrue(runCatching {
                    connection.execSQL("INSERT INTO simulation_provenance VALUES ('other-scenario', 'medication_plans', 'other', '1')")
                }.isFailure)
                assertTrue(runCatching {
                    connection.execSQL("INSERT INTO simulation_provenance VALUES ('missing-scenario', 'notes', 'note', '1')")
                }.isFailure)
                assertTrue(runCatching {
                    connection.execSQL("DELETE FROM simulation_scenarios WHERE scenarioId = 'october-2026'")
                }.isFailure)
                connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
                assertEquals(1L, connection.scalar("SELECT COUNT(*) FROM preexisting WHERE id = 'original-fixture' AND hex(payload) = '010203FF'"))
            }
        } finally {
            key.fill(0)
            root.toFile().deleteRecursively()
        }
    }

    private fun SQLiteConnection.scalar(sql: String): Long =
        prepare(sql).use { statement ->
            check(statement.step())
            statement.getLong(0)
        }
}
