package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.toMeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.occurrencesOn
import java.time.LocalDate
import java.time.LocalDateTime

/** Only query preferences; no readings or drafts belong in saved UI state. */
data class MeasurementCalendarSelection(
    val date: LocalDate,
    val filter: MeasurementKind? = null,
) {
    val weekStart: LocalDate
        get() = date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun moveWeek(offset: Long) = copy(date = date.plusWeeks(offset))
    fun selectDay(day: LocalDate) = copy(date = day)
    fun filterBy(kind: MeasurementKind?) = copy(filter = kind)
}

/** References the existing aggregate, including its inactive and previous revisions. */
data class MeasurementScheduleHistory(
    val kind: MeasurementKind,
    val followUp: GlucoseFollowUp?,
    val decisions: List<GlucoseOccurrenceDecision> = emptyList(),
)

/** Shared timeline identity and state; the original typed event remains available for actions. */
data class MeasurementTimelineEvent(
    val kind: MeasurementKind,
    val source: GlucoseHistoryEvent,
) {
    val record: MeasurementRecord?
        get() = (source as? GlucoseHistoryEvent.Registered)?.record

    val occurrence: GlucoseScheduledOccurrence?
        get() = when (source) {
            is GlucoseHistoryEvent.Registered -> null
            is GlucoseHistoryEvent.NotPerformed -> source.occurrence
            is GlucoseHistoryEvent.Unconfirmed -> source.occurrence
        }

    val state: GlucoseOccurrenceState
        get() = when (source) {
            is GlucoseHistoryEvent.Registered -> GlucoseOccurrenceState.RECORDED
            is GlucoseHistoryEvent.NotPerformed -> GlucoseOccurrenceState.NOT_PERFORMED
            is GlucoseHistoryEvent.Unconfirmed -> GlucoseOccurrenceState.UNCONFIRMED
        }

    val at: LocalDateTime get() = source.sortAt

    // A record ID and occurrence ID may coincide; repositories may also share raw IDs.
    val key: String
        get() = kind.name + ":" + when (source) {
            is GlucoseHistoryEvent.Registered -> "record:" + source.record.id
            is GlucoseHistoryEvent.NotPerformed -> "occurrence:" + source.occurrence.id
            is GlucoseHistoryEvent.Unconfirmed -> "occurrence:" + source.occurrence.id
        }
}

data class MeasurementCalendarDay(
    val date: LocalDate,
    val events: List<MeasurementTimelineEvent>,
) {
    val recordedCount: Int get() = events.count { it.state == GlucoseOccurrenceState.RECORDED }
    val unconfirmedCount: Int get() = events.count { it.state == GlucoseOccurrenceState.UNCONFIRMED }
    val notPerformedCount: Int get() = events.count { it.state == GlucoseOccurrenceState.NOT_PERFORMED }
}

data class MeasurementCalendarWeek(
    val selection: MeasurementCalendarSelection,
    val days: List<MeasurementCalendarDay>,
) {
    val start: LocalDate get() = days.first().date
    val endInclusive: LocalDate get() = days.last().date
    val selectedDay: MeasurementCalendarDay get() = days.first { it.date == selection.date }
}

/**
 * Read-only adapter over the existing histories. Full record lists are passed to each projection:
 * a linked reading on a different day/week must still suppress its scheduled placeholder.
 * Future schedules and open windows never become unconfirmed events.
 */
fun projectMeasurementCalendar(
    records: List<MeasurementRecord>,
    schedules: List<MeasurementScheduleHistory>,
    selection: MeasurementCalendarSelection,
    now: LocalDateTime,
): MeasurementCalendarWeek {
    val start = selection.weekStart
    val kinds = selection.filter?.let(::listOf) ?: MeasurementKind.entries
    val events = kinds.flatMap { kind ->
        val schedule = schedules.firstOrNull { it.kind == kind }
        projectMeasurementHistoryEvents(
            records = records,
            followUp = schedule?.followUp,
            decisions = schedule?.decisions.orEmpty(),
            start = start,
            endInclusive = start.plusDays(6),
            now = now,
            kind = kind,
        ).flatMap { day -> day.events.map { MeasurementTimelineEvent(kind, it) } }
    }.sortedWith(
        compareByDescending<MeasurementTimelineEvent> { it.at }
            .thenByDescending { it.record?.capturedAt ?: it.at }
            .thenBy { it.key },
    ).groupBy { it.at.toLocalDate() }
    return MeasurementCalendarWeek(
        selection,
        (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            MeasurementCalendarDay(date, events[date].orEmpty())
        },
    )
}

/**
 * Capture of a historical event uses its original active revision as context. It never changes
 * the current follow-up or bypasses the existing authorization/consent and save use cases.
 */
fun measurementFollowUpForOccurrence(
    followUp: GlucoseFollowUp,
    occurrence: GlucoseScheduledOccurrence,
    kind: MeasurementKind = MeasurementKind.CAPILLARY_GLUCOSE,
): MeasurementFollowUp? {
    if (followUp.id != occurrence.followUpId) return null
    val index = followUp.revisions.indexOfFirst { it.id == occurrence.followUpRevisionId }
    if (index < 0) return null
    val historical = followUp.copy(revisions = followUp.revisions.take(index + 1))
    if (historical.occurrencesOn(occurrence.scheduledAt.toLocalDate()).none { it == occurrence }) {
        return null
    }
    return historical.toMeasurementFollowUp(kind)
}

