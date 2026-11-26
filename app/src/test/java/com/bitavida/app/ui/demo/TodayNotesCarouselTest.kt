package com.bitavida.app.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayNotesCarouselTest {
    @Test
    fun virtualPagesWrapBothWaysWithoutCopyingRecords() {
        val start = notesCarouselInitialPage(4)
        assertEquals(0, notesCarouselIndex(start, 4))
        assertEquals(3, notesCarouselIndex(start - 1, 4))
        assertEquals(0, notesCarouselIndex(start + 4, 4))
        assertEquals(1, notesCarouselIndex(start + 5, 4))
    }

    @Test
    fun currentNoteRemainsSelectedWhenPagerIsRecentered() {
        for (count in 2..20) for (selected in 0 until count) {
            val page = notesCarouselInitialPage(count, selected)
            assertEquals(selected, notesCarouselIndex(page, count))
            assertTrue(page > count && page < Int.MAX_VALUE - count)
        }
    }

    @Test
    fun singleNoteUsesOnlyOnePage() {
        assertEquals(0, notesCarouselInitialPage(1))
        assertEquals(0, notesCarouselIndex(0, 1))
    }
}
