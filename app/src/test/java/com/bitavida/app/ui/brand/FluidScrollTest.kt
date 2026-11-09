package com.bitavida.app.ui.brand

import com.bitavida.app.ui.components.currentIntensityTarget
import com.bitavida.app.ui.components.smoothCurrentValue
import com.bitavida.app.ui.components.threadFlowLogicalPosition
import com.bitavida.app.ui.components.threadFlowSectionPosition
import com.bitavida.app.ui.components.threadFlowVisualDirection
import com.bitavida.app.ui.components.threadNodePassage
import com.bitavida.app.ui.components.threadViewportLag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidScrollTest {
    @Test fun currentIntensityTracksSpeedInBothDirectionsAndDecaysSmoothly() {
        val slow = currentIntensityTarget(0.1f)
        val fast = currentIntensityTarget(1f)
        assertTrue(fast > slow && slow > 0f)
        assertEquals(fast, currentIntensityTarget(-1f), 0f)
        var value = fast
        repeat(60) {
            val next = smoothCurrentValue(value, 0f, 1f / 60f, 7f)
            assertTrue(next >= 0f && next < value)
            value = next
        }
        assertTrue(value < 0.002f)
    }

    @Test fun currentResponseIsFrameRateIndependentAndReversesContinuously() {
        var at60 = 0f
        var at120 = 0f
        repeat(60) { at60 = smoothCurrentValue(at60, 1f, 1f / 60f, 11f) }
        repeat(120) { at120 = smoothCurrentValue(at120, 1f, 1f / 120f, 11f) }
        assertEquals(at60, at120, 0.0001f)
        val firstReverse = smoothCurrentValue(at60, -1f, 1f / 60f, 11f)
        assertTrue(firstReverse < at60 && firstReverse > 0f)
    }

    @Test fun logicalPositionAlwaysMatchesAbsoluteScroll() {
        assertEquals(0f, threadFlowLogicalPosition(0, 1000), 0.001f)
        assertEquals(0.42f, threadFlowLogicalPosition(420, 1000), 0.001f)
        assertEquals(1f, threadFlowLogicalPosition(1000, 1000), 0.001f)
        assertEquals(0f, threadFlowLogicalPosition(200, 0), 0.001f)
    }

    @Test fun catchUpThresholdScalesWithViewport() {
        val threshold = threadViewportLag(800, 4000)
        assertEquals(20f, threshold * 4000, 0.001f)
        assertEquals(0f, threadViewportLag(800, 0), 0f)
    }

    @Test fun renderedTrailChangesDirectionOnlyAfterVelocityCrossesZero() {
        assertEquals(1, threadFlowVisualDirection(0.4f, 1))
        assertEquals(1, threadFlowVisualDirection(0f, 1))
        assertEquals(1, threadFlowVisualDirection(0.2f, -1))
        assertEquals(-1, threadFlowVisualDirection(-0.0002f, 1))
        assertEquals(-1, threadFlowVisualDirection(0f, -1))
    }

    @Test fun oneCoordinateCrossesSectionBoundariesWithoutRestarting() {
        assertEquals(0.8f, threadFlowSectionPosition(0.20f, 0, 4), 0.001f)
        assertEquals(-0.2f, threadFlowSectionPosition(0.20f, 1, 4), 0.001f)
        assertEquals(1f, threadFlowSectionPosition(0.25f, 0, 4), 0.001f)
        assertEquals(0f, threadFlowSectionPosition(0.25f, 1, 4), 0.001f)
        assertEquals(1f, threadFlowSectionPosition(1f, 3, 4), 0.001f)
    }

    @Test fun nodeResponseIsSymmetricAndBoundToTheSameCurrent() {
        assertEquals(1f, threadNodePassage(0f, 1f), 0.001f)
        assertEquals(threadNodePassage(-0.07f, 1f), threadNodePassage(0.07f, 1f), 0.001f)
        assertEquals(0f, threadNodePassage(0.14f, 1f), 0.001f)
        assertEquals(0f, threadNodePassage(0f, 0f), 0.001f)
    }
}
