package com.bitavida.app.ui.report

import android.graphics.Paint
import android.graphics.RectF
import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationTimePrecision
import kotlinx.coroutines.runBlocking
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.ui.theme.LightExtendedColors
import com.bitavida.app.ui.theme.LightOnSurface
import com.bitavida.app.ui.theme.LightOnSurfaceVariant
import com.bitavida.app.ui.theme.LightOutline
import com.bitavida.app.ui.theme.LightSurfaceVariant
import com.bitavida.app.ui.theme.LightError
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Region in PDF points, measured from the top-left; page indices are zero based. */
internal data class FollowUpPdfLink(
    val sourcePage: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val targetPage: Int,
    val targetY: Float = 0f,
)

/**
 * Produces the same immutable document shown by preview, without saving or modifying source data.
 * A measuring pass determines actual page references before the final document is written.
 */
internal fun writeFollowUpPdf(
    output: OutputStream,
    report: FollowUpReport,
    caregiver: String,
    patient: String,
    regular: Typeface,
    bold: Typeface,
    generatedOn: LocalDate = LocalDate.now(),
    onPageLinks: ((List<FollowUpPdfLink>) -> Unit)? = null,
    photos: ReportObservationPhotos = ReportObservationPhotos.omitted(),
    onPhotoCounts: ((ReportPhotoCounts) -> Unit)? = null,
) {
    val model = buildFollowUpDocument(report, patient, caregiver, generatedOn)
    val references = renderFollowUpDocument(null, model, report, regular, bold, emptyMap(), photos = photos)
    val links = mutableListOf<FollowUpPdfLink>()
    val detected = report.observations.sumOf { it.localPhotoIds.distinct().size }
    val counts = MutableReportPhotoCounts()
    val source = SensitivePdfBuffer()
    try {
        renderFollowUpDocument(source, model, report, regular, bold, references, links, photos, counts)
        val bookmarks = references.map { (title, anchor) ->
            FollowUpPdfBookmark(title, anchor.page - 1, anchor.y)
        }
        val bytes = source.toByteArray()
        try { writeReportPdfWithEmbeddedLinks(bytes, links, output, bookmarks) }
        finally { bytes.fill(0) }
        onPageLinks?.invoke(links.toList())
        onPhotoCounts?.invoke(ReportPhotoCounts(detected, counts.included, counts.unavailable,
            if (photos.included) 0 else detected))
    } finally { source.wipe() }
}

internal data class ReportPhotoCounts(val detected: Int, val included: Int, val unavailable: Int, val omitted: Int)
private class MutableReportPhotoCounts(var included: Int = 0, var unavailable: Int = 0)

private class SensitivePdfBuffer : ByteArrayOutputStream() {
    fun wipe() { buf.fill(0); reset() }
}

private data class PdfPoint(val time: LocalDateTime, val value: Double, val secondary: Boolean = false, val readingId: String? = null)
private data class PdfTableSpec(val titles: List<String>, val widths: List<Float>)
private data class PdfAnchor(val page: Int, val y: Float)

private fun renderFollowUpDocument(
    output: OutputStream?,
    model: FollowUpDocumentModel,
    report: FollowUpReport,
    regular: Typeface,
    bold: Typeface,
    pageReferences: Map<String, PdfAnchor>,
    links: MutableList<FollowUpPdfLink>? = null,
    photos: ReportObservationPhotos = ReportObservationPhotos.omitted(),
    photoCounts: MutableReportPhotoCounts? = null,
): Map<String, PdfAnchor> {
    val pdf = PdfDocument()
    val width = 595
    val height = 842
    val margin = 40f
    val bottom = height - 49f
    val bodyWidth = width - margin * 2
    val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LightOnSurface.toArgb(); textSize = 10.5f; typeface = regular }
    val small = Paint(normal).apply { textSize = 9f; color = LightOnSurfaceVariant.toArgb() }
    val header = Paint(normal).apply { textSize = 13f; typeface = bold }
    val title = Paint(header).apply { textSize = 20f }
    val brand = Paint(header).apply { textSize = 20f; color = LightExtendedColors.brand.toArgb() }
    val rowTitle = Paint(normal).apply { typeface = bold }
    val band = Paint(normal).apply { color = LightSurfaceVariant.toArgb(); style = Paint.Style.FILL }
    val border = Paint(normal).apply { color = LightOutline.toArgb(); style = Paint.Style.STROKE; strokeWidth = .65f }
    val accent = Paint(normal).apply { color = LightExtendedColors.brand.toArgb(); strokeWidth = 1.2f }
    val tableHeaderPaint = Paint(normal).apply { textSize = 8.5f; typeface = bold }
    val metric = Paint(normal).apply { textSize = 17f; typeface = bold; color = LightExtendedColors.brand.toArgb() }
    val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    val seenSections = linkedMapOf<String, PdfAnchor>()
    val photoRecords = report.observations.filter { it.localPhotoIds.isNotEmpty() }
    val photoLabels = photoRecords.mapIndexed { index, record -> record.id to "F-${index + 1}" }.toMap()
    var page: PdfDocument.Page? = null
    var pageNumber = 0
    var y = margin
    var continuingSection: String? = null
    var continuingGroup: String? = null
    var continuingRecord: String? = null
    var activeTable: PdfTableSpec? = null

    fun lines(text: String, paint: Paint, maxWidth: Float = bodyWidth): List<String> {
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var rest = paragraph
            if (rest.isEmpty()) result.add("")
            while (rest.isNotEmpty()) {
                var count = paint.breakText(rest, true, maxWidth.coerceAtLeast(1f), null).coerceAtLeast(1)
                if (count < rest.length && Character.isHighSurrogate(rest[count - 1])) count--
                if (count == 0) count = Character.charCount(rest.codePointAt(0))
                if (count < rest.length) {
                    val boundary = rest.lastIndexOf(' ', count - 1)
                    if (boundary > 0) count = boundary
                }
                result.add(rest.substring(0, count))
                rest = rest.substring(count).trimStart()
            }
        }
        return result
    }
    fun drawTableHeader(spec: PdfTableSpec) {
        val canvas = page!!.canvas
        val bandHeight = 22f
        canvas.drawRect(margin, y, width - margin, y + bandHeight, band)
        var left = margin
        spec.titles.forEachIndexed { index, label ->
            canvas.drawText(label, left + 5f, y + 15f, tableHeaderPaint)
            left += bodyWidth * spec.widths[index]
        }
        y += bandHeight
    }
    fun finishPage() {
        val current = page ?: return
        current.canvas.drawLine(margin, height - 37f, width - margin, height - 37f, border)
        current.canvas.drawText("Bitavida · Informe para consulta", margin, height - 23f, small)
        val number = "Página $pageNumber"
        current.canvas.drawText(number, width - margin - small.measureText(number), height - 23f, small)
        pdf.finishPage(current)
        page = null
    }
    fun nextPage() {
        finishPage()
        pageNumber++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        y = margin
        val canvas = page!!.canvas
        canvas.drawText("Bitavida", margin, y + brand.textSize, brand)
        canvas.drawText("INFORME PARA CONSULTA", margin + 112f, y + 17f, tableHeaderPaint)
        if (model.containsSimulatedData) {
            val label = "CONTIENE DATOS SIMULADOS"
            canvas.drawText(label, width - margin - tableHeaderPaint.measureText(label), y + 17f, tableHeaderPaint)
        }
        y += 29f
        canvas.drawLine(margin, y, width - margin, y, accent)
        y += 10f
        if (pageNumber > 1) {
            fun pageHeader(text: String, paint: Paint = small) {
                lines(text, paint).forEach { segment ->
                    canvas.drawText(segment, margin, y + paint.textSize, paint)
                    y += paint.textSize + 3f
                }
            }
            pageHeader("Paciente: ${model.patient}", rowTitle)
            pageHeader("Periodo: ${model.start.format(date)} – ${model.end.format(date)} · Corte: ${report.cutoffAt.format(dateTime)}")
            y += 4f
        }
        val section = continuingSection
        if (section != null) {
            lines("$section · continuación", header).forEach { text ->
                canvas.drawText(text, margin, y + header.textSize, header)
                y += 18f
            }
            continuingGroup?.takeUnless { it == section }?.let { group ->
                lines(group, rowTitle).forEach { text ->
                    canvas.drawText(text, margin, y + rowTitle.textSize, rowTitle)
                    y += 15f
                }
            }
            continuingRecord?.let { record ->
                lines("Continúa: $record", small).forEach { text ->
                    canvas.drawText(text, margin, y + small.textSize, small)
                    y += small.textSize + 4f
                }
            }
            activeTable?.let(::drawTableHeader)
        }
    }
    fun ensureSpace(space: Float) {
        if (page == null || y + space > bottom) nextPage()
    }
    fun line(text: String, paint: Paint = normal) {
        val advance = paint.textSize + 4f
        lines(text, paint).forEach { segment ->
            ensureSpace(advance)
            page!!.canvas.drawText(segment, margin, y + paint.textSize, paint)
            y += advance
        }
    }
    // Available room after the repeated identification and section/group headings.
    // This is measured using exactly the same paints and line wrapping as nextPage.
    fun continuationCapacity(): Float {
        var headerHeight = margin + 39f
        headerHeight += lines("Paciente: ${model.patient}", rowTitle).size * (rowTitle.textSize + 3f)
        val periodLabel = "Periodo: ${model.start.format(date)} – ${model.end.format(date)} · Corte: ${report.cutoffAt.format(dateTime)}"
        headerHeight += lines(periodLabel, small).size * (small.textSize + 3f) + 4f
        continuingSection?.let { section ->
            headerHeight += lines("$section · continuación", header).size * 18f
            continuingGroup?.takeUnless { it == section }?.let {
                headerHeight += lines(it, rowTitle).size * 15f
            }
        }
        return (bottom - headerHeight).coerceAtLeast(0f)
    }
    fun historyBlock(label: String, description: String) {
        val labelHeight = lines(label, rowTitle).size * (rowTitle.textSize + 4f)
        val descriptionHeight = lines(description, small).size * (small.textSize + 4f)
        val blockHeight = labelHeight + descriptionHeight
        // Keep a short entry whole when it fits on a fresh page. Long entries can
        // continue, but must start with at least their first descriptive line.
        if (y + blockHeight > bottom && blockHeight <= continuationCapacity()) nextPage()
        else ensureSpace(labelHeight + small.textSize + 4f)
        line(label, rowTitle)
        continuingRecord = label
        line(description, small)
        continuingRecord = null
    }
    fun tableRow(spec: PdfTableSpec, cells: List<String>, index: Int, continuationIdentity: String) {
        val wrapped = cells.mapIndexed { column, value ->
            lines(value, if (column == 1) rowTitle else normal, bodyWidth * spec.widths[column] - 10f)
        }
        val totalLines = wrapped.maxOfOrNull { it.size } ?: 1
        val lineHeight = normal.textSize + 3f
        // Keep a row intact if it fits on a fresh page; only exceptionally long
        // records need to be continued across pages.
        val completeHeight = totalLines * lineHeight + 7f
        val conservativePageCapacity = bottom - margin - 145f
        if (y + completeHeight > bottom && completeHeight <= conservativePageCapacity) nextPage()
        var startLine = 0
        while (startLine < totalLines) {
            if (y + lineHeight + 8f > bottom) nextPage()
            val count = minOf(totalLines - startLine, ((bottom - y - 7f) / lineHeight).toInt().coerceAtLeast(1))
            val rowHeight = count * lineHeight + 7f
            val canvas = page!!.canvas
            if (index % 2 == 1) canvas.drawRect(margin, y, width - margin, y + rowHeight, band)
            var left = margin
            wrapped.forEachIndexed { column, values ->
                for (lineIndex in startLine until minOf(startLine + count, values.size)) {
                    val paint = if (column == 1) rowTitle else normal
                    canvas.drawText(values[lineIndex], left + 5f,
                        y + 4f + normal.textSize + (lineIndex - startLine) * lineHeight, paint)
                }
                left += bodyWidth * spec.widths[column]
            }
            y += rowHeight
            canvas.drawLine(margin, y, width - margin, y, border)
            startLine += count
            if (startLine < totalLines) {
                nextPage()
                // Restore the identity of a split record independently of its text.
                line("Continúa: $continuationIdentity", small)
            }
        }
    }
    data class SummaryMetric(val label: String, val value: String, val detail: String)
    fun metricCards(items: List<SummaryMetric>) {
        items.chunked(2).forEach { row ->
            val gap = 12f
            val cardWidth = (bodyWidth - gap) / 2f
            fun itemHeight(item: SummaryMetric): Float = 20f +
                lines(item.label, small, cardWidth - 20f).size * 12f +
                lines(item.value, metric, cardWidth - 20f).size * 21f +
                lines(item.detail, small, cardWidth - 20f).size * 12f
            val cardHeight = row.maxOf(::itemHeight)
            ensureSpace(cardHeight + 10f)
            val top = y
            row.forEachIndexed { index, item ->
                val x = margin + index * (cardWidth + gap)
                val canvas = page!!.canvas
                canvas.drawRoundRect(x, top, x + cardWidth, top + cardHeight, 8f, 8f, band)
                canvas.drawRoundRect(x, top, x + cardWidth, top + cardHeight, 8f, 8f, border)
                var textY = top + 10f
                fun cardText(text: String, paint: Paint, advance: Float) {
                    lines(text, paint, cardWidth - 20f).forEach { segment ->
                        canvas.drawText(segment, x + 10f, textY + paint.textSize, paint)
                        textY += advance
                    }
                }
                cardText(item.label, small, 12f)
                cardText(item.value, metric, 21f)
                cardText(item.detail, small, 12f)
            }
            y = top + cardHeight + 10f
        }
    }
    data class PdfChart(val label: String, val unit: String, val points: List<PdfPoint>, val pressure: Boolean = false)
    fun chartCards(charts: List<PdfChart>) {
        charts.filter { it.points.isNotEmpty() }.chunked(2).forEach { row ->
            val cardHeight = 163f
            ensureSpace(cardHeight + 10f)
            val top = y
            val gap = 12f
            val cardWidth = if (row.size == 1) bodyWidth else (bodyWidth - gap) / 2f
            row.forEachIndexed { index, chart ->
                val x0 = margin + index * (cardWidth + gap)
                val canvas = page!!.canvas
                canvas.drawRoundRect(x0, top, x0 + cardWidth, top + cardHeight, 8f, 8f, border)
                canvas.drawText(chart.label, x0 + 11f, top + 20f, rowTitle)
                canvas.drawText(chart.unit + " · lecturas declaradas", x0 + 11f, top + 34f, small)
                val left = x0 + 36f
                val right = x0 + cardWidth - 12f
                val chartTop = top + 45f
                val chartBottom = top + 112f
                val lowValue = chart.points.minOf { it.value }
                val highValue = chart.points.maxOf { it.value }
                val padding = ((highValue - lowValue) * .12).coerceAtLeast(1.0)
                val low = lowValue - padding
                val high = highValue + padding
                // The axis represents the selected period, including gaps and
                // the part after the data cutoff; points remain actual readings.
                val first = model.start.atStartOfDay()
                val last = model.end.atTime(23, 59, 59)
                val duration = ChronoUnit.SECONDS.between(first, last).coerceAtLeast(1)
                fun position(point: PdfPoint): Pair<Float, Float> {
                    val elapsed = ChronoUnit.SECONDS.between(first, point.time).toDouble()
                    val x = if (first == last) (left + right) / 2f else left + (right - left) * (elapsed / duration).toFloat()
                    val py = chartBottom - (chartBottom - chartTop) * ((point.value - low) / (high - low)).toFloat()
                    return x to py
                }
                repeat(3) { grid ->
                    val gy = chartTop + (chartBottom - chartTop) * grid / 2f
                    canvas.drawLine(left, gy, right, gy, border)
                }
                canvas.drawText(String.format(Locale.ROOT, "%.0f", high), x0 + 5f, chartTop + 4f, small)
                canvas.drawText(String.format(Locale.ROOT, "%.0f", low), x0 + 5f, chartBottom, small)
                val firstPaint = Paint(accent).apply { style = Paint.Style.FILL }
                val secondPaint = Paint(firstPaint).apply { color = if (chart.pressure) LightError.toArgb() else LightExtendedColors.brand.toArgb() }
                if (chart.pressure) {
                    chart.points.filter { it.readingId != null }.groupBy { it.readingId }.values.forEach { values ->
                        val systolic = values.firstOrNull { !it.secondary }
                        val diastolic = values.firstOrNull { it.secondary }
                        if (systolic != null && diastolic != null) {
                            val (sx, sy) = position(systolic)
                            val (dx, dy) = position(diastolic)
                            val gradient = Paint(accent).apply {
                                shader = LinearGradient(sx, sy, dx, if (dy == sy) dy + 1f else dy,
                                    firstPaint.color, secondPaint.color, Shader.TileMode.CLAMP)
                            }
                            canvas.drawLine(sx, sy, dx, dy, gradient)
                        }
                    }
                }
                chart.points.forEach { point ->
                    val (px, py) = position(point)
                    val paint = if (point.secondary) secondPaint else firstPaint
                    if (!chart.pressure && point.secondary) {
                        canvas.drawCircle(px, py, 3f, Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f })
                    } else canvas.drawCircle(px, py, 3f, paint)
                }
                if (report.cutoffAt > first && report.cutoffAt < last) {
                    val cutoffX = left + (right - left) *
                        (ChronoUnit.SECONDS.between(first, report.cutoffAt).toDouble() / duration).toFloat()
                    canvas.drawLine(cutoffX, chartTop, cutoffX, chartBottom, border)
                }
                val firstLabel = first.format(date)
                val lastLabel = last.format(date)
                canvas.drawText(firstLabel, left, top + 128f, small)
                if (first.toLocalDate() != last.toLocalDate() && right - left > small.measureText(firstLabel + lastLabel) + 8f) {
                    canvas.drawText(lastLabel, right - small.measureText(lastLabel), top + 128f, small)
                }
                val legend = when {
                    chart.pressure -> "Sistólica: turquesa · Diastólica: rojo"
                    chart.points.any { it.secondary } -> "Relleno: otros momentos · Contorno: después de comer"
                    else -> "Puntos: registros individuales"
                }
                // The legend is short enough for half-page cards; no clinical state is inferred.
                lines(legend, small, cardWidth - 22f).forEachIndexed { lineIndex, segment ->
                    canvas.drawText(segment, x0 + 11f, top + 140f + lineIndex * 10f, small)
                }
            }
            y = top + cardHeight + 10f
        }
    }

    try {
        nextPage()
        seenSections["Resumen para consulta"] = PdfAnchor(pageNumber, y)
        line("Resumen para consulta", title)
        line("Registro de seguimiento aportado por la persona cuidadora", small)
        y += 8f
        val identityLines = listOf(
            "Paciente: ${model.patient}",
            "Cuidador: ${model.caregiver}",
            "Periodo: ${model.start.format(date)} – ${model.end.format(date)}",
            "Corte: ${report.cutoffAt.format(dateTime)} · Captura: ${report.snapshotAt.format(dateTime)}",
            "Fecha de generación: ${model.generatedOn.format(date)}",
        ).flatMap { lines(it, normal, bodyWidth - 20f) }
        val identityHeight = identityLines.size * 14.5f + 18f
        ensureSpace(identityHeight + 8f)
        page!!.canvas.drawRoundRect(margin, y, width - margin, y + identityHeight, 8f, 8f, band)
        var identityY = y + 10f
        identityLines.forEach { text ->
            page!!.canvas.drawText(text, margin + 10f, identityY + normal.textSize, normal)
            identityY += 14.5f
        }
        y += identityHeight + 12f
        model.sourceWarning?.let { line(it, rowTitle) }
        line("Fuentes locales consultadas por separado; cambios simultáneos podrían no reflejarse de forma uniforme.", small)
        continuingSection = "Resumen para consulta"
        ensureSpace(40f)
        line("Tratamiento durante el periodo", header)
        val counts = report.summaries.associate { it.label to it.count }
        val medicationGroups = report.medicationSeriesIds.size
        val medicationLabel = if (medicationGroups == 1) "medicamento" else "medicamentos"
        line("$medicationGroups $medicationLabel con seguimiento o actividad.", normal)
        val uncertainOccurrences = counts["Ocasiones con asociación no verificable"] ?: 0
        val unknownSchedules = counts["Pautas sin programación reconstruible"] ?: 0
        val incomplete = uncertainOccurrences > 0 || unknownSchedules > 0
        line(reportMedicationCounts(
            complete = counts["Tomas completas"] ?: 0,
            partial = counts["Tomas parciales"] ?: 0,
            notPerformed = counts["Tomas no realizadas"] ?: 0,
            unconfirmed = counts["Tomas sin confirmar"] ?: 0,
            incomplete = incomplete,
        ), small)
        if (incomplete) {
            val limitations = buildList {
                if (uncertainOccurrences > 0) add("$uncertainOccurrences " +
                    if (uncertainOccurrences == 1) "ocasión sin asociación verificable" else "ocasiones sin asociación verificable")
                if (unknownSchedules > 0) add("$unknownSchedules " +
                    if (unknownSchedules == 1) "pauta sin programación reconstruible" else "pautas sin programación reconstruible")
            }
            line("Sin confirmar puede estar incompleto: ${limitations.joinToString(" · ")}.", small)
        }
        line("Pautas y cambios por tramo en Medicamentos; el periodo no confirma vigencia posterior.", small)
        y += 6f
        ensureSpace(40f)
        line("Mediciones disponibles", header)
        val metrics = reportSummaryMetrics(report)
        if (metrics.cards.isEmpty()) line("No hay valores numéricos registrados en este periodo.", small)
        else {
            metricCards(metrics.cards.map { SummaryMetric(it.label, it.value, it.detail) })
            metrics.additionalGlucose.forEach { value ->
                line("${value.label}: ${value.value} · ${value.detail.substringBefore(" · ")}", small)
            }
        }
        val observationCount = counts["Registros de observación"] ?: 0
        val eventCount = counts["Citas"] ?: 0
        line("Observaciones registradas: $observationCount · Consultas, estudios y otros eventos registrados: $eventCount.", small)
        line("Información declarada por la persona cuidadora. No sustituye la valoración profesional.", small)
        y += 8f
        continuingGroup = null
        activeTable = null
        continuingSection = null
        if (model.sections.isNotEmpty()) {
            ensureSpace(40f)
            line("Detalle del informe", rowTitle)
            model.sections.forEach { section ->
                ensureSpace(16f)
                val label = section.title
                val target = pageReferences[label]
                val reference = target?.let { "p. ${it.page}" } ?: "p. 000"
                page!!.canvas.drawText(label, margin, y + small.textSize, small)
                page!!.canvas.drawText(reference, width - margin - small.measureText(reference), y + small.textSize, small)
                if (target != null) links?.add(FollowUpPdfLink(
                    sourcePage = pageNumber - 1, left = margin, top = y,
                    right = width - margin, bottom = y + 14f,
                    targetPage = target.page - 1, targetY = target.y,
                ))
                y += 14f
            }
        } else line("No hay datos registrados para este periodo.", small)
        y += 6f

        model.sections.forEach { section ->
            continuingSection = null
            continuingGroup = null
            activeTable = null
            val glucose = report.measurements.mapNotNull { record ->
                val value = record.value as? MeasurementValue.Glucose ?: return@mapNotNull null
                value.reading.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                    PdfPoint(record.occurredAt, it,
                        secondary = value.context == com.bitavida.app.domain.model.GlucoseContext.AFTER_MEAL,
                        readingId = record.id)
                }
            }
            val pressure = report.measurements.flatMap { record ->
                val value = record.value as? MeasurementValue.BloodPressure
                if (value == null) emptyList() else listOfNotNull(
                    value.systolic.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                        PdfPoint(record.occurredAt, it, readingId = record.id)
                    },
                    value.diastolic.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                        PdfPoint(record.occurredAt, it, secondary = true, readingId = record.id)
                    },
                )
            }
            val weight = report.measurements.mapNotNull { record ->
                (record.value as? MeasurementValue.Weight)?.kilograms?.toDouble()
                    ?.takeIf { it.isFinite() }?.let { PdfPoint(record.occurredAt, it, readingId = record.id) }
            }
            val charts = if (section.title == "Mediciones") listOf(
                PdfChart("Glucosa capilar", "mg/dL", glucose),
                PdfChart("Presión arterial", "mmHg", pressure, pressure = true),
                PdfChart("Peso", "kg", weight),
            ).filter { it.points.isNotEmpty() } else emptyList()
            val displayTitle = when (section.title) {
                "Mediciones" -> "Mediciones y seguimiento"
                "Medicamentos" -> "Medicamentos y tomas"
                "Observaciones" -> "Observaciones y atención"
                "Citas" -> "Consultas, estudios y procedimientos"
                else -> section.title
            }
            fun groupSpace(group: String, rows: List<com.bitavida.app.domain.usecase.FollowUpReportRow>): Float {
                val first = rows.first()
                val widths = listOf(.20f, .24f, .56f)
                val timestamp = first.occurredAt.format(if (first.timeKnown) dateTime else date)
                val detail = buildString {
                    append(first.detail)
                    if (first.provenance.isNotBlank() &&
                        first.provenance !in setOf("Registro declarado por el cuidador", "Seguimiento programado", "Información declarada por el cuidador")) {
                        append("\n").append(first.provenance)
                    }
                }
                val cells = listOf(timestamp, first.title, detail)
                val rowLines = cells.mapIndexed { index, text ->
                    lines(text, if (index == 1) rowTitle else normal, bodyWidth * widths[index] - 10f).size
                }.maxOrNull() ?: 1
                val rowHeight = rowLines * (normal.textSize + 3f) + 7f
                val firstRow = if (rowHeight <= bottom - margin - 145f) rowHeight else normal.textSize + 10f
                val groupHeight = if (group == displayTitle) 0f
                    else lines(group, rowTitle).size * (rowTitle.textSize + 4f)
                return 4f + groupHeight + 22f + firstRow
            }
            val firstGroup = section.rows.groupBy { it.section }.entries.firstOrNull()
            val firstGroupSpace = if (section.title != "Medicamentos" && firstGroup != null)
                groupSpace(firstGroup.key, firstGroup.value) else 68f
            val headingHeight = 4f + lines(displayTitle, header).size * (header.textSize + 4f) +
                small.textSize + 4f
            // The return link has its own baseline; reserve it with the title and first content.
            ensureSpace(if (charts.isNotEmpty()) headingHeight + 173f else headingHeight + firstGroupSpace)
            y += 4f
            seenSections[section.title] = PdfAnchor(pageNumber, y)
            line(displayTitle, header)
            val linkLabel = "Ir al resumen"
            val linkWidth = small.measureText(linkLabel)
            val linkTop = y
            page!!.canvas.drawText(linkLabel, width - margin - linkWidth, y + small.textSize, small)
            val summaryAnchor = pageReferences["Resumen para consulta"]
            if (summaryAnchor != null) links?.add(FollowUpPdfLink(
                pageNumber - 1, width - margin - linkWidth, linkTop,
                width - margin, linkTop + small.textSize + 4f, summaryAnchor.page - 1, summaryAnchor.y,
            ))
            y += small.textSize + 4f
            continuingSection = displayTitle
            if (charts.isNotEmpty()) {
                chartCards(charts)
                report.currentWeightTarget?.let {
                    line("Referencia de peso configurada actualmente: ${it.kilograms.toPlainString()} kg; su vigencia histórica no está registrada.", small)
                }
            }
            if (section.title == "Medicamentos") {
                // Keep the logical medication and each persisted plan distinct. A name or
                // matching schedule is never used to merge treatment periods.
                val groups = linkedMapOf<Pair<String, String>, MutableList<com.bitavida.app.domain.usecase.FollowUpReportRow>>()
                section.rows.forEachIndexed { index, row ->
                    val series = row.groupId ?: row.sourceId?.takeIf { it.startsWith("medication-plan:") } ?: "independent:$index"
                    val plan = reportMedicationPlanId(row) ?: "unresolved:$index"
                    groups.getOrPut(series to plan) { mutableListOf() }.add(row)
                }
                val spec = PdfTableSpec(listOf("FECHA", "HORA GUARDADA", "REAL", "REGISTRO Y ESTADO"),
                    listOf(.17f, .16f, .16f, .51f))
                groups.values.forEach { rows ->
                    continuingGroup = null
                    activeTable = null
                    val plan = rows.firstNotNullOfOrNull(::reportMedicationPlanId)
                    val planLabel = plan?.let { model.medicationIdentity.planLabels[it] }
                    val titleRow = rows.firstOrNull { it.section == "Indicaciones médicas" } ?: rows.first()
                    val baseLabel = titleRow.revisionNumber?.let {
                        titleRow.title.removeSuffix(" · versión $it")
                    } ?: titleRow.title
                    val label = baseLabel + (planLabel?.let { " · $it" } ?: "")
                    // Indications and treatment changes use the whole page width.
                    // Only administrations belong in the compact occurrence table.
                    val history = medicationHistoryBlocks(
                        rows.filter { it.section != "Medicamentos" }
                            .sortedWith(compareBy({ it.occurredAt }, { it.revisionNumber ?: 0 })),
                    )
                    fun historyLabel(row: com.bitavida.app.domain.usecase.FollowUpReportRow): String {
                        val timestamp = row.occurredAt.format(if (row.timeKnown) dateTime else date)
                        val name = row.revisionNumber?.let { row.title.removeSuffix(" · versión $it") } ?: row.title
                        val version = row.revisionNumber?.let { "versión $it" }
                        return listOfNotNull(timestamp, name.takeUnless { it == baseLabel }, version)
                            .joinToString(" · ")
                    }
                    val groupHeight = 5f + lines(label, rowTitle).size * (rowTitle.textSize + 4f)
                    val firstHistory = history.firstOrNull()
                    val firstBlock = firstHistory?.let {
                        lines(historyLabel(it.row), rowTitle).size * (rowTitle.textSize + 4f) +
                            lines(it.detail, small).size * (small.textSize + 4f)
                    } ?: 48f
                    val minimum = groupHeight + (firstHistory?.let {
                        lines(historyLabel(it.row), rowTitle).size * (rowTitle.textSize + 4f) + small.textSize + 4f
                    } ?: 48f)
                    val desired = groupHeight + firstBlock
                    if (desired <= continuationCapacity() && y + desired > bottom) nextPage()
                    else ensureSpace(minimum)
                    y += 5f
                    line(label, rowTitle)
                    continuingGroup = label
                    history.forEach { block ->
                        historyBlock(historyLabel(block.row), block.detail)
                    }
                    val administrations = rows.filter { it.section == "Medicamentos" }
                        .sortedWith(compareBy({ it.occurredAt }, { it.revisionNumber ?: 0 }))
                    fun administrationCells(row: com.bitavida.app.domain.usecase.FollowUpReportRow): List<String> {
                        val day = row.occurredAt.format(date)
                        val planned = listOfNotNull(reportScheduledTimeLabel(row),
                            row.sourceId?.let { model.medicationIdentity.declarationLabels[it] }).joinToString("\n")
                        val actual = row.actualAt?.let {
                            if (it.toLocalDate() == row.occurredAt.toLocalDate()) it.format(DateTimeFormatter.ofPattern("HH:mm"))
                            else it.format(dateTime)
                        } ?: "—"
                        val detail = buildString {
                            if (row.section != "Medicamentos") append(row.section).append(": ")
                            val expected = row.sourceId?.startsWith("medication-expected:") == true
                            append(if (expected && row.detail == "Sin confirmar · Ocasión prevista sin confirmación al corte")
                                "Sin confirmar" else row.detail)
                            if (row.provenance.isNotBlank() &&
                                row.provenance !in setOf("Registro declarado por el cuidador", "Seguimiento programado", "Información declarada por el cuidador", "Programación registrada")) {
                                append("\n").append(row.provenance)
                            }
                        }
                        return listOf(day, planned, actual, detail)
                    }
                    if (administrations.isNotEmpty()) {
                        if (administrations.any { it.sourceId?.startsWith("medication-expected:") == true }) {
                            line("Sin confirmar: ocasión prevista sin respuesta al corte.", small)
                        }
                        val firstCells = administrationCells(administrations.first())
                        val firstLines = firstCells.mapIndexed { column, value ->
                            lines(value, if (column == 1) rowTitle else normal,
                                bodyWidth * spec.widths[column] - 10f).size
                        }.maxOrNull() ?: 1
                        val rowHeight = firstLines * (normal.textSize + 3f) + 7f
                        val capacity = bottom - margin - 145f
                        // Match tableRow's measurement before placing the header.
                        val firstRowSpace = if (rowHeight <= capacity) rowHeight else normal.textSize + 11f
                        ensureSpace(22f + firstRowSpace)
                        drawTableHeader(spec)
                        activeTable = spec
                    }
                    administrations.forEachIndexed { index, row ->
                        val cells = administrationCells(row)
                        val version = row.revisionNumber?.let { " · versión $it" }.orEmpty()
                        tableRow(spec, cells, index,
                            "$label · ${cells[0]} · ${cells[1].replace('\n', ' ')}$version")
                    }
                    continuingGroup = null
                    activeTable = null
                    y += 5f
                }
            } else {
                val spec = PdfTableSpec(listOf("FECHA / HORA", "TIPO", "REGISTRO Y ESTADO"),
                    listOf(.20f, .24f, .56f))
                section.rows.groupBy { it.section }.forEach { (group, rows) ->
                    continuingGroup = null
                    activeTable = null
                    ensureSpace(groupSpace(group, rows))
                    y += 4f
                    if (group != displayTitle) line(group, rowTitle)
                    drawTableHeader(spec)
                    continuingGroup = group
                    activeTable = spec
                    rows.forEachIndexed { index, row ->
                        val timestamp = row.occurredAt.format(if (row.timeKnown) dateTime else date)
                        val detail = buildString {
                            append(row.detail)
                            if (section.title == "Observaciones") {
                                val id = row.sourceId?.removePrefix("observation:")
                                photoLabels[id]?.let { label ->
                                    append(if (photos.included) "\nEvidencia fotográfica: $label" else "\nFotografías omitidas de esta exportación")
                                }
                            }
                            if (row.provenance.isNotBlank() &&
                                row.provenance !in setOf("Registro declarado por el cuidador", "Seguimiento programado", "Información declarada por el cuidador")) {
                                append("\n").append(row.provenance)
                            }
                        }
                        val version = row.revisionNumber?.let { " · versión $it" }.orEmpty()
                        tableRow(spec, listOf(timestamp, row.title, detail), index,
                            "$timestamp · ${row.title}$version")
                    }
                    continuingGroup = null
                    activeTable = null
                    y += 5f
                }
            }
            if (section.title == "Observaciones" && photoRecords.isNotEmpty() && photos.included) {
                continuingGroup = null
                activeTable = null
                ensureSpace(58f)
                y += 5f
                line("Evidencia fotográfica", header)
                line("Imágenes vinculadas a las observaciones registradas al corte.", small)
                continuingGroup = "Evidencia fotográfica"
                photoRecords.forEach { record ->
                    val label = requireNotNull(photoLabels[record.id])
                    val name = ObservationCatalog.all.firstOrNull { it.id == record.observationId }?.name
                        ?: "Observación registrada"
                    val time = record.occurredAt.takeUnless { record.timePrecision == ObservationTimePrecision.UNKNOWN }
                    val timeText = time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Hora no indicada"
                    val precision = if (time != null && record.timePrecision == ObservationTimePrecision.APPROXIMATE) " · hora aproximada" else ""
                    val titleText = "$label · $name · ${record.occurredOn.format(date)} · $timeText$precision"
                    val description = record.description.takeIf { it.isNotBlank() }
                    val capturedText = "Registro capturado: ${record.createdAt.format(dateTime)}"
                    val headerHeight = lines(titleText, rowTitle).size * (rowTitle.textSize + 4f) +
                        lines(capturedText, small).size * (small.textSize + 4f) +
                        (description?.let { lines(it, small).size * (small.textSize + 4f) } ?: 0f)
                    val keys = record.localPhotoIds.distinct().map { ReportPhotoKey(record.id, it) }
                    val firstPair = keys.take(2)
                    val firstWidth = if (firstPair.size == 1) minOf(340f, bodyWidth) else (bodyWidth - 12f) / 2f
                    val firstHeight = firstPair.maxOf { key ->
                        photos.dimensions[key]?.let { minOf(220f, firstWidth * it.height / it.width.toFloat()) } ?: 42f
                    }
                    val needed = headerHeight + firstHeight + small.textSize + 18f
                    if (needed <= continuationCapacity()) ensureSpace(needed)
                    else ensureSpace(minOf(headerHeight, 60f) + 30f)
                    line(titleText, rowTitle)
                    continuingRecord = titleText
                    line(capturedText, small)
                    if (description != null) line(description, small)
                    keys.chunked(2).forEach { pair ->
                        val columnWidth = if (pair.size == 1) minOf(340f, bodyWidth) else (bodyWidth - 12f) / 2f
                        val rowHeight = pair.maxOf { key ->
                            val size = photos.dimensions[key]
                            if (size == null) 42f else minOf(220f, columnWidth * size.height / size.width.toFloat())
                        }
                        ensureSpace(rowHeight + 23f)
                        pair.forEachIndexed { column, key ->
                            val left = margin + column * (columnWidth + 12f)
                            val size = photos.dimensions[key]
                            if (size == null) {
                                photoCounts?.let { it.unavailable++ }
                                page!!.canvas.drawText("Imagen no disponible o ilegible", left, y + small.textSize, small)
                            } else {
                                val drawHeight = minOf(220f, columnWidth * size.height / size.width.toFloat())
                                val drawWidth = minOf(columnWidth, drawHeight * size.width / size.height.toFloat())
                                var bitmap: android.graphics.Bitmap? = null
                                if (output != null) {
                                    val encoded = runBlocking { photos.read(key) }
                                    if (encoded != null) {
                                        try { bitmap = try { decodeReportPhoto(encoded) } catch (_: Exception) { null } }
                                        finally { encoded.fill(0) }
                                    }
                                }
                                if (output == null || bitmap != null) {
                                    if (bitmap != null) {
                                        try {
                                            page!!.canvas.drawBitmap(bitmap!!, null,
                                                RectF(left, y, left + drawWidth, y + drawHeight), normal)
                                            photoCounts?.let { it.included++ }
                                        } finally { bitmap!!.recycle() }
                                    }
                                } else {
                                    photoCounts?.let { it.unavailable++ }
                                    page!!.canvas.drawText("Imagen no disponible o ilegible", left, y + small.textSize, small)
                                }
                            }
                            val ordinal = keys.indexOf(key) + 1
                            page!!.canvas.drawText("$label · imagen $ordinal", left, y + rowHeight + small.textSize + 3f, small)
                        }
                        y += rowHeight + small.textSize + 10f
                    }
                    continuingRecord = null
                    y += 8f
                }
            }
            continuingSection = null
            continuingGroup = null
            activeTable = null
            y += 6f
        }
        finishPage()
        if (output != null) pdf.writeTo(output)
    } finally {
        pdf.close()
    }
    return seenSections
}
