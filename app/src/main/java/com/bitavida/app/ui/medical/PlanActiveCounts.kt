package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.ObservationSnapshot
import com.bitavida.app.domain.model.applicableRevision
import com.bitavida.app.domain.usecase.activeCareReminders
import java.time.LocalDate
import java.time.LocalDateTime

/** Counts configuration identities, never doses, results or historical revisions. */
internal data class PlanActiveCounts(
    val medications: Int,
    val measurements: Int,
    val observations: Int,
    val reminders: Int,
)

internal fun countPlanActiveItems(
    overview: MedicalCareOverview,
    glucose: GlucoseFollowUp?,
    bloodPressure: GlucoseFollowUp?,
    weight: GlucoseFollowUp?,
    observations: ObservationSnapshot,
    reminders: List<CareReminder>,
    personUnderCareId: String,
    now: LocalDateTime,
): PlanActiveCounts {
    val today = now.toLocalDate()
    val medicationCount = currentMedicationPeriodsForList(
        overview.medicationPlans.filter { it.personUnderCareId == personUnderCareId }, now,
    ) { plan ->
        overview.indications.singleOrNull {
            it.id == plan.medicalIndicationId && it.personUnderCareId == personUnderCareId
        }?.currentRevision?.status
    }.count { it.second == MedicationPlanDisplayState.CURRENT }
    return PlanActiveCounts(
        medications = medicationCount,
        measurements = listOf(glucose, bloodPressure, weight).count {
            it?.isCurrentForPlan(personUnderCareId, today) == true
        },
        observations = observations.followUps.asSequence()
            .filter { it.schedule.isCurrentForPlan(personUnderCareId, today) }
            .map { it.observationId }.distinct().count(),
        reminders = activeCareReminders(reminders, personUnderCareId).size,
    )
}

private fun GlucoseFollowUp.isCurrentForPlan(personUnderCareId: String, today: LocalDate): Boolean {
    if (this.personUnderCareId != personUnderCareId) return false
    val revision = applicableRevision(today) ?: return false
    return !revision.startsOn.isAfter(today) && revision.endsOn?.isBefore(today) != true
}
