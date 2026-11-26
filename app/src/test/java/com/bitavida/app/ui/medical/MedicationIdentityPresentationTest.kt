package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.groupByMedicationIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedicationIdentityPresentationTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val now = LocalDateTime.of(today, LocalTime.NOON)

    @Test
    fun exactNormalizedLegacyDuplicatesAreOneIdentityWithoutRewritingPeriods() {
        val first = plan(
            id = "pantoprazol-old",
            seriesId = "legacy-series-a",
            name = "Pantoprazol",
            startsOn = today.minusDays(20),
            endsOn = today.minusDays(10),
            status = MedicationTreatmentStatus.COMPLETED,
            capturedAt = 1L,
        )
        val second = plan(
            id = "pantoprazol-current",
            seriesId = "legacy-series-b",
            name = "  PANTOPRAZOL ",
            startsOn = today.minusDays(2),
            endsOn = today.plusDays(10),
            status = MedicationTreatmentStatus.ACTIVE,
            capturedAt = 2L,
        )

        val groups = listOf(first, second).groupByMedicationIdentity()
        val visible = currentMedicationPeriodsForList(
            plans = listOf(first, second),
            at = now,
            indicationStatus = { null },
        )

        assertEquals(1, groups.size)
        assertEquals(setOf(first.id, second.id), groups.single().map { it.id }.toSet())
        assertEquals(1, visible.size)
        assertEquals(second.id, visible.single().first.id)
        assertEquals(MedicationPlanDisplayState.CURRENT, visible.single().second)
    }

    @Test
    fun oneRepresentativeIsSelectedForEachOfTheFourOperationalSections() {
        val plans = listOf(
            plan(
                "current",
                "current",
                "Medicamento vigente",
                today.minusDays(1),
                today.plusDays(5),
                MedicationTreatmentStatus.ACTIVE,
                1L,
            ),
            plan(
                "upcoming",
                "upcoming",
                "Medicamento futuro",
                today.plusDays(2),
                today.plusDays(8),
                MedicationTreatmentStatus.ACTIVE,
                2L,
            ),
            plan(
                "suspended",
                "suspended",
                "Medicamento suspendido",
                today.minusDays(5),
                today.plusDays(8),
                MedicationTreatmentStatus.SUSPENDED,
                3L,
            ),
            plan(
                "completed",
                "completed",
                "Medicamento finalizado",
                today.minusDays(10),
                today.minusDays(1),
                MedicationTreatmentStatus.COMPLETED,
                4L,
            ),
        )

        assertEquals(
            setOf(
                MedicationPlanDisplayState.CURRENT,
                MedicationPlanDisplayState.UPCOMING,
                MedicationPlanDisplayState.SUSPENDED,
                MedicationPlanDisplayState.COMPLETED,
            ),
            currentMedicationPeriodsForList(
                plans = plans,
                at = now,
                indicationStatus = { null },
            ).map { it.second }.toSet(),
        )
    }

    @Test
    fun unifiedHistoryKeepsEveryRevisionAndMarksLaterPeriod() {
        val old = plan(
            "old",
            "series",
            "Medicamento ficticio",
            today.minusDays(20),
            today.minusDays(10),
            MedicationTreatmentStatus.COMPLETED,
            1L,
        )
        val current = plan(
            "new",
            "series",
            "Medicamento ficticio",
            today,
            today.plusDays(10),
            MedicationTreatmentStatus.ACTIVE,
            2L,
        )

        val entries = medicationHistoryEntries(listOf(old, current))

        assertEquals(
            old.revisions.size + current.revisions.size,
            entries.size,
        )
        assertEquals(setOf(1, 2), entries.map { it.periodNumber }.toSet())
        assertTrue(
            entries.any {
                it.periodNumber == 2 &&
                    it.revision.medicationPlanId == current.id &&
                    it.previous == null
            },
        )
    }

    private fun plan(
        id: String,
        seriesId: String,
        name: String,
        startsOn: LocalDate,
        endsOn: LocalDate?,
        status: MedicationTreatmentStatus,
        capturedAt: Long,
    ): MedicationPlan {
        val configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET,
            concentrationValue = null,
            concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"),
            frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = startsOn,
            endsOn = endsOn,
            status = status,
            administrations = listOf(
                MedicationAdministration(
                    id = "administration-$id",
                    timing = MedicationAdministrationTiming.EXACT_TIME,
                    exactTime = LocalTime.of(8, 0),
                ),
            ),
        )
        return MedicationPlan(
            id = id,
            medicationSeriesId = seriesId,
            personUnderCareId = "person",
            medicalIndicationId = null,
            medicalContentOrigin =
                InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
            createdAtEpochMillis = capturedAt,
            createdByActorId = "caregiver",
            contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
            revisions = listOf(
                MedicationPlanRevision(
                    id = "revision-$id",
                    medicationPlanId = id,
                    revisionNumber = 1,
                    medicationName = name,
                    doseAsIndicated = "1 tableta",
                    unitOrPresentation = "TABLET",
                    frequencyAsIndicated = "Todos los días",
                    startsOn = startsOn,
                    endsOn = endsOn,
                    originalAdditionalInstructions = null,
                    scheduledDoses = emptyList(),
                    capturedAtEpochMillis = capturedAt,
                    authorActorId = "caregiver",
                    changeKind = MedicationPlanChangeKind.CREATED,
                    changeReason = null,
                    configuration = configuration,
                ),
            ),
        )
    }
}
