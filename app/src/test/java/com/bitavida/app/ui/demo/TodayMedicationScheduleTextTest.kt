package com.bitavida.app.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayMedicationScheduleTextTest {
    @Test fun keepsTheClockAndItsSpanishPeriodOnOneLine() {
        assertEquals("10:00\u00A0a.\u00A0m.", visualTodayMedicationSchedule("10:00 a. m."))
        assertEquals("1:05\u00A0p.m.", visualTodayMedicationSchedule("1:05 p.m."))
        assertEquals("01:05:30\u00A0p.\u00A0m.", visualTodayMedicationSchedule("01:05:30 p. m."))
    }

    @Test fun leavesBreakOpportunitiesAroundTheRangeSeparator() {
        assertEquals(
            "11:00\u00A0a.\u00A0m. – 12:00\u00A0p.\u00A0m.",
            visualTodayMedicationSchedule("11:00 a. m. – 12:00 p. m."),
        )
        assertEquals(
            "12:00\u00A0p.\u00A0m. – 1:00\u00A0p.\u00A0m.",
            visualTodayMedicationSchedule("12:00 p. m. – 1:00 p. m."),
        )
    }

    @Test fun preservesContextAndItsMiddleDotSeparator() {
        assertEquals(
            "Con el desayuno · 10:00\u00A0a.\u00A0m.",
            visualTodayMedicationSchedule("Con el desayuno · 10:00 a. m."),
        )
    }

    @Test fun supportsAmPmWithoutDotsWithoutChangingCaseOrPunctuation() {
        assertEquals("9:05\u00A0AM", visualTodayMedicationSchedule("9:05 AM"))
        assertEquals("12:00\u00A0pm, según indicación", visualTodayMedicationSchedule("12:00 pm, según indicación"))
    }

    @Test fun changesOnlySpacesInsideTheTimeTokenInUnicodeContent() {
        assertEquals(
            "Cafe\u0301 👩🏽‍🦳\u202F·\u202F10:00\u00A0a.\u00A0m.\u3000mañana",
            visualTodayMedicationSchedule("Cafe\u0301 👩🏽‍🦳\u202F·\u202F10:00\u202Fa.\u2007m.\u3000mañana"),
        )
    }

    @Test fun existingNonBreakingSpacesAreIdempotent() {
        val schedule = "Con el desayuno · 10:00\u00A0a.\u00A0m."
        assertEquals(schedule, visualTodayMedicationSchedule(schedule))
        assertEquals(schedule, visualTodayMedicationSchedule(visualTodayMedicationSchedule(schedule)))
    }

    @Test fun leaves24HourTimesAndUnknownDescriptionsUntouched() {
        for (schedule in listOf(
            "00:00 – 23:59",
            "12:00",
            "Con el desayuno",
            "Hora por confirmar",
            "Mañana a las doce",
            "12:00 UTC",
            "١٠:٠٠ ص",
        )) {
            assertEquals(schedule, visualTodayMedicationSchedule(schedule))
        }
    }

    @Test fun doesNotRepairOrPartiallyMatchUnsupportedClockText() {
        for (schedule in listOf(
            "09:70 p. m.",
            "13:00 p. m.",
            "123:00 p. m.",
            "11:30 p. m.ado",
            "11:30 a. m",
            "10:00\np. m.",
        )) {
            assertEquals(schedule, visualTodayMedicationSchedule(schedule))
        }
    }
}
