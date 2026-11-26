package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseTargetRange
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseWeekPlotTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun coincidentEventsKeepUniqueOrderedPositionsInsteadOfBeingAveraged() {
        val records = (0..7).map { record("event-$it", "128") }.reversed()
        val plot = plot(records)
        val dots = plot.days.single { it.day.date == today }.dots
        assertEquals(8, dots.size)
        assertEquals(8, dots.map { it.positionWithinDay }.distinct().size)
        assertEquals(records.map { it.id }.sorted(), dots.map { it.point.recordId })
        assertTrue(dots.all { it.positionWithinDay > 0f && it.positionWithinDay < 1f })
        assertTrue(dots.all { it.point.value == BigDecimal("128") })
    }

    @Test
    fun axisUsesEvenReadableStepsAndContainsEveryRecordedValue() {
        val plot = plot(listOf(record("one", "99.2"), record("two", "128"), record("three", "166.4")))
        val axis = plot.axis!!
        assertTrue(axis.ticks.size in 3..8)
        assertTrue(axis.lower <= BigDecimal("99.2"))
        assertTrue(axis.upper >= BigDecimal("166.4"))
        val steps = axis.ticks.zipWithNext { a, b -> b.subtract(a) }
        assertTrue(steps.all { it.compareTo(steps.first()) == 0 })
        assertTrue(plot.days.flatMap { it.dots }.all { axis.fraction(it.point.value) in 0f..1f })
    }

    @Test
    fun constantZeroAndFractionalSeriesHaveNonzeroScales() {
        listOf("128", "0", "0.125").forEach { value ->
            val axis = plot(listOf(record("constant", value))).axis!!
            assertTrue(axis.upper > axis.lower)
            assertTrue(axis.fraction(BigDecimal(value)).isFinite())
            assertTrue(axis.fraction(BigDecimal(value)) in 0f..1f)
        }
    }

    @Test
    fun hiAndLoStayCountedWithoutAnInventedNumericalAxis() {
        val plot = plot(listOf(record("hi", "HI"), record("lo", "LO")))
        assertNull(plot.axis)
        assertEquals(2, plot.unplottedRecordCount)
        val day = plot.days.single { it.day.date == today }
        assertEquals(2, day.day.recordCount)
        assertEquals(MeasurementDayState.WITH_RECORDS, day.day.state)
        assertTrue(day.dots.isEmpty())
    }

    @Test
    fun emptyWeekKeepsMissingDaysAndFutureDatesDistinct() {
        val plot = plot(emptyList())
        assertNull(plot.axis)
        assertEquals(7, plot.days.size)
        assertTrue(plot.days.filter { it.day.date <= today }.all { it.day.state == MeasurementDayState.WITHOUT_RECORDS })
        assertTrue(plot.days.filter { it.day.date > today }.all { it.day.state == MeasurementDayState.FUTURE })
    }

    @Test
    fun backdatedAndMultipleEventsRemainInTheirActualDays() {
        val one = record("morning", "118")
        val two = record("evening", "142").copy(occurredAt = today.atTime(20, 0))
        val old = record("backdated", "130").copy(
            occurredAt = today.minusDays(1).atTime(10, 0),
            capturedAt = today.plusWeeks(1).atStartOfDay(),
        )
        val plot = plot(listOf(two, old, one))
        assertEquals(listOf("morning", "evening"), plot.days.single { it.day.date == today }.dots.map { it.point.recordId })
        assertEquals(listOf("backdated"), plot.days.single { it.day.date == today.minusDays(1) }.dots.map { it.point.recordId })
        assertNotNull(plot.axis)
    }

    @Test
    fun decimalCommaAndEventsBeyondTheVisibleWindowKeepTheirWeeklyScale() {
        val monday = today.minusDays(2)
        val sunday = monday.plusDays(6)
        val records = listOf(
            record("monday", "99,5").copy(occurredAt = monday.atTime(8, 0)),
            record("today", "128"),
            record("sunday", "166.4").copy(occurredAt = sunday.atTime(8, 0)),
        )
        val plot = plot(records)
        assertEquals(7, plot.days.size)
        assertEquals(3, plot.days.sumOf { it.dots.size })
        assertEquals(BigDecimal("99.5"), plot.days.first().dots.single().point.value)
        val axis = requireNotNull(plot.axis)
        assertTrue(axis.lower <= BigDecimal("99.5"))
        assertTrue(axis.upper >= BigDecimal("166.4"))
    }

    @Test
    fun configuredTargetExpandsTheAxisWithoutInventingOneWhenAbsent() {
        val week = projectMeasurementHistoryWeek(
            records = listOf(record("reading", "128")),
            kind = MeasurementKind.CAPILLARY_GLUCOSE,
            weekContaining = today,
            today = today,
        )
        val withoutTarget = projectGlucoseWeekPlot(week)
        val withTarget = projectGlucoseWeekPlot(
            week,
            GlucoseTargetRange(BigDecimal("70"), BigDecimal("180")),
        )

        assertTrue(requireNotNull(withoutTarget.axis).lower > BigDecimal("70"))
        assertTrue(requireNotNull(withTarget.axis).lower <= BigDecimal("70"))
        assertTrue(requireNotNull(withTarget.axis).upper >= BigDecimal("180"))
    }

    private fun plot(records: List<MeasurementRecord>) = projectGlucoseWeekPlot(
        projectMeasurementHistoryWeek(records, MeasurementKind.CAPILLARY_GLUCOSE, today, today),
    )

    private fun record(id: String, value: String): MeasurementRecord {
        val time = today.atTime(8, 15)
        return MeasurementRecord(id, MeasurementValue.Glucose(value), time, time)
    }
}
