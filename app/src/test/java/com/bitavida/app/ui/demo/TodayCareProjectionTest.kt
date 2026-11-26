package com.bitavida.app.ui.demo

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.projectObservationOccurrences
import com.bitavida.app.ui.medical.medicationOccurrencesForDay
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TodayCareProjectionTest {
    private val now = LocalDateTime.of(2026, 11, 20, 12, 0)
    private val day = now.toLocalDate()
    private val person = "fictional-person"
    private val emptyOverview = MedicalCareOverview(emptyList(), emptyList())

    @Test fun calendarShowsIndependentMeasurementWithoutChangingScheduledProgress() {
        val record = MeasurementRecord(id = "fictional-independent", value = MeasurementValue.Glucose("102"),
            occurredAt = now, capturedAt = now,
            glucoseMetadata = GlucoseRecordMetadata(entryKind = GlucoseEntryKind.INDEPENDENT))
        val result = project(glucoseRecords = listOf(record, record))
        assertEquals(0, result.progress.total)
        assertEquals(1, result.spontaneousActivities.size)
        val event = careCalendarEvents(result, now).single()
        assertTrue(event.spontaneous)
        assertEquals("102 mg/dL", event.detail)
        assertEquals(now, event.actualAt)
    }

    @Test fun calendarDoesNotLabelFutureMedicationUnconfirmed() {
        val plan = medication("fictional-treatment", listOf(exact("future", 14)))
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan)))
        assertEquals("Programada", careCalendarEvents(result, now).single().status)
        assertEquals("Sin confirmar", careCalendarEvents(result, now.plusHours(3)).single().status)
        assertEquals(0, result.progress.attended)
    }

    @Test fun calendarShowsIntakeOnlyOnceWhenAnOccurrencesContextChanges() {
        val original = medication("legacy-treatment", listOf(exact("same-slot", 21)))
        val oldEvent = medicationOccurrencesForDay(original, day).single()
        val previous = original.currentRevision
        val changed = previous.copy(id = "legacy-treatment-v2", revisionNumber = 2,
            changeKind = MedicationPlanChangeKind.BASE_DETAILS_CORRECTED,
            changeReason = "Corrección ficticia",
            configuration = previous.configuration!!.copy(administrations = listOf(
                exact("same-slot", 21).copy(activityRelation = MedicationActivityRelation.WITH_DINNER))))
        val current = original.copy(revisions = listOf(previous, changed))
        val recordedAt = day.atTime(18, 15)
        val initialRecord = intake(original, "declared", oldEvent.id, oldEvent.startsAt, MedicationIntakeOutcome.COMPLETED)
        val record = initialRecord.copy(revisions = listOf(initialRecord.currentRevision.copy(actualAt = recordedAt)))
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(current), medicationIntakes = listOf(record)))

        val event = careCalendarEvents(result, now).single()
        assertEquals("Se tomó", event.status)
        assertEquals(day.atTime(21, 0), event.scheduledAt)
        assertEquals(recordedAt, event.actualAt)
        assertEquals("declared", (event.reference as TodayResolvedActivity).recordId)
        assertEquals(TodayCareProgress(total = 1, attended = 1, performed = 1), result.progress)
    }

    @Test fun distinctHistoricalSlotAndCurrentSlotAtTheSameTimeRemainSeparate() {
        val original = medication("different-schedules", listOf(exact("old-slot", 21)))
        val oldEvent = medicationOccurrencesForDay(original, day).single()
        val previous = original.currentRevision
        val changed = previous.copy(id = "different-schedules-v2", revisionNumber = 2,
            changeKind = MedicationPlanChangeKind.SCHEDULE_CORRECTED, changeReason = "Corrección ficticia",
            configuration = previous.configuration!!.copy(administrations = listOf(exact("new-slot", 21))))
        val current = original.copy(revisions = listOf(previous, changed))
        val record = intake(original, "old-record", oldEvent.id, oldEvent.startsAt, MedicationIntakeOutcome.COMPLETED)
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(current), medicationIntakes = listOf(record)))
        val calendar = careCalendarEvents(result, now)
        assertEquals(2, calendar.size)
        assertEquals(setOf("Programada", "Se tomó"), calendar.map { it.status }.toSet())
        assertEquals(1, result.medications.size)
        assertEquals("old-record", result.resolvedActivities.single().recordId)
    }

    @Test fun calendarKeepsIndependentMeasurementsEvenAtTheScheduledTime() {
        val schedule = followUp("glucose", hours = listOf(8))
        val independent = MeasurementRecord("independent", MeasurementValue.Glucose("90"), day.atTime(8, 0), now,
            glucoseMetadata = GlucoseRecordMetadata(entryKind = GlucoseEntryKind.INDEPENDENT))
        val result = project(glucoseFollowUp = schedule, glucoseRecords = listOf(independent))
        val calendar = careCalendarEvents(result, now)
        assertEquals(2, calendar.size)
        assertEquals(1, calendar.count { it.spontaneous })
        assertEquals(1, result.measurements.size)
    }

    @Test fun unknownMedicationReferenceRemainsVisibleOnTheRealDateWithoutClaimingAnAssociation() {
        val plan = medication("unknown-reference", listOf(exact("slot", 21)))
        val initial = intake(plan, "unlinked-record", "missing-occurrence", day.minusDays(1).atTime(21, 0), MedicationIntakeOutcome.COMPLETED)
        val record = initial.copy(revisions = listOf(initial.currentRevision.copy(actualAt = day.atTime(6, 15))))
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan), medicationIntakes = listOf(record)))
        val unlinked = result.unlinkedActivities.single()
        assertEquals("unlinked-record", unlinked.recordId)
        assertEquals(day.atTime(6, 15), unlinked.actualAt)
        assertEquals(day.atTime(6, 15), unlinked.scheduledAt)
        assertTrue(unlinked.unlinked)
        assertFalse(unlinked.spontaneous)
        assertEquals(TodayCareProgress(total = 1), result.progress)
    }

    @Test fun ambiguousLegacyMedicationReferenceIsNotMatchedByClockTime() {
        val plan = medication("ambiguous", listOf(exact("first", 8), exact("second", 8)))
        val record = intake(plan, "legacy", null, day.atTime(8, 0), MedicationIntakeOutcome.COMPLETED)
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan), medicationIntakes = listOf(record)))
        assertEquals(2, result.medications.size)
        assertEquals("legacy", result.unlinkedActivities.single().recordId)
        assertTrue(result.resolvedActivities.isEmpty())
    }

    @Test fun multipleDeclarationsAreVisibleWithoutChoosingAnOutcomeOrCountingTwoOccasions() {
        val plan = medication("separate-facts", listOf(exact("slot", 8)))
        val occurrence = medicationOccurrencesForDay(plan, day).single()
        val complete = intake(plan, "first-fact", occurrence.id, occurrence.startsAt, MedicationIntakeOutcome.COMPLETED)
        val partial = intake(plan, "second-fact", occurrence.id, occurrence.startsAt, MedicationIntakeOutcome.PARTIAL)
        val result = project(overview = emptyOverview.copy(
            medicationPlans = listOf(plan), medicationIntakes = listOf(complete, partial)))
        assertEquals(TodayCareProgress(total = 1, attended = 1), result.progress)
        assertEquals(TodayActivityOutcome.AMBIGUOUS, result.resolvedActivities.single().outcome)
        assertEquals(setOf("first-fact", "second-fact"), result.additionalActivities.mapNotNull { it.recordId }.toSet())
        assertEquals(0, result.medications.size)
        val calendar = careCalendarEvents(result, now)
        assertEquals(2, calendar.size)
        assertEquals(setOf("first-fact", "second-fact"),
            calendar.mapNotNull { (it.reference as? TodayResolvedActivity)?.recordId }.toSet())
    }

    @Test fun answeredOccasionIsNotPendingWhenAnotherDeclarationIsUnconfirmed() {
        val plan = medication("answer-and-unknown", listOf(exact("slot", 8)))
        val occurrence = medicationOccurrencesForDay(plan, day).single()
        val complete = intake(plan, "answer", occurrence.id, occurrence.startsAt, MedicationIntakeOutcome.COMPLETED)
        val unknown = intake(plan, "unknown", occurrence.id, occurrence.startsAt, MedicationIntakeOutcome.UNCONFIRMED)
        val result = project(overview = emptyOverview.copy(
            medicationPlans = listOf(plan), medicationIntakes = listOf(complete, unknown)))
        assertEquals(TodayCareProgress(total = 1, attended = 1, performed = 1), result.progress)
        assertTrue(result.medications.isEmpty())
        assertEquals("answer", result.resolvedActivities.single().recordId)
        assertEquals("unknown", result.additionalActivities.single().recordId)
        assertEquals(TodayActivityOutcome.UNCONFIRMED, result.additionalActivities.single().outcome)
    }

    @Test fun aSingleClockTimeDoesNotIdentifyAnUnlinkedHistoricalDeclaration() {
        val plan = medication("unknown-slot", listOf(exact("slot", 8)))
        val record = intake(plan, "unlinked", null, day.atTime(8, 0), MedicationIntakeOutcome.COMPLETED)
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan), medicationIntakes = listOf(record)))
        assertEquals(1, result.medications.size)
        assertEquals("unlinked", result.unlinkedActivities.single().recordId)
        assertTrue(result.resolvedActivities.isEmpty())
    }

    @Test fun calendarKeepsDifferentPlansAtTheSameTimeSeparate() {
        val firstPlan = medication("first-plan", listOf(exact("first-dose", 8)))
        val secondPlan = medication("second-plan", listOf(exact("second-dose", 8)))
        val firstEvent = medicationOccurrencesForDay(firstPlan, day).single()
        val record = intake(firstPlan, "only-first", firstEvent.id, firstEvent.startsAt, MedicationIntakeOutcome.COMPLETED)
        val result = project(overview = emptyOverview.copy(
            medicationPlans = listOf(firstPlan, secondPlan), medicationIntakes = listOf(record)))
        val calendar = careCalendarEvents(result, now)
        assertEquals(2, calendar.size)
        assertEquals("second-plan", result.medications.single().occurrence.medicationPlanId)
        assertEquals("only-first", result.resolvedActivities.single().recordId)
    }

    @Test fun calendarKeepsDifferentSameTimeMedicationOccurrencesAndTheirOwnRecords() {
        val plan = medication("distinct-treatment", listOf(exact("first", 8), exact("second", 8)))
        val occurrences = medicationOccurrencesForDay(plan, day)
        val records = occurrences.mapIndexed { index, event ->
            intake(plan, "record-$index", event.id, event.startsAt, MedicationIntakeOutcome.COMPLETED)
        }
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan), medicationIntakes = records))
        val events = careCalendarEvents(result, now)
        assertEquals(2, events.size)
        assertEquals(records.map { it.id }.toSet(), events.map { (it.reference as TodayResolvedActivity).recordId }.toSet())
        assertTrue(events.all { it.status == "Se tomó" })
    }

    @Test fun calendarDoesNotDuplicateScheduledMeasurement() {
        val followUp = followUp("glucose", hours = listOf(8))
        val scheduled = followUp.occurrencesOn(day).single()
        val record = reading(MeasurementKind.CAPILLARY_GLUCOSE, scheduled)
        val result = project(glucoseFollowUp = followUp, glucoseRecords = listOf(record))
        assertTrue(result.spontaneousActivities.isEmpty())
        assertEquals(1, careCalendarEvents(result, now).size)
        assertEquals(1, result.progress.attended)
    }

    @Test fun notPerformedMeasurementCarriesItsExactOccurrenceForDetail() {
        val followUp = followUp("decision-follow", hours = listOf(8))
        val occurrence = followUp.occurrencesOn(day).single()
        val result = project(glucoseFollowUp = followUp, glucoseDecisions = listOf(decision(occurrence)))
        val event = result.resolvedActivities.single()
        assertEquals(TodayActivityOutcome.NOT_PERFORMED, event.outcome)
        assertEquals(occurrence.id, event.measurementOccurrence?.id)
        assertEquals(occurrence.followUpId, event.measurementOccurrence?.followUpId)
        assertNull(event.recordId)
    }

    @Test fun unknownScheduledMeasurementReferencesRemainVisibleWithoutAClaimedLink() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = followUp("unknown-$kind", hours = listOf(8))
            val occurrence = followUp.occurrencesOn(day).single()
            val original = reading(kind, occurrence)
            val unknown = when (kind) {
                MeasurementKind.CAPILLARY_GLUCOSE -> original.copy(glucoseMetadata = original.glucoseMetadata!!.copy(scheduledOccurrenceId = "unknown"))
                MeasurementKind.BLOOD_PRESSURE -> original.copy(bloodPressureMetadata = original.bloodPressureMetadata!!.copy(scheduledOccurrenceId = "unknown"))
                MeasurementKind.WEIGHT -> original.copy(weightMetadata = original.weightMetadata!!.copy(scheduledOccurrenceId = "unknown"))
            }
            val result = when (kind) {
                MeasurementKind.CAPILLARY_GLUCOSE -> project(glucoseFollowUp = followUp, glucoseRecords = listOf(unknown))
                MeasurementKind.BLOOD_PRESSURE -> project(bloodPressureFollowUp = followUp, bloodPressureRecords = listOf(unknown))
                MeasurementKind.WEIGHT -> project(weightFollowUp = followUp, weightRecords = listOf(unknown))
            }
            val unlinked = result.unlinkedActivities.single()
            assertEquals(unknown.id, unlinked.recordId)
            assertEquals(unknown.occurredAt, unlinked.actualAt)
            assertEquals(kind, unlinked.measurementKind)
            assertTrue(unlinked.unlinked)
            assertFalse(unlinked.spontaneous)
            assertTrue(result.spontaneousActivities.isEmpty())
            assertEquals(1, result.measurements.size)
        }
    }

    @Test fun inconsistentMeasurementScheduledTimeDoesNotResolveAnotherSlot() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = followUp("mismatch-$kind", hours = listOf(8))
            val occurrence = followUp.occurrencesOn(day).single()
            val original = reading(kind, occurrence)
            val inconsistent = when (kind) {
                MeasurementKind.CAPILLARY_GLUCOSE -> original.copy(glucoseMetadata = original.glucoseMetadata!!.copy(scheduledAt = occurrence.scheduledAt.plusHours(1)))
                MeasurementKind.BLOOD_PRESSURE -> original.copy(bloodPressureMetadata = original.bloodPressureMetadata!!.copy(scheduledAt = occurrence.scheduledAt.plusHours(1)))
                MeasurementKind.WEIGHT -> original.copy(weightMetadata = original.weightMetadata!!.copy(scheduledAt = occurrence.scheduledAt.plusHours(1)))
            }
            val result = when (kind) {
                MeasurementKind.CAPILLARY_GLUCOSE -> project(glucoseFollowUp = followUp, glucoseRecords = listOf(inconsistent))
                MeasurementKind.BLOOD_PRESSURE -> project(bloodPressureFollowUp = followUp, bloodPressureRecords = listOf(inconsistent))
                MeasurementKind.WEIGHT -> project(weightFollowUp = followUp, weightRecords = listOf(inconsistent))
            }
            assertEquals(1, result.measurements.size)
            assertTrue(result.resolvedActivities.isEmpty())
            assertEquals(inconsistent.id, result.unlinkedActivities.single().recordId)
        }
    }

    @Test fun legacyMeasurementWithoutScheduledTimeUsesExactHistoricalOccurrenceId() {
        val followUp = followUp("legacy-follow", hours = listOf(8))
        val yesterday = followUp.occurrencesOn(day.minusDays(1)).single()
        val original = reading(MeasurementKind.CAPILLARY_GLUCOSE, yesterday)
        val record = original.copy(occurredAt = now, glucoseMetadata = original.glucoseMetadata!!.copy(scheduledAt = null))
        val result = project(glucoseFollowUp = followUp, glucoseRecords = listOf(record))
        assertTrue(result.unlinkedActivities.isEmpty())
        assertTrue(result.spontaneousActivities.isEmpty())
        val historic = projectTodayCare(overview = emptyOverview, glucoseFollowUp = followUp,
            glucoseRecords = listOf(record), personUnderCareId = person, now = day.minusDays(1).atTime(LocalTime.MAX))
        assertEquals(record.id, historic.resolvedActivities.single().recordId)
    }

    @Test fun linkedMeasurementOnAnotherDateDoesNotAppearAsUnlinkedOrSpontaneous() {
        val followUp = followUp("earlier-follow", hours = listOf(8))
        val yesterday = followUp.occurrencesOn(day.minusDays(1)).single()
        val record = reading(MeasurementKind.CAPILLARY_GLUCOSE, yesterday).copy(occurredAt = now)
        val result = project(glucoseFollowUp = followUp, glucoseRecords = listOf(record))
        assertTrue(result.unlinkedActivities.isEmpty())
        assertTrue(result.spontaneousActivities.isEmpty())
        assertEquals(1, result.measurements.size)
    }

    @Test fun unknownObservationReferenceRemainsVisibleWithoutGuessingSchedule() {
        val followUp = observationSchedule()
        val scheduled = projectObservationOccurrences(day, listOf(followUp), emptyList(), now).single()
        val record = observationRecord("unknown-review", scheduled).copy(occurrenceId = "unknown")
        val result = project(observations = ObservationSnapshot(followUps = listOf(followUp), records = listOf(record)))
        assertEquals("unknown-review", result.unlinkedActivities.single().recordId)
        assertTrue(result.unlinkedActivities.single().unlinked)
        assertTrue(result.spontaneousActivities.isEmpty())
        assertEquals(1, result.observations.size)
    }

    @Test fun linkedObservationOnAnotherDateDoesNotAppearAsUnlinkedOrSpontaneous() {
        val followUp = observationSchedule()
        val yesterday = projectObservationOccurrences(day.minusDays(1), listOf(followUp), emptyList(), now).single()
        val record = observationRecord("known-review", yesterday).copy(occurredOn = day)
        val result = project(observations = ObservationSnapshot(followUps = listOf(followUp), records = listOf(record)))
        assertTrue(result.unlinkedActivities.isEmpty())
        assertTrue(result.spontaneousActivities.isEmpty())
        assertEquals(1, result.observations.size)
    }

    @Test fun noScheduleIsDifferentFromAnExplicitlyResolvedDay() {
        val empty = project()
        assertFalse(empty.hasScheduledActivities)
        assertEquals(0, empty.pendingCount)
        val reminder = reminder("closed", 10).copy(status = CareReminderStatus.DONE, closedAt = now)
        val resolved = project(reminders = listOf(reminder))
        assertTrue(resolved.hasScheduledActivities)
        assertEquals(0, resolved.pendingCount)
    }

    @Test fun progressCountsExplicitOutcomesSeparatelyFromPendingAndUnconfirmed() {
        val plan = medication("treatment", listOf(
            exact("done", 8), exact("partial", 9), exact("omitted", 10),
            exact("unconfirmed", 11), exact("pending", 12),
        ))
        val events = medicationOccurrencesForDay(plan, day)
        val outcomes = listOf(MedicationIntakeOutcome.COMPLETED, MedicationIntakeOutcome.PARTIAL,
            MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.UNCONFIRMED)
        val records = outcomes.mapIndexed { index, outcome ->
            intake(plan, "record-$index", events[index].id, events[index].startsAt, outcome)
        }
        val result = project(overview = emptyOverview.copy(
            medicationPlans = listOf(plan, plan), medicationIntakes = records + records,
        ))
        assertEquals(TodayCareProgress(total = 5, attended = 3, performed = 1,
            notPerformed = 1, partiallyPerformed = 1), result.progress)
        assertEquals(listOf(11, 12), result.medications.map { it.occurrence.startsAt.hour })
        assertEquals(setOf(TodayActivityOutcome.PERFORMED, TodayActivityOutcome.PARTIALLY_PERFORMED,
            TodayActivityOutcome.NOT_PERFORMED), result.resolvedActivities.map { it.outcome }.toSet())
    }

    @Test fun progressCombinesOnlyRealScheduledEventsAcrossModules() {
        val glucose = followUp("glucose", listOf(8, 10, 14))
        val events = glucose.occurrencesOn(day)
        val observation = observationSchedule()
        val review = projectObservationOccurrences(day, listOf(observation), emptyList(), now).single()
        val done = reminder("done", 9).copy(status = CareReminderStatus.DONE, closedAt = now)
        val discarded = reminder("discarded", 11).copy(status = CareReminderStatus.DISCARDED, closedAt = now)
        val result = project(
            glucoseFollowUp = glucose,
            glucoseRecords = listOf(reading(MeasurementKind.CAPILLARY_GLUCOSE, events[0])),
            glucoseDecisions = listOf(decision(events[1])),
            observations = ObservationSnapshot(followUps = listOf(observation),
                records = listOf(observationRecord("review", review, ObservationResult.NOT_PERFORMED))),
            reminders = listOf(done, done, discarded, reminder("pending", 16)),
        )
        assertEquals(TodayCareProgress(total = 7, attended = 5, performed = 2,
            notPerformed = 2, discarded = 1), result.progress)
        assertEquals(2, result.pendingCount)
        assertEquals(5, result.resolvedActivities.size)
    }

    @Test fun categoriesRemainSeparateAndEveryPendingIsRetainedBeyondTheUiLimit() {
        val meds = medication("fictional-treatment", listOf(8, 10, 14, 20).mapIndexed { i, h -> exact("dose-$i", h) })
        val glucose = followUp("glucose", hours = listOf(8, 10, 14, 20))
        val obs = observationSchedule()
        val result = project(
            overview = emptyOverview.copy(medicationPlans = listOf(meds)),
            glucoseFollowUp = glucose,
            observations = ObservationSnapshot(followUps = listOf(obs)),
            reminders = listOf(reminder("r3", 20), reminder("r1", 10), reminder("r2", 16), reminder("r4", 21)),
        )
        assertEquals(4, result.medications.size)
        assertEquals(4, result.measurements.size)
        assertEquals(1, result.observations.size)
        assertEquals(4, result.reminders.size)
        assertEquals(13, result.pendingCount)
        assertEquals(listOf(8, 10, 14, 20), result.medications.map { it.occurrence.startsAt.hour })
        assertEquals(listOf("r1", "r2", "r3", "r4"), result.reminders.map { it.id })
    }

    @Test fun medicationCompletionAndExplicitNonAdministrationBothRemoveOnlyTheirPending() {
        val plan = medication("treatment", listOf(exact("first", 8), exact("second", 10), exact("third", 14)))
        val occurrences = medicationOccurrencesForDay(plan, day)
        val records = listOf(
            intake(plan, "one", occurrences[0].id, occurrences[0].startsAt, MedicationIntakeOutcome.COMPLETED),
            intake(plan, "two", occurrences[1].id, occurrences[1].startsAt, MedicationIntakeOutcome.OMITTED),
        )
        val overview = emptyOverview.copy(medicationPlans = listOf(plan), medicationIntakes = records)
        val result = project(overview = overview)
        assertEquals(listOf(14), result.medications.map { it.occurrence.startsAt.hour })
        assertEquals(MedicationIntakeOutcome.OMITTED, overview.medicationIntakes.last().currentRevision.outcome)
        assertTrue(result.hasScheduledActivities)
        val resolved = project(overview = overview.copy(medicationIntakes = records +
            intake(plan, "three", occurrences[2].id, occurrences[2].startsAt, MedicationIntakeOutcome.REFUSED)))
        assertEquals(0, resolved.pendingCount)
        assertTrue(resolved.hasScheduledActivities)
    }

    @Test fun unconfirmedMedicationDeclarationIsStillPendingAndLegacyReferenceResolvesItsExactSlot() {
        val old = medication("treatment", listOf(exact("first", 8), exact("second", 14)))
        val oldOccurrence = medicationOccurrencesForDay(old, day).first()
        val revised = old.copy(revisions = old.revisions + old.currentRevision.copy(
            id = "changed-details", revisionNumber = 2, changeKind = MedicationPlanChangeKind.BASE_DETAILS_CORRECTED,
            changeReason = "Corrección ficticia"))
        val unconfirmed = intake(old, "unconfirmed", oldOccurrence.id, oldOccurrence.startsAt, MedicationIntakeOutcome.UNCONFIRMED)
        assertEquals(2, project(overview = emptyOverview.copy(
            medicationPlans = listOf(revised), medicationIntakes = listOf(unconfirmed))).medications.size)
        val declared = unconfirmed.copy(revisions = listOf(unconfirmed.currentRevision.copy(outcome = MedicationIntakeOutcome.COMPLETED)))
        assertEquals(listOf(14), project(overview = emptyOverview.copy(
            medicationPlans = listOf(revised), medicationIntakes = listOf(declared))).medications.map { it.occurrence.startsAt.hour })
    }

    @Test fun sameClockTimeNeverResolvesTwoDistinctMedicationSlots() {
        val plan = medication("treatment", listOf(exact("first", 8), exact("second", 8)))
        val occurrences = medicationOccurrencesForDay(plan, day)
        val record = intake(plan, "one", occurrences.first().id, occurrences.first().startsAt, MedicationIntakeOutcome.COMPLETED)
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan), medicationIntakes = listOf(record)))
        assertEquals(1, result.medications.size)
        assertNotEquals(record.currentRevision.scheduledOccurrenceId, result.medications.single().id)
    }

    @Test fun medicationWindowAcrossMidnightKeepsTheModulesCanonicalOccurrenceIdentity() {
        val window = MedicationAdministration("night", MedicationAdministrationTiming.TIME_WINDOW,
            windowStart = LocalTime.of(23, 0), windowEnd = LocalTime.of(1, 0), windowEndsNextDay = true)
        val plan = medication("treatment", listOf(window))
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan)))
        assertTrue(result.medications.any {
            it.occurrence.startsAt == day.minusDays(1).atTime(23, 0) && it.occurrence.endsAt == day.atTime(1, 0)
        })
        assertEquals(medicationOccurrencesForDay(plan, day).map { it.id }, result.medications.map { it.id })
    }

    @Test fun calendarUsesScheduledStartDateForAnOvernightMedicationWindow() {
        val window = MedicationAdministration("night", MedicationAdministrationTiming.TIME_WINDOW,
            windowStart = LocalTime.of(23, 0), windowEnd = LocalTime.of(1, 0), windowEndsNextDay = true)
        val plan = medication("night-treatment", listOf(window))
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan)))
        assertTrue(result.medications.any { it.occurrence.startsAt.toLocalDate() == day.minusDays(1) })
        val events = careCalendarEvents(result, now, selectedDate = day)
        assertEquals(1, events.size)
        assertEquals(day.atTime(23, 0), events.single().scheduledAt)
    }

    @Test fun eachMeasurementTypeUsesItsRealReadingOrNotPerformedDecisionWithoutInferringValues() {
        MeasurementKind.entries.forEach { kind ->
            val followUp = followUp("follow-$kind", hours = listOf(8, 10, 14))
            val occurrences = followUp.occurrencesOn(day)
            val records = listOf(reading(kind, occurrences[0]))
            val decisions = listOf(decision(occurrences[1]))
            val result = when (kind) {
                MeasurementKind.CAPILLARY_GLUCOSE -> project(glucoseFollowUp = followUp, glucoseRecords = records, glucoseDecisions = decisions)
                MeasurementKind.BLOOD_PRESSURE -> project(bloodPressureFollowUp = followUp, bloodPressureRecords = records, bloodPressureDecisions = decisions)
                MeasurementKind.WEIGHT -> project(weightFollowUp = followUp, weightRecords = records, weightDecisions = decisions)
            }
            assertEquals(kind, result.measurements.single().kind)
            assertEquals(14, result.measurements.single().occurrence.scheduledAt.hour)
            assertEquals(GlucoseOccurrenceDecisionKind.NOT_PERFORMED, decisions.single().kind)
        }
    }

    @Test fun oldMeasurementRevisionAliasesDoNotRecreateAnAlreadyDeclaredPending() {
        val old = followUp("follow", hours = listOf(8, 14))
        val originalOccurrence = old.occurrencesOn(day).first()
        val revised = old.copy(revisions = old.revisions + old.currentRevision.copy(
            id = "new-revision", revisionNumber = 2, changeKind = GlucoseFollowUpChangeKind.INDICATION_CHANGED,
            capturedAt = day.atTime(6, 0)))
        val result = project(glucoseFollowUp = revised, glucoseDecisions = listOf(decision(originalOccurrence)))
        assertEquals(listOf(14), result.measurements.map { it.occurrence.scheduledAt.hour })
    }

    @Test fun observationReviewsExcludeSpontaneousAndResolvedRecordsIncludingNotPerformed() {
        val schedule = observationSchedule()
        val event = projectObservationOccurrences(day, listOf(schedule), emptyList(), now).single()
        val snapshot = ObservationSnapshot(records = listOf(observationRecord("spontaneous", null)),
            followUps = listOf(schedule))
        assertEquals(1, project(observations = snapshot).observations.size)
        listOf(ObservationResult.NO_CHANGES, ObservationResult.NOT_PERFORMED).forEach { result ->
            val record = observationRecord("scheduled", event, result)
            val resolved = project(observations = snapshot.copy(records = snapshot.records + record))
            assertEquals(0, resolved.observations.size)
            assertTrue(resolved.hasScheduledActivities)
            assertEquals(result, record.result)
        }
    }

    @Test fun observationRecordWithMismatchedScheduledTimeDoesNotResolveOccurrence() {
        val schedule = observationSchedule()
        val event = projectObservationOccurrences(day, listOf(schedule), emptyList(), now).single()
        val invalid = observationRecord("wrong-time", event).copy(scheduledAt = event.scheduledAt.plusHours(1))
        val result = project(observations = ObservationSnapshot(
            followUps = listOf(schedule), records = listOf(invalid),
        ))
        assertEquals(listOf(event.id), result.observations.map { it.id })
        assertEquals(TodayCareProgress(total = 1), result.progress)
        assertTrue(result.resolvedActivities.isEmpty())
    }

    @Test fun remindersOnlyAppearForTodayAndKeepYesterdayAccessibleInTheirOwnModule() {
        val today = reminder("today", 8)
        val yesterday = reminder("yesterday", 8).let { it.copy(scheduledAt = it.scheduledAt.minusDays(1), originallyScheduledAt = it.scheduledAt.minusDays(1)) }
        val tomorrow = reminder("tomorrow", 8).let { it.copy(scheduledAt = it.scheduledAt.plusDays(1), originallyScheduledAt = it.scheduledAt.plusDays(1)) }
        val closed = reminder("closed", 10).copy(status = CareReminderStatus.DISCARDED, closedAt = now)
        val result = project(reminders = listOf(today, yesterday, tomorrow, closed))
        assertEquals(listOf("today"), result.reminders.map { it.id })
        assertEquals(CareReminderStatus.PENDING, yesterday.status)
        val postponed = project(reminders = listOf(today.copy(scheduledAt = today.scheduledAt.plusDays(1))))
        assertEquals(0, postponed.pendingCount)
        assertTrue(postponed.hasScheduledActivities)
    }

    @Test fun anotherPatientsPlansAndFollowUpsNeverEnterTheCurrentDay() {
        val otherPlan = medication("other", listOf(exact("dose", 8))).copy(personUnderCareId = "other-person")
        val otherFollowUp = followUp("other-follow").copy(personUnderCareId = "other-person")
        val otherObservation = observationSchedule().let { it.copy(schedule = it.schedule.copy(personUnderCareId = "other-person")) }
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(otherPlan)),
            glucoseFollowUp = otherFollowUp, observations = ObservationSnapshot(followUps = listOf(otherObservation)),
            reminders = listOf(reminder("other", 8).copy(personUnderCareId = "other-person")))
        assertEquals(0, result.pendingCount)
        assertFalse(result.hasScheduledActivities)
    }

    @Test fun duplicateInputsKeepStableIdsAndIndependentReadingsDoNotCloseScheduledMeasurements() {
        val plan = medication("treatment", listOf(exact("dose", 8)))
        val independent = MeasurementRecord("independent", MeasurementValue.Glucose("90", GlucoseContext.FASTING), day.atTime(8, 0), now)
        val reminder = reminder("reminder", 9)
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan, plan)),
            glucoseFollowUp = followUp("glucose"), glucoseRecords = listOf(independent),
            reminders = listOf(reminder, reminder))
        assertEquals(1, result.medications.size)
        assertEquals(1, result.measurements.size)
        assertEquals(1, result.reminders.size)
        assertEquals(result, project(overview = emptyOverview.copy(medicationPlans = listOf(plan, plan)),
            glucoseFollowUp = followUp("glucose"), glucoseRecords = listOf(independent),
            reminders = listOf(reminder, reminder)))
    }

    @Test fun inactiveIndicationsHideTheirScheduleWhileFutureReactivationTodayStaysEligible() {
        val base = medication("linked", listOf(exact("past", 8), exact("future", 18)))
            .copy(medicalIndicationId = "indication")
        val epoch = now.minusDays(2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val indication = MedicalIndication("indication", person, InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            epoch, "fictional-caregiver", ClinicalContentMode.REAL_CLINICAL_DATA, listOf(
                MedicalIndicationRevision("indication-v1", "indication", 1, "Indicación ficticia", day,
                    epoch, null, null, null, MedicalIndicationStatus.ACTIVE, MedicalIndicationChangeKind.CREATED,
                    null, "fictional-caregiver")))
        for (status in listOf(MedicalIndicationStatus.SUSPENDED, MedicalIndicationStatus.COMPLETED)) {
            val inactive = indication.copy(revisions = listOf(indication.currentRevision.copy(status = status)))
            val result = project(overview = emptyOverview.copy(indications = listOf(inactive), medicationPlans = listOf(base)))
            assertTrue(result.medications.isEmpty())
        }
        val suspendedAt = day.atTime(6, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reactivatedAt = day.atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val suspended = base.currentRevision.copy(
            id = "suspended", revisionNumber = 2, changeKind = MedicationPlanChangeKind.SUSPENDED,
            changeReason = "Motivo ficticio", capturedAtEpochMillis = suspendedAt,
            lifecycleEffectiveAtEpochMillis = suspendedAt,
            configuration = base.currentRevision.configuration?.copy(status = MedicationTreatmentStatus.SUSPENDED))
        val future = base.currentRevision.copy(
            id = "future-reactivation", revisionNumber = 3, changeKind = MedicationPlanChangeKind.REACTIVATED,
            capturedAtEpochMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            lifecycleEffectiveAtEpochMillis = reactivatedAt)
        val reactivated = base.copy(revisions = listOf(base.currentRevision, suspended, future))
        val result = project(overview = emptyOverview.copy(indications = listOf(indication), medicationPlans = listOf(reactivated)))
        assertEquals(listOf(18), result.medications.map { it.occurrence.startsAt.hour })
    }

    @Test fun measurementAndObservationWindowsKeepTheirModulesLogicalStartDate() {
        val base = followUp("night-measurement")
        val revision = base.currentRevision.copy(slots = listOf(
            base.currentRevision.slots.single().copy(time = LocalTime.of(23, 0),
                timingType = GlucoseFollowUpTimingType.TIME_WINDOW, windowEnd = LocalTime.of(1, 0), endsNextDay = true)))
        val scheduled = base.copy(revisions = listOf(revision))
        val result = project(glucoseFollowUp = scheduled,
            observations = ObservationSnapshot(followUps = listOf(ObservationFollowUp("OBS-001", scheduled))))
        assertEquals(day.atTime(23, 0), result.measurements.single().occurrence.scheduledAt)
        assertEquals(day.plusDays(1).atTime(1, 0), result.measurements.single().occurrence.windowEndAt)
        assertEquals(day.atTime(23, 0), result.observations.single().scheduledAt)
        assertEquals(day.plusDays(1).atTime(1, 0), result.observations.single().windowEndAt)
    }

    @Test fun anObservationSnapshotFromAFutureDateCannotMoveTheOtherModulesOrInventReviews() {
        val schedule = observationSchedule()
        val futureCaptured = schedule.copy(schedule = schedule.schedule.copy(revisions = listOf(
            schedule.schedule.currentRevision.copy(capturedAt = now.plusDays(1)))))
        val tomorrow = reminder("tomorrow", 10).let { it.copy(scheduledAt = it.scheduledAt.plusDays(1), originallyScheduledAt = it.scheduledAt.plusDays(1)) }
        val medication = medication("today", listOf(exact("dose", 8)))
        val result = projectTodayCare(
            overview = emptyOverview.copy(medicationPlans = listOf(medication)),
            observations = ObservationSnapshot(followUps = listOf(futureCaptured)),
            reminders = listOf(reminder("today", 10), tomorrow),
            personUnderCareId = person, now = now, observationNow = now.plusDays(1),
        )
        assertEquals(listOf(day), result.medications.map { it.occurrence.startsAt.toLocalDate() })
        assertEquals(listOf("today"), result.reminders.map { it.id })
        assertTrue(result.observations.isEmpty())
    }

    @Test fun aScheduledResponseKeepsTheNeutralClearDayAfterItsMedicationTemplateMoves() {
        val original = medication("treatment", listOf(exact("dose", 8)))
        val event = medicationOccurrencesForDay(original, day).single()
        val record = intake(original, "declared", event.id, event.startsAt, MedicationIntakeOutcome.OMITTED)
        val changed = original.currentRevision.copy(
            id = "changed-schedule", revisionNumber = 2, changeKind = MedicationPlanChangeKind.SCHEDULE_CORRECTED,
            changeReason = "Cambio ficticio", startsOn = day.plusDays(1),
            configuration = original.currentRevision.configuration?.copy(startsOn = day.plusDays(1)))
        val plan = original.copy(revisions = original.revisions + changed)
        val result = project(overview = emptyOverview.copy(medicationPlans = listOf(plan), medicationIntakes = listOf(record)))
        assertEquals(0, result.pendingCount)
        assertTrue(result.hasScheduledActivities)
        assertEquals(MedicationIntakeOutcome.OMITTED, record.currentRevision.outcome)
        val otherPatient = project(overview = emptyOverview.copy(
            medicationPlans = listOf(plan.copy(personUnderCareId = "other-person")), medicationIntakes = listOf(record)))
        assertFalse(otherPatient.hasScheduledActivities)
    }

    @Test fun aClosedObservationWindowStillEstablishesTheDayAfterLaterCancellation() {
        val exact = observationSchedule()
        val first = exact.schedule.currentRevision.copy(slots = listOf(
            exact.schedule.currentRevision.slots.single().copy(timingType = GlucoseFollowUpTimingType.TIME_WINDOW,
                time = LocalTime.of(10, 0), windowEnd = LocalTime.of(14, 0))))
        val original = exact.copy(schedule = exact.schedule.copy(revisions = listOf(first)))
        val event = projectObservationOccurrences(day, listOf(original), emptyList(), now).single()
        val record = observationRecord("not-performed", event, ObservationResult.NOT_PERFORMED)
        val removed = first.copy(id = "cancelled", revisionNumber = 2, changeKind = GlucoseFollowUpChangeKind.REMOVED,
            changeReason = "Motivo ficticio", capturedAt = day.atTime(11, 0))
        val cancelled = original.copy(schedule = original.schedule.copy(revisions = listOf(first, removed)))
        val result = project(observations = ObservationSnapshot(records = listOf(record), followUps = listOf(cancelled)))
        assertEquals(0, result.pendingCount)
        assertTrue(result.hasScheduledActivities)
        assertEquals(ObservationResult.NOT_PERFORMED, record.result)
        val spontaneous = project(observations = ObservationSnapshot(records = listOf(observationRecord("spontaneous", null))))
        assertFalse(spontaneous.hasScheduledActivities)
    }

    private fun project(
        overview: MedicalCareOverview = emptyOverview,
        glucoseFollowUp: GlucoseFollowUp? = null, glucoseRecords: List<MeasurementRecord> = emptyList(),
        glucoseDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
        bloodPressureFollowUp: GlucoseFollowUp? = null, bloodPressureRecords: List<MeasurementRecord> = emptyList(),
        bloodPressureDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
        weightFollowUp: GlucoseFollowUp? = null, weightRecords: List<MeasurementRecord> = emptyList(),
        weightDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
        observations: ObservationSnapshot = ObservationSnapshot(), reminders: List<CareReminder> = emptyList(),
    ) = projectTodayCare(overview, glucoseFollowUp, glucoseRecords, glucoseDecisions,
        bloodPressureFollowUp, bloodPressureRecords, bloodPressureDecisions, weightFollowUp, weightRecords,
        weightDecisions, observations, reminders, person, now)

    private fun exact(id: String, hour: Int) = MedicationAdministration(id,
        MedicationAdministrationTiming.EXACT_TIME, exactTime = LocalTime.of(hour, 0))

    private fun medication(id: String, slots: List<MedicationAdministration>): MedicationPlan {
        val epoch = now.minusDays(2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = day.minusDays(2), administrations = slots,
        )
        val revision = MedicationPlanRevision("$id-v1", id, 1, "Producto ficticio $id", "1 tableta", "TABLET",
            "Diario", day.minusDays(2), null, null, emptyList(), epoch, "fictional-caregiver",
            MedicationPlanChangeKind.CREATED, null, configuration = configuration)
        return MedicationPlan(id = id, personUnderCareId = person, medicalIndicationId = null,
            medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD, createdAtEpochMillis = epoch,
            createdByActorId = "fictional-caregiver", contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            revisions = listOf(revision))
    }

    private fun intake(plan: MedicationPlan, id: String, occurrenceId: String?, at: LocalDateTime, outcome: MedicationIntakeOutcome): MedicationIntakeRecord {
        val epoch = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return MedicationIntakeRecord(id, plan.id, epoch, "fictional-caregiver", listOf(
            MedicationIntakeRevision("$id-v1", id, 1, outcome, at,
                now.takeIf { outcome == MedicationIntakeOutcome.COMPLETED }, null, null,
                MedicationIntakeChangeKind.REGISTERED, null, epoch, "fictional-caregiver", occurrenceId)))
    }

    private fun followUp(id: String, hours: List<Int> = listOf(8)) = GlucoseFollowUp(id, person, now.minusDays(2), "fictional-caregiver", listOf(
        GlucoseFollowUpRevision("$id-v1", id, 1, "Indicación ficticia", GlucoseFollowUpFrequency.DAILY,
            startsOn = day.minusDays(2), effectiveFrom = day.minusDays(2),
            slots = hours.mapIndexed { index, hour -> GlucoseFollowUpSlot("$id-slot-$index", time = LocalTime.of(hour, 0),
                suggestedContext = GlucoseContext.FASTING, reminderEnabled = false) },
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = now.minusDays(2), authorActorId = "fictional-caregiver")))

    private fun decision(event: GlucoseScheduledOccurrence) = GlucoseOccurrenceDecision(
        "decision-" + event.slotId, event.followUpId, event.followUpRevisionId, event.id, event.scheduledAt,
        GlucoseOccurrenceDecisionKind.NOT_PERFORMED, "Motivo ficticio", now, "fictional-caregiver")

    private fun reading(kind: MeasurementKind, event: GlucoseScheduledOccurrence) = MeasurementRecord(
        "reading-$kind", when (kind) {
            MeasurementKind.CAPILLARY_GLUCOSE -> MeasurementValue.Glucose("90", GlucoseContext.FASTING)
            MeasurementKind.BLOOD_PRESSURE -> MeasurementValue.BloodPressure("120", "80")
            MeasurementKind.WEIGHT -> MeasurementValue.Weight(BigDecimal("60"))
        }, event.scheduledAt, now,
        glucoseMetadata = if (kind == MeasurementKind.CAPILLARY_GLUCOSE) GlucoseRecordMetadata(GlucoseEntryKind.SCHEDULED,
            event.followUpId, event.id, event.scheduledAt, event.suggestedContext) else null,
        bloodPressureMetadata = if (kind == MeasurementKind.BLOOD_PRESSURE) BloodPressureRecordMetadata(BloodPressureEntryKind.SCHEDULED,
            event.followUpId, event.id, event.scheduledAt, event.suggestedContext, event.suggestedContext) else null,
        weightMetadata = if (kind == MeasurementKind.WEIGHT) WeightRecordMetadata(WeightEntryKind.SCHEDULED,
            event.followUpId, event.id, event.scheduledAt) else null,
    )

    private fun observationSchedule() = ObservationFollowUp("OBS-001", followUp("observation", listOf(10)))

    private fun observationRecord(id: String, event: ObservationOccurrence?, result: ObservationResult = ObservationResult.CHANGES) =
        ObservationRecord(id, "OBS-001", person, day, LocalTime.of(10, 0), ObservationTimePrecision.EXACT,
            result, emptyMap(), "Descripción ficticia", followUpId = event?.followUpId, occurrenceId = event?.id,
            scheduledAt = event?.scheduledAt, createdAt = now, actorId = "fictional-caregiver")

    private fun reminder(id: String, hour: Int) = CareReminder(id, person, "Pendiente ficticio $id", null,
        day.atTime(hour, 0), false, CareReminderStatus.PENDING, now.minusDays(1), now.minusDays(1), null, 1)
}
