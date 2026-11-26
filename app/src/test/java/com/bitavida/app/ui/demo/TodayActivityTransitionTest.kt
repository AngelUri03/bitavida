package com.bitavida.app.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayActivityTransitionTest {
    @Test
    fun committedOccurrenceKeepsItsKeyAndReplacementEntersDuringTheSameExit() {
        val ordered = listOf("med:a", "med:b", "med:c", "med:d", "med:e", "med:f")
        val before = activityTargetKeys(ordered, emptyList(), expanded = false, previousExpanded = false, exitingKeys = emptySet())
        val after = activityTargetKeys(ordered, emptyList(), expanded = false, previousExpanded = false, exitingKeys = setOf("med:c"))
        assertEquals(listOf("med:a", "med:b", "med:c", "med:d", "med:e"), before)
        assertEquals(listOf("med:a", "med:b", "med:d", "med:e", "med:f"), after)
        // The old occurrence remains in its original slot until its exit finishes.
        assertEquals(ordered, reconcileActivityTransitionKeys(ordered, after, before, motion = true))
    }

    @Test
    fun resolvingPreviousDoesNotMoveItIntoCurrentAndKeepsOtherPreviousOccurrences() {
        val current = listOf("med:future")
        val previous = listOf("med:past1", "med:past2")
        assertEquals(listOf("med:future", "med:past2"), activityTargetKeys(
            current, previous, expanded = false, previousExpanded = true, exitingKeys = setOf("med:past1")))
        assertEquals(listOf("med:future"), activityTargetKeys(
            current, previous, expanded = false, previousExpanded = false, exitingKeys = setOf("med:past1")))
    }

    @Test
    fun overlappingConfirmationsKeepTheirOwnKeysAndFillVacanciesTogether() {
        val ordered = listOf("med:a", "med:b", "med:c", "med:d", "med:e", "med:f", "med:g")
        val before = ordered.take(5)
        val target = activityTargetKeys(ordered, emptyList(), false, false, setOf("med:b", "med:d"))
        assertEquals(listOf("med:a", "med:c", "med:e", "med:f", "med:g"), target)
        assertEquals(ordered, reconcileActivityTransitionKeys(ordered, target, before, motion = true))
    }

    @Test
    fun reducedMotionSelectsFinalWindowWithoutRetainingResolvedCard() {
        val ordered = listOf("med:a", "med:b", "med:c", "med:d", "med:e", "med:f")
        val target = activityTargetKeys(ordered, emptyList(), false, false, setOf("med:a"))
        assertEquals(listOf("med:b", "med:c", "med:d", "med:e", "med:f"),
            reconcileActivityTransitionKeys(ordered, target, ordered.take(5), motion = false))
    }
    @Test
    fun changingFiltersKeepsOutgoingAndIncomingInRealOrder() {
        assertEquals(
            listOf("med", "measurement", "reminder"),
            reconcileActivityTransitionKeys(
                available = listOf("med", "measurement", "reminder"),
                target = listOf("measurement"),
                retained = listOf("med", "reminder"),
                motion = true,
            ),
        )
    }

    @Test
    fun sourceRemovalNeverResurrectsAnOutgoingActivity() {
        assertEquals(
            listOf("remaining"),
            reconcileActivityTransitionKeys(
                available = listOf("remaining"),
                target = listOf("remaining"),
                retained = listOf("resolved", "remaining"),
                motion = true,
            ),
        )
    }

    @Test
    fun quickReselectionRestoresTheTargetAndFollowsUpdatedOrder() {
        assertEquals(
            listOf("second", "first"),
            reconcileActivityTransitionKeys(
                available = listOf("second", "first"),
                target = listOf("first"),
                retained = listOf("first", "second", "first"),
                motion = true,
            ),
        )
    }

    @Test
    fun reducedMotionDropsOutgoingRowsImmediately() {
        assertEquals(
            listOf("new"),
            reconcileActivityTransitionKeys(
                available = listOf("old", "new"),
                target = listOf("new"),
                retained = listOf("old"),
                motion = false,
            ),
        )
    }
}
