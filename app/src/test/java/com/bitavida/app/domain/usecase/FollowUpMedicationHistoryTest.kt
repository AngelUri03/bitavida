package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class FollowUpMedicationHistoryTest {
    @Test fun laterCorrectionDoesNotRewriteThePeriodSnapshotOrItsCount() {
        val day = LocalDate.of(2026, 1, 12)
        val moment = day.atTime(8, 0)
        fun millis(offset: Long) = day.plusDays(offset).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val planRevision = MedicationPlanRevision(
            id = "plan-r1", medicationPlanId = "plan", revisionNumber = 1,
            medicationName = "Medicamento ficticio", doseAsIndicated = "Una tableta",
            unitOrPresentation = null, frequencyAsIndicated = null, startsOn = day.minusDays(2),
            endsOn = null, originalAdditionalInstructions = null,
            scheduledDoses = listOf(ScheduledDose("dose", "plan-r1", LocalTime.of(8, 0), millis(-2),
                "actor", InformationOrigin.CAREGIVER_RECORD)),
            capturedAtEpochMillis = millis(-2), authorActorId = "actor",
            changeKind = MedicationPlanChangeKind.CREATED, changeReason = null,
        )
        val plan = MedicationPlan("plan", personUnderCareId = "patient", medicalIndicationId = null,
            medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD, createdAtEpochMillis = millis(-2),
            createdByActorId = "actor", contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            revisions = listOf(planRevision))
        val original = MedicationIntakeRevision("r1", "intake", 1, MedicationIntakeOutcome.COMPLETED,
            moment, moment, null, null, MedicationIntakeChangeKind.REGISTERED, null, millis(0), "actor")
        val corrected = MedicationIntakeRevision("r2", "intake", 2, MedicationIntakeOutcome.OMITTED,
            moment, null, null, null, MedicationIntakeChangeKind.CORRECTED, "Corrección ficticia", millis(2), "actor")
        val record = MedicationIntakeRecord("intake", "plan", millis(0), "actor", listOf(original, corrected))
        val overview = MedicalCareOverview(emptyList(), listOf(plan), listOf(record))
        val before = buildFollowUpReport(day, day, "patient", overview, emptyList(), emptyList(),
            zoneId = ZoneOffset.UTC, now = day.plusDays(5).atStartOfDay())
        assertEquals(1, before.summaries.single { it.label == "Registros de toma" }.count)
        assertTrue(before.rows.any { it.section == "Medicamentos" && it.detail.startsWith("Se tomó") })
        val after = buildFollowUpReport(day, day.plusDays(3), "patient", overview, emptyList(), emptyList(),
            zoneId = ZoneOffset.UTC, now = day.plusDays(5).atStartOfDay())
        assertEquals(0, after.summaries.single { it.label == "Registros de toma" }.count)
        assertTrue(after.rows.any { it.section == "Medicamentos" && it.detail.startsWith("No se tomó") })
    }
}
