package com.bitavida.app.ui.observations

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate

/**
 * Faithful Observations adapter of the approved week calendar.
 * Its day/arrow/legend helpers are private in Measurements, whose files remain unchanged.
 * Only the return-to-today action differs: a full-width secondary button inside this calendar.
 */
@Composable
internal fun ObservationWeekCalendar(
    selectedDate: LocalDate,
    days: List<BitavidaCalendarDay>,
    today: LocalDate,
    motionAllowed: Boolean,
    onDateSelected: (LocalDate) -> Unit,
) {
    MeasurementPanel {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ObservationCalendarWeekArrow(false, motionAllowed) { onDateSelected(selectedDate.minusWeeks(1)) }
            Text(
                days.first().date.format(measurementShortDate) + " – " +
                    days.last().date.format(measurementShortDate) + " " + days.last().date.year,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ObservationCalendarWeekArrow(true, motionAllowed) { onDateSelected(selectedDate.plusWeeks(1)) }
        }
        ObservationHistoryChange(days.first().date, motionAllowed) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val large = LocalDensity.current.fontScale >= 1.5f
                val dayWidth = maxOf((maxWidth - 24.dp) / 7, if (large) 76.dp else 48.dp)
                val dayScroll = rememberScrollState()
                val density = LocalDensity.current
                val viewport = with(density) { maxWidth.roundToPx() }
                val span = with(density) { (dayWidth + 4.dp).roundToPx() }
                val tileWidth = with(density) { dayWidth.roundToPx() }
                LaunchedEffect(selectedDate, span, viewport, dayScroll.maxValue) {
                    val start = selectedDate.dayOfWeek.ordinal * span
                    val target = when {
                        start < dayScroll.value -> start
                        start + tileWidth > dayScroll.value + viewport -> start + tileWidth - viewport
                        else -> dayScroll.value
                    }.coerceIn(0, dayScroll.maxValue)
                    if (motionAllowed) dayScroll.animateScrollTo(target)
                    else dayScroll.scrollTo(target)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(dayScroll).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    days.forEach { day ->
                        ObservationCalendarDayTile(
                            day, day.date == selectedDate, day.date == today,
                            day.date > today, motionAllowed, Modifier.width(dayWidth),
                        ) { onDateSelected(day.date) }
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            ObservationCalendarLegend(GlucoseOccurrenceState.RECORDED, "Registrada")
            ObservationCalendarLegend(GlucoseOccurrenceState.UNCONFIRMED, "Sin confirmar")
            ObservationCalendarLegend(GlucoseOccurrenceState.NOT_PERFORMED, "No realizada")
        }
        if (selectedDate != today) {
            MeasurementButton(
                "Volver a hoy",
                { onDateSelected(today) },
                modifier = Modifier.fillMaxWidth().testTag("observation_calendar_today"),
                style = MeasurementButtonStyle.OUTLINED,
            )
        }
    }
}

@Composable
private fun ObservationCalendarWeekArrow(next: Boolean, motionAllowed: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget)
            .semantics { contentDescription = if (next) "Semana siguiente" else "Semana anterior" }
            .bitavidaFluidInteraction(
                source, true, motionAllowed, shape = MaterialTheme.shapes.small,
                restingElevationDp = 0f,
            )
            .testTag(if (next) "observation_calendar_next_week" else "observation_calendar_previous_week"),
    ) {
        Box(Modifier.graphicsLayer { rotationZ = if (next) 0f else 180f }) {
            MeasurementIcon(R.drawable.ic_v2_chevron)
        }
    }
}

@Composable
private fun ObservationCalendarDayTile(
    day: BitavidaCalendarDay,
    selected: Boolean,
    today: Boolean,
    future: Boolean,
    motionAllowed: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val source = remember { MutableInteractionSource() }
    val color by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surface,
        tween(if (motionAllowed) BitavidaFluidMotion.Selection else 0),
        label = "calendar_day_selection",
    )
    Surface(
        color = color,
        shape = shape,
        border = BorderStroke(1.dp, if (selected || today) scheme.primary else scheme.outline),
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .clip(shape)
            .bitavidaFluidInteraction(source, true, motionAllowed, shape = shape, restingElevationDp = 0f)
            .selectable(
                selected, source, androidx.compose.material3.ripple(), role = Role.Tab, onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = day.date.format(measurementFullDate) + if (today) ", hoy" else ""
                stateDescription = if (!day.hasEvents) {
                    if (future) "Día futuro, sin eventos" else "Sin eventos"
                } else {
                    "${day.recordedCount} registradas, ${day.unconfirmedCount} sin confirmar, " +
                        "${day.notPerformedCount} no realizadas"
                }
            }.testTag("observation_calendar_day_${day.date}"),
    ) {
        Column(
            Modifier.padding(horizontal = 2.dp, vertical = BitavidaSpacing.Small)
                .clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                day.date.format(measurementWeekday).trimEnd('.')
                    .replaceFirstChar { it.titlecase(measurementLocale) },
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!day.hasEvents) ObservationCalendarStatusMark(null, future)
                if (day.recordedCount > 0) ObservationCalendarStatusMark(GlucoseOccurrenceState.RECORDED)
                if (day.unconfirmedCount > 0) ObservationCalendarStatusMark(GlucoseOccurrenceState.UNCONFIRMED)
                if (day.notPerformedCount > 0) ObservationCalendarStatusMark(GlucoseOccurrenceState.NOT_PERFORMED)
            }
        }
    }
}

@Composable
private fun ObservationCalendarLegend(state: GlucoseOccurrenceState, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        ObservationCalendarStatusMark(state)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ObservationCalendarStatusMark(state: GlucoseOccurrenceState?, future: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(12.dp).clearAndSetSemantics {}) {
        val c = center
        val r = size.minDimension / 2 - 1.dp.toPx()
        val stroke = 1.4.dp.toPx()
        when (state) {
            GlucoseOccurrenceState.RECORDED -> {
                drawCircle(primary, r)
                drawLine(onPrimary, Offset(c.x - r * .5f, c.y), Offset(c.x - r * .1f, c.y + r * .4f), stroke, StrokeCap.Round)
                drawLine(onPrimary, Offset(c.x - r * .1f, c.y + r * .4f), Offset(c.x + r * .6f, c.y - r * .4f), stroke, StrokeCap.Round)
            }
            GlucoseOccurrenceState.UNCONFIRMED -> {
                drawCircle(primary, r, style = Stroke(stroke))
                drawLine(primary, c, Offset(c.x, c.y - r * .55f), stroke, StrokeCap.Round)
                drawLine(primary, c, Offset(c.x + r * .45f, c.y), stroke, StrokeCap.Round)
            }
            GlucoseOccurrenceState.NOT_PERFORMED -> {
                drawCircle(neutral, r, style = Stroke(stroke))
                drawLine(neutral, Offset(c.x - r * .35f, c.y - r * .35f), Offset(c.x + r * .35f, c.y + r * .35f), stroke, StrokeCap.Round)
                drawLine(neutral, Offset(c.x + r * .35f, c.y - r * .35f), Offset(c.x - r * .35f, c.y + r * .35f), stroke, StrokeCap.Round)
            }
            null -> if (future) drawCircle(neutral, r, style = Stroke(stroke))
                else drawCircle(outline, r)
        }
    }
}

/** Content and semantics change immediately. Only the new content fades/slides into place. */
@Composable
internal fun ObservationHistoryChange(
    change: Any,
    motionAllowed: Boolean,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(1f) }
    val distance = with(LocalDensity.current) { 6.dp.toPx() }
    LaunchedEffect(change, motionAllowed) {
        if (motionAllowed) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(BitavidaFluidMotion.Selection))
        } else progress.snapTo(1f)
    }
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = .55f + .45f * progress.value
        translationY = distance * (1f - progress.value)
    }) { content() }
}

