package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CareEventDuplicateTest {
    private val date = LocalDate.of(2027, 2, 4)
    private val draft = CareEventDraft(
        kind = CareEventKind.APPOINTMENT, title = "Consulta ficticia",
        status = CareEventStatus.CONFIRMED, scheduledDate = date,
    )
    private val event = CareEvent("one", "person", draft, LocalDateTime.MIN, LocalDateTime.MIN, 1)

    @Test fun sameTitleAndDateWarnsEvenWithDifferentSpacingAndTime() {
        val candidate = draft.copy(title = "  CONSULTA   ficticia ", scheduledAt = date.atTime(10, 30))
        assertEquals(event, findPossibleDuplicate(candidate, listOf(event), null))
    }

    @Test fun editingSameEventDoesNotWarn() {
        assertNull(findPossibleDuplicate(draft, listOf(event), event.id))
    }

    @Test fun anotherDateOrCategoryDoesNotWarn() {
        assertNull(findPossibleDuplicate(draft.copy(scheduledDate = date.plusDays(1)), listOf(event), null))
        assertNull(findPossibleDuplicate(draft.copy(category = CareEventCategory.ADMINISTRATIVE), listOf(event), null))
    }

    @Test fun unscheduledDuplicatesAreDetectedWithoutInventingADate() {
        val pending = draft.copy(kind = CareEventKind.REVIEW_TO_SCHEDULE, status = CareEventStatus.TO_SCHEDULE, scheduledDate = null)
        val existing = event.copy(draft = pending)
        assertEquals(existing, findPossibleDuplicate(pending, listOf(existing), null))
    }
}
