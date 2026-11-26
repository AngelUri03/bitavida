package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationPlan
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Expected administrations reconstructed from recorded versions, never clinical facts.
 * A response occupies a slot only if its persisted occurrence ID resolves to a matching
 * historical snapshot. Missing or ambiguous legacy references are never matched by time.
 */
internal data class ReportMedicationExpectedOccurrences(
    val unmatched: List<UpcomingMedicationAdministration>,
    val inProgress: List<UpcomingMedicationAdministration>,
    val plansWithUnverifiableRecords: Set<String>,
    val plansWithoutReconstructibleSchedules: Set<String>,
    /** Only exact persisted occurrence references resolved against a historical snapshot. */
    val resolvedByRecordId: Map<String, UpcomingMedicationAdministration>,
)

internal fun reportUnconfirmedMedicationOccurrences(
    plans: Collection<MedicationPlan>,
    records: List<MedicationIntakeRecord>,
    start: LocalDateTime,
    cutoff: LocalDateTime,
    zone: ZoneId,
): ReportMedicationExpectedOccurrences {
    if (cutoff.isBefore(start)) return ReportMedicationExpectedOccurrences(emptyList(), emptyList(), emptySet(), emptySet(), emptyMap())
    fun time(epoch: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone)
    fun UpcomingMedicationAdministration.key() = listOf(
        medicationPlanId, administrationId, startsAt, endsAt, timing,
    )
    val unverifiablePlans = mutableSetOf<String>()
    val unreconstructiblePlans = mutableSetOf<String>()
    val resolvedByRecord = mutableMapOf<String, UpcomingMedicationAdministration>()
    val unmatched = plans.flatMap { plan ->
        val known = plan.revisions.filter { !time(it.capturedAtEpochMillis).isAfter(cutoff) }
        if (known.isEmpty()) return@flatMap emptyList()
        val expected = known.indices.flatMap { index ->
            val from = maxOf(start, time(known[index].capturedAtEpochMillis))
            val until = known.getOrNull(index + 1)?.let {
                minOf(cutoff, time(it.capturedAtEpochMillis).minusNanos(1))
            } ?: cutoff
            if (!until.isBefore(from) && known[index].configuration == null &&
                !known[index].startsOn.isAfter(until.toLocalDate()) &&
                known[index].endsOn?.isBefore(from.toLocalDate()) != true) {
                unreconstructiblePlans += plan.id
            }
            if (until.isBefore(from)) emptyList() else
                projectMedicationAdministrations(listOf(plan.copy(revisions = known.take(index + 1))),
                    from, until, Int.MAX_VALUE, zone)
                    .filter { !it.startsAt.isBefore(from) && !it.startsAt.isAfter(until) }
        }.distinctBy { it.key() }
        val aliasesByDay = mutableMapOf<java.time.LocalDate, Map<String, UpcomingMedicationAdministration>>()
        val occupied = records.asSequence().filter { it.medicationPlanId == plan.id }.mapNotNull { record ->
            val revision = record.revisions.lastOrNull { !time(it.capturedAtEpochMillis).isAfter(cutoff) }
                ?: return@mapNotNull null
            if (revision.scheduledAt.isBefore(start) || revision.scheduledAt.isAfter(cutoff))
                return@mapNotNull null
            val reference = revision.scheduledOccurrenceId
            if (reference == null) {
                unverifiablePlans += plan.id
                return@mapNotNull null
            }
            val day = revision.scheduledAt.toLocalDate()
            val aliases = aliasesByDay.getOrPut(day) {
                known.indices.flatMap { index ->
                    projectMedicationAdministrations(listOf(plan.copy(revisions = known.take(index + 1))),
                        day.atStartOfDay(), day.atTime(23, 59, 59), Int.MAX_VALUE, zone)
                }.associateBy { it.id }
            }
            val linked = aliases[reference]?.takeIf { it.startsAt == revision.scheduledAt }
            if (linked == null) {
                unverifiablePlans += plan.id
                return@mapNotNull null
            }
            resolvedByRecord[record.id] = linked
            linked.key()
        }.toSet()
        // Historical migrated slots can have a different physical ID in each revision.
        // If a response resolves to an older occurrence that is absent from the
        // effective projection, continuity with a newer slot is not proven. Mark
        // this plan uncertain instead of asserting those newer slots are pending.
        val expectedKeys = expected.mapTo(mutableSetOf()) { it.key() }
        if (occupied.any { it !in expectedKeys }) unverifiablePlans += plan.id
        expected.filter { it.key() !in occupied }
    }.sortedWith(compareBy<UpcomingMedicationAdministration> { it.startsAt }.thenBy { it.id })
    val (openWindows, elapsed) = unmatched.partition { it.endsAt?.isAfter(cutoff) == true }
    return ReportMedicationExpectedOccurrences(elapsed, openWindows, unverifiablePlans, unreconstructiblePlans,
        resolvedByRecord.toMap())
}
