package com.bitavida.app.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * Explicit synthetic-fixture transfer only. Paths are fixed; this never reads
 * the application's database, key envelope, preferences or attachments.
 *
 * Contents exported to external storage are the three encrypted fixture files
 * and a fixture-only public key. No private or unencrypted key is transferred.
 */
@RunWith(AndroidJUnit4::class)
class FixtureExternalTransferTest {
    @Test
    fun copyEncryptedFixtureToExternal() {
        requireExplicit("export-transfer-v1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = context.cacheDir
        val fixture = File(cache, "bitavida-recovery-fixture-v1")
        val external = requireNotNull(context.getExternalFilesDir(null))
        val destination = File(external, "bitavida-fixture-transfer-v1")
        val inbound = File(destination, "inbound")
        val sources = listOf(
            File(fixture, "snapshot.db") to "snapshot.db",
            File(fixture, "recovery-key.rsa") to "recovery-key.rsa",
            File(fixture, "associated-files.enc") to "associated-files.enc",
            File(cache, "bitavida-fixture-receiver-public.bin") to "receiver-public.bin",
        )
        check(Files.isDirectory(fixture.toPath(), LinkOption.NOFOLLOW_LINKS))
        check(Files.isDirectory(external.toPath(), LinkOption.NOFOLLOW_LINKS))
        sources.forEach { (file, _) -> requireRegularFile(file) }
        var directoryCreated = false
        val copies = mutableListOf<File>()
        try {
            // Fails if any directory already exists; never replaces files.
            Files.createDirectory(destination.toPath())
            directoryCreated = true
            Files.createDirectory(inbound.toPath())
            sources.forEach { (source, name) ->
                val target = File(destination, name)
                copyAndVerify(source, target)
                copies.add(target)
            }
        } catch (_: Exception) {
            // Delete only names that this invocation successfully created.
            copies.forEach(File::delete)
            if (directoryCreated) {
                inbound.delete()
                destination.delete()
            }
            throw AssertionError("Synthetic fixture export failed.")
        }
    }

    @Test
    fun copyReturnedFixtureFromExternal() {
        requireExplicit("import-transfer-v1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = context.cacheDir
        val external = requireNotNull(context.getExternalFilesDir(null))
        val destination = File(external, "bitavida-fixture-transfer-v1")
        val inbound = File(destination, "inbound")
        check(Files.isDirectory(destination.toPath(), LinkOption.NOFOLLOW_LINKS))
        check(Files.isDirectory(inbound.toPath(), LinkOption.NOFOLLOW_LINKS))
        val mappings = listOf(
            File(inbound, "snapshot.db") to File(cache, "bitavida-fixture-roundtrip.db"),
            File(inbound, "associated-files.enc") to File(cache, "bitavida-fixture-associated.enc"),
            File(inbound, "fixture-rewrapped.rsa") to File(cache, "bitavida-fixture-rewrapped.rsa"),
        )
        mappings.forEach { (source, target) ->
            requireRegularFile(source)
            check(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS))
        }
        val copies = mutableListOf<File>()
        try {
            mappings.forEach { (source, target) ->
                copyAndVerify(source, target)
                copies.add(target)
            }
        } catch (_: Exception) {
            copies.forEach(File::delete)
            throw AssertionError("Synthetic fixture import failed.")
        }
    }

    private fun copyAndVerify(source: File, target: File) {
        requireRegularFile(source)
        // CREATE_NEW atomically refuses existing names, including symlinks.
        // Once the stream opens, a failed copy may remove only that new file.
        val output = Files.newOutputStream(
            target.toPath(),
            java.nio.file.StandardOpenOption.CREATE_NEW,
            java.nio.file.StandardOpenOption.WRITE,
        )
        try {
            output.use {
                Files.newInputStream(source.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                    input.copyTo(it)
                }
            }
            requireRegularFile(target)
            check(source.length() == target.length())
            check(MessageDigest.isEqual(sha256(source), sha256(target)))
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun requireRegularFile(file: File) {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
    }

    private fun sha256(file: File): ByteArray {
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
            return digest.digest()
        } finally {
            buffer.fill(0)
        }
    }

    private fun requireExplicit(value: String) {
        assumeTrue(
            "Explicit fixture transfer required",
            InstrumentationRegistry.getArguments().getString("bitavida_fixture") == value,
        )
    }
}
