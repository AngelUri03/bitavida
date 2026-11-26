package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ReportRecordedDataPresentationTest {
    private val day = LocalDate.of(2026, 11, 18)

    private fun revision(configuration: MedicationConfiguration? = null) = MedicationPlanRevision(
        id = "r1", medicationPlanId = "p", revisionNumber = 1, medicationName = "Ficticio",
        doseAsIndicated = "1 tableta", unitOrPresentation = null, frequencyAsIndicated = "Frecuencia transcrita",
        startsOn = day, endsOn = null, originalAdditionalInstructions = null,
        scheduledDoses = if (configuration == null) listOf(ScheduledDose(
            "dose", "r1", LocalTime.of(8, 0), 0, "actor", InformationOrigin.CAREGIVER_RECORD,
        )) else emptyList(),
        capturedAtEpochMillis = 0, authorActorId = "actor", changeKind = MedicationPlanChangeKind.CREATED,
        changeReason = null, configuration = configuration,
    )

    private fun config(kind: MedicationFrequencyKind) = MedicationConfiguration(
        form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
        baseAmount = MedicationAmount("1", "tableta"), frequency = kind, startsOn = day,
        administrations = listOf(MedicationAdministration("slot", MedicationAdministrationTiming.EXACT_TIME, exactTime = LocalTime.of(8, 0))),
    )

    @Test fun compositePresentationTranslatesOnlyRecognizedFirstToken() {
        assertEquals("Tópico · 2 g", reportPresentation("TOPICAL · 2 g"))
        assertEquals("Inhalador · 1 inhalación", reportPresentation("INHALER · 1 inhalación"))
        assertEquals("Parche · 12 horas · nota", reportPresentation("PATCH · 12 horas · nota"))
        assertEquals("Nombre libre · PATCH · 2", reportPresentation("Nombre libre · PATCH · 2"))
        assertEquals("TOPICAL-extra · 2 g", reportPresentation("TOPICAL-extra · 2 g"))
        assertEquals("Tópico ·  2 g  ", reportPresentation("TOPICAL ·  2 g  "))
    }

    @Test fun hourlyWindowPreservesIntervalAndOvernightBoundary() {
        val result = reportMedicationSchedule(revision(config(MedicationFrequencyKind.EVERY_N_HOURS).copy(
            hourlyInterval = 6, hourlyMode = MedicationHourlyIntervalMode.DAILY_WINDOW,
            hourlyWindowStart = LocalTime.of(20, 0), hourlyWindowEnd = LocalTime.of(8, 0),
            hourlyWindowEndsNextDay = true, hourlyFirstAdministration = LocalTime.of(20, 0),
        )))!!
        assertTrue(result.contains("Cada 6 horas"))
        assertTrue(result.contains("20:00–08:00 del día siguiente"))
        assertTrue(result.contains("Primera toma de la ventana: 20:00"))
        assertTrue(result.contains("Frecuencia transcrita"))
    }

    @Test fun codedAmountsUseTheSameLocalizedPresentationAsTheReport() {
        val result = reportMedicationSchedule(revision(config(MedicationFrequencyKind.EVERY_DAY).copy(
            administrations = listOf(MedicationAdministration(
                "slot", MedicationAdministrationTiming.EXACT_TIME,
                exactTime = LocalTime.of(8, 0), amountOverride = MedicationAmount("2", "CAPSULE"),
            )),
        )))!!
        assertTrue(result.contains("2 Cápsula"))
    }

    @Test fun selectedDaysAndIndividualWindowsRemainExplicit() {
        val result = reportMedicationSchedule(revision(config(MedicationFrequencyKind.SPECIFIC_DAYS).copy(
            weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
            administrations = listOf(MedicationAdministration(
                "window", MedicationAdministrationTiming.TIME_WINDOW,
                windowStart = LocalTime.of(8, 0), windowEnd = LocalTime.of(10, 0),
                amountOverride = MedicationAmount("2", "gotas"),
            )),
        )))!!
        assertTrue(result.contains("lunes, viernes"))
        assertTrue(result.contains("08:00–10:00"))
        assertTrue(result.contains("2 gotas"))
    }

    @Test fun cycleAndDayIntervalRetainTheirReferenceDates() {
        val cycle = reportMedicationSchedule(revision(config(MedicationFrequencyKind.CYCLE).copy(
            cycle = MedicationCycle(5, 2, day, 3),
        )))!!
        assertTrue(cycle.contains("5 días de toma y 2 de descanso"))
        assertTrue(cycle.contains("18/11/2026"))
        assertTrue(cycle.contains("Número de ciclos: 3"))
        val days = reportMedicationSchedule(revision(config(MedicationFrequencyKind.EVERY_N_DAYS).copy(
            everyNDaysInterval = 3, everyNDaysReferenceDate = day,
        )))!!
        assertTrue(days.contains("Cada 3 días"))
        assertTrue(days.contains("18/11/2026"))
    }

    @Test fun legacyScheduleRetainsTranscriptionAndExplicitTime() {
        val text = reportMedicationSchedule(revision())!!
        assertTrue(text.contains("Frecuencia transcrita"))
        assertTrue(text.contains("08:00"))
    }

    @Test fun observationAnswersRetainCatalogQuestionsAndUnknownValues() {
        val record = ObservationRecord(
            id = "o", observationId = "OBS-022", personUnderCareId = "p", occurredOn = day,
            occurredAt = null, timePrecision = ObservationTimePrecision.UNKNOWN,
            result = ObservationResult.EVENT, answers = mapOf("event" to listOf("Se cayó"), "desconocido" to listOf("escaleras")),
            description = "", createdAt = day.atStartOfDay(), actorId = "actor",
        )
        val text = reportObservationAnswers(record).joinToString(" · ")
        assertTrue(text.contains("¿Qué sucedió?: Se cayó"))
        assertTrue(text.contains("Campo registrado (desconocido): escaleras"))
    }
}
