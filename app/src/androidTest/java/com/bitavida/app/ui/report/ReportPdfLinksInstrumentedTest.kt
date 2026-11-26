package com.bitavida.app.ui.report

import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the actual PDF bytes with Android's PDF renderer; contains no patient data. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class ReportPdfLinksInstrumentedTest {
    @Test fun exported_document_contains_real_goto_annotations_with_matching_destinations() {
        val source = ByteArrayOutputStream()
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                page.canvas.drawColor(android.graphics.Color.WHITE)
                pdf.finishPage(page)
            }
            pdf.writeTo(source)
        } finally { pdf.close() }
        val output = ByteArrayOutputStream()
        writeReportPdfWithEmbeddedLinks(source.toByteArray(), listOf(
            FollowUpPdfLink(0, 40f, 90f, 160f, 112f, 2),
            FollowUpPdfLink(2, 40f, 40f, 130f, 65f, 0),
        ), output)
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = java.io.File.createTempFile("synthetic-pdf-links-", ".pdf", cache)
        try {
            file.writeBytes(output.toByteArray())
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd).use { renderer ->
                assertEquals(3, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val links = page.gotoLinks
                    assertEquals(1, links.size)
                    assertEquals(2, links.single().destination.pageNumber)
                    assertTrue(links.single().bounds.any { it.contains(100f, 100f) })
                }
                renderer.openPage(2).use { page ->
                    val links = page.gotoLinks
                    assertEquals(1, links.size)
                    assertEquals(0, links.single().destination.pageNumber)
                    assertTrue(links.single().bounds.any { it.contains(80f, 50f) })
                }
            }
        } finally {
            file.delete()
        }
    }
}
