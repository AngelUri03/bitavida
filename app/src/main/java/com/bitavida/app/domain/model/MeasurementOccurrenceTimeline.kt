package com.bitavida.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A logical schedule occurrence, independent of administrative revision IDs.
 * Equal clock times alone never identify two events: the explicit slot ID is required.
 * Time changes on the same slot remain different occurrences.
 */
private data class MeasurementOccurrenceKey(
    val followUpId: String,
    val slotId: String,
    val scheduledAt: LocalDateTime,
    val timingType: GlucoseFollowUpTimingType,
    val windowEndAt: LocalDateTime?,
)

private fun GlucoseScheduledOccurrence.logicalKey() = MeasurementOccurrenceKey(
    followUpId, slotId, scheduledAt, timingType, windowEndAt,
)

/** A new configuration cannot implicitly create events before it was captured. */
private val GlucoseFollowUpRevision.effectiveAt: LocalDateTime
    get() = maxOf(effectiveFrom.atStartOfDay(), capturedAt)

/** One boundary per effective instant; older future revisions cannot supersede newer ones. */
private class MeasurementScheduleValidity(revisions: List<GlucoseFollowUpRevision>) {
    private val boundaries = buildList {
        var latestNumber = 0
        revisions.groupBy { it.effectiveAt }.toSortedMap().forEach { (at, versions) ->
            val newest = versions.maxBy { it.revisionNumber }
            if (newest.revisionNumber > latestNumber) {
                add(at to newest)
                latestNumber = newest.revisionNumber
            }
        }
    }
    private val removals = boundaries.filter { !it.second.active }.map { it.first }

    fun revisionAt(at: LocalDateTime): GlucoseFollowUpRevision? {
        val index = boundaries.binarySearchBy(at) { it.first }
        return boundaries.getOrNull(if (index >= 0) index else -index - 2)?.second
    }

    fun cancelsOpenWindow(occurrence: GlucoseScheduledOccurrence): Boolean {
        val end = occurrence.windowEndAt ?: return false
        val index = removals.binarySearch(occurrence.scheduledAt)
        val next = removals.getOrNull(if (index >= 0) index + 1 else -index - 1)
        return next != null && next < end
    }
}

/**
 * Historical eligibility is evaluated at each scheduled start, rather than by replaying
 * every old template as if it had been active for the entire date.
 */
private fun GlucoseFollowUp.validOccurrencesOn(
    date: LocalDate,
): List<GlucoseScheduledOccurrence> {
    val validity = MeasurementScheduleValidity(revisions)
    return revisions.flatMap { revision ->
        revision.declaredOccurrencesOn(date).filter { occurrence ->
            date >= revision.startsOn && (revision.endsOn == null || date <= revision.endsOn) &&
                validity.revisionAt(occurrence.scheduledAt)?.id == revision.id &&
                !validity.cancelsOpenWindow(occurrence)
        }
    }.sortedWith(compareBy<GlucoseScheduledOccurrence> { it.scheduledAt }.thenBy { it.id })
}

/**
 * Single normalization used by all three types, both histories and today's schedule.
 * Stored facts retain their original references. Reading > explicit declaration > valid schedule.
 * This is a pure read: it never inserts, rewrites or deletes persisted facts.
 */
fun projectMeasurementOccurrences(
    date: LocalDate,
    followUp: GlucoseFollowUp?,
    records: List<MeasurementRecord>,
    decisions: List<GlucoseOccurrenceDecision>,
    kind: MeasurementKind,
): List<GlucoseTodayOccurrence> {
    if (followUp == null) return emptyList()
    val aliases = followUp.revisions.flatMap { it.declaredOccurrencesOn(date) }
        .associateBy { it.id }
    val valid = followUp.validOccurrencesOn(date).associateBy { it.logicalKey() }
    val readings = records.asSequence().filter { it.kind == kind }.mapNotNull { record ->
        val reference = record.scheduledOccurrenceId() ?: return@mapNotNull null
        aliases[reference]?.let { it.logicalKey() to record }
    }.groupBy({ it.first }, { it.second })
    val declared = decisions.asSequence()
        .filter { it.followUpId == followUp.id && it.scheduledAt.toLocalDate() == date }
        .mapNotNull { decision ->
            aliases[decision.occurrenceId]
                ?.takeIf { it.followUpRevisionId == decision.followUpRevisionId && it.scheduledAt == decision.scheduledAt }
                ?.let { it.logicalKey() to decision }
        }.groupBy({ it.first }, { it.second })
        .mapValues { (_, versions) ->
            versions.maxWith(compareBy<GlucoseOccurrenceDecision> { it.capturedAt }.thenBy { it.id })
        }
    // Explicit facts remain visible even if the latest template no longer contains their slot.
    return (valid.keys + declared.keys + readings.keys).map { identity ->
        val record = readings[identity]?.maxWithOrNull(
            compareBy<MeasurementRecord> { it.occurredAt }.thenBy { it.capturedAt }.thenBy { it.id },
        )
        val decision = declared[identity]
        when {
            record != null -> GlucoseTodayOccurrence(
                occurrence = aliases.getValue(requireNotNull(record.scheduledOccurrenceId())),
                state = GlucoseOccurrenceState.RECORDED,
                record = record,
            )
            decision != null -> GlucoseTodayOccurrence(
                occurrence = aliases.getValue(decision.occurrenceId),
                state = GlucoseOccurrenceState.NOT_PERFORMED,
                decision = decision,
            )
            else -> GlucoseTodayOccurrence(
                occurrence = valid.getValue(identity),
                state = GlucoseOccurrenceState.UNCONFIRMED,
            )
        }
    }.sortedWith(compareBy<GlucoseTodayOccurrence> { it.occurrence.scheduledAt }.thenBy { it.occurrence.id })
}

/** All actual records remain separate, including distinct readings linked to legacy aliases. */
internal fun projectMeasurementToday(
    date: LocalDate,
    followUp: GlucoseFollowUp?,
    records: List<MeasurementRecord>,
    decisions: List<GlucoseOccurrenceDecision>,
    kind: MeasurementKind,
): GlucoseTodaySummary {
    val scheduled = projectMeasurementOccurrences(date, followUp, records, decisions, kind)
    val represented = scheduled.mapNotNull { it.record?.id }.toSet()
    return GlucoseTodaySummary(
        scheduled,
        records.filter { it.kind == kind && it.occurredAt.toLocalDate() == date && it.id !in represented }
            .sortedWith(compareBy<MeasurementRecord> { it.occurredAt }.thenBy { it.id }),
    )
}

fun MeasurementRecord.scheduledOccurrenceId(): String? = if (historicalAssociationUncertain) null else when (kind) {
    MeasurementKind.CAPILLARY_GLUCOSE -> glucoseMetadata?.scheduledOccurrenceId
    MeasurementKind.BLOOD_PRESSURE -> bloodPressureMetadata?.scheduledOccurrenceId
    MeasurementKind.WEIGHT -> weightMetadata?.scheduledOccurrenceId
}
