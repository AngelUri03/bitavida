package com.bitavida.app.data.local.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class WrappedDatabaseKeyStore(
    context: Context,
    private val config: SecureStorageConfig,
) {
    private val wrappedKeyFile = File(context.noBackupFilesDir, config.wrappedKeyFileName)
    private val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }

    fun loadOrCreate(databaseExists: Boolean): ByteArray {
        if (wrappedKeyFile.exists()) {
            if (!databaseExists) {
                throw InconsistentSecureStorageException(
                    "A wrapped key exists without its encrypted database.",
                )
            }
            return unwrapExistingKey()
        }
        if (databaseExists) {
            throw InconsistentSecureStorageException(
                "The encrypted database exists without its wrapped key.",
            )
        }
        if (hasWrappingKey()) {
            throw InconsistentSecureStorageException(
                "A wrapping key exists without matching private storage.",
            )
        }
        return createWrappedKey()
    }

    private fun unwrapExistingKey(): ByteArray {
        val wrappingKey = loadWrappingKey()
        return try {
            val encoded = wrappedKeyFile.readBytes()
            val envelope = try {
                WrappedKeyEnvelopeCodec.decode(encoded)
            } finally {
                encoded.fill(0)
            }
            try {
                val cipher = Cipher.getInstance(AesGcmTransformation)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    wrappingKey,
                    GCMParameterSpec(GcmTagBits, envelope.initializationVector),
                )
                cipher.doFinal(envelope.ciphertext).also { databaseKey ->
                    if (databaseKey.size != DatabaseKeyBytes) {
                        databaseKey.fill(0)
                        throw WrappedDatabaseKeyException()
                    }
                }
            } finally {
                envelope.initializationVector.fill(0)
                envelope.ciphertext.fill(0)
            }
        } catch (error: WrappedDatabaseKeyException) {
            throw error
        } catch (error: IOException) {
            throw WrappedDatabaseKeyException(error)
        } catch (error: IllegalArgumentException) {
            throw WrappedDatabaseKeyException(error)
        } catch (error: GeneralSecurityException) {
            throw WrappedDatabaseKeyException(error)
        }
    }

    private fun createWrappedKey(): ByteArray {
        val databaseKey = ByteArray(DatabaseKeyBytes)
        SecureRandom().nextBytes(databaseKey)
        var wrappingKeyCreated = false
        try {
            val wrappingKey = generateWrappingKey()
            wrappingKeyCreated = true
            val cipher = Cipher.getInstance(AesGcmTransformation)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
            val initializationVector = cipher.iv
            val ciphertext = cipher.doFinal(databaseKey)
            val encoded = try {
                WrappedKeyEnvelopeCodec.encode(
                    WrappedKeyEnvelope(
                        initializationVector = initializationVector,
                        ciphertext = ciphertext,
                    ),
                )
            } finally {
                initializationVector.fill(0)
                ciphertext.fill(0)
            }
            try {
                writeAtomically(encoded)
            } finally {
                encoded.fill(0)
            }
            return databaseKey
        } catch (error: Exception) {
            databaseKey.fill(0)
            if (wrappingKeyCreated && !wrappedKeyFile.exists()) {
                runCatching { keyStore.deleteEntry(config.wrappingKeyAlias) }
            }
            when (error) {
                is SecurePersistenceException -> throw error
                is IOException, is GeneralSecurityException, is IllegalArgumentException ->
                    throw WrappedDatabaseKeyException(error)
                else -> throw error
            }
        }
    }

    private fun loadWrappingKey(): SecretKey {
        if (!hasWrappingKey()) {
            throw WrappingKeyUnavailableException()
        }
        return try {
            keyStore.getKey(config.wrappingKeyAlias, null) as? SecretKey
                ?: throw WrappingKeyUnavailableException()
        } catch (error: GeneralSecurityException) {
            throw WrappingKeyUnavailableException(error)
        }
    }

    private fun hasWrappingKey(): Boolean =
        try {
            keyStore.containsAlias(config.wrappingKeyAlias)
        } catch (error: GeneralSecurityException) {
            throw WrappingKeyUnavailableException(error)
        }

    private fun generateWrappingKey(): SecretKey {
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        config.wrappingKeyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }.generateKey()
        } catch (error: GeneralSecurityException) {
            throw WrappingKeyUnavailableException(error)
        }
    }

    private fun writeAtomically(encoded: ByteArray) {
        val atomicFile = AtomicFile(wrappedKeyFile)
        val output = atomicFile.startWrite()
        try {
            output.write(encoded)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: IOException) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val AesGcmTransformation = "AES/GCM/NoPadding"
        private const val DatabaseKeyBytes = 32
        private const val GcmTagBits = 128
    }
}
