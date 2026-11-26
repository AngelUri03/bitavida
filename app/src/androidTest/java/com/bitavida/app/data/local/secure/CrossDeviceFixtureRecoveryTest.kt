package com.bitavida.app.data.local.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

/** Synthetic fixture only: the host never sends its private key to Android. */
@RunWith(AndroidJUnit4::class)
class CrossDeviceFixtureRecoveryTest {
    private val alias = "com.bitavida.app.test.cross-device-snapshot-fixture-v1"

    @Test
    fun createFixtureReceiverPublicKey() {
        requireExplicit("receiver-v1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "bitavida-fixture-receiver-public.bin")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(!store.containsAlias(alias) && !file.exists())
        var generated = false
        try {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build(),
            )
            val public = generator.generateKeyPair().public as RSAPublicKey
            generated = true
            val modulus = public.modulus.toByteArray().let {
                if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
            }
            val exponent = public.publicExponent.toByteArray()
            DataOutputStream(file.outputStream()).use {
                it.writeInt(modulus.size)
                it.write(modulus)
                it.writeInt(exponent.size)
                it.write(exponent)
            }
        } catch (_: Exception) {
            if (generated) store.deleteEntry(alias)
            file.delete()
            throw AssertionError("Synthetic receiver key generation failed.")
        }
    }

    @Test
    fun reopenReturnedFixtureUsingHostRecoveredKey() {
        requireExplicit("verify-returned-v1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "bitavida-fixture-rewrapped.rsa")
        val returnedDatabase = File(context.cacheDir, "bitavida-fixture-roundtrip.db")
        val returnedAssociated = File(context.cacheDir, "bitavida-fixture-associated.enc")
        val publicFile = File(context.cacheDir, "bitavida-fixture-receiver-public.bin")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var key: ByteArray? = null
        try {
            check(
                input.isFile && returnedDatabase.isFile && returnedAssociated.isFile &&
                    publicFile.isFile && store.containsAlias(alias),
            )
            val privateKey = store.getKey(alias, null) as? PrivateKey ?: error("Missing fixture key")
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            key = cipher.doFinal(input.readBytes())
            check(key.size == 32)
            val archiveKey = AssociatedFilesBackup.deriveKey(key)
            try {
                val plaintext = java.io.ByteArrayOutputStream()
                ChunkedEncryptedArchive.decryptTo(returnedAssociated, archiveKey, plaintext)
                val entries = mutableMapOf<String, String>()
                java.util.zip.ZipInputStream(plaintext.toByteArray().inputStream()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                        zip.closeEntry()
                    }
                }
                val token = android.util.Base64.encodeToString(
                    "fictitious.txt".toByteArray(),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
                )
                assertEquals("synthetic-associated-file", entries["files/fixture/$token"])
                assertTrue(entries["bitavida-inventory.tsv"]?.contains(token) == true)
            } finally {
                archiveKey.fill(0)
            }
            System.loadLibrary("sqlcipher")
            SQLiteDatabase.openDatabase(
                returnedDatabase.absolutePath,
                key,
                null,
                SQLiteDatabase.OPEN_READONLY,
                DatabaseErrorHandler { _, _ -> },
                null,
            ).use { copy ->
                assertEquals(37, copy.version)
                copy.rawQuery("PRAGMA auto_vacuum", emptyArray()).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1, it.getInt(0))
                }
                copy.rawQuery("SELECT id, label, image FROM proof", emptyArray()).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1, it.getInt(0))
                    assertEquals("synthetic-recovery-proof", it.getString(1))
                    assertArrayEquals(byteArrayOf(0, 1, 2, 3, -1), it.getBlob(2))
                    assertTrue(!it.moveToNext())
                }
                copy.rawQuery("PRAGMA integrity_check", emptyArray()).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("ok", it.getString(0))
                }
            }
        } catch (_: Exception) {
            throw AssertionError("Synthetic returned fixture could not be verified.")
        } finally {
            key?.fill(0)
            if (store.containsAlias(alias)) store.deleteEntry(alias)
            // These contain only a test public key and an encrypted test key.
            publicFile.delete()
            input.delete()
        }
    }

    private fun requireExplicit(value: String) {
        assumeTrue(
            "Explicit fixture invocation required",
            InstrumentationRegistry.getArguments().getString("bitavida_fixture") == value,
        )
    }
}
