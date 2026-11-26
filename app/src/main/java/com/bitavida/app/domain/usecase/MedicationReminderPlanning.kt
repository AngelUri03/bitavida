package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderPreference
import java.time.LocalDateTime

data class PlannedMedicationReminder(
    val id: String,
    val occurrenceId: String,
    val medicationPlanId: String,
    val medicationName: String,
    val form: MedicationProductForm,
    val amount: MedicationAmount,
    val scheduledAt: LocalDateTime,
    val preference: MedicationReminderPreference,
)

fun planMedicationReminders(
    plans: List<MedicationPlan>,
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
    limit: Int = 256,
): List<PlannedMedicationReminder> {
    if (limit <= 0 || rangeEnd.isBefore(rangeStart)) return emptyList()
    val reminderPlans = plans.filter { plan ->
        plan.currentRevision.configuration?.reminderPreference
            ?.let { it != MedicationReminderPreference.NONE } == true
    }
    val reminderPreferences = reminderPlans.associate { plan ->
        plan.id to plan.currentRevision.configuration?.reminderPreference
    }
    return projectMedicationAdministrations(
        plans = reminderPlans,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        limit = Int.MAX_VALUE,
    ).asSequence()
        .mapNotNull { occurrence ->
            val preference =
                reminderPreferences[occurrence.medicationPlanId]
                    ?.takeUnless { it == MedicationReminderPreference.NONE }
                    ?: return@mapNotNull null
            val scheduledAt = occurrence.reminderAt ?: return@mapNotNull null
            if (scheduledAt.isBefore(rangeStart) || scheduledAt.isAfter(rangeEnd)) {
                return@mapNotNull null
            }
            PlannedMedicationReminder(
                id = occurrence.id + ":primary",
                occurrenceId = occurrence.id,
                medicationPlanId = occurrence.medicationPlanId,
                medicationName = occurrence.medicationName,
                form = occurrence.form,
                amount = occurrence.amount,
                scheduledAt = scheduledAt,
                preference = preference,
            )
        }
        .distinctBy(PlannedMedicationReminder::id)
        .sortedWith(
            compareBy<PlannedMedicationReminder>(
                PlannedMedicationReminder::scheduledAt,
            ).thenBy(PlannedMedicationReminder::id),
        )
        .take(limit)
        .toList()
}
