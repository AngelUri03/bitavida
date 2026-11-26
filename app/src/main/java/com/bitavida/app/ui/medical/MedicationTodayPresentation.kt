package com.bitavida.app.ui.medical

import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.domain.usecase.projectMedicationAdministrations
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

internal fun MedicalPlanUiState.isCurrentMedicationDetail(
    now: LocalDateTime,
): Boolean {
    val plan = selectedMedicationPlan ?: return false
    val indication = overview.indications.singleOrNull {
        it.id == plan.medicalIndicationId
    }
    return plan.displayState(now, indication?.currentRevision?.status) ==
        MedicationPlanDisplayState.CURRENT
}

internal fun MedicalPlanUiState.usesApprovedMedicationDetailLayout(
    now: LocalDateTime,
): Boolean {
    val plan = selectedMedicationPlan ?: return false
    val indication = overview.indications.singleOrNull {
        it.id == plan.medicalIndicationId
    }
    return plan.displayState(now, indication?.currentRevision?.status) in setOf(
        MedicationPlanDisplayState.CURRENT,
        MedicationPlanDisplayState.UPCOMING,
        MedicationPlanDisplayState.SUSPENDED,
    )
}

internal fun medicationOccurrencesForDay(plan: MedicationPlan, day: LocalDate) =
    projectMedicationAdministrations(
        listOf(plan), day.minusDays(1).atStartOfDay(),
        day.plusDays(1).atStartOfDay().minusNanos(1), Int.MAX_VALUE,
    ).filter {
        it.startsAt.toLocalDate() == day ||
            (it.startsAt.isBefore(day.atStartOfDay()) &&
                it.endsAt?.isAfter(day.atStartOfDay()) == true)
    }

internal fun medicationPlanSnapshotForDay(
    plan: MedicationPlan,
    day: LocalDate,
): MedicationPlan {
    val cutoff = day.plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli() - 1
    val revisionIndex = plan.revisions.indexOfLast {
        it.capturedAtEpochMillis <= cutoff
    }.takeIf { it >= 0 } ?: 0
    return plan.copy(revisions = plan.revisions.take(revisionIndex + 1))
}

internal fun noMedicationAdministrationsMessage(plan: MedicationPlan, day: LocalDate): String {
    val configuration = plan.currentRevision.configuration
    val cycle = configuration?.cycle
    if (configuration?.frequency == com.bitavida.app.domain.model.MedicationFrequencyKind.CYCLE && cycle != null) {
        val offset = java.time.temporal.ChronoUnit.DAYS.between(cycle.referenceDate, day)
        val length = cycle.activeDays.toLong() + cycle.restDays.toLong()
        if (offset >= 0 && (cycle.numberOfCycles == null || offset / length < cycle.numberOfCycles)) {
            if (offset % length >= cycle.activeDays) return "Hoy es día de descanso del ciclo."
        }
        return "Hoy no hay administraciones dentro de los ciclos configurados."
    }
    return "Hoy no corresponden administraciones según la programación registrada."
}

internal fun MedicationIntakeRecord.hasDeclaredResponse(): Boolean =
    currentRevision.outcome != MedicationIntakeOutcome.UNCONFIRMED

internal fun intakeForOccurrence(
    records: List<MedicationIntakeRecord>,
    occurrence: UpcomingMedicationAdministration,
    occurrences: List<UpcomingMedicationAdministration> = listOf(occurrence),
    plan: MedicationPlan? = null,
): MedicationIntakeRecord? {
    return intakesForOccurrence(records, occurrence, occurrences, plan).singleOrNull()
}

/** Return every declaration supported by the persisted occurrence identity.
 * Identical times alone do not identify a slot or a revision.
 */
internal fun intakesForOccurrence(
    records: List<MedicationIntakeRecord>,
    occurrence: UpcomingMedicationAdministration,
    occurrences: List<UpcomingMedicationAdministration> = listOf(occurrence),
    plan: MedicationPlan? = null,
): List<MedicationIntakeRecord> {
    if (occurrences.none { it.id == occurrence.id }) return emptyList()
    val aliases = mutableSetOf(occurrence.id)
    if (plan != null) {
        plan.revisions.indices.forEach { index ->
            val snapshot = plan.copy(revisions = plan.revisions.take(index + 1))
            projectMedicationAdministrations(
                listOf(snapshot), occurrence.startsAt, occurrence.startsAt.plusSeconds(1), Int.MAX_VALUE,
            ).filter {
                it.medicationPlanId == occurrence.medicationPlanId &&
                    it.administrationId == occurrence.administrationId &&
                    it.startsAt == occurrence.startsAt && it.endsAt == occurrence.endsAt &&
                    it.timing == occurrence.timing
            }.forEach { aliases += it.id }
        }
    }
    return records.distinctBy { it.id }.filter {
        it.medicationPlanId == occurrence.medicationPlanId &&
            it.currentRevision.scheduledAt == occurrence.startsAt &&
            it.currentRevision.scheduledOccurrenceId in aliases
    }
}

// A time-based key survives a reload and corrections of the medication's display data.
internal fun todaySlotKey(
    occurrence: UpcomingMedicationAdministration,
    occurrences: List<UpcomingMedicationAdministration>,
): String = if (occurrences.count { it.startsAt == occurrence.startsAt } == 1) {
    "slot:${occurrence.startsAt}"
} else occurrence.id

internal fun initialMedicationTodayKey(
    occurrences: List<UpcomingMedicationAdministration>,
    records: List<MedicationIntakeRecord>,
    now: LocalDateTime,
    resolvedRecords: Map<String, MedicationIntakeRecord?>? = null,
): String? {
    val pending = occurrences.filter {
        val record = if (resolvedRecords != null) resolvedRecords[it.id]
            else intakeForOccurrence(records, it, occurrences)
        record?.hasDeclaredResponse() != true &&
            intakesForOccurrence(records, it, occurrences).none { candidate -> candidate.hasDeclaredResponse() }
    }
    val inProgress = pending.firstOrNull {
        !it.startsAt.isAfter(now) && it.endsAt?.isBefore(now) == false
    }
    val chosen = inProgress ?: pending.minWithOrNull(
        compareBy<UpcomingMedicationAdministration> {
            abs(Duration.between(it.startsAt, now).seconds)
        }.thenBy { it.startsAt.isAfter(now) },
    ) ?: occurrences.lastOrNull()
    return chosen?.let { todaySlotKey(it, occurrences) }
}
