package com.bitavida.app.ui.demo

import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.ui.medical.TodayMedicationFeedback
import com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TodayMedicationFeedbackTest {
    @Test fun pendingWriteAndConsentNeverAnnounceSuccess() {
        assertNull(receipt(TodayMedicationFeedbackStatus.SAVING).presentationMessage())
        assertNull(receipt(TodayMedicationFeedbackStatus.AWAITING_CONSENT).presentationMessage())
    }

    @Test fun aNegativeDeclarationIsNotPresentedAsAnAdministration() {
        assertEquals("Toma registrada: Medicamento ficticio",
            receipt(TodayMedicationFeedbackStatus.STORED).presentationMessage())
        val omitted = receipt(TodayMedicationFeedbackStatus.STORED).copy(
            outcome = MedicationIntakeOutcome.OMITTED,
        ).presentationMessage()
        assertEquals("Declaración registrada: Medicamento ficticio", omitted)
        assertFalse(requireNotNull(omitted).contains("Se tomó"))
    }

    @Test fun failureAndUndoHaveExplicitDistinctMessages() {
        assertEquals("No se pudo registrar la toma. Inténtalo otra vez.",
            receipt(TodayMedicationFeedbackStatus.FAILED).presentationMessage())
        assertEquals("La toma cambió.",
            receipt(TodayMedicationFeedbackStatus.FAILED).copy(message = "La toma cambió.").presentationMessage())
        assertEquals("Corrección guardada. La toma vuelve a pendientes.",
            receipt(TodayMedicationFeedbackStatus.UNDONE).presentationMessage())
    }

    @Test fun rotationAndReturningToTodayDoNotRestartTheUndoWindow() {
        assertEquals(5_000L, remainingTodayMedicationFeedbackMillis(1_000L, 4_000L, 8_000L))
        assertEquals(0L, remainingTodayMedicationFeedbackMillis(1_000L, 10_000L, 8_000L))
        assertEquals(25_000L, remainingTodayMedicationFeedbackMillis(1_000L, 6_000L, 30_000L))
        assertEquals(8_000L, remainingTodayMedicationFeedbackMillis(1_000L, 500L, 8_000L))
    }

    private fun receipt(status: TodayMedicationFeedbackStatus) = TodayMedicationFeedback(
        requestId = "fictional-request", occurrenceId = "fictional-occurrence",
        medicationName = "Medicamento ficticio", outcome = MedicationIntakeOutcome.COMPLETED,
        status = status,
    )
}
