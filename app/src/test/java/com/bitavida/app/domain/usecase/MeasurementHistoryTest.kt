package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementHistoryTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val morning = today.atTime(8, 15)

    @Test
    fun historyAndGraphOnlyUseSelectedMeasurementAndWeek() {
        val glucose = record("glucose", MeasurementValue.Glucose("126"))
        val pressure = record("pressure", MeasurementValue.BloodPressure("118", "72", "78"))
        val previousWeek = glucose.copy(id = "old", occurredAt = morning.minusWeeks(1))
        val week = project(listOf(glucose, pressure, previousWeek))
        assertEquals(listOf(glucose), week.records)
        assertEquals(1, week.points.size)
        assertEquals(1, week.days.sumOf { it.recordCount })
    }

    @Test
    fun hiAndLoRemainInHistoryWithoutFabricatingNumericValues() {
        val records = listOf(
            record("hi", MeasurementValue.Glucose("HI")),
            record("lo", MeasurementValue.Glucose("LO")),
            record("numeric", MeasurementValue.Glucose("126")),
        )
        val week = project(records)
        assertEquals(3, week.records.size)
        assertEquals(listOf(BigDecimal("126")), week.points.map { it.value })
        assertEquals(2, week.unplottedRecordCount)
        assertEquals(3, week.recordsOn(today).size)
    }

    @Test
    fun pressureGraphHasTwoNamedSeriesAndDoesNotPlotPulseAsPressure() {
        val week = projectMeasurementHistoryWeek(
            listOf(record("pressure", MeasurementValue.BloodPressure("118", "72", "78"))),
            MeasurementKind.BLOOD_PRESSURE, today, today,
        )
        assertEquals(
            listOf(MeasurementPlotSeries.SYSTOLIC, MeasurementPlotSeries.DIASTOLIC),
            week.points.map { it.series },
        )
        assertEquals(listOf(BigDecimal("118"), BigDecimal("72")), week.points.map { it.value })
    }

    @Test
    fun everyMeasurementKeepsItsActualTimestampWithoutDailyAverages() {
        val one = record("one", MeasurementValue.Glucose("120"))
        val two = record("two", MeasurementValue.Glucose("134")).copy(occurredAt = today.atTime(18, 30))
        val week = project(listOf(two, one))
        assertEquals(listOf("one", "two"), week.records.map { it.id })
        assertEquals(listOf(one.occurredAt, two.occurredAt), week.points.map { it.occurredAt })
        assertEquals(listOf(BigDecimal("120"), BigDecimal("134")), week.points.map { it.value })
    }

    @Test
    fun datePickerWeekUsesRealTodayForFutureDays() {
        val nextWeek = projectMeasurementHistoryWeek(
            emptyList(), MeasurementKind.CAPILLARY_GLUCOSE, today.plusWeeks(1), today,
        )
        assertTrue(nextWeek.days.all { it.state == MeasurementDayState.FUTURE })
        val previousWeek = projectMeasurementHistoryWeek(
            emptyList(), MeasurementKind.CAPILLARY_GLUCOSE, today.minusWeeks(1), today,
        )
        assertTrue(previousWeek.days.all { it.state == MeasurementDayState.WITHOUT_RECORDS })
    }

    @Test
    fun historyUsesEventDateEvenWhenCaptureHappenedInAnotherWeek() {
        val event = record("backdated", MeasurementValue.Glucose("126"))
            .copy(capturedAt = morning.plusWeeks(1))
        val week = project(listOf(event))
        assertEquals(listOf(event), week.recordsOn(today))
        assertTrue(week.recordsOn(today.plusDays(1)).isEmpty())
    }

    @Test
    fun weekCrossingYearIncludesSundayAndExcludesNextMonday() {
        val sunday = LocalDate.of(2027, 1, 3)
        val event = record("sunday", MeasurementValue.Glucose("126")).copy(occurredAt = sunday.atTime(23, 59))
        val next = event.copy(id = "monday", occurredAt = sunday.plusDays(1).atStartOfDay())
        val week = projectMeasurementHistoryWeek(
            listOf(event, next), MeasurementKind.CAPILLARY_GLUCOSE, sunday, sunday,
        )
        assertEquals(LocalDate.of(2026, 12, 28), week.start)
        assertEquals(listOf(event), week.records)
    }

    @Test
    fun weightGraphPlotsDeclaredWeightAndDoesNotMixInBmi() {
        val weight = record("weight", MeasurementValue.Weight(BigDecimal("74.20"), BigDecimal("1.68")))
        val week = projectMeasurementHistoryWeek(listOf(weight), MeasurementKind.WEIGHT, today, today)
        assertEquals(listOf(BigDecimal("74.20")), week.points.map { it.value })
        assertEquals(0, week.unplottedRecordCount)
    }

    @Test
    fun explicitReportPeriodPreservesBoundaryDatesAndDistinctSameDayReadings() {
        val start = LocalDate.of(2026, 10, 17)
        val end = LocalDate.of(2026, 12, 3)
        val first = record("first", MeasurementValue.Glucose("110"), start.atTime(7, 30))
        val second = record("second", MeasurementValue.Glucose("110"), start.atTime(7, 30))
        val last = record("last", MeasurementValue.Glucose("127"), end.atTime(23, 59))
        val before = first.copy(id = "before", occurredAt = start.minusDays(1).atTime(23, 59))
        val after = last.copy(id = "after", occurredAt = end.plusDays(1).atStartOfDay())

        val history = projectMeasurementHistoryRange(
            listOf(after, last, second, first, before),
            MeasurementKind.CAPILLARY_GLUCOSE,
            start,
            end,
            end,
        )

        assertEquals(start, history.days.first().date)
        assertEquals(end, history.days.last().date)
        assertEquals(setOf("first", "second", "last"), history.records.map { it.id }.toSet())
        assertEquals(2, history.recordsOn(start).size)
        assertEquals(3, history.points.size)
    }

    private fun project(records: List<MeasurementRecord>) =
        projectMeasurementHistoryWeek(records, MeasurementKind.CAPILLARY_GLUCOSE, today, today)

    private fun record(
        id: String,
        value: MeasurementValue,
        event: LocalDateTime = morning,
    ) = MeasurementRecord(id, value, event, event)
}
