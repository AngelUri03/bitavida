package com.bitavida.app.ui.report

import com.bitavida.app.domain.usecase.FollowUpReportRow

internal data class MedicationHistoryBlock(
    val row: FollowUpReportRow,
    val detail: String,
)

/**
 * The change and transcribed indication for one persisted plan revision share
 * a heading only when their source, revision and capture time all match.
 * Distinct facts and ambiguous or legacy rows remain separate.
 */
internal fun medicationHistoryBlocks(rows: List<FollowUpReportRow>): List<MedicationHistoryBlock> {
    data class Key(val source: String, val group: String, val revision: Int, val at: java.time.LocalDateTime)
    fun key(row: FollowUpReportRow): Key? {
        val source = row.sourceId?.takeIf { it.startsWith("medication-plan:") } ?: return null
        val group = row.groupId ?: return null
        val revision = row.revisionNumber ?: return null
        return Key(source, group, revision, row.occurredAt)
    }

    val candidates = rows.groupBy(::key)
    val paired = candidates.filterKeys { it != null }.filterValues { group ->
        group.size == 2 &&
            group.count { it.section == "Cambios de medicamentos" } == 1 &&
            group.count { it.section == "Indicaciones médicas" } == 1
    }
    val emitted = mutableSetOf<Key>()
    return buildList {
        rows.forEach { row ->
            val identity = key(row)
            val pair = identity?.let { paired[it] }
            if (identity != null && pair != null) {
                if (emitted.add(identity)) {
                    val change = pair.single { it.section == "Cambios de medicamentos" }
                    val indication = pair.single { it.section == "Indicaciones médicas" }
                    add(MedicationHistoryBlock(change,
                        "Cambio registrado: ${change.detail}\nIndicación médica transcrita: ${indication.detail}"))
                }
            } else {
                add(MedicationHistoryBlock(row, row.detail))
            }
        }
    }
}
