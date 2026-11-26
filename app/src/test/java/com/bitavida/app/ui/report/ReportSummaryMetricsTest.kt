package com.bitavida.app.ui.report

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.usecase.buildFollowUpReport
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSummaryMetricsTest {
    private val day = LocalDate.of(2026, 11, 16)
    private fun reading(id: String, value: MeasurementValue, days: Long, hour: Int): MeasurementRecord {
        val time = day.plusDays(days).atTime(hour, 0)
        return MeasurementRecord(id, value, time, time)
    }

    @Test fun medicationSummaryUsesSingularWithoutMixingUnknownAndNotPerformed() {
        assertEquals("1 completa · 1 parcial · 1 no realizada · 2 sin confirmar.",
            reportMedicationCounts(1, 1, 1, 2, incomplete = false))
        assertEquals("0 completas · 2 parciales · 0 no realizadas · al menos 1 sin confirmar.",
            reportMedicationCounts(0, 2, 0, 1, incomplete = true))
    }

    @Test fun pressureAndWeightRemainVisibleAlongsideGlucoseContexts() {
        val records = listOf(
            reading("g1", MeasurementValue.Glucose("100", GlucoseContext.FASTING), 0, 7),
            reading("g2", MeasurementValue.Glucose("120", GlucoseContext.FASTING), 0, 8),
            reading("g3", MeasurementValue.Glucose("140", GlucoseContext.AFTER_MEAL), 0, 9),
            reading("g4", MeasurementValue.Glucose("150", GlucoseContext.BEFORE_SLEEP), 0, 10),
            reading("p1", MeasurementValue.BloodPressure("120", "80"), 0, 11),
            reading("p2", MeasurementValue.BloodPressure("130", "86"), 1, 11),
            reading("p-incomplete", MeasurementValue.BloodPressure("140", "NO REGISTRADO"), 1, 12),
            reading("w1", MeasurementValue.Weight(BigDecimal("74.2")), 0, 7),
            reading("w2", MeasurementValue.Weight(BigDecimal("73.8")), 1, 7),
            // A later measurement must not leak past the snapshot cutoff.
            reading("future", MeasurementValue.Weight(BigDecimal("100")), 2, 7),
        )
        val report = buildFollowUpReport(
            day, day.plusDays(2), "persona-ficticia", MedicalCareOverview(emptyList(), emptyList()),
            records, emptyList(), zoneId = ZoneOffset.UTC, now = day.plusDays(1).atTime(23, 0),
        )
        val selection = reportSummaryMetrics(report)
        assertEquals(4, selection.cards.size)
        assertTrue(selection.hasMore)
        val allGlucose = (selection.cards + selection.additionalGlucose).filter { it.label.startsWith("Glucosa") }
        assertEquals(3, allGlucose.size)
        assertTrue(allGlucose.any { it.label.contains("después de comer") && it.value == "140.0 mg/dL" })
        assertTrue(allGlucose.any { it.label.contains("antes de dormir") && it.value == "150.0 mg/dL" })
        assertTrue(selection.additionalGlucose.all { it.detail.contains("lectura") })
        val glucose = selection.cards.first { it.label.contains("en ayunas") }
        assertTrue(glucose.label.contains("promedio"))
        assertEquals("110.0 mg/dL", glucose.value)
        assertTrue(glucose.detail.contains("2 lecturas"))
        val pressure = selection.cards.single { it.label.startsWith("Presión") }
        assertEquals("125.0 / 83.0 mmHg", pressure.value)
        assertEquals("2 pares", pressure.detail)
        val weight = selection.cards.single { it.label.startsWith("Peso") }
        assertEquals("73.8 kg", weight.value)
        assertTrue(weight.detail.contains("17 nov 2026"))
        assertTrue(weight.detail.contains("2 registros"))
        assertTrue(weight.detail.contains("Cambio -0.4 kg"))
    }

    @Test fun singleReadingUsesSingularAndDoesNotInventWeightChange() {
        val values = listOf(
            reading("glucose", MeasurementValue.Glucose("90", GlucoseContext.FASTING), 0, 7),
            reading("pressure", MeasurementValue.BloodPressure("120", "80"), 0, 8),
            reading("weight", MeasurementValue.Weight(BigDecimal("72")), 0, 9),
        )
        val report = buildFollowUpReport(day, day, "persona-ficticia", MedicalCareOverview(emptyList(), emptyList()),
            values, emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(23, 0))
        val metrics = reportSummaryMetrics(report)
        assertFalse(metrics.hasMore)
        assertTrue(metrics.cards.first { it.label.startsWith("Glucosa") }.detail.contains("1 lectura"))
        assertEquals("1 par", metrics.cards.first { it.label.startsWith("Presión") }.detail)
        val weight = metrics.cards.first { it.label.startsWith("Peso") }
        assertTrue(weight.detail.contains("1 registro"))
        assertFalse(weight.detail.contains("Cambio"))
    }
}
