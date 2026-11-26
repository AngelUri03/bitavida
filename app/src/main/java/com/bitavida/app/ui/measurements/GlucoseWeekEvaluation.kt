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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseTargetRange
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.usecase.GlucosePlotAxis
import com.bitavida.app.domain.usecase.GlucosePlotDay
import com.bitavida.app.domain.usecase.MeasurementDayState
import com.bitavida.app.domain.usecase.MeasurementHistoryWeek
import com.bitavida.app.domain.usecase.projectGlucoseWeekPlot
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
internal fun GlucoseWeekEvaluation(
    week: MeasurementHistoryWeek,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onHistory: (() -> Unit)? = null,
    motionEnabledOverride: Boolean? = null,
    title: String = "Evaluación semanal",
    targetRange: GlucoseTargetRange? = null,
    referenceValue: BigDecimal? = null,
    referenceLabel: String? = null,
    unit: String = "mg/dL",
    historyContentDescription: String = "glucosa capilar",
    testTagPrefix: String = "glucose",
) {
    val motionAllowed = rememberBitavidaMotionAllowed(motionEnabledOverride)
    val duration = if (motionAllowed) BitavidaFluidMotion.Selection else 0
    val (_, plotDays, axis, unplottedRecordCount) =
        remember(week, targetRange, referenceValue) {
            projectGlucoseWeekPlot(
                week = week,
                targetRange = targetRange,
                referenceValue = referenceValue,
            )
        }
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val labels = axis?.ticks?.map { it.stripTrailingZeros().toPlainString() }.orEmpty()
    val axisWidth = with(density) {
        (labels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0).toDp()
    }.coerceAtMost(88.dp)
    val lineHeight = with(density) { labelStyle.lineHeight.toDp() }
    val inset = (lineHeight / 2).coerceAtLeast(12.dp)
    // Empty weeks retain the same graphical lanes, without fabricated numerical bounds.
    val measuredPlotHeight =
        (lineHeight * (axis?.ticks?.size?.minus(1) ?: 0)) + (inset * 2)
    val plotHeight = if (axis == null) {
        144.dp
    } else {
        measuredPlotHeight.coerceAtLeast(192.dp)
    }

    MeasurementPanel(modifier = Modifier.testTag("${testTagPrefix}_week_evaluation")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                SecondaryText(
                    week.start.format(measurementShortDate) + " – " +
                        (
                            week.days.lastOrNull()?.date ?: week.start
                            ).format(measurementShortDate) + " · " + unit,
                )
            }
            if (onHistory != null) {
                IconButton(
                    onClick = onHistory,
                    modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget)
                        .testTag("${testTagPrefix}_open_history")
                        .semantics {
                            contentDescription =
                                "Ver historial completo de $historyContentDescription"
                        },
                ) {
                    MeasurementIcon(
                        R.drawable.ic_v2_chevron,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if ((referenceValue != null) && (referenceLabel != null)) {
            Text(
                text = "$referenceLabel: " +
                    referenceValue.stripTrailingZeros().toPlainString() +
                    " $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                if (axis == null) 0.dp else BitavidaSpacing.Small,
            ),
        ) {
            if (axis != null) {
                Box(Modifier.width(axisWidth).height(plotHeight).clearAndSetSemantics {}) {
                    axis.ticks.forEach { tick ->
                        Text(
                            text = tick.stripTrailingZeros().toPlainString(),
                            style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().glucoseAxisOffset(
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
                // Keep the complete week available while showing five calm lanes when space allows.
                val visibleDays = if (
                    (maxWidth < 280.dp) || (density.fontScale >= 1.5f)
                ) {
                    3
                } else {
                    5
                }
                val laneWidth = (maxWidth / visibleDays).coerceAtLeast(
                    if (density.fontScale >= 1.5f) 88.dp
                    else BitavidaSpacing.MinimumTouchTarget,
                )
                val chartWidth = laneWidth * plotDays.size
                val scroll = rememberScrollState()
                val viewport = maxWidth
                var positioned by remember(week.start) { mutableStateOf(value = false) }
                val activeIndex = ChronoUnit.DAYS
                    .between(week.start, selectedDate)
                    .toInt()
                    .coerceIn(0, (plotDays.size - 1).coerceAtLeast(0))
                val selection by animateFloatAsState(
                    targetValue = activeIndex.toFloat(),
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                    label = "glucose_day_focus",
                )
                LaunchedEffect(
                    selectedDate,
                    week.start,
                    laneWidth,
                    viewport,
                    scroll.maxValue,
                    motionAllowed,
                ) {
                    val index = ChronoUnit.DAYS
                        .between(week.start, selectedDate)
                        .toInt()
                        .coerceIn(0, (plotDays.size - 1).coerceAtLeast(0))
                    val x = with(density) {
                        (
                            (laneWidth.toPx() * (index + 0.5f)) -
                                (viewport.toPx() / 2)
                            ).roundToInt()
                    }
                    val target = x.coerceIn(0, scroll.maxValue)
                    val animate = positioned && motionAllowed
                    if (animate) {
                        scroll.animateScrollTo(
                            target,
                            tween(duration, easing = FastOutSlowInEasing),
                        )
                    } else {
                        scroll.scrollTo(target)
                        if ((scroll.maxValue > 0) || (plotDays.size <= visibleDays)) {
                            positioned = true
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                    Box(Modifier.width(chartWidth)) {
                        GlucosePlotGrid(
                            axis = axis,
                            targetRange = targetRange,
                            referenceValue = referenceValue,
                            plotHeight = plotHeight,
                            inset = inset,
                        )
                        Row(Modifier.width(chartWidth).selectableGroup()) {
                            plotDays.forEachIndexed { index, day ->
                                GlucoseDayLane(
                                    day = day,
                                    axis = axis,
                                    isSelected = day.day.date == selectedDate,
                                    modifier = Modifier.width(laneWidth),
                                    plotHeight = plotHeight,
                                    inset = inset,
                                    duration = duration,
                                    unit = unit,
                                    selectionOffset = { selection - index },
                                ) {
                                    onSelectDate(day.day.date)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (plotDays.size > 3) {
            SecondaryText(
                if (plotDays.size == 7) {
                    "Semana completa · desliza para recorrer los siete días."
                } else {
                    "Desliza para recorrer el periodo."
                },
            )
        }
        if (unplottedRecordCount > 0) {
            SecondaryText(
                "$unplottedRecordCount lecturas sin valor numérico; disponibles en el historial.",
            )
        }
        AnimatedContent(
            targetState = selectedDate,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            transitionSpec = {
                val forward = targetState > initialState
                val enter = if (motionAllowed) fadeIn(tween(duration)) +
                    slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) {
                        if (forward) it / 16 else -it / 16
                    } else EnterTransition.None
                val exit = if (motionAllowed) fadeOut(tween(BitavidaFluidMotion.Press)) +
                    slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) {
                        if (forward) -it / 16 else it / 16
                    } else ExitTransition.None
                (enter togetherWith exit).using(SizeTransform { _, _ -> tween(duration) })
            },
            label = "glucose_day_summary",
        ) { date ->
            Column(
                Modifier.fillMaxWidth().then(
                    if (date != selectedDate) Modifier.clearAndSetSemantics {} else Modifier,
                ),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) { GlucoseSelectedDaySummary(week, date, unit) }
        }
    }
}

@Composable
private fun Modifier.glucoseAxisOffset(
    fraction: Float,
    plotHeight: Dp,
    inset: Dp,
    lineHeight: Dp,
): Modifier {
    val density = LocalDensity.current
    return this.offset {
        with(density) {
            val plotTop = inset.toPx()
            val innerHeight = (plotHeight - (inset * 2)).toPx()
            val labelOffset = lineHeight.toPx() / 2
            val scaledOffset = fraction * innerHeight
            val baseline = plotTop + scaledOffset
            val verticalPosition = baseline - labelOffset
            IntOffset(x = 0, y = verticalPosition.roundToInt())
        }
    }
}

@Composable
private fun GlucosePlotGrid(
    axis: GlucosePlotAxis?,
    targetRange: GlucoseTargetRange?,
    referenceValue: BigDecimal?,
    plotHeight: Dp,
    inset: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val guideColor = scheme.outline.copy(alpha = 0.24f)
    Canvas(
        Modifier.fillMaxWidth()
            .height(plotHeight)
            .clearAndSetSemantics {},
    ) {
        val plotTop = inset.toPx()
        val innerHeight = size.height - (plotTop * 2)
        if ((axis != null) && (targetRange != null)) {
            val upperOffset =
                (1f - axis.fraction(targetRange.upper)) * innerHeight
            val lowerOffset =
                (1f - axis.fraction(targetRange.lower)) * innerHeight
            val upperY = plotTop + upperOffset
            val lowerY = plotTop + lowerOffset
            drawRect(
                color = scheme.primaryContainer.copy(alpha = 0.34f),
                topLeft = Offset(0f, upperY),
                size = Size(size.width, (lowerY - upperY).coerceAtLeast(0f)),
            )
            listOf(upperY, lowerY).forEach { y ->
                drawLine(
                    color = scheme.primary.copy(alpha = 0.42f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        val fractions = axis?.ticks?.map { 1f - axis.fraction(it) }
            ?: listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        fractions.distinct().forEach { fraction ->
            val scaledOffset = fraction * innerHeight
            val y = plotTop + scaledOffset
            drawLine(
                color = guideColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if ((axis != null) && (referenceValue != null)) {
            val y = plotTop +
                (1f - axis.fraction(referenceValue)) * innerHeight
            drawLine(
                color = scheme.secondary.copy(alpha = 0.82f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                ),
            )
        }
    }
}

@Composable
private fun GlucoseDayLane(
    day: GlucosePlotDay,
    axis: GlucosePlotAxis?,
    isSelected: Boolean,
    modifier: Modifier,
    plotHeight: Dp,
    inset: Dp,
    duration: Int,
    unit: String,
    selectionOffset: () -> Float,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val labelBackground by animateColorAsState(
        targetValue = if (isSelected) {
            scheme.primaryContainer
        } else {
            scheme.surface.copy(alpha = 0f)
        },
        animationSpec = tween(duration),
        label = "glucose_day_label_background",
    )
    val unselectedLabel = when (day.day.state) {
        MeasurementDayState.WITH_RECORDS -> scheme.onSurface
        MeasurementDayState.WITHOUT_RECORDS ->
            scheme.onSurfaceVariant.copy(alpha = 0.76f)
        MeasurementDayState.FUTURE ->
            scheme.onSurfaceVariant.copy(alpha = 0.46f)
    }
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) scheme.primary else unselectedLabel,
        animationSpec = tween(duration),
        label = "glucose_day_label_color",
    )
    val pointEmphasis by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(duration),
        label = "glucose_day_points",
    )
    Column(
        modifier = modifier
            .clip(shape)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onSelect)
            .semantics {
                contentDescription =
                    day.day.date.format(measurementFullDate) + ", " +
                        day.day.description() +
                        if (day.dots.isEmpty()) {
                            ""
                        } else {
                            ", " + day.dots.joinToString { (point) ->
                                "${point.value.toPlainString()} $unit"
                            }
                        }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Canvas(
            Modifier.fillMaxWidth()
                .height(plotHeight)
                .clipToBounds()
                .clearAndSetSemantics {},
        ) {
            val gap = 4.dp.toPx()
            val radius = CornerRadius(10.dp.toPx())
            // Every clipped lane paints a fragment of the same travelling selection wash.
            val focusTravel = selectionOffset() * size.width
            val focusLeft = focusTravel + gap
            val horizontalInsets = gap * 2
            val focusWidth = (size.width - horizontalInsets).coerceAtLeast(0f)
            drawRoundRect(
                color = scheme.primaryContainer.copy(alpha = 0.58f),
                topLeft = Offset(focusLeft, 0f),
                size = Size(focusWidth, size.height),
                cornerRadius = radius,
            )
            if ((axis != null) && day.dots.isNotEmpty()) {
                val plotTop = inset.toPx()
                val innerHeight = size.height - (plotTop * 2)
                val dotCount = day.dots.size
                val spread = minOf(
                    6.dp.toPx() * (dotCount - 1).coerceAtLeast(0),
                    24.dp.toPx(),
                    size.width * 0.42f,
                )
                val laneCenter = size.width / 2f
                val halfSpread = spread / 2f
                val firstX = laneCenter - halfSpread
                val step = if (dotCount > 1) spread / (dotCount - 1) else 0f
                day.dots.forEachIndexed { index, (point) ->
                    val horizontalOffset = step * index
                    val x = firstX + horizontalOffset
                    val valueFraction = 1f - axis.fraction(point.value)
                    val verticalOffset = valueFraction * innerHeight
                    val y = plotTop + verticalOffset
                    val center = Offset(x = x, y = y)
                    if (isSelected) {
                        drawCircle(
                            color = scheme.primary.copy(alpha = 0.14f),
                            radius = 9.dp.toPx(),
                            center = center,
                        )
                    }
                    drawCircle(
                        color = scheme.surface,
                        radius = 7.dp.toPx(),
                        center = center,
                    )
                    drawCircle(
                        color = scheme.primary.copy(alpha = if (isSelected) 1f else 0.8f),
                        radius = (4.5f + pointEmphasis).dp.toPx(),
                        center = center,
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
                day.day.date.format(measurementWeekday).replace(".", ""),
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

@Composable
private fun GlucoseSelectedDaySummary(
    week: MeasurementHistoryWeek,
    selectedDate: LocalDate,
    unit: String,
) {
    val records = week.recordsOn(selectedDate).sortedWith(
        compareBy(MeasurementRecord::occurredAt)
            .thenBy(MeasurementRecord::capturedAt),
    )
    val isFuture = week.days
        .firstOrNull { (date) -> date == selectedDate }
        ?.state == MeasurementDayState.FUTURE
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
            .padding(BitavidaSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Text(
            selectedDate.format(
                DateTimeFormatter.ofPattern("EEE d MMM", measurementLocale),
            ).replaceFirstChar { it.titlecase(measurementLocale) },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (records.isEmpty()) {
            SecondaryText(
                if (isFuture) {
                    "Día futuro · sin registros."
                } else {
                    "Sin registros ese día."
                },
            )
        } else {
            records.forEach { record ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(
                        BitavidaSpacing.ExtraSmall,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            BitavidaSpacing.Small,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                record.valueLabel(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                unit,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            record.occurredAt.format(
                                DateTimeFormatter.ofPattern(
                                    "HH:mm",
                                    measurementLocale,
                                ),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    record.complement()?.let { context ->
                        Text(
                            context,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
