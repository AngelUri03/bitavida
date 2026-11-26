package com.bitavida.app.data.local.secure

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.SecureRandom
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class AssociatedFilesBackupTest {
    @Test
    fun capturesOnlySyntheticFilesWithEncryptedInventoryAndContents() {
        val root = Files.createTempDirectory(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(),
            "associated-fixture-",
        ).toFile()
        val files = File(root, "files").apply { check(mkdir()) }
        val privateDir = File(root, "no_backup").apply { check(mkdir()) }
        val output = File(root, "output").apply { check(mkdir()) }
        val attachment = File(files, "fictitious.bin")
        val preference = File(privateDir, "fictitious.xml")
        val syntheticAttachment = ByteArray(180_000).also(SecureRandom()::nextBytes)
        attachment.writeBytes(syntheticAttachment)
        preference.writeText("synthetic-preference")
        val archive = File(output, "associated-files.enc")
        val modified = File(output, "modified.enc")
        val truncated = File(output, "truncated.enc")
        val masterKey = ByteArray(32).also(SecureRandom()::nextBytes)
        var key: ByteArray? = null
        try {
            AssociatedFilesBackup.create(mapOf("files" to files, "no_backup" to privateDir), archive, masterKey)
            val bytes = archive.readBytes()
            assertEquals("BVA2", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("synthetic-preference"))
            key = AssociatedFilesBackup.deriveKey(masterKey)
            val plaintext = ByteArrayOutputStream()
            ChunkedEncryptedArchive.decryptTo(archive, key, plaintext)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(plaintext.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            val changed = bytes.copyOf()
            changed[20] = (changed[20].toInt() xor 1).toByte()
            modified.writeBytes(changed)
            truncated.writeBytes(bytes.copyOf(bytes.size - 10))
            assertThrows(Exception::class.java) {
                ChunkedEncryptedArchive.decryptTo(modified, key, ByteArrayOutputStream())
            }
            assertThrows(Exception::class.java) {
                ChunkedEncryptedArchive.decryptTo(truncated, key, ByteArrayOutputStream())
            }
            val token = Base64.encodeToString(
                "fictitious.bin".toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            assertArrayEquals(syntheticAttachment, entries["files/files/$token"])
            assertTrue(entries["bitavida-inventory.tsv"]?.toString(Charsets.UTF_8)?.contains(token) == true)
            assertEquals(3, entries.size)
        } finally {
            key?.fill(0)
            masterKey.fill(0)
            archive.delete()
            modified.delete()
            truncated.delete()
            attachment.delete()
            preference.delete()
            files.delete()
            privateDir.delete()
            output.delete()
            root.delete()
        }
    }
}
