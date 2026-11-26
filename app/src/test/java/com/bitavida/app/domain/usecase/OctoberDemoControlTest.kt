package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent control manifest: daily 08:00, with explicit expected IDs and outcomes. */
class OctoberDemoControlTest {
    @Test fun measurementManifestHasExpectedIdsAndStatisticsAtBothCuts() {
        val zone = ZoneId.of("America/Mexico_City")
        val start = LocalDate.of(2026, 10, 1)
        val contexts = listOf(GlucoseContext.FASTING, GlucoseContext.AFTER_MEAL, GlucoseContext.BEFORE_SLEEP)
        val records = (1..31 step 3).mapIndexed { index, day ->
            val moment = start.withDayOfMonth(day).atTime(7, 0)
            MeasurementRecord("demo-glucose-" + day, MeasurementValue.Glucose((100 + day).toString(),
                contexts[index % contexts.size]), moment, moment.plusMinutes(5))
        } + listOf(3, 9, 15, 21, 27).mapIndexed { index, day ->
            val moment = start.withDayOfMonth(day).atTime(8, 0)
            MeasurementRecord("demo-pressure-" + day, MeasurementValue.BloodPressure((120 + index).toString(),
                (78 + index).toString()), moment, moment.plusMinutes(5))
        } + listOf(1, 11, 21, 31).mapIndexed { index, day ->
            val moment = start.withDayOfMonth(day).atTime(7, 30)
            MeasurementRecord("demo-weight-" + day,
                MeasurementValue.Weight(java.math.BigDecimal(listOf("74.0", "73.9", "73.8", "73.7")[index])),
                moment, moment.plusMinutes(5))
        }
        fun report(day: Int) = buildFollowUpReport(start, start.withDayOfMonth(31), "patient",
            MedicalCareOverview(emptyList(), emptyList()), records, emptyList(),
            zoneId = zone, now = start.withDayOfMonth(day).atTime(23, 59, 59))
        val final = report(31)
        assertEquals(records.map { it.id }.toSet(), final.measurements.map { it.id }.toSet())
        val statistics = requireNotNull(final.statistics)
        assertEquals(114.5, requireNotNull(statistics.glucoseByContext[GlucoseContext.FASTING]).average, 0.00001)
        assertEquals(4, requireNotNull(statistics.glucoseByContext[GlucoseContext.FASTING]).count)
        assertEquals(117.5, requireNotNull(statistics.glucoseByContext[GlucoseContext.AFTER_MEAL]).average, 0.00001)
        assertEquals(116.0, requireNotNull(statistics.glucoseByContext[GlucoseContext.BEFORE_SLEEP]).average, 0.00001)
        assertEquals(122.0, requireNotNull(statistics.systolic).average, 0.00001)
        assertEquals(80.0, requireNotNull(statistics.diastolic).average, 0.00001)
        assertEquals(-0.3, requireNotNull(statistics.weightDifference), 0.00001)
        val middle = report(15)
        val expectedIds = records.filter { it.occurredAt.toLocalDate() <= start.withDayOfMonth(15) }.map { it.id }.toSet()
        assertEquals(expectedIds, middle.measurements.map { it.id }.toSet())
        val middleStats = requireNotNull(middle.statistics)
        assertEquals(105.5, requireNotNull(middleStats.glucoseByContext[GlucoseContext.FASTING]).average, 0.00001)
        assertEquals(108.5, requireNotNull(middleStats.glucoseByContext[GlucoseContext.AFTER_MEAL]).average, 0.00001)
        assertEquals(107.0, requireNotNull(middleStats.glucoseByContext[GlucoseContext.BEFORE_SLEEP]).average, 0.00001)
        assertEquals(121.0, requireNotNull(middleStats.systolic).average, 0.00001)
        assertEquals(79.0, requireNotNull(middleStats.diastolic).average, 0.00001)
        assertEquals(-0.1, requireNotNull(middleStats.weightDifference), 0.00001)
    }

    @Test fun thirtyOneScheduledOccasionsHaveOneEffectiveOutcomeAndOneAuditHistory() {
        val zone = ZoneId.of("America/Mexico_City")
        val start = LocalDate.of(2026, 10, 1)
        val end = start.withDayOfMonth(31)
        fun epoch(day: Int, hour: Int, minute: Int = 0) =
            start.withDayOfMonth(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val config = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = start, endsOn = end, administrations = listOf(MedicationAdministration(
                id = "demo-slot-1", timing = MedicationAdministrationTiming.EXACT_TIME,
                exactTime = LocalTime.of(8, 0), reminderEnabled = false)),
        )
        val revision = MedicationPlanRevision(
            id = "demo-plan-revision-1", medicationPlanId = "demo-plan-1", revisionNumber = 1,
            medicationName = "Medicamento Alfa simulado", doseAsIndicated = "1 tableta",
            unitOrPresentation = "TABLET", frequencyAsIndicated = "Una vez al día",
            startsOn = start, endsOn = end, originalAdditionalInstructions = null,
            scheduledDoses = emptyList(), capturedAtEpochMillis = epoch(1, 6), authorActorId = "actor",
            changeKind = MedicationPlanChangeKind.CREATED, changeReason = null, configuration = config,
        )
        val plan = MedicationPlan(
            id = "demo-plan-1", personUnderCareId = "patient", medicalIndicationId = null,
            medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD, createdAtEpochMillis = epoch(1, 6),
            createdByActorId = "actor", contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            revisions = listOf(revision),
        )
        val records = (1..29).map { day ->
            val id = "demo-intake-$day"
            val scheduled = start.withDayOfMonth(day).atTime(8, 0)
            val occurrence = "demo-plan-1:demo-plan-revision-1:demo-slot-1:" + scheduled.toLocalDate()
            val original = MedicationIntakeRevision(
                id = id + "-revision", intakeRecordId = id, revisionNumber = 1,
                outcome = if (day == 29) MedicationIntakeOutcome.OMITTED else MedicationIntakeOutcome.COMPLETED,
                scheduledAt = scheduled, actualAt = if (day == 29) null else scheduled.plusMinutes(3),
                actualAmount = if (day == 29) null else MedicationAmount("1", "tableta"),
                observation = null, changeKind = MedicationIntakeChangeKind.REGISTERED,
                changeReason = null, capturedAtEpochMillis = epoch(day, 8, 5), authorActorId = "actor",
                scheduledOccurrenceId = occurrence,
            )
            val revisions = if (day == 28) listOf(original, original.copy(
                id = "demo-intake-correction-28", revisionNumber = 2, outcome = MedicationIntakeOutcome.PARTIAL,
                actualAmount = MedicationAmount("0.5", "tableta"), changeKind = MedicationIntakeChangeKind.CORRECTED,
                changeReason = "Corrección simulada", capturedAtEpochMillis = epoch(28, 9))) else listOf(original)
            MedicationIntakeRecord(id, plan.id, epoch(day, 8, 5), "actor", revisions)
        }
        fun report(day: Int) = buildFollowUpReport(start, end, "patient",
            MedicalCareOverview(emptyList(), listOf(plan), records), emptyList(), emptyList(),
            zoneId = zone, now = start.withDayOfMonth(day).atTime(23, 59, 59))
        val final = report(31)
        val expected = (1..29).mapTo(mutableSetOf()) { "medication-intake:demo-intake-" + it }
        expected += (30..31).map { day ->
            "medication-expected:demo-plan-1:demo-plan-revision-1:demo-slot-1:" + start.withDayOfMonth(day)
        }
        assertEquals(expected, final.rows.filter { it.section == "Medicamentos" }.mapNotNull { it.sourceId }.toSet())
        val counts = final.summaries.associate { it.label to it.count }
        assertEquals(27, counts["Tomas completas"] ?: -1)
        assertEquals(1, counts["Tomas parciales"] ?: -1)
        assertEquals(1, counts["Tomas no realizadas"] ?: -1)
        assertEquals(2, counts["Tomas sin confirmar"] ?: -1)
        assertEquals(1, final.rows.count { it.section == "Cambios de medicamentos" &&
            it.sourceId == "medication-intake:demo-intake-28" })
        val middle = report(15)
        assertEquals(15, middle.summaries.single { it.label == "Tomas completas" }.count)
        assertTrue(middle.rows.none { it.sourceId == "medication-intake:demo-intake-28" })
    }
}
