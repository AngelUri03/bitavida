package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class GlucoseHistoryPeriodKind { WEEK, MONTH }

sealed interface GlucoseHistoryEvent {
    val sortAt: LocalDateTime

    data class Registered(
        val record: MeasurementRecord,
    ) : GlucoseHistoryEvent {
        override val sortAt: LocalDateTime = record.occurredAt
    }

    data class NotPerformed(
        val occurrence: GlucoseScheduledOccurrence,
        val decision: GlucoseOccurrenceDecision,
    ) : GlucoseHistoryEvent {
        override val sortAt: LocalDateTime = occurrence.scheduledAt
    }

    data class Unconfirmed(
        val occurrence: GlucoseScheduledOccurrence,
    ) : GlucoseHistoryEvent {
        override val sortAt: LocalDateTime = occurrence.scheduledAt
    }
}

data class GlucoseHistoryDay(
    val date: LocalDate,
    val records: List<MeasurementRecord>,
    val events: List<GlucoseHistoryEvent> = records.map(
        GlucoseHistoryEvent::Registered,
    ),
)

data class GlucoseHistoryStatistics(
    val average: BigDecimal?,
    val minimum: BigDecimal?,
    val maximum: BigDecimal?,
    val totalMeasurements: Int,
    val numericMeasurements: Int,
    val nonNumericMeasurements: Int,
)

data class GlucoseHistoryPeriod(
    val kind: GlucoseHistoryPeriodKind,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val history: MeasurementHistoryWeek,
    val statistics: GlucoseHistoryStatistics,
    val days: List<GlucoseHistoryDay>,
)

fun glucosePeriodBounds(
    anchor: LocalDate,
    kind: GlucoseHistoryPeriodKind,
): Pair<LocalDate, LocalDate> = when (kind) {
    GlucoseHistoryPeriodKind.WEEK -> {
        val start = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
        start to start.plusDays(6)
    }
    GlucoseHistoryPeriodKind.MONTH -> {
        val month = YearMonth.from(anchor)
        month.atDay(1) to month.atEndOfMonth()
    }
}

fun calculateGlucoseStatistics(
    records: List<MeasurementRecord>,
    kind: MeasurementKind = MeasurementKind.CAPILLARY_GLUCOSE,
): GlucoseHistoryStatistics {
    val glucoseRecords = recordsForMeasurementKind(records, kind)
    val values = glucoseRecords.mapNotNull { (_, value) ->
        when (value) {
            is MeasurementValue.Glucose ->
                value.reading.replace(',', '.').toBigDecimalOrNull()
            is MeasurementValue.Weight -> value.kilograms
            is MeasurementValue.BloodPressure -> null
        }
    }
    val average = values.takeIf(List<BigDecimal>::isNotEmpty)?.let { numeric ->
        numeric.reduce(BigDecimal::add).divide(
            BigDecimal(numeric.size),
            1,
            RoundingMode.HALF_UP,
        )
    }
    return GlucoseHistoryStatistics(
        average = average,
        minimum = values.minOrNull(),
        maximum = values.maxOrNull(),
        totalMeasurements = glucoseRecords.size,
        numericMeasurements = values.size,
        nonNumericMeasurements = glucoseRecords.size - values.size,
    )
}

fun projectGlucoseHistoryPeriod(
    records: List<MeasurementRecord>,
    anchor: LocalDate,
    kind: GlucoseHistoryPeriodKind,
    today: LocalDate,
    followUp: GlucoseFollowUp? = null,
    decisions: List<GlucoseOccurrenceDecision> = emptyList(),
    now: LocalDateTime = today.atTime(LocalTime.MAX),
    measurementKind: MeasurementKind = MeasurementKind.CAPILLARY_GLUCOSE,
): GlucoseHistoryPeriod {
    val (start, endInclusive) = glucosePeriodBounds(anchor, kind)
    val history = projectMeasurementHistoryRange(
        records = records,
        kind = measurementKind,
        start = start,
        endInclusive = endInclusive,
        today = today,
    )
    return GlucoseHistoryPeriod(
        kind = kind,
        start = start,
        endInclusive = endInclusive,
        history = history,
        statistics = calculateGlucoseStatistics(history.records, measurementKind),
        days = projectGlucoseHistoryEvents(
            records = records,
            followUp = followUp,
            decisions = decisions,
            start = start,
            endInclusive = endInclusive,
            now = now,
            measurementKind = measurementKind,
        ),
    )
}

fun projectGlucoseHistoryEvents(
    records: List<MeasurementRecord>,
    followUp: GlucoseFollowUp?,
    decisions: List<GlucoseOccurrenceDecision>,
    start: LocalDate,
    endInclusive: LocalDate,
    now: LocalDateTime,
    measurementKind: MeasurementKind = MeasurementKind.CAPILLARY_GLUCOSE,
): List<GlucoseHistoryDay> {
    return projectMeasurementHistoryEvents(
        records, followUp, decisions, start, endInclusive, now, measurementKind,
    )
}

/** Complete declared history, independent of the week currently selected in the detail. */
fun projectGlucoseHistory(records: List<MeasurementRecord>): List<GlucoseHistoryDay> =
    records.asSequence()
        .filter { it.kind == MeasurementKind.CAPILLARY_GLUCOSE }
        .sortedWith(
            compareByDescending(MeasurementRecord::occurredAt)
                .thenByDescending(MeasurementRecord::capturedAt)
                .thenBy(MeasurementRecord::id),
        )
        .groupBy { it.occurredAt.toLocalDate() }
        .map { (date, events) -> GlucoseHistoryDay(date, events) }
