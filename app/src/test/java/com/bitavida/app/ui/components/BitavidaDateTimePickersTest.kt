package com.bitavida.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class BitavidaDateTimePickersTest {
    @Test
    fun calendarGridHonorsFirstDayAndKeepsCompleteWeeks() {
        val cells = bitavidaCalendarCells(
            month = YearMonth.of(2024, 2),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(35, cells.size)
        assertNull(cells.first())
        assertEquals(LocalDate.of(2024, 2, 1), cells[3])
        assertEquals(LocalDate.of(2024, 2, 29), cells[31])
        assertTrue(cells.drop(32).all { it == null })
    }

    @Test
    fun dateRangeSelectionRestartsAndPreservesChronologicalOrder() {
        val first = LocalDate.of(2026, 10, 29)
        val later = LocalDate.of(2026, 11, 4)
        val earlier = LocalDate.of(2026, 10, 10)
        val original = BitavidaDateRangeSelection(first, later)

        val newStart = bitavidaDateRangeAfterSelection(original, first)
        assertEquals(BitavidaDateRangeSelection(first, null), newStart)
        assertEquals(BitavidaDateRangeSelection(first, later),
            bitavidaDateRangeAfterSelection(newStart, later))
        assertEquals(BitavidaDateRangeSelection(earlier, null),
            bitavidaDateRangeAfterSelection(newStart, earlier))
        assertEquals(BitavidaDateRangeSelection(first, first),
            bitavidaDateRangeAfterSelection(newStart, first))
    }

    @Test
    fun dateConstraintsAreOperationSpecific() {
        val constraints = BitavidaDatePickerConstraints(
            minDate = LocalDate.of(2026, 11, 1),
            maxDate = LocalDate.of(2026, 11, 17),
            yearRange = 2025..2027,
        )

        assertTrue(constraints.allows(LocalDate.of(2026, 11, 17)))
        assertFalse(constraints.allows(LocalDate.of(2026, 11, 18)))
        assertTrue(constraints.allowsMonth(YearMonth.of(2026, 11)))
        assertFalse(constraints.allowsMonth(YearMonth.of(2026, 12)))
    }

    @Test
    fun twelveHourConversionHandlesMidnightAndNoon() {
        assertEquals(12, bitavidaHour12(0))
        assertEquals(12, bitavidaHour12(12))
        assertEquals(0, bitavidaHour24(12, isPm = false))
        assertEquals(12, bitavidaHour24(12, isPm = true))
        assertEquals(23, bitavidaHour24(11, isPm = true))
        assertEquals(10, bitavidaHour24(bitavidaHour12(22), isPm = false))
        assertEquals(22, bitavidaHour24(bitavidaHour12(10), isPm = true))
    }

    @Test
    fun futureTimeRemainsRepresentableUntilConfirmationValidation() {
        val constraints = BitavidaTimePickerConstraints(
            maxTime = LocalTime.of(14, 4),
            invalidSelectionMessage = "No puedes registrar una hora futura.",
        )
        val elevenPm = LocalTime.of(bitavidaHour24(11, isPm = true), 0)

        assertEquals(LocalTime.of(23, 0), elevenPm)
        assertFalse(constraints.allows(elevenPm))
        assertTrue(constraints.allows(LocalTime.of(11, 0)))
        assertEquals("No puedes registrar una hora futura.", constraints.invalidSelectionMessage)
    }

    @Test
    fun timeConstraintsRespectStepAndBounds() {
        val constraints = BitavidaTimePickerConstraints(
            minTime = LocalTime.of(8, 0),
            maxTime = LocalTime.of(18, 0),
            minuteStep = 5,
        )

        assertTrue(constraints.allows(LocalTime.of(8, 5)))
        assertFalse(constraints.allows(LocalTime.of(7, 55)))
        assertFalse(constraints.allows(LocalTime.of(8, 3)))
    }
}
