package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseTargetRange
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** Display scale only: these ticks are never clinical thresholds or prescribed targets. */
data class GlucosePlotAxis(
    val lower: BigDecimal,
    val upper: BigDecimal,
    val ticks: List<BigDecimal>,
) {
    fun fraction(value: BigDecimal): Float =
        value.subtract(lower).divide(upper.subtract(lower), 8, RoundingMode.HALF_UP).toFloat()
}

data class GlucoseDayDot(
    val point: MeasurementPlotPoint,
    val positionWithinDay: Float,
)

data class GlucosePlotDay(
    val day: MeasurementDay,
    val dots: List<GlucoseDayDot>,
)

data class GlucoseWeekPlot(
    val start: LocalDate,
    val days: List<GlucosePlotDay>,
    val axis: GlucosePlotAxis?,
    val unplottedRecordCount: Int,
)

/** One point per numeric event, ordered by time within the day, including coincident readings. */
fun projectGlucoseWeekPlot(
    week: MeasurementHistoryWeek,
    targetRange: GlucoseTargetRange? = null,
    referenceValue: BigDecimal? = null,
): GlucoseWeekPlot {
    val readings = week.points.filter { it.series == MeasurementPlotSeries.READING }
    val days = week.days.map { day ->
        val points = readings.filter { it.occurredAt.toLocalDate() == day.date }
            .sortedWith(compareBy<MeasurementPlotPoint> { it.occurredAt }.thenBy { it.recordId })
        GlucosePlotDay(
            day = day,
            dots = points.mapIndexed { index, point ->
                GlucoseDayDot(point, (index + 1f) / (points.size + 1f))
            },
        )
    }
    return GlucoseWeekPlot(
        start = week.start,
        days = days,
        axis = glucoseAxis(
            readings.map { it.value } +
                listOfNotNull(
                    targetRange?.lower,
                    targetRange?.upper,
                    referenceValue,
                ),
        ),
        unplottedRecordCount = week.unplottedRecordCount,
    )
}

private fun glucoseAxis(values: List<BigDecimal>): GlucosePlotAxis? {
    if (values.isEmpty()) return null
    val minimum = values.minOrNull()!!
    val maximum = values.maxOrNull()!!
    val spread = maximum.subtract(minimum)
    val padding = if (spread.signum() == 0) maximum.abs().multiply(BigDecimal("0.10"))
        .max(BigDecimal.ONE) else spread.multiply(BigDecimal("0.15"))
    val paddedLower = minimum.subtract(padding).let {
        if (minimum.signum() >= 0) it.max(BigDecimal.ZERO) else it
    }
    val paddedUpper = maximum.add(padding)
    val roughStep = paddedUpper.subtract(paddedLower).divide(BigDecimal(4), MathContext.DECIMAL64)
    val exponent = roughStep.precision() - roughStep.scale() - 1
    val magnitude = BigDecimal.ONE.scaleByPowerOfTen(exponent)
    val step = listOf(1, 2, 5, 10).map { magnitude.multiply(BigDecimal(it)) }
        .first { it >= roughStep }
    val lower = paddedLower.divide(step, 0, RoundingMode.FLOOR).multiply(step)
    val upper = paddedUpper.divide(step, 0, RoundingMode.CEILING).multiply(step)
    val ticks = generateSequence(lower) { it.add(step) }.takeWhile { it <= upper }.toList()
    return GlucosePlotAxis(lower, upper, ticks)
}
