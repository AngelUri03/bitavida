package com.bitavida.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DemoReferenceTest {
    private val reference = DemoReference(
        Instant.parse("2026-11-23T18:30:00Z"),
        ZoneId.of("America/Mexico_City"),
    )

    @Test fun startsAtFirstDayOfSecondPreviousCalendarMonth() {
        assertEquals(LocalDate.of(2026, 9, 1), reference.firstDate)
        assertEquals(LocalDate.of(2026, 11, 23), reference.lastDate)
    }

    @Test fun currentDayIsBoundedByTheReferenceInstant() {
        assertTrue(reference.includesOccurredAt(LocalDateTime.of(2026, 11, 23, 12, 30)))
        assertFalse(reference.includesOccurredAt(LocalDateTime.of(2026, 11, 23, 12, 31)))
    }

    @Test fun rollsAcrossYearsAndLeapFebruary() {
        val march = DemoReference(Instant.parse("2024-03-01T12:00:00Z"), ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2024, 1, 1), march.firstDate)
        val january = DemoReference(Instant.parse("2027-01-01T12:00:00Z"), ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2026, 11, 1), january.firstDate)
    }
}
