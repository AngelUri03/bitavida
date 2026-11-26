package com.bitavida.app.data.local.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Explicit two-UID handoff for the independently encrypted original snapshot.
 * Only the public receiver key, ciphertext key envelope and ciphertext database
 * are transferred. No active DB, production key store, Room or repository is opened.
 * The caller moves the public and encrypted files between app-specific directories.
 */
@RunWith(AndroidJUnit4::class)
class CandidateAuditKeyTransferTest {
    private val auditAlias = "com.bitavida.app.test.candidate-audit-receiver-v1"
    private val originalReceiverAlias = "com.bitavida.app.test.returned-snapshot-analysis-v1"

    @Test
    fun prepareCandidateReceiver() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("bitavida_analysis") == "prepare-candidate-receiver-v1")
        val expectedApkHash = args.getString("candidate_apk_sha256")
        check(expectedApkHash != null && expectedApkHash.matches(Regex("[0-9a-f]{64}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bitavida.app.audit")
        check(sha256File(File(context.applicationInfo.sourceDir)) == expectedApkHash)
        val external = requireNotNull(context.getExternalFilesDir(null))
        val directory = File(external, "bitavida-candidate-audit-receiver-v1")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(!store.containsAlias(auditAlias))
        try {
            Files.createDirectory(directory.toPath())
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(auditAlias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build(),
            )
            val publicKey = generator.generateKeyPair().public
            Files.newOutputStream(
                File(directory, "receiver-public.der").toPath(),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
            ).use { it.write(publicKey.encoded) }
        } catch (_: Exception) {
            // Never remove an existing key or file, including on partial failure.
            throw AssertionError("Candidate receiver preparation failed.")
        }
    }

    @Test
    fun rewrapOriginalSnapshotKeyForCandidate() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("bitavida_analysis") == "rewrap-candidate-key-v1")
        val expectedSnapshotHash = args.getString("snapshot_sha256")
        val expectedReceiverHash = args.getString("receiver_sha256")
        check(expectedSnapshotHash != null && expectedSnapshotHash.matches(Regex("[0-9a-f]{64}")))
        check(expectedReceiverHash != null && expectedReceiverHash.matches(Regex("[0-9a-f]{64}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bitavida.app")
        val external = requireNotNull(context.getExternalFilesDir(null))
        val sourceDir = File(external, "bitavida-original-analysis-return-v1")
        val snapshot = File(sourceDir, "snapshot.db")
        val inputKey = File(sourceDir, "analysis-key.rsa")
        val publicDir = File(external, "bitavida-candidate-audit-public-v1")
        val publicFile = File(publicDir, "receiver-public.der")
        val output = File(external, "bitavida-candidate-audit-envelope-v1")
        var plaintextKey: ByteArray? = null
        try {
            for (directory in listOf(sourceDir, publicDir)) {
                check(Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            for (file in listOf(snapshot, inputKey, publicFile)) {
                check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            check(inputKey.length() in 128..1024 && publicFile.length() in 128..1024)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                check(!Files.exists(File(snapshot.path + suffix).toPath(), LinkOption.NOFOLLOW_LINKS))
            }
            check(sha256File(snapshot) == expectedSnapshotHash)
            val publicBytes = publicFile.readBytes()
            check(sha256(publicBytes) == expectedReceiverHash)
            val receiver = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicBytes)) as RSAPublicKey
            check(receiver.modulus.bitLength() >= 2048)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val originalKey = store.getKey(originalReceiverAlias, null) as? PrivateKey
                ?: error("Missing original snapshot receiver key")
            plaintextKey = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, originalKey)
                doFinal(inputKey.readBytes())
            }
            check(plaintextKey.size == 32)
            val envelope = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.ENCRYPT_MODE, receiver)
                doFinal(plaintextKey)
            }
            try {
                // CREATE_NEW semantics: no prior evidence can be overwritten.
                Files.createDirectory(output.toPath())
                Files.newOutputStream(File(output, "candidate-key.rsa").toPath(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { it.write(envelope) }
                check(sha256File(snapshot) == expectedSnapshotHash)
                val metadata = "snapshot_sha256=$expectedSnapshotHash\nreceiver_sha256=$expectedReceiverHash\n"
                Files.newOutputStream(File(output, "identity.txt").toPath(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { it.write(metadata.toByteArray(Charsets.US_ASCII)) }
            } finally {
                envelope.fill(0)
            }
        } catch (_: Exception) {
            // No raw exception, SQL, file contents, record IDs, key material or paths in output.
            throw AssertionError("Candidate receiver key handoff failed.")
        } finally {
            plaintextKey?.fill(0)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
