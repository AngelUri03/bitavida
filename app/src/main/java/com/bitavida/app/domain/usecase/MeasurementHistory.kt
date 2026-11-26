package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class MeasurementPlotSeries { READING, SYSTOLIC, DIASTOLIC }

data class MeasurementPlotPoint(
    val recordId: String,
    val occurredAt: LocalDateTime,
    val value: BigDecimal,
    val series: MeasurementPlotSeries,
)

data class MeasurementHistoryWeek(
    val start: LocalDate,
    val days: List<MeasurementDay>,
    val records: List<MeasurementRecord>,
    val points: List<MeasurementPlotPoint>,
) {
    fun recordsOn(date: LocalDate): List<MeasurementRecord> =
        records.filter { it.occurredAt.toLocalDate() == date }

    val unplottedRecordCount: Int
        get() = records.count { record ->
            points.none { it.recordId == record.id }
        }
}

/** Single isolation rule shared by detail, history, statistics and navigation. */
fun recordsForMeasurementKind(
    records: List<MeasurementRecord>,
    kind: MeasurementKind,
): List<MeasurementRecord> = records.filter { it.kind == kind }

/** Facts in the selected week, with no averages, target ranges or inferred responses. */
fun projectMeasurementHistoryWeek(
    records: List<MeasurementRecord>,
    kind: MeasurementKind,
    weekContaining: LocalDate,
    today: LocalDate,
): MeasurementHistoryWeek {
    val start = weekContaining.minusDays(
        (weekContaining.dayOfWeek.value - 1).toLong(),
    )
    return projectMeasurementHistoryRange(
        records = records,
        kind = kind,
        start = start,
        endInclusive = start.plusDays(6),
        today = today,
    )
}

/** Facts in an explicit inclusive period; callers decide whether it is a week or month. */
fun projectMeasurementHistoryRange(
    records: List<MeasurementRecord>,
    kind: MeasurementKind,
    start: LocalDate,
    endInclusive: LocalDate,
    today: LocalDate,
): MeasurementHistoryWeek {
    require(!endInclusive.isBefore(start))
    val endExclusive = endInclusive.plusDays(1)
    val visible = recordsForMeasurementKind(records, kind)
        .asSequence()
        .filter {
            !it.occurredAt.toLocalDate().isBefore(start) &&
                it.occurredAt.toLocalDate().isBefore(endExclusive)
        }
        .sortedWith(
            compareBy(MeasurementRecord::occurredAt)
                .thenBy(MeasurementRecord::capturedAt),
        )
        .toList()
    val counts = visible.groupingBy { it.occurredAt.toLocalDate() }.eachCount()
    val dayCount = java.time.temporal.ChronoUnit.DAYS
        .between(start, endInclusive)
        .toInt()
    val days = (0..dayCount).map { offset ->
        val date = start.plusDays(offset.toLong())
        val count = counts[date] ?: 0
        MeasurementDay(
            date = date,
            recordCount = count,
            state = when {
                count > 0 -> MeasurementDayState.WITH_RECORDS
                date > today -> MeasurementDayState.FUTURE
                else -> MeasurementDayState.WITHOUT_RECORDS
            },
        )
    }
    val points = visible.flatMap { record ->
        fun point(value: BigDecimal?, series: MeasurementPlotSeries) =
            value?.takeIf { it.toDouble().isFinite() }?.let {
                MeasurementPlotPoint(record.id, record.occurredAt, it, series)
            }
        when (val value = record.value) {
            is MeasurementValue.Glucose -> listOfNotNull(
                point(
                    value.reading.replace(',', '.').toBigDecimalOrNull(),
                    MeasurementPlotSeries.READING,
                ),
            )
            is MeasurementValue.BloodPressure -> listOfNotNull(
                point(value.systolic.toBigDecimalOrNull(), MeasurementPlotSeries.SYSTOLIC),
                point(value.diastolic.toBigDecimalOrNull(), MeasurementPlotSeries.DIASTOLIC),
            )
            is MeasurementValue.Weight -> listOfNotNull(
                point(value.kilograms, MeasurementPlotSeries.READING),
            )
        }
    }
    return MeasurementHistoryWeek(start, days, visible, points)
}
