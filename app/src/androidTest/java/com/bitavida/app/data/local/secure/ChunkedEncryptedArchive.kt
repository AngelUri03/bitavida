package com.bitavida.app.data.local.secure

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Streaming BVA2 archive: independent bounded GCM frames and an authenticated end marker. */
internal object ChunkedEncryptedArchive {
    private const val FrameSize = 64 * 1024
    private val magic = byteArrayOf(0x42, 0x56, 0x41, 0x32)

    fun encryptTo(output: OutputStream, key: ByteArray): OutputStream {
        val prefix = ByteArray(8).also(SecureRandom()::nextBytes)
        return EncryptingStream(output, key, prefix)
    }

    fun decryptTo(file: File, key: ByteArray, output: OutputStream) {
        DataInputStream(file.inputStream()).use { input ->
            val header = ByteArray(4)
            val prefix = ByteArray(8)
            input.readFully(header)
            input.readFully(prefix)
            check(header.contentEquals(magic))
            var counter = 0
            while (true) {
                val length = input.readInt()
                check(length in 0..FrameSize && counter < Int.MAX_VALUE)
                val ciphertext = ByteArray(length + 16)
                input.readFully(ciphertext)
                val plaintext = crypt(Cipher.DECRYPT_MODE, key, prefix, counter, length, ciphertext)
                try {
                    check(plaintext.size == length)
                    if (length == 0) {
                        check(input.read() == -1)
                        break
                    }
                    output.write(plaintext)
                } finally {
                    plaintext.fill(0)
                    ciphertext.fill(0)
                }
                counter++
            }
            prefix.fill(0)
        }
    }

    private fun crypt(
        mode: Int,
        key: ByteArray,
        prefix: ByteArray,
        counter: Int,
        length: Int,
        payload: ByteArray,
    ): ByteArray {
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter).array()
        val metadata = ByteBuffer.allocate(20).put(magic).put(prefix).putInt(counter).putInt(length).array()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(metadata)
            return cipher.doFinal(payload)
        } finally {
            nonce.fill(0)
            metadata.fill(0)
        }
    }

    private class EncryptingStream(
        output: OutputStream,
        private val key: ByteArray,
        private val prefix: ByteArray,
    ) : OutputStream() {
        private val output = DataOutputStream(output)
        private val buffer = ByteArray(FrameSize)
        private var used = 0
        private var counter = 0
        private var closed = false

        init {
            this.output.write(magic)
            this.output.write(prefix)
        }

        override fun write(value: Int) {
            check(!closed)
            buffer[used++] = value.toByte()
            if (used == buffer.size) writeFrame()
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(!closed)
            check(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            var position = offset
            var remaining = length
            while (remaining > 0) {
                val amount = minOf(remaining, buffer.size - used)
                bytes.copyInto(buffer, used, position, position + amount)
                used += amount
                position += amount
                remaining -= amount
                if (used == buffer.size) writeFrame()
            }
        }

        override fun flush() {
            check(!closed)
            output.flush()
        }

        private fun writeFrame() {
            check(counter < Int.MAX_VALUE)
            val plaintext = buffer.copyOf(used)
            try {
                val ciphertext = crypt(Cipher.ENCRYPT_MODE, key, prefix, counter, used, plaintext)
                try {
                    output.writeInt(used)
                    output.write(ciphertext)
                } finally {
                    ciphertext.fill(0)
                }
            } finally {
                plaintext.fill(0)
                buffer.fill(0)
            }
            counter++
            used = 0
        }

        override fun close() {
            if (closed) return
            try {
                if (used > 0) writeFrame()
                // A zero-length GCM frame authenticates the end of the stream.
                writeFrame()
                output.flush()
            } finally {
                closed = true
                buffer.fill(0)
                prefix.fill(0)
                output.close()
            }
        }
    }
}
