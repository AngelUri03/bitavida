package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class MeasurementHistoryConsistencyTest {
    private val date = LocalDate.of(2026, 11, 17)
    private val now = date.atTime(18, 0)

    @Test
    fun repeatedDeactivationDoesNotReplayObsoleteTemplatesAndKeepsAuthenticDeclaration() {
        MeasurementKind.entries.forEach { kind ->
            val (followUp, records, decisions) = measurementHistoryIncidentFixture(kind)
            val original = followUp.copy()
            val events = consistent(kind, followUp, records, decisions)
            // Before the fix this fixture gave 5 individual events and 12 general events:
            // 3 readings + 2 latest-template events + 1 authentic older declaration +
            // 6 placeholders replayed from three deactivations. The true result is 6,
            // including the authentic declaration missing from the old individual projection.
            assertEquals(6, events.size)
            assertEquals(3, events.count { it is GlucoseHistoryEvent.Registered })
            assertEquals(1, events.count { it is GlucoseHistoryEvent.NotPerformed })
            assertEquals(listOf(9, 14), events.filterIsInstance<GlucoseHistoryEvent.Unconfirmed>().map { it.sortAt.hour })
            assertEquals(records, events.filterIsInstance<GlucoseHistoryEvent.Registered>().map { it.record })
            assertEquals(original, followUp)
            assertEquals(8, followUp.revisions.size)
            assertEquals(decisions.single(), events.filterIsInstance<GlucoseHistoryEvent.NotPerformed>().single().decision)
        }
    }

    @Test
    fun severalScheduleEditsBeforeTheirTimesCreateOnlyTheEffectiveSchedule() {
        MeasurementKind.entries.forEach { kind ->
            val revisions = listOf(
                historyRevision(1, date.atTime(6, 0), slots = listOf(historySlot("same-slot", 9))),
                historyRevision(2, date.atTime(7, 0), slots = listOf(historySlot("same-slot", 10))),
                historyRevision(3, date.atTime(8, 0), slots = listOf(historySlot("same-slot", 14))),
            )
            val events = consistent(kind, historyFollowUp(revisions))
            assertEquals(listOf(14), events.map { it.sortAt.hour })
            assertTrue(events.single() is GlucoseHistoryEvent.Unconfirmed)
        }
    }

    @Test
    fun anEditAfterAnElapsedOccurrenceDoesNotEraseThatOccurrenceOrInventEarlierOnes() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = historyFollowUp(listOf(
                historyRevision(1, date.atTime(8, 0), slots = listOf(historySlot("slot", 9))),
                historyRevision(2, date.atTime(10, 0), slots = listOf(historySlot("slot", 14))),
                historyRevision(3, date.atTime(15, 0), slots = listOf(historySlot("slot", 20))),
            ))
            assertEquals(listOf(9, 14), consistent(kind, followUp).map { it.sortAt.hour })
            assertEquals(listOf(9, 14, 20), consistent(kind, followUp, moment = date.atTime(21, 0)).map { it.sortAt.hour })
        }
    }

    @Test
    fun indicationContextAndReminderChangesDoNotRecreateAnAlreadyRecordedOccurrence() {
        MeasurementKind.entries.forEach { kind ->
            val slot = historySlot("stable-slot", 14)
            val first = historyRevision(1, date.atTime(6, 0), slots = listOf(slot))
            val second = historyRevision(2, date.atTime(10, 0), GlucoseFollowUpChangeKind.INDICATION_CHANGED,
                listOf(slot.copy(suggestedContext = GlucoseContext.OTHER, reminderEnabled = true, reminderTime = LocalTime.NOON)))
            val followUp = historyFollowUp(listOf(first, second))
            val oldReference = historyFollowUp(listOf(first)).occurrencesOn(date).single()
            val record = historyReading("authentic", kind, date.atTime(14, 5), oldReference)
            val events = consistent(kind, followUp, listOf(record))
            assertEquals(listOf(GlucoseHistoryEvent.Registered(record)), events)
            val today = projectMeasurementOccurrences(date, followUp, listOf(record), emptyList(), kind)
            assertEquals(GlucoseOccurrenceState.RECORDED, today.single().state)
            assertEquals(oldReference.id, today.single().occurrence.id)
        }
    }

    @Test
    fun explicitDeclarationFromOlderRevisionSuppressesEquivalentUnconfirmedEvent() {
        MeasurementKind.entries.forEach { kind ->
            val first = historyRevision(1, date.atTime(6, 0))
            val second = historyRevision(2, date.atTime(8, 0), GlucoseFollowUpChangeKind.INDICATION_CHANGED)
            val old = historyFollowUp(listOf(first)).occurrencesOn(date).first()
            val decision = historyDecision(old)
            val events = consistent(kind, historyFollowUp(listOf(first, second)), decisions = listOf(decision))
            assertEquals(2, events.size)
            assertEquals(1, events.count { it is GlucoseHistoryEvent.NotPerformed })
            assertEquals(1, events.count { it is GlucoseHistoryEvent.Unconfirmed })
        }
    }

    @Test
    fun realReadingTakesPriorityOverAnExplicitDeclarationThroughLegacyRevisionReferences() {
        MeasurementKind.entries.forEach { kind ->
            val first = historyRevision(1, date.atTime(6, 0))
            val second = historyRevision(2, date.atTime(8, 0))
            val old = historyFollowUp(listOf(first)).occurrencesOn(date).first()
            val current = historyFollowUp(listOf(first, second)).occurrencesOn(date).first()
            val record = historyReading("correction", kind, date.atTime(9, 5), current)
            val decisions = listOf(historyDecision(old))
            val events = consistent(kind, historyFollowUp(listOf(first, second)), listOf(record), decisions)
            assertEquals(2, events.size)
            assertEquals(1, events.count { it is GlucoseHistoryEvent.Registered })
            assertEquals(0, events.count { it is GlucoseHistoryEvent.NotPerformed })
            assertEquals(1, decisions.size) // Historical declaration and its original ID are untouched.
        }
    }

    @Test
    fun repeatedExplicitDeclarationsAcrossLegacyAliasesHaveOneCurrentStateWithoutDeletingTheirAudit() {
        MeasurementKind.entries.forEach { kind ->
            val first = historyRevision(1, date.atTime(6, 0))
            val second = historyRevision(2, date.atTime(8, 0))
            val old = historyFollowUp(listOf(first)).occurrencesOn(date).first()
            val current = historyFollowUp(listOf(first, second)).occurrencesOn(date).first()
            val decisions = listOf(historyDecision(old), historyDecision(current).copy(capturedAt = now))
            val events = consistent(kind, historyFollowUp(listOf(first, second)), decisions = decisions)
            assertEquals(1, events.count { it is GlucoseHistoryEvent.NotPerformed })
            assertEquals(decisions.last(), events.filterIsInstance<GlucoseHistoryEvent.NotPerformed>().single().decision)
            assertEquals(2, decisions.size)
        }
    }

    @Test
    fun activationAfterTodaysTimesDoesNotCreateRetroactiveUnconfirmedEvents() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = historyFollowUp(listOf(
                historyRevision(1, date.atTime(6, 0)),
                historyRevision(2, date.atTime(8, 0), GlucoseFollowUpChangeKind.REMOVED),
                historyRevision(3, date.atTime(16, 0), GlucoseFollowUpChangeKind.INDICATION_CHANGED),
            ))
            assertTrue(consistent(kind, followUp).isEmpty())
            val tomorrow = date.plusDays(1)
            val events = consistent(kind, followUp, selected = tomorrow, moment = tomorrow.atTime(18, 0))
            assertEquals(listOf(9, 14), events.map { it.sortAt.hour })
        }
    }

    @Test
    fun twoCloseSchedulesAndIndependentCoincidentReadingRemainDistinct() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = historyFollowUp(listOf(historyRevision(1, date.atTime(6, 0), slots = listOf(
                historySlot("first", 14), historySlot("second", 14).copy(time = LocalTime.of(14, 1)),
            ))))
            val independent = historyReading("independent", kind, date.atTime(14, 0))
            val events = consistent(kind, followUp, listOf(independent))
            assertEquals(3, events.size)
            assertEquals(2, events.count { it is GlucoseHistoryEvent.Unconfirmed })
            assertEquals(1, events.count { it is GlucoseHistoryEvent.Registered })
        }
    }

    @Test
    fun everyAuthenticReadingIsPreservedEvenWhenLegacyAssociationsReferToOneLogicalOccurrence() {
        MeasurementKind.entries.forEach { kind ->
            val first = historyRevision(1, date.atTime(6, 0))
            val second = historyRevision(2, date.atTime(8, 0))
            val old = historyFollowUp(listOf(first)).occurrencesOn(date).first()
            val current = historyFollowUp(listOf(first, second)).occurrencesOn(date).first()
            val readings = listOf(
                historyReading("one", kind, date.atTime(9, 5), old),
                historyReading("two", kind, date.atTime(9, 5), current),
                historyReading("three", kind, date.atTime(9, 5), old),
            )
            val events = consistent(kind, historyFollowUp(listOf(first, second)), readings)
            assertEquals(3, events.filterIsInstance<GlucoseHistoryEvent.Registered>().size)
            assertEquals(readings.toSet(), events.filterIsInstance<GlucoseHistoryEvent.Registered>().map { it.record }.toSet())
            assertEquals(1, events.filterIsInstance<GlucoseHistoryEvent.Unconfirmed>().size)
        }
    }

    @Test
    fun scheduledReadingWithDifferentActualDateSuppressesPlaceholderAcrossBothHistories() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = historyFollowUp(listOf(historyRevision(1, date.atTime(6, 0))))
            val occurrence = followUp.occurrencesOn(date).first()
            val record = historyReading("next-week", kind, date.plusWeeks(1).atTime(8, 0), occurrence)
            val events = consistent(kind, followUp, listOf(record))
            assertEquals(listOf(14), events.map { it.sortAt.hour })
            val later = consistent(kind, followUp, listOf(record), selected = date.plusWeeks(1), moment = now.plusWeeks(1))
            assertEquals(1, later.filterIsInstance<GlucoseHistoryEvent.Registered>().size)
        }
    }

    @Test
    fun openWindowCancelledBeforeItsEndDoesNotBecomeHistoricalPending() {
        MeasurementKind.entries.forEach { kind ->
            val window = historySlot("overnight", 22).copy(
                timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                windowEnd = LocalTime.of(2, 0), endsNextDay = true,
            )
            val first = historyRevision(1, date.atTime(6, 0), slots = listOf(window))
            val removed = historyRevision(2, date.plusDays(1).atTime(1, 0), GlucoseFollowUpChangeKind.REMOVED, listOf(window))
            val followUp = historyFollowUp(listOf(first, removed))
            assertTrue(consistent(kind, followUp, moment = now.plusDays(1)).isEmpty())
            val occurrence = historyFollowUp(listOf(first)).occurrencesOn(date).single()
            val record = historyReading("confirmed-window", kind, date.atTime(22, 10), occurrence)
            assertEquals(
                listOf(GlucoseHistoryEvent.Registered(record)),
                consistent(kind, followUp, listOf(record), moment = now.plusDays(1)),
            )
        }
    }

    @Test
    fun futureEffectiveDateNeverChangesEarlierHistoryAndFutureDeadlinesAreExcluded() {
        MeasurementKind.entries.forEach { kind ->
            val first = historyRevision(1, date.atTime(6, 0))
            val tomorrow = date.plusDays(1)
            val future = historyRevision(2, date.atTime(8, 0), slots = listOf(historySlot("future", 16)))
                .copy(startsOn = tomorrow, effectiveFrom = tomorrow)
            val followUp = historyFollowUp(listOf(first, future))
            assertEquals(listOf(9, 14), consistent(kind, followUp).map { it.sortAt.hour })
            assertTrue(consistent(kind, followUp, selected = tomorrow).isEmpty())
            assertEquals(listOf(16), consistent(kind, followUp, selected = tomorrow, moment = tomorrow.atTime(18, 0)).map { it.sortAt.hour })
        }
    }

    @Test
    fun repeatedQueriesAndRecreatedSnapshotsDoNotGenerateNewEventsOrMutateStoredFacts() {
        MeasurementKind.entries.forEach { kind ->
            val (followUp, records, decisions) = measurementHistoryIncidentFixture(kind)
            val expected = consistent(kind, followUp, records, decisions)
            repeat(20) {
                val reloaded = followUp.copy(revisions = followUp.revisions.map { revision ->
                    revision.copy(slots = revision.slots.map { it.copy() })
                })
                assertEquals(expected, consistent(kind, reloaded, records.map { it.copy() }, decisions.map { it.copy() }))
            }
            assertEquals(3, records.size)
            assertEquals(1, decisions.size)
            assertEquals(8, followUp.revisions.size)
        }
    }

    @Test
    fun generalAllIsExactlyTheUnionOfTheThreeIndividualQueries() {
        val fixtures = MeasurementKind.entries.associateWith(::measurementHistoryIncidentFixture)
        val calendar = projectMeasurementCalendar(
            fixtures.values.flatMap { it.second },
            fixtures.map { (kind, value) -> MeasurementScheduleHistory(kind, value.first, value.third) },
            MeasurementCalendarSelection(date), now,
        )
        assertEquals(18, calendar.selectedDay.events.size)
        assertEquals(18, calendar.selectedDay.events.map { it.key }.toSet().size)
        fixtures.forEach { (kind, value) ->
            val individual = consistent(kind, value.first, value.second, value.third)
            assertEquals(individual.groupingBy { it }.eachCount(),
                calendar.selectedDay.events.filter { it.kind == kind }.map { it.source }.groupingBy { it }.eachCount())
        }
    }

    private fun consistent(
        kind: MeasurementKind,
        followUp: GlucoseFollowUp,
        records: List<MeasurementRecord> = emptyList(),
        decisions: List<GlucoseOccurrenceDecision> = emptyList(),
        selected: LocalDate = date,
        moment: LocalDateTime = now,
    ): List<GlucoseHistoryEvent> {
        val individual = when (kind) {
            MeasurementKind.BLOOD_PRESSURE -> projectBloodPressureHistoryPeriod(
                records, selected, GlucoseHistoryPeriodKind.WEEK, moment.toLocalDate(), followUp, decisions, moment,
            ).days.first { it.date == selected }.events
            else -> projectGlucoseHistoryPeriod(
                records, selected, GlucoseHistoryPeriodKind.WEEK, moment.toLocalDate(), followUp, decisions, moment, kind,
            ).days.first { it.date == selected }.events
        }
        val general = projectMeasurementCalendar(records, listOf(MeasurementScheduleHistory(kind, followUp, decisions)),
            MeasurementCalendarSelection(selected, kind), moment).selectedDay.events.map { it.source }
        assertEquals(individual.size, general.size)
        assertEquals(individual.groupingBy { it }.eachCount(), general.groupingBy { it }.eachCount())
        return individual
    }
}

internal fun measurementHistoryIncidentFixture(kind: MeasurementKind):
    Triple<GlucoseFollowUp, List<MeasurementRecord>, List<GlucoseOccurrenceDecision>> {
    val date = LocalDate.of(2026, 11, 17)
    val first = historyRevision(1, date.minusDays(1).atStartOfDay(),
        slots = listOf(historySlot("early", 7), historySlot("morning", 9), historySlot("afternoon", 14)))
    val revisions = listOf(first,
        historyRevision(2, date.atTime(8, 0)),
        historyRevision(3, date.atTime(15, 0), GlucoseFollowUpChangeKind.REMOVED),
        historyRevision(4, date.atTime(15, 10), GlucoseFollowUpChangeKind.INDICATION_CHANGED),
        historyRevision(5, date.atTime(15, 20), GlucoseFollowUpChangeKind.REMOVED),
        historyRevision(6, date.atTime(15, 30), GlucoseFollowUpChangeKind.INDICATION_CHANGED),
        historyRevision(7, date.atTime(15, 40), GlucoseFollowUpChangeKind.REMOVED),
        historyRevision(8, date.atTime(15, 50), GlucoseFollowUpChangeKind.INDICATION_CHANGED))
    val declaration = historyDecision(historyFollowUp(listOf(first)).occurrencesOn(date).first())
    return Triple(historyFollowUp(revisions),
        listOf(8, 10, 17).map { historyReading("fictitious-$kind-$it", kind, date.atTime(it, 30)) },
        listOf(declaration))
}

internal fun historyFollowUp(revisions: List<GlucoseFollowUpRevision>) = GlucoseFollowUp(
    "fictitious-follow-up", "fictitious-person", revisions.first().capturedAt, "fictitious-caregiver", revisions,
)

internal fun historySlot(id: String, hour: Int) = GlucoseFollowUpSlot(
    id = id, time = LocalTime.of(hour, 0), suggestedContext = GlucoseContext.FASTING, reminderEnabled = false,
)

internal fun historyRevision(
    number: Int,
    at: LocalDateTime,
    change: GlucoseFollowUpChangeKind = if (number == 1) GlucoseFollowUpChangeKind.CREATED else GlucoseFollowUpChangeKind.CORRECTED,
    slots: List<GlucoseFollowUpSlot> = listOf(historySlot("morning", 9), historySlot("afternoon", 14)),
) = GlucoseFollowUpRevision(
    id = "fictitious-revision-$number", followUpId = "fictitious-follow-up", revisionNumber = number,
    indication = "Indicación ficticia $number", frequency = GlucoseFollowUpFrequency.DAILY,
    startsOn = at.toLocalDate(), effectiveFrom = at.toLocalDate(), slots = slots,
    remindersEnabled = false, changeKind = change, capturedAt = at, authorActorId = "fictitious-caregiver",
)

internal fun historyDecision(occurrence: GlucoseScheduledOccurrence) = GlucoseOccurrenceDecision(
    id = "declaration-${occurrence.id}", followUpId = occurrence.followUpId,
    followUpRevisionId = occurrence.followUpRevisionId, occurrenceId = occurrence.id,
    scheduledAt = occurrence.scheduledAt, kind = GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
    reason = "Motivo ficticio", capturedAt = occurrence.scheduledAt.plusMinutes(15), actorId = "fictitious-caregiver",
)

internal fun historyReading(
    id: String, kind: MeasurementKind, at: LocalDateTime, occurrence: GlucoseScheduledOccurrence? = null,
): MeasurementRecord = MeasurementRecord(
    id = id,
    value = when (kind) {
        MeasurementKind.CAPILLARY_GLUCOSE -> MeasurementValue.Glucose("119", GlucoseContext.FASTING)
        MeasurementKind.BLOOD_PRESSURE -> MeasurementValue.BloodPressure("120", "80")
        MeasurementKind.WEIGHT -> MeasurementValue.Weight(BigDecimal("74.2"))
    },
    occurredAt = at, capturedAt = at,
    glucoseMetadata = if (kind != MeasurementKind.CAPILLARY_GLUCOSE) null else GlucoseRecordMetadata(
        if (occurrence == null) GlucoseEntryKind.INDEPENDENT else GlucoseEntryKind.SCHEDULED,
        occurrence?.followUpId, occurrence?.id, occurrence?.scheduledAt),
    bloodPressureMetadata = if (kind != MeasurementKind.BLOOD_PRESSURE) null else BloodPressureRecordMetadata(
        if (occurrence == null) BloodPressureEntryKind.INDEPENDENT else BloodPressureEntryKind.SCHEDULED,
        occurrence?.followUpId, occurrence?.id, occurrence?.scheduledAt, actualContext = GlucoseContext.RESTING),
    weightMetadata = if (kind != MeasurementKind.WEIGHT) null else WeightRecordMetadata(
        if (occurrence == null) WeightEntryKind.INDEPENDENT else WeightEntryKind.SCHEDULED,
        occurrence?.followUpId, occurrence?.id, occurrence?.scheduledAt),
)
