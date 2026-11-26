package com.bitavida.app.ui.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bitavida.app.R
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.domain.model.ObservationTimePrecision
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.FollowUpReportRow
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only generated geometric images and fictional records are used. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class ReportObservationPhotoPdfTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun pdfEmbedsLinkedImagesAndExplicitlyAccountsForUnavailableOrOmitted() = runBlocking {
        val day = LocalDate.of(2026, 10, 7)
        val record = ObservationRecord(
            id = "record-fictional", observationId = "OBS-027", personUnderCareId = "person-fictional",
            occurredOn = day, occurredAt = day.atTime(14, 30).toLocalTime(),
            timePrecision = ObservationTimePrecision.APPROXIMATE, result = ObservationResult.CHANGES,
            answers = emptyMap(), description = "Descripción ficticia de prueba.",
            localPhotoIds = listOf("portrait", "landscape", "unreadable", "portrait"),
            createdAt = day.atTime(15, 0), actorId = "caregiver-fictional",
        )
        val noPhoto = record.copy(id = "without-photo", localPhotoIds = emptyList())
        val rows = listOf(record, noPhoto).map {
            FollowUpReportRow("Observaciones", day.atTime(14, 30), "Observación ficticia",
                "Registro ficticio", "Registro declarado por el cuidador", sourceId = "observation:${it.id}")
        }
        val report = FollowUpReport(day, day, rows, observations = listOf(record, noPhoto),
            snapshotAt = day.atTime(18, 0))
        val portrait = colorPng(140, 260, Color.rgb(220, 10, 10))
        val landscape = colorPng(260, 140, Color.rgb(10, 10, 220))
        val sources = mapOf("portrait" to portrait, "landscape" to landscape,
            "unreadable" to byteArrayOf(1, 2, 3, 4))
        var reads = 0
        val reader: suspend (String, String) -> ByteArray? = { recordId, photoId ->
            assertEquals("record-fictional", recordId)
            reads++
            sources[photoId]?.copyOf()
        }
        try {
            val included = prepareReportObservationPhotos(report, true, reader)
            val result = export(report, included)
            assertEquals(ReportPhotoCounts(3, 2, 1, 0), result.second)
            val rendered = render(result.first)
            assertTrue("La fotografía vertical se dibuja en el PDF", rendered.any { it.redPixels > 500 })
            assertTrue("La fotografía horizontal se dibuja en el PDF", rendered.any { it.bluePixels > 500 })
            val text = rendered.joinToString(" ") { it.text }
            assertTrue(text.contains("Evidencia fotográfica"))
            assertTrue(text.contains("Imagen no disponible o ilegible"))
            assertTrue(text.contains("Registro capturado"))
            assertTrue(text.contains("hora aproximada"))
            assertTrue(result.first.toString(Charsets.ISO_8859_1).contains("/Subtype /Image"))

            reads = 0
            val omitted = prepareReportObservationPhotos(report, false, reader)
            val omittedResult = export(report, omitted)
            assertEquals(0, reads)
            assertEquals(ReportPhotoCounts(3, 0, 0, 3), omittedResult.second)
            val omittedPages = render(omittedResult.first)
            assertTrue(omittedPages.joinToString(" ") { it.text }.contains("Fotografías omitidas de esta exportación"))
            assertTrue(omittedPages.all { it.redPixels <= 500 && it.bluePixels <= 500 })
            assertEquals(0, reads)
        } finally { sources.values.forEach { it.fill(0) } }
    }

    @Test fun storageFailureIsNotMisreportedAsMissingPhoto() = runBlocking {
        val day = LocalDate.of(2026, 10, 7)
        val record = ObservationRecord("record-fictional", "OBS-027", "person-fictional", day,
            null, ObservationTimePrecision.UNKNOWN, ObservationResult.CHANGES, emptyMap(), "",
            localPhotoIds = listOf("photo"), createdAt = day.atTime(15, 0), actorId = "caregiver-fictional")
        val report = FollowUpReport(day, day, emptyList(), observations = listOf(record), snapshotAt = day.atTime(18, 0))
        var failed = false
        try {
            prepareReportObservationPhotos(report, true) { _, _ -> throw IllegalStateException("fictional failure") }
        } catch (_: IllegalStateException) { failed = true }
        assertTrue("Un error de almacenamiento debe interrumpir la preparación", failed)
    }

    @Test fun imageThatDisappearsAfterPreparationIsNotCountedAsIncluded() = runBlocking {
        val day = LocalDate.of(2026, 10, 7)
        val record = ObservationRecord("record-fictional", "OBS-027", "person-fictional", day,
            null, ObservationTimePrecision.UNKNOWN, ObservationResult.CHANGES, emptyMap(), "",
            localPhotoIds = listOf("photo"), createdAt = day.atTime(15, 0), actorId = "caregiver-fictional")
        val report = FollowUpReport(day, day,
            listOf(FollowUpReportRow("Observaciones", day.atStartOfDay(), "Observación ficticia", "Registro ficticio",
                "Registro declarado por el cuidador", sourceId = "observation:${record.id}")),
            observations = listOf(record), snapshotAt = day.atTime(18, 0))
        val png = colorPng(120, 180, Color.RED)
        try {
            var reads = 0
            val photos = prepareReportObservationPhotos(report, true) { _, _ ->
                reads++
                if (reads == 1) png.copyOf() else null
            }
            val result = export(report, photos)
            assertEquals(ReportPhotoCounts(1, 0, 1, 0), result.second)
            assertTrue(render(result.first).joinToString(" ") { it.text }.contains("Imagen no disponible o ilegible"))
        } finally { png.fill(0) }
    }

    private fun colorPng(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun export(report: FollowUpReport, photos: ReportObservationPhotos): Pair<ByteArray, ReportPhotoCounts> {
        val regular = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_regular))
        val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.kodchasan_bold))
        var counts: ReportPhotoCounts? = null
        val output = ByteArrayOutputStream()
        writeFollowUpPdf(output, report, "Cuidadora ficticia", "Paciente ficticio", regular, bold,
            report.end, photos = photos, onPhotoCounts = { counts = it })
        return output.toByteArray() to requireNotNull(counts)
    }

    private data class Page(val text: String, val redPixels: Int, val bluePixels: Int)

    private fun render(bytes: ByteArray): List<Page> {
        val file = File.createTempFile("photo-pdf-fictional-", ".pdf", context.cacheDir)
        return try {
            file.writeBytes(bytes)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(descriptor).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixels = IntArray(page.width * page.height)
                            bitmap.getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
                            Page(page.textContents.joinToString(" ") { it.text },
                                pixels.count { Color.red(it) > 170 && Color.green(it) < 70 && Color.blue(it) < 70 },
                                pixels.count { Color.blue(it) > 170 && Color.red(it) < 70 && Color.green(it) < 70 })
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally { file.delete() }
    }
}
