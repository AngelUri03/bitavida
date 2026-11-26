package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventCategory
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

internal fun eventDate(event: CareEvent): LocalDate? =
    event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate

internal fun eventCategory(event: CareEvent): CareEventCategory =
    if (event.draft.category == CareEventCategory.GENERAL && event.draft.kind == CareEventKind.STUDY)
        CareEventCategory.LABORATORY else event.draft.category

internal fun isClosed(event: CareEvent): Boolean = event.draft.status in setOf(
    CareEventStatus.COMPLETED, CareEventStatus.CANCELLED, CareEventStatus.PERFORMED,
    CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)

internal fun eventOrder(): Comparator<CareEvent> = compareBy<CareEvent> { eventDate(it) ?: LocalDate.MAX }
    .thenBy { it.draft.scheduledAt?.toLocalTime() ?: LocalTime.MAX }.thenBy { it.id }

/** A scheduled instant is required: never invent a time for records from older versions. */
internal fun isFutureAppointment(event: CareEvent, now: LocalDateTime): Boolean =
    !isClosed(event) && event.draft.scheduledAt?.isAfter(now) == true

/** Keep legacy undated records and cancelled events accessible without inferring attendance. */
internal fun isHistoricalAppointment(event: CareEvent, now: LocalDateTime): Boolean =
    !isFutureAppointment(event, now)

internal fun upcomingAppointments(events: List<CareEvent>, now: LocalDateTime): List<CareEvent> =
    events.filter { isFutureAppointment(it, now) }.sortedWith(eventOrder())

internal fun appointmentHistory(events: List<CareEvent>, now: LocalDateTime): List<CareEvent> =
    events.filter { isHistoricalAppointment(it, now) }.sortedWith(
        compareByDescending<CareEvent> { it.draft.scheduledAt ?: eventDate(it)?.atTime(LocalTime.MAX) ?: LocalDateTime.MIN }
            .thenBy { it.id },
    )


/** Groups preserve the already descending order; undated legacy records remain together last. */
internal fun appointmentHistoryGroups(events: List<CareEvent>, now: LocalDateTime): List<Pair<YearMonth?, List<CareEvent>>> =
    appointmentHistory(events, now).groupBy { eventDate(it)?.let(YearMonth::from) }.toList()

internal fun monthDays(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val weeks = (leading + month.lengthOfMonth() + 6) / 7
    return List(weeks * 7) { index ->
        val day = index - leading + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

internal fun moveMonth(date: LocalDate, delta: Long): LocalDate {
    val next = YearMonth.from(date).plusMonths(delta)
    return next.atDay(date.dayOfMonth.coerceAtMost(next.lengthOfMonth()))
}

internal fun calendarEligible(event: CareEvent): Boolean =
    event.draft.status != CareEventStatus.CANCELLED && eventDate(event) != null

internal fun eventsOnDate(events: List<CareEvent>, date: LocalDate): List<CareEvent> =
    events.filter { calendarEligible(it) && eventDate(it) == date }.sortedWith(eventOrder())
