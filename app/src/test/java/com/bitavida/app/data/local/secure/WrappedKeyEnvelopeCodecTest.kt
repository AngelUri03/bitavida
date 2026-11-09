package com.bitavida.app.data.local.secure

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WrappedKeyEnvelopeCodecTest {
    @Test
    fun encodeAndDecodePreserveEnvelope() {
        val envelope = WrappedKeyEnvelope(
            initializationVector = ByteArray(12) { it.toByte() },
            ciphertext = ByteArray(48) { (it * 3).toByte() },
        )

        val decoded = WrappedKeyEnvelopeCodec.decode(
            WrappedKeyEnvelopeCodec.encode(envelope),
        )

        assertArrayEquals(envelope.initializationVector, decoded.initializationVector)
        assertArrayEquals(envelope.ciphertext, decoded.ciphertext)
    }

    @Test
    fun decodeRejectsTruncatedEnvelope() {
        val encoded = WrappedKeyEnvelopeCodec.encode(
            WrappedKeyEnvelope(ByteArray(12), ByteArray(48)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            WrappedKeyEnvelopeCodec.decode(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test
    fun decodeRejectsUnknownVersionWithoutExposingContent() {
        val encoded = WrappedKeyEnvelopeCodec.encode(
            WrappedKeyEnvelope(ByteArray(12), ByteArray(48)),
        )
        encoded[Int.SIZE_BYTES] = 2

        assertThrows(IllegalArgumentException::class.java) {
            WrappedKeyEnvelopeCodec.decode(encoded)
        }
    }
}
