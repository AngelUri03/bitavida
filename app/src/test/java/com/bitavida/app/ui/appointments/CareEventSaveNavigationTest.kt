package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CareEventSaveNavigationTest {
    private val time = LocalDateTime.of(2030, 4, 12, 10, 0)
    private val draft = CareEventDraft(CareEventKind.APPOINTMENT, "Consulta ficticia",
        status = CareEventStatus.CONFIRMED, scheduledAt = time)
    private val saved = CareEvent("event", "person", draft, time, time, 1)

    @Test fun creatingReturnsToTheListWithUpdatedEventsAndNoDetail() {
        val state = CareEventsState(saving = true, draft = draft, initialDraft = draft,
            operationId = "op", draftId = "event")
        val result = careEventSavedState(state, listOf(saved), saved)
        assertEquals(listOf(saved), result.events)
        assertNull(result.selected)
        assertNull(result.draft)
        assertNull(result.operationId)
        assertFalse(result.saving)
    }

    @Test fun editingReturnsToTheUpdatedDetailWithoutCreatingAnotherEvent() {
        val old = saved.copy(revision = 1)
        val updated = saved.copy(revision = 2, draft = draft.copy(title = "Otro nombre"))
        val state = CareEventsState(saving = true, draft = updated.draft, editing = old, draftId = old.id)
        val result = careEventSavedState(state, listOf(updated), updated)
        assertEquals(updated, result.selected)
        assertEquals(listOf(updated), result.events)
        assertNull(result.editing)
        assertNull(result.draft)
    }
}
