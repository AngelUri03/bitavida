package com.bitavida.app.data.appointments

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MAX_CARE_EVENT_PHOTO_BYTES = 16 * 1024 * 1024

/** Fails as soon as the source exceeds the limit; the caller closes the stream. */
internal fun readCareEventPhoto(input: InputStream, maxBytes: Int = MAX_CARE_EVENT_PHOTO_BYTES): ByteArray {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    try {
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(count <= maxBytes - total)
            total += count
            output.write(buffer, 0, count)
        }
        require(total > 0)
        return output.toByteArray()
    } finally {
        buffer.fill(0)
    }
}
