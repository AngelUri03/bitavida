package com.bitavida.app.ui.medical

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalPlanEditStateTest {
    @Test
    fun confirmChangesStaysDisabledUntilDraftDiffersFromOriginal() {
        val original = GuidedMedicationDraft(
            medicationName = "Medicamento ficticio",
        )
        val unchanged = MedicalPlanUiState(
            editingExisting = true,
            medicationEditPurpose = MedicationEditPurpose.CORRECTION,
            originalGuidedMedicationDraft = original,
            guidedMedicationDraft = original,
        )

        assertFalse(unchanged.hasMedicationChanges)
        assertTrue(
            unchanged.copy(
                guidedMedicationDraft = original.copy(
                    medicationName = "Medicamento ficticio corregido",
                ),
            ).hasMedicationChanges,
        )
    }

    @Test
    fun creationDraftIsNeverTreatedAsAnExistingMedicationChange() {
        val draft = GuidedMedicationDraft(
            medicationName = "Medicamento ficticio",
        )

        assertFalse(
            MedicalPlanUiState(
                editingExisting = false,
                guidedMedicationDraft = draft,
            ).hasMedicationChanges,
        )
    }
}
