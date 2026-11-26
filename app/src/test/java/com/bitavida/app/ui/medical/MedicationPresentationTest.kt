package com.bitavida.app.ui.medical

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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MedicationPresentationTest {
    @Test
    fun everyStoredProductFormHasAUserFacingSpanishName() {
        MedicationProductForm.entries.forEach { form ->
            val label = form.displayName()

            assertFalse(label.isBlank())
            assertFalse(label == form.name)
            assertEquals(label, form.name.medicationPresentationDisplayName())
        }
    }

    @Test
    fun summariesUseTheEffectiveStoredParameters() {
        assertEquals(
            "Lunes, miércoles y viernes",
            configuration(
                frequency = MedicationFrequencyKind.SPECIFIC_DAYS,
                weekdays = setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.FRIDAY,
                ),
            ).frequencySummary(),
        )
        assertEquals(
            "Cada 6 horas",
            configuration(
                frequency = MedicationFrequencyKind.EVERY_N_HOURS,
                hourlyInterval = 6,
            ).frequencySummary(),
        )
        assertEquals(
            "Por ciclos: 3 días activos y 2 de descanso",
            configuration(
                frequency = MedicationFrequencyKind.CYCLE,
                cycle = MedicationCycle(
                    activeDays = 3,
                    restDays = 2,
                    referenceDate = LocalDate.of(2026, 11, 11),
                ),
            ).frequencySummary(),
        )
    }

    @Test
    fun detailStateFollowsPersistedImmediateLifecycleTransitions() {
        val start = LocalDate.of(2026, 11, 17)
        val suspendedAt = start.plusDays(1).atTime(8, 0)
        val reactivatedAt = suspendedAt.plusHours(1)
        val plan = planWithTransitions(
            startsOn = start,
            transitions = listOf(
                MedicationPlanChangeKind.SUSPENDED to suspendedAt,
                MedicationPlanChangeKind.REACTIVATED to reactivatedAt,
            ),
        )

        assertEquals(
            MedicationPlanDisplayState.UPCOMING,
            plan.displayState(start.minusDays(1).atTime(12, 0), null),
        )
        assertEquals(
            MedicationPlanDisplayState.CURRENT,
            plan.displayState(start.atTime(12, 0), null),
        )
        assertEquals(
            MedicationPlanDisplayState.UPCOMING,
            plan.displayState(suspendedAt.plusMinutes(1), null),
        )
        assertEquals(
            MedicationPlanDisplayState.CURRENT,
            plan.displayState(reactivatedAt, null),
        )
    }

    @Test
    fun scheduledReactivationIsPresentedAsUpcomingUntilItsEffectiveInstant() {
        val start = LocalDate.of(2026, 11, 14)
        val suspendedAt = start.atTime(8, 0)
        val scheduledAt = start.plusDays(2).atTime(12, 55)
        val plan = planWithTransitions(
            startsOn = start,
            transitions = listOf(
                MedicationPlanChangeKind.SUSPENDED to suspendedAt,
                MedicationPlanChangeKind.REACTIVATED to scheduledAt,
            ),
        )

        assertEquals(
            MedicationPlanDisplayState.UPCOMING,
            plan.displayState(scheduledAt.minusNanos(1), null),
        )
        assertEquals(
            MedicationPlanDisplayState.CURRENT,
            plan.displayState(scheduledAt, null),
        )
    }

    private fun planWithTransitions(
        startsOn: LocalDate,
        transitions: List<Pair<MedicationPlanChangeKind, LocalDateTime>>,
    ): MedicationPlan {
        val zoneId = ZoneId.systemDefault()
        val activeConfiguration = configuration(
            frequency = MedicationFrequencyKind.EVERY_DAY,
        ).copy(startsOn = startsOn)
        val created = MedicationPlanRevision(
            id = "revision-created",
            medicationPlanId = "plan",
            revisionNumber = 1,
            medicationName = "Medicamento ficticio",
            doseAsIndicated = "1 tableta",
            unitOrPresentation = "TABLET",
            frequencyAsIndicated = "Todos los días",
            startsOn = startsOn,
            endsOn = null,
            originalAdditionalInstructions = null,
            scheduledDoses = emptyList(),
            capturedAtEpochMillis = startsOn.minusDays(1).atStartOfDay(zoneId)
                .toInstant().toEpochMilli(),
            authorActorId = "caregiver",
            changeKind = MedicationPlanChangeKind.CREATED,
            changeReason = null,
            configuration = activeConfiguration,
        )
        val revisions = transitions.mapIndexed { index, (kind, effectiveAt) ->
            val status = when (kind) {
                MedicationPlanChangeKind.SUSPENDED ->
                    MedicationTreatmentStatus.SUSPENDED
                else -> MedicationTreatmentStatus.ACTIVE
            }
            created.copy(
                id = "revision-${index + 2}",
                revisionNumber = index + 2,
                capturedAtEpochMillis = effectiveAt.atZone(zoneId)
                    .toInstant().toEpochMilli(),
                changeKind = kind,
                changeReason = if (kind == MedicationPlanChangeKind.SUSPENDED) {
                    "Motivo ficticio"
                } else {
                    null
                },
                lifecycleEffectiveAtEpochMillis = effectiveAt.atZone(zoneId)
                    .toInstant().toEpochMilli(),
                configuration = activeConfiguration.copy(status = status),
            )
        }
        return MedicationPlan(
            id = "plan",
            personUnderCareId = "person",
            medicalIndicationId = null,
            medicalContentOrigin =
                InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
            createdAtEpochMillis = created.capturedAtEpochMillis,
            createdByActorId = "caregiver",
            contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
            revisions = listOf(created) + revisions,
        )
    }

    private fun configuration(
        frequency: MedicationFrequencyKind,
        weekdays: Set<DayOfWeek> = emptySet(),
        hourlyInterval: Int? = null,
        cycle: MedicationCycle? = null,
    ) = MedicationConfiguration(
        form = MedicationProductForm.TABLET,
        concentrationValue = null,
        concentrationUnit = null,
        baseAmount = MedicationAmount("1", "tableta"),
        frequency = frequency,
        weekdays = weekdays,
        hourlyInterval = hourlyInterval,
        cycle = cycle,
        startsOn = LocalDate.of(2026, 11, 11),
        endsOn = null,
        status = MedicationTreatmentStatus.ACTIVE,
        reminderPreference = MedicationReminderPreference.NONE,
        secondReminderEnabled = false,
        reminderEffectiveState = MedicationReminderEffectiveState.NOT_REQUESTED,
        administrations = if (
            frequency == MedicationFrequencyKind.EVERY_N_HOURS ||
            frequency == MedicationFrequencyKind.AS_NEEDED
        ) {
            emptyList()
        } else {
            listOf(
                MedicationAdministration(
                    id = "administration",
                    timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(8, 0),
                ),
            )
        },
    )
}
