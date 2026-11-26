package com.bitavida.app.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayActivityThreadMotionTest {
    @Test
    fun samePhysicalDistanceUsesCurrentPathLength() {
        val long = advanceActivityThread(0.25f, 1, 20f, 400f)
        val short = advanceActivityThread(0.25f, 1, 20f, 100f)
        assertEquals(20f, (long.first - 0.25f) * 400f, 0.001f)
        assertEquals(20f, (short.first - 0.25f) * 100f, 0.001f)
        assertEquals(1, short.second)
    }

    @Test
    fun reflectsAtEndWithoutLosingTravelDistance() {
        val next = advanceActivityThread(0.9f, 1, 30f, 100f)
        assertEquals(0.8f, next.first, 0.0001f)
        assertEquals(-1, next.second)
    }

    @Test
    fun doesNotAdvanceWithoutMeasuredPath() {
        val next = advanceActivityThread(0.37f, -1, 12f, 0f)
        assertEquals(0.37f, next.first, 0.0001f)
        assertEquals(-1, next.second)
    }

    @Test
    fun shrinkingPathKeepsPhaseAndUsesNewLengthImmediately() {
        val before = advanceActivityThread(0.2f, 1, 10f, 200f)
        val after = advanceActivityThread(before.first, before.second, 10f, 100f)
        assertEquals(0.25f, before.first, 0.0001f)
        assertEquals(0.35f, after.first, 0.0001f)
    }
}
