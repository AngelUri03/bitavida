package com.bitavida.app.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySectionThreadTest {
    @Test fun measuredMarkerCentersAreExactPointsWithVerticalTangents() {
        val wave = wave()
        val markers = listOf(32f to 83.4f, 32.5f to 292f, 31.75f to 507.7f)
        markers.forEach { (x, y) -> wave.addNode(x, y) }
        markers.forEach { (x, y) ->
            wave.sample(y)
            assertEquals(x, wave.x, 0.0001f)
            assertEquals(0f, wave.derivative, 0.0001f)
        }
    }

    @Test fun correctionWindowsAndNodeJoinsHaveContinuousValuesAndDerivatives() {
        val wave = wave()
        wave.addNode(32f, 100f)
        wave.addNode(32.5f, 170f) // Overlapping correction windows must remain smooth.
        listOf(52f, 100f, 122f, 148f, 170f, 218f).forEach { y ->
            wave.sample(y - 0.001f)
            val leftX = wave.x
            val leftSlope = wave.derivative
            wave.sample(y + 0.001f)
            assertEquals(leftX, wave.x, 0.001f)
            assertEquals(leftSlope, wave.derivative, 0.001f)
        }
    }

    @Test fun waveAndBezierControlsStayInVisibleCorridorBeforeContentAt44Dp() {
        val wave = wave()
        wave.addNode(32f, 144f)
        wave.addNode(32f, 490f)
        assertTrue(wave.visibleRange(840f, 60f, 820f, 0))
        val knots = FloatArray(128)
        val count = wave.fillKnots(knots)
        wave.sample(knots[0])
        var oldX = wave.x
        var oldY = knots[0]
        var oldSlope = wave.derivative
        for (index in 1 until count) {
            val y = knots[index]
            wave.sample(y)
            val span = y - oldY
            val control1 = oldX + oldSlope * span / 3f
            val control2 = wave.x - wave.derivative * span / 3f
            assertTrue(wave.x in 26f..38f)
            assertTrue(control1 in 25f..39f)
            assertTrue(control2 in 25f..39f)
            assertTrue(maxOf(wave.x, control1, control2) + 5f < 44f)
            oldX = wave.x
            oldY = y
            oldSlope = wave.derivative
        }
    }

    @Test fun scrollingPreservesWorldGeometryWhenVisibleItemsAndMarkersMove() {
        val before = wave(scroll = 100.0)
        before.addNode(32f, 300f)
        val after = wave(scroll = 180.0)
        after.addNode(32f, 220f)
        listOf(200f, 275f, 300f, 320f, 420f, 600f).forEach { y ->
            before.sample(y)
            after.sample(y - 80f)
            assertEquals(before.x, after.x, 0.0001f)
            assertEquals(before.derivative, after.derivative, 0.0001f)
        }
    }

    @Test fun virtualizingUnrelatedHeadingsDoesNotResetTheGlobalWave() {
        val full = wave(scroll = 753.0)
        full.addNode(32f, -200f)
        full.addNode(32f, 300f)
        full.addNode(32f, 1200f)
        val virtualized = wave(scroll = 753.0)
        virtualized.addNode(32f, 300f)
        for (y in 0..840 step 7) {
            full.sample(y.toFloat())
            virtualized.sample(y.toFloat())
            assertEquals(full.x, virtualized.x, 0f)
            assertEquals(full.derivative, virtualized.derivative, 0f)
        }
        val noHeadings = wave(scroll = 753.0)
        for (y in 0..840 step 7) {
            noHeadings.sample(y.toFloat())
            assertTrue(noHeadings.x in 26f..38f)
            assertTrue(noHeadings.derivative.isFinite())
        }
    }

    @Test fun scrollPhaseHandlesChangedFirstKeyFastJumpsAndExpansionWithoutGuessedHeights() {
        val phase = TodayThreadScrollPhase()
        phase.update(listOf("header", "medication"), floatArrayOf(0f, 100f), 2, false)
        phase.update(listOf("medication", "measurement"), floatArrayOf(-20f, 100f), 2, true)
        assertEquals(120.0, phase.worldOffset, 0.0)
        phase.update(listOf("notes"), floatArrayOf(-10f), 1, true) // No shared key: preserve phase.
        assertEquals(120.0, phase.worldOffset, 0.0)
        phase.update(listOf("notes"), floatArrayOf(-60f), 1, true)
        assertEquals(170.0, phase.worldOffset, 0.0)
        phase.update(listOf("notes"), floatArrayOf(-300f), 1, false) // Structural movement is not scroll.
        assertEquals(170.0, phase.worldOffset, 0.0)
    }

    @Test fun actualStartAndEndBoundsPreventDrawingOverHeaderOrBeyondFooter() {
        val wave = wave()
        assertTrue(wave.visibleRange(800f, 122.5f, 675.2f, 0))
        assertEquals(122.5f, wave.rangeStart, 0f)
        assertEquals(675.2f, wave.rangeEnd, 0f)
        assertTrue(wave.visibleRange(400f, 122.5f, 675.2f, 0))
        assertEquals(400f, wave.rangeEnd, 0f)
        assertFalse(wave.visibleRange(800f, Float.NaN, Float.NaN, 0))
        assertTrue(wave.visibleRange(800f, Float.NaN, Float.NaN, 8))
        assertEquals(0f, wave.rangeStart, 0f)
        assertEquals(800f, wave.rangeEnd, 0f)
        assertFalse(wave.visibleRange(800f, 900f, Float.NaN, 0))
        assertFalse(wave.visibleRange(800f, Float.NaN, -10f, 8))
    }

    @Test fun worldGridIncludesEveryActualNodeAndHasNoDuplicateKnots() {
        val wave = wave(scroll = 123.75)
        wave.addNode(32f, 83.4f)
        wave.addNode(32f, 205.2f)
        assertTrue(wave.visibleRange(800f, 10f, 700f, 0))
        val knots = FloatArray(128)
        val count = wave.fillKnots(knots)
        assertEquals(10f, knots[0], 0f)
        assertEquals(700f, knots[count - 1], 0f)
        assertTrue((0 until count).any { knots[it] == 83.4f })
        assertTrue((0 until count).any { knots[it] == 205.2f })
        for (index in 1 until count) assertTrue(knots[index] > knots[index - 1])
    }

    @Test fun resizeDensityAndRtlPreserveShapeAndMeasuredNodeAlignment() {
        val normal = wave(scroll = 90.0)
        normal.addNode(32f, 150f)
        val scaled = wave(base = 64f, density = 2f, scroll = 180.0)
        scaled.addNode(64f, 300f)
        val rtl = wave(base = 288f, scroll = 90.0, rtl = true)
        rtl.addNode(288f, 150f)
        for (y in 0..800 step 9) {
            normal.sample(y.toFloat())
            scaled.sample(y * 2f)
            rtl.sample(y.toFloat())
            assertEquals(normal.x * 2f, scaled.x, 0.0001f)
            assertEquals(normal.derivative, scaled.derivative, 0.0001f)
            assertEquals(320f, normal.x + rtl.x, 0.0001f)
            assertEquals(normal.derivative, -rtl.derivative, 0.0001f)
        }
    }

    @Test fun largeScrollDistancesAndInvalidSamplesCannotCreateUnboundedLoopsOrGeometry() {
        val wave = wave(scroll = Double.MAX_VALUE)
        assertTrue(wave.visibleRange(800f, 0f, 800f, 0))
        val knots = FloatArray(128)
        val count = wave.fillKnots(knots)
        assertTrue(count in 2..100)
        for (index in 0 until count) {
            assertTrue(knots[index].isFinite())
            wave.sample(knots[index])
            assertTrue(wave.x.isFinite())
            assertTrue(wave.derivative.isFinite())
        }
        assertFalse(wave.visibleRange(Float.POSITIVE_INFINITY, 0f, Float.NaN, 0))
        assertEquals(androidx.compose.ui.geometry.Offset.Zero,
            todayThreadTrailBounds(Float.NaN, 800f, 80f, 1))
        assertEquals(androidx.compose.ui.geometry.Offset.Zero,
            todayThreadTrailBounds(100f, Float.NaN, 80f, 1))
    }

    @Test fun highlightMovesInBothDirectionsAndKeepsItsPositionAcrossGestures() {
        val travel = TodayThreadTravel()
        assertEquals(0f, travel.fraction, 0f)
        travel.advance(200f, 1000f)
        assertEquals(0.12f, travel.fraction, 0.0001f)
        assertEquals(1, travel.direction)
        val stopped = travel.fraction
        travel.advance(0f, 1000f)
        assertEquals(stopped, travel.fraction, 0f)
        travel.advance(100f, 1000f) // A second gesture continues, rather than starting again.
        assertEquals(0.18f, travel.fraction, 0.0001f)
        travel.advance(-100f, 1000f)
        assertEquals(0.12f, travel.fraction, 0.0001f)
        assertEquals(-1, travel.direction)
        travel.advance(Float.MAX_VALUE, 1000f)
        assertEquals(1f, travel.fraction, 0f) // No wrap to zero at the endpoint.
        travel.advance(100f, 1000f)
        assertEquals(1f, travel.fraction, 0f)
        travel.advance(-100f, 1000f)
        assertEquals(0.94f, travel.fraction, 0.0001f)
        travel.advance(Float.NaN, 1000f)
        travel.advance(100f, 0f)
        assertEquals(0.94f, travel.fraction, 0.0001f)
    }

    @Test fun trailAlwaysFollowsBehindTheHeadAndClampsToTheMeasuredPath() {
        val forward = todayThreadTrailBounds(500f, 1000f, 80f, 1)
        assertEquals(420f, forward.x, 0f)
        assertEquals(500f, forward.y, 0f)
        val reverse = todayThreadTrailBounds(500f, 1000f, 80f, -1)
        assertEquals(500f, reverse.x, 0f)
        assertEquals(580f, reverse.y, 0f)
        assertEquals(0f, todayThreadTrailBounds(20f, 1000f, 80f, 1).x, 0f)
        assertEquals(1000f, todayThreadTrailBounds(980f, 1000f, 80f, -1).y, 0f)
        listOf(-200f, 0f, 100f, 1200f).forEach { head ->
            listOf(-1, 1).forEach { direction ->
                val bounds = todayThreadTrailBounds(head, 1000f, 80f, direction)
                assertTrue(bounds.x in 0f..1000f)
                assertTrue(bounds.y in bounds.x..1000f)
            }
        }
    }

    @Test fun firstRealSectionStartsThePathWithoutAnExtraHeaderOrFooterOnlyThread() {
        val metadata = listOf(
            TodayThreadAnchor("legacy_header", TodayThreadAnchorKind.Header),
            TodayThreadAnchor("row", TodayThreadAnchorKind.Surface),
            TodayThreadAnchor("measurement_heading", TodayThreadAnchorKind.Section),
            TodayThreadAnchor("notes", TodayThreadAnchorKind.Section),
            TodayThreadAnchor("end", TodayThreadAnchorKind.End),
        )
        assertEquals("measurement_heading", todayThreadFirstSectionKey(metadata))
        assertNull(todayThreadFirstSectionKey(metadata.filter { it.kind != TodayThreadAnchorKind.Section }))
        assertNull(todayThreadFirstSectionKey(listOf(TodayThreadAnchor("end", TodayThreadAnchorKind.End))))
        val wave = wave()
        wave.addNode(32f, 242.5f)
        assertTrue(wave.visibleRange(800f, 242.5f, 700f, 0))
        assertEquals(242.5f, wave.rangeStart, 0f)
        wave.sample(wave.rangeStart)
        assertEquals(32f, wave.x, 0f)
        assertTrue(wave.visibleRange(800f, -40f, 700f, 4))
        assertEquals(0f, wave.rangeStart, 0f)
    }

    @Test fun focusDoesNotRetriggerForRecompositionOrAChangedCandidateWithoutNewScroll() {
        val focus = TodayThreadFocusGate()
        assertTrue(focus.consider("medication", 32f))
        assertFalse(focus.consider("medication", 32f))
        assertFalse(focus.consider("measurement", 32f))
        focus.advance(40f)
        assertFalse(focus.consider("medication", 32f))
        assertFalse(focus.consider("measurement", 32f)) // A layout change consumes no new scroll.
        focus.advance(1f)
        assertTrue(focus.consider("measurement", 32f))
        assertFalse(focus.consider("measurement", 32f))
    }

    @Test fun theSameNodeRearmsOnlyAfterLeavingItsFocusAndReversingThroughRealScroll() {
        val focus = TodayThreadFocusGate()
        assertTrue(focus.consider("medication", 32f))
        focus.advance(40f)
        assertFalse(focus.consider(null, 32f))
        focus.advance(-40f)
        assertTrue(focus.consider("medication", 32f))
        assertFalse(focus.consider("medication", 32f))
        focus.advance(-40f)
        assertFalse(focus.consider(null, 32f))
        focus.advance(-40f)
        assertFalse(focus.consider("medication", 32f)) // Continuing in the same direction is one passage.
        focus.advance(80f)
        assertTrue(focus.consider("medication", 32f))
    }

    @Test fun motionPreferenceChangesCannotReplayFocusUntilThereIsEnoughNewScroll() {
        val focus = TodayThreadFocusGate()
        focus.requireScroll()
        assertFalse(focus.consider("medication", 32f))
        focus.advance(0f)
        assertFalse(focus.consider("medication", 32f))
        focus.advance(16f)
        assertFalse(focus.consider("medication", 32f))
        focus.advance(16f)
        assertTrue(focus.consider("medication", 32f))
        assertFalse(focus.consider("medication", 32f))
    }

    private fun wave(
        base: Float = 32f,
        density: Float = 1f,
        scroll: Double = 0.0,
        rtl: Boolean = false,
    ) = TodayThreadWave().apply { configure(base, density, scroll, rtl) }
}
