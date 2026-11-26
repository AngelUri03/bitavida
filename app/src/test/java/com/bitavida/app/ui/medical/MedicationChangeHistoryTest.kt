package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.MedicationActivityRelation
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderPreference
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationChangeHistoryTest {
    @Test
    fun removingOneOfSixWithRegeneratedIdsReportsOnlyTheRemoval() {
        val times = listOf(2, 6, 10, 14, 18, 22).map { LocalTime.of(it, 0) }
        val previousAdministrations = times.mapIndexed { index, time ->
            exactAdministration(id = "old-$index", time = time)
        }
        val currentAdministrations = times
            .filterNot { it == LocalTime.of(2, 0) }
            .reversed()
            .mapIndexed { index, time ->
                exactAdministration(id = "new-$index", time = time)
            }

        val changes = medicationFieldChanges(
            previous = revision(
                id = "revision-1",
                revisionNumber = 1,
                changeKind = MedicationPlanChangeKind.CREATED,
                administrations = previousAdministrations,
            ),
            current = revision(
                id = "revision-2",
                revisionNumber = 2,
                changeKind = MedicationPlanChangeKind.SCHEDULE_CORRECTED,
                administrations = currentAdministrations,
            ),
        )

        assertEquals(1, changes.size)
        assertEquals("Administración retirada", changes.single().field)
        assertEquals("02:00", changes.single().before)
        assertNull(changes.single().after)
        assertFalse(changes.any { it.field == "Administración añadida" })
    }

    @Test
    fun changingOnlyActivityNoticeWithRegeneratedIdsReportsOneNoticeChange() {
        val previousAdministrations = listOf(
            exactAdministration(id = "old-morning", time = LocalTime.of(8, 0)),
            beforeSleepAdministration(id = "old-sleep", notice = LocalTime.of(12, 0)),
        )
        val currentAdministrations = listOf(
            beforeSleepAdministration(id = "new-sleep", notice = LocalTime.of(23, 59)),
            exactAdministration(id = "new-morning", time = LocalTime.of(8, 0)),
        )

        val changes = medicationFieldChanges(
            previous = revision(
                id = "revision-1",
                revisionNumber = 1,
                changeKind = MedicationPlanChangeKind.CREATED,
                administrations = previousAdministrations,
            ),
            current = revision(
                id = "revision-2",
                revisionNumber = 2,
                changeKind = MedicationPlanChangeKind.SCHEDULE_CORRECTED,
                administrations = currentAdministrations,
            ),
        )

        assertEquals(1, changes.size)
        assertEquals("Hora del aviso", changes.single().field)
        assertEquals("12:00", changes.single().before)
        assertEquals("23:59", changes.single().after)
    }

    private fun exactAdministration(
        id: String,
        time: LocalTime,
    ) = MedicationAdministration(
        id = id,
        timing = MedicationAdministrationTiming.EXACT_TIME,
        exactTime = time,
        reminderEnabled = true,
    )

    private fun beforeSleepAdministration(
        id: String,
        notice: LocalTime,
    ) = MedicationAdministration(
        id = id,
        timing = MedicationAdministrationTiming.ACTIVITY_ONLY,
        activityRelation = MedicationActivityRelation.BEFORE_SLEEP,
        activityNoticeTime = notice,
    )

    private fun revision(
        id: String,
        revisionNumber: Int,
        changeKind: MedicationPlanChangeKind,
        administrations: List<MedicationAdministration>,
    ) = MedicationPlanRevision(
        id = id,
        medicationPlanId = "plan-1",
        revisionNumber = revisionNumber,
        medicationName = "Medicamento de prueba",
        doseAsIndicated = "1 tableta",
        unitOrPresentation = "Tableta",
        frequencyAsIndicated = "Todos los días",
        startsOn = LocalDate.of(2026, 11, 1),
        endsOn = null,
        originalAdditionalInstructions = null,
        scheduledDoses = emptyList(),
        capturedAtEpochMillis = 1_000L * revisionNumber,
        authorActorId = "caregiver-1",
        changeKind = changeKind,
        changeReason = if (changeKind == MedicationPlanChangeKind.CREATED) null else "Corrección",
        configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET,
            concentrationValue = "850",
            concentrationUnit = "mg",
            baseAmount = MedicationAmount("1", "tableta"),
            frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = LocalDate.of(2026, 11, 1),
            reminderPreference = MedicationReminderPreference.NORMAL,
            administrations = administrations,
        ),
    )
}
