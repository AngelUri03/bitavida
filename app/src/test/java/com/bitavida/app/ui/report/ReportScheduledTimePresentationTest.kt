package com.bitavida.app.ui.report

import com.bitavida.app.domain.usecase.FollowUpReportRow
import com.bitavida.app.domain.usecase.ReportScheduledTimeMeaning
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationProductForm
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportScheduledTimePresentationTest {
    @Test fun nonOralLabelsRequireBothRecordedOutcomeAndResolvedHistoricalForm() {
        val moment = LocalDate.of(2026, 11, 14).atTime(10, 0)
        val row = FollowUpReportRow("Medicamentos", moment, "Ejemplo",
            "Se tomó · Cantidad declarada: 1 parche", "Prueba",
            medicationOutcome = MedicationIntakeOutcome.COMPLETED)
        assertEquals(row.detail, reportMedicationDisplayDetail(row))
        assertEquals("Se aplicó · Cantidad declarada: 1 parche",
            reportMedicationDisplayDetail(row.copy(medicationForm = MedicationProductForm.PATCH)))
        assertEquals("Se administró · Cantidad declarada: 1 parche",
            reportMedicationDisplayDetail(row.copy(medicationForm = MedicationProductForm.INJECTABLE)))
        assertEquals(row.detail, reportMedicationDisplayDetail(row.copy(medicationForm = MedicationProductForm.TABLET)))
        val omitted = row.copy(detail = "No se tomó", medicationOutcome = MedicationIntakeOutcome.OMITTED,
            medicationForm = MedicationProductForm.PATCH)
        assertEquals("No se aplicó", reportMedicationDisplayDetail(omitted))
        assertEquals(true, reportMedicationWasNotPerformed(omitted))
        assertEquals(false, reportMedicationIsUnconfirmed(omitted))
        // Inconsistent text and typed status must never be silently rewritten.
        assertEquals("Otro texto", reportMedicationDisplayDetail(omitted.copy(detail = "Otro texto")))
    }

    @Test fun reminderAndUnknownTimesAreNeverRenderedAsClinicalSchedule() {
        val moment = LocalDate.of(2026, 11, 14).atTime(23, 59)
        val row = FollowUpReportRow("Medicamentos", moment, "Ejemplo", "Registro", "Prueba", scheduledAt = moment)
        assertEquals("Tipo no verificable\n23:59", reportScheduledTimeLabel(row))
        assertEquals("Aviso de actividad\n23:59", reportScheduledTimeLabel(
            row.copy(scheduledTimeMeaning = ReportScheduledTimeMeaning.ACTIVITY_NOTICE)))
        assertEquals("Prevista\n23:59", reportScheduledTimeLabel(
            row.copy(scheduledTimeMeaning = ReportScheduledTimeMeaning.EXPECTED)))
        assertEquals("—", reportScheduledTimeLabel(row.copy(scheduledAt = null)))
    }
}
