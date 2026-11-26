package com.bitavida.app.ui.measurements

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.usecase.MeasurementHistoryWeek
import com.bitavida.app.domain.usecase.MeasurementPlotSeries
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

@Composable
internal fun MeasurementWeekChart(week: MeasurementHistoryWeek, kind: MeasurementKind) {
    MeasurementPanel {
        SectionTitle("Evaluación semanal", small = true)
        SecondaryText(
            week.start.format(measurementShortDate) + " – " +
                week.start.plusDays(6).format(measurementShortDate) + " · " + kind.unit(),
        )
        when {
            week.records.isEmpty() -> SecondaryText("Sin registros esta semana.")
            week.points.isEmpty() -> SecondaryText("Sin valores numéricos para graficar. Consulta las lecturas en el historial.")
            else -> {
                val primary = MaterialTheme.colorScheme.primary
                val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                val minimum = week.points.minOf { it.value }
                val maximum = week.points.maxOf { it.value }
                val constant = minimum.compareTo(maximum) == 0
                val maximumLabel = maximum.stripTrailingZeros().toPlainString()
                val minimumLabel = minimum.stripTrailingZeros().toPlainString()
                val measurer = rememberTextMeasurer()
                val labelStyle = MaterialTheme.typography.bodySmall
                val axisWidth = with(LocalDensity.current) {
                    maxOf(
                        measurer.measure(maximumLabel, labelStyle).size.width,
                        measurer.measure(minimumLabel, labelStyle).size.width,
                    ).toDp().coerceAtMost(88.dp)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    PlotLegend(
                        if (kind == MeasurementKind.BLOOD_PRESSURE) "Sistólica" else "Lecturas",
                        square = false,
                    )
                    if (kind == MeasurementKind.BLOOD_PRESSURE) PlotLegend("Diastólica", square = true)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .clearAndSetSemantics {
                            contentDescription = "Gráfica de ${kind.label().lowercase(measurementLocale)}. " +
                                "${week.records.size} registros en la semana. " +
                                "Valores numéricos entre ${minimum.toPlainString()} y ${maximum.toPlainString()} ${kind.unit()}. " +
                                "Cada punto representa una lectura; los valores por fecha están en Calendario e historial."
                        },
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    Column(
                        modifier = Modifier.width(axisWidth).heightIn(min = 144.dp),
                        verticalArrangement = if (constant) Arrangement.Center else Arrangement.SpaceBetween,
                    ) {
                        SecondaryText(maximumLabel)
                        if (!constant) SecondaryText(minimumLabel)
                    }
                    Canvas(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 5.dp),
                    ) {
                        listOf(0f, 0.5f, 1f).forEach { fraction ->
                            drawLine(
                                color = grid,
                                start = Offset(0f, size.height * fraction),
                                end = Offset(size.width, size.height * fraction),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                        week.points.forEach { point ->
                            val day = ChronoUnit.DAYS.between(week.start, point.occurredAt.toLocalDate())
                            val timeFraction = point.occurredAt.toLocalTime().toSecondOfDay() / 86400f
                            val x = (day.toFloat() + timeFraction) / 7f * size.width
                            val fraction = if (constant) 0.5f else point.value.subtract(minimum)
                                .divide(maximum.subtract(minimum), 8, RoundingMode.HALF_UP).toFloat()
                            val center = Offset(x, size.height * (1f - fraction))
                            if (point.series == MeasurementPlotSeries.DIASTOLIC) {
                                val edge = 7.dp.toPx()
                                drawRect(
                                    color = secondary,
                                    topLeft = center - Offset(edge / 2, edge / 2),
                                    size = Size(edge, edge),
                                )
                            } else {
                                drawCircle(primary, radius = 4.dp.toPx(), center = center)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = axisWidth + BitavidaSpacing.Small + 5.dp, end = 5.dp),
                ) {
                    week.days.forEach { day ->
                        Text(
                            text = day.date.format(measurementWeekday).replace(".", "").take(1).uppercase(measurementLocale),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                if (week.unplottedRecordCount > 0) {
                    SecondaryText("${week.unplottedRecordCount} registros sin representación numérica; se conservan en el historial.")
                }
            }
        }
    }
}

@Composable
private fun PlotLegend(label: String, square: Boolean) {
    val color = if (square) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp).clearAndSetSemantics {}) {
            if (square) drawRect(color) else drawCircle(color)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
