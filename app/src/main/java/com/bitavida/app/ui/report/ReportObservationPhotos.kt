package com.bitavida.app.ui.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.bitavida.app.domain.usecase.FollowUpReport
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ReportPhotoKey(val recordId: String, val photoId: String)
internal data class ReportPhotoDimensions(val width: Int, val height: Int)

/** Holds only availability and dimensions; no image bytes are retained between reads. */
internal class ReportObservationPhotos(
    val included: Boolean,
    val dimensions: Map<ReportPhotoKey, ReportPhotoDimensions?>,
    private val reader: suspend (String, String) -> ByteArray?,
) {
    suspend fun read(key: ReportPhotoKey): ByteArray? = reader(key.recordId, key.photoId)

    companion object {
        fun omitted() = ReportObservationPhotos(false, emptyMap()) { _, _ -> null }
    }
}

/** Only IDs from the report's version-at-cutoff snapshots are considered. */
internal suspend fun prepareReportObservationPhotos(
    report: FollowUpReport,
    included: Boolean,
    reader: suspend (String, String) -> ByteArray?,
): ReportObservationPhotos {
    if (!included) return ReportObservationPhotos.omitted()
    return withContext(Dispatchers.IO) {
        val dimensions = linkedMapOf<ReportPhotoKey, ReportPhotoDimensions?>()
        report.observations.forEach { record ->
            record.localPhotoIds.distinct().forEach { photoId ->
                val key = ReportPhotoKey(record.id, photoId)
                val bytes = reader(record.id, photoId)
                dimensions[key] = if (bytes == null) null else try {
                    val bitmap = try { decodeReportPhoto(bytes) } catch (_: Exception) { null }
                    bitmap?.let { ReportPhotoDimensions(it.width, it.height).also { _ -> it.recycle() } }
                } finally { bytes.fill(0) }
            }
        }
        ReportObservationPhotos(true, dimensions, reader)
    }
}

/** Bounded decoding for old or malformed images; orientation is applied, never filters. */
internal fun decodeReportPhoto(bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..100_000 || bounds.outHeight !in 1..100_000 ||
        bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) return null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val orientation = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
    val transform = Matrix().apply {
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
    if (!transform.isIdentity) {
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
        if (rotated !== bitmap) { bitmap.recycle(); bitmap = rotated }
    }
    return bitmap
}
