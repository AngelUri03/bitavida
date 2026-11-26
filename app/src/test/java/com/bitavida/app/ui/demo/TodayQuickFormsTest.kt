package com.bitavida.app.ui.demo

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.BloodPressureFakeRepository
import com.bitavida.app.domain.usecase.GlucoseFakeRepository
import com.bitavida.app.domain.usecase.WeightFakeRepository
import com.bitavida.app.ui.measurements.*
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TodayQuickFormsTest {
    private val actor = GlucoseActor("fictional-profile", "fictional-caregiver", "fictional-person", true)
    private val now = LocalDateTime.of(2026, 11, 20, 12, 0)

    @Test fun glucoseOpensTheActualScheduledCaptureWithKnownContextAndNoInventedReading() = withModels { models ->
        val event = models.glucoseFollowUp.occurrencesOn(now.toLocalDate()).single()
        openTodayMeasurementRegistration(MeasurementKind.CAPILLARY_GLUCOSE, event,
            models.glucose, models.pressure, models.weight)
        val state = models.glucose.state.value
        val draft = requireNotNull(state.draft)
        assertEquals(event.id, (draft.context as GlucoseRegistrationContext.Scheduled).occurrence.id)
        assertEquals(GlucoseContext.FASTING, draft.moment)
        assertEquals("", draft.reading)
        assertEquals(now, draft.actualAt)
        assertNull(state.selectedOccurrence)
        assertTrue(models.glucoseRepository.records.isEmpty())
    }

    @Test fun pressureOpensItsOwnFormWithTheExactEventAndBlankActualValues() = withModels { models ->
        val event = models.pressureFollowUp.occurrencesOn(now.toLocalDate()).single()
        openTodayMeasurementRegistration(MeasurementKind.BLOOD_PRESSURE, event,
            models.glucose, models.pressure, models.weight)
        val draft = requireNotNull(models.pressure.state.value.draft)
        assertEquals(event.id, (draft.context as BloodPressureRegistrationContext.Scheduled).occurrence.id)
        assertEquals(GlucoseContext.RESTING, draft.moment)
        assertEquals("", draft.systolic)
        assertEquals("", draft.diastolic)
        assertNull(models.glucose.state.value.draft)
        assertNull(models.weight.state.value.draft)
    }

    @Test fun weightUsesItsOwnScheduledContextAndDoesNotGuessKilograms() = withModels { models ->
        val event = models.weightFollowUp.occurrencesOn(now.toLocalDate()).single()
        openTodayMeasurementRegistration(MeasurementKind.WEIGHT, event,
            models.glucose, models.pressure, models.weight)
        val draft = requireNotNull(models.weight.state.value.draft)
        assertEquals(event.id, (draft.context as WeightRegistrationContext.Scheduled).occurrence.id)
        assertEquals("", draft.kilograms)
        assertNull(models.glucose.state.value.draft)
        assertNull(models.pressure.state.value.draft)
    }

    @Test fun notPerformedOpensADeclarationAndOnlyPersistsAfterTheExistingExplicitConfirmation() = withModels { models ->
        val event = models.glucoseFollowUp.occurrencesOn(now.toLocalDate()).single()
        openTodayMeasurementNotPerformed(MeasurementKind.CAPILLARY_GLUCOSE, event,
            models.glucose, models.pressure, models.weight)
        assertEquals(event, models.glucose.state.value.selectedOccurrence)
        assertNull(models.glucose.state.value.draft)
        assertTrue(models.glucoseRepository.records.isEmpty())
        assertTrue(models.glucoseRepository.occurrenceDecisions.isEmpty())

        models.glucose.onAction(GlucoseAction.ConfirmNotPerformed(event, "Motivo ficticio"))
        assertEquals(1, models.glucoseRepository.occurrenceDecisions.size)
        assertEquals(GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
            models.glucose.state.value.selectedOccurrenceDecision?.kind)
        openTodayMeasurementRegistration(MeasurementKind.CAPILLARY_GLUCOSE, event,
            models.glucose, models.pressure, models.weight)
        assertNull(models.glucose.state.value.draft)
        assertTrue(models.glucoseRepository.records.isEmpty())
    }

    @Test fun repeatedRegisterTapsKeepTheSameDraftAndCannotCreateADuplicate() = withModels { models ->
        val event = models.glucoseFollowUp.occurrencesOn(now.toLocalDate()).single()
        openTodayMeasurementRegistration(MeasurementKind.CAPILLARY_GLUCOSE, event,
            models.glucose, models.pressure, models.weight)
        val first = models.glucose.state.value.draft
        openTodayMeasurementRegistration(MeasurementKind.CAPILLARY_GLUCOSE, event,
            models.glucose, models.pressure, models.weight)
        assertEquals(first, models.glucose.state.value.draft)
        assertTrue(models.glucoseRepository.records.isEmpty())
    }

    private fun withModels(block: (Models) -> Unit) = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val glucoseFollowUp = followUp("glucose", GlucoseContext.FASTING)
            val pressureFollowUp = followUp("pressure", GlucoseContext.RESTING)
            val weightFollowUp = followUp("weight", GlucoseContext.UNSPECIFIED)
            val glucoseRepository = GlucoseFakeRepository()
            val pressureRepository = BloodPressureFakeRepository()
            val weightRepository = WeightFakeRepository()
            glucoseRepository.createFollowUp(actor, glucoseFollowUp)
            pressureRepository.createFollowUp(actor, pressureFollowUp)
            weightRepository.createFollowUp(actor, weightFollowUp)
            block(Models(
                GlucoseViewModel(actor, glucoseRepository, scope, { now }),
                BloodPressureViewModel(actor, pressureRepository, scope, { now }),
                WeightViewModel(actor, weightRepository, scope, { now }),
                glucoseRepository, glucoseFollowUp, pressureFollowUp, weightFollowUp,
            ))
        } finally { scope.cancel() }
    }

    private data class Models(
        val glucose: GlucoseViewModel,
        val pressure: BloodPressureViewModel,
        val weight: WeightViewModel,
        val glucoseRepository: GlucoseFakeRepository,
        val glucoseFollowUp: GlucoseFollowUp,
        val pressureFollowUp: GlucoseFollowUp,
        val weightFollowUp: GlucoseFollowUp,
    )

    private fun followUp(id: String, context: GlucoseContext): GlucoseFollowUp {
        val day = now.toLocalDate().minusDays(1)
        val revision = GlucoseFollowUpRevision(
            "$id-revision", id, 1, "Indicación ficticia", GlucoseFollowUpFrequency.DAILY,
            startsOn = day, effectiveFrom = day,
            slots = listOf(GlucoseFollowUpSlot("$id-slot", time = LocalTime.of(9, 0),
                suggestedContext = context, reminderEnabled = false)),
            remindersEnabled = false, changeKind = GlucoseFollowUpChangeKind.CREATED,
            capturedAt = day.atStartOfDay(), authorActorId = actor.caregiverId,
        )
        return GlucoseFollowUp(id, actor.personUnderCareId, day.atStartOfDay(), actor.caregiverId, listOf(revision))
    }
}
