package com.bitavida.app.ui.measurements

import com.bitavida.app.domain.model.MeasurementKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarMeasurementNavigationTest {
    @Test fun cancelledDraftReturnsToItsOriginalDetailForEveryKind() {
        MeasurementKind.entries.forEach { kind ->
            assertEquals(CalendarCaptureTransition.RESTORE_DETAIL, calendarCaptureTransition(kind, kind, saved = false))
        }
    }

    @Test fun persistedDraftReturnsToCalendarForEveryKind() {
        MeasurementKind.entries.forEach { kind ->
            assertEquals(CalendarCaptureTransition.RETURN_TO_CALENDAR, calendarCaptureTransition(kind, kind, saved = true))
        }
    }

    @Test fun unrelatedOrUntrackedEffectCannotNavigate() {
        MeasurementKind.entries.forEach { source ->
            assertEquals(CalendarCaptureTransition.IGNORE, calendarCaptureTransition(null, source, saved = true))
            MeasurementKind.entries.filter { it != source }.forEach { tracked ->
                assertEquals(CalendarCaptureTransition.IGNORE, calendarCaptureTransition(tracked, source, saved = false))
                assertEquals(CalendarCaptureTransition.IGNORE, calendarCaptureTransition(tracked, source, saved = true))
            }
        }
    }
}
