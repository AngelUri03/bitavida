package com.bitavida.app.ui.report

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportZoomGeometryTest {
    @Test
    fun scaleKeepsFocalPointOnScreen() {
        val adjustment = reportZoomScrollAdjustment(
            oldZoom = 1f, newZoom = 2f, currentX = 100f,
            focus = Offset(300f, 400f), pan = Offset.Zero,
        )
        assertEquals(200f, 300f - 100f, 0.001f)
        assertEquals(200f, 600f - adjustment.x, 0.001f)
        assertEquals(400f, adjustment.y, 0.001f)
    }

    @Test
    fun queuedPinchesRetainBothScaleAndPanWithoutExceedingBounds() {
        val first = accumulateReportZoom(
            ReportZoomPosition(1f, 100f, 0f), 2f, Offset(300f, 400f), Offset(10f, 20f), false,
        )
        val second = accumulateReportZoom(
            first, 4f, Offset(150f, 200f), Offset(5f, 7f), false,
        )
        assertEquals(3f, second.zoom, 0.001f)
        assertEquals(460f, second.horizontal, 0.001f)
        assertEquals(473f, second.verticalDelta, 0.001f)
    }

    @Test
    fun doubleTapTogglesReadingScaleAndFitWidth() {
        val reading = accumulateReportZoom(
            ReportZoomPosition(1f, 0f, 0f), 1f, Offset(20f, 40f), Offset.Zero, true,
        )
        assertEquals(2f, reading.zoom, 0.001f)
        val fit = accumulateReportZoom(reading, 1f, Offset(40f, 80f), Offset.Zero, true)
        assertEquals(1f, fit.zoom, 0.001f)
    }
}
