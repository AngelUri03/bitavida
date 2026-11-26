package com.bitavida.app.data.measurements

import android.content.Context
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.usecase.MeasurementScheduleReportHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads each history twice and rejects concurrent or incomplete changes. */
class MeasurementScheduleReportHistoryReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun load(actor: GlucoseActor): MeasurementScheduleReportHistory = withContext(Dispatchers.IO) {
        // Apply the same scoped authorization used for measurement histories before reading schedules.
        MeasurementReportHistoryReader(appContext).load(actor)
        val glucose = EncryptedGlucoseRepository(appContext)
        val pressure = EncryptedBloodPressureRepository(appContext)
        val weight = EncryptedWeightRepository(appContext)
        suspend fun read(
            kind: MeasurementKind,
            followUp: suspend () -> GlucoseFollowUp?,
            decisions: suspend (String) -> List<GlucoseOccurrenceDecision>,
            revisions: suspend (String) -> List<GlucoseOccurrenceDecisionRevision>,
        ): MeasurementScheduleReportHistory.ScheduleHistory {
            repeat(3) {
                val before = followUp()
                val current = before?.let { decisions(it.id) }.orEmpty().sortedBy { it.id }
                val history = current.map { it to revisions(it.occurrenceId).sortedBy { revision -> revision.revisionNumber } }
                val after = followUp()
                val afterDecisions = after?.let { decisions(it.id) }.orEmpty().sortedBy { it.id }
                val afterHistories = afterDecisions.map {
                    it to revisions(it.occurrenceId).sortedBy { revision -> revision.revisionNumber }
                }
                if (before == after && current == afterDecisions && history == afterHistories &&
                    history.all { (decision, versions) ->
                        versions.isNotEmpty() && versions.map { it.revisionNumber } == (1..versions.size).toList() &&
                            versions.all { it.decisionId == decision.id && it.occurrenceId == decision.occurrenceId }
                    }) {
                    return MeasurementScheduleReportHistory.ScheduleHistory(kind, before, history)
                }
            }
            error("No se pudo obtener una instantánea coherente de los seguimientos.")
        }
        MeasurementScheduleReportHistory(listOf(
            read(MeasurementKind.CAPILLARY_GLUCOSE, { glucose.loadFollowUp(actor) },
                { glucose.loadOccurrenceDecisions(actor, it) }, { glucose.loadOccurrenceDecisionRevisions(actor, it) }),
            read(MeasurementKind.BLOOD_PRESSURE, { pressure.loadFollowUp(actor) },
                { pressure.loadOccurrenceDecisions(actor, it) }, { pressure.loadOccurrenceDecisionRevisions(actor, it) }),
            read(MeasurementKind.WEIGHT, { weight.loadFollowUp(actor) },
                { weight.loadOccurrenceDecisions(actor, it) }, { weight.loadOccurrenceDecisionRevisions(actor, it) }),
        ))
    }
}
