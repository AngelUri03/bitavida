package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReadCareEventReportHistoryTest {
    private val actor = CareEventActor("profile", "caregiver", "patient", true)
    private val first = LocalDate.of(2026, 1, 10)
    private fun event(revision: Int, scheduled: LocalDate, changed: LocalDate,
        title: String = "Cita ficticia"): CareEvent = CareEvent(
        "event", "patient",
        CareEventDraft(CareEventKind.APPOINTMENT, title, scheduledDate = scheduled,
            status = CareEventStatus.CONFIRMED),
        first.atStartOfDay(), changed.atStartOfDay(), revision,
    )

    private class Repository(
        val reads: MutableList<List<CareEvent>>,
        var versions: List<CareEvent>,
    ) : CareEventRepository {
        override suspend fun load(actor: CareEventActor): List<CareEvent> =
            if (reads.size > 1) reads.removeAt(0) else reads.single()
        override suspend fun history(actor: CareEventActor, id: String) = versions
        override suspend fun hasSensitiveConsent(actor: CareEventActor) = true
        override suspend fun acceptSensitiveConsent(actor: CareEventActor, acceptedAtEpochMillis: Long) = Unit
        override suspend fun save(actor: CareEventActor, command: CareEventCommand, now: LocalDateTime): CareEvent =
            error("No writes are allowed in this test")
    }

    @Test fun includesCompleteHistoryAndChoosesVersionKnownAtEnd() = runBlocking {
        val initial = event(1, first.plusDays(10), first)
        val revised = event(2, first.plusDays(20), first.plusDays(3))
        val result = readCareEventReportHistory(Repository(mutableListOf(listOf(revised)), listOf(initial, revised)), actor)
        assertEquals(revised, result.events.single())
        assertEquals(initial, careEventVersionAtEnd(result.revisionsByEvent.getValue("event"), first.plusDays(2)))
        assertEquals(revised, careEventVersionAtEnd(result.revisionsByEvent.getValue("event"), first.plusDays(3)))
        assertNull(careEventVersionAtEnd(result.revisionsByEvent.getValue("event"), first.minusDays(1)))
        val change = careEventReschedulings(result.revisionsByEvent.getValue("event"), first, first.plusDays(5)).single()
        assertEquals(initial, change.previous)
        assertEquals(revised, change.revised)
        assertTrue(careEventReschedulings(result.revisionsByEvent.getValue("event"), first.plusDays(4), first.plusDays(5)).isEmpty())
    }

    @Test fun retriesWhenEventChangesDuringRead() = runBlocking {
        val old = event(1, first.plusDays(1), first)
        val current = event(2, first.plusDays(2), first.plusDays(1))
        val repo = Repository(mutableListOf(listOf(old), listOf(current), listOf(current), listOf(current)), listOf(old, current))
        val result = readCareEventReportHistory(repo, actor)
        assertEquals(current, result.events.single())
    }

    @Test fun incompleteHistoryIsNeverPresentedAsComplete() = runBlocking {
        val current = event(2, first.plusDays(2), first.plusDays(1))
        val failure = runCatching { readCareEventReportHistory(Repository(mutableListOf(listOf(current)), listOf(current)), actor) }
            .exceptionOrNull()
        assertTrue(failure is CareEventHistoryChangedException)
    }

    @Test fun distinctRecordsAndUnknownTimesAreNotInferredAsRescheduled() {
        val initial = event(1, first.plusDays(1), first)
        val unknownHour = initial.copy(revision = 2, modifiedAt = first.plusDays(1).atStartOfDay(),
            draft = initial.draft.copy(scheduledAt = first.plusDays(1).atTime(9, 0)))
        val unrelated = unknownHour.copy(id = "different", revision = 3, modifiedAt = first.plusDays(2).atStartOfDay(),
            draft = unknownHour.draft.copy(scheduledDate = first.plusDays(5), scheduledAt = first.plusDays(5).atTime(9, 0)))
        assertTrue(careEventReschedulings(listOf(initial, unknownHour, unrelated), first, first.plusDays(3)).isEmpty())
    }
}
