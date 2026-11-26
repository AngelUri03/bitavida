package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanActiveCountsTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val person = "fictional-person"

    @Test fun medicationsCountCurrentIdentitiesWithoutDosesSuspensionsOrFuturePeriods() {
        val current = medication("current")
        val sameIdentity = medication("another-period").copy(medicationSeriesId = current.medicationSeriesId)
        val suspended = medication("suspended", MedicationTreatmentStatus.SUSPENDED)
        val upcoming = medication("future", starts = now.toLocalDate().plusDays(1))
        val ended = medication("ended", ends = now.toLocalDate().minusDays(1))
        val otherPatient = medication("other").copy(personUnderCareId = "another-fictional-person")
        val counts = counts(plans = listOf(current, sameIdentity, suspended, upcoming, ended, otherPatient))
        assertEquals(1, counts.medications)
        assertEquals(0, counts(plans = listOf(suspended, upcoming, ended)).medications)
    }

    @Test fun followUpsCountTypesEvenWithoutAnOccurrenceTodayAndExcludeInactiveFutureAndOtherPatients() {
        // Thursday: a Monday-only configuration is nevertheless a currently active follow-up.
        val weekly = followUp("weekly").let { schedule ->
            schedule.copy(revisions = listOf(schedule.currentRevision.copy(
                frequency = GlucoseFollowUpFrequency.SPECIFIC_DAYS,
                weekdays = setOf(DayOfWeek.MONDAY),
            )))
        }
        val removed = followUp("removed", removed = true)
        val future = followUp("future", starts = now.toLocalDate().plusDays(1))
        val observations = ObservationSnapshot(followUps = listOf(
            ObservationFollowUp("OBS-001", weekly),
            ObservationFollowUp("OBS-001", followUp("same-observation")),
            ObservationFollowUp("OBS-002", removed),
            ObservationFollowUp("OBS-003", future),
            ObservationFollowUp("OBS-004", followUp("other").copy(personUnderCareId = "other-person")),
        ))
        assertEquals(1, counts(glucose = weekly, pressure = removed, weight = future,
            observations = observations).measurements)
        assertEquals(1, counts(observations = observations).observations)
        assertEquals(0, counts(glucose = followUp("ended", ends = now.toLocalDate().minusDays(1))).measurements)
    }

    @Test fun pendingCountIncludesPastTimesAndReactsToExplicitCloseForTheSamePatientOnly() {
        val past = reminder("past", now.minusDays(2))
        val future = reminder("future", now.plusHours(1))
        val done = reminder("done", now.minusDays(1), CareReminderStatus.DONE)
        val discarded = reminder("discarded", now.minusDays(1), CareReminderStatus.DISCARDED)
        val other = reminder("other", now).copy(personUnderCareId = "other-person")
        assertEquals(2, counts(reminders = listOf(past, future, done, discarded, other)).reminders)
        assertEquals(1, counts(reminders = listOf(
            past.copy(status = CareReminderStatus.DONE, closedAt = now), future,
        )).reminders)
    }

    @Test fun effectiveFollowUpRemovalChangesCountWithoutChangingItsHistory() {
        val original = followUp("scheduled")
        val removal = original.currentRevision.copy(id = "removal", revisionNumber = 2,
            effectiveFrom = now.toLocalDate().plusDays(1), changeKind = GlucoseFollowUpChangeKind.REMOVED)
        val schedule = original.copy(revisions = listOf(original.currentRevision, removal))
        assertEquals(1, counts(glucose = schedule).measurements)
        assertEquals(0, counts(glucose = schedule, at = now.plusDays(1)).measurements)
        assertEquals(2, schedule.revisions.size)
    }

    private fun counts(
        plans: List<MedicationPlan> = emptyList(),
        glucose: GlucoseFollowUp? = null,
        pressure: GlucoseFollowUp? = null,
        weight: GlucoseFollowUp? = null,
        observations: ObservationSnapshot = ObservationSnapshot(),
        reminders: List<CareReminder> = emptyList(),
        at: LocalDateTime = now,
    ) = countPlanActiveItems(
        MedicalCareOverview(emptyList(), plans), glucose, pressure, weight,
        observations, reminders, person, at,
    )

    private fun followUp(
        id: String, removed: Boolean = false,
        starts: LocalDate = now.toLocalDate().minusDays(2), ends: LocalDate? = null,
    ): GlucoseFollowUp {
        val revision = GlucoseFollowUpRevision(
            id = "$id-revision", followUpId = id, revisionNumber = 1,
            indication = "Indicación ficticia", frequency = GlucoseFollowUpFrequency.DAILY,
            startsOn = starts, endsOn = ends, effectiveFrom = starts,
            slots = listOf(GlucoseFollowUpSlot("$id-slot", time = LocalTime.NOON,
                suggestedContext = GlucoseContext.OTHER, reminderEnabled = false)),
            remindersEnabled = false,
            changeKind = if (removed) GlucoseFollowUpChangeKind.REMOVED else GlucoseFollowUpChangeKind.CREATED,
            capturedAt = now.minusDays(3), authorActorId = "fictional-caregiver",
        )
        return GlucoseFollowUp(id, person, now.minusDays(3), "fictional-caregiver", listOf(revision))
    }

    private fun medication(
        id: String,
        status: MedicationTreatmentStatus = MedicationTreatmentStatus.ACTIVE,
        starts: LocalDate = now.toLocalDate().minusDays(2),
        ends: LocalDate? = null,
    ): MedicationPlan {
        val configuration = MedicationConfiguration(
            form = MedicationProductForm.TABLET, concentrationValue = null, concentrationUnit = null,
            baseAmount = MedicationAmount("1", "tableta"), frequency = MedicationFrequencyKind.EVERY_DAY,
            startsOn = starts, endsOn = ends, status = status,
            administrations = listOf(MedicationAdministration("$id-dose",
                MedicationAdministrationTiming.EXACT_TIME, exactTime = LocalTime.NOON)),
        )
        val epoch = now.minusDays(3).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val revision = MedicationPlanRevision(
            id = "$id-revision", medicationPlanId = id, revisionNumber = 1,
            medicationName = "Producto ficticio $id", doseAsIndicated = "1 tableta",
            unitOrPresentation = "TABLET", frequencyAsIndicated = "Diaria",
            startsOn = starts, endsOn = ends, originalAdditionalInstructions = null,
            scheduledDoses = emptyList(), capturedAtEpochMillis = epoch,
            authorActorId = "fictional-caregiver", changeKind = MedicationPlanChangeKind.CREATED,
            changeReason = null, configuration = configuration,
        )
        return MedicationPlan(id = id, personUnderCareId = person, medicalIndicationId = null,
            medicalContentOrigin = InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
            scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
            createdAtEpochMillis = epoch, createdByActorId = "fictional-caregiver",
            contentMode = ClinicalContentMode.REAL_CLINICAL_DATA, revisions = listOf(revision))
    }

    private fun reminder(id: String, at: LocalDateTime, status: CareReminderStatus = CareReminderStatus.PENDING) =
        CareReminder(id, person, "Pendiente ficticio", null, at, false, status,
            now.minusDays(3), now, now.takeIf { status != CareReminderStatus.PENDING }, 1)
}
