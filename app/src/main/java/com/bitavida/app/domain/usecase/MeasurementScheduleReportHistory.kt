package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDateTime

data class MeasurementScheduleReportHistory(
    val schedules: List<ScheduleHistory>,
) {
    data class ScheduleHistory(
        val kind: MeasurementKind,
        val followUp: GlucoseFollowUp?,
        val decisions: List<Pair<GlucoseOccurrenceDecision, List<GlucoseOccurrenceDecisionRevision>>>,
    )
}

/** Reconstruct schedules and explicit responses using only versions known at cutoff. */
fun MeasurementScheduleReportHistory.schedulesAt(cutoff: LocalDateTime): List<ReportMeasurementSchedule> =
    schedules.map { history ->
        val knownFollowUp = history.followUp?.let { followUp ->
            check(followUp.revisions.zipWithNext().all { (a, b) -> !b.capturedAt.isBefore(a.capturedAt) })
            val known = followUp.revisions.filter { !it.capturedAt.isAfter(cutoff) }
            if (known.isEmpty()) null else followUp.copy(revisions = known)
        }
        val knownDecisions = history.decisions.mapNotNull { (decision, versions) ->
            check(versions.isNotEmpty() && versions.all {
                it.decisionId == decision.id && it.occurrenceId == decision.occurrenceId
            })
            val ordered = versions.sortedBy { it.revisionNumber }
            check(ordered.map { it.revisionNumber } == (1..ordered.size).toList())
            check(ordered.first().operation == GlucoseOccurrenceDecisionRevisionOperation.CONFIRMED)
            check(ordered.drop(1).none { it.operation == GlucoseOccurrenceDecisionRevisionOperation.CONFIRMED })
            check(ordered.zipWithNext().all { (a, b) -> !b.capturedAt.isBefore(a.capturedAt) })
            val revision = versions.filter { !it.capturedAt.isAfter(cutoff) }
                .maxByOrNull { it.revisionNumber } ?: return@mapNotNull null
            if (revision.operation == GlucoseOccurrenceDecisionRevisionOperation.CORRECTED_TO_RECORDED)
                return@mapNotNull null
            decision.copy(reason = revision.reason, capturedAt = revision.capturedAt, actorId = revision.actorId)
        }
        ReportMeasurementSchedule(history.kind, knownFollowUp, knownDecisions)
    }
