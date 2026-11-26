package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MeasurementCatalog
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class MeasurementSummary(
    val kind: MeasurementKind,
    val latest: MeasurementRecord?,
    val followUp: MeasurementFollowUp?,
)

enum class MeasurementDayState { WITH_RECORDS, WITHOUT_RECORDS, FUTURE }

data class MeasurementDay(
    val date: LocalDate,
    val recordCount: Int,
    val state: MeasurementDayState,
)

data class MeasurementOverview(
    val primary: List<MeasurementSummary>,
    val secondary: List<MeasurementSummary>,
    val week: List<MeasurementDay>,
)

/** Pure projection: counts facts, never generates scheduled events or missed measurements. */
fun projectMeasurementOverview(
    records: List<MeasurementRecord>,
    today: LocalDate,
    followUps: List<MeasurementFollowUp> = emptyList(),
    enabledSecondary: Set<MeasurementKind> = emptySet(),
): MeasurementOverview {
    fun summary(kind: MeasurementKind) = MeasurementSummary(
        kind = kind,
        latest = recordsForMeasurementKind(records, kind)
            .maxWithOrNull(
                compareBy(MeasurementRecord::occurredAt)
                    .thenBy(MeasurementRecord::capturedAt),
            ),
        followUp = followUps.firstOrNull {
            (it.kind == kind) && it.summary.isNotBlank()
        },
    )
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val counts = records.groupingBy { it.occurredAt.toLocalDate() }.eachCount()
    return MeasurementOverview(
        primary = MeasurementCatalog.primary.map(::summary),
        secondary = MeasurementCatalog.secondary.filter { it in enabledSecondary }.map(::summary),
        week = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            val count = counts[date] ?: 0
            MeasurementDay(
                date, count,
                when {
                    count > 0 -> MeasurementDayState.WITH_RECORDS
                    date > today -> MeasurementDayState.FUTURE
                    else -> MeasurementDayState.WITHOUT_RECORDS
                },
            )
        },
    )
}

/** Numeric information only. A missing or nonpositive declared height cannot produce BMI. */
fun MeasurementValue.Weight.bodyMassIndex(): BigDecimal? {
    val height = heightMeters ?: return null
    if (height.signum() <= 0 || kilograms.signum() <= 0) return null
    return kilograms.divide(height.multiply(height), 1, RoundingMode.HALF_UP)
}
