package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUpReportAvailabilityTest {
    private val day = LocalDate.of(2026, 1, 15)
    private val emptyOverview = MedicalCareOverview(emptyList(), emptyList())
    private val emptyReport = FollowUpReport(day, day, emptyList())

    private fun availability(
        overview: MedicalCareOverview = emptyOverview,
        measurements: List<MeasurementRecord> = emptyList(),
        observations: List<ObservationRecord> = emptyList(),
        reminders: List<CareReminder> = emptyList(),
        events: List<CareEvent> = emptyList(),
        report: FollowUpReport = emptyReport,
    ) = followUpAvailability("fictional-patient", overview, measurements, observations, reminders, events, report)

    private fun plan(person: String, mode: ClinicalContentMode) = MedicationPlan(
        id = "fictional-plan", personUnderCareId = person, medicalIndicationId = null,
        medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
        scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
        createdAtEpochMillis = 0, createdByActorId = "fictional-caregiver", contentMode = mode,
        revisions = listOf(MedicationPlanRevision(
            id = "revision", medicationPlanId = "fictional-plan", revisionNumber = 1,
            medicationName = "Medicamento ficticio", doseAsIndicated = "Cantidad ficticia",
            unitOrPresentation = null, frequencyAsIndicated = null, startsOn = day,
            endsOn = null, originalAdditionalInstructions = null,
            scheduledDoses = listOf(ScheduledDose("dose", "revision", LocalTime.of(8, 0), 0,
                "fictional-caregiver", InformationOrigin.CAREGIVER_RECORD)),
            capturedAtEpochMillis = 0, authorActorId = "fictional-caregiver",
            changeKind = MedicationPlanChangeKind.CREATED, changeReason = null,
        )),
    )

    private fun indication() = MedicalIndication(
        "fictional-indication", "fictional-patient", InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
        1, "fictional-caregiver", ClinicalContentMode.REAL_CLINICAL_DATA, listOf(
            MedicalIndicationRevision(
                "revision", "fictional-indication", 1, "Indicación ficticia", day,
                1, null, null, null, MedicalIndicationStatus.ACTIVE, MedicalIndicationChangeKind.CREATED,
                null, "fictional-caregiver",
            )
        ),
    )

    private fun row(section: String, detail: String, provenance: String) =
        FollowUpReportRow(section, day.atTime(LocalTime.NOON), "Dato ficticio", detail, provenance)

    @Test fun noStoredInformationHasItsOwnEmptyState() {
        assertEquals(FollowUpAvailability.NO_INFORMATION, availability())
    }

    @Test fun planOrIndicationOutsidePeriodIsNotAnEmptyApplication() {
        val plans = MedicalCareOverview(emptyList(), listOf(plan("fictional-patient", ClinicalContentMode.REAL_CLINICAL_DATA)))
        val indications = MedicalCareOverview(listOf(indication()), emptyList())
        assertEquals(FollowUpAvailability.EMPTY_PERIOD, availability(overview = plans))
        assertEquals(FollowUpAvailability.EMPTY_PERIOD, availability(overview = indications))
    }

    @Test fun unrelatedPatientAndExcludedContentDoNotAppearAsOwnData() {
        val other = MedicalCareOverview(emptyList(), listOf(plan("other", ClinicalContentMode.REAL_CLINICAL_DATA)))
        val sample = MedicalCareOverview(emptyList(), listOf(plan("fictional-patient", ClinicalContentMode.FICTITIOUS_DEVELOPMENT)))
        assertEquals(FollowUpAvailability.NO_INFORMATION, availability(overview = other))
        assertEquals(FollowUpAvailability.NO_INFORMATION, availability(overview = sample))
    }

    @Test fun previousReadingMakesSelectedEmptyPeriodDifferentFromNoInformation() {
        val prior = MeasurementRecord("prior", MeasurementValue.Glucose("100"),
            day.minusDays(2).atStartOfDay(), day.minusDays(2).atStartOfDay())
        assertEquals(FollowUpAvailability.EMPTY_PERIOD, availability(measurements = listOf(prior)))
    }

    @Test fun pendingReminderAndFutureAppointmentAreStoredWithoutImplyingCompletion() {
        val reminder = CareReminder("fictional-reminder", "fictional-patient", "Recordatorio ficticio", null,
            day.plusDays(1).atStartOfDay(), false, CareReminderStatus.PENDING,
            day.atStartOfDay(), day.atStartOfDay(), null, 1)
        val appointment = CareEvent("fictional-appointment", "fictional-patient",
            CareEventDraft(CareEventKind.APPOINTMENT, "Cita ficticia",
                scheduledDate = day.plusDays(1), status = CareEventStatus.CONFIRMED),
            day.atStartOfDay(), day.atStartOfDay(), 1)
        assertEquals(FollowUpAvailability.EMPTY_PERIOD, availability(reminders = listOf(reminder)))
        assertEquals(FollowUpAvailability.EMPTY_PERIOD, availability(events = listOf(appointment)))
    }

    @Test fun scheduledOnlyIsNotCountedAsPerformedOrUsefulReport() {
        val scheduled = row("Mediciones programadas", "Sin confirmar", "Seguimiento programado")
        val report = emptyReport.copy(rows = listOf(scheduled))
        val prior = MeasurementRecord("prior", MeasurementValue.Glucose("100"),
            day.minusDays(2).atStartOfDay(), day.minusDays(2).atStartOfDay())
        assertEquals(FollowUpAvailability.EMPTY_PERIOD, availability(measurements = listOf(prior), report = report))
    }

    @Test fun explicitNonPerformanceAndTranscribedIndicationAreUsefulWithoutCompletedCount() {
        val notPerformed = row("Mediciones programadas", "No realizada, declarado", "Seguimiento programado")
        val indication = row("Indicaciones médicas", "Texto ficticio", "Indicación médica transcrita")
        assertEquals(FollowUpAvailability.AVAILABLE, availability(report = emptyReport.copy(rows = listOf(notPerformed))))
        assertEquals(FollowUpAvailability.AVAILABLE, availability(report = emptyReport.copy(rows = listOf(indication))))
    }
}
