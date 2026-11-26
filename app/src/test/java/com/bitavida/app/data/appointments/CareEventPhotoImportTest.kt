package com.bitavida.app.data.appointments

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CareEventPhotoImportTest {
    @Test fun acceptsExactLimit() {
        val image = ByteArray(10) { it.toByte() }
        assertArrayEquals(image, readCareEventPhoto(ByteArrayInputStream(image), 10))
    }

    @Test fun rejectsAnOversizedSource() {
        assertThrows(IllegalArgumentException::class.java) {
            readCareEventPhoto(ByteArrayInputStream(ByteArray(11)), 10)
        }
    }

    @Test fun rejectsEmptySourceAndInvalidLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            readCareEventPhoto(ByteArrayInputStream(byteArrayOf()), 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            readCareEventPhoto(ByteArrayInputStream(byteArrayOf(1)), 0)
        }
    }
}
