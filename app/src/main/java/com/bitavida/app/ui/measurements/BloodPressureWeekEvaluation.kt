package com.bitavida.app.ui.measurements

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.BloodPressurePlotDay
import com.bitavida.app.domain.usecase.GlucosePlotAxis
import com.bitavida.app.domain.usecase.MeasurementDayState
import com.bitavida.app.domain.usecase.MeasurementHistoryWeek
import com.bitavida.app.domain.usecase.projectBloodPressurePlot
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
internal fun BloodPressureWeekEvaluation(
    week: MeasurementHistoryWeek,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onHistory: (() -> Unit)? = null,
    motionEnabledOverride: Boolean? = null,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(
        motionEnabledOverride,
    )
    val duration =
        if (motionAllowed) BitavidaFluidMotion.Selection else 0
    val (_, plotDays, axis) = remember(week) {
        projectBloodPressurePlot(week)
    }
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val labels = axis?.ticks?.map {
        it.stripTrailingZeros().toPlainString()
    }.orEmpty()
    val axisWidth = with(density) {
        (
            labels.maxOfOrNull {
                measurer.measure(it, labelStyle).size.width
            } ?: 0
            ).toDp()
    }.coerceAtMost(88.dp)
    val lineHeight = with(density) { labelStyle.lineHeight.toDp() }
    val inset = (lineHeight / 2).coerceAtLeast(12.dp)
    val plotHeight = if (axis == null) {
        144.dp
    } else {
        (
            (lineHeight * (axis.ticks.size - 1)) + (inset * 2)
            ).coerceAtLeast(192.dp)
    }

    MeasurementPanel(
        modifier = Modifier.testTag("blood_pressure_week_evaluation"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                Text(
                    "Evaluación semanal",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                SecondaryText(
                    week.start.format(measurementShortDate) + " – " +
                        (
                            week.days.lastOrNull()?.date ?: week.start
                            ).format(measurementShortDate) + " · mmHg",
                )
            }
            onHistory?.let { openHistory ->
                IconButton(
                    onClick = openHistory,
                    modifier = Modifier
                        .size(BitavidaSpacing.MinimumTouchTarget)
                        .semantics {
                            contentDescription =
                                "Ver historial completo de presión arterial"
                        },
                ) {
                    MeasurementIcon(
                        R.drawable.ic_v2_chevron,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        PressureSeriesKey()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                if (axis == null) 0.dp else BitavidaSpacing.Small,
            ),
        ) {
            if (axis != null) {
                Box(
                    Modifier.width(axisWidth)
                        .height(plotHeight)
                        .clearAndSetSemantics {},
                ) {
                    axis.ticks.forEach { tick ->
                        Text(
                            tick.stripTrailingZeros().toPlainString(),
                            style = labelStyle,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                                .pressureAxisOffset(
                                    1f - axis.fraction(tick),
                                    plotHeight,
                                    inset,
                                    lineHeight,
                                ),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val visibleDays = if (
                    (maxWidth < 280.dp) ||
                    (density.fontScale >= 1.5f)
                ) {
                    3
                } else {
                    5
                }
                val laneWidth = (maxWidth / visibleDays).coerceAtLeast(
                    if (density.fontScale >= 1.5f) {
                        88.dp
                    } else {
                        BitavidaSpacing.MinimumTouchTarget
                    },
                )
                val chartWidth = laneWidth * plotDays.size
                val scroll = rememberScrollState()
                var positioned by remember(week.start) {
                    mutableStateOf(value = false)
                }
                val activeIndex = ChronoUnit.DAYS
                    .between(week.start, selectedDate)
                    .toInt()
                    .coerceIn(
                        0,
                        (plotDays.size - 1).coerceAtLeast(0),
                    )
                val selection by animateFloatAsState(
                    activeIndex.toFloat(),
                    tween(duration, easing = FastOutSlowInEasing),
                    label = "blood_pressure_day_focus",
                )
                LaunchedEffect(
                    selectedDate,
                    week.start,
                    laneWidth,
                    maxWidth,
                    scroll.maxValue,
                    motionAllowed,
                ) {
                    val x = with(density) {
                        (
                            laneWidth.toPx() * (activeIndex + 0.5f) -
                                maxWidth.toPx() / 2
                            ).roundToInt()
                    }
                    val target = x.coerceIn(0, scroll.maxValue)
                    if (positioned && motionAllowed) {
                        scroll.animateScrollTo(
                            target,
                            tween(
                                duration,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    } else {
                        scroll.scrollTo(target)
                        if (
                            scroll.maxValue > 0 ||
                            plotDays.size <= visibleDays
                        ) {
                            positioned = true
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().horizontalScroll(scroll),
                ) {
                    Box(Modifier.width(chartWidth)) {
                        PressurePlotGrid(axis, plotHeight, inset)
                        Row(
                            Modifier.width(chartWidth)
                                .selectableGroup(),
                        ) {
                            plotDays.forEachIndexed { index, day ->
                                PressureDayLane(
                                    day = day,
                                    axis = axis,
                                    selected =
                                        day.day.date == selectedDate,
                                    modifier = Modifier.width(laneWidth),
                                    plotHeight = plotHeight,
                                    inset = inset,
                                    duration = duration,
                                    selectionOffset = {
                                        selection - index
                                    },
                                ) {
                                    onSelectDate(day.day.date)
                                }
                            }
                        }
                    }
                }
            }
        }
        SecondaryText(
            "Semana completa · desliza para recorrer los siete días.",
        )
        AnimatedContent(
            targetState = selectedDate,
            modifier = Modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Polite
            },
            transitionSpec = {
                val forward = targetState > initialState
                val enter = if (motionAllowed) {
                    fadeIn(tween(duration)) +
                        slideInHorizontally(
                            tween(
                                duration,
                                easing = FastOutSlowInEasing,
                            ),
                        ) { if (forward) it / 16 else -it / 16 }
                } else {
                    EnterTransition.None
                }
                val exit = if (motionAllowed) {
                    fadeOut(tween(BitavidaFluidMotion.Press)) +
                        slideOutHorizontally(
                            tween(
                                duration,
                                easing = FastOutSlowInEasing,
                            ),
                        ) { if (forward) -it / 16 else it / 16 }
                } else {
                    ExitTransition.None
                }
                (enter togetherWith exit).using(
                    SizeTransform { _, _ -> tween(duration) },
                )
            },
            label = "blood_pressure_day_summary",
        ) { date ->
            PressureSelectedDaySummary(week, date)
        }
    }
}

@Composable
private fun PressureSeriesKey() {
    Row(
        horizontalArrangement =
            Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeriesKey(
            MaterialTheme.colorScheme.primary,
            "Sistólica",
        )
        SeriesKey(
            MaterialTheme.colorScheme.tertiary,
            "Diastólica",
        )
    }
}

@Composable
private fun SeriesKey(color: Color, label: String) {
    Row(
        horizontalArrangement =
            Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(color)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PressurePlotGrid(
    axis: GlucosePlotAxis?,
    plotHeight: Dp,
    inset: Dp,
) {
    val guide = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    Canvas(
        Modifier.fillMaxWidth().height(plotHeight)
            .clearAndSetSemantics {},
    ) {
        val top = inset.toPx()
        val inner = size.height - top * 2
        val fractions = axis?.ticks?.map {
            1f - axis.fraction(it)
        } ?: listOf(0f, .25f, .5f, .75f, 1f)
        fractions.distinct().forEach { fraction ->
            val y = top + fraction * inner
            drawLine(
                guide,
                Offset(0f, y),
                Offset(size.width, y),
                1.dp.toPx(),
            )
        }
    }
}

@Composable
private fun PressureDayLane(
    day: BloodPressurePlotDay,
    axis: GlucosePlotAxis?,
    selected: Boolean,
    modifier: Modifier,
    plotHeight: Dp,
    inset: Dp,
    duration: Int,
    selectionOffset: () -> Float,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val labelBackground by animateColorAsState(
        if (selected) scheme.primaryContainer
        else scheme.surface.copy(alpha = 0f),
        tween(duration),
        label = "pressure_day_label_background",
    )
    val labelColor by animateColorAsState(
        if (selected) scheme.primary else when (day.day.state) {
            MeasurementDayState.WITH_RECORDS -> scheme.onSurface
            MeasurementDayState.WITHOUT_RECORDS ->
                scheme.onSurfaceVariant.copy(alpha = .76f)
            MeasurementDayState.FUTURE ->
                scheme.onSurfaceVariant.copy(alpha = .46f)
        },
        tween(duration),
        label = "pressure_day_label_color",
    )
    val pointEmphasis by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(duration),
        label = "pressure_points",
    )
    Column(
        modifier = modifier.clip(shape).selectable(
            selected = selected,
            role = Role.Tab,
            onClick = onSelect,
        ).semantics {
            contentDescription =
                day.day.date.format(measurementFullDate) +
                    if (day.readings.isEmpty()) {
                        ", sin registros"
                    } else {
                        ", " + day.readings.joinToString {
                                (_, systolic, diastolic) ->
                            systolic.toPlainString() + " sobre " +
                                diastolic.toPlainString()
                        }
                    }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        val primary = scheme.primary
        val tertiary = scheme.tertiary
        Canvas(
            Modifier.fillMaxWidth().height(plotHeight)
                .clipToBounds().clearAndSetSemantics {},
        ) {
            val gap = 4.dp.toPx()
            val focusLeft = selectionOffset() * size.width + gap
            drawRoundRect(
                color = scheme.primaryContainer.copy(alpha = .58f),
                topLeft = Offset(focusLeft, 0f),
                size = Size(
                    (size.width - gap * 2).coerceAtLeast(0f),
                    size.height,
                ),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )
            if (axis != null) {
                val top = inset.toPx()
                val inner = size.height - top * 2
                val count = day.readings.size
                val spread = minOf(
                    6.dp.toPx() * (count - 1).coerceAtLeast(0),
                    24.dp.toPx(),
                    size.width * .42f,
                )
                val firstX = size.width / 2 - spread / 2
                val step =
                    if (count > 1) spread / (count - 1) else 0f
                day.readings.forEachIndexed { index, (_, systolic, diastolic) ->
                    val x = firstX + step * index
                    val systolicY = top +
                        (1f - axis.fraction(systolic)) * inner
                    val diastolicY = top +
                        (1f - axis.fraction(diastolic)) * inner
                    drawLine(
                        color = scheme.outline.copy(alpha = .5f),
                        start = Offset(x, systolicY),
                        end = Offset(x, diastolicY),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    drawPressurePoint(
                        Offset(x, systolicY),
                        primary,
                        selected,
                        pointEmphasis,
                        scheme.surface,
                    )
                    drawPressurePoint(
                        Offset(x, diastolicY),
                        tertiary,
                        selected,
                        pointEmphasis,
                        scheme.surface,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .padding(vertical = BitavidaSpacing.ExtraSmall)
                .clip(shape)
                .background(labelBackground)
                .padding(
                    horizontal = BitavidaSpacing.Small,
                    vertical = BitavidaSpacing.ExtraSmall,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                day.day.date.format(measurementWeekday)
                    .replace(".", ""),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                day.day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = labelColor,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPressurePoint(
    center: Offset,
    color: Color,
    selected: Boolean,
    emphasis: Float,
    surface: Color,
) {
    if (selected) {
        drawCircle(
            color.copy(alpha = .14f),
            9.dp.toPx(),
            center,
        )
    }
    drawCircle(surface, 7.dp.toPx(), center)
    drawCircle(
        color.copy(alpha = if (selected) 1f else .82f),
        (4.5f + emphasis).dp.toPx(),
        center,
    )
}

@Composable
private fun PressureSelectedDaySummary(
    week: MeasurementHistoryWeek,
    selectedDate: LocalDate,
) {
    val records = week.recordsOn(selectedDate)
        .sortedBy { it.occurredAt }
    val future = week.days.firstOrNull {
        it.date == selectedDate
    }?.state == MeasurementDayState.FUTURE
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
            )
            .padding(BitavidaSpacing.Small),
        verticalArrangement =
            Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Text(
            selectedDate.format(
                DateTimeFormatter.ofPattern(
                    "EEE d MMM",
                    measurementLocale,
                ),
            ).replaceFirstChar {
                it.titlecase(measurementLocale)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (records.isEmpty()) {
            SecondaryText(
                if (future) "Día futuro · sin registros."
                else "Sin registros ese día.",
            )
        } else {
            records.forEach { record ->
                val value =
                    record.value as MeasurementValue.BloodPressure
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(BitavidaSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            value.systolic + " / " +
                                value.diastolic + " mmHg",
                            style =
                                MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            record.bloodPressureMetadata
                                ?.actualContext
                                ?.captureLabel()
                                .orEmpty(),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        record.occurredAt.format(
                            DateTimeFormatter.ofPattern(
                                "HH:mm",
                                measurementLocale,
                            ),
                        ),
                        style =
                            MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.pressureAxisOffset(
    fraction: Float,
    plotHeight: Dp,
    inset: Dp,
    lineHeight: Dp,
): Modifier {
    val density = LocalDensity.current
    return offset {
        with(density) {
            val top = inset.toPx()
            val inner = (plotHeight - inset * 2).toPx()
            IntOffset(
                0,
                (
                    top + fraction * inner -
                        lineHeight.toPx() / 2
                    ).roundToInt(),
            )
        }
    }
}
