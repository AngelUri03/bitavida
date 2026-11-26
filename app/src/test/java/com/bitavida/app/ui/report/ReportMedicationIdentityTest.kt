package com.bitavida.app.ui.report

import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.FollowUpReportRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportMedicationIdentityTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val at = day.atTime(21, 30)
    private val series = "medication-series:fictional-series"

    private fun plan(id: String, minute: Int) = FollowUpReportRow(
        "Indicaciones médicas", day.atTime(10, minute), "Nombre ficticio", "Una pauta registrada",
        "Indicación médica transcrita", sourceId = "medication-plan:$id", groupId = series,
    )

    private fun declaration(id: String, plan: String, detail: String) = FollowUpReportRow(
        "Medicamentos", at, "Nombre ficticio", detail, "Registro declarado por el cuidador",
        sourceId = "medication-intake:$id", groupId = series, medicationPlanId = plan,
        scheduledAt = at,
    )

    @Test fun stablePlanAndDeclarationReferencesNeverMergeCoincidentFacts() {
        val one = declaration("a", "plan-a", "Se tomó · Cantidad declarada: 3 gotas")
        val two = declaration("b", "plan-a", "Toma parcial · Cantidad declarada: 2 gotas")
        val separatePlan = declaration("c", "plan-b", "Se tomó · Cantidad declarada: 3 gotas")
        val correction = FollowUpReportRow(
            "Cambios de medicamentos", day.atTime(22, 0), "Nombre ficticio",
            "Corrección declarada", "Corrección declarada por el cuidador",
            sourceId = one.sourceId, groupId = series, medicationPlanId = "plan-a",
        )
        val future = FollowUpReportRow("Programación de medicamentos", day.plusDays(1).atStartOfDay(),
            "Nombre ficticio", "Pauta registrada", "Indicación registrada",
            sourceId = "medication-future-plan:plan-b:0", groupId = series, medicationPlanId = "plan-b")
        val rows = listOf(plan("plan-a", 0), plan("plan-b", 1), one, two, separatePlan, correction, future)
        val report = FollowUpReport(day, day.plusDays(3), rows, snapshotAt = day.atTime(23, 0))
        val references = reportMedicationIdentity(report)
        assertEquals(mapOf("plan-a" to "Pauta 1", "plan-b" to "Pauta 2"), references.planLabels)
        assertEquals("Registro 1 de 2", references.declarationLabels[requireNotNull(one.sourceId)])
        assertEquals("Registro 2 de 2", references.declarationLabels[requireNotNull(two.sourceId)])
        assertFalse(references.declarationLabels.containsKey(requireNotNull(separatePlan.sourceId)))
        assertEquals(references, reportMedicationIdentity(report.copy(rows = rows.reversed())))

        val document = buildFollowUpDocument(report, "Paciente ficticio", "Cuidadora ficticia", day)
        assertEquals(3, document.sections.single { it.title == "Medicamentos" }.rows.count { it.section == "Medicamentos" })
        assertTrue(document.sections.single { it.title == "Programación posterior al corte" }.rows.single().title.endsWith("Pauta 2"))
        val audit = document.sections.single { it.title == "Auditoría y correcciones" }.rows.single()
        assertTrue(audit.title.contains("Pauta 1"))
        assertTrue(audit.title.contains("Registro 1 de 2"))
        assertEquals("Corrección declarada", audit.detail)
    }

    @Test fun equalNamesOrHoursAcrossPlansDoNotEstablishRecordRelationship() {
        val first = declaration("a", "plan-a", "Registro ficticio")
        val second = declaration("b", "plan-b", "Registro ficticio")
        val report = FollowUpReport(day, day, listOf(plan("plan-a", 0), plan("plan-b", 1), first, second))
        val references = reportMedicationIdentity(report)
        assertTrue(references.declarationLabels.isEmpty())
        assertEquals(2, references.planLabels.size)
    }
}
