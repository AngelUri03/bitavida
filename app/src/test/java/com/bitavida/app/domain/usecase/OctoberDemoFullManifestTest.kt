package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Independent expected sets for the October demo, not calculated by report projection. */
class OctoberDemoFullManifestTest {
    @Test fun allMedicationPeriodsAndBothCutoffsMatchTheManifest() {
        val zone = ZoneId.of("America/Mexico_City")
        val start = LocalDate.of(2026, 10, 1)
        val end = start.withDayOfMonth(31)
        fun epoch(day: Int, hour: Int, minute: Int = 0) =
            start.withDayOfMonth(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        fun base(index: Int, day: Int = 1, series: String = "demo-plan-" + index): MedicationPlan {
            val id = "demo-plan-" + index
            val config = MedicationConfiguration(
                form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
                baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
                startsOn = start.withDayOfMonth(day), endsOn = end,
                administrations = listOf(MedicationAdministration(
                    id = "demo-slot-" + index, timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(if (index == 5) 10 else 7 + index, 0), reminderEnabled = false)),
            )
            val revision = MedicationPlanRevision(
                id = "demo-plan-revision-" + index, medicationPlanId = id, revisionNumber = 1,
                medicationName = "Medicamento simulado " + series, doseAsIndicated = "1 tableta",
                unitOrPresentation = "TABLET", frequencyAsIndicated = "Una vez al día",
                startsOn = start.withDayOfMonth(day), endsOn = end, originalAdditionalInstructions = null,
                scheduledDoses = emptyList(), capturedAtEpochMillis = epoch(day, 6), authorActorId = "actor",
                changeKind = MedicationPlanChangeKind.CREATED, changeReason = null, configuration = config,
            )
            return MedicationPlan(id = id, medicationSeriesId = series, personUnderCareId = "patient",
                medicalIndicationId = null, medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
                scheduleOrigin = InformationOrigin.CAREGIVER_RECORD, createdAtEpochMillis = epoch(day, 6),
                createdByActorId = "actor", contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
                revisions = listOf(revision))
        }
        val alpha = base(1)
        val beta0 = base(2)
        val b1 = beta0.currentRevision
        val b2 = b1.copy(id = "demo-beta-change", revisionNumber = 2, doseAsIndicated = "2 tabletas",
            capturedAtEpochMillis = epoch(7, 6), changeKind = MedicationPlanChangeKind.CORRECTED,
            changeReason = "Cambio simulado", configuration = requireNotNull(b1.configuration).copy(
                baseAmount = MedicationAmount("2", "tabletas")))
        val b3 = b2.copy(id = "demo-beta-pause", revisionNumber = 3, capturedAtEpochMillis = epoch(10, 12),
            changeKind = MedicationPlanChangeKind.SUSPENDED, lifecycleEffectiveAtEpochMillis = epoch(10, 12),
            configuration = requireNotNull(b2.configuration).copy(status = MedicationTreatmentStatus.SUSPENDED))
        val b4 = b3.copy(id = "demo-beta-resume", revisionNumber = 4, capturedAtEpochMillis = epoch(16, 12),
            changeKind = MedicationPlanChangeKind.REACTIVATED, lifecycleEffectiveAtEpochMillis = epoch(16, 12),
            configuration = requireNotNull(b3.configuration).copy(status = MedicationTreatmentStatus.ACTIVE))
        val beta = beta0.copy(revisions = listOf(b1, b2, b3, b4))
        val gamma0 = base(3)
        val g1 = gamma0.currentRevision
        val g2 = g1.copy(id = "demo-gamma-end", revisionNumber = 2, capturedAtEpochMillis = epoch(14, 12),
            changeKind = MedicationPlanChangeKind.COMPLETED, changeReason = "Fin simulado",
            lifecycleEffectiveAtEpochMillis = epoch(14, 12),
            configuration = requireNotNull(g1.configuration).copy(status = MedicationTreatmentStatus.COMPLETED))
        val gamma = gamma0.copy(revisions = listOf(g1, g2))
        val gammaNext = base(5, 18, gamma.medicationSeriesId)
        val delta = base(4)
        fun intake(id: String, planId: String, revisionId: String, slot: Int, day: Int, hour: Int,
            outcome: MedicationIntakeOutcome = MedicationIntakeOutcome.COMPLETED): MedicationIntakeRecord {
            val planned = start.withDayOfMonth(day).atTime(hour, 0)
            val occurrenceId = listOf(planId, revisionId, "demo-slot-" + slot, planned.toLocalDate()).joinToString(":")
            val revision = MedicationIntakeRevision(
                id = id + "-revision", intakeRecordId = id, revisionNumber = 1, outcome = outcome,
                scheduledAt = planned, actualAt = if (outcome == MedicationIntakeOutcome.OMITTED) null else planned.plusMinutes(3),
                actualAmount = if (outcome == MedicationIntakeOutcome.OMITTED) null else MedicationAmount("1", "tableta"),
                observation = null, changeKind = MedicationIntakeChangeKind.REGISTERED, changeReason = null,
                capturedAtEpochMillis = epoch(day, hour, 5), authorActorId = "actor", scheduledOccurrenceId = occurrenceId,
            )
            val versions = if (id == "demo-intake-28") listOf(revision, revision.copy(
                id = "demo-intake-correction-28", revisionNumber = 2, outcome = MedicationIntakeOutcome.PARTIAL,
                actualAmount = MedicationAmount("0.5", "tableta"), changeKind = MedicationIntakeChangeKind.CORRECTED,
                changeReason = "Corrección simulada", capturedAtEpochMillis = epoch(28, 9))) else listOf(revision)
            return MedicationIntakeRecord(id, planId, epoch(day, hour, 5), "actor", versions)
        }
        val alphaRecords = (1..29).map { day -> intake("demo-intake-" + day, alpha.id,
            "demo-plan-revision-1", 1, day, 8,
            if (day == 29) MedicationIntakeOutcome.OMITTED else MedicationIntakeOutcome.COMPLETED) }
        val extra = listOf(
            intake("demo-extra-2-8", beta.id, b2.id, 2, 8, 9),
            intake("demo-extra-2-17", beta.id, b4.id, 2, 17, 9),
            intake("demo-extra-2-30", beta.id, b4.id, 2, 30, 9),
            intake("demo-extra-3-8", gamma.id, g1.id, 3, 8, 10),
            intake("demo-extra-5-20", gammaNext.id, gammaNext.currentRevision.id, 5, 20, 10),
            intake("demo-extra-4-25", delta.id, delta.currentRevision.id, 4, 25, 11),
        )
        val plans = listOf(alpha, beta, gamma, gammaNext, delta)
        fun report(day: Int) = buildFollowUpReport(start, end, "patient",
            MedicalCareOverview(emptyList(), plans, alphaRecords + extra), emptyList(), emptyList(),
            zoneId = zone, now = start.withDayOfMonth(day).atTime(23, 59, 59))
        fun expected(day: Int): Set<String> = buildSet {
            for (date in 1..day) {
                add("demo-plan-1:" + date)
                if (date <= 10 || date >= 17) add("demo-plan-2:" + date)
                if (date <= 14 || date >= 18) add("demo-plan-3:" + date)
                add("demo-plan-4:" + date)
            }
        }
        fun actual(report: FollowUpReport): List<String> = report.rows.filter { it.section == "Medicamentos" }
            .map { requireNotNull(it.groupId).removePrefix("medication-series:") + ":" +
                requireNotNull(it.scheduledAt).dayOfMonth }
        val mid = report(15)
        assertEquals(expected(15), actual(mid).toSet())
        assertEquals(54, actual(mid).size)
        assertEquals(17, mid.summaries.single { it.label == "Tomas completas" }.count)
        assertEquals(37, mid.summaries.single { it.label == "Tomas sin confirmar" }.count)
        val final = report(31)
        assertEquals(expected(31), actual(final).toSet())
        assertEquals(115, actual(final).size)
        assertEquals(33, final.summaries.single { it.label == "Tomas completas" }.count)
        assertEquals(1, final.summaries.single { it.label == "Tomas parciales" }.count)
        assertEquals(1, final.summaries.single { it.label == "Tomas no realizadas" }.count)
        assertEquals(80, final.summaries.single { it.label == "Tomas sin confirmar" }.count)
        assertEquals((alphaRecords + extra).map { "medication-intake:" + it.id }.toSet(),
            final.rows.filter { it.sourceId?.startsWith("medication-intake:") == true && it.section == "Medicamentos" }
                .mapNotNull { it.sourceId }.toSet())
    }
}
