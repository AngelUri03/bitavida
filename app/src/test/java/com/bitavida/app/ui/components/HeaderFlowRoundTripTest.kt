package com.bitavida.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HeaderFlowRoundTripTest {
    @Test fun headIsContinuousAndDeceleratesAtBothTurns() {
        assertEquals(0f, headerFlowRoundTripPosition(0f), 0f)
        assertEquals(1f, headerFlowRoundTripPosition(1f), 0f)
        assertEquals(0f, headerFlowRoundTripPosition(2f), 0f)
        listOf(1f, 2f).forEach { turn ->
            val before = headerFlowRoundTripPosition(turn - 0.001f)
            val atTurn = headerFlowRoundTripPosition(turn)
            val after = headerFlowRoundTripPosition(turn + 0.001f)
            assertTrue(abs(before - atTurn) < 0.0001f)
            assertTrue(abs(after - atTurn) < 0.0001f)
        }
    }

    @Test fun historyTailDoesNotJumpOrDisappearWhenItsHeadReverses() {
        val before = HeaderFlowTrailWindow()
        val atTurn = HeaderFlowTrailWindow()
        val after = HeaderFlowTrailWindow()
        listOf(1f, 2f).forEach { turn ->
            before.sample(turn - 0.001f, 0.18f)
            atTurn.sample(turn, 0.18f)
            after.sample(turn + 0.001f, 0.18f)
            assertTrue(atTurn.end > atTurn.start)
            assertEquals(before.start, after.start, 0.005f)
            assertEquals(before.end, after.end, 0.005f)
            if (turn == 1f) assertEquals(1f, atTurn.end, 0f)
            else assertEquals(0f, atTurn.start, 0f)
        }
    }

    @Test fun tailFollowsTheActualTravelDirectionWithoutIndependentDirectionState() {
        val window = HeaderFlowTrailWindow()
        window.sample(0.5f, 0.18f)
        assertEquals(headerFlowRoundTripPosition(0.5f), window.end, 0f)
        window.sample(1.5f, 0.18f)
        assertEquals(headerFlowRoundTripPosition(1.5f), window.start, 0f)
        for (index in 0..2_000) {
            val phase = index / 1_000f
            window.sample(phase, 0.18f)
            assertTrue(window.start in 0f..1f)
            assertTrue(window.end in window.start..1f)
            assertTrue(headerFlowRoundTripPosition(phase) in window.start..window.end)
        }
        assertEquals(0f, headerFlowRoundTripPosition(Float.NaN), 0f)
        window.sample(Float.POSITIVE_INFINITY, 0.18f)
        assertEquals(0f, window.start, 0f)
        assertEquals(0f, window.end, 0f)
    }
}
