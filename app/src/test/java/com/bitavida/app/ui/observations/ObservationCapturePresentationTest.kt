package com.bitavida.app.ui.observations

import com.bitavida.app.domain.model.*
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationCapturePresentationTest {
    private val now = LocalDateTime.of(2026, 11, 18, 15, 0)
    private fun record(observationId: String = "OBS-001", result: ObservationResult = ObservationResult.CHANGES) =
        ObservationRecord("fictional-record", observationId, "fictional-person", now.toLocalDate(),
            LocalTime.of(14, 30), ObservationTimePrecision.EXACT, result, emptyMap(), "",
            createdAt = now, actorId = "fictional-caregiver")

    @Test fun everyObservationKeepsAllRequiredQuestionsWithoutOpeningOptionalDetails() {
        ObservationCatalog.all.forEach { definition ->
            val fields = observationCaptureQuestions(record(definition.id))
            assertTrue(definition.id, fields.essential.containsAll(definition.fields.filter { it.required }))
            assertTrue(definition.id, fields.essential.none { it in fields.additional })
            assertTrue(definition.id, fields.additional.none { it.required })
        }
    }

    @Test fun footChangesAreEssentialOnlyWhenChangesWereDeclared() {
        assertTrue(observationCaptureQuestions(record()).essential.any { it.id == "changes" })
        val unchanged = observationCaptureQuestions(record(result = ObservationResult.NO_CHANGES))
        assertEquals(listOf("foot"), unchanged.essential.map { it.id })
        assertTrue(unchanged.additional.isEmpty())
        val notPerformed = observationCaptureQuestions(record(result = ObservationResult.NOT_PERFORMED))
        assertTrue(notPerformed.essential.isEmpty())
        assertTrue(notPerformed.additional.isEmpty())
    }

    @Test fun eventReviewDoesNotAskForAnUnperformedFallsDetails() {
        val event = observationCaptureQuestions(record("OBS-022", ObservationResult.EVENT))
        assertEquals(listOf("event", "place", "discomfort", "head"), event.essential.map { it.id })
        val notPerformed = observationCaptureQuestions(record("OBS-022", ObservationResult.NOT_PERFORMED))
        assertTrue(notPerformed.essential.isEmpty())
        assertTrue(notPerformed.additional.isEmpty())
    }

    @Test fun newRecordsHaveOneNarrativeWhileHistoricalNarrativesRemainEditable() {
        listOf("OBS-001" to "finding", "OBS-009" to "details", "OBS-027" to "additional").forEach { (id, field) ->
            val input = record(id)
            assertFalse(observationCaptureQuestions(input).additional.any { it.id == field })
            val old = input.copy(answers = mapOf(field to listOf("Detalle histórico ficticio")))
            assertTrue(observationCaptureQuestions(old).additional.any { it.id == field })
            assertEquals(listOf("Detalle histórico ficticio"), old.answers[field])
        }
    }

    @Test fun comparisonAppearsOnlyAsOptionalContextAfterAPreviousRecord() {
        val first = observationCaptureQuestions(record("OBS-004"), hasPreviousRecord = false)
        assertFalse(first.essential.any { it.id == "continues" })
        assertFalse(first.additional.any { it.id == "continues" })

        val later = observationCaptureQuestions(record("OBS-004"), hasPreviousRecord = true)
        assertFalse(later.essential.any { it.id == "continues" })
        assertTrue(later.additional.any { it.id == "continues" })
        assertFalse(ObservationCatalog.get("OBS-004").fields.first { it.id == "continues" }.required)
    }

    @Test fun correctionKeepsHistoricalTextEditableWithoutSilentlyReplacingIt() {
        val original = record("OBS-027").copy(description = "Hecho histórico ".repeat(80),
            answers = mapOf("zone" to listOf("Zona histórica ".repeat(20))))
        val state = ObservationsUiState(
            loading = false, snapshot = ObservationSnapshot(records = listOf(original)),
            draft = original.copy(occurredOn = original.occurredOn.minusDays(1)),
            correctionOriginalId = original.id, correctionReason = "Corrección ficticia",
        )
        assertTrue(observationCanSubmit(state, now))
        assertFalse(observationCanSubmit(state.copy(draft = state.draft?.copy(description = "x".repeat(501))), now))
        assertFalse(observationCanSubmit(state.copy(correctionReason = "x".repeat(301)), now))
        assertEquals(original.description, state.draft?.description)
    }

    @Test fun unknownTimeRequiresExplicitSelectionBeforeBecomingApproximateOrExact() {
        val input = record()
        val unknown = requireNotNull(observationWithTimePrecision(input, ObservationTimePrecision.UNKNOWN))
        assertNull(unknown.occurredAt)
        assertEquals(ObservationTimePrecision.UNKNOWN, unknown.timePrecision)
        assertNull(observationWithTimePrecision(unknown, ObservationTimePrecision.APPROXIMATE))
        assertNull(observationWithTimePrecision(unknown, ObservationTimePrecision.EXACT))
        assertNull(unknown.occurredAt)
    }

    @Test fun markingAKnownTimeApproximatePreservesTheDeclaredHourAndRecordIdentity() {
        val input = record()
        val approximate = requireNotNull(observationWithTimePrecision(input, ObservationTimePrecision.APPROXIMATE))
        assertEquals(input.occurredAt, approximate.occurredAt)
        assertEquals(input.occurredOn, approximate.occurredOn)
        assertEquals(input.id, approximate.id)
        assertEquals(ObservationTimePrecision.APPROXIMATE, approximate.timePrecision)
        val exact = observationWithTimePrecision(approximate, ObservationTimePrecision.EXACT)
        assertEquals(input, exact)
    }
}
