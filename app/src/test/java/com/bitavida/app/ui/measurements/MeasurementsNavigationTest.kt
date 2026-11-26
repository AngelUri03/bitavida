package com.bitavida.app.ui.measurements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementsNavigationTest {
    @Test
    fun generalCalendarReturnsToMeasurementsWhileAnalyticalHistoriesReturnToDetail() {
        assertEquals(
            MeasurementDestination.MAIN,
            measurementBackDestination(MeasurementDestination.HISTORY, helpFromDetail = false),
        )
        listOf(
            MeasurementDestination.GLUCOSE_HISTORY,
            MeasurementDestination.BLOOD_PRESSURE_HISTORY,
            MeasurementDestination.WEIGHT_HISTORY,
        ).forEach { destination ->
            assertEquals(
                MeasurementDestination.DETAIL,
                measurementBackDestination(destination, helpFromDetail = false),
            )
        }
    }

    @Test
    fun followUpActionsReturnToGlucoseDetailWithoutAddingAnotherDestination() {
        listOf(
            MeasurementFollowUpAction.CORRECT,
            MeasurementFollowUpAction.CHANGE_INDICATIONS,
            MeasurementFollowUpAction.VIEW_CHANGES,
        ).forEach { action ->
            assertEquals(
                "Atrás debe volver al detalle desde $action",
                MeasurementDestination.DETAIL,
                measurementBackDestination(
                    destination = MeasurementDestination.FOLLOW_UP,
                    helpFromDetail = false,
                ),
            )
        }
    }

    @Test
    fun remainingDestinationsKeepTheirEstablishedParent() {
        assertNull(
            measurementBackDestination(
                destination = MeasurementDestination.MAIN,
                helpFromDetail = false,
            ),
        )
        assertEquals(
            MeasurementDestination.MAIN,
            measurementBackDestination(
                destination = MeasurementDestination.DETAIL,
                helpFromDetail = false,
            ),
        )
        assertEquals(
            MeasurementDestination.DETAIL,
            measurementBackDestination(
                destination = MeasurementDestination.GLUCOSE_HISTORY,
                helpFromDetail = false,
            ),
        )
        assertEquals(
            MeasurementDestination.DETAIL,
            measurementBackDestination(
                destination = MeasurementDestination.HELP,
                helpFromDetail = true,
            ),
        )
    }
}
