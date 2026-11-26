package com.bitavida.app.domain.validation

import org.junit.Assert.*
import org.junit.Test

class TextCharactersTest {
    @Test fun accentsAndComposedEmojiCountAsPerceivedCharacters() {
        listOf("á", "a\u0301", "😊", "👍🏽", "👩‍👩‍👧‍👦", "🇲🇽", "1️⃣").forEach {
            assertEquals(it, 1, textCharacterCount(it))
            assertTrue(fitsTextLimit(it.repeat(100), 100))
            assertFalse(fitsTextLimit(it.repeat(101), 100))
        }
        assertEquals(0, textCharacterCount(""))
        assertEquals(4, textCharacterCount("a\u0301😊🇲🇽z"))
    }

    @Test fun limitingTransientQueriesKeepsEveryGraphemeWhole() {
        val query = "👩‍👩‍👧‍👦".repeat(99) + "a\u0301" + "🇲🇽"
        assertEquals("👩‍👩‍👧‍👦".repeat(99) + "a\u0301", limitTextCharacters(query, 100))
        assertEquals("", limitTextCharacters(query, 0))
        assertEquals(query, limitTextCharacters(query, 101))
    }

    @Test fun typingAndPastingOverTheLimitAreRejectedWithoutChangingExistingText() {
        val atLimit = "a".repeat(99) + "👩‍👩‍👧‍👦"
        assertTrue(CaptureLimit.REMINDER_TITLE.acceptsEdit("", atLimit))
        assertFalse(CaptureLimit.REMINDER_TITLE.acceptsEdit(atLimit, atLimit + "z"))
        assertFalse(CaptureLimit.REMINDER_TITLE.acceptsEdit("Original", "👩‍👩‍👧‍👦".repeat(101)))
        assertEquals(100, CaptureLimit.REMINDER_TITLE.characterCount(atLimit))
    }

    @Test fun historicalTextRemainsIntactAndAllowsProgressiveWholeCharacterDeletion() {
        val original = "👩‍👩‍👧‍👦".repeat(110)
        val shorter = "👩‍👩‍👧‍👦".repeat(105)
        assertTrue(fitsTextLimit(original, 100, original))
        assertTrue(CaptureLimit.REMINDER_TITLE.acceptsEdit(original, original))
        assertTrue(CaptureLimit.REMINDER_TITLE.acceptsEdit(original, shorter))
        assertFalse(fitsTextLimit(shorter, 100, original))
        assertFalse(CaptureLimit.REMINDER_TITLE.acceptsEdit(original, "x".repeat(105)))
        assertTrue(CaptureLimit.REMINDER_TITLE.acceptsEdit(original, "x".repeat(100)))
    }

    @Test fun brokenSurrogatesCannotBeIntroducedOrProducedByDeletingPartOfAnEmoji() {
        assertFalse(fitsTextLimit("\uD83D", 100))
        assertFalse(fitsTextLimit("\uDC69", 100))
        assertFalse(CaptureLimit.REMINDER_TITLE.acceptsEdit("😊".repeat(110), "😊".repeat(109) + "\uD83D"))
        assertFalse(isHistoricalTextDeletion("a\u0301".repeat(110), "a".repeat(109)))
    }

    @Test fun limitsOutsideCareKeepTheirExistingNumericAndCaptureBehavior() {
        assertEquals(4_000, CaptureLimit.TRANSCRIPTION.maximum)
        assertEquals(500, CaptureLimit.REASON.maximum)
        assertEquals(300, CaptureLimit.BRIEF_NOTE.maximum)
        assertFalse(CaptureLimit.CYCLE_COUNT.unicodeText)
        assertTrue(CaptureLimit.CYCLE_COUNT.acceptsEdit("123", "1234"))
        assertFalse(CaptureLimit.CYCLE_COUNT.acceptsEdit("1234", "12345"))
    }
}
