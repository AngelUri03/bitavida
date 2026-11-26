package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationCycle
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderEffectiveState
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationReminderPlanningTest {
    private val date = LocalDate.of(2026, 11, 11)
    private val rangeStart = date.atStartOfDay()
    private val rangeEnd = date.plusDays(2).atStartOfDay()

    @Test
    fun exactReminderUsesDeclaredTimeAndStableIdentityWithoutDuplicates() {
        val plan = plan(
            "exact",
            configuration(
                administrations = listOf(exact("dose", LocalTime.of(8, 15))),
            ),
        )

        val reminders = planMedicationReminders(
            plans = listOf(plan, plan),
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )

        assertEquals(2, reminders.size)
        assertEquals(LocalTime.of(8, 15), reminders.first().scheduledAt.toLocalTime())
        assertEquals(reminders.map { it.id }.distinct(), reminders.map { it.id })
    }

    @Test
    fun exactAdministrationCanOptOutWithoutChangingItsIndicatedTime() {
        val indicatedTime = LocalTime.of(18, 0)
        val plan = plan(
            "exact-disabled",
            configuration(
                administrations = listOf(
                    exact(
                        id = "dose",
                        time = indicatedTime,
                        reminderEnabled = false,
                    ),
                ),
            ),
        )

        assertTrue(
            planMedicationReminders(
                plans = listOf(plan),
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
            ).isEmpty(),
        )
        assertEquals(
            indicatedTime,
            plan.currentRevision.configuration
                ?.administrations?.single()?.exactTime,
        )
    }

    @Test
    fun windowUsesExplicitNoticeAndPreservesFollowingDay() {
        val plan = plan(
            "window",
            configuration(
                administrations = listOf(
                    MedicationAdministration(
                        id = "night-window",
                        timing = MedicationAdministrationTiming.TIME_WINDOW,
                        windowStart = LocalTime.of(23, 0),
                        windowEnd = LocalTime.of(1, 30),
                        windowEndsNextDay = true,
                        noticeTime = LocalTime.of(0, 30),
                        noticeNextDay = true,
                    ),
                ),
            ),
        )

        val reminder = planMedicationReminders(
            listOf(plan),
            rangeStart,
            rangeEnd,
        ).first()

        assertEquals(date.plusDays(1), reminder.scheduledAt.toLocalDate())
        assertEquals(LocalTime.of(0, 30), reminder.scheduledAt.toLocalTime())
    }

    @Test
    fun windowWithoutExplicitNoticeDoesNotCreateReminder() {
        val plan = plan(
            "window-without-notice",
            configuration(
                administrations = listOf(
                    MedicationAdministration(
                        id = "window",
                        timing = MedicationAdministrationTiming.TIME_WINDOW,
                        windowStart = LocalTime.of(8, 0),
                        windowEnd = LocalTime.of(9, 0),
                    ),
                ),
            ),
        )

        assertTrue(
            planMedicationReminders(listOf(plan), rangeStart, rangeEnd)
                .isEmpty(),
        )
    }

    @Test
    fun cycleRestDayAndSuspendedPlanDoNotCreateReminder() {
        val restDayPlan = plan(
            "cycle",
            configuration(
                startsOn = date.minusDays(1),
                frequency = MedicationFrequencyKind.CYCLE,
                cycle = MedicationCycle(
                    activeDays = 1,
                    restDays = 1,
                    referenceDate = date.minusDays(1),
                    numberOfCycles = 1,
                ),
                administrations = listOf(exact("cycle-dose", LocalTime.of(9, 0))),
            ),
        )
        val suspendedPlan = plan(
            "suspended",
            configuration(
                status = MedicationTreatmentStatus.SUSPENDED,
                administrations = listOf(exact("suspended-dose", LocalTime.of(10, 0))),
            ),
        )

        val reminders = planMedicationReminders(
            listOf(restDayPlan, suspendedPlan),
            rangeStart,
            date.plusDays(1).atStartOfDay().minusNanos(1),
        )

        assertTrue(reminders.isEmpty())
    }

    @Test
    fun disabledAndAsNeededPreferencesNeverCreateAutomaticReminder() {
        val disabled = plan(
            "disabled",
            configuration(
                reminderPreference = MedicationReminderPreference.NONE,
                administrations = listOf(exact("disabled-dose", LocalTime.of(8, 0))),
            ),
        )
        val asNeeded = plan(
            "needed",
            configuration(
                frequency = MedicationFrequencyKind.AS_NEEDED,
                administrations = emptyList(),
            ),
        )

        assertTrue(
            planMedicationReminders(
                listOf(disabled, asNeeded),
                rangeStart,
                rangeEnd,
            ).isEmpty(),
        )
    }

    @Test
    fun changingScheduleReplacesOccurrenceIdentity() {
        val initial = plan(
            "modified",
            configuration(
                administrations = listOf(exact("dose", LocalTime.of(8, 0))),
            ),
            revisionId = "revision-1",
        )
        val modified = plan(
            "modified",
            configuration(
                administrations = listOf(exact("dose", LocalTime.of(9, 0))),
            ),
            revisionId = "revision-2",
        )

        val first = planMedicationReminders(
            listOf(initial),
            rangeStart,
            rangeEnd,
        ).first()
        val second = planMedicationReminders(
            listOf(modified),
            rangeStart,
            rangeEnd,
        ).first()

        assertNotEquals(first.id, second.id)
        assertEquals(LocalTime.of(9, 0), second.scheduledAt.toLocalTime())
    }

    private fun exact(
        id: String,
        time: LocalTime,
        reminderEnabled: Boolean? = null,
    ) = MedicationAdministration(
        id = id,
        timing = MedicationAdministrationTiming.EXACT_TIME,
        exactTime = time,
        reminderEnabled = reminderEnabled,
    )

    private fun configuration(
        startsOn: LocalDate = date,
        frequency: MedicationFrequencyKind = MedicationFrequencyKind.EVERY_DAY,
        cycle: MedicationCycle? = null,
        status: MedicationTreatmentStatus = MedicationTreatmentStatus.ACTIVE,
        reminderPreference: MedicationReminderPreference =
            MedicationReminderPreference.NORMAL,
        administrations: List<MedicationAdministration>,
    ) = MedicationConfiguration(
        form = MedicationProductForm.TABLET,
        concentrationValue = null,
        concentrationUnit = null,
        baseAmount = MedicationAmount("1", "tableta"),
        frequency = frequency,
        cycle = cycle,
        startsOn = startsOn,
        status = status,
        reminderPreference = reminderPreference,
        secondReminderEnabled = false,
        reminderEffectiveState =
            MedicationReminderEffectiveState.PREFERENCE_RECORDED_NOT_SCHEDULED,
        administrations = administrations,
    )

    private fun plan(
        id: String,
        configuration: MedicationConfiguration,
        revisionId: String = "$id-revision",
    ) = MedicationPlan(
        id = id,
        personUnderCareId = "person",
        medicalIndicationId = null,
        medicalContentOrigin =
            InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
        scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
        createdAtEpochMillis = 1L,
        createdByActorId = "caregiver",
        contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
        revisions = listOf(
            MedicationPlanRevision(
                id = revisionId,
                medicationPlanId = id,
                revisionNumber = 1,
                medicationName = "Medicamento",
                doseAsIndicated = "1 tableta",
                unitOrPresentation = "TABLET",
                frequencyAsIndicated = configuration.frequency.name,
                startsOn = configuration.startsOn,
                endsOn = configuration.endsOn,
                originalAdditionalInstructions = null,
                scheduledDoses = emptyList(),
                capturedAtEpochMillis = 1L,
                authorActorId = "caregiver",
                changeKind = MedicationPlanChangeKind.CREATED,
                changeReason = null,
                configuration = configuration,
            ),
        ),
    )
}
