package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseEntryKind
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpSlot
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionKind
import com.bitavida.app.domain.model.GlucoseRecordMetadata
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.occurrencesOn
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseHistoryTest {
    private val today = LocalDateTime.of(2026, 11, 18, 10, 0)

    @Test
    fun completeHistoryIncludesEveryWeekAndOnlyGlucose() {
        val recent = glucose("recent", today, "128")
        val previous = glucose("previous", today.minusWeeks(3), "132")
        val oldest = glucose("oldest", today.minusYears(1), "HI")
        val pressure = MeasurementRecord("pressure", MeasurementValue.BloodPressure("118", "72"), today, today)
        val weight = MeasurementRecord("weight", MeasurementValue.Weight(BigDecimal("74.2")), today, today)
        val history = projectGlucoseHistory(listOf(previous, pressure, oldest, recent, weight))
        assertEquals(
            listOf(today.toLocalDate(), previous.occurredAt.toLocalDate(), oldest.occurredAt.toLocalDate()),
            history.map { (date) -> date },
        )
        assertEquals(
            listOf("recent", "previous", "oldest"),
            history.asSequence().flatMap { it.records }.map { it.id }.toList(),
        )
    }

    @Test
    fun dayAndOrderingUseEventTimeRatherThanCaptureTime() {
        val morning = glucose("morning", today.withHour(8), "128")
        val evening = glucose("evening", today.withHour(18), "134")
        val backdated = glucose("backdated", today.minusDays(1), "130")
            .copy(capturedAt = today.plusWeeks(1))
        val history = projectGlucoseHistory(listOf(morning, backdated, evening))
        assertEquals(listOf("evening", "morning"), history.first().records.map { it.id })
        assertEquals(backdated.occurredAt.toLocalDate(), history.last().date)
    }

    @Test
    fun coincidentEventsAndNonnumericReadingsAreNeverCollapsed() {
        val records = listOf(
            glucose("one", today, "128"),
            glucose("two", today, "128"),
            glucose("hi", today, "HI"),
            glucose("lo", today, "LO"),
        )
        val history = projectGlucoseHistory(records)
        assertEquals(4, history.single().records.size)
        assertEquals(records.map { it.id }.toSet(), history.single().records.map { it.id }.toSet())
        assertTrue(history.single().records.any { (it.value as MeasurementValue.Glucose).reading == "HI" })
        assertTrue(history.single().records.any { (it.value as MeasurementValue.Glucose).reading == "LO" })
    }

    @Test
    fun descriptiveStatisticsExcludeHiAndLoWithoutLosingTheirCount() {
        val statistics = calculateGlucoseStatistics(
            records = listOf(
                glucose("minimum", today, "100"),
                glucose("maximum", today, "150"),
                glucose("hi", today, "HI"),
                glucose("lo", today, "LO"),
            ),
        )

        assertEquals(BigDecimal("125.0"), statistics.average)
        assertEquals(BigDecimal("100"), statistics.minimum)
        assertEquals(BigDecimal("150"), statistics.maximum)
        assertEquals(4, statistics.totalMeasurements)
        assertEquals(2, statistics.numericMeasurements)
        assertEquals(2, statistics.nonNumericMeasurements)
    }

    @Test
    fun historyCombinesReadingsExplicitDecisionsAndPastUnconfirmedOccurrences() {
        val followUp = glucoseFollowUp()
        val mondayOccurrence = followUp.occurrencesOn(
            today.toLocalDate().minusDays(2),
        ).first()
        val tuesdayOccurrence = followUp.occurrencesOn(
            today.toLocalDate().minusDays(1),
        ).first()
        val record = glucose(
            "scheduled-reading",
            mondayOccurrence.scheduledAt.plusMinutes(5),
            "121",
        ).copy(
            glucoseMetadata = GlucoseRecordMetadata(
                entryKind = GlucoseEntryKind.SCHEDULED,
                followUpId = followUp.id,
                scheduledOccurrenceId = mondayOccurrence.id,
                scheduledAt = mondayOccurrence.scheduledAt,
                expectedContext = mondayOccurrence.suggestedContext,
            ),
        )
        val decision = GlucoseOccurrenceDecision(
            id = "decision-test",
            followUpId = followUp.id,
            followUpRevisionId = tuesdayOccurrence.followUpRevisionId,
            occurrenceId = tuesdayOccurrence.id,
            scheduledAt = tuesdayOccurrence.scheduledAt,
            kind = GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
            reason = "La persona cuidadora indicó un motivo ficticio.",
            capturedAt = tuesdayOccurrence.scheduledAt.plusHours(1),
            actorId = "caregiver-test",
        )

        val period = projectGlucoseHistoryPeriod(
            records = listOf(record),
            anchor = today.toLocalDate(),
            kind = GlucoseHistoryPeriodKind.WEEK,
            today = today.toLocalDate(),
            followUp = followUp,
            decisions = listOf(decision),
            now = today,
        )

        assertTrue(
            period.days.first { it.date == mondayOccurrence.scheduledAt.toLocalDate() }
                .events.any { it is GlucoseHistoryEvent.Registered },
        )
        assertTrue(
            period.days.first { it.date == tuesdayOccurrence.scheduledAt.toLocalDate() }
                .events.any { it is GlucoseHistoryEvent.NotPerformed },
        )
        val todayEvents = period.days.first { it.date == today.toLocalDate() }.events
        assertEquals(1, todayEvents.size)
        assertTrue(todayEvents.single() is GlucoseHistoryEvent.Unconfirmed)
        assertEquals(1, period.statistics.totalMeasurements)
        assertEquals(BigDecimal("121.0"), period.statistics.average)
    }

    @Test
    fun deactivationKeepsEarlierOccurrencesWithoutInventingLaterOnes() {
        val active = glucoseFollowUp()
        val removed = active.copy(
            revisions = active.revisions + active.currentRevision.copy(
                id = "follow-up-revision-2",
                revisionNumber = 2,
                effectiveFrom = today.toLocalDate(),
                changeKind = GlucoseFollowUpChangeKind.REMOVED,
                capturedAt = today.minusHours(1),
            ),
        )
        val period = projectGlucoseHistoryPeriod(
            records = emptyList(),
            anchor = today.toLocalDate(),
            kind = GlucoseHistoryPeriodKind.WEEK,
            today = today.toLocalDate(),
            followUp = removed,
            now = today.plusDays(1),
        )

        assertTrue(
            period.days.first { it.date == today.toLocalDate().minusDays(1) }
                .events.isNotEmpty(),
        )
        val removalDay = period.days.first { it.date == today.toLocalDate() }.events
        assertEquals(1, removalDay.size)
        assertTrue(removalDay.single() is GlucoseHistoryEvent.Unconfirmed)
        assertEquals(today.toLocalDate().atTime(8, 0), removalDay.single().sortAt)
    }

    @Test
    fun monthlyPeriodProjectsEveryDayForThePanoramicView() {
        val period = projectGlucoseHistoryPeriod(
            records = listOf(glucose("one", today, "128")),
            anchor = today.toLocalDate(),
            kind = GlucoseHistoryPeriodKind.MONTH,
            today = today.toLocalDate(),
        )

        assertEquals(30, period.history.days.size)
        assertEquals(period.start, period.history.days.first().date)
        assertEquals(period.endInclusive, period.history.days.last().date)
    }

    @Test
    fun weeklyPeriodAlwaysProjectsSevenNavigableDays() {
        val period = projectGlucoseHistoryPeriod(
            records = listOf(glucose("one", today, "128")),
            anchor = today.toLocalDate(),
            kind = GlucoseHistoryPeriodKind.WEEK,
            today = today.toLocalDate(),
        )

        assertEquals(7, period.history.days.size)
        assertEquals(period.start, period.history.days.first().date)
        assertEquals(period.endInclusive, period.history.days.last().date)
    }

    private fun glucose(id: String, time: LocalDateTime, value: String) =
        MeasurementRecord(id, MeasurementValue.Glucose(value), time, time)

    private fun glucoseFollowUp(): GlucoseFollowUp {
        val start = today.toLocalDate().minusDays(2)
        return GlucoseFollowUp(
            id = "follow-up-test",
            personUnderCareId = "person-test",
            createdAt = start.atStartOfDay(),
            createdByActorId = "caregiver-test",
            revisions = listOf(
                GlucoseFollowUpRevision(
                    id = "follow-up-revision-1",
                    followUpId = "follow-up-test",
                    revisionNumber = 1,
                    indication = "Indicación médica ficticia transcrita.",
                    frequency = GlucoseFollowUpFrequency.DAILY,
                    startsOn = start,
                    effectiveFrom = start,
                    slots = listOf(
                        GlucoseFollowUpSlot(
                            id = "morning",
                            time = LocalTime.of(8, 0),
                            suggestedContext = GlucoseContext.FASTING,
                            reminderEnabled = false,
                        ),
                        GlucoseFollowUpSlot(
                            id = "afternoon",
                            time = LocalTime.of(14, 0),
                            suggestedContext = GlucoseContext.AFTER_MEAL,
                            reminderEnabled = false,
                        ),
                    ),
                    remindersEnabled = false,
                    changeKind = GlucoseFollowUpChangeKind.CREATED,
                    capturedAt = start.atStartOfDay(),
                    authorActorId = "caregiver-test",
                ),
            ),
        )
    }
}
