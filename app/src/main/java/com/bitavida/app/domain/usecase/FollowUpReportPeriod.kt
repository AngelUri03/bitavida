package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import com.bitavida.app.domain.model.CareEventPurpose
import com.bitavida.app.domain.model.CareConsultationType
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** The dates are boundaries, not assertions about clinical outcomes. */
data class FollowUpPeriod(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) }
}

enum class ConsultationIntervalKind {
    NO_APPOINTMENTS, FIRST_COMPLETED, FIRST_CONFIRMED, BETWEEN_COMPLETED, IN_PROGRESS, CURRENT_MONTH
}

data class ConsultationInterval(
    val period: FollowUpPeriod,
    val kind: ConsultationIntervalKind,
    val startEventId: String? = null,
    val endEventId: String? = null,
    val nextConsultationDate: LocalDate? = null,
)

data class ConsultationPeriodChoices(
    val intervals: List<ConsultationInterval>,
    val selectedIndex: Int,
) {
    val selected: ConsultationInterval get() = intervals[selectedIndex]
}

/** Indices preserve identity in the complete list while bounding the initial selector. */
fun visibleConsultationPeriodIndices(choices: ConsultationPeriodChoices): List<Int> {
    val indexed = choices.intervals.withIndex()
    val completed = indexed.filter { it.value.kind == ConsultationIntervalKind.BETWEEN_COMPLETED ||
        it.value.kind == ConsultationIntervalKind.FIRST_COMPLETED }
        .sortedByDescending { it.value.period.end }
    val next = indexed.firstOrNull {
        (it.value.kind == ConsultationIntervalKind.IN_PROGRESS ||
            it.value.kind == ConsultationIntervalKind.FIRST_CONFIRMED) && it.value.nextConsultationDate != null
    }
    return when {
        next != null -> listOf(next.index) + completed.take(2).map { it.index }
        completed.isNotEmpty() -> completed.take(3).map { it.index }
        else -> listOf(choices.selectedIndex)
    }
}

/**
 * Finds the first declared occurrence, never the creation of a schedule or a planned event.
 * Measurement snapshots must already be scoped to the patient by their repository.
 */
fun firstFollowUpEventDate(
    personUnderCareId: String,
    overview: MedicalCareOverview,
    measurements: List<MeasurementRecord>,
    observations: List<ObservationRecord>,
    reminders: List<CareReminder>,
    events: List<CareEvent>,
    today: LocalDate,
    now: LocalDateTime = today.atTime(LocalTime.MAX),
): LocalDate? {
    val plans = overview.medicationPlans.asSequence()
        .filter { it.personUnderCareId == personUnderCareId && it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA }
        .map { it.id }.toSet()
    val days = sequence {
        overview.medicationIntakes.asSequence().filter { it.medicationPlanId in plans }.forEach {
            // actualAt is a declared occurrence; scheduledAt alone cannot establish one.
            it.currentRevision.actualAt?.takeIf { moment -> !moment.isAfter(now) }
                ?.toLocalDate()?.let { day -> yield(day) }
        }
        measurements.filter { !it.occurredAt.isAfter(now) }.forEach { yield(it.occurredAt.toLocalDate()) }
        observations.asSequence().filter {
            it.personUnderCareId == personUnderCareId && it.result != ObservationResult.NOT_PERFORMED &&
                !it.occurredOn.atTime(it.occurredAt ?: LocalTime.MIN).isAfter(now)
        }.forEach { yield(it.occurredOn) }
        reminders.asSequence().filter {
            it.personUnderCareId == personUnderCareId && it.status == CareReminderStatus.DONE
        }.forEach { it.closedAt?.takeIf { closed -> !closed.isAfter(now) }?.toLocalDate()?.let { day -> yield(day) } }
        events.asSequence().filter {
            it.personUnderCareId == personUnderCareId && when (it.draft.kind) {
                CareEventKind.APPOINTMENT -> it.draft.status == CareEventStatus.COMPLETED
                CareEventKind.STUDY -> it.draft.status in setOf(CareEventStatus.PERFORMED,
                    CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)
                CareEventKind.REVIEW_TO_SCHEDULE -> false
            }
        }.forEach { event ->
            val dateTime = event.draft.scheduledAt ?: event.draft.scheduledDate?.atStartOfDay()
            dateTime?.takeIf { !it.isAfter(now) }?.toLocalDate()?.let { yield(it) }
        }
    }
    return days.filter { !it.isAfter(today) }.minOrNull()
}

fun monthlyFollowUpPeriod(month: YearMonth): FollowUpPeriod =
    FollowUpPeriod(month.atDay(1), month.atEndOfMonth())

/**
 * Confirmed appointments are not treated as completed merely because time has passed.
 * Cancelled or unconfirmed review requests are never chosen as consultation boundaries.
 */
fun consultationFollowUpPeriods(
    firstEvent: LocalDate?,
    events: List<CareEvent>,
    personUnderCareId: String,
    today: LocalDate,
): ConsultationPeriodChoices {
    data class Appointment(val id: String, val date: LocalDate, val status: CareEventStatus)
    val appointments = events.asSequence()
        .filter { it.personUnderCareId == personUnderCareId && it.draft.kind == CareEventKind.APPOINTMENT &&
            it.draft.purpose == CareEventPurpose.MEDICAL_CONSULTATION &&
            it.draft.consultationType != CareConsultationType.UNSPECIFIED && it.draft.isFollowUp }
        .distinctBy { it.id }
        .mapNotNull {
            val date = it.draft.scheduledAt?.toLocalDate() ?: it.draft.scheduledDate
            if (date == null) null else Appointment(it.id, date, it.draft.status)
        }
        .sortedWith(compareBy<Appointment> { it.date }.thenBy { it.id })
        .toList()
    val completed = appointments.filter { it.status == CareEventStatus.COMPLETED && !it.date.isAfter(today) }
    val upcoming = appointments.filter { it.status == CareEventStatus.CONFIRMED && !it.date.isBefore(today) }
    val intervals = mutableListOf<ConsultationInterval>()
    if (completed.isNotEmpty()) {
        val first = completed.first()
        if (firstEvent != null && !firstEvent.isAfter(first.date)) {
            intervals += ConsultationInterval(FollowUpPeriod(firstEvent, first.date),
                ConsultationIntervalKind.FIRST_COMPLETED, endEventId = first.id)
        }
        completed.zipWithNext().forEach { (previous, next) ->
            intervals += ConsultationInterval(FollowUpPeriod(previous.date, next.date),
                ConsultationIntervalKind.BETWEEN_COMPLETED, previous.id, next.id)
        }
        val last = completed.last()
        val next = upcoming.firstOrNull { !it.date.isBefore(last.date) }
        intervals += ConsultationInterval(FollowUpPeriod(last.date, next?.date ?: today),
            ConsultationIntervalKind.IN_PROGRESS, last.id, next?.id, next?.date)
    } else {
        val first = upcoming.firstOrNull()
        if (first != null && firstEvent != null && !firstEvent.isAfter(first.date)) {
            intervals += ConsultationInterval(FollowUpPeriod(firstEvent, first.date),
                ConsultationIntervalKind.FIRST_CONFIRMED, endEventId = first.id,
                nextConsultationDate = first.date)
        }
    }
    if (intervals.isEmpty()) {
        val fallback = firstEvent?.takeIf { !it.isAfter(today) }
        intervals += if (fallback != null) ConsultationInterval(FollowUpPeriod(fallback, today),
            ConsultationIntervalKind.NO_APPOINTMENTS)
        else ConsultationInterval(monthlyFollowUpPeriod(YearMonth.from(today)), ConsultationIntervalKind.CURRENT_MONTH)
    }
    val completedIndex = intervals.indexOfLast {
        it.kind == ConsultationIntervalKind.BETWEEN_COMPLETED || it.kind == ConsultationIntervalKind.FIRST_COMPLETED
    }
    return ConsultationPeriodChoices(intervals, if (completedIndex >= 0) completedIndex else 0)
}
