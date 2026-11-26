package com.bitavida.app.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.FollowUpReport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class QuickTrendPoint(
    val at: LocalDateTime,
    val value: Double,
    val diastolic: Boolean = false,
)

/**
 * Compact, read-only view of individual measurements in the selected report period.
 * Its visual conventions follow MeasurementWeekChart: a temporal axis and discrete points,
 * with colored circles for the two values of a blood-pressure event.
 */
@Composable
internal fun ReportQuickTrends(
    report: FollowUpReport,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = listOf(MeasurementKind.CAPILLARY_GLUCOSE, MeasurementKind.BLOOD_PRESSURE, MeasurementKind.WEIGHT)
    val available = remember(report.measurements) {
        order.filter { kind -> report.measurements.any { it.kind == kind } }
    }
    var selectedName by rememberSaveable { mutableStateOf(MeasurementKind.CAPILLARY_GLUCOSE.name) }
    val selected = available.firstOrNull { it.name == selectedName } ?: available.firstOrNull()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vista rápida de tendencias", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface)
        if (selected == null) {
            Text("Aún no hay mediciones en este periodo.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                labelColor = MaterialTheme.colorScheme.onSurface,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { kind ->
                    val isSelected = selected == kind
                    FilterChip(selected = isSelected, onClick = { selectedName = kind.name },
                        label = { Text(kind.quickLabel()) },
                        modifier = Modifier.heightIn(min = 48.dp), colors = chipColors,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline))
                }
            }
            val records = remember(report.measurements, selected) {
                report.measurements.filter { it.kind == selected }.sortedBy { it.occurredAt }
            }
            val points = remember(records) { records.flatMap(::quickTrendPoints) }
            Text("${records.size} ${if (records.size == 1) "registro" else "registros"} · ${selected.quickUnit()}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.isEmpty()) {
                Text("No hay valores numéricos para graficar; las lecturas se conservan en el detalle.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                QuickTrendPlot(points, report, selected)
            }
            if (selected == MeasurementKind.WEIGHT && report.currentWeightTarget != null) {
                Text("Referencia indicada configurada actualmente: ${report.currentWeightTarget.kilograms.toPlainString()} kg. No se conoce su vigencia histórica.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onExplore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
            Text("Explorar tendencias")
        }
    }
}

private fun MeasurementKind.quickLabel(): String = when (this) {
    MeasurementKind.CAPILLARY_GLUCOSE -> "Glucosa"
    MeasurementKind.BLOOD_PRESSURE -> "Presión"
    MeasurementKind.WEIGHT -> "Peso"
}

private fun MeasurementKind.quickUnit(): String = when (this) {
    MeasurementKind.CAPILLARY_GLUCOSE -> "mg/dL"
    MeasurementKind.BLOOD_PRESSURE -> "mmHg"
    MeasurementKind.WEIGHT -> "kg"
}

internal fun quickTrendPoints(record: MeasurementRecord): List<QuickTrendPoint> {
    fun numeric(value: String): Double? = value.toDoubleOrNull()?.takeIf(Double::isFinite)
    return when (val value = record.value) {
        is MeasurementValue.Glucose -> listOfNotNull(numeric(value.reading)?.let { QuickTrendPoint(record.occurredAt, it) })
        is MeasurementValue.BloodPressure -> listOfNotNull(
            numeric(value.systolic)?.let { QuickTrendPoint(record.occurredAt, it) },
            numeric(value.diastolic)?.let { QuickTrendPoint(record.occurredAt, it, diastolic = true) },
        )
        is MeasurementValue.Weight -> listOfNotNull(
            value.kilograms.toDouble().takeIf(Double::isFinite)?.let { QuickTrendPoint(record.occurredAt, it) },
        )
    }
}

internal fun quickTrendTimeFraction(at: LocalDateTime, start: LocalDate, end: LocalDate): Float {
    require(!end.isBefore(start))
    val from = start.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
    val until = end.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
    return ((at.toEpochSecond(ZoneOffset.UTC) - from) / (until - from).coerceAtLeast(1).toDouble())
        .coerceIn(0.0, 1.0).toFloat()
}

@Composable
internal fun QuickTrendPlot(points: List<QuickTrendPoint>, report: FollowUpReport, kind: MeasurementKind,
    onPointSelected: ((QuickTrendPoint) -> Unit)? = null) {
    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val numberFormat = remember {
        java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("es-MX")).apply {
            isGroupingUsed = false
            maximumFractionDigits = 2
        }
    }
    val minLabel = numberFormat.format(minimum)
    val maxLabel = numberFormat.format(maximum)
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.bodySmall
    val axisWidth = with(LocalDensity.current) {
        maxOf(textMeasurer.measure(minLabel, axisStyle).size.width,
            textMeasurer.measure(maxLabel, axisStyle).size.width).toDp().coerceAtMost(88.dp)
    }
    if (kind == MeasurementKind.BLOOD_PRESSURE) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QuickTrendLegend("Sistólica", diastolic = false)
            QuickTrendLegend("Diastólica", diastolic = true)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val constant = minimum == maximum
    val touchRadius = with(LocalDensity.current) { 24.dp.toPx() }
    fun pointPosition(point: QuickTrendPoint, width: Float, height: Float): Offset {
        val x = quickTrendTimeFraction(point.at, report.start, report.end) * width
        val fraction = if (constant) 0.5 else (point.value - minimum) / (maximum - minimum)
        return Offset(x, (1.0 - fraction).toFloat() * height)
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clearAndSetSemantics {
            contentDescription = "Gráfica de ${kind.quickLabel().lowercase(Locale.forLanguageTag("es-MX"))}. " +
                "${points.size} valores numéricos de $minLabel a $maxLabel ${kind.quickUnit()}. " +
                "Cada punto representa un valor registrado. El detalle contiene sus fechas y horas."
        }, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.width(axisWidth).heightIn(min = 112.dp),
            verticalArrangement = if (constant) Arrangement.Center else Arrangement.SpaceBetween) {
            Text(maxLabel, style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!constant) Text(minLabel, style = axisStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp, horizontal = 5.dp)
            .pointerInput(points, report.start, report.end, onPointSelected, touchRadius) {
                if (onPointSelected != null) detectTapGestures { tap ->
                    val nearest = points.minByOrNull {
                        (pointPosition(it, size.width.toFloat(), size.height.toFloat()) - tap).getDistance()
                    }
                    if (nearest != null &&
                        (pointPosition(nearest, size.width.toFloat(), size.height.toFloat()) - tap).getDistance() <= touchRadius) {
                        onPointSelected(nearest)
                    }
                }
            }) {
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                drawLine(grid, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
            points.forEach { point ->
                val center = pointPosition(point, size.width, size.height)
                drawCircle(if (point.diastolic) secondary else primary, 4.dp.toPx(), center)
            }
        }
    }
    val dates = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX")) }
    Row(Modifier.fillMaxWidth().padding(start = axisWidth + 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(report.start.format(dates), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(report.end.format(dates), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickTrendLegend(label: String, diastolic: Boolean) {
    val color = if (diastolic) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp).clearAndSetSemantics {}) {
            drawCircle(color)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
