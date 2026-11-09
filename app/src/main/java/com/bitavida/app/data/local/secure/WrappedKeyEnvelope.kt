package com.bitavida.app.data.local.secure

import java.nio.ByteBuffer

internal class WrappedKeyEnvelope(
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
)

internal object WrappedKeyEnvelopeCodec {
    private const val Magic = 0x42564B31
    private const val Version: Byte = 1
    private const val HeaderSize = Int.SIZE_BYTES + Byte.SIZE_BYTES + Byte.SIZE_BYTES + Int.SIZE_BYTES
    private const val MinimumIvBytes = 12
    private const val MaximumIvBytes = 16
    private const val MinimumCiphertextBytes = 16
    private const val MaximumEnvelopeBytes = 8 * 1024

    fun encode(envelope: WrappedKeyEnvelope): ByteArray {
        require(envelope.initializationVector.size in MinimumIvBytes..MaximumIvBytes)
        require(envelope.ciphertext.size >= MinimumCiphertextBytes)
        val totalSize = HeaderSize + envelope.initializationVector.size + envelope.ciphertext.size
        require(totalSize <= MaximumEnvelopeBytes)
        return ByteBuffer.allocate(totalSize)
            .putInt(Magic)
            .put(Version)
            .put(envelope.initializationVector.size.toByte())
            .putInt(envelope.ciphertext.size)
            .put(envelope.initializationVector)
            .put(envelope.ciphertext)
            .array()
    }

    fun decode(bytes: ByteArray): WrappedKeyEnvelope {
        require(bytes.size in (HeaderSize + MinimumIvBytes + MinimumCiphertextBytes)..MaximumEnvelopeBytes)
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.int == Magic)
        require(buffer.get() == Version)
        val ivSize = buffer.get().toInt() and 0xFF
        val ciphertextSize = buffer.int
        require(ivSize in MinimumIvBytes..MaximumIvBytes)
        require(ciphertextSize >= MinimumCiphertextBytes)
        require(buffer.remaining() == ivSize + ciphertextSize)
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(ciphertextSize).also(buffer::get)
        return WrappedKeyEnvelope(iv, ciphertext)
    }
}
