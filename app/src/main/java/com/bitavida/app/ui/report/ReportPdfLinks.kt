package com.bitavida.app.ui.report

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/** A section destination in PDF points, measured from the top of its page. */
internal data class FollowUpPdfBookmark(val title: String, val targetPage: Int, val targetY: Float)

/**
 * Adds GoTo annotations to the unencrypted, classic-xref PDF produced by Android PdfDocument.
 *
 * This is deliberately not a general PDF parser. We validate the original xref, catalog and
 * page tree before emitting any bytes; unknown structures fail rather than losing annotations
 * or generating an ambiguous document. The original document is never changed on disk.
 */
internal fun writeReportPdfWithEmbeddedLinks(
    source: ByteArray,
    links: List<FollowUpPdfLink>,
    output: OutputStream,
    bookmarks: List<FollowUpPdfBookmark> = emptyList(),
) {
    if (links.isEmpty() && bookmarks.isEmpty()) {
        output.write(source)
        return
    }
    val text = source.toString(StandardCharsets.ISO_8859_1)
    require(text.startsWith("%PDF-1.")) { "Formato de PDF no compatible" }
    val startMatch = Regex("""startxref\s+(\d+)\s+%%EOF\s*$""").find(text)
        ?: error("No se encontró el índice del PDF")
    val xrefOffset = startMatch.groupValues[1].toIntOrNull() ?: error("Índice del PDF inválido")
    require(xrefOffset in 0 until startMatch.range.first && text.startsWith("xref", xrefOffset)) {
        "El PDF no tiene un índice clásico compatible"
    }
    val trailerOffset = text.indexOf("trailer", xrefOffset + 4)
    require(trailerOffset in (xrefOffset + 4) until startMatch.range.first) { "Falta el cierre del índice" }
    val xrefLines = text.substring(xrefOffset + 4, trailerOffset).lineSequence()
        .map(String::trim).filter(String::isNotEmpty).toList()
    require(xrefLines.isNotEmpty()) { "Índice vacío" }
    val xrefHeader = Regex("""0\s+(\d+)""").matchEntire(xrefLines.first())
        ?: error("Subsecciones del PDF no compatibles")
    val size = xrefHeader.groupValues[1].toIntOrNull() ?: error("Tamaño del índice inválido")
    require(size >= 2 && xrefLines.size == size + 1) { "Índice de objetos incompleto" }
    val xrefEntry = Regex("""(\d{10})\s+(\d{5})\s+([nf])""")
    val offsets = IntArray(size)
    xrefLines.drop(1).forEachIndexed { id, entry ->
        val match = xrefEntry.matchEntire(entry) ?: error("Entrada de índice inválida")
        if (id == 0) {
            require(match.groupValues[3] == "f") { "Objeto cero inválido" }
        } else {
            require(match.groupValues[2] == "00000" && match.groupValues[3] == "n") {
                "Generación de objeto no compatible"
            }
            offsets[id] = match.groupValues[1].toIntOrNull() ?: error("Posición de objeto inválida")
            require(offsets[id] in 0 until xrefOffset) { "Posición de objeto fuera del PDF" }
            // Skia writes every indirect-object header in this exact ASCII form.
            // Verify streams by their xref offsets without inspecting their contents.
            require(text.startsWith("$id 0 obj\n", offsets[id])) { "El índice no apunta al objeto esperado" }
        }
    }
    val trailer = text.substring(trailerOffset + "trailer".length, startMatch.range.first).trim()
    require(trailer.startsWith("<<") && trailer.endsWith(">>") &&
        !Regex("""/Encrypt\b|/Prev\b|/XRefStm\b""").containsMatchIn(trailer)) {
        "Tráiler del PDF no compatible"
    }
    val sizeMatch = Regex("""/Size\s+(\d+)\b""").findAll(trailer).toList()
    require(sizeMatch.size == 1 && sizeMatch.single().groupValues[1].toIntOrNull() == size) {
        "Tamaño de tráiler inconsistente"
    }
    val root = Regex("""/Root\s+(\d+)\s+0\s+R\b""").find(trailer)
        ?.groupValues?.get(1)?.toIntOrNull() ?: error("Falta el catálogo del PDF")

    fun objectDictionary(id: Int): String {
        require(id in 1 until size) { "Referencia fuera del índice" }
        val offset = offsets[id]
        val header = Regex("""\s*$id\s+0\s+obj\b""").find(text, offset)
        require(header != null && header.range.first == offset) { "El índice no apunta al objeto esperado" }
        val end = text.indexOf("endobj", header.range.last + 1)
        require(end in (header.range.last + 1) until xrefOffset) { "Objeto incompleto" }
        val body = text.substring(header.range.last + 1, end).trim()
        require(body.startsWith("<<") && body.endsWith(">>") && !Regex("""\bstream\b""").containsMatchIn(body)) {
            "Diccionario de página no compatible"
        }
        return body
    }
    val catalog = objectDictionary(root)
    require(Regex("""/Type\s*/Catalog\b""").containsMatchIn(catalog)) { "Catálogo inválido" }
    val pageTreeRoot = Regex("""/Pages\s+(\d+)\s+0\s+R\b""").find(catalog)
        ?.groupValues?.get(1)?.toIntOrNull() ?: error("Falta el árbol de páginas")
    val pageIds = mutableListOf<Int>()
    val pageBodies = mutableMapOf<Int, String>()
    val seen = mutableSetOf<Int>()
    fun visit(id: Int) {
        require(seen.add(id)) { "Árbol de páginas cíclico" }
        val body = objectDictionary(id)
        when {
            Regex("""/Type\s*/Pages\b""").containsMatchIn(body) -> {
                val kids = Regex("""(?s)/Kids\s*\[([^]]*)]""").find(body)?.groupValues?.get(1)
                    ?: error("Lista de páginas no compatible")
                val refs = Regex("""(\d+)\s+0\s+R\b""").findAll(kids).toList()
                require(refs.isNotEmpty() &&
                    Regex("""(\d+)\s+0\s+R\b""").replace(kids, "").isBlank()) {
                    "Referencias de páginas no compatibles"
                }
                refs.forEach { visit(it.groupValues[1].toInt()) }
            }
            Regex("""/Type\s*/Page\b""").containsMatchIn(body) -> {
                require(!Regex("""/Annots\b""").containsMatchIn(body)) {
                    "No se pueden reemplazar anotaciones existentes"
                }
                pageIds += id
                pageBodies[id] = body
            }
            else -> error("Nodo de página inválido")
        }
    }
    visit(pageTreeRoot)
    require(pageIds.isNotEmpty()) { "Documento sin páginas" }
    val byPage = links.groupBy { it.sourcePage }
    bookmarks.forEach { bookmark ->
        require(bookmark.title.isNotBlank() && bookmark.targetPage in pageIds.indices &&
            bookmark.targetY.isFinite() && bookmark.targetY in 0f..842f) { "Marcador inválido" }
    }
    if (bookmarks.isNotEmpty()) require(!Regex("""/Outlines\b|/PageMode\b""").containsMatchIn(catalog)) {
        "No se pueden reemplazar marcadores existentes"
    }
    links.forEach { link ->
        require(link.sourcePage in pageIds.indices && link.targetPage in pageIds.indices) {
            "Destino de enlace fuera del documento"
        }
        require(listOf(link.left, link.top, link.right, link.bottom).all { it.isFinite() } &&
            link.left >= 0f && link.top >= 0f && link.right <= 595f && link.bottom <= 842f &&
            link.left < link.right && link.top < link.bottom && link.targetY.isFinite() &&
            link.targetY in 0f..842f) { "Región de enlace inválida" }
    }

    val update = ByteArrayOutputStream()
    val newOffsets = sortedMapOf<Int, Long>()
    fun append(value: String) { update.write(value.toByteArray(StandardCharsets.ISO_8859_1)) }
    fun beginObject(id: Int) {
        newOffsets[id] = source.size.toLong() + update.size()
        require(newOffsets.getValue(id) <= 9_999_999_999L) { "Documento demasiado grande para índice clásico" }
        append("$id 0 obj\n")
    }
    append("\n")
    var nextId = size
    fun number(value: Float) = String.format(Locale.ROOT, "%.3f", value)
    if (bookmarks.isNotEmpty()) {
        val outlineRoot = nextId++
        val outlineItems = bookmarks.map { nextId++ }
        beginObject(root)
        append(catalog.dropLast(2))
        append("\n/Outlines $outlineRoot 0 R\n/PageMode /UseOutlines\n>>\nendobj\n")
        beginObject(outlineRoot)
        append("<< /Type /Outlines /First ${outlineItems.first()} 0 R /Last ${outlineItems.last()} 0 R /Count ${outlineItems.size} >>\nendobj\n")
        bookmarks.forEachIndexed { index, bookmark ->
            beginObject(outlineItems[index])
            val unicode = ("\uFEFF" + bookmark.title).toByteArray(StandardCharsets.UTF_16BE)
                .joinToString("") { "%02X".format(Locale.ROOT, it.toInt() and 0xff) }
            append("<< /Title <$unicode> /Parent $outlineRoot 0 R ")
            if (index > 0) append("/Prev ${outlineItems[index - 1]} 0 R ")
            if (index < outlineItems.lastIndex) append("/Next ${outlineItems[index + 1]} 0 R ")
            append("/Dest [${pageIds[bookmark.targetPage]} 0 R /XYZ null ${number(842f - bookmark.targetY)} null] >>\nendobj\n")
        }
    }
    byPage.toSortedMap().forEach { (pageIndex, pageLinks) ->
        val pageId = pageIds[pageIndex]
        val annotationIds = pageLinks.map { nextId++ }
        beginObject(pageId)
        append(pageBodies.getValue(pageId).dropLast(2))
        append("\n/Annots [")
        annotationIds.forEach { append(" $it 0 R") }
        append(" ]\n>>\nendobj\n")
        pageLinks.zip(annotationIds).forEach { (link, annotationId) ->
            beginObject(annotationId)
            val rect = listOf(link.left, 842f - link.bottom, link.right, 842f - link.top)
                .joinToString(" ") { number(it) }
            val destinationId = pageIds[link.targetPage]
            append("<< /Type /Annot /Subtype /Link /Rect [$rect] /Border [0 0 0] " +
                "/A << /S /GoTo /D [$destinationId 0 R /XYZ null ${number(842f - link.targetY)} null] >> >>\nendobj\n")
        }
    }
    val newXrefOffset = source.size.toLong() + update.size()
    require(newXrefOffset <= 9_999_999_999L) { "Documento demasiado grande para índice clásico" }
    append("xref\n")
    newOffsets.forEach { (id, offset) ->
        append("$id 1\n")
        append(String.format(Locale.ROOT, "%010d 00000 n \n", offset))
    }
    val newTrailer = trailer.replaceRange(sizeMatch.single().range, "/Size $nextId").dropLast(2) +
        "\n/Prev $xrefOffset\n>>"
    append("trailer\n$newTrailer\nstartxref\n$newXrefOffset\n%%EOF\n")
    // All parsing and offset calculations must finish before writing to the chosen destination.
    output.write(source)
    update.writeTo(output)
}
