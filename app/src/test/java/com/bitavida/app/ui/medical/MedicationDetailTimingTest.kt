package com.bitavida.app.ui.medical

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDetailTimingTest {
    private val now = LocalDateTime.of(2026, 11, 13, 10, 30)

    @Test fun futureCannotBeQuickRegisteredEvenWithinTheSameMinute() {
        listOf(now.plusSeconds(1), now.plusMinutes(1), now.plusDays(5)).forEach {
            val timing = medicationDetailTiming(it, null, now, false)
            assertEquals(MedicationDetailTiming.FUTURE, timing)
            assertFalse(timing.canQuickRegister)
        }
    }

    @Test fun windowBecomesRegistrableOnlyAtItsStartAndIncludesItsEnd() {
        assertEquals(MedicationDetailTiming.FUTURE,
            medicationDetailTiming(now.plusMinutes(1), now.plusHours(1), now, false))
        listOf(now, now.plusHours(1)).forEach { moment ->
            val timing = medicationDetailTiming(now, now.plusHours(1), moment, false)
            assertEquals(MedicationDetailTiming.CURRENT_WINDOW, timing)
            assertTrue(timing.canQuickRegister)
        }
        assertEquals(MedicationDetailTiming.PAST_UNCONFIRMED,
            medicationDetailTiming(now, now.plusHours(1), now.plusHours(2), false))
    }

    @Test fun exactTimeAndPastAreDistinctWithoutInventingAGracePeriod() {
        assertEquals(MedicationDetailTiming.AT_RECORDED_TIME,
            medicationDetailTiming(now, null, now.plusSeconds(20), false))
        val past = medicationDetailTiming(now.minusDays(1), null, now, false)
        assertEquals(MedicationDetailTiming.PAST_UNCONFIRMED, past)
        assertTrue(past.canQuickRegister)
    }

    @Test fun existingRecordNeverOffersAnotherQuickRegistration() {
        listOf(now.minusDays(1), now, now.plusDays(5)).forEach {
            val timing = medicationDetailTiming(it, null, now, true)
            assertEquals(MedicationDetailTiming.RECORDED, timing)
            assertFalse(timing.canQuickRegister)
        }
    }
}
