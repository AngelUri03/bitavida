package com.bitavida.app.ui.demo

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayCareClockTest {
    private val now = LocalDateTime.of(2026, 11, 20, 10, 0)

    @Test fun nextScheduleBoundaryWinsWithoutAPollingInterval() {
        val event = now.plusMinutes(17)
        assertEquals(event, nextTodayCareClockChange(now, listOf(now.minusMinutes(1), event, event.plusHours(2))))
    }

    @Test fun emptyDayAdvancesAtTheGreetingBoundaryAndThenMidnight() {
        assertEquals(now.toLocalDate().atTime(12, 0), nextTodayCareClockChange(now, emptyList()))
        val night = now.toLocalDate().atTime(22, 0)
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(), nextTodayCareClockChange(night, emptyList()))
    }

    @Test fun consumedBoundaryDoesNotCauseAnImmediateLoop() {
        assertEquals(now.toLocalDate().atTime(12, 0), nextTodayCareClockChange(now, listOf(now, now.minusDays(1))))
    }
}
