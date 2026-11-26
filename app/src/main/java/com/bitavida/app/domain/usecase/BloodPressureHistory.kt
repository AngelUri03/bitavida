package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class BloodPressurePlotReading(
    val record: MeasurementRecord,
    val systolic: BigDecimal,
    val diastolic: BigDecimal,
)

data class BloodPressurePlotDay(
    val day: MeasurementDay,
    val readings: List<BloodPressurePlotReading>,
)

data class BloodPressurePlot(
    val start: LocalDate,
    val days: List<BloodPressurePlotDay>,
    val axis: GlucosePlotAxis?,
)

data class BloodPressureHistoryStatistics(
    val averageSystolic: BigDecimal?,
    val averageDiastolic: BigDecimal?,
    val minimumSystolic: BigDecimal?,
    val maximumSystolic: BigDecimal?,
    val minimumDiastolic: BigDecimal?,
    val maximumDiastolic: BigDecimal?,
    val totalMeasurements: Int,
)

data class BloodPressureHistoryDay(
    val date: LocalDate,
    val records: List<MeasurementRecord>,
    val events: List<GlucoseHistoryEvent>,
)

data class BloodPressureHistoryPeriod(
    val kind: GlucoseHistoryPeriodKind,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val history: MeasurementHistoryWeek,
    val plot: BloodPressurePlot,
    val statistics: BloodPressureHistoryStatistics,
    val days: List<BloodPressureHistoryDay>,
)

fun projectBloodPressurePlot(
    history: MeasurementHistoryWeek,
): BloodPressurePlot {
    val records = history.records.mapNotNull { record ->
        val value = record.value as? MeasurementValue.BloodPressure
            ?: return@mapNotNull null
        val systolic = value.systolic.toBigDecimalOrNull()
            ?: return@mapNotNull null
        val diastolic = value.diastolic.toBigDecimalOrNull()
            ?: return@mapNotNull null
        BloodPressurePlotReading(record, systolic, diastolic)
    }
    return BloodPressurePlot(
        start = history.start,
        days = history.days.map { day ->
            BloodPressurePlotDay(
                day = day,
                readings = records.filter {
                    it.record.occurredAt.toLocalDate() == day.date
                }.sortedBy { it.record.occurredAt },
            )
        },
        axis = bloodPressureAxis(
            records.flatMap { listOf(it.systolic, it.diastolic) },
        ),
    )
}

fun calculateBloodPressureStatistics(
    records: List<MeasurementRecord>,
): BloodPressureHistoryStatistics {
    val pairs = records.mapNotNull { record ->
        val value = record.value as? MeasurementValue.BloodPressure
            ?: return@mapNotNull null
        val systolic = value.systolic.toBigDecimalOrNull()
            ?: return@mapNotNull null
        val diastolic = value.diastolic.toBigDecimalOrNull()
            ?: return@mapNotNull null
        systolic to diastolic
    }
    fun List<BigDecimal>.average() = takeIf { it.isNotEmpty() }?.let {
        it.reduce(BigDecimal::add).divide(
            BigDecimal(it.size),
            1,
            RoundingMode.HALF_UP,
        )
    }
    val systolic = pairs.map(Pair<BigDecimal, BigDecimal>::first)
    val diastolic = pairs.map(Pair<BigDecimal, BigDecimal>::second)
    return BloodPressureHistoryStatistics(
        averageSystolic = systolic.average(),
        averageDiastolic = diastolic.average(),
        minimumSystolic = systolic.minOrNull(),
        maximumSystolic = systolic.maxOrNull(),
        minimumDiastolic = diastolic.minOrNull(),
        maximumDiastolic = diastolic.maxOrNull(),
        totalMeasurements = pairs.size,
    )
}

fun projectBloodPressureHistoryPeriod(
    records: List<MeasurementRecord>,
    anchor: LocalDate,
    kind: GlucoseHistoryPeriodKind,
    today: LocalDate,
    followUp: GlucoseFollowUp? = null,
    decisions: List<GlucoseOccurrenceDecision> = emptyList(),
    now: LocalDateTime = today.atTime(LocalTime.MAX),
): BloodPressureHistoryPeriod {
    val (start, endInclusive) = glucosePeriodBounds(anchor, kind)
    val history = projectMeasurementHistoryRange(
        records = records,
        kind = MeasurementKind.BLOOD_PRESSURE,
        start = start,
        endInclusive = endInclusive,
        today = today,
    )
    val days = projectBloodPressureHistoryEvents(
        records = records,
        followUp = followUp,
        decisions = decisions,
        start = start,
        endInclusive = endInclusive,
        now = now,
    )
    return BloodPressureHistoryPeriod(
        kind = kind,
        start = start,
        endInclusive = endInclusive,
        history = history,
        plot = projectBloodPressurePlot(history),
        statistics = calculateBloodPressureStatistics(history.records),
        days = days,
    )
}

fun projectBloodPressureHistoryEvents(
    records: List<MeasurementRecord>,
    followUp: GlucoseFollowUp?,
    decisions: List<GlucoseOccurrenceDecision>,
    start: LocalDate,
    endInclusive: LocalDate,
    now: LocalDateTime,
): List<BloodPressureHistoryDay> {
    return projectMeasurementHistoryEvents(
        records, followUp, decisions, start, endInclusive, now, MeasurementKind.BLOOD_PRESSURE,
    ).map { BloodPressureHistoryDay(it.date, it.records, it.events) }
}

private fun bloodPressureAxis(
    values: List<BigDecimal>,
): GlucosePlotAxis? {
    if (values.isEmpty()) return null
    val minimum = requireNotNull(values.minOrNull())
    val maximum = requireNotNull(values.maxOrNull())
    val spread = maximum.subtract(minimum)
    val padding = if (spread.signum() == 0) {
        maximum.abs().multiply(BigDecimal("0.10")).max(BigDecimal.ONE)
    } else {
        spread.multiply(BigDecimal("0.15"))
    }
    val paddedLower = minimum.subtract(padding).max(BigDecimal.ZERO)
    val paddedUpper = maximum.add(padding)
    val roughStep = paddedUpper.subtract(paddedLower).divide(
        BigDecimal(4),
        MathContext.DECIMAL64,
    )
    val exponent = roughStep.precision() - roughStep.scale() - 1
    val magnitude = BigDecimal.ONE.scaleByPowerOfTen(exponent)
    val step = listOf(1, 2, 5, 10)
        .map { magnitude.multiply(BigDecimal(it)) }
        .first { it >= roughStep }
    val lower = paddedLower.divide(
        step,
        0,
        RoundingMode.FLOOR,
    ).multiply(step)
    val upper = paddedUpper.divide(
        step,
        0,
        RoundingMode.CEILING,
    ).multiply(step)
    val ticks = generateSequence(lower) { it.add(step) }
        .takeWhile { it <= upper }
        .toList()
    return GlucosePlotAxis(lower, upper, ticks)
}
