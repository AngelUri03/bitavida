package com.bitavida.app.ui.report

import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.FollowUpReportRow
import java.time.LocalDateTime

/** Display-only references; they do not merge plans, occasions or declarations. */
internal data class ReportMedicationIdentity(
    val planLabels: Map<String, String>,
    val declarationLabels: Map<String, String>,
)

internal fun reportMedicationPlanId(row: FollowUpReportRow): String? =
    row.medicationPlanId ?: row.sourceId
        ?.takeIf { it.startsWith("medication-plan:") }
        ?.removePrefix("medication-plan:")

/**
 * Labels are constructed once from persisted identities for the entire snapshot. Time only
 * determines display order or exposes a visual collision; it never establishes a relationship.
 */
internal fun reportMedicationIdentity(report: FollowUpReport): ReportMedicationIdentity {
    data class Plan(val series: String, val id: String, val first: LocalDateTime)
    val fromRows = report.rows.mapNotNull { row ->
        val series = row.groupId ?: return@mapNotNull null
        val id = reportMedicationPlanId(row) ?: return@mapNotNull null
        Plan(series, id, row.occurredAt)
    }
    val fromCurrent = report.currentMedications.mapNotNull { current ->
        val series = current.medicationSeriesId ?: return@mapNotNull null
        Plan("medication-series:$series", current.planId, current.revision.startsOn.atStartOfDay())
    }
    val plans = (fromRows + fromCurrent).groupBy { it.series }.values.flatMap { seriesPlans ->
        val distinct = seriesPlans.groupBy { it.id }.map { (id, versions) ->
            id to versions.minOf { it.first }
        }.sortedWith(compareBy<Pair<String, LocalDateTime>> { it.second }.thenBy { it.first })
        if (distinct.size < 2) emptyList() else distinct.mapIndexed { index, (id, _) ->
            id to "Pauta ${index + 1}"
        }
    }.toMap()

    val records = report.rows.asSequence()
        .filter { it.section == "Medicamentos" && it.sourceId?.startsWith("medication-intake:") == true }
        .filter { it.medicationPlanId != null && it.scheduledAt != null }
        .groupBy { it.medicationPlanId!! to it.scheduledAt!! }
        .values.flatMap { rows ->
            val ids = rows.mapNotNull { it.sourceId }.distinct().sorted()
            if (ids.size < 2) emptyList() else ids.mapIndexed { index, id ->
                id to "Registro ${index + 1} de ${ids.size}"
            }
        }.toMap()
    return ReportMedicationIdentity(plans, records)
}
