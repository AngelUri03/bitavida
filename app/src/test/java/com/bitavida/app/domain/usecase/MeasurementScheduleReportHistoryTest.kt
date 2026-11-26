package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class MeasurementScheduleReportHistoryTest {
    private val first = LocalDateTime.of(2026, 9, 1, 8, 0)
    private val next = first.plusMonths(1)
    private val slot = GlucoseFollowUpSlot("slot", time = LocalTime.of(8, 0),
        suggestedContext = GlucoseContext.FASTING, reminderEnabled = false)
    private val version = GlucoseFollowUpRevision(
        id = "v1", followUpId = "f", revisionNumber = 1, indication = "Seguimiento ficticio",
        frequency = GlucoseFollowUpFrequency.DAILY, startsOn = first.toLocalDate(),
        effectiveFrom = first.toLocalDate(), slots = listOf(slot), remindersEnabled = false,
        changeKind = GlucoseFollowUpChangeKind.CREATED, capturedAt = first, authorActorId = "actor",
    )
    private val followUp = GlucoseFollowUp("f", "patient", first, "actor",
        listOf(version, version.copy(id = "v2", revisionNumber = 2,
            indication = "Modificada", changeKind = GlucoseFollowUpChangeKind.CORRECTED, capturedAt = next)))
    private val decision = GlucoseOccurrenceDecision("d", "f", "v1", "occ", first.plusDays(1),
        GlucoseOccurrenceDecisionKind.NOT_PERFORMED, "actual", next.plusDays(1), "actor")
    private val revisions = listOf(
        GlucoseOccurrenceDecisionRevision("d1", "d", "occ", 1,
            GlucoseOccurrenceDecisionRevisionOperation.CONFIRMED, "anterior", first.plusDays(1), "actor"),
        GlucoseOccurrenceDecisionRevision("d2", "d", "occ", 2,
            GlucoseOccurrenceDecisionRevisionOperation.CORRECTED, "actual", next, "actor"),
        GlucoseOccurrenceDecisionRevision("d3", "d", "occ", 3,
            GlucoseOccurrenceDecisionRevisionOperation.CORRECTED_TO_RECORDED, null, next.plusDays(1), "actor"),
    )
    private val history = MeasurementScheduleReportHistory(listOf(
        MeasurementScheduleReportHistory.ScheduleHistory(MeasurementKind.CAPILLARY_GLUCOSE,
            followUp, listOf(decision to revisions)),
    ))

    @Test fun usesVersionsAndExplicitDecisionKnownAtCutoff() {
        val old = history.schedulesAt(next.minusSeconds(1)).single()
        assertEquals(listOf("v1"), old.followUp!!.revisions.map { it.id })
        assertEquals("anterior", old.decisions.single().reason)
        val corrected = history.schedulesAt(next).single()
        assertEquals(listOf("v1", "v2"), corrected.followUp!!.revisions.map { it.id })
        assertEquals("actual", corrected.decisions.single().reason)
        assertTrue(history.schedulesAt(next.plusDays(1)).single().decisions.isEmpty())
        val before = history.schedulesAt(first.minusSeconds(1)).single()
        assertNull(before.followUp)
        assertTrue(before.decisions.isEmpty())
    }

    @Test fun ambiguousHistoricalLinkNeverClaimsAnOccasionWasConfirmedOrUnconfirmed() {
        val day = first.toLocalDate().plusDays(1)
        val occurrence = version.declaredOccurrencesOn(day).single()
        val record = MeasurementRecord(
            id = "reading", value = MeasurementValue.Glucose("110", GlucoseContext.FASTING),
            occurredAt = occurrence.scheduledAt, capturedAt = occurrence.scheduledAt,
            glucoseMetadata = GlucoseRecordMetadata(GlucoseEntryKind.SCHEDULED,
                followUpId = "f", scheduledOccurrenceId = occurrence.id, scheduledAt = occurrence.scheduledAt),
            historicalAssociationUncertain = true,
        )
        val schedule = ReportMeasurementSchedule(MeasurementKind.CAPILLARY_GLUCOSE,
            followUp.copy(revisions = listOf(version)), emptyList())
        val report = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), emptyList()),
            listOf(record), emptyList(), measurementSchedules = listOf(schedule), now = day.atTime(23, 0))
        assertEquals(1, report.measurements.size)
        assertTrue(report.rows.single { it.section == "Mediciones" }.detail.contains("no verificable"))
        val scheduled = report.rows.single { it.section == "Mediciones programadas" }
        assertEquals("Asociación con registros existentes no verificable", scheduled.detail)
        assertFalse(report.rows.any { it.section == "Mediciones programadas" && it.detail == "Sin confirmar" })

        val linked = buildFollowUpReport(day, day, "patient", MedicalCareOverview(emptyList(), emptyList()),
            listOf(record.copy(historicalAssociationUncertain = false)), emptyList(),
            measurementSchedules = listOf(schedule), now = day.atTime(23, 0))
        assertTrue(linked.rows.none { it.section == "Mediciones programadas" })
    }

    @Test fun rejectsNonMonotonicDecisionTime() {
        val changed = revisions[1].copy(capturedAt = revisions[0].capturedAt.minusMinutes(1))
        val inconsistent = history.copy(schedules = history.schedules.map {
            it.copy(decisions = listOf(decision to listOf(revisions[0], changed, revisions[2])))
        })
        assertThrows(IllegalStateException::class.java) { inconsistent.schedulesAt(next) }
    }

    @Test fun rejectsMissingDecisionRevision() {
        val incomplete = history.copy(schedules = history.schedules.map {
            it.copy(decisions = listOf(decision to revisions.drop(1)))
        })
        assertThrows(IllegalStateException::class.java) { incomplete.schedulesAt(next) }
    }
}
