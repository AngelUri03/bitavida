package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.*
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class MeasurementHistoryReloadTest {
    @Test
    fun recreatingAllThreeViewModelsAndReloadingDoesNotAppendFactsOrReplayRevisions() = runBlocking {
        val actor = GlucoseActor("fictitious-profile", "fictitious-caregiver", "fictitious-person", true)
        val now = LocalDateTime.of(2026, 11, 17, 18, 0)
        val glucoseFixture = measurementHistoryIncidentFixture(MeasurementKind.CAPILLARY_GLUCOSE)
        val pressureFixture = measurementHistoryIncidentFixture(MeasurementKind.BLOOD_PRESSURE)
        val weightFixture = measurementHistoryIncidentFixture(MeasurementKind.WEIGHT)
        val glucose = GlucoseFakeRepository()
        val pressure = BloodPressureFakeRepository()
        val weight = WeightFakeRepository()
        glucose.createFollowUp(actor, glucoseFixture.first)
        pressure.createFollowUp(actor, pressureFixture.first)
        weight.createFollowUp(actor, weightFixture.first)
        glucose.records += glucoseFixture.second
        pressure.records += pressureFixture.second
        weight.records += weightFixture.second
        glucose.confirmOccurrenceDecision(actor, glucoseFixture.third.single())
        pressure.confirmOccurrenceDecision(actor, pressureFixture.third.single())
        weight.confirmOccurrenceDecision(actor, weightFixture.third.single())
        val writes = glucose.followUpWrites
        val scope = this

        fun query(kind: MeasurementKind, followUp: GlucoseFollowUp?, records: List<MeasurementRecord>, decisions: List<GlucoseOccurrenceDecision>) =
            projectMeasurementCalendar(records, listOf(MeasurementScheduleHistory(kind, followUp, decisions)),
                MeasurementCalendarSelection(now.toLocalDate(), kind), now).selectedDay.events
        val expected = mapOf(
            MeasurementKind.CAPILLARY_GLUCOSE to query(MeasurementKind.CAPILLARY_GLUCOSE, glucoseFixture.first, glucoseFixture.second, glucoseFixture.third),
            MeasurementKind.BLOOD_PRESSURE to query(MeasurementKind.BLOOD_PRESSURE, pressureFixture.first, pressureFixture.second, pressureFixture.third),
            MeasurementKind.WEIGHT to query(MeasurementKind.WEIGHT, weightFixture.first, weightFixture.second, weightFixture.third),
        )
        // JVM recreation of the retained presentation layer against the same repository snapshot.
        // This does not claim a device/process or encrypted-database migration test.
        repeat(2) {
            val g = GlucoseViewModel(actor, glucose, scope, { now })
            val p = BloodPressureViewModel(actor, pressure, scope, { now })
            val w = WeightViewModel(actor, weight, scope, { now })
            yield()
            repeat(3) {
                assertFalse(g.state.value.loading || g.state.value.loadFailed)
                assertFalse(p.state.value.loading || p.state.value.loadFailed)
                assertFalse(w.state.value.loading || w.state.value.loadFailed)
                assertEquals(expected.getValue(MeasurementKind.CAPILLARY_GLUCOSE),
                    query(MeasurementKind.CAPILLARY_GLUCOSE, g.state.value.followUp, g.state.value.records, g.state.value.occurrenceDecisions))
                assertEquals(expected.getValue(MeasurementKind.BLOOD_PRESSURE),
                    query(MeasurementKind.BLOOD_PRESSURE, p.state.value.followUp, p.state.value.records, p.state.value.occurrenceDecisions))
                assertEquals(expected.getValue(MeasurementKind.WEIGHT),
                    query(MeasurementKind.WEIGHT, w.state.value.followUp, w.state.value.records, w.state.value.occurrenceDecisions))
                g.onAction(GlucoseAction.Reload)
                p.onAction(BloodPressureAction.Reload)
                w.onAction(WeightAction.Reload)
                yield()
            }
        }
        assertEquals(glucoseFixture.second, glucose.load(actor))
        assertEquals(pressureFixture.second, pressure.load(actor))
        assertEquals(weightFixture.second, weight.load(actor))
        assertEquals(glucoseFixture.third, glucose.loadOccurrenceDecisions(actor, glucoseFixture.first.id))
        assertEquals(pressureFixture.third, pressure.loadOccurrenceDecisions(actor, pressureFixture.first.id))
        assertEquals(weightFixture.third, weight.loadOccurrenceDecisions(actor, weightFixture.first.id))
        assertEquals(writes, glucose.followUpWrites)
        assertEquals(0, glucose.creates)
    }
}
