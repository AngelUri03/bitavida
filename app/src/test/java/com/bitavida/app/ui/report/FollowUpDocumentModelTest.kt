package com.bitavida.app.ui.report

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.buildFollowUpReport
import com.bitavida.app.domain.usecase.ReportCurrentMedication
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.FollowUpReportRow
import com.bitavida.app.domain.usecase.FollowUpReportSummary
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FollowUpDocumentModelTest {
    private val day = LocalDate.of(2026, 11, 16)

    private fun row(section: String, title: String, detail: String) =
        FollowUpReportRow(section, day.atTime(8, 0), title, detail, "Declarado")

    @Test fun documentIncludesEveryRowOnceEvenForCoincidentFacts() {
        val sameFactA = row("Mediciones", "Glucosa", "120 mg/dL")
        val sameFactB = row("Mediciones", "Glucosa", "120 mg/dL")
        val rows = listOf(
            row("Medicamentos", "Medicamento de ejemplo", "No se tomó"),
            sameFactA, sameFactB,
            row("Observaciones", "Observación de ejemplo", "Descripción"),
            row("Citas", "Cita de ejemplo", "Confirmada"),
            row("Otros", "Dato complementario", "Nota"),
        )
        val report = FollowUpReport(day, day, rows)
        val document = buildFollowUpDocument(report, "Paciente", "Cuidadora", day)
        assertEquals(listOf("Mediciones", "Medicamentos", "Observaciones", "Citas", "Otros"),
            document.sections.map { it.title })
        assertEquals(rows.size, document.sections.sumOf { it.rows.size })
        assertEquals(2, document.sections.single { it.title == "Mediciones" }.rows.size)
    }

    @Test fun associatedInstructionsStayWithAppointmentAndCorrectionsMoveToAudit() {
        val appointment = row("Citas", "Consulta", "Confirmada").copy(sourceId = "care-event:1")
        val instruction = row("Indicaciones médicas", "Consulta", "Texto transcrito").copy(sourceId = "care-event:1")
        val intake = row("Medicamentos", "Ejemplo", "Se tomó").copy(sourceId = "medication-intake:1")
        val correction = row("Cambios de medicamentos", "Ejemplo", "Motivo: corrección").copy(sourceId = "medication-intake:1")
        val report = FollowUpReport(day, day, listOf(instruction, correction, appointment, intake))
        val document = buildFollowUpDocument(report, "Paciente", "Cuidadora", day)
        assertEquals(3, document.sections.sumOf { it.rows.size })
        assertTrue(document.sections.single { it.title == "Citas" }.rows.single().detail.contains("Texto transcrito"))
        assertFalse(document.sections.single { it.title == "Medicamentos" }.rows.single().detail.contains("Motivo: corrección"))
        assertTrue(document.sections.single { it.title == "Auditoría y correcciones" }.rows.single().detail.contains("Motivo: corrección"))
    }

    @Test fun observationCorrectionStaysInAuditAndDoesNotDuplicateClinicalFact() {
        val clinical = row("Observaciones", "Observación ficticia", "Descripción corregida")
            .copy(sourceId = "observation:one", timeKnown = false)
        val correction = row("Auditoría y correcciones", "Corrección de observación", "Versión anterior → corregida")
            .copy(sourceId = "observation-revision:two", revisionNumber = 2)
        val document = buildFollowUpDocument(FollowUpReport(day, day, listOf(clinical, correction)),
            "Paciente", "Cuidadora", day)
        val observations = document.sections.single { it.title == "Observaciones" }.rows
        val audit = document.sections.single { it.title == "Auditoría y correcciones" }.rows
        assertEquals(listOf(clinical), observations)
        assertEquals(listOf(correction), audit)
        assertFalse(observations.single().timeKnown)
    }

    @Test fun correctionForIntakeOutsidePeriodIsAuditOnly() {
        val correction = row("Cambios de medicamentos", "Toma histórica", "Corrección declarada")
            .copy(sourceId = "medication-intake:historical")
        val document = buildFollowUpDocument(FollowUpReport(day, day, listOf(correction)),
            "Paciente", "Cuidadora", day)
        assertEquals(listOf("Auditoría y correcciones"), document.sections.map { it.title })
        assertEquals("Corrección declarada", document.sections.single().rows.single().detail)
    }

    @Test fun medicationPlanVersionsAndChangesRemainSeparate() {
        val change = row("Cambios de medicamentos", "Medicamento ficticio", "Suspensión registrada")
            .copy(sourceId = "medication-plan:1", occurredAt = day.atTime(10, 0))
        val indication = row("Indicaciones médicas", "Medicamento ficticio", "Cantidad indicada ficticia")
            .copy(sourceId = change.sourceId, occurredAt = day.atStartOfDay())
        val document = buildFollowUpDocument(FollowUpReport(day, day, listOf(change, indication)),
            "Paciente", "Cuidadora", day)
        val planRows = document.sections.single { it.title == "Medicamentos" }.rows
        assertEquals(2, planRows.size)
        assertTrue(planRows.any { it.section == "Indicaciones médicas" && it.detail == "Cantidad indicada ficticia" })
        assertTrue(planRows.any { it.section == "Cambios de medicamentos" && it.detail == "Suspensión registrada" })
    }

    @Test fun summaryDistinguishesDeclaredNonTakenFromUnconfirmed() {
        val report = FollowUpReport(day, day, listOf(
            row("Medicamentos", "Medicamento A", "No se tomó"),
            row("Medicamentos", "Medicamento B", "Sin confirmar"),
        ), summaries = listOf(FollowUpReportSummary("Registros de toma", 0)))
        val summary = buildFollowUpDocument(report, "Paciente", "Cuidadora", day).summary.joinToString()
        assertTrue(summary.contains("1 no realizada"))
        assertTrue(summary.contains("1 sin confirmar"))
    }

    @Test fun summaryCountsObservationsWithoutSelectingIndividualFacts() {
        val observation = row("Observaciones", "Observación ficticia", "Detalle declarado individual")
        val omitted = row("Medicamentos", "Medicamento ficticio", "No se tomó")
        val report = FollowUpReport(day, day, listOf(observation, omitted), summaries = listOf(
            FollowUpReportSummary("Registros de observación", 1),
            FollowUpReportSummary("Citas", 2),
        ))
        val document = buildFollowUpDocument(report, "Paciente ficticio", "Cuidadora ficticia", day)
        assertTrue(document.summary.any { it.contains("1 observación") && it.contains("2 citas") })
        assertFalse(document.summary.any { it.contains("Detalle declarado individual") })
        assertEquals(observation, document.sections.single { it.title == "Observaciones" }.rows.single())
        assertTrue(document.sections.single { it.title == "Medicamentos" }.rows.single().detail.contains("No se tomó"))
    }

    @Test fun glucoseSummaryKeepsContextsAndCountsSeparate() {
        val records = listOf(
            MeasurementRecord("f1", MeasurementValue.Glucose("90", GlucoseContext.FASTING), day.atTime(7, 0), day.atTime(7, 0)),
            MeasurementRecord("f2", MeasurementValue.Glucose("110", GlucoseContext.FASTING), day.atTime(8, 0), day.atTime(8, 0)),
            MeasurementRecord("post", MeasurementValue.Glucose("130", GlucoseContext.AFTER_MEAL), day.atTime(10, 0), day.atTime(10, 0)),
            MeasurementRecord("unknown", MeasurementValue.Glucose("120"), day.atTime(11, 0), day.atTime(11, 0)),
        )
        val report = buildFollowUpReport(day, day, "paciente-ficticio", MedicalCareOverview(emptyList(), emptyList()),
            records, emptyList(), zoneId = ZoneOffset.UTC, now = day.atTime(23, 0))
        val summary = buildFollowUpDocument(report, "Paciente ficticio", "Cuidadora ficticia", day).summary
        assertTrue(summary.any { it.contains("En ayunas") && it.contains("100.0 mg/dL") && it.contains("2 lecturas") })
        assertTrue(summary.any { it.contains("Después de comer") && it.contains("130.0 mg/dL") && it.contains("1 lectura.") })
        assertTrue(summary.any { it.contains("Sin contexto registrado") && it.contains("120.0 mg/dL") })
        assertFalse(summary.any { it.startsWith("Glucosa: promedio") })
        assertFalse(summary.any { it.contains("2 horas después") })
    }

    @Test fun readingsAndScheduledOccasionsStayTogetherInChronologicalOrder() {
        val rows = listOf(
            row("Mediciones programadas", "Glucosa capilar", "Sin confirmar").copy(occurredAt = day.atTime(10, 0)),
            row("Mediciones", "Presión arterial", "120/80 mmHg").copy(occurredAt = day.atTime(8, 0)),
            row("Mediciones programadas", "Glucosa capilar", "No realizada, declarado").copy(occurredAt = day.atTime(7, 0)),
            row("Mediciones", "Glucosa capilar", "110 mg/dL").copy(occurredAt = day.atTime(9, 0)),
        )
        val document = buildFollowUpDocument(FollowUpReport(day, day, rows), "Paciente", "Cuidadora", day)
        val glucose = document.sections.single { it.title == "Mediciones" }.rows.filter { it.section == "Glucosa capilar" }
        assertEquals(listOf("No realizada, declarado", "110 mg/dL", "Sin confirmar"), glucose.map { it.detail })
        assertEquals(4, document.sections.single { it.title == "Mediciones" }.rows.size)
    }

    @Test fun administrativeIntakeCorrectionsRemainInAuditAndClinicalChangesInDetail() {
        val intake = row("Medicamentos", "Toma ficticia", "Se tomó").copy(sourceId = "medication-intake:1")
        val correction = row("Cambios de medicamentos", "Toma ficticia",
            "Corrección de hora y motivo completo").copy(sourceId = intake.sourceId)
        val clinicalChange = row("Cambios de medicamentos", "Medicamento ficticio",
            "Suspensión registrada").copy(sourceId = "medication-plan:1")
        val document = buildFollowUpDocument(FollowUpReport(day, day, listOf(intake, correction, clinicalChange)),
            "Paciente", "Cuidadora", day)
        assertTrue(document.sections.single { it.title == "Auditoría y correcciones" }.rows.any {
            it.detail.contains("Corrección de hora y motivo completo")
        })
        assertTrue(document.sections.single { it.title == "Medicamentos" }.rows.any {
            it.detail.contains("Suspensión registrada")
        })
    }

    @Test fun futureOccurrencesAreSeparatedAndOnlyConsecutiveIdenticalSchedulesAreCompacted() {
        val cutoff = day.atTime(12, 0)
        val rows = listOf(
            row("Mediciones programadas", "Glucosa", "Sin confirmar").copy(occurredAt = day.atTime(8, 0)),
            row("Mediciones programadas", "Glucosa", "Programada").copy(occurredAt = day.plusDays(1).atTime(8, 0), groupId = "slot-a"),
            row("Mediciones programadas", "Glucosa", "Programada").copy(occurredAt = day.plusDays(2).atTime(8, 0), groupId = "slot-a"),
            row("Programación de medicamentos", "Medicamento", "Pauta registrada").copy(
                occurredAt = day.plusDays(1).atTime(8, 0), sourceId = "medication-future-plan:1"),
        )
        val document = buildFollowUpDocument(
            FollowUpReport(day, day.plusDays(2), rows, cutoffAt = cutoff), "Paciente", "Cuidadora", day)
        assertEquals(1, document.sections.single { it.title == "Mediciones" }.rows.size)
        val future = document.sections.single { it.title == "Programación posterior al corte" }.rows
        assertEquals(2, future.size)
        assertTrue(future.any { it.detail.contains("2 ocasiones") })
        assertTrue(future.any { it.sourceId == "medication-future-plan:1" && it.detail == "Pauta registrada" })
    }

    @Test fun unrelatedOrUnidentifiedFutureOccurrencesAreNeverCombined() {
        val rows = listOf(
            row("Mediciones programadas", "Glucosa", "Programada")
                .copy(occurredAt = day.plusDays(1).atTime(8, 0), groupId = "slot-a"),
            row("Mediciones programadas", "Glucosa", "Programada")
                .copy(occurredAt = day.plusDays(2).atTime(8, 0), groupId = "slot-b"),
            row("Mediciones programadas", "Glucosa", "Programada")
                .copy(occurredAt = day.plusDays(3).atTime(8, 0)),
            row("Mediciones programadas", "Glucosa", "Programada")
                .copy(occurredAt = day.plusDays(4).atTime(8, 0)),
        )
        val report = FollowUpReport(day, day.plusDays(4), rows, cutoffAt = day.atTime(12, 0))
        val future = buildFollowUpDocument(report, "Paciente", "Cuidadora", day)
            .sections.single { it.title == "Programación posterior al corte" }.rows
        assertEquals(rows, future)
    }

    @Test fun fullIndicationTextRemainsVisibleWithoutAutomaticEllipsis() {
        val longText = "Indicación extensa ficticia con detalles sin abreviar. ".repeat(5)
        val row = row("Indicaciones médicas", "Nombre ficticio", longText)
            .copy(sourceId = "medication-plan:1")
        val document = buildFollowUpDocument(FollowUpReport(day, day, listOf(row)), "Paciente", "Cuidadora", day)
        assertTrue(document.summary.any { it.startsWith("Indicaciones:") && it.contains(longText.trim()) })
        assertFalse(document.summary.any { it.contains("…") })
    }

    @Test fun currentTreatmentUsesReportCutoffInSummaryAndDetail() {
        val now = day.plusDays(10).atTime(12, 0)
        val captured = day.minusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val revision = MedicationPlanRevision(
            id = "r-actual", medicationPlanId = "plan-actual", revisionNumber = 1,
            medicationName = "Tratamiento actual ficticio", doseAsIndicated = "Una unidad",
            unitOrPresentation = null, frequencyAsIndicated = null, startsOn = day.minusDays(1),
            endsOn = null, originalAdditionalInstructions = null,
            scheduledDoses = listOf(ScheduledDose("s-actual", "r-actual", LocalTime.of(8, 0),
                captured, "actor-ficticio", InformationOrigin.CAREGIVER_RECORD)),
            capturedAtEpochMillis = captured, authorActorId = "actor-ficticio",
            changeKind = MedicationPlanChangeKind.CREATED, changeReason = null,
        )
        val historical = row("Indicaciones médicas", "Tratamiento histórico ficticio",
            "Indicación del periodo").copy(sourceId = "medication-plan:historico")
        val report = FollowUpReport(day, day, listOf(historical),
            medicationPlanIds = setOf("historico"), snapshotAt = now,
            currentMedications = listOf(ReportCurrentMedication("plan-actual", revision)))
        val document = buildFollowUpDocument(report, "Paciente", "Cuidadora", now.toLocalDate())
        assertTrue(document.summary.any { it.startsWith("Indicaciones:") && it.contains("Tratamiento histórico ficticio") })
        assertTrue(document.summary.any { it.startsWith("Tratamiento vigente al corte:") && it.contains("Tratamiento actual ficticio") })
        assertEquals(day.atTime(LocalTime.MAX), document.cutoffAt)
        assertEquals(now, document.snapshotAt)
        assertEquals(document.cutoffAt, document.sections.single { it.title == "Tratamiento vigente al corte" }.rows.single().occurredAt)
    }

    @Test fun emptyReportHasNoEmptyDetailSections() {
        val document = buildFollowUpDocument(FollowUpReport(day, day, emptyList()), "Paciente", "Cuidadora", day)
        assertTrue(document.sections.isEmpty())
    }

    @Test fun longDescriptionRemainsCompleteInDetail() {
        val longText = "Descripción ".repeat(400)
        val report = FollowUpReport(day, day, listOf(row("Observaciones", "Observación", longText)))
        val document = buildFollowUpDocument(report, "Paciente", "Cuidadora", day)
        assertEquals(longText, document.sections.single().rows.single().detail)
    }
}
