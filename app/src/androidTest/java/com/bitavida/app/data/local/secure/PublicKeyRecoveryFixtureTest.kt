package com.bitavida.app.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec

/** Synthetic-only fixture for off-device recovery using a host-generated RSA key. */
@RunWith(AndroidJUnit4::class)
class PublicKeyRecoveryFixtureTest {
    @Test
    fun exportSyntheticFixtureForOffDeviceRecovery() {
        assumeTrue(
            "Explicit fixture invocation required",
            InstrumentationRegistry.getArguments().getString("bitavida_fixture") == "recover-v1",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val publicFile = File(context.cacheDir, "bitavida-snapshot-public.der")
        val output = File(context.cacheDir, "bitavida-recovery-fixture-v1")
        val sourceDir = Files.createTempDirectory(context.cacheDir.toPath(), "recovery-source-").toFile()
        val source = File(sourceDir, "source.db")
        val associatedDir = File(sourceDir, "associated").apply { check(mkdir()) }
        val attachment = File(associatedDir, "fictitious.txt").apply { writeText("synthetic-associated-file") }
        val sourceKey = ByteArray(32).also(SecureRandom()::nextBytes)
        var outputCreated = false
        var success = false
        try {
            check(Files.isRegularFile(publicFile.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(publicFile.length() in 100..8192)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(publicFile.readBytes()))
            Files.createDirectory(output.toPath())
            outputCreated = true
            Logger.setTarget(NoopTarget())
            System.loadLibrary("sqlcipher")
            SQLiteDatabase.openDatabase(
                source.absolutePath,
                sourceKey,
                null,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                DatabaseErrorHandler { _, _ -> },
                null,
            ).use { fixture ->
                fixture.execSQL("PRAGMA auto_vacuum = FULL")
                fixture.execSQL("PRAGMA user_version = 37")
                fixture.execSQL("CREATE TABLE proof (id INTEGER PRIMARY KEY, label TEXT, image BLOB)")
                fixture.execSQL(
                    "INSERT INTO proof VALUES (?, ?, ?)",
                    arrayOf(1, "synthetic-recovery-proof", byteArrayOf(0, 1, 2, 3, -1)),
                )
            }
            IndependentSnapshotExporter.create(source, sourceKey, output, publicKey) { masterKey ->
                AssociatedFilesBackup.create(
                    mapOf("fixture" to associatedDir),
                    File(output, "associated-files.enc"),
                    masterKey,
                )
            }
            success = true
        } catch (_: Exception) {
            throw AssertionError("Synthetic recovery fixture failed.")
        } finally {
            sourceKey.fill(0)
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                File(source.path + suffix).delete()
            }
            attachment.delete()
            associatedDir.delete()
            sourceDir.delete()
            if (outputCreated && !success) {
                for (name in listOf(
                    "snapshot.db", "snapshot.db-wal", "snapshot.db-shm", "snapshot.db-journal",
                    "recovery-key.rsa", "associated-files.enc",
                )) {
                    File(output, name).delete()
                }
                output.delete()
            }
        }
    }
}
