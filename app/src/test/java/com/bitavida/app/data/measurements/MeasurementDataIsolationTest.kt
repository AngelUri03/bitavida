package com.bitavida.app.data.measurements

import com.bitavida.app.data.local.secure.BloodPressureMeasurementEntity
import com.bitavida.app.data.local.secure.GlucoseMeasurementEntity
import com.bitavida.app.data.local.secure.WeightMeasurementEntity
import com.bitavida.app.domain.model.BloodPressureEntryKind
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseEntryKind
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.WeightEntryKind
import com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind
import com.bitavida.app.domain.usecase.projectBloodPressureHistoryPeriod
import com.bitavida.app.domain.usecase.projectGlucoseHistoryPeriod
import com.bitavida.app.domain.usecase.projectMeasurementHistoryWeek
import com.bitavida.app.domain.usecase.projectMeasurementOverview
import com.bitavida.app.domain.usecase.recordsForMeasurementKind
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementDataIsolationTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val occurredAt = LocalDateTime.of(2026, 11, 18, 11, 27)

    @Test
    fun persistedRowsRestoreWithTheirOwnMeasurementKinds() {
        val restored = persistedRecords()

        assertEquals(
            listOf(
                MeasurementKind.CAPILLARY_GLUCOSE,
                MeasurementKind.BLOOD_PRESSURE,
                MeasurementKind.WEIGHT,
            ),
            restored.map { it.kind },
        )
        assertEquals("glucose-row", restored[0].id)
        assertEquals("pressure-row", restored[1].id)
        assertEquals("weight-row", restored[2].id)
    }

    @Test
    fun weightContextCompatibilityPreservesLegacyAndHidesTechnicalAbsence() {
        val legacy = persistedWeight().toDomain()
        val withoutContext = persistedWeight().copy(
            id = "weight-without-context",
            actualContext = GlucoseContext.UNSPECIFIED.name,
        ).toDomain()

        assertEquals(
            GlucoseContext.UPON_WAKING,
            legacy.weightMetadata?.actualContext,
        )
        assertNull(withoutContext.weightMetadata?.actualContext)
    }

    @Test
    fun weightRemainsEmptyWhenOnlyBloodPressureWasPersisted() {
        val pressureOnly = listOf(persistedPressure().toDomain())
        val primary = projectMeasurementOverview(
            pressureOnly,
            today,
        ).primary
        val week = projectMeasurementHistoryWeek(
            records = pressureOnly,
            kind = MeasurementKind.WEIGHT,
            weekContaining = today,
            today = today,
        )
        val history = projectGlucoseHistoryPeriod(
            records = pressureOnly,
            anchor = today,
            kind = GlucoseHistoryPeriodKind.WEEK,
            today = today,
            measurementKind = MeasurementKind.WEIGHT,
        )

        assertNull(
            primary.single {
                it.kind == MeasurementKind.WEIGHT
            }.latest,
        )
        assertTrue(week.records.isEmpty())
        assertTrue(week.points.isEmpty())
        assertEquals(0, history.statistics.totalMeasurements)
        assertTrue(history.days.all { it.records.isEmpty() })
    }

    @Test
    fun latestHistoryPlotsAndStatisticsStayIsolatedForAllKinds() {
        val restored = persistedRecords()
        val overview = projectMeasurementOverview(restored, today)

        MeasurementKind.entries.forEach { kind ->
            val expectedId = when (kind) {
                MeasurementKind.CAPILLARY_GLUCOSE -> "glucose-row"
                MeasurementKind.BLOOD_PRESSURE -> "pressure-row"
                MeasurementKind.WEIGHT -> "weight-row"
            }
            assertEquals(
                expectedId,
                overview.primary.single { it.kind == kind }.latest?.id,
            )
        }

        val glucoseWeek = projectMeasurementHistoryWeek(
            restored,
            MeasurementKind.CAPILLARY_GLUCOSE,
            today,
            today,
        )
        val pressureWeek = projectMeasurementHistoryWeek(
            restored,
            MeasurementKind.BLOOD_PRESSURE,
            today,
            today,
        )
        val weightWeek = projectMeasurementHistoryWeek(
            restored,
            MeasurementKind.WEIGHT,
            today,
            today,
        )
        assertEquals(listOf("glucose-row"), glucoseWeek.records.map { it.id })
        assertEquals(1, glucoseWeek.points.size)
        assertEquals(listOf("pressure-row"), pressureWeek.records.map { it.id })
        assertEquals(2, pressureWeek.points.size)
        assertEquals(listOf("weight-row"), weightWeek.records.map { it.id })
        assertEquals(1, weightWeek.points.size)

        val glucoseHistory = projectGlucoseHistoryPeriod(
            restored,
            today,
            GlucoseHistoryPeriodKind.WEEK,
            today,
        )
        val pressureHistory = projectBloodPressureHistoryPeriod(
            restored,
            today,
            GlucoseHistoryPeriodKind.WEEK,
            today,
        )
        val weightHistory = projectGlucoseHistoryPeriod(
            records = restored,
            anchor = today,
            kind = GlucoseHistoryPeriodKind.WEEK,
            today = today,
            measurementKind = MeasurementKind.WEIGHT,
        )
        assertEquals(BigDecimal("126.0"), glucoseHistory.statistics.average)
        assertEquals(1, pressureHistory.statistics.totalMeasurements)
        assertEquals(BigDecimal("74.3"), weightHistory.statistics.average)
        assertEquals(1, weightHistory.statistics.totalMeasurements)
    }

    @Test
    fun changingDestinationsNeverReusesAnotherKindsRecords() {
        val restoredAfterRestart = persistedRecords().map { it.copy() }
        val navigation = listOf(
            MeasurementKind.WEIGHT,
            MeasurementKind.BLOOD_PRESSURE,
            MeasurementKind.CAPILLARY_GLUCOSE,
            MeasurementKind.WEIGHT,
        )

        val selectedIds = navigation.map { kind ->
            recordsForMeasurementKind(restoredAfterRestart, kind)
                .map { it.id }
        }

        assertEquals(
            listOf(
                listOf("weight-row"),
                listOf("pressure-row"),
                listOf("glucose-row"),
                listOf("weight-row"),
            ),
            selectedIds,
        )
    }

    private fun persistedRecords() = listOf(
        persistedGlucose().toDomain(),
        persistedPressure().toDomain(),
        persistedWeight().toDomain(),
    )

    private fun persistedGlucose() = GlucoseMeasurementEntity(
        id = "glucose-row",
        profileId = "profile",
        caregiverId = "caregiver",
        personUnderCareId = "person",
        reading = "126",
        actualContext = GlucoseContext.FASTING.name,
        actualAt = occurredAt.toString(),
        capturedAt = occurredAt.toString(),
        origin = "CAREGIVER_DECLARED",
        entryKind = GlucoseEntryKind.INDEPENDENT.name,
        followUpId = null,
        scheduledOccurrenceId = null,
        scheduledAt = null,
        expectedContext = null,
        note = null,
    )

    private fun persistedPressure() = BloodPressureMeasurementEntity(
        id = "pressure-row",
        profileId = "profile",
        caregiverId = "caregiver",
        personUnderCareId = "person",
        systolic = "180",
        diastolic = "115",
        actualContext = GlucoseContext.RESTING.name,
        actualAt = occurredAt.toString(),
        capturedAt = occurredAt.toString(),
        origin = "CAREGIVER_DECLARED",
        entryKind = BloodPressureEntryKind.INDEPENDENT.name,
        followUpId = null,
        scheduledOccurrenceId = null,
        scheduledAt = null,
        expectedContext = null,
        note = null,
    )

    private fun persistedWeight() = WeightMeasurementEntity(
        id = "weight-row",
        profileId = "profile",
        caregiverId = "caregiver",
        personUnderCareId = "person",
        kilograms = "74.3",
        heightMeters = "1.68",
        actualContext = GlucoseContext.UPON_WAKING.name,
        actualAt = occurredAt.toString(),
        capturedAt = occurredAt.toString(),
        origin = "CAREGIVER_DECLARED",
        entryKind = WeightEntryKind.INDEPENDENT.name,
        followUpId = null,
        scheduledOccurrenceId = null,
        scheduledAt = null,
        expectedContext = null,
        note = null,
    )
}
