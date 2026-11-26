package com.bitavida.app.ui.observations

import com.bitavida.app.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationDetailPresentationTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun latestUsesActualEventDateAndExcludesUnperformedAndOtherTypes() {
        val older = record("older", today.minusDays(1), LocalTime.NOON)
        val latest = record("latest", today, LocalTime.of(8, 0))
        val administrative = record("unperformed", today, LocalTime.of(18, 0)).copy(result = ObservationResult.NOT_PERFORMED)
        val unrelated = record("unrelated", today.plusDays(1), LocalTime.NOON).copy(observationId = "OBS-009")
        assertEquals(latest, latestObservationRecord("OBS-001", listOf(unrelated, older, administrative, latest)))
        assertNull(latestObservationRecord("OBS-002", listOf(latest)))
    }

    @Test fun latestDoesNotTreatRecentCaptureOfAnOldFactAsNewestEvent() {
        val actualNewer = record("newer", today, LocalTime.of(8, 0))
        val capturedLater = record("older", today.minusDays(4), LocalTime.NOON)
            .copy(createdAt = today.atTime(22, 0))
        assertEquals(actualNewer, latestObservationRecord("OBS-001", listOf(capturedLater, actualNewer)))
    }

    @Test fun summariesOnlyUseDeclaredContentAndDoNotInventClosedResults() {
        val record = record("fact", today, LocalTime.NOON).copy(
            answers = mapOf("foot" to listOf("Ambos"), "changes" to listOf("Ampolla")),
            description = "",
        )
        assertEquals("Ambos · Ampolla", observationSummary(record))
        assertEquals("Detalle ficticio transcrito", observationSummary(record.copy(description = "Detalle ficticio transcrito")))
        assertEquals("", observationSummary(record.copy(answers = emptyMap())))
    }

    @Test fun timePrecisionIsPreservedRatherThanFilledWithReminderTime() {
        val record = record("fact", today, null).copy(timePrecision = ObservationTimePrecision.UNKNOWN,
            scheduledAt = today.atTime(8, 0))
        assertEquals("Hora desconocida", observationTimeLabel(record))
        assertTrue(observationTimeLabel(record.copy(occurredAt = LocalTime.NOON,
            timePrecision = ObservationTimePrecision.APPROXIMATE)).startsWith("Aproximadamente "))
    }

    @Test fun indicationOnlyAppearsForAnActiveRevisionAtTheCurrentDay() {
        val revision = followUp().schedule.currentRevision
        assertTrue(observationIndicationIsCurrent(revision, today))
        assertFalse(observationIndicationIsCurrent(revision.copy(startsOn = today.plusDays(1), effectiveFrom = today.plusDays(1)), today))
        assertFalse(observationIndicationIsCurrent(revision.copy(startsOn = today.minusDays(2), effectiveFrom = today.minusDays(2), endsOn = today.minusDays(1)), today))
        assertFalse(observationIndicationIsCurrent(revision.copy(changeKind = GlucoseFollowUpChangeKind.REMOVED), today))
        assertTrue(observationIndicationIsCurrent(revision.copy(endsOn = today), today))
    }

    @Test fun followUpPresentationKeepsSuspensionSeparateWithoutTemporalValidityLabels() {
        val followUp = followUp()
        assertEquals("Sin seguimiento programado", observationFollowUpLabel(null, today))
        assertEquals("Seguimiento activo", observationFollowUpLabel(followUp, today))
        val suspended = followUp.copy(schedule = followUp.schedule.copy(
            revisions = listOf(followUp.schedule.currentRevision.copy(changeKind = GlucoseFollowUpChangeKind.REMOVED))))
        assertEquals("Sin seguimiento programado", observationFollowUpLabel(suspended, today))
        val futureLegacy = followUp.copy(schedule = followUp.schedule.copy(
            revisions = listOf(followUp.schedule.currentRevision.copy(startsOn = today.plusDays(1), effectiveFrom = today.plusDays(1)))))
        assertEquals("Sin seguimiento activo", observationFollowUpLabel(futureLegacy, today))
    }

    private fun followUp(): ObservationFollowUp {
        val revision = GlucoseFollowUpRevision(
            id = "fictitious-revision", followUpId = "fictitious-follow-up", revisionNumber = 1,
            indication = "Indicación ficticia transcrita", frequency = GlucoseFollowUpFrequency.DAILY,
            startsOn = today, effectiveFrom = today,
            slots = listOf(GlucoseFollowUpSlot(
                id = "fictitious-slot", time = LocalTime.of(8, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false,
            )),
            remindersEnabled = false,
            changeKind = GlucoseFollowUpChangeKind.CREATED, capturedAt = today.atTime(8, 0),
            authorActorId = "fictitious-caregiver",
        )
        return ObservationFollowUp("OBS-001", GlucoseFollowUp(
            id = "fictitious-follow-up", personUnderCareId = "fictitious-person",
            createdAt = revision.capturedAt, createdByActorId = revision.authorActorId,
            revisions = listOf(revision),
        ))
    }

    private fun record(id: String, day: LocalDate, time: LocalTime?) = ObservationRecord(
        id = id, observationId = "OBS-001", personUnderCareId = "fictitious-person",
        occurredOn = day, occurredAt = time,
        timePrecision = if (time == null) ObservationTimePrecision.UNKNOWN else ObservationTimePrecision.EXACT,
        result = ObservationResult.CHANGES, answers = emptyMap(), description = "",
        createdAt = LocalDateTime.of(2026, 11, 18, 12, 0), actorId = "fictitious-caregiver",
    )
}
