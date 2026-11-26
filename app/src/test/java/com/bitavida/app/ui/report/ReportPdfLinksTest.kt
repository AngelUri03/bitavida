package com.bitavida.app.ui.report

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ReportPdfLinksTest {
    /** A classic-xref PDF-shaped fixture; fake stream contents must never be parsed as objects. */
    private fun sample(existingAnnotations: Boolean = false): ByteArray {
        val stream = "This is a stream with fake 99 0 obj and endobj markers."
        val page1 = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 5 0 R" +
            (if (existingAnnotations) " /Annots [6 0 R]" else "") + " >>"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 2 /Kids [3 0 R 4 0 R] >>",
            page1,
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 5 0 R >>",
            "<< /Length ${stream.length} >>\nstream\n${stream}\nendstream",
            "<< /Type /Annot /Subtype /Text /Rect [0 0 1 1] >>",
        )
        val out = ByteArrayOutputStream()
        fun write(text: String) { out.write(text.toByteArray(StandardCharsets.ISO_8859_1)) }
        write("%PDF-1.4\n%test\n")
        val offsets = objects.mapIndexed { index, obj ->
            val offset = out.size()
            write("${index + 1} 0 obj\n$obj\nendobj\n")
            offset
        }
        val xref = out.size()
        write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    @Test fun adds_real_destinations_and_preserves_original_bytes() {
        val source = sample()
        val output = ByteArrayOutputStream()
        writeReportPdfWithEmbeddedLinks(source, listOf(
            FollowUpPdfLink(0, 40f, 100f, 120f, 120f, 1),
            FollowUpPdfLink(1, 45f, 20f, 95f, 40f, 0),
        ), output)
        val result = output.toByteArray()
        assertArrayEquals(source, result.copyOfRange(0, source.size))
        val update = result.copyOfRange(source.size, result.size).toString(StandardCharsets.ISO_8859_1)
        assertTrue(update.contains("/Annots [ 7 0 R ]"))
        assertTrue(update.contains("/Annots [ 8 0 R ]"))
        assertTrue(update.contains("/Rect [40.000 722.000 120.000 742.000]"))
        assertTrue(update.contains("/D [4 0 R /XYZ null 842.000 null]"))
        assertTrue(update.contains("/D [3 0 R /XYZ null 842.000 null]"))
        assertTrue(update.contains("/Size 9"))
        assertTrue(update.contains("/Prev "))
        val newXref = Regex("""startxref\s+(\d+)\s+%%EOF""").findAll(update).last().groupValues[1].toInt()
        assertTrue(result.toString(StandardCharsets.ISO_8859_1).startsWith("xref\n", newXref))
        val entries = Regex("""(?m)^(\d+) 1\n(\d{10}) 00000 n""").findAll(update).toList()
        assertEquals(4, entries.size)
        val text = result.toString(StandardCharsets.ISO_8859_1)
        entries.forEach { assertTrue(text.startsWith("${it.groupValues[1]} 0 obj", it.groupValues[2].toInt())) }
    }

    @Test fun writes_bookmarks_and_links_to_exact_section_position() {
        val output = ByteArrayOutputStream()
        writeReportPdfWithEmbeddedLinks(
            sample(),
            listOf(FollowUpPdfLink(0, 40f, 100f, 120f, 120f, 1, 230f)),
            output,
            listOf(FollowUpPdfBookmark("Mediciones y atención", 1, 230f)),
        )
        val result = output.toString(StandardCharsets.ISO_8859_1.name())
        assertTrue(result.contains("/Outlines "))
        assertTrue(result.contains("/PageMode /UseOutlines"))
        assertTrue(result.contains("/Type /Outlines"))
        assertTrue(result.contains("/Dest [4 0 R /XYZ null 612.000 null]"))
        assertTrue(result.contains("/D [4 0 R /XYZ null 612.000 null]"))
        val title = ("\uFEFF" + "Mediciones y atención").toByteArray(StandardCharsets.UTF_16BE)
            .joinToString("") { "%02X".format(Locale.ROOT, it.toInt() and 0xff) }
        assertTrue(result.contains("/Title <" + title + ">"))
    }

    @Test fun rejects_unsupported_or_corrupt_input_without_writing_output() {
        val valid = sample()
        val altered = valid.toString(StandardCharsets.ISO_8859_1)
            .replace("/Root 1 0 R", "/Root 9 0 R").toByteArray(StandardCharsets.ISO_8859_1)
        val link = FollowUpPdfLink(0, 1f, 1f, 20f, 20f, 1)
        listOf(altered, sample(existingAnnotations = true), "%PDF-1.7\ninvalid".toByteArray()).forEach { input ->
            val output = ByteArrayOutputStream()
            assertThrows(IllegalArgumentException::class.java) {
                try { writeReportPdfWithEmbeddedLinks(input, listOf(link), output) }
                catch (error: IllegalStateException) { throw IllegalArgumentException(error) }
            }
            assertEquals(0, output.size())
        }
    }

    @Test fun rejects_a_corrupted_object_offset_without_writing() {
        val source = sample().toString(StandardCharsets.ISO_8859_1)
        val xref = source.lastIndexOf("\nxref\n") + 1
        val position = source.indexOf("0000000000 65535 f", xref)
        val nextEntry = source.indexOf('\n', position) + 1
        val corrupt = source.replaceRange(nextEntry, nextEntry + 10, "0000000001")
            .toByteArray(StandardCharsets.ISO_8859_1)
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            writeReportPdfWithEmbeddedLinks(corrupt, listOf(FollowUpPdfLink(0, 1f, 1f, 20f, 20f, 1)), output)
        }
        assertEquals(0, output.size())
    }

    @Test fun rejects_corrupted_stream_offset_without_reading_stream() {
        val source = sample().toString(StandardCharsets.ISO_8859_1)
        val xref = source.lastIndexOf("\nxref\n") + 1
        val entries = Regex("""(?m)^\d{10} 00000 n $""").findAll(source.substring(xref)).toList()
        val streamEntry = xref + entries[4].range.first // object 5 is a content stream
        val corrupt = source.replaceRange(streamEntry, streamEntry + 10, "0000000001")
            .toByteArray(StandardCharsets.ISO_8859_1)
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            writeReportPdfWithEmbeddedLinks(corrupt, listOf(FollowUpPdfLink(0, 1f, 1f, 20f, 20f, 1)), output)
        }
        assertEquals(0, output.size())
    }

    @Test fun rejects_invalid_rectangle_and_target_before_writing() {
        val source = sample()
        val invalid = listOf(
            FollowUpPdfLink(0, 2f, 10f, 1f, 20f, 1),
            FollowUpPdfLink(0, 1f, 1f, 20f, 20f, 7),
            FollowUpPdfLink(0, Float.NaN, 1f, 20f, 20f, 1),
        )
        invalid.forEach { link ->
            val output = ByteArrayOutputStream()
            assertThrows(IllegalArgumentException::class.java) {
                writeReportPdfWithEmbeddedLinks(source, listOf(link), output)
            }
            assertEquals(0, output.size())
        }
    }

    @Test fun without_links_keeps_original_unchanged() {
        val source = sample()
        val output = ByteArrayOutputStream()
        writeReportPdfWithEmbeddedLinks(source, emptyList(), output)
        assertArrayEquals(source, output.toByteArray())
    }
}
