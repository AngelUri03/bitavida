package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.ObservationRepository
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ObservationCorrectionTest {
    private val now = LocalDateTime.of(2026, 11, 19, 12, 0)
    private val actor = GlucoseActor("fictional-profile", "fictional-actor", "fictional-person", true)
    private val original = ObservationRecord(
        "fictional-fact", "OBS-001", actor.personUnderCareId, now.toLocalDate(), null,
        ObservationTimePrecision.UNKNOWN, ObservationResult.NOT_PERFORMED, emptyMap(), "",
        followUpId = "fictional-follow-up", occurrenceId = "fictional-occurrence", scheduledAt = now.minusHours(2),
        createdAt = now.minusHours(1), actorId = actor.caregiverId,
    )
    private val corrected = original.copy(result = ObservationResult.NO_CHANGES,
        occurredAt = now.minusHours(2).toLocalTime(), timePrecision = ObservationTimePrecision.EXACT,
        answers = mapOf("foot" to listOf("Ambos")))
    private fun revision() = ObservationRecordRevision("fictional-correction", original.id, 2, corrected,
        "Se corrigió una declaración ficticia", now, actor.caregiverId)

    @Test fun correctingNotPerformedKeepsOriginalAndExactOccurrenceWithoutAddingAnEvent() = runBlocking {
        val repository = FakeRepository(original)
        val saved = CorrectObservationUseCase(repository)(actor, revision(), 1, now)
        assertEquals(original, repository.snapshot.recordRevisions.first().snapshot)
        assertEquals(corrected, repository.snapshot.records.single())
        assertEquals(listOf(1, 2), repository.snapshot.recordRevisions.map { it.revisionNumber })
        assertEquals(original.occurrenceId, saved.snapshot.occurrenceId)
        assertEquals(original.scheduledAt, saved.snapshot.scheduledAt)
        assertEquals(ObservationResult.NOT_PERFORMED, repository.snapshot.recordRevisions.first().snapshot.result)
        assertEquals(ObservationResult.NO_CHANGES, repository.snapshot.records.single().result)
        assertEquals(1, projectObservationTimeline(now.toLocalDate(), null, repository.snapshot, now).size)
    }

    @Test fun exactOperationRetryReturnsCommittedVersionAfterAYoungerCorrection() = runBlocking {
        val repository = FakeRepository(original)
        val correct = CorrectObservationUseCase(repository)
        val second = correct(actor, revision(), 1, now)
        val third = second.copy(id = "fictional-third", revisionNumber = 3,
            snapshot = corrected.copy(description = "Detalle ficticio posterior"),
            reason = "Segundo motivo ficticio", correctedAt = now.plusMinutes(1))
        correct(actor, third, 2, now.plusMinutes(1))
        assertEquals(second, correct(actor, revision(), 1, now.plusMinutes(2)))
        assertEquals(3, repository.snapshot.recordRevisions.size)
        assertEquals(third.snapshot, repository.snapshot.records.single())
    }

    @Test fun reasonVersionIdentityAndAssociationMustBeExplicitAndUnchanged() {
        val cases = listOf(
            revision().copy(reason = " "),
            revision().copy(revisionNumber = 4),
            revision().copy(snapshot = corrected.copy(observationId = "OBS-027")),
            revision().copy(snapshot = corrected.copy(id = "another-fact")),
            revision().copy(snapshot = corrected.copy(followUpId = null, occurrenceId = null, scheduledAt = null)),
            revision().copy(snapshot = corrected.copy(createdAt = now)),
            revision().copy(snapshot = corrected.copy(personUnderCareId = "another-person")),
            revision().copy(correctedAt = now.plusMinutes(1)),
        )
        cases.forEach { assertFalse(validateObservationCorrection(original, it, 1, actor, now).isEmpty()) }
        assertTrue(validateObservationCorrection(original, revision(), 1, actor, now).isEmpty())
    }

    @Test fun consentAndOptimisticVersionPreventUnauthorizedOrLostCorrections() = runBlocking {
        val repository = FakeRepository(original)
        val correct = CorrectObservationUseCase(repository)
        repository.consent = false
        try { correct(actor, revision(), 1, now); fail("Consent required") } catch (_: IllegalArgumentException) { }
        repository.consent = true
        correct(actor, revision(), 1, now)
        try {
            correct(actor, revision().copy(id = "stale-operation"), 1, now)
            fail("Stale version")
        } catch (_: IllegalArgumentException) { }
        assertEquals(2, repository.snapshot.recordRevisions.size)
    }

    @Test fun descriptionUsesOnlyKnownAnswersAndDoesNotInventFactsOrSave() {
        val wound = original.copy(observationId = "OBS-002", result = ObservationResult.CHANGES,
            followUpId = null, occurrenceId = null, scheduledAt = null,
            answers = mapOf("lesion" to listOf("Ampolla"), "zone" to listOf("pie"),
                "side" to listOf("Izquierdo"), "onset" to listOf("ayer")))
        val proposal = buildObservationDescription(wound)
        assertTrue(proposal.contains("ampolla"))
        assertTrue(proposal.contains("pie"))
        assertTrue(proposal.contains("izquierdo"))
        assertTrue(proposal.contains("ayer"))
        assertFalse(proposal.contains("infección", ignoreCase = true))
        assertFalse(proposal.contains("grave", ignoreCase = true))
        assertFalse(proposal.contains("tratamiento", ignoreCase = true))
        assertEquals("", wound.description)
        assertEquals("", buildObservationDescription(wound.copy(answers = emptyMap())))
        assertEquals("", buildObservationDescription(wound.copy(answers = mapOf("not-a-field" to listOf("invented")))))
        assertEquals("No se realizó la revisión.", buildObservationDescription(original))
        assertTrue(buildObservationDescription(corrected).startsWith("No se observaron cambios."))
    }

    private class FakeRepository(original: ObservationRecord) : ObservationRepository {
        var consent = true
        var snapshot = ObservationSnapshot(listOf(original), recordRevisions = listOf(
            ObservationRecordRevision(original.id, original.id, 1, original, null, original.createdAt, original.actorId)))
        override suspend fun hasSensitiveConsent(actor: GlucoseActor) = consent
        override suspend fun acceptSensitiveConsent(actor: GlucoseActor, acceptedAtEpochMillis: Long) { consent = true }
        override suspend fun load(actor: GlucoseActor) = snapshot
        override suspend fun saveRecord(actor: GlucoseActor, record: ObservationRecord) = error("Not an insert")
        override suspend fun appendFollowUp(actor: GlucoseActor, followUp: ObservationFollowUp, expectedRevisionNumber: Int) =
            error("Not a follow-up change")
        override suspend fun appendRecordCorrection(actor: GlucoseActor, revision: ObservationRecordRevision,
            expectedRevisionNumber: Int): ObservationRecordRevision {
            check(snapshot.recordRevisions.filter { it.recordId == revision.recordId }.maxOf { it.revisionNumber } == expectedRevisionNumber)
            snapshot = snapshot.copy(records = snapshot.records.map { if (it.id == revision.recordId) revision.snapshot else it },
                recordRevisions = snapshot.recordRevisions + revision)
            return revision
        }
    }
}
