package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ObservationTextLimitsTest {
    private val now = LocalDateTime.of(2026, 11, 20, 12, 0)
    private val actor = GlucoseActor("fictional-profile", "fictional-caregiver", "fictional-person", true)
    private val family = "👩‍👩‍👧‍👦"

    @Test fun descriptionsValidateAtFiveHundredUserPerceivedCharacters() {
        assertTrue(validateObservation(record("á".repeat(500)), now).isEmpty())
        assertTrue(validateObservation(record(family.repeat(500)), now).isEmpty())
        assertTrue(validateObservation(record("a\u0301".repeat(500)), now).isEmpty())
        assertTrue("description" in validateObservation(record("a".repeat(501)), now))
        assertTrue("description" in validateObservation(record(family.repeat(501)), now))
        assertTrue("description" in validateObservation(record("  "), now))
    }

    @Test fun freeAnswersUseOneHundredCharactersWithoutRestrictingCatalogChoices() {
        val valid = record("Descripción ficticia").copy(answers = mapOf("zone" to listOf(family.repeat(100))))
        assertTrue(validateObservation(valid, now).isEmpty())
        assertTrue("zone" in validateObservation(valid.copy(answers = mapOf("zone" to listOf("x".repeat(101)))), now))
        assertTrue("zone" in validateObservation(valid.copy(answers = mapOf("zone" to listOf("pie", "mano"))), now))
    }

    @Test fun historicalOversizedFieldsRemainIntactWhenOtherDataIsCorrected() {
        val original = record("Texto histórico ".repeat(100)).copy(
            answers = mapOf("zone" to listOf("Zona histórica ".repeat(30))))
        val replacement = original.copy(occurredOn = now.toLocalDate().minusDays(1))
        assertTrue(validateObservation(replacement, now, original).isEmpty())
        assertEquals(original.description, replacement.description)
        assertEquals(original.answers, replacement.answers)
        assertTrue("description" in validateObservation(original, now))
        assertTrue("zone" in validateObservation(original, now))
    }

    @Test fun historicalExceptionCannotAuthorizeChangedOversizedTextOrAnotherRecord() {
        val original = record("x".repeat(700)).copy(answers = mapOf("zone" to listOf("y".repeat(150))))
        assertTrue("description" in validateObservation(original.copy(description = "z".repeat(700)), now, original))
        assertTrue("zone" in validateObservation(original.copy(answers = mapOf("zone" to listOf("z".repeat(150)))), now, original))
        assertTrue("description" in validateObservation(original.copy(id = "another-fictional-record"), now, original))
        assertTrue(validateObservation(original.copy(description = "Corregida", answers = mapOf("zone" to listOf("pie"))),
            now, original).isEmpty())
    }

    @Test fun correctionRequiresANewReasonOfAtMostThreeHundredCharacters() {
        val original = record("Texto histórico ".repeat(100))
        val revision = ObservationRecordRevision("fictional-correction", original.id, 2,
            original.copy(occurredOn = now.toLocalDate().minusDays(1)), family.repeat(300), now, actor.caregiverId)
        assertTrue(validateObservationCorrection(original, revision, 1, actor, now).isEmpty())
        assertTrue("correctionReason" in validateObservationCorrection(original,
            revision.copy(reason = family.repeat(301)), 1, actor, now))
        assertTrue("correctionReason" in validateObservationCorrection(original,
            revision.copy(reason = " "), 1, actor, now))
    }

    @Test fun deletionReasonIsLimitedAndPreviousFollowUpTextIsPreserved() {
        val previous = followUpRevision()
        val removed = observationDeletedRevision(previous, family.repeat(300), actor, now) { "fictional-removed" }
        assertEquals(previous.indication, removed.indication)
        assertEquals(family.repeat(300), removed.changeReason)
        assertEquals(GlucoseFollowUpChangeKind.REMOVED, removed.changeKind)
        try {
            observationDeletedRevision(previous, family.repeat(301), actor, now) { "fictional-invalid" }
            fail("Oversized reason must not reach persistence")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun followUpTextPreservesVerbatimIndicationAndHistoricalReasonOnlyWhenUnchanged() {
        val original = followUpRevision().copy(changeReason = "Motivo histórico ".repeat(25))
        assertTrue(validateObservationFollowUpText(original, original).isEmpty())
        assertTrue("changeReason" in validateObservationFollowUpText(original))
        assertTrue("changeReason" in validateObservationFollowUpText(original.copy(changeReason = "x".repeat(301)), original))
        val indication = original.copy(indication = family.repeat(1_000), changeReason = "Corrección")
        assertTrue(validateObservationFollowUpText(indication, original).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            assertNotNull(indication.copy(indication = family.repeat(1_001)))
        }
    }

    @Test fun oneHugeGraphemeCannotExceedTheExistingWriteUtfCapacity() {
        val exact = "a" + "\u0301".repeat(32_767)
        assertTrue(fitsObservationTextCapture(exact, 1))
        assertFalse(fitsObservationTextCapture(exact + "\u0301", 1))
        val errors = validateObservation(record(exact + "\u0301"), now)
        assertTrue("description" in errors)
        assertTrue(errors.getValue("description").contains("Unicode"))
        assertTrue(fitsObservationTextCapture(exact + "\u0301", 1, exact + "\u0301"))
    }

    @Test fun payloadCapacityMatchesModifiedUtfForNullsAndSurrogatePairs() {
        val nulBoundary = "a" + "\u0301".repeat(32_766) + "\u0000"
        assertTrue(fitsObservationTextCapture(nulBoundary, 3))
        assertFalse(fitsObservationTextCapture(nulBoundary + "\u0000", 3))
        val emojiBoundary = "😀" + "\u200D😀".repeat(7_281)
        assertTrue(fitsObservationTextCapture(emojiBoundary, 1))
        assertFalse(fitsObservationTextCapture(emojiBoundary + "\u200D😀", 1))
    }

    private fun record(description: String) = ObservationRecord(
        "fictional-record", "OBS-027", actor.personUnderCareId, now.toLocalDate(),
        null, ObservationTimePrecision.UNKNOWN, ObservationResult.CHANGES,
        emptyMap(), description, createdAt = now.minusHours(1), actorId = actor.caregiverId)

    private fun followUpRevision() = GlucoseFollowUpRevision(
        id = "fictional-revision", followUpId = "fictional-follow-up", revisionNumber = 1,
        indication = "Indicación ficticia transcrita", frequency = GlucoseFollowUpFrequency.DAILY,
        startsOn = now.toLocalDate().minusDays(1), effectiveFrom = now.toLocalDate().minusDays(1),
        slots = listOf(GlucoseFollowUpSlot("fictional-slot", time = now.toLocalTime(),
            suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false)),
        remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
        capturedAt = now.minusDays(1), authorActorId = actor.caregiverId)
}
