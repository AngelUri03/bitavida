package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicationActivityRelation
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationCycle
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationHourlyIntervalMode
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderEffectiveState
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationScheduleProjectionTest {
    private val now = LocalDateTime.of(2026, 11, 11, 20, 0)

    @Test
    fun futureTreatmentDoesNotCreatePrematureOccurrence() {
        val plan = plan(
            id = "future",
            configuration = dailyConfiguration(
                startsOn = now.toLocalDate().plusDays(1),
                time = LocalTime.of(21, 0),
            ),
        )

        assertTrue(
            projectUpcomingMedicationAdministrations(listOf(plan), now)
                .isEmpty(),
        )
    }

    @Test
    fun validDailyAdministrationUsesStoredFormAmountAndStableIdentity() {
        val configuration = dailyConfiguration(
            startsOn = now.toLocalDate(),
            time = LocalTime.of(21, 0),
            form = MedicationProductForm.INHALER,
            overrideAmount = MedicationAmount("2", "inhalaciones"),
        )
        val plan = plan("daily", configuration)

        val first = projectUpcomingMedicationAdministrations(
            listOf(plan),
            now,
        ).single()
        val second = projectUpcomingMedicationAdministrations(
            listOf(plan),
            now,
        ).single()

        assertEquals(MedicationProductForm.INHALER, first.form)
        assertEquals(MedicationAmount("2", "inhalaciones"), first.amount)
        assertEquals(first.id, second.id)
        assertEquals("daily", first.medicationPlanId)
    }

    @Test
    fun continuousHourlyIntervalRespectsReferenceAndInterval() {
        val configuration = baseConfiguration(
            startsOn = now.toLocalDate(),
            frequency = MedicationFrequencyKind.EVERY_N_HOURS,
            hourlyInterval = 6,
            hourlyMode = MedicationHourlyIntervalMode.CONTINUOUS,
            hourlyReferenceDate = now.toLocalDate(),
            hourlyReferenceTime = LocalTime.of(8, 0),
        )

        val occurrences = projectUpcomingMedicationAdministrations(
            listOf(plan("hourly", configuration)),
            now,
        )

        assertEquals(
            listOf(
                LocalTime.of(14, 0),
                LocalTime.of(20, 0),
                LocalTime.of(2, 0),
            ),
            occurrences.map { it.startsAt.toLocalTime() },
        )
    }

    @Test
    fun cycleDoesNotCreateOccurrenceDuringRestDay() {
        val date = now.toLocalDate()
        val configuration = dailyConfiguration(
            startsOn = date.minusDays(1),
            time = LocalTime.of(21, 0),
            frequency = MedicationFrequencyKind.CYCLE,
            cycle = MedicationCycle(
                activeDays = 1,
                restDays = 1,
                referenceDate = date.minusDays(1),
            ),
        )

        assertTrue(
            projectUpcomingMedicationAdministrations(
                listOf(plan("cycle", configuration)),
                now,
            ).isEmpty(),
        )
    }

    @Test
    fun reactivationProjectionSkipsAnAdministrationThatAlreadyPassed() {
        val reactivationAt = LocalDateTime.of(2026, 11, 14, 12, 55)
        val plan = plan(
            "reactivation",
            dailyConfiguration(
                startsOn = LocalDate.of(2026, 11, 14),
                time = LocalTime.of(0, 30),
            ),
        )

        val next = projectNextMedicationAdministration(plan, reactivationAt)

        assertEquals(LocalDate.of(2026, 11, 15), next?.date)
        assertEquals(LocalTime.of(0, 30), next?.exactTime)
    }

    @Test
    fun asNeededMedicationNeverCreatesAutomaticOccurrence() {
        val configuration = baseConfiguration(
            startsOn = now.toLocalDate(),
            frequency = MedicationFrequencyKind.AS_NEEDED,
            administrations = emptyList(),
        )

        assertTrue(
            projectUpcomingMedicationAdministrations(
                listOf(plan("needed", configuration)),
                now,
            ).isEmpty(),
        )
    }

    @Test
    fun specificDaysAndWindowRemainBoundToStoredSchedule() {
        val configuration = baseConfiguration(
            startsOn = now.toLocalDate(),
            frequency = MedicationFrequencyKind.SPECIFIC_DAYS,
            weekdays = setOf(DayOfWeek.WEDNESDAY),
            administrations = listOf(
                MedicationAdministration(
                    id = "window",
                    timing = MedicationAdministrationTiming.TIME_WINDOW,
                    windowStart = LocalTime.of(19, 30),
                    windowEnd = LocalTime.of(20, 30),
                ),
            ),
        )

        val occurrence = projectUpcomingMedicationAdministrations(
            listOf(plan("window-plan", configuration)),
            now,
        ).single()

        assertEquals(MedicationOccurrenceTiming.TIME_WINDOW, occurrence.timing)
        assertEquals(LocalTime.of(19, 30), occurrence.startsAt.toLocalTime())
        assertEquals(LocalTime.of(20, 30), occurrence.endsAt?.toLocalTime())
    }

    @Test
    fun legacyMixedRelationIsIgnoredOutsideActivityAdministration() {
        val configuration = baseConfiguration(
            startsOn = now.toLocalDate(),
            frequency = MedicationFrequencyKind.EVERY_DAY,
            administrations = listOf(
                MedicationAdministration(
                    id = "legacy-exact",
                    timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(21, 0),
                    activityRelation = MedicationActivityRelation.WITH_BREAKFAST,
                ),
                MedicationAdministration(
                    id = "activity",
                    timing = MedicationAdministrationTiming.ACTIVITY_ONLY,
                    activityRelation = MedicationActivityRelation.BEFORE_SLEEP,
                    activityNoticeTime = LocalTime.of(21, 30),
                ),
            ),
        )

        val occurrences = projectUpcomingMedicationAdministrations(
            listOf(plan("compatibility", configuration)),
            now,
        )

        assertEquals(2, occurrences.size)
        assertEquals(null, occurrences.first().activityRelation)
        assertEquals(
            MedicationActivityRelation.BEFORE_SLEEP,
            occurrences.last().activityRelation,
        )
        assertEquals(
            MedicationOccurrenceTiming.ACTIVITY_NOTICE,
            occurrences.last().timing,
        )
    }

    @Test
    fun projectionNeverReturnsMoreThanFourActivities() {
        val plans = (1..6).map { index ->
            plan(
                "plan-$index",
                dailyConfiguration(
                    startsOn = now.toLocalDate(),
                    time = LocalTime.of(20, index),
                ),
            )
        }

        val activities = projectUpcomingMedicationAdministrations(
            plans = plans,
            now = now,
            limit = 4,
        )

        assertEquals(4, activities.size)
        assertEquals(
            activities.sortedBy { it.startsAt }.map { it.id },
            activities.map { it.id },
        )
    }

    private fun dailyConfiguration(
        startsOn: LocalDate,
        time: LocalTime,
        form: MedicationProductForm = MedicationProductForm.TABLET,
        overrideAmount: MedicationAmount? = null,
        frequency: MedicationFrequencyKind =
            MedicationFrequencyKind.EVERY_DAY,
        cycle: MedicationCycle? = null,
    ): MedicationConfiguration = baseConfiguration(
        startsOn = startsOn,
        frequency = frequency,
        form = form,
        cycle = cycle,
        administrations = listOf(
            MedicationAdministration(
                id = "administration",
                timing = MedicationAdministrationTiming.EXACT_TIME,
                exactTime = time,
                amountOverride = overrideAmount,
            ),
        ),
    )

    private fun baseConfiguration(
        startsOn: LocalDate,
        frequency: MedicationFrequencyKind,
        form: MedicationProductForm = MedicationProductForm.TABLET,
        weekdays: Set<DayOfWeek> = emptySet(),
        hourlyInterval: Int? = null,
        hourlyMode: MedicationHourlyIntervalMode? = null,
        hourlyReferenceDate: LocalDate? = null,
        hourlyReferenceTime: LocalTime? = null,
        cycle: MedicationCycle? = null,
        administrations: List<MedicationAdministration> = emptyList(),
    ) = MedicationConfiguration(
        form = form,
        concentrationValue = null,
        concentrationUnit = null,
        baseAmount = MedicationAmount("1", "tableta"),
        frequency = frequency,
        weekdays = weekdays,
        hourlyInterval = hourlyInterval,
        hourlyMode = hourlyMode,
        hourlyReferenceDate = hourlyReferenceDate,
        hourlyReferenceTime = hourlyReferenceTime,
        cycle = cycle,
        startsOn = startsOn,
        endsOn = null,
        status = MedicationTreatmentStatus.ACTIVE,
        reminderPreference = MedicationReminderPreference.NONE,
        secondReminderEnabled = false,
        reminderEffectiveState =
            MedicationReminderEffectiveState.NOT_REQUESTED,
        administrations = administrations,
    )

    private fun plan(
        id: String,
        configuration: MedicationConfiguration,
    ): MedicationPlan {
        val revisionId = "$id-revision"
        return MedicationPlan(
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
                    unitOrPresentation = configuration.form.name,
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
}
