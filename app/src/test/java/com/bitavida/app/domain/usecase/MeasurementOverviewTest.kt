package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementOverviewTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun emptyOverviewAlwaysContainsThreePrimaryMeasurementsInOrder() {
        val overview = projectMeasurementOverview(emptyList(), today)
        assertEquals(
            listOf(MeasurementKind.CAPILLARY_GLUCOSE, MeasurementKind.BLOOD_PRESSURE, MeasurementKind.WEIGHT),
            overview.primary.map { it.kind },
        )
        assertTrue(overview.primary.all { it.latest == null && it.followUp == null })
        assertTrue(overview.secondary.isEmpty())
    }

    @Test
    fun suspendedFollowUpDoesNotHideMeasurementOrItsLatestReading() {
        val record = glucose("reading", today.atTime(8, 0))
        val followUp = MeasurementFollowUp(MeasurementKind.CAPILLARY_GLUCOSE, "Frecuencia ficticia", true)
        val overview = projectMeasurementOverview(listOf(record), today, listOf(followUp))
        assertEquals(3, overview.primary.size)
        assertEquals(record, overview.primary.first().latest)
        assertEquals(followUp, overview.primary.first().followUp)
    }

    @Test
    fun lastReadingUsesEventTimeRatherThanBackdatedCaptureTime() {
        val olderEvent = glucose("older", today.minusDays(1).atTime(8, 0)).copy(capturedAt = today.atTime(10, 0))
        val newerEvent = glucose("newer", today.atTime(8, 0))
        val overview = projectMeasurementOverview(listOf(olderEvent, newerEvent), today)
        assertEquals("newer", overview.primary.first().latest?.id)
        assertEquals(1, overview.week.first { it.date == today.minusDays(1) }.recordCount)
        assertEquals(1, overview.week.first { it.date == today }.recordCount)
    }

    @Test
    fun weekCountsRecordsWithoutInferringMissedFollowUps() {
        val records = listOf(
            glucose("one", today.atTime(8, 0)),
            glucose("two", today.atTime(17, 0)),
        )
        val overview = projectMeasurementOverview(records, today)
        assertEquals(LocalDate.of(2026, 11, 16), overview.week.first().date)
        assertEquals(7, overview.week.size)
        assertEquals(MeasurementDayState.WITHOUT_RECORDS, overview.week.first().state)
        assertEquals(2, overview.week.first { it.date == today }.recordCount)
        assertEquals(MeasurementDayState.WITH_RECORDS, overview.week.first { it.date == today }.state)
        assertTrue(overview.week.filter { it.date > today }.all { it.state == MeasurementDayState.FUTURE })
    }

    @Test
    fun weekIncludesSundayAndExcludesAdjacentWeeks() {
        val sunday = LocalDate.of(2027, 1, 3)
        val records = listOf(
            glucose("before", sunday.minusDays(7).atTime(8, 0)),
            glucose("sunday", sunday.atTime(8, 0)),
            glucose("after", sunday.plusDays(1).atTime(8, 0)),
        )
        val week = projectMeasurementOverview(records, sunday).week
        assertEquals(LocalDate.of(2026, 12, 28), week.first().date)
        assertEquals(sunday, week.last().date)
        assertEquals(1, week.sumOf { it.recordCount })
    }

    @Test
    fun meterIndicatorsArePreservedWithoutInterpretation() {
        listOf("HI", "LO", "126").forEach { transcription ->
            val record = glucose("fixture", today.atTime(8, 0))
                .copy(value = MeasurementValue.Glucose(transcription))
            val latest = projectMeasurementOverview(listOf(record), today).primary.first().latest
            assertEquals(transcription, (latest?.value as MeasurementValue.Glucose).reading)
        }
    }

    @Test
    fun blankProgrammingDoesNotProduceFollowUpText() {
        val overview = projectMeasurementOverview(
            emptyList(), today,
            listOf(MeasurementFollowUp(MeasurementKind.WEIGHT, " ")),
        )
        assertNull(overview.primary.last().followUp)
    }

    @Test
    fun bmiRequiresAnExplicitPositiveHeightAndNeverAssignsACategory() {
        val weight = MeasurementValue.Weight(BigDecimal("74.2"))
        assertNull(weight.bodyMassIndex())
        assertNull(weight.copy(heightMeters = BigDecimal.ZERO).bodyMassIndex())
        assertNull(weight.copy(heightMeters = BigDecimal("-1")).bodyMassIndex())
        assertEquals(BigDecimal("26.3"), weight.copy(heightMeters = BigDecimal("1.68")).bodyMassIndex())
    }

    private fun glucose(id: String, event: LocalDateTime) =
        MeasurementRecord(id, MeasurementValue.Glucose("126"), event, event)
}
