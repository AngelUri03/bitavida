package com.bitavida.app.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Explicit transfer of an already completed, encrypted backup only.
 *
 * This does not open the live database or unwrap a key. It neither creates nor
 * repairs a backup and must run only after the original snapshot has completed.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedSnapshotExternalTransferTest {
    @Test
    fun exportCompletedEncryptedSnapshot() {
        assumeTrue(
            "Explicit encrypted transfer required",
            InstrumentationRegistry.getArguments().getString("bitavida_snapshot") == "export-encrypted-v1",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bitavida.app")
        val source = File(context.noBackupFilesDir, "bitavida-original-snapshot-v1")
        val external = requireNotNull(context.getExternalFilesDir(null))
        val destination = File(external, "bitavida-encrypted-snapshot-export-v1")
        val names = listOf(
            "snapshot.db",
            "recovery-key.rsa",
            "original-key-envelope.bin",
            "associated-files.enc",
        )
        val manifest = File(source, "verified.sha256")
        var createdDirectory = false
        val createdFiles = mutableListOf<File>()
        try {
            check(Files.isDirectory(source.toPath(), LinkOption.NOFOLLOW_LINKS))
            check(Files.isDirectory(external.toPath(), LinkOption.NOFOLLOW_LINKS))
            requireRegularFile(manifest)
            check(manifest.length() in 1..4096)
            val originalManifest = manifest.readBytes()
            val expected = parseManifest(originalManifest, names)
            val manifestDigest = sha256(manifest)

            val sourceFiles = names.map { name -> File(source, name).also(::requireRegularFile) }
            val requiredBytes = sourceFiles.sumOf(File::length) + manifest.length() + 1_048_576L
            check(requiredBytes > 0 && external.usableSpace > requiredBytes)
            // This directory is new or the operation fails before copying.
            Files.createDirectory(destination.toPath())
            createdDirectory = true

            val recorded = linkedMapOf<String, String>()
            names.forEach { name ->
                val file = File(source, name)
                val target = File(destination, name)
                copyAndVerify(file, target, expected.getValue(name))
                createdFiles.add(target)
                recorded[name] = expected.getValue(name)
            }
            check(manifest.readBytes().contentEquals(originalManifest))
            val copiedManifest = File(destination, "verified.sha256")
            copyAndVerify(manifest, copiedManifest, manifestDigest)
            createdFiles.add(copiedManifest)
            recorded["verified.sha256"] = manifestDigest

            val transferManifest = File(destination, "transfer.sha256")
            val lines = buildString {
                recorded.forEach { (name, hash) -> append(hash).append("  ").append(name).append('\n') }
            }
            writeNew(transferManifest, lines.toByteArray(Charsets.US_ASCII))
            createdFiles.add(transferManifest)
        } catch (_: Exception) {
            // Only remove paths created in this new destination by this run.
            createdFiles.forEach(File::delete)
            if (createdDirectory) destination.delete()
            throw AssertionError("Encrypted snapshot transfer failed; no live database was opened.")
        }
    }

    private fun parseManifest(content: ByteArray, names: List<String>): Map<String, String> {
        val lines = content.toString(Charsets.US_ASCII).lines().filter(String::isNotEmpty)
        check(lines.size == names.size + 1 && lines.first() == "Bitavida encrypted snapshot v1")
        val result = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val parts = line.split(' ')
            check(parts.size == 2 && parts[0] in names && parts[1].matches(Regex("[0-9a-f]{64}")))
            check(result.put(parts[0], parts[1]) == null)
        }
        check(result.keys == names.toSet())
        return result
    }

    private fun copyAndVerify(source: File, target: File, expectedHash: String) {
        requireRegularFile(source)
        check(sha256(source) == expectedHash)
        val output = Files.newOutputStream(
            target.toPath(),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
        try {
            output.use {
                Files.newInputStream(source.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                    input.copyTo(it)
                }
            }
            requireRegularFile(target)
            check(sha256(target) == expectedHash && sha256(source) == expectedHash)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun writeNew(file: File, bytes: ByteArray) {
        val output = Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        try {
            output.use { it.write(bytes) }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun requireRegularFile(file: File) {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        try {
            Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } finally {
            buffer.fill(0)
        }
    }
}
