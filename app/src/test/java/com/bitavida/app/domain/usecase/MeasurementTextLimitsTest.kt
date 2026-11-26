package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MeasurementTextLimitsTest {
    private val now = LocalDateTime.of(2026, 11, 20, 10, 0)
    private val actor = GlucoseActor("fictional-profile", "fictional-caregiver", "fictional-person", true)
    private val family = "👨‍👩‍👧‍👦"

    @Test fun allThreeMeasurementNotesAccept500WholeGraphemesAndReject501() {
        for (note in listOf("á".repeat(500), "a\u0301".repeat(500), family.repeat(500))) {
            assertTrue(validateGlucoseDraft(glucose(note), now).isEmpty())
            assertTrue(validateBloodPressureDraft(pressure(note), now).isEmpty())
            assertTrue(validateWeightDraft(weight(note), now).isEmpty())
            assertEquals(setOf(GlucoseField.NOTE), validateGlucoseDraft(glucose(note + "x"), now))
            assertEquals(setOf(BloodPressureField.NOTE), validateBloodPressureDraft(pressure(note + "x"), now))
            assertEquals(setOf(WeightField.NOTE), validateWeightDraft(weight(note + "x"), now))
        }
    }

    @Test fun invalidPastedTextCannotReachACreateOperation() = runBlocking {
        val repository = GlucoseFakeRepository()
        val save = SaveGlucoseMeasurementUseCase(repository) { now }
        assertTrue(save(actor, glucose(family.repeat(501))) is GlucoseSaveResult.Invalid)
        assertEquals(0, repository.creates)
        val result = save(actor, glucose(family.repeat(500))) as GlucoseSaveResult.Stored
        assertEquals(family.repeat(500), result.record.glucoseMetadata?.note)
        assertEquals(1, repository.creates)
    }

    @Test fun unchangedLegacyGlucoseNoteSurvivesAnEditButCannotBeReplacedByOversizedText() = runBlocking {
        val legacy = "  " + "Texto conservado ".repeat(60) + "  "
        val repository = GlucoseFakeRepository()
        repository.records += MeasurementRecord(
            "glucose", MeasurementValue.Glucose("90", GlucoseContext.FASTING), now.minusMinutes(1), now,
            glucoseMetadata = GlucoseRecordMetadata(GlucoseEntryKind.INDEPENDENT, note = legacy),
        )
        val correct = CorrectGlucoseMeasurementUseCase(repository) { now }
        val corrected = correct(actor, glucose(legacy).copy(reading = "91")) as GlucoseSaveResult.Stored
        assertEquals(legacy, corrected.record.glucoseMetadata?.note)
        assertTrue(correct(actor, glucose("x".repeat(501))) is GlucoseSaveResult.Invalid)
        assertEquals(legacy, repository.records.single().glucoseMetadata?.note)
        val shortened = correct(actor, glucose("Texto corregido")) as GlucoseSaveResult.Stored
        assertEquals("Texto corregido", shortened.record.glucoseMetadata?.note)
    }

    @Test fun legacyPressureAndWeightNotesRemainCompleteWhileOtherFieldsAreCorrected() = runBlocking {
        val legacy = "Texto histórico ".repeat(60)
        val pressureRepository = BloodPressureFakeRepository()
        pressureRepository.records += MeasurementRecord(
            "pressure", MeasurementValue.BloodPressure("120", "80"), now, now,
            bloodPressureMetadata = BloodPressureRecordMetadata(
                BloodPressureEntryKind.INDEPENDENT, actualContext = GlucoseContext.RESTING, note = legacy),
        )
        val pressure = CorrectBloodPressureMeasurementUseCase(pressureRepository) { now }(
            actor, pressure(legacy).copy(systolic = "121")) as BloodPressureSaveResult.Stored
        assertEquals(legacy, pressure.record.bloodPressureMetadata?.note)

        val weightRepository = WeightFakeRepository()
        weightRepository.records += MeasurementRecord(
            "weight", MeasurementValue.Weight(BigDecimal("60")), now, now,
            weightMetadata = WeightRecordMetadata(WeightEntryKind.INDEPENDENT, note = legacy),
        )
        val weight = CorrectWeightMeasurementUseCase(weightRepository) { now }(
            actor, weight(legacy).copy(kilograms = "61")) as WeightSaveResult.Stored
        assertEquals(legacy, weight.record.weightMetadata?.note)
    }

    @Test fun aLoadedLegacyValueCanRemainOrBeCorrectedWithoutChangingNumericRules() {
        val legacy = "x".repeat(700)
        assertTrue(validateGlucoseDraft(glucose(legacy), now, legacy).isEmpty())
        assertTrue(GlucoseField.NOTE in validateGlucoseDraft(glucose(legacy + "x"), now, legacy))
        assertTrue(GlucoseField.READING in validateGlucoseDraft(glucose("Nota").copy(reading = "1e3"), now))
        assertTrue(BloodPressureField.SYSTOLIC in validateBloodPressureDraft(pressure("Nota").copy(systolic = "1000"), now))
        assertTrue(WeightField.KILOGRAMS in validateWeightDraft(weight("Nota").copy(kilograms = "1.234"), now))
    }

    @Test fun followUpTextUsesItsPurposeAndASeparate300CharacterChangeReason() {
        val draft = GlucoseFollowUpDraft(
            followUpId = "fictional-follow-up", indication = family.repeat(1_000),
            effectiveFrom = now.toLocalDate(), slots = emptyList(), changeReason = family.repeat(300),
        )
        val accepted = validateGlucoseFollowUpDraft(draft)
        assertFalse(GlucoseFollowUpField.INDICATION in accepted)
        assertFalse(GlucoseFollowUpField.CHANGE_REASON in accepted)
        assertTrue(GlucoseFollowUpField.INDICATION in validateGlucoseFollowUpDraft(draft.copy(indication = draft.indication + "x")))
        assertTrue(GlucoseFollowUpField.CHANGE_REASON in validateGlucoseFollowUpDraft(draft.copy(changeReason = draft.changeReason + "x")))
    }

    private fun glucose(note: String) = GlucoseDraft("glucose", GlucoseRegistrationContext.Independent,
        now.minusMinutes(1), reading = "90", moment = GlucoseContext.FASTING, note = note)
    private fun pressure(note: String) = BloodPressureDraft("pressure", BloodPressureRegistrationContext.Independent,
        now.minusMinutes(1), systolic = "120", diastolic = "80", moment = GlucoseContext.RESTING, note = note)
    private fun weight(note: String) = WeightDraft("weight", WeightRegistrationContext.Independent,
        now.minusMinutes(1), kilograms = "60", note = note)
}
