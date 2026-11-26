package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class MeasurementReportHistoryTest {
    private val start = LocalDateTime.of(2026, 9, 2, 10, 0)
    private val correction = start.plusMonths(1)
    private val deletion = correction.plusDays(1)

    @Test fun reconstructsHistoricalValuesAndDeletionWithoutDuplicatingFacts() {
        val current = MeasurementRecord("g", MeasurementValue.Glucose("140", GlucoseContext.AFTER_MEAL),
            start, correction, glucoseMetadata = GlucoseRecordMetadata(GlucoseEntryKind.SCHEDULED,
                followUpId = "follow", scheduledOccurrenceId = "slot", scheduledAt = start, note = "corregida"))
        val versions = listOf(
            GlucoseRevision("r1", "g", 1, GlucoseRevisionOperation.CREATED, "100", GlucoseContext.FASTING,
                start, "original", "actor", start),
            GlucoseRevision("r2", "g", 2, GlucoseRevisionOperation.CORRECTED, "140", GlucoseContext.AFTER_MEAL,
                start.plusHours(1), "corregida", "actor", correction),
            GlucoseRevision("r3", "g", 3, GlucoseRevisionOperation.DELETED, "140", GlucoseContext.AFTER_MEAL,
                start.plusHours(1), "corregida", "actor", deletion),
        )
        val history = MeasurementReportHistory(listOf(current to versions), emptyList(), emptyList())
        val old = history.recordsAt(correction.minusNanos(1)).single()
        assertEquals(MeasurementValue.Glucose("100", GlucoseContext.FASTING), old.value)
        assertEquals(start, old.occurredAt)
        assertEquals("original", old.glucoseMetadata?.note)
        assertTrue(old.historicalAssociationUncertain)
        assertNull(old.scheduledOccurrenceId())
        val corrected = history.recordsAt(correction).single()
        assertEquals(MeasurementValue.Glucose("140", GlucoseContext.AFTER_MEAL), corrected.value)
        assertEquals(start.plusHours(1), corrected.occurredAt)
        assertFalse(corrected.historicalAssociationUncertain)
        assertEquals("slot", corrected.scheduledOccurrenceId())
        assertTrue(history.recordsAt(deletion).isEmpty())
        assertTrue(history.recordsAt(start.minusNanos(1)).isEmpty())
    }

    @Test fun pressurePairsAndWeightComeFromSameHistoricalRevisions() {
        val pressure = MeasurementRecord("p", MeasurementValue.BloodPressure("135", "90"), start, correction,
            bloodPressureMetadata = BloodPressureRecordMetadata(BloodPressureEntryKind.INDEPENDENT,
                actualContext = GlucoseContext.RESTING))
        val pv = listOf(
            BloodPressureRevision("p1", "p", 1, BloodPressureRevisionOperation.CREATED, "120", "78",
                GlucoseContext.RESTING, start, null, "actor", start),
            BloodPressureRevision("p2", "p", 2, BloodPressureRevisionOperation.CORRECTED, "135", "90",
                GlucoseContext.AFTER_PHYSICAL_ACTIVITY, start, "nueva", "actor", correction),
        )
        val weight = MeasurementRecord("w", MeasurementValue.Weight(BigDecimal("72")), start, correction,
            weightMetadata = WeightRecordMetadata(WeightEntryKind.INDEPENDENT))
        val wv = listOf(
            WeightRevision("w1", "w", 1, WeightRevisionOperation.CREATED, BigDecimal("74.5"), null,
                null, start, null, "actor", start),
            WeightRevision("w2", "w", 2, WeightRevisionOperation.CORRECTED, BigDecimal("72"), null,
                null, start, null, "actor", correction),
        )
        val selected = MeasurementReportHistory(emptyList(), listOf(pressure to pv), listOf(weight to wv))
            .recordsAt(correction.minusSeconds(1)).associateBy { it.id }
        assertEquals(MeasurementValue.BloodPressure("120", "78"), selected.getValue("p").value)
        assertEquals(BigDecimal("74.5"), (selected.getValue("w").value as MeasurementValue.Weight).kilograms)
    }

    @Test fun refusesOutOfOrderCaptureTimesInsteadOfInventingPastValues() {
        val record = MeasurementRecord("g", MeasurementValue.Glucose("120"), start, start)
        val created = GlucoseRevision("r1", "g", 1, GlucoseRevisionOperation.CREATED, "100",
            GlucoseContext.FASTING, start, null, "actor", start)
        val corrected = GlucoseRevision("r2", "g", 2, GlucoseRevisionOperation.CORRECTED, "120",
            GlucoseContext.FASTING, start, null, "actor", start.minusHours(1))
        assertThrows(IllegalStateException::class.java) {
            MeasurementReportHistory(listOf(record to listOf(created, corrected)), emptyList(), emptyList())
                .recordsAt(start.minusMinutes(30))
        }
    }

    @Test fun refusesIncompleteHistoryInsteadOfDisplayingCurrentValue() {
        val record = MeasurementRecord("g", MeasurementValue.Glucose("140"), start, start)
        val version = GlucoseRevision("r2", "g", 2, GlucoseRevisionOperation.CORRECTED, "140",
            GlucoseContext.FASTING, start, null, "actor", correction)
        assertThrows(IllegalStateException::class.java) {
            MeasurementReportHistory(listOf(record to listOf(version)), emptyList(), emptyList()).recordsAt(correction)
        }
    }
}
