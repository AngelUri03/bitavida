package com.bitavida.app.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Explicitly invoked development instrumentation, not application functionality.
 * This never invokes Room, a migration, a key generator, or a database write
 * against the source. It must not be used until its synthetic fixture and
 * independent off-device recovery have both been verified.
 */
@RunWith(AndroidJUnit4::class)
class OriginalDatabaseSnapshotInstrumentedTest {
    @Test
    fun createExplicitSnapshot() {
        assumeTrue(
            "Explicit invocation required",
            InstrumentationRegistry.getArguments().getString("bitavida_snapshot") == "create-v1",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = context.getDatabasePath("bitavida-secure-spike.db")
        val envelope = File(context.noBackupFilesDir, "bitavida-secure-spike-key-v1.bin")
        val publicKeyFile = File(context.cacheDir, "bitavida-snapshot-public.der")
        val output = File(context.noBackupFilesDir, "bitavida-original-snapshot-v1")
        var sourceKey: ByteArray? = null
        var ownOutput = false
        var completed = false
        try {
            check(Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isRegularFile(envelope.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isRegularFile(publicKeyFile.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(envelope.length() in 38..8192 && publicKeyFile.length() in 100..8192)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(publicKeyFile.readBytes()))
            val keyEnvelope = envelope.readBytes()
            try {
                sourceKey = unwrapExistingKey(keyEnvelope)
                Files.createDirectory(output.toPath())
                ownOutput = true
                Logger.setTarget(NoopTarget())
                System.loadLibrary("sqlcipher")
                IndependentSnapshotExporter.create(source, sourceKey, output, publicKey) { masterKey ->
                    val appData = File(context.applicationInfo.dataDir)
                    AssociatedFilesBackup.create(
                        roots = mapOf(
                            "files" to File(appData, "files"),
                            "no_backup" to context.noBackupFilesDir,
                            "shared_prefs" to File(appData, "shared_prefs"),
                        ),
                        output = File(output, "associated-files.enc"),
                        masterKey = masterKey,
                    )
                }
                for (name in listOf("snapshot.db", "recovery-key.rsa", "associated-files.enc")) {
                    RandomAccessFile(File(output, name), "rw").use { it.fd.sync() }
                }
                writeAndSync(File(output, "original-key-envelope.bin"), keyEnvelope)
                val contents = listOf(
                    "snapshot.db",
                    "recovery-key.rsa",
                    "original-key-envelope.bin",
                    "associated-files.enc",
                )
                val manifest = buildString {
                    appendLine("Bitavida encrypted snapshot v1")
                    contents.forEach { name ->
                        append(name)
                        append(' ')
                        append(sha256(File(output, name)))
                        appendLine()
                    }
                }
                writeAndSync(File(output, "verified.sha256"), manifest.toByteArray(Charsets.US_ASCII))
                completed = true
            } finally {
                keyEnvelope.fill(0)
            }
        } catch (_: Exception) {
            // Do not include SQL, paths, key material or patient data in test output.
            throw AssertionError("Snapshot failed; source database was not written by this test.")
        } finally {
            sourceKey?.fill(0)
            if (ownOutput && !completed) {
                for (name in listOf(
                    "snapshot.db", "snapshot.db-wal", "snapshot.db-shm", "snapshot.db-journal",
                    "recovery-key.rsa", "original-key-envelope.bin", "associated-files.enc", "verified.sha256",
                )) {
                    File(output, name).delete()
                }
                output.delete()
            }
        }
    }

    private fun unwrapExistingKey(encoded: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encoded)
        check(buffer.int == 0x42564B31 && buffer.get().toInt() == 1)
        val ivSize = buffer.get().toInt() and 0xff
        val ciphertextSize = buffer.int
        check(ivSize in 12..16 && ciphertextSize >= 16)
        check(buffer.remaining() == ivSize + ciphertextSize)
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(ciphertextSize).also(buffer::get)
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = store.getKey(
                "com.bitavida.app.secure-spike.database-key.v1",
                null,
            ) as? SecretKey ?: error("Missing key")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val databaseKey = cipher.doFinal(ciphertext)
            if (databaseKey.size != 32) {
                databaseKey.fill(0)
                error("Invalid key")
            }
            return databaseKey
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun writeAndSync(file: File, value: ByteArray) {
        FileOutputStream(file).use {
            it.write(value)
            it.fd.sync()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        try {
            file.inputStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } finally {
            buffer.fill(0)
        }
    }
}
