package com.bitavida.app.ui.medical

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationScheduleFlowTest {
    @Test
    fun administrationAmountKeepsExactTextAcrossEditsAndUnitChanges() {
        val other = MedicationAdministrationDraft(
            id = 2,
            timingType = MedicationTimingType.EXACT_TIME,
            exactTime = MedicationTime(20, 0),
        )
        var draft = GuidedMedicationDraft(
            frequency = MedicationFrequency.EVERY_DAY,
            administrationsByFrequency = mapOf(
                MedicationFrequency.EVERY_DAY to listOf(
                    MedicationAdministrationDraft(
                        id = 1,
                        timingType = MedicationTimingType.EXACT_TIME,
                        exactTime = MedicationTime(8, 0),
                    ),
                    other,
                ),
            ),
        )

        listOf("1", "1/2", "0.5").forEach { typed ->
            val current = draft.administrationsByFrequency
                .getValue(MedicationFrequency.EVERY_DAY)
                .first()
            draft = checkNotNull(
                draft.withUpdatedAdministration(
                    MedicationFrequency.EVERY_DAY,
                    current.copy(
                        amountOverride = MedicationAdministrationAmount(
                            value = typed,
                            unit = current.amountOverride?.unit.orEmpty(),
                        ),
                    ),
                ),
            )
            assertEquals(
                typed,
                draft.administrationsByFrequency
                    .getValue(MedicationFrequency.EVERY_DAY)
                    .first().amountOverride?.value,
            )
        }

        val current = draft.administrationsByFrequency
            .getValue(MedicationFrequency.EVERY_DAY)
            .first()
        draft = checkNotNull(
            draft.withUpdatedAdministration(
                MedicationFrequency.EVERY_DAY,
                current.copy(
                    amountOverride = current.amountOverride?.copy(
                        unit = "tableta",
                    ),
                ),
            ),
        )

        val administrations = draft.administrationsByFrequency
            .getValue(MedicationFrequency.EVERY_DAY)
        assertEquals("0.5", administrations.first().amountOverride?.value)
        assertEquals("tableta", administrations.first().amountOverride?.unit)
        assertEquals(other, administrations.last())
    }

    @Test
    fun medicationAmountsAcceptPositiveDecimalsAndFractionsWithoutPrematureNormalization() {
        listOf("1", "2", "0.5", "1/2", "1/4", "3/4")
            .forEach { assertTrue(it.isPositiveMedicationAmountText()) }

        listOf("", "0", "-1", "1/", "/2", "1/0", "1//2", "texto")
            .forEach { assertFalse(it.isPositiveMedicationAmountText()) }
    }

    @Test
    fun eachFrequencyKeepsItsOwnTemporaryAdministrations() {
        val daily = MedicationAdministrationDraft(
            id = 1,
            timingType = MedicationTimingType.EXACT_TIME,
            exactTime = MedicationTime(8, 0),
        )
        val cycle = MedicationAdministrationDraft(
            id = 2,
            timingType = MedicationTimingType.TIME_WINDOW,
            windowStart = MedicationTime(9, 0),
            windowEnd = MedicationTime(10, 0),
        )
        val draft = GuidedMedicationDraft(
            frequency = MedicationFrequency.EVERY_DAY,
            administrationsByFrequency = mapOf(
                MedicationFrequency.EVERY_DAY to listOf(daily),
                MedicationFrequency.CYCLE to listOf(cycle),
            ),
        )

        val updated = checkNotNull(
            draft.withUpdatedAdministration(
                MedicationFrequency.EVERY_DAY,
                daily.copy(
                    amountOverride = MedicationAdministrationAmount(
                        value = "1/2",
                        unit = "tableta",
                    ),
                ),
            ),
        )

        assertEquals(
            cycle,
            updated.administrationsByFrequency
                .getValue(MedicationFrequency.CYCLE)
                .single(),
        )
        assertEquals(
            "1/2",
            updated.administrationsByFrequency
                .getValue(MedicationFrequency.EVERY_DAY)
                .single().amountOverride?.value,
        )
    }

    @Test
    fun substepsValidateOnlyTheirVisibleRequirements() {
        val draft = GuidedMedicationDraft(
            frequency = MedicationFrequency.SPECIFIC_DAYS,
        )

        assertTrue(
            validateMedicationScheduleSubstep(
                MedicationScheduleSubstep.FREQUENCY,
                draft,
            ).isEmpty(),
        )
        assertTrue(
            MedicationRegistrationField.WEEKDAYS in
                validateMedicationScheduleSubstep(
                    MedicationScheduleSubstep.PARAMETERS,
                    draft,
                ),
        )
        assertFalse(
            MedicationRegistrationField.ADMINISTRATIONS in
                validateMedicationScheduleSubstep(
                    MedicationScheduleSubstep.PARAMETERS,
                    draft,
                ),
        )
    }

    @Test
    fun activityAdministrationRequiresActivityAndNoticeTime() {
        val administration = MedicationAdministrationDraft(
            id = 1,
            timingType = MedicationTimingType.ACTIVITY_ONLY,
        )
        val base = GuidedMedicationDraft(
            frequency = MedicationFrequency.EVERY_DAY,
            administrationsByFrequency = mapOf(
                MedicationFrequency.EVERY_DAY to listOf(administration),
            ),
        )

        assertTrue(
            MedicationRegistrationField.ADMINISTRATIONS in
                validateMedicationScheduleSubstep(
                    MedicationScheduleSubstep.ADMINISTRATIONS,
                    base,
                ),
        )
        val withActivity = base.copy(
            administrationsByFrequency = mapOf(
                MedicationFrequency.EVERY_DAY to listOf(
                    administration.copy(
                        activity = MedicationActivityOption.WITH_BREAKFAST,
                    ),
                ),
            ),
        )
        assertTrue(
            MedicationRegistrationField.ADMINISTRATIONS in
                validateMedicationScheduleSubstep(
                    MedicationScheduleSubstep.ADMINISTRATIONS,
                    withActivity,
                ),
        )
        val complete = withActivity.copy(
            administrationsByFrequency = mapOf(
                MedicationFrequency.EVERY_DAY to listOf(
                    administration.copy(
                        activity = MedicationActivityOption.WITH_BREAKFAST,
                        activityNoticeTime = MedicationTime(8, 0),
                    ),
                ),
            ),
        )
        assertFalse(
            MedicationRegistrationField.ADMINISTRATIONS in
                validateMedicationScheduleSubstep(
                    MedicationScheduleSubstep.ADMINISTRATIONS,
                    complete,
                ),
        )
    }

    @Test
    fun cycleEndMustMatchExplicitCycleCount() {
        val valid = GuidedMedicationDraft(
            frequency = MedicationFrequency.CYCLE,
            cycleActiveDays = "2",
            cycleRestDays = "1",
            cycleReferenceDate = LocalDate.of(2026, 1, 1),
            cycleCount = "2",
            treatmentStartMode = MedicationTreatmentStartMode.SPECIFIC_DATE,
            treatmentStartDate = LocalDate.of(2026, 1, 1),
            treatmentEndDate = LocalDate.of(2026, 1, 5),
        )

        assertTrue(
            validateMedicationScheduleSubstep(
                MedicationScheduleSubstep.VALIDITY,
                valid,
            ).isEmpty(),
        )
        assertTrue(
            MedicationRegistrationField.TREATMENT_END in
                validateMedicationScheduleSubstep(
                    MedicationScheduleSubstep.VALIDITY,
                    valid.copy(
                        treatmentEndDate = LocalDate.of(2026, 1, 6),
                    ),
                ),
        )
    }
}
