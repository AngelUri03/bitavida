package com.bitavida.app.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Persisted StateFlow changes immediately invalidate the projection. The clock wakes only at a
 * known schedule, greeting or date boundary; it never polls repositories or waits to acknowledge a save.
 */
@Composable
internal fun rememberTodayCareClock(
    snapshotKey: List<Any?>,
    extraBoundaries: List<LocalDateTime> = emptyList(),
    project: (LocalDateTime) -> TodayCareProjection,
): State<LocalDateTime> {
    val owner = LocalLifecycleOwner.current
    val currentProject by rememberUpdatedState(project)
    return produceState(LocalDateTime.now(), owner, snapshotKey, extraBoundaries) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val now = LocalDateTime.now()
                value = now
                val projected = currentProject(now)
                val boundaries = buildList {
                    addAll(extraBoundaries)
                    projected.medications.forEach { item ->
                        add(item.occurrence.startsAt)
                        item.occurrence.endsAt?.let { add(it.plusNanos(1)) }
                    }
                    projected.measurements.forEach { item ->
                        add(item.occurrence.scheduledAt)
                        item.occurrence.windowEndAt?.let { add(it.plusNanos(1)) }
                    }
                    projected.observations.forEach { item ->
                        add(item.scheduledAt)
                        item.windowEndAt?.let { add(it.plusNanos(1)) }
                    }
                    projected.reminders.forEach { add(it.scheduledAt) }
                }
                val next = nextTodayCareClockChange(now, boundaries)
                val zone = ZoneId.systemDefault()
                val remaining = Duration.between(now.atZone(zone).toInstant(), next.atZone(zone).toInstant())
                delay(remaining.toMillis().coerceAtLeast(1L) + 1L)
            }
        }
    }
}

internal fun nextTodayCareClockChange(now: LocalDateTime, boundaries: List<LocalDateTime>): LocalDateTime {
    val date = now.toLocalDate()
    val candidates = boundaries + listOf(
        date.atTime(5, 0), date.atTime(12, 0), date.atTime(19, 0),
        date.plusDays(1).atStartOfDay(),
    )
    return candidates.asSequence().filter { it.isAfter(now) }.minOrNull()
        ?: date.plusDays(1).atStartOfDay()
}
