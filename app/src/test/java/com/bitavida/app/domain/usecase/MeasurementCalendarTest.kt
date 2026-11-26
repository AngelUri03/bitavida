package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.BloodPressureEntryKind
import com.bitavida.app.domain.model.BloodPressureRecordMetadata
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseEntryKind
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpSlot
import com.bitavida.app.domain.model.GlucoseFollowUpTimingType
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionKind
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.domain.model.GlucoseRecordMetadata
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.WeightEntryKind
import com.bitavida.app.domain.model.WeightRecordMetadata
import com.bitavida.app.domain.model.occurrencesOn
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCalendarTest {
    private val date = LocalDate.of(2026, 11, 18)
    private val now = date.atTime(18, 0)
    private val glucose = MeasurementKind.CAPILLARY_GLUCOSE

    @Test
    fun weekNavigationCrossesYearAndPreservesWeekdayAndFilter() {
        val selected = MeasurementCalendarSelection(LocalDate.of(2026, 12, 30), MeasurementKind.WEIGHT)
        val next = selected.moveWeek(1)
        assertEquals(LocalDate.of(2027, 1, 6), next.date)
        assertEquals(LocalDate.of(2027, 1, 4), next.weekStart)
        assertEquals(MeasurementKind.WEIGHT, next.filter)
        assertEquals(selected, next.moveWeek(-1))
        val week = project(selection = selected)
        assertEquals(LocalDate.of(2026, 12, 28), week.start)
        assertEquals(LocalDate.of(2027, 1, 3), week.endInclusive)
        assertEquals(7, week.days.size)
    }

    @Test
    fun selectingDayAndChangingFilterKeepsOtherQueryPreferences() {
        val selected = MeasurementCalendarSelection(date).filterBy(glucose)
            .selectDay(date.minusDays(1))
        val record = reading("previous-day", glucose, date.minusDays(1).atTime(8, 0))
        assertEquals(glucose, selected.filter)
        assertEquals(listOf(record), project(listOf(record), selection = selected).selectedDay.events.map { it.record })
        val all = selected.filterBy(null)
        assertEquals(selected.date, all.date)
        assertEquals(date.minusDays(1), project(selection = all).selectedDay.date)
        assertTrue(project(selection = all).selectedDay.events.isEmpty())
    }

    @Test
    fun filtersUpdateAllSevenIndicatorsAndExactSelectedDayCount() {
        val records = MeasurementKind.entries.flatMap { kind ->
            listOf(
                reading("today-$kind", kind, date.atTime(8, 0)),
                reading("yesterday-$kind", kind, date.minusDays(1).atTime(9, 0)),
            )
        }
        val all = project(records)
        assertEquals(3, all.selectedDay.events.size)
        assertEquals(6, all.days.sumOf { it.recordedCount })
        MeasurementKind.entries.forEach { kind ->
            val filtered = project(records, selection = MeasurementCalendarSelection(date, kind))
            assertEquals(date, filtered.selection.date)
            assertEquals(1, filtered.selectedDay.events.size)
            assertEquals(2, filtered.days.sumOf { it.recordedCount })
            assertTrue(filtered.days.flatMap { it.events }.all { it.kind == kind })
        }
    }

    @Test
    fun multipleTypesInterleaveByActualTimeDescendingAndPressureIsOneEvent() {
        val records = listOf(
            reading("early-glucose", glucose, date.atTime(7, 0)).copy(capturedAt = now.plusWeeks(1)),
            reading("pressure", MeasurementKind.BLOOD_PRESSURE, date.atTime(12, 0)),
            reading("late-glucose", glucose, date.atTime(17, 0)),
            reading("weight", MeasurementKind.WEIGHT, date.atTime(9, 0)),
        )
        val day = project(records).selectedDay
        assertEquals(listOf("late-glucose", "pressure", "weight", "early-glucose"), day.events.map { it.record?.id })
        assertEquals(4, day.recordedCount)
        assertEquals(1, day.events.count { it.kind == MeasurementKind.BLOOD_PRESSURE })
        assertEquals(listOf("early-glucose", "pressure", "late-glucose", "weight"), records.map { it.id })
    }

    @Test
    fun coincidentReadingsArePreservedAndOrderingIsDeterministic() {
        val records = listOf(
            reading("a", glucose, date.atTime(8, 0)),
            reading("b", glucose, date.atTime(8, 0)),
            reading("hi", glucose, date.atTime(8, 0)).copy(value = MeasurementValue.Glucose("HI")),
            reading("lo", glucose, date.atTime(8, 0)).copy(value = MeasurementValue.Glucose("LO")),
        )
        val first = project(records).selectedDay
        val reversed = project(records.reversed()).selectedDay
        assertEquals(4, first.events.size)
        assertEquals(first, reversed)
        assertEquals(records.map { it.id }.toSet(), first.events.map { it.record?.id }.toSet())
    }

    @Test
    fun allTypesExposeRecordedExplicitNotPerformedAndUnconfirmed() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = followUp(kind)
            val occurrences = followUp.occurrencesOn(date)
            val record = reading("registered-$kind", kind, occurrences[0].scheduledAt.plusMinutes(5), occurrences[0])
            val week = project(
                listOf(record),
                listOf(MeasurementScheduleHistory(kind, followUp, listOf(decision(occurrences[1])))),
            )
            val day = week.selectedDay
            assertEquals(
                listOf(GlucoseOccurrenceState.UNCONFIRMED, GlucoseOccurrenceState.NOT_PERFORMED, GlucoseOccurrenceState.RECORDED),
                day.events.map { it.state },
            )
            assertEquals(1, day.recordedCount)
            assertEquals(1, day.unconfirmedCount)
            assertEquals(1, day.notPerformedCount)
            assertEquals(record, day.events.last().record)
            assertEquals(occurrences[1], day.events[1].occurrence)
        }
    }

    @Test
    fun noReadingDoesNotBecomeNotPerformedOrWeeklyNoncompliance() {
        val followUp = followUp(glucose)
        val week = project(schedules = listOf(MeasurementScheduleHistory(glucose, followUp)))
        assertTrue(week.selectedDay.events.all { it.state == GlucoseOccurrenceState.UNCONFIRMED })
        assertEquals(0, week.selectedDay.notPerformedCount)
        assertEquals(0, week.selectedDay.recordedCount)
        val main = projectMeasurementOverview(emptyList(), date)
        assertEquals(MeasurementDayState.WITHOUT_RECORDS, main.week.first { it.date == date }.state)
    }

    @Test
    fun futureSchedulesAndDaysAreNotUnconfirmedEvents() {
        val followUp = followUp(glucose)
        val week = project(schedules = listOf(MeasurementScheduleHistory(glucose, followUp)), moment = date.atTime(10, 0))
        assertEquals(1, week.selectedDay.events.size)
        assertTrue(week.days.filter { it.date > date }.all { it.events.isEmpty() })
        val next = project(
            schedules = listOf(MeasurementScheduleHistory(glucose, followUp)),
            selection = MeasurementCalendarSelection(date).moveWeek(1),
        )
        assertTrue(next.days.all { it.events.isEmpty() })
    }

    @Test
    fun overnightWindowStaysUnconfirmedOnlyAfterItsEndAndBelongsToStartDay() {
        val slot = GlucoseFollowUpSlot(
            id = "overnight",
            timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
            time = LocalTime.of(22, 0),
            windowEnd = LocalTime.of(2, 0),
            endsNextDay = true,
            suggestedContext = GlucoseContext.BEFORE_SLEEP,
            reminderEnabled = false,
        )
        val followUp = followUp(glucose, slots = listOf(slot))
        val schedule = listOf(MeasurementScheduleHistory(glucose, followUp))
        assertTrue(project(schedules = schedule, moment = date.plusDays(1).atTime(1, 59)).selectedDay.events.isEmpty())
        val atEnd = project(schedules = schedule, moment = date.plusDays(1).atTime(2, 0))
        assertEquals(1, atEnd.selectedDay.unconfirmedCount)
        assertTrue(atEnd.days.first { it.date == date.plusDays(1) }.events.isEmpty())
    }

    @Test
    fun linkedReadingOnAnotherWeekSuppressesItsOriginalScheduledPlaceholderForEveryType() {
        val sunday = LocalDate.of(2026, 11, 22)
        MeasurementKind.entries.forEach { kind ->
            val followUp = followUp(kind, start = sunday)
            val occurrence = followUp.occurrencesOn(sunday).first()
            val record = reading("cross-week-$kind", kind, sunday.plusDays(1).atTime(0, 15), occurrence)
            val schedule = listOf(MeasurementScheduleHistory(kind, followUp))
            val previous = project(
                listOf(record), schedule, MeasurementCalendarSelection(sunday), sunday.plusDays(1).atTime(1, 0),
            )
            assertEquals(2, previous.selectedDay.events.size)
            assertTrue(previous.selectedDay.events.none { it.occurrence?.id == occurrence.id })
            val next = project(
                listOf(record), schedule, MeasurementCalendarSelection(sunday.plusDays(1)), sunday.plusDays(1).atTime(1, 0),
            )
            assertEquals(listOf(record), next.selectedDay.events.map { it.record })
        }
    }

    @Test
    fun correctedNotPerformedDeclarationAndLinkedReadingProduceOneRegisteredEvent() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = followUp(kind)
            val occurrence = followUp.occurrencesOn(date).first()
            val record = reading("corrected-$kind", kind, occurrence.scheduledAt, occurrence)
            val result = project(
                listOf(record),
                listOf(MeasurementScheduleHistory(kind, followUp, listOf(decision(occurrence)))),
            ).selectedDay
            assertEquals(3, result.events.size)
            assertEquals(0, result.notPerformedCount)
            assertEquals(1, result.recordedCount)
            assertEquals(1, result.events.count { it.record?.id == record.id })
        }
    }

    @Test
    fun equalTimesNeverAssociateAnIndependentReadingByGuessing() {
        val followUp = followUp(glucose)
        val occurrence = followUp.occurrencesOn(date).first()
        val record = reading("independent", glucose, occurrence.scheduledAt)
        val day = project(listOf(record), listOf(MeasurementScheduleHistory(glucose, followUp))).selectedDay
        assertEquals(4, day.events.size)
        assertEquals(3, day.unconfirmedCount)
        assertEquals(1, day.recordedCount)
    }

    @Test
    fun deactivationKeepsEarlierReadingsAndStatesForAllTypes() {
        MeasurementKind.entries.forEach { kind ->
            val historicalDate = date.minusYears(1)
            val active = followUp(kind, start = historicalDate)
            val occurrences = active.occurrencesOn(historicalDate)
            val record = reading("old-$kind", kind, occurrences[0].scheduledAt, occurrences[0])
            val removed = active.copy(
                revisions = active.revisions + active.currentRevision.copy(
                    id = "removed-$kind",
                    revisionNumber = 2,
                    effectiveFrom = date,
                    changeKind = GlucoseFollowUpChangeKind.REMOVED,
                    capturedAt = now,
                ),
            )
            val decisions = listOf(decision(occurrences[1]))
            val selection = MeasurementCalendarSelection(historicalDate)
            val before = project(listOf(record), listOf(MeasurementScheduleHistory(kind, active, decisions)), selection)
            val after = project(listOf(record), listOf(MeasurementScheduleHistory(kind, removed, decisions)), selection)
            assertEquals(before, after)
            assertEquals(3, after.selectedDay.events.size)
            val removalDay = project(listOf(record), listOf(MeasurementScheduleHistory(kind, removed, decisions))).selectedDay
            assertEquals(3, removalDay.unconfirmedCount)
            assertEquals(0, removalDay.recordedCount)
            assertEquals(2, removed.revisions.size)
        }
    }

    @Test
    fun explicitDeclarationSurvivesReplacementOfTheSameDaysSchedule() {
        val active = followUp(glucose)
        val occurrence = active.occurrencesOn(date).first()
        val removed = active.copy(
            revisions = active.revisions + active.currentRevision.copy(
                id = "removed-same-day", revisionNumber = 2, effectiveFrom = date,
                changeKind = GlucoseFollowUpChangeKind.REMOVED, capturedAt = now,
            ),
        )
        val day = project(
            schedules = listOf(MeasurementScheduleHistory(glucose, removed, listOf(decision(occurrence)))),
        ).selectedDay
        assertEquals(3, day.events.size)
        assertEquals(1, day.notPerformedCount)
        assertEquals(2, day.unconfirmedCount)
        assertEquals(occurrence, day.events.first { it.state == GlucoseOccurrenceState.NOT_PERFORMED }.occurrence)
    }

    @Test
    fun latestExplicitDecisionIsShownWithoutDuplicateAndKeepsItsIdentity() {
        val followUp = followUp(glucose)
        val occurrence = followUp.occurrencesOn(date).first()
        val original = decision(occurrence)
        val correction = original.copy(id = "corrected-decision", reason = "Motivo ficticio corregido.", capturedAt = now)
        val day = project(schedules = listOf(MeasurementScheduleHistory(glucose, followUp, listOf(correction, original)))).selectedDay
        assertEquals(1, day.notPerformedCount)
        assertEquals(correction, (day.events.last().source as GlucoseHistoryEvent.NotPerformed).decision)
    }

    @Test
    fun rawIdentifiersFromDifferentTypesNeverCollapseEvents() {
        val records = MeasurementKind.entries.map { kind -> reading("shared-raw-id", kind, date.atTime(8, 0)) }
        val day = project(records + records.first()).selectedDay
        assertEquals(3, day.events.size)
        assertEquals(3, day.events.map { it.key }.distinct().size)
    }

    @Test
    fun browsingAndFilteringDoNotAlterIndividualAnalyticalHistories() {
        val records = listOf(
            reading("recent", glucose, date.atTime(9, 0)),
            reading("old", glucose, date.minusMonths(3).atTime(9, 0)),
            reading("pressure", MeasurementKind.BLOOD_PRESSURE, date.atTime(10, 0)),
        )
        val before = projectGlucoseHistory(records)
        project(records, selection = MeasurementCalendarSelection(date, MeasurementKind.BLOOD_PRESSURE))
        project(records, selection = MeasurementCalendarSelection(date.minusMonths(3), glucose))
        assertEquals(before, projectGlucoseHistory(records))
        assertEquals(2, before.sumOf { it.records.size })
    }

    @Test
    fun deactivationRetainsOnlyDeadlinesThatAlreadyElapsedAndCancelsLaterSchedules() {
        val active = followUp(glucose)
        val removed = active.copy(
            revisions = active.revisions + active.currentRevision.copy(
                id = "removed-at-ten", revisionNumber = 2, effectiveFrom = date,
                changeKind = GlucoseFollowUpChangeKind.REMOVED, capturedAt = date.atTime(10, 0),
            ),
        )
        val schedules = listOf(MeasurementScheduleHistory(glucose, removed))
        val today = project(schedules = schedules).selectedDay
        assertEquals(1, today.unconfirmedCount)
        assertEquals(date.atTime(8, 0), today.events.single().at)
        assertTrue(project(schedules = schedules, selection = MeasurementCalendarSelection(date.plusDays(1))).selectedDay.events.isEmpty())
    }

    @Test
    fun historicalActionsUseExactOriginalRevisionWithoutReactivatingAnyType() {
        MeasurementKind.entries.forEach { kind ->
            val active = followUp(kind)
            val occurrence = active.occurrencesOn(date).first()
            val removed = active.copy(
                revisions = active.revisions + active.currentRevision.copy(
                    id = "removed-$kind", revisionNumber = 2, effectiveFrom = date,
                    changeKind = GlucoseFollowUpChangeKind.REMOVED, capturedAt = now,
                ),
            )
            val context = measurementFollowUpForOccurrence(removed, occurrence, kind)
            assertNotNull(context)
            assertEquals(kind, context!!.kind)
            assertEquals(active.id, context.id)
            assertFalse(context.suspended)
            assertFalse(removed.currentRevision.active)
            assertNull(measurementFollowUpForOccurrence(removed, occurrence.copy(id = "unknown"), kind))
            assertNull(measurementFollowUpForOccurrence(removed, occurrence.copy(followUpId = "another"), kind))
            assertNull(measurementFollowUpForOccurrence(removed, occurrence.copy(scheduledAt = occurrence.scheduledAt.plusMinutes(1)), kind))
        }
    }

    private fun project(
        records: List<MeasurementRecord> = emptyList(),
        schedules: List<MeasurementScheduleHistory> = emptyList(),
        selection: MeasurementCalendarSelection = MeasurementCalendarSelection(date),
        moment: LocalDateTime = now,
    ) = projectMeasurementCalendar(records, schedules, selection, moment)

    private fun followUp(
        kind: MeasurementKind,
        start: LocalDate = date,
        slots: List<GlucoseFollowUpSlot> = listOf(8, 12, 16).map { hour ->
            GlucoseFollowUpSlot(
                id = "slot-$hour", time = LocalTime.of(hour, 0),
                suggestedContext = GlucoseContext.FASTING, reminderEnabled = false,
            )
        },
    ): GlucoseFollowUp {
        val id = "follow-up-$kind"
        return GlucoseFollowUp(
            id, "fictitious-person", start.atStartOfDay(), "fictitious-caregiver",
            listOf(
                GlucoseFollowUpRevision(
                    id = "revision-$kind", followUpId = id, revisionNumber = 1,
                    indication = "Indicación ficticia para prueba.", frequency = GlucoseFollowUpFrequency.DAILY,
                    startsOn = start, effectiveFrom = start, slots = slots, remindersEnabled = false,
                    changeKind = GlucoseFollowUpChangeKind.CREATED,
                    capturedAt = start.atStartOfDay(), authorActorId = "fictitious-caregiver",
                ),
            ),
        )
    }

    private fun decision(occurrence: GlucoseScheduledOccurrence) = GlucoseOccurrenceDecision(
        "decision-${occurrence.id}", occurrence.followUpId, occurrence.followUpRevisionId,
        occurrence.id, occurrence.scheduledAt, GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
        "Motivo ficticio.", occurrence.scheduledAt.plusMinutes(30), "fictitious-caregiver",
    )

    private fun reading(
        id: String,
        kind: MeasurementKind,
        at: LocalDateTime,
        occurrence: GlucoseScheduledOccurrence? = null,
    ): MeasurementRecord {
        val value = when (kind) {
            MeasurementKind.CAPILLARY_GLUCOSE -> MeasurementValue.Glucose("119", GlucoseContext.FASTING)
            MeasurementKind.BLOOD_PRESSURE -> MeasurementValue.BloodPressure("120", "80")
            MeasurementKind.WEIGHT -> MeasurementValue.Weight(BigDecimal("74.2"))
        }
        return MeasurementRecord(
            id, value, at, at,
            glucoseMetadata = if (kind != glucose) null else GlucoseRecordMetadata(
                if (occurrence == null) GlucoseEntryKind.INDEPENDENT else GlucoseEntryKind.SCHEDULED,
                occurrence?.followUpId, occurrence?.id, occurrence?.scheduledAt,
            ),
            bloodPressureMetadata = if (kind != MeasurementKind.BLOOD_PRESSURE) null else BloodPressureRecordMetadata(
                if (occurrence == null) BloodPressureEntryKind.INDEPENDENT else BloodPressureEntryKind.SCHEDULED,
                occurrence?.followUpId, occurrence?.id, occurrence?.scheduledAt, actualContext = GlucoseContext.RESTING,
            ),
            weightMetadata = if (kind != MeasurementKind.WEIGHT) null else WeightRecordMetadata(
                if (occurrence == null) WeightEntryKind.INDEPENDENT else WeightEntryKind.SCHEDULED,
                occurrence?.followUpId, occurrence?.id, occurrence?.scheduledAt,
            ),
        )
    }
}
