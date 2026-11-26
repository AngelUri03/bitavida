package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationFocusedHistoryTest {
    private val now = LocalDateTime.of(2026, 11, 18, 14, 0)

    @Test fun exactObservationFilterIncludesOnlyItsFactsAndUnconfirmedReviews() {
        val facts = listOf(record("target", "OBS-004"), record("same-category", "OBS-002"),
            record("different-category", "OBS-006"), record("other-day", "OBS-004").copy(
                occurredOn = now.toLocalDate().minusDays(1)))
        val schedules = listOf(followUp("OBS-004"), followUp("OBS-002"))
        val snapshot = ObservationSnapshot(records = facts, followUps = schedules)
        val focused = projectObservationTimeline(now.toLocalDate(), null, snapshot, now, "OBS-004")
        assertEquals(2, focused.size)
        assertTrue(focused.all { it.observationId == "OBS-004" })
        assertEquals(listOf("target"), focused.mapNotNull { it.record?.id })
        assertEquals(1, focused.count { it.occurrence?.state == ObservationOccurrenceState.UNCONFIRMED })
        assertEquals(5, projectObservationTimeline(now.toLocalDate(), null, snapshot, now).size)
    }

    @Test fun changingDatesKeepsExactFilterAndNeverTreatsNoFactAsNoChanges() {
        val date = now.toLocalDate().minusDays(1)
        val fact = record("yesterday", "OBS-004").copy(occurredOn = date)
        val unrelated = record("unrelated", "OBS-002").copy(occurredOn = date)
        val snapshot = ObservationSnapshot(records = listOf(fact, unrelated))
        assertEquals(listOf(fact), projectObservationTimeline(date, null, snapshot, now, "OBS-004")
            .mapNotNull { it.record })
        assertTrue(projectObservationTimeline(now.toLocalDate(), null, snapshot, now, "OBS-004").isEmpty())
    }

    private fun record(id: String, observationId: String) = ObservationRecord(
        id, observationId, "fictional-person", now.toLocalDate(), LocalTime.of(10, 0),
        ObservationTimePrecision.EXACT, ObservationResult.CHANGES, emptyMap(), "Descripción ficticia",
        createdAt = now, actorId = "fictional-caregiver",
    )

    private fun followUp(observationId: String): ObservationFollowUp {
        val id = "fictional-follow-up-" + observationId
        val revision = GlucoseFollowUpRevision(
            id = "revision-" + observationId, followUpId = id, revisionNumber = 1,
            indication = "Indicación ficticia", frequency = GlucoseFollowUpFrequency.DAILY,
            startsOn = now.toLocalDate().minusDays(1), effectiveFrom = now.toLocalDate().minusDays(1),
            slots = listOf(GlucoseFollowUpSlot("slot-" + observationId, time = LocalTime.of(8, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false)),
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = now.minusDays(1), authorActorId = "fictional-caregiver",
        )
        return ObservationFollowUp(observationId, GlucoseFollowUp(id, "fictional-person",
            now.minusDays(1), "fictional-caregiver", listOf(revision)))
    }
}
