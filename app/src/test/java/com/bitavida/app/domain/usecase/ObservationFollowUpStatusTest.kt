package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationFollowUpStatusTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val now = today.atTime(12, 0)
    private val person = "fictional-person"

    private fun followUp(
        observationId: String,
        id: String,
        start: LocalDate,
        end: LocalDate? = null,
        owner: String = person,
        capturedAt: LocalDateTime = today.minusDays(1).atTime(10, 0),
    ): ObservationFollowUp {
        val revision = GlucoseFollowUpRevision(
            id = "$id-rev-1", followUpId = id, revisionNumber = 1,
            indication = "Indicación ficticia", frequency = GlucoseFollowUpFrequency.DAILY,
            startsOn = start, endsOn = end, effectiveFrom = start,
            slots = listOf(GlucoseFollowUpSlot(
                id = "$id-slot", time = LocalTime.of(8, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED,
                reminderEnabled = false,
            )),
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = capturedAt, authorActorId = "fictional-caregiver",
        )
        return ObservationFollowUp(observationId,
            GlucoseFollowUp(id, owner, capturedAt, "fictional-caregiver", listOf(revision)))
    }

    @Test fun includesActiveAndFutureButNotEndedDeletedOrOtherPerson() {
        val active = followUp("OBS-001", "active", today.minusDays(3))
        val future = followUp("OBS-002", "future", today.plusDays(2))
        val ended = followUp("OBS-003", "ended", today.minusDays(8), today.minusDays(1))
        val other = followUp("OBS-004", "other", today, owner = "another-person")
        val base = followUp("OBS-005", "deleted", today.minusDays(8))
        val removed = base.schedule.currentRevision.copy(
            id = "deleted-rev-2", revisionNumber = 2, startsOn = today,
            effectiveFrom = today, changeKind = GlucoseFollowUpChangeKind.REMOVED,
            capturedAt = now.minusMinutes(1), changeReason = "Motivo ficticio",
        )
        val deleted = base.copy(schedule = base.schedule.copy(revisions = base.schedule.revisions + removed))

        assertEquals(
            mapOf("OBS-001" to ObservationFollowUpDisplayStatus.ACTIVE,
                "OBS-002" to ObservationFollowUpDisplayStatus.UPCOMING),
            observationFollowUpStatuses(listOf(active, future, ended, other, deleted), person, now),
        )
    }

    @Test fun futureStartBecomesActiveOnItsDateAndSameDayEndIsStillValid() {
        val future = followUp("OBS-001", "future", today.plusDays(1), today.plusDays(1))
        assertEquals(ObservationFollowUpDisplayStatus.UPCOMING,
            observationFollowUpStatuses(listOf(future), person, now)["OBS-001"])
        assertEquals(ObservationFollowUpDisplayStatus.ACTIVE,
            observationFollowUpStatuses(listOf(future), person, now.plusDays(1))["OBS-001"])
        assertTrue(observationFollowUpStatuses(listOf(future), person, now.plusDays(2)).isEmpty())
    }

    @Test fun countsDistinctObservationsAndPreservesActiveOverUpcoming() {
        val active = followUp("OBS-001", "active", today)
        val future = followUp("OBS-001", "future", today.plusDays(5))
        val statuses = observationFollowUpStatuses(listOf(future, active, future), person, now)
        assertEquals(1, statuses.size)
        assertEquals(ObservationFollowUpDisplayStatus.ACTIVE, statuses["OBS-001"])
    }

    @Test fun doesNotUseAnUncapturedRevision() {
        val base = followUp("OBS-001", "a", today.minusDays(1))
        val removed = base.schedule.currentRevision.copy(
            id = "a-rev-2", revisionNumber = 2, effectiveFrom = today,
            changeKind = GlucoseFollowUpChangeKind.REMOVED, capturedAt = now.plusMinutes(1),
            changeReason = "Motivo ficticio",
        )
        val item = base.copy(schedule = base.schedule.copy(revisions = base.schedule.revisions + removed))
        assertEquals(ObservationFollowUpDisplayStatus.ACTIVE,
            observationFollowUpStatuses(listOf(item), person, now)["OBS-001"])
        assertTrue(observationFollowUpStatuses(listOf(item), person, now.plusMinutes(2)).isEmpty())
    }

    @Test fun supersededFutureRevisionDoesNotReviveAnEndedFollowUp() {
        val obsolete = followUp("OBS-001", "a", today.plusDays(4))
        val replacement = obsolete.schedule.currentRevision.copy(
            id = "a-rev-2", revisionNumber = 2, startsOn = today.minusDays(5),
            endsOn = today.minusDays(1), effectiveFrom = today.minusDays(5),
            changeKind = GlucoseFollowUpChangeKind.CORRECTED,
            capturedAt = now.minusHours(1),
        )
        val item = obsolete.copy(schedule = obsolete.schedule.copy(revisions = listOf(
            obsolete.schedule.currentRevision, replacement,
        )))
        assertTrue(observationFollowUpStatuses(listOf(item), person, now).isEmpty())
    }

    @Test fun futureRevisionDoesNotHideAnEarlierActiveIndication() {
        val base = followUp("OBS-001", "a", today.minusDays(2))
        val upcoming = base.schedule.currentRevision.copy(
            id = "a-rev-2", revisionNumber = 2, startsOn = today.plusDays(3),
            effectiveFrom = today.plusDays(3), changeKind = GlucoseFollowUpChangeKind.INDICATION_CHANGED,
            capturedAt = now.minusHours(1),
        )
        val item = base.copy(schedule = base.schedule.copy(revisions = base.schedule.revisions + upcoming))
        assertEquals(ObservationFollowUpDisplayStatus.ACTIVE,
            observationFollowUpStatuses(listOf(item), person, now)["OBS-001"])
    }
}
