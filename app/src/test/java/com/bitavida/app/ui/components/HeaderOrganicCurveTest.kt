package com.bitavida.app.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HeaderOrganicCurveTest {
    @Test fun bothEndpointsAndTheirHorizontalTangentsStayFixedForTheEntireCycle() {
        val curve = HeaderOrganicCurve()
        for (index in 0..200) {
            curve.sample(index / 100f)
            assertEquals(0.58f, curve.y[0], 0f)
            assertEquals(0.52f, curve.y[curve.segments], 0f)
            assertEquals(0f, curve.slope[0], 0f)
            assertEquals(0f, curve.slope[curve.segments], 0f)
            assertEquals(curve.y[0], curve.firstControlY(0), 0f)
            assertEquals(curve.y[curve.segments], curve.secondControlY(curve.segments - 1), 0f)
        }
    }

    @Test fun everyBezierControlAndSampleStaysWithinTheSmallHeaderBand() {
        val curve = HeaderOrganicCurve()
        for (phase in 0..200) {
            curve.sample(phase / 100f)
            for (segment in 0 until curve.segments) {
                assertTrue(curve.firstControlY(segment) in 0.20f..0.82f)
                assertTrue(curve.secondControlY(segment) in 0.20f..0.82f)
                for (step in 0..20) {
                    val t = step / 20f
                    val rest = 1f - t
                    val y = rest * rest * rest * curve.y[segment] +
                        3f * rest * rest * t * curve.firstControlY(segment) +
                        3f * rest * t * t * curve.secondControlY(segment) +
                        t * t * t * curve.y[segment + 1]
                    assertTrue(y.isFinite() && y in 0.20f..0.82f)
                }
            }
        }
    }

    @Test fun theSoftStrokeFitsTheNativeHeaderIncludingBezierControlExtents() {
        val curve = HeaderOrganicCurve()
        val nativeHeightDp = 38f
        val halfSoftStrokeDp = 7f / 2f
        for (phase in 0..200) {
            curve.sample(phase / 100f)
            for (segment in 0 until curve.segments) {
                // A cubic stays inside its control hull, so this also protects every point between samples.
                val controls = floatArrayOf(
                    curve.y[segment], curve.firstControlY(segment),
                    curve.secondControlY(segment), curve.y[segment + 1],
                )
                controls.forEach { y ->
                    assertTrue(y * nativeHeightDp - halfSoftStrokeDp > 0f)
                    assertTrue(y * nativeHeightDp + halfSoftStrokeDp < nativeHeightDp)
                }
            }
        }
    }

    @Test fun adjoiningCubicSegmentsKeepTheSameTangentInsteadOfFormingCorners() {
        val curve = HeaderOrganicCurve()
        listOf(0f, 0.27f, 0.83f, 1f, 1.42f, 1.91f).forEach { phase ->
            curve.sample(phase)
            for (point in 1 until curve.segments) {
                val arriving = (curve.y[point] - curve.secondControlY(point - 1)) * 3f * curve.segments
                val departing = (curve.firstControlY(point) - curve.y[point]) * 3f * curve.segments
                assertEquals(arriving, departing, 0.00001f)
            }
        }
    }

    @Test fun theCurveItselfMovesButItsCycleAndTurnPointsNeverJump() {
        val curve = HeaderOrganicCurve()
        curve.sample(0f)
        val rest = curve.y.copyOf()
        curve.sample(0.63f)
        assertTrue((1 until curve.segments).any { abs(rest[it] - curve.y[it]) > 0.005f })
        curve.sample(2f)
        assertArrayEquals(rest, curve.y, 0f)

        listOf(1f, 2f).forEach { turn ->
            curve.sample(turn - 0.001f)
            val before = curve.y.copyOf()
            val beforeSlope = curve.slope.copyOf()
            curve.sample(turn + 0.001f)
            assertArrayEquals(before, curve.y, 0.001f)
            assertArrayEquals(beforeSlope, curve.slope, 0.01f)
        }
    }

    @Test fun invalidPhaseUsesTheSameFiniteStaticOrganicRestShape() {
        val curve = HeaderOrganicCurve()
        curve.sample(0f)
        val rest = curve.y.copyOf()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            curve.sample(it)
            assertArrayEquals(rest, curve.y, 0f)
            assertTrue(curve.slope.all(Float::isFinite))
        }
    }
}
