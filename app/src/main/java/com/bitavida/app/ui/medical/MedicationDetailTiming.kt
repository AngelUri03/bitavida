package com.bitavida.app.ui.medical

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Presentation of the recorded schedule, without clinical grace periods. */
internal enum class MedicationDetailTiming(val canQuickRegister: Boolean) {
    FUTURE(false),
    AT_RECORDED_TIME(true),
    CURRENT_WINDOW(true),
    PAST_UNCONFIRMED(true),
    RECORDED(false),
}

internal fun medicationDetailTiming(
    startsAt: LocalDateTime,
    endsAt: LocalDateTime?,
    now: LocalDateTime,
    recorded: Boolean,
): MedicationDetailTiming = when {
    recorded -> MedicationDetailTiming.RECORDED
    startsAt.isAfter(now) -> MedicationDetailTiming.FUTURE
    endsAt != null && !endsAt.isBefore(now) -> MedicationDetailTiming.CURRENT_WINDOW
    startsAt.truncatedTo(ChronoUnit.MINUTES) == now.truncatedTo(ChronoUnit.MINUTES) ->
        MedicationDetailTiming.AT_RECORDED_TIME
    else -> MedicationDetailTiming.PAST_UNCONFIRMED
}
