package com.bitavida.app.domain.model

import com.bitavida.app.domain.validation.textCharacterCount

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class GlucoseFollowUpFrequency {
    DAILY,
    SPECIFIC_DAYS,
    EVERY_N_DAYS,
}

enum class GlucoseFollowUpTimingType {
    EXACT_TIME,
    TIME_WINDOW,
}

enum class GlucoseFollowUpChangeKind {
    CREATED,
    CORRECTED,
    INDICATION_CHANGED,
    REMOVED,
}

data class GlucoseFollowUpSlot(
    val id: String,
    val timingType: GlucoseFollowUpTimingType = GlucoseFollowUpTimingType.EXACT_TIME,
    val time: LocalTime,
    val windowEnd: LocalTime? = null,
    val endsNextDay: Boolean = false,
    val suggestedContext: GlucoseContext,
    val reminderEnabled: Boolean,
    val reminderTime: LocalTime? = null,
    val reminderNextDay: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        when (timingType) {
            GlucoseFollowUpTimingType.EXACT_TIME -> {
                require(windowEnd == null)
                require(!endsNextDay)
                require(!reminderNextDay)
            }
            GlucoseFollowUpTimingType.TIME_WINDOW -> {
                require(windowEnd != null)
                require(endsNextDay || (windowEnd > time))
                require(windowEnd != time)
                if (reminderEnabled) {
                    requireNotNull(reminderTime)
                    require(
                        reminderTime.isWithinGlucoseWindow(
                            start = time,
                            end = windowEnd,
                            endsNextDay = endsNextDay,
                            reminderNextDay = reminderNextDay,
                        ),
                    )
                }
            }
        }
        require(!reminderEnabled || (reminderTime != null))
    }
}

data class GlucoseFollowUpRevision(
    val id: String,
    val followUpId: String,
    val revisionNumber: Int,
    val indication: String,
    val frequency: GlucoseFollowUpFrequency,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val everyNDaysInterval: Int? = null,
    val everyNDaysReferenceDate: LocalDate? = null,
    val startsOn: LocalDate,
    val endsOn: LocalDate? = null,
    val effectiveFrom: LocalDate,
    val slots: List<GlucoseFollowUpSlot>,
    val remindersEnabled: Boolean,
    val changeKind: GlucoseFollowUpChangeKind,
    val changeReason: String? = null,
    val capturedAt: LocalDateTime,
    val authorActorId: String,
) {
    init {
        require(id.isNotBlank())
        require(followUpId.isNotBlank())
        require(revisionNumber > 0)
        require(indication.isNotBlank() && textCharacterCount(indication) <= 1_000)
        require(endsOn == null || !endsOn.isBefore(startsOn))
        require(effectiveFrom >= startsOn)
        require(slots.isNotEmpty())
        require(slots.map(GlucoseFollowUpSlot::id).distinct().size == slots.size)
        require(slots.map(GlucoseFollowUpSlot::time).distinct().size == slots.size)
        require(
            frequency != GlucoseFollowUpFrequency.SPECIFIC_DAYS ||
                weekdays.isNotEmpty(),
        )
        require(
            frequency != GlucoseFollowUpFrequency.EVERY_N_DAYS ||
                (
                    everyNDaysInterval != null &&
                        everyNDaysInterval > 0 &&
                        everyNDaysReferenceDate != null
                    ),
        )
        require(changeReason == null || textCharacterCount(changeReason) <= 500)
        require(authorActorId.isNotBlank())
    }

    val active: Boolean
        get() = changeKind != GlucoseFollowUpChangeKind.REMOVED
}

data class GlucoseFollowUp(
    val id: String,
    val personUnderCareId: String,
    val createdAt: LocalDateTime,
    val createdByActorId: String,
    val revisions: List<GlucoseFollowUpRevision>,
) {
    init {
        require(id.isNotBlank())
        require(personUnderCareId.isNotBlank())
        require(createdByActorId.isNotBlank())
        require(revisions.isNotEmpty())
        require(revisions.all { it.followUpId == id })
        require(revisions.map { it.revisionNumber } == (1..revisions.size).toList())
    }

    val currentRevision: GlucoseFollowUpRevision
        get() = revisions.last()
}

enum class GlucoseOccurrenceDecisionKind { NOT_PERFORMED }

enum class GlucoseOccurrenceDecisionRevisionOperation {
    CONFIRMED,
    CORRECTED,
    CORRECTED_TO_RECORDED,
}

data class GlucoseOccurrenceDecision(
    val id: String,
    val followUpId: String,
    val followUpRevisionId: String,
    val occurrenceId: String,
    val scheduledAt: LocalDateTime,
    val kind: GlucoseOccurrenceDecisionKind,
    val reason: String?,
    val capturedAt: LocalDateTime,
    val actorId: String,
) {
    init {
        require(id.isNotBlank())
        require(followUpId.isNotBlank())
        require(followUpRevisionId.isNotBlank())
        require(occurrenceId.isNotBlank())
        require(reason == null || textCharacterCount(reason) <= 300)
        require(actorId.isNotBlank())
    }
}

data class GlucoseOccurrenceDecisionRevision(
    val id: String,
    val decisionId: String,
    val occurrenceId: String,
    val revisionNumber: Int,
    val operation: GlucoseOccurrenceDecisionRevisionOperation,
    val reason: String?,
    val capturedAt: LocalDateTime,
    val actorId: String,
) {
    init {
        require(id.isNotBlank())
        require(decisionId.isNotBlank())
        require(occurrenceId.isNotBlank())
        require(revisionNumber > 0)
        require(reason == null || textCharacterCount(reason) <= 300)
        require(actorId.isNotBlank())
    }
}

data class GlucoseScheduledOccurrence(
    val id: String,
    val followUpId: String,
    val followUpRevisionId: String,
    val slotId: String,
    val timingType: GlucoseFollowUpTimingType,
    val scheduledAt: LocalDateTime,
    val windowEndAt: LocalDateTime? = null,
    val suggestedContext: GlucoseContext,
    val reminderAt: LocalDateTime?,
)

enum class GlucoseOccurrenceState {
    RECORDED,
    UNCONFIRMED,
    NOT_PERFORMED,
}

data class GlucoseTodayOccurrence(
    val occurrence: GlucoseScheduledOccurrence,
    val state: GlucoseOccurrenceState,
    val record: MeasurementRecord? = null,
    val decision: GlucoseOccurrenceDecision? = null,
)

data class GlucoseTodaySummary(
    val scheduled: List<GlucoseTodayOccurrence>,
    val additionalRecords: List<MeasurementRecord>,
)

fun GlucoseFollowUp.applicableRevision(date: LocalDate): GlucoseFollowUpRevision? {
    val revision = revisions.lastOrNull {
        !it.effectiveFrom.isAfter(date)
    } ?: return null
    return revision.takeIf { it.active }
}

fun GlucoseFollowUpRevision.appliesOn(date: LocalDate): Boolean =
    active && when (frequency) {
        GlucoseFollowUpFrequency.DAILY -> true
        GlucoseFollowUpFrequency.SPECIFIC_DAYS -> date.dayOfWeek in weekdays
        GlucoseFollowUpFrequency.EVERY_N_DAYS -> {
            val reference = requireNotNull(everyNDaysReferenceDate)
            val interval = requireNotNull(everyNDaysInterval)
            val elapsed = ChronoUnit.DAYS.between(reference, date)
            elapsed >= 0 && elapsed % interval == 0L
        }
    }

/** Declared template, also retained for resolving references written by earlier app versions. */
fun GlucoseFollowUp.occurrencesOn(date: LocalDate): List<GlucoseScheduledOccurrence> =
    applicableRevision(date)?.declaredOccurrencesOn(date).orEmpty()

internal fun GlucoseFollowUpRevision.declaredOccurrencesOn(
    date: LocalDate,
): List<GlucoseScheduledOccurrence> {
    if (!appliesOn(date)) return emptyList()
    return slots.sortedBy(GlucoseFollowUpSlot::time).map { slot ->
        GlucoseScheduledOccurrence(
            id = listOf(id, date, slot.id).joinToString(":"),
            followUpId = followUpId,
            followUpRevisionId = id,
            slotId = slot.id,
            timingType = slot.timingType,
            scheduledAt = date.atTime(slot.time),
            windowEndAt = slot.windowEnd?.let {
                date.plusDays(if (slot.endsNextDay) 1 else 0).atTime(it)
            },
            suggestedContext = slot.suggestedContext,
            reminderAt = slot.reminderTime
                ?.takeIf { remindersEnabled && slot.reminderEnabled }
                ?.let {
                    date.plusDays(if (slot.reminderNextDay) 1 else 0).atTime(it)
                },
        )
    }
}

fun GlucoseScheduledOccurrence.confirmed() = ConfirmedGlucoseOccurrence(
    id = id,
    followUpId = followUpId,
    scheduledAt = scheduledAt,
    expectedContext = suggestedContext,
    timingType = timingType,
    windowEndAt = windowEndAt,
)

fun projectGlucoseToday(
    date: LocalDate,
    followUp: GlucoseFollowUp?,
    records: List<MeasurementRecord>,
    decisions: List<GlucoseOccurrenceDecision>,
): GlucoseTodaySummary {
    return projectMeasurementToday(
        date, followUp, records, decisions, MeasurementKind.CAPILLARY_GLUCOSE,
    )
}

fun GlucoseFollowUp.toMeasurementFollowUp(
    kind: MeasurementKind = MeasurementKind.CAPILLARY_GLUCOSE,
): MeasurementFollowUp {
    val revision = currentRevision
    val frequencyLabel = when (revision.frequency) {
        GlucoseFollowUpFrequency.DAILY -> "Todos los días"
        GlucoseFollowUpFrequency.SPECIFIC_DAYS ->
            revision.weekdays.sortedBy(DayOfWeek::getValue)
                .joinToString { it.spanishShortLabel() }
        GlucoseFollowUpFrequency.EVERY_N_DAYS ->
            "Cada ${revision.everyNDaysInterval} días"
    }
    return MeasurementFollowUp(
        kind = kind,
        summary = "${revision.slots.size} mediciones · $frequencyLabel",
        suspended = !revision.active,
        frequency = "${revision.slots.size} mediciones programadas",
        periodicity = frequencyLabel,
        moments = revision.slots.map {
            val schedule = when (it.timingType) {
                GlucoseFollowUpTimingType.EXACT_TIME -> it.time.toString()
                GlucoseFollowUpTimingType.TIME_WINDOW ->
                    it.time.toString() + "–" + it.windowEnd.toString()
            }
            if (kind == MeasurementKind.WEIGHT) {
                schedule
            } else {
                schedule + " · " + it.suggestedContext.spanishLabel()
            }
        },
        reminders = MeasurementReminderInfo(
            status = if (!revision.remindersEnabled) {
                MeasurementReminderStatus.DISABLED
            } else {
                MeasurementReminderStatus.CONFIGURED
            },
            detail = if (revision.remindersEnabled) {
                "Avisos locales configurados"
            } else {
                null
            },
        ),
        id = id,
    )
}

fun LocalTime.isWithinGlucoseWindow(
    start: LocalTime,
    end: LocalTime,
    endsNextDay: Boolean,
    reminderNextDay: Boolean,
): Boolean {
    val reminderMinute = toSecondOfDay() / 60 +
        if (reminderNextDay) 24 * 60 else 0
    val startMinute = start.toSecondOfDay() / 60
    val endMinute = end.toSecondOfDay() / 60 +
        if (endsNextDay) 24 * 60 else 0
    return reminderMinute in startMinute..endMinute
}

private fun DayOfWeek.spanishShortLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "lun"
    DayOfWeek.TUESDAY -> "mar"
    DayOfWeek.WEDNESDAY -> "mié"
    DayOfWeek.THURSDAY -> "jue"
    DayOfWeek.FRIDAY -> "vie"
    DayOfWeek.SATURDAY -> "sáb"
    DayOfWeek.SUNDAY -> "dom"
}

private fun GlucoseContext.spanishLabel(): String = when (this) {
    GlucoseContext.FASTING -> "En ayunas"
    GlucoseContext.BEFORE_MEAL -> "Antes de comer"
    GlucoseContext.AFTER_MEAL -> "Después de comer"
    GlucoseContext.BEFORE_SLEEP -> "Antes de dormir"
    GlucoseContext.OTHER -> "Otro momento"
    GlucoseContext.RESTING -> "En reposo"
    GlucoseContext.AFTER_PHYSICAL_ACTIVITY ->
        "Después de actividad física"
    GlucoseContext.UPON_WAKING -> "Al despertar"
    GlucoseContext.UNSPECIFIED -> "Sin contexto"
}
