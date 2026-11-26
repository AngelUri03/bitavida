package com.bitavida.app.ui.appointments

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentCalendarGeometryTest {
    @Test fun sevenIndependentTargetsAndCirclesRemainWithinGrid() {
        listOf(280.dp, 320.dp, 360.dp, 480.dp).forEach { available ->
            val width = appointmentCalendarGridWidth(available)
            val cell = (width - 16.dp) / 7
            val diameter = appointmentCalendarDayDiameter(cell)
            assertTrue(cell >= 48.dp)
            assertTrue(diameter <= 40.dp)
            assertTrue(cell - diameter >= 8.dp)
            val firstCenter = 8.dp + cell / 2
            val lastCenter = 8.dp + cell * 6 + cell / 2
            assertTrue(firstCenter - diameter / 2 >= 8.dp)
            assertTrue(lastCenter + diameter / 2 <= width - 8.dp)
        }
    }

    @Test fun compactGridScrollsWithoutCompressingTargetsAndWideGridFillsSpace() {
        assertEquals(352.dp, appointmentCalendarGridWidth(280.dp))
        assertEquals(480.dp, appointmentCalendarGridWidth(480.dp))
        assertEquals(40.dp, appointmentCalendarDayDiameter(48.dp))
        assertEquals(32.dp, appointmentCalendarDayDiameter(40.dp))
    }
}
