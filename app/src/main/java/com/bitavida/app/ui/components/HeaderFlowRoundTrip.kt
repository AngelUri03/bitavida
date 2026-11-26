package com.bitavida.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.max
import kotlin.math.min

/** One easing per half-cycle. Both ends have zero velocity, including the 2→0 wrap. */
internal fun headerFlowRoundTripPosition(phase: Float): Float {
    if (!phase.isFinite()) return 0f
    val cycle = ((phase % 2f) + 2f) % 2f
    return if (cycle <= 1f) FastOutSlowInEasing.transform(cycle)
    else 1f - FastOutSlowInEasing.transform(cycle - 1f)
}

/**
 * A short path history follows the same phase as its head, including the turning point.
 * Reused by draw: changing direction never replaces a left-hand tail with a right-hand tail.
 */
internal class HeaderFlowTrailWindow {
    var start = 0f
        private set
    var end = 0f
        private set

    fun sample(phase: Float, history: Float) {
        if (!phase.isFinite() || !history.isFinite()) {
            start = 0f
            end = 0f
            return
        }
        val cycle = ((phase % 2f) + 2f) % 2f
        val previousPhase = cycle - history.coerceIn(0f, 1f)
        val current = headerFlowRoundTripPosition(cycle)
        val previous = headerFlowRoundTripPosition(previousPhase)
        start = min(current, previous)
        end = max(current, previous)
        if (previousPhase < 0f) start = 0f
        if (previousPhase < 1f && cycle >= 1f) end = 1f
    }
}
