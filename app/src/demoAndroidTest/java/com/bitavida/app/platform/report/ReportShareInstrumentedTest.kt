package com.bitavida.app.platform.report

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only generated content in the separately installed Demo sandbox. */
@RunWith(AndroidJUnit4::class)
class ReportShareInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun chooserGrantsOnlyReadAndKeepsSeparateDocumentsReadable() = runBlocking {
        assertEquals("com.bitavida.app.demo", context.packageName)
        val name = reportShareFileName(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "Persona Ficticia")
        fun document(output: java.io.OutputStream, pages: Int) {
            val pdf = PdfDocument()
            try {
                repeat(pages) { index ->
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(100, 100, index + 1).create())
                    pdf.finishPage(page)
                }
                pdf.writeTo(output)
            } finally { pdf.close() }
        }
        val first = prepareReportShare(context, name) { document(it, 1) }
        val second = prepareReportShare(context, name) { document(it, 2) }
        try {
            assertNotEquals(first.uri, second.uri)
            assertEquals("content", first.uri.scheme)
            assertEquals("${context.packageName}.reports", first.uri.authority)
            context.contentResolver.query(first.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(name, it.getString(0))
            }
            context.contentResolver.openFileDescriptor(first.uri, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { assertEquals(1, it.pageCount) }
            }
            context.contentResolver.openFileDescriptor(second.uri, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { assertEquals(2, it.pageCount) }
            }
            var launched: Intent? = null
            val capture = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) { launched = intent }
            }
            openReportShareChooser(capture, first)
            val chooser = requireNotNull(launched)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION")
            val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("application/pdf", send.type)
            @Suppress("DEPRECATION")
            val sentUri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            assertEquals(first.uri, sentUri)
            assertEquals(first.uri, send.clipData!!.getItemAt(0).uri)
            assertEquals(first.uri, chooser.clipData!!.getItemAt(0).uri)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, send.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        } finally {
            context.contentResolver.delete(first.uri, null, null)
            context.contentResolver.delete(second.uri, null, null)
        }
    }
}
