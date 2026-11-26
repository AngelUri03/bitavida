package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareEventDetailStatusTest {
    private val now = LocalDateTime.of(2026, 11, 24, 12, 0)

    private fun event(status: CareEventStatus, date: LocalDateTime?) = CareEvent(
        id = "evento", personUnderCareId = "persona",
        draft = CareEventDraft(kind = CareEventKind.APPOINTMENT, title = "Consulta",
            status = status, scheduledAt = date),
        createdAt = now, modifiedAt = now, revision = 1,
    )

    @Test fun editingClosesWhenScheduledTimePasses() {
        assertTrue(isFutureAppointment(event(CareEventStatus.CONFIRMED, now.plusMinutes(1)), now))
        assertFalse(isFutureAppointment(event(CareEventStatus.CONFIRMED, now), now))
        assertFalse(isFutureAppointment(event(CareEventStatus.CONFIRMED, now.minusMinutes(1)), now))
    }

    @Test fun legacyAndClosedEventsRemainReadOnly() {
        assertFalse(isFutureAppointment(event(CareEventStatus.TO_SCHEDULE, null), now))
        assertFalse(isFutureAppointment(event(CareEventStatus.COMPLETED, now.minusDays(1)), now))
        assertFalse(isFutureAppointment(event(CareEventStatus.CANCELLED, now.plusDays(1)), now))
    }
}
