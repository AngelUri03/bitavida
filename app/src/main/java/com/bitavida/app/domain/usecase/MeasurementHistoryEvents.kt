package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.projectMeasurementOccurrences
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Authoritative event query for glucose, pressure, weight and the combined calendar.
 * Preserves every distinct persisted reading; identical copies of the same primary key are
 * the same row. Conflicting snapshots of one primary key are an integrity error, not a merge.
 */
fun projectMeasurementHistoryEvents(
    records: List<MeasurementRecord>,
    followUp: GlucoseFollowUp?,
    decisions: List<GlucoseOccurrenceDecision>,
    start: LocalDate,
    endInclusive: LocalDate,
    now: LocalDateTime,
    kind: MeasurementKind,
): List<GlucoseHistoryDay> {
    require(endInclusive >= start)
    val readings = recordsForMeasurementKind(records, kind).groupBy { it.id }.values.map { copies ->
        val first = copies.first()
        check(copies.all { it == first }) { "Conflicting measurement snapshots." }
        first
    }
    val byActualDate = readings.groupBy { it.occurredAt.toLocalDate() }
    return generateSequence(start) { it.plusDays(1).takeIf { next -> next <= endInclusive } }
        .map { date ->
            val dayRecords = byActualDate[date].orEmpty().sortedWith(
                compareBy<MeasurementRecord> { it.occurredAt }.thenBy { it.capturedAt }.thenBy { it.id },
            )
            val states = projectMeasurementOccurrences(date, followUp, readings, decisions, kind)
            val scheduledEvents = states.filter {
                it.record == null && (it.occurrence.windowEndAt ?: it.occurrence.scheduledAt) <= now
            }.map { state ->
                when (state.state) {
                    GlucoseOccurrenceState.NOT_PERFORMED -> GlucoseHistoryEvent.NotPerformed(
                        state.occurrence, requireNotNull(state.decision),
                    )
                    GlucoseOccurrenceState.UNCONFIRMED -> GlucoseHistoryEvent.Unconfirmed(state.occurrence)
                    GlucoseOccurrenceState.RECORDED -> error("Recorded event already represented by its actual reading.")
                }
            }
            GlucoseHistoryDay(
                date,
                dayRecords,
                (dayRecords.map(GlucoseHistoryEvent::Registered) + scheduledEvents).sortedWith(
                    compareBy<GlucoseHistoryEvent> { it.sortAt }.thenBy {
                        when (it) {
                            is GlucoseHistoryEvent.Registered -> it.record.id
                            is GlucoseHistoryEvent.NotPerformed -> it.occurrence.id
                            is GlucoseHistoryEvent.Unconfirmed -> it.occurrence.id
                        }
                    },
                ),
            )
        }.toList()
}
