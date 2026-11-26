package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.ObservationRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ObservationUseCasesTest {
    private val date = LocalDate.of(2026, 11, 18)
    private val now = date.atTime(16, 0)
    private val actor = GlucoseActor("profile-fictitious", "caregiver-fictitious", "person-fictitious", true)

    @Test fun catalogHasExactlyApprovedStableIdentitiesAndEightCategories() {
        assertEquals((1..27).map { "OBS-" + it.toString().padStart(3, '0') }, ObservationCatalog.all.map { it.id })
        assertEquals(8, ObservationCategory.entries.size)
        assertEquals(8, ObservationCatalog.all.map { it.category }.distinct().size)
        assertTrue(ObservationCatalog.all.all { it.context.isNotBlank() && it.fields.isNotEmpty() && it.supportsFollowUp })
        assertEquals(27, ObservationCatalog.all.map { it.fields }.distinct().size)
    }

    @Test fun searchNormalizesCaseAccentsPartialsAndEveryRequestedEverydayExpression() {
        val phrases = mapOf(
            "Se le duermen los pies" to "OBS-017", "Va mucho al baño" to "OBS-011",
            "Tiene los pies hinchados" to "OBS-004", "Se cayó" to "OBS-022",
            "Ve borroso" to "OBS-016", "No quiere comer" to "OBS-018",
            "Está muy confundido" to "OBS-020", "Le salió una ampolla" to "OBS-002",
            "NÁUSEAS" to "OBS-014", "entumec" to "OBS-017",
        )
        phrases.forEach { (phrase, expected) ->
            assertTrue(phrase, ObservationCatalog.search(phrase).any { it.id == expected })
        }
        assertEquals(ObservationCatalog.all, ObservationCatalog.search(" "))
        assertTrue(ObservationCatalog.search("xyzsincoincidencia").isEmpty())
        assertEquals("OBS-027", ObservationCatalog.get("OBS-027").id)
    }

    @Test fun everySpecializedFormAcceptsCompleteDeclaredFactsWithoutFollowUp() {
        ObservationCatalog.all.forEach { definition ->
            val answers = definition.fields.associate { field ->
                field.id to when (field.type) {
                    ObservationFieldType.TEXT -> listOf("Detalle ficticio observado")
                    else -> listOf(field.options.first())
                }
            }
            val record = record(definition.id).copy(
                answers = answers,
                result = if (definition.eventOnly) ObservationResult.EVENT else ObservationResult.CHANGES,
                description = "Hecho ficticio para la prueba.",
            )
            assertEquals(definition.id, emptyMap<String, String>(), validateObservation(record, now))
            assertNull(record.followUpId)
        }
    }

    @Test fun missingDescriptionForOtherFutureDatesAndUnknownHoursAreValidated() {
        assertTrue(validateObservation(record("OBS-027").copy(description = ""), now).containsKey("description"))
        assertTrue(validateObservation(record("OBS-027").copy(occurredOn = date.plusDays(1)), now).containsKey("date"))
        assertTrue(validateObservation(record("OBS-027").copy(occurredAt = LocalTime.of(23, 0)), now).containsKey("date"))
        assertFalse(validateObservation(record("OBS-027").copy(occurredAt = null, timePrecision = ObservationTimePrecision.UNKNOWN), now).containsKey("time"))
        assertTrue(validateObservation(record("OBS-027").copy(timePrecision = ObservationTimePrecision.UNKNOWN), now).containsKey("time"))
    }

    @Test fun closedReviewDoesNotRequireParagraphButFallCannotDeclareNoFall() {
        val closed = record("OBS-001").copy(
            result = ObservationResult.NO_CHANGES, answers = mapOf("foot" to listOf("Ambos")), description = "",
        )
        assertTrue(validateObservation(closed, now).isEmpty())
        assertTrue(validateObservation(closed.copy(answers = emptyMap()), now).containsKey("foot"))
        assertTrue(validateObservation(closed.copy(result = ObservationResult.CHANGES), now).containsKey("description"))
        assertTrue(validateObservation(closed.copy(result = ObservationResult.CHANGES,
            answers = mapOf("foot" to listOf("Ambos"), "changes" to listOf("Ampolla"))), now).isEmpty())
        assertTrue(validateObservation(record("OBS-022").copy(result = ObservationResult.NO_CHANGES), now).containsKey("result"))
        assertTrue(validateObservation(record("OBS-009").copy(answers = mapOf("unknown-field" to listOf("value"))), now).containsKey("answers"))
        assertFalse(validateObservation(record("OBS-009").copy(localPhotoIds = listOf("opaque-id")), now).containsKey("photos"))
        assertTrue(validateObservation(record("OBS-009").copy(
            localPhotoIds = List(6) { "opaque-id-$it" },
        ), now).containsKey("photos"))
    }

    @Test fun pastWithoutDeclarationRemainsUnconfirmedAndAdditionalDoesNotFillIt() {
        val followUp = followUp()
        val events = projectObservationOccurrences(date, listOf(followUp), listOf(record("OBS-001")), now)
        assertEquals(ObservationOccurrenceState.UNCONFIRMED, events.single().state)
        assertNull(events.single().record)
        val snapshot = ObservationSnapshot(listOf(record("OBS-001")), listOf(followUp))
        assertEquals(2, projectObservationTimeline(date, null, snapshot, now).size)
    }

    @Test fun associatedFactOnDifferentActualDaySuppressesScheduledPlaceholder() {
        val followUp = followUp()
        val event = projectObservationOccurrences(date, listOf(followUp), emptyList(), now).single()
        val fact = record("OBS-001").copy(occurredOn = date.minusDays(1), followUpId = event.followUpId,
            occurrenceId = event.id, scheduledAt = event.scheduledAt)
        val snapshot = ObservationSnapshot(listOf(fact), listOf(followUp))
        assertTrue(projectObservationTimeline(date, null, snapshot, now).isEmpty())
        assertEquals(listOf(fact), projectObservationTimeline(date.minusDays(1), null, snapshot, now).mapNotNull { it.record })
    }

    @Test fun suspensionPreservesPastCancelsFutureAndReactivationDoesNotBackfill() {
        val original = followUp()
        val first = original.schedule.currentRevision
        val suspension = first.copy(id = "revision-2", revisionNumber = 2,
            changeKind = GlucoseFollowUpChangeKind.REMOVED, capturedAt = date.atTime(10, 0))
        val suspended = original.copy(schedule = original.schedule.copy(revisions = listOf(first, suspension)))
        assertEquals(1, projectObservationOccurrences(date, listOf(suspended), emptyList(), now).size)
        assertTrue(projectObservationOccurrences(date.plusDays(1), listOf(suspended), emptyList(), now).isEmpty())
        val resumed = suspension.copy(id = "revision-3", revisionNumber = 3,
            changeKind = GlucoseFollowUpChangeKind.CORRECTED, capturedAt = date.plusDays(1).atTime(12, 0),
            effectiveFrom = date.plusDays(1))
        val reactivated = original.copy(schedule = original.schedule.copy(revisions = listOf(first, suspension, resumed)))
        assertTrue(projectObservationOccurrences(date.plusDays(1), listOf(reactivated), emptyList(), date.plusDays(1).atTime(16, 0)).isEmpty())
        assertEquals(1, projectObservationOccurrences(date.plusDays(2), listOf(reactivated), emptyList(), date.plusDays(1).atTime(16, 0)).size)
    }

    @Test fun cancelledOpenWindowNeverReappearsAfterSameDayReactivationOrReschedule() {
        val original = followUp()
        val first = original.schedule.currentRevision.copy(slots = listOf(
            GlucoseFollowUpSlot("window-slot", GlucoseFollowUpTimingType.TIME_WINDOW,
                time = LocalTime.of(8, 0), windowEnd = LocalTime.of(12, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false),
        ))
        val suspension = first.copy(id = "revision-2", revisionNumber = 2,
            changeKind = GlucoseFollowUpChangeKind.REMOVED, capturedAt = date.atTime(9, 0))
        val reactivation = first.copy(id = "revision-3", revisionNumber = 3,
            changeKind = GlucoseFollowUpChangeKind.CORRECTED, capturedAt = date.atTime(10, 0))
        val restored = original.copy(schedule = original.schedule.copy(revisions = listOf(first, suspension, reactivation)))
        assertTrue(projectObservationOccurrences(date, listOf(restored), emptyList(), now).isEmpty())
        assertEquals(1, projectObservationOccurrences(date.plusDays(1), listOf(restored), emptyList(), now).size)
        val moved = first.copy(id = "revision-2", revisionNumber = 2, capturedAt = date.atTime(9, 0),
            slots = listOf(first.slots.single().copy(time = LocalTime.of(14, 0), windowEnd = LocalTime.of(15, 0))))
        val rescheduled = original.copy(schedule = original.schedule.copy(revisions = listOf(first, moved)))
        val events = projectObservationOccurrences(date, listOf(rescheduled), emptyList(), now)
        assertEquals(1, events.size)
        assertEquals(LocalTime.of(14, 0), events.single().scheduledAt.toLocalTime())
    }

    @Test fun overnightWindowCancellationUsesEffectiveMomentAcrossDayBoundary() {
        val original = followUp()
        val first = original.schedule.currentRevision.copy(slots = listOf(
            GlucoseFollowUpSlot("overnight-slot", GlucoseFollowUpTimingType.TIME_WINDOW,
                time = LocalTime.of(22, 0), windowEnd = LocalTime.of(2, 0), endsNextDay = true,
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false),
        ))
        val suspension = first.copy(id = "revision-2", revisionNumber = 2,
            changeKind = GlucoseFollowUpChangeKind.REMOVED,
            effectiveFrom = date.plusDays(1), capturedAt = date.plusDays(1).atTime(1, 0))
        val schedule = original.copy(schedule = original.schedule.copy(revisions = listOf(first, suspension)))
        assertTrue(projectObservationOccurrences(date, listOf(schedule), emptyList(), date.plusDays(1).atTime(3, 0)).isEmpty())
    }

    @Test fun recurrenceRespectsStartsEndsSpecificDaysIntervalsAndStableSlotIdentity() {
        val original = followUp()
        val first = original.schedule.currentRevision
        val range = original.copy(schedule = original.schedule.copy(revisions = listOf(first.copy(endsOn = date))))
        assertTrue(projectObservationOccurrences(date.plusDays(1), listOf(range), emptyList(), now).isEmpty())
        val specific = original.copy(schedule = original.schedule.copy(revisions = listOf(first.copy(
            frequency = GlucoseFollowUpFrequency.SPECIFIC_DAYS, weekdays = setOf(DayOfWeek.THURSDAY),
        ))))
        assertTrue(projectObservationOccurrences(date, listOf(specific), emptyList(), now).isEmpty())
        assertEquals(1, projectObservationOccurrences(date.plusDays(1), listOf(specific), emptyList(), now).size)
        val interval = original.copy(schedule = original.schedule.copy(revisions = listOf(first.copy(
            frequency = GlucoseFollowUpFrequency.EVERY_N_DAYS, everyNDaysInterval = 2, everyNDaysReferenceDate = date,
        ))))
        assertTrue(projectObservationOccurrences(date.plusDays(1), listOf(interval), emptyList(), now).isEmpty())
        assertEquals(1, projectObservationOccurrences(date.plusDays(2), listOf(interval), emptyList(), now).size)
        val revised = original.copy(schedule = original.schedule.copy(revisions = listOf(first, first.copy(
            id = "revision-2", revisionNumber = 2, indication = "Texto ficticio cambiado", capturedAt = date.atTime(6, 0),
        ))))
        assertEquals(projectObservationOccurrences(date, listOf(original), emptyList(), now).single().id,
            projectObservationOccurrences(date, listOf(revised), emptyList(), now).single().id)
    }

    @Test fun lateCreationDoesNotInventPastAndForecastExcludesLinkedOrSuspended() {
        val original = followUp()
        val late = original.copy(schedule = original.schedule.copy(revisions = listOf(
            original.schedule.currentRevision.copy(capturedAt = date.atTime(12, 0)),
        )))
        assertTrue(projectObservationOccurrences(date, listOf(late), emptyList(), now).isEmpty())
        val morning = date.atTime(6, 0)
        val forecast = projectUpcomingObservationOccurrences(ObservationSnapshot(followUps = listOf(original)), morning)
        assertEquals(2, forecast.size)
        val linked = record("OBS-001").copy(occurrenceId = forecast.first().id)
        assertEquals(1, projectUpcomingObservationOccurrences(ObservationSnapshot(listOf(linked), listOf(original)), morning).size)
        assertTrue(projectUpcomingObservationOccurrences(ObservationSnapshot(followUps = listOf(original)), morning, 0).isEmpty())
    }

    @Test fun dateAndCategoryFilteringNeverLeaksOtherDaysOrCategories() {
        val records = listOf(record("OBS-027"), record("OBS-027").copy(id = "other-day", occurredOn = date.minusDays(1)),
            record("OBS-001").copy(id = "foot"))
        val result = projectObservationTimeline(date, ObservationCategory.OTHER, ObservationSnapshot(records), now)
        assertEquals(listOf("record-fictitious"), result.mapNotNull { it.record?.id })
    }

    @Test fun savesWithConsentAndStableIdentityWithoutCreatingFollowUp() = runBlocking {
        val repository = FakeRepository()
        val save = SaveObservationUseCase(repository)
        val fact = record("OBS-027")
        save(actor, fact, now)
        save(actor, fact, now)
        assertEquals(listOf(fact), repository.snapshot.records)
        assertTrue(repository.snapshot.followUps.isEmpty())
        repository.consent = false
        try {
            save(actor, fact.copy(id = "second"), now)
            fail("Consent required")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, repository.snapshot.records.size)
    }

    private fun record(kind: String) = ObservationRecord(
        id = "record-fictitious", observationId = kind, personUnderCareId = actor.personUnderCareId,
        occurredOn = date, occurredAt = LocalTime.of(9, 0), timePrecision = ObservationTimePrecision.EXACT,
        result = ObservationResult.CHANGES, answers = emptyMap(), description = "Hecho ficticio.",
        createdAt = now, actorId = actor.caregiverId,
    )

    private fun followUp(): ObservationFollowUp {
        val revision = GlucoseFollowUpRevision(
            id = "revision-1", followUpId = "follow-up", revisionNumber = 1,
            indication = "Indicación ficticia de revisión", frequency = GlucoseFollowUpFrequency.DAILY,
            startsOn = date, effectiveFrom = date,
            slots = listOf(GlucoseFollowUpSlot("slot-1", time = LocalTime.of(8, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = false)),
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = date.minusDays(1).atTime(10, 0), authorActorId = actor.caregiverId,
        )
        return ObservationFollowUp("OBS-001", GlucoseFollowUp("follow-up", actor.personUnderCareId,
            date.minusDays(1).atTime(10, 0), actor.caregiverId, listOf(revision)))
    }

    private class FakeRepository : ObservationRepository {
        var snapshot = ObservationSnapshot()
        var consent = true
        override suspend fun hasSensitiveConsent(actor: GlucoseActor) = consent
        override suspend fun acceptSensitiveConsent(actor: GlucoseActor, acceptedAtEpochMillis: Long) { consent = true }
        override suspend fun load(actor: GlucoseActor) = snapshot
        override suspend fun saveRecord(actor: GlucoseActor, record: ObservationRecord): ObservationRecord {
            val prior = snapshot.records.firstOrNull { it.id == record.id }
            if (prior != null) return prior
            snapshot = snapshot.copy(records = snapshot.records + record)
            return record
        }
        override suspend fun appendFollowUp(actor: GlucoseActor, followUp: ObservationFollowUp, expectedRevisionNumber: Int): ObservationFollowUp {
            snapshot = snapshot.copy(followUps = snapshot.followUps.filterNot { it.schedule.id == followUp.schedule.id } + followUp)
            return followUp
        }
    }
}
