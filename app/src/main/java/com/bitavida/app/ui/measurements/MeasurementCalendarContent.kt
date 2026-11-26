package com.bitavida.app.ui.measurements

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseOccurrenceState
import com.bitavida.app.domain.model.MeasurementCatalog
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.GlucoseHistoryEvent
import com.bitavida.app.domain.usecase.MeasurementCalendarSelection
import com.bitavida.app.domain.usecase.MeasurementCalendarWeek
import com.bitavida.app.domain.usecase.MeasurementScheduleHistory
import com.bitavida.app.domain.usecase.MeasurementTimelineEvent
import com.bitavida.app.domain.usecase.projectMeasurementCalendar
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

private val calendarSelectionSaver = Saver<MeasurementCalendarSelection, List<String>>(
    save = { listOf(it.date.toString(), it.filter?.name.orEmpty()) },
    restore = {
        MeasurementCalendarSelection(
            LocalDate.parse(it[0]),
            it[1].takeIf(String::isNotEmpty)?.let(MeasurementKind::valueOf),
        )
    },
)

/** A day-sized read-only query over the same data used by the individual measurement screens. */
@Composable
internal fun MeasurementCalendarContent(
    records: List<MeasurementRecord>,
    schedules: List<MeasurementScheduleHistory>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    enabledSecondary: List<MeasurementKind> = emptyList(),
    actionableKinds: Set<MeasurementKind> = emptySet(),
    onEvent: ((MeasurementTimelineEvent) -> Unit)? = null,
    now: LocalDateTime? = null,
    motionAllowed: Boolean? = null,
) {
    var selection by rememberSaveable(stateSaver = calendarSelectionSaver) {
        mutableStateOf(MeasurementCalendarSelection(today))
    }
    val motion = rememberBitavidaMotionAllowed(motionAllowed)
    val currentMoment = now ?: calendarMoment()
    val week = remember(records, schedules, selection, currentMoment) {
        projectMeasurementCalendar(records, schedules, selection, currentMoment)
    }
    Column(
        modifier.fillMaxWidth().testTag("measurement_calendar_history"),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
    ) {
        MeasurementWeekCalendar(week, currentMoment.toLocalDate(), motion) { selection = it }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .selectableGroup().testTag("measurement_calendar_filters"),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            (listOf<MeasurementKind?>(null) + MeasurementCatalog.primary +
                enabledSecondary.filter { it in MeasurementCatalog.secondary }).distinct().forEach { kind ->
                GlucoseCaptureChip(
                    label = kind?.label() ?: "Todas",
                    selected = selection.filter == kind,
                    enabled = true,
                    motionAllowed = motion,
                    onClick = { selection = selection.filterBy(kind) },
                    modifier = Modifier.testTag("measurement_calendar_filter_${kind?.name ?: "ALL"}"),
                )
            }
        }
        CalendarChange(week.selectedDay to selection.filter, motion) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                Column(
                    Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    Text(
                        selection.date.format(measurementFullDate)
                            .replaceFirstChar { it.titlecase(measurementLocale) },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val count = week.selectedDay.events.size
                    SecondaryText(
                        if (count == 1) "1 evento del día" else "$count eventos del día",
                    )
                }
                if (week.selectedDay.events.isEmpty()) {
                    MeasurementPanel {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MeasurementIcon(R.drawable.ic_v2_calendar)
                            SectionTitle("Sin eventos", small = true)
                        }
                        SecondaryText(
                            if (selection.date > currentMoment.toLocalDate()) {
                                "Los eventos aparecerán aquí cuando ocurran."
                            } else {
                                "No hay eventos para este día y filtro."
                            },
                        )
                    }
                } else {
                    SecondaryText("Registros declarados por la persona cuidadora")
                    Column(Modifier.fillMaxWidth()) {
                        week.selectedDay.events.forEachIndexed { index, event ->
                            key(event.key) {
                                MeasurementTimelineRow(
                                    event = event,
                                    first = index == 0,
                                    last = index == week.selectedDay.events.lastIndex,
                                    motionAllowed = motion,
                                    onClick = onEvent?.takeIf { event.kind in actionableKinds }?.let {
                                        { it(event) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Refreshes deadlines while visible; it does no work in the background. */
@Composable
private fun calendarMoment(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = LocalDateTime.now()
                delay((60 - value.second).seconds)
            }
        }
    }.value
}

internal data class BitavidaCalendarDay(
    val date: LocalDate,
    val recordedCount: Int = 0,
    val unconfirmedCount: Int = 0,
    val notPerformedCount: Int = 0,
) {
    val hasEvents: Boolean get() = recordedCount + unconfirmedCount + notPerformedCount > 0
}

@Composable
private fun MeasurementWeekCalendar(
    week: MeasurementCalendarWeek,
    today: LocalDate,
    motionAllowed: Boolean,
    onSelection: (MeasurementCalendarSelection) -> Unit,
) {
    BitavidaWeekCalendar(
        selectedDate = week.selection.date,
        days = week.days.map { BitavidaCalendarDay(it.date, it.recordedCount, it.unconfirmedCount, it.notPerformedCount) },
        today = today,
        motionAllowed = motionAllowed,
        onDateSelected = { onSelection(week.selection.selectDay(it)) },
    )
}

/** Shared week presentation; each area supplies its own facts without adapting clinical types. */
@Composable
internal fun BitavidaWeekCalendar(
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
            CalendarWeekArrow(false, motionAllowed) { onDateSelected(selectedDate.minusWeeks(1)) }
            Text(
                days.first().date.format(measurementShortDate) + " – " +
                    days.last().date.format(measurementShortDate) + " " + days.last().date.year,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            CalendarWeekArrow(true, motionAllowed) { onDateSelected(selectedDate.plusWeeks(1)) }
        }
        CalendarChange(days.first().date, motionAllowed) {
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
                        CalendarDayTile(
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
            CalendarLegend(GlucoseOccurrenceState.RECORDED, "Registrada")
            CalendarLegend(GlucoseOccurrenceState.UNCONFIRMED, "Sin confirmar")
            CalendarLegend(GlucoseOccurrenceState.NOT_PERFORMED, "No realizada")
        }
        if (selectedDate != today) {
            MeasurementButton(
                "Volver a hoy",
                { onDateSelected(today) },
                style = MeasurementButtonStyle.TEXT,
            )
        }
    }
}

@Composable
private fun CalendarWeekArrow(next: Boolean, motionAllowed: Boolean, onClick: () -> Unit) {
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
            .testTag(if (next) "measurement_calendar_next_week" else "measurement_calendar_previous_week"),
    ) {
        Box(Modifier.graphicsLayer { rotationZ = if (next) 0f else 180f }) {
            MeasurementIcon(R.drawable.ic_v2_chevron)
        }
    }
}

@Composable
private fun CalendarDayTile(
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
            }.testTag("measurement_calendar_day_${day.date}"),
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
                if (!day.hasEvents) CalendarStatusMark(null, future)
                if (day.recordedCount > 0) CalendarStatusMark(GlucoseOccurrenceState.RECORDED)
                if (day.unconfirmedCount > 0) CalendarStatusMark(GlucoseOccurrenceState.UNCONFIRMED)
                if (day.notPerformedCount > 0) CalendarStatusMark(GlucoseOccurrenceState.NOT_PERFORMED)
            }
        }
    }
}

@Composable
private fun CalendarLegend(state: GlucoseOccurrenceState, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        CalendarStatusMark(state)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarStatusMark(state: GlucoseOccurrenceState?, future: Boolean = false) {
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
private fun CalendarChange(
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

@Composable
private fun MeasurementTimelineRow(
    event: MeasurementTimelineEvent,
    first: Boolean,
    last: Boolean,
    motionAllowed: Boolean,
    onClick: (() -> Unit)?,
) {
    val large = LocalDensity.current.fontScale >= 1.5f
    val lineColor = MaterialTheme.colorScheme.outline
    val markerColor = if (event.state == GlucoseOccurrenceState.RECORDED) {
        MaterialTheme.colorScheme.primary
    } else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = MaterialTheme.shapes.medium
    val source = remember(event.key) { MutableInteractionSource() }
    val timeFormat = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val time = event.at.format(DateTimeFormatter.ofPattern(timeFormat, measurementLocale))
    Row(
        Modifier.fillMaxWidth().drawBehind {
            val x = (if (large) 6.dp else 56.dp + BitavidaSpacing.Small + 6.dp).toPx()
            val markerY = 24.dp.toPx()
            drawLine(
                lineColor, Offset(x, if (first) markerY else 0f),
                Offset(x, if (last) markerY else size.height), 1.dp.toPx(),
            )
        }.padding(bottom = BitavidaSpacing.Small),
    ) {
        if (!large) {
            Surface(
                color = MaterialTheme.bitavidaColors.paleBlue,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.width(56.dp),
            ) {
                Text(
                    time,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = BitavidaSpacing.Small),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.width(BitavidaSpacing.Small))
        }
        Canvas(Modifier.padding(top = 18.dp).size(12.dp).clearAndSetSemantics {}) {
            drawCircle(markerColor, 4.dp.toPx())
        }
        Spacer(Modifier.width(BitavidaSpacing.Small))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = shape,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, lineColor.copy(alpha = .7f)),
            modifier = Modifier.weight(1f).heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                .then(
                    if (onClick == null) Modifier else Modifier
                        .bitavidaFluidInteraction(source, true, motionAllowed, shape = shape, restingElevationDp = 0f)
                        .clip(shape)
                        .clickable(
                            source, androidx.compose.material3.ripple(), role = Role.Button,
                            onClickLabel = "Consultar ${event.kind.label()}", onClick = onClick,
                        ),
                ).semantics(mergeDescendants = true) {}.testTag("measurement_timeline_${event.key}"),
        ) {
            Column(
                Modifier.padding(BitavidaSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                if (large) Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    MeasurementIcon(event.kind.icon(), 24)
                    Text(
                        event.kind.label(), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (onClick != null) MeasurementIcon(R.drawable.ic_v2_chevron, 16)
                }
                val record = event.record
                if (record != null) {
                    val unitStyle = MaterialTheme.typography.bodyMedium
                    Text(
                        buildAnnotatedString {
                            append(record.valueLabel())
                            if ((record.value as? MeasurementValue.Glucose)?.reading?.uppercase() !in setOf("HI", "LO")) {
                                withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontWeight = unitStyle.fontWeight)) {
                                    append(" ${record.kind.unit()}")
                                }
                            }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val context = when (val value = record.value) {
                        is MeasurementValue.Glucose -> value.context
                        is MeasurementValue.BloodPressure -> record.bloodPressureMetadata?.actualContext
                        is MeasurementValue.Weight -> null
                    }
                    context?.let { SecondaryText(it.captureLabel()) }
                } else {
                    Text(
                        if (event.state == GlucoseOccurrenceState.NOT_PERFORMED) "No realizada" else "Sin confirmar",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val occurrence = event.occurrence
                    if (event.kind != MeasurementKind.WEIGHT) {
                        occurrence?.suggestedContext?.let { SecondaryText(it.captureLabel()) }
                    }
                    SecondaryText(
                        occurrence?.windowEndAt?.let { end ->
                            "Ventana programada hasta " + end.format(
                                if (end.toLocalDate() == event.at.toLocalDate()) {
                                    DateTimeFormatter.ofPattern(timeFormat, measurementLocale)
                                } else DateTimeFormatter.ofPattern("d MMM · $timeFormat", measurementLocale),
                            )
                        } ?: "Medición programada",
                    )
                    (event.source as? GlucoseHistoryEvent.NotPerformed)?.decision?.reason
                        ?.takeIf(String::isNotBlank)?.let { SecondaryText(it) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    CalendarStatusMark(event.state)
                    Text(
                        when (event.state) {
                            GlucoseOccurrenceState.RECORDED -> "Registrada"
                            GlucoseOccurrenceState.UNCONFIRMED -> "Pendiente de confirmación"
                            GlucoseOccurrenceState.NOT_PERFORMED -> "Declaración del cuidador"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
