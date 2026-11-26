package com.bitavida.app.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Notes and observations retain their original policy; appointments use a higher minimum quality. */
internal enum class PrivateImagePolicy { STANDARD, CARE_EVENT }
internal class PrivateImageQualityException : IllegalArgumentException()

internal fun encodePrivateImage(source: ByteArray, policy: PrivateImagePolicy = PrivateImagePolicy.STANDARD): ByteArray {
    val eventPhoto = policy == PrivateImagePolicy.CARE_EVENT
    val maxEdge = if (eventPhoto) 2400 else MAX_EDGE
    val maxStoredBytes = if (eventPhoto) com.bitavida.app.domain.repository.CARE_EVENT_MAX_PHOTO_BYTES else MAX_STORED_BYTES
    val decodeLimit = if (eventPhoto) 3000 else maxEdge
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(source, 0, source.size, options)
    require(options.outWidth in 1..100_000 && options.outHeight in 1..100_000)
    require(options.outWidth.toLong() * options.outHeight <= 100_000_000L)
    options.inSampleSize = 1
    while (options.outWidth / options.inSampleSize > decodeLimit ||
        options.outHeight / options.inSampleSize > decodeLimit
    ) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    var bitmap = requireNotNull(BitmapFactory.decodeByteArray(source, 0, source.size, options))
    try {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(source))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) { bitmap.recycle(); bitmap = rotated }
        }
        if (eventPhoto && maxOf(bitmap.width, bitmap.height) > maxEdge) {
            val ratio = maxEdge.toDouble() / maxOf(bitmap.width, bitmap.height)
            val scaled = bitmap.scale(
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
            )
            if (scaled !== bitmap) { bitmap.recycle(); bitmap = scaled }
        }
        var quality = if (eventPhoto) 90 else 85
        while (true) {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
            val encoded = output.toByteArray()
            if (encoded.size <= maxStoredBytes) return encoded
            encoded.fill(0)
            if (eventPhoto && quality <= 80) throw PrivateImageQualityException()
            if (eventPhoto) quality -= 5
            else if (quality > 45) quality -= 20 else {
                val smaller = bitmap.scale(
                    (bitmap.width * 3 / 4).coerceAtLeast(1),
                    (bitmap.height * 3 / 4).coerceAtLeast(1),
                )
                check(smaller !== bitmap)
                bitmap.recycle(); bitmap = smaller
            }
        }
    } finally { bitmap.recycle() }
}

private const val MAX_STORED_BYTES = 512 * 1024
private const val MAX_EDGE = 1280
