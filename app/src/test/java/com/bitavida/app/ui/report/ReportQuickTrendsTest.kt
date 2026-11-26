package com.bitavida.app.ui.report

import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportQuickTrendsTest {
    private val first = LocalDate.of(2026, 11, 1)
    private val last = first.plusDays(29)

    @Test fun separatesSameDayReadingsByTheirRegisteredTime() {
        val morning = first.plusDays(4).atTime(7, 0)
        val evening = first.plusDays(4).atTime(20, 0)
        assertTrue(quickTrendTimeFraction(morning, first, last) <
            quickTrendTimeFraction(evening, first, last))
        assertEquals(0f, quickTrendTimeFraction(first.atStartOfDay(), first, last), 0.0001f)
        assertEquals(1f, quickTrendTimeFraction(last.plusDays(1).atStartOfDay(), first, last), 0.0001f)
    }

    @Test fun pressureKeepsBothValuesAtTheSameRegisteredInstant() {
        val at = first.plusDays(8).atTime(9, 30)
        val record = MeasurementRecord("reading", MeasurementValue.BloodPressure("120", "80"), at, at)
        val points = quickTrendPoints(record)
        assertEquals(2, points.size)
        assertEquals(at, points[0].at)
        assertEquals(at, points[1].at)
        assertEquals(120.0, points[0].value, 0.0)
        assertEquals(80.0, points[1].value, 0.0)
        assertTrue(points[1].diastolic)
    }

    @Test fun nonNumericReadingIsNotReplacedWithAValue() {
        val at = first.atTime(10, 0)
        assertTrue(quickTrendPoints(MeasurementRecord("glucose", MeasurementValue.Glucose("HI"), at, at)).isEmpty())
        assertEquals(1, quickTrendPoints(MeasurementRecord("weight", MeasurementValue.Weight(BigDecimal("72.4")), at, at)).size)
    }
}
