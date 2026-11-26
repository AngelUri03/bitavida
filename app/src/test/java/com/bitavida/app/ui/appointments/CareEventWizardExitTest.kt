package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventPurpose
import com.bitavida.app.domain.model.CareEventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareEventWizardExitTest {
    private val newDraft = CareEventDraft(
        kind = CareEventKind.REVIEW_TO_SCHEDULE,
        title = "",
        status = CareEventStatus.TO_SCHEDULE,
    )

    @Test fun newDraftHasChangesOnlyWhileDifferentFromInitialValues() {
        assertFalse(hasCareEventDraftChanges(newDraft, newDraft))
        val selected = newDraft.copy(purpose = CareEventPurpose.MEDICAL_CONSULTATION)
        assertTrue(hasCareEventDraftChanges(selected, newDraft))
        assertTrue(hasCareEventDraftChanges(newDraft.copy(title = "Consulta ficticia"), newDraft))
        assertFalse(hasCareEventDraftChanges(selected.copy(purpose = CareEventPurpose.LEGACY), newDraft))
    }

    @Test fun editBaselineClearsPreviousReasonAndDetectsOtherChanges() {
        val stored = newDraft.copy(title = "Consulta ficticia", changeReason = "Corrección previa")
        val initial = stored.copy(changeReason = "")
        assertFalse(hasCareEventDraftChanges(initial, initial))
        assertTrue(hasCareEventDraftChanges(initial.copy(institution = "Unidad ficticia"), initial))
        assertTrue(hasCareEventDraftChanges(initial.copy(changeReason = "Nuevo motivo"), initial))
        assertFalse(hasCareEventDraftChanges(initial.copy(institution = "Unidad ficticia").copy(institution = ""), initial))
    }

    @Test fun savingAndPickerTakePriorityOverExitConfirmation() {
        assertEquals(CareEventBackDecision.IGNORE, careEventBackDecision(true, true, false, 0, true))
        assertEquals(CareEventBackDecision.DISMISS_PICKER, careEventBackDecision(false, true, false, 0, true))
        assertEquals(CareEventBackDecision.CLOSE_CONFIRMATION, careEventBackDecision(false, false, true, 0, true))
    }

    @Test fun backPreservesDraftUntilExplicitDiscardOrUnmodifiedExit() {
        for (step in 1..5) {
            assertEquals(CareEventBackDecision.PREVIOUS_STEP, careEventBackDecision(false, false, false, step, true))
        }
        assertEquals(CareEventBackDecision.ASK_DISCARD, careEventBackDecision(false, false, false, 0, true))
        assertEquals(CareEventBackDecision.EXIT, careEventBackDecision(false, false, false, 0, false))
    }
}
