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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseEntryKind
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.WeightEntryKind
import com.bitavida.app.domain.usecase.GlucoseHistoryEvent
import com.bitavida.app.domain.usecase.GlucoseHistoryPeriod
import com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind
import com.bitavida.app.domain.usecase.GlucoseHistoryStatistics
import com.bitavida.app.domain.usecase.GlucosePlotAxis
import com.bitavida.app.domain.usecase.MeasurementDayState
import com.bitavida.app.domain.usecase.projectGlucoseHistoryPeriod
import com.bitavida.app.domain.usecase.projectGlucoseWeekPlot
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.medical.TodayStatusIcon
import com.bitavida.app.ui.medical.TodayStatusVisual
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
internal fun GlucoseHistoryContent(
    records: List<MeasurementRecord>,
    today: LocalDate,
    initialPeriodKind: GlucoseHistoryPeriodKind = GlucoseHistoryPeriodKind.WEEK,
    followUp: GlucoseFollowUp? = null,
    decisions: List<GlucoseOccurrenceDecision> = emptyList(),
    currentMoment: LocalDateTime = today.atTime(LocalTime.MAX),
    onRecord: ((MeasurementRecord) -> Unit)? = null,
    onOccurrence: ((GlucoseScheduledOccurrence) -> Unit)? = null,
    measurementKind: MeasurementKind = MeasurementKind.CAPILLARY_GLUCOSE,
    unit: String = "mg/dL",
    measurementNoun: String = "glucosa",
    referenceValue: BigDecimal? = null,
    referenceLabel: String? = null,
) {
    var periodKindName by rememberSaveable {
        mutableStateOf(initialPeriodKind.name)
    }
    var anchorText by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val periodKind = GlucoseHistoryPeriodKind.valueOf(periodKindName)
    val anchor = LocalDate.parse(anchorText)
    val period = remember(
        records,
        anchor,
        periodKind,
        today,
        followUp,
        decisions,
        currentMoment,
        measurementKind,
    ) {
        projectGlucoseHistoryPeriod(
            records = records,
            anchor = anchor,
            kind = periodKind,
            today = today,
            followUp = followUp,
            decisions = decisions,
            now = currentMoment,
            measurementKind = measurementKind,
        )
    }
    val selectedDate = LocalDate.parse(selectedText).coerceIn(
        period.start,
        period.endInclusive,
    )

    GlucoseHistoryAnalysis(
        period = period,
        selectedDate = selectedDate,
        today = today,
        onSelectDate = { selectedText = it.toString() },
        onKind = { kind ->
            periodKindName = kind.name
            anchorText = selectedDate.toString()
        },
        onPrevious = {
            anchorText = when (periodKind) {
                GlucoseHistoryPeriodKind.WEEK -> anchor.minusWeeks(1)
                GlucoseHistoryPeriodKind.MONTH -> anchor.minusMonths(1)
            }.toString()
        },
        onNext = {
            val candidate = when (periodKind) {
                GlucoseHistoryPeriodKind.WEEK -> anchor.plusWeeks(1)
                GlucoseHistoryPeriodKind.MONTH -> anchor.plusMonths(1)
            }
            anchorText = minOf(candidate, today).toString()
        },
        unit = unit,
        measurementNoun = measurementNoun,
        referenceValue = referenceValue,
        referenceLabel = referenceLabel,
    )

    SelectedDayRecords(
        period = period,
        selectedDate = selectedDate,
        onRecord = onRecord,
        onOccurrence = onOccurrence,
        measurementKind = measurementKind,
        unit = unit,
    )

    Text(
        "Lecturas y estados confirmados por la persona cuidadora",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GlucoseHistoryAnalysis(
    period: GlucoseHistoryPeriod,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onKind: (GlucoseHistoryPeriodKind) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    unit: String,
    measurementNoun: String,
    referenceValue: BigDecimal?,
    referenceLabel: String?,
) {
    MeasurementPanel(
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        MeasurementHistoryAnalysisHeader(kind = period.kind, onKind = onKind)
        MeasurementHistoryPeriodNavigation(
            kind = period.kind,
            start = period.start,
            endInclusive = period.endInclusive,
            canMoveForward = period.endInclusive < today,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        HistoryMetrics(period.statistics, unit, measurementNoun)
        if ((referenceValue != null) && (referenceLabel != null)) {
            Text(
                text = "$referenceLabel: " +
                    referenceValue.stripTrailingZeros().toPlainString() +
                    " $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        GlucoseEvolutionChart(
            period = period,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            unit = unit,
            referenceValue = referenceValue,
        )
        SecondaryText(
            if (period.kind == GlucoseHistoryPeriodKind.WEEK) {
                "Semana completa · desliza para recorrer los siete días."
            } else {
                "Desliza para recorrer todos los días del mes."
            },
        )
        if (period.history.unplottedRecordCount > 0) {
            SecondaryText(
                "${period.history.unplottedRecordCount} lecturas HI/LO se conservan en el día correspondiente.",
            )
        }
    }
}

@Composable
internal fun MeasurementHistoryAnalysisHeader(
    kind: GlucoseHistoryPeriodKind,
    onKind: (GlucoseHistoryPeriodKind) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = (maxWidth < 300.dp) || (LocalDensity.current.fontScale >= 1.5f)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                Text(
                    "Análisis del periodo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                CompactPeriodSelector(kind, onKind)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Análisis del periodo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                CompactPeriodSelector(kind, onKind)
            }
        }
    }
}

@Composable
private fun CompactPeriodSelector(
    kind: GlucoseHistoryPeriodKind,
    onKind: (GlucoseHistoryPeriodKind) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    ) {
        Row(
            modifier = Modifier.padding(2.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PeriodSegment(
                label = "Semana",
                selected = kind == GlucoseHistoryPeriodKind.WEEK,
            ) {
                onKind(GlucoseHistoryPeriodKind.WEEK)
            }
            PeriodSegment(
                label = "Mes",
                selected = kind == GlucoseHistoryPeriodKind.MONTH,
            ) {
                onKind(GlucoseHistoryPeriodKind.MONTH)
            }
        }
    }
}

@Composable
private fun PeriodSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        animationSpec = tween(duration),
        label = "history period background",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(duration),
        label = "history period foreground",
    )
    Surface(
        modifier = Modifier
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = background,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = BitavidaSpacing.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = foreground)
        }
    }
}

@Composable
internal fun MeasurementHistoryPeriodNavigation(
    kind: GlucoseHistoryPeriodKind,
    start: LocalDate,
    endInclusive: LocalDate,
    canMoveForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PeriodArrow(previous = true, enabled = true, onClick = onPrevious)
        Text(
            text = periodLabel(kind, start, endInclusive),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        PeriodArrow(previous = false, enabled = canMoveForward, onClick = onNext)
    }
}

@Composable
private fun PeriodArrow(
    previous: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics {
            contentDescription = if (previous) "Periodo anterior" else "Periodo siguiente"
        },
    ) {
        MeasurementIcon(
            resource = if (previous) {
                R.drawable.ic_v2_arrow_back
            } else {
                R.drawable.ic_v2_chevron
            },
            size = 20,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

@Composable
private fun HistoryMetrics(
    statistics: GlucoseHistoryStatistics,
    unit: String,
    measurementNoun: String,
) {
    val average = statistics.average?.asReading()
    val range = if ((statistics.minimum != null) && (statistics.maximum != null)) {
        "${statistics.minimum.asReading()}–${statistics.maximum.asReading()}"
    } else {
        "—"
    }
    MeasurementHistoryAverageMetric(average, unit = unit)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        MeasurementHistoryInlineMetric(
            label = "Mín · máx",
            value = range,
            support = unit,
            modifier = Modifier.weight(1f),
        )
        MeasurementHistoryInlineMetric(
            label = "Mediciones",
            value = statistics.totalMeasurements.toString(),
            support = statistics.nonNumericMeasurements.takeIf { it > 0 }?.let {
                "$it HI/LO"
            } ?: "en el periodo",
            modifier = Modifier.weight(1f),
        )
    }
    SecondaryText(
        "Los datos mostrados ayudan a visualizar el comportamiento del $measurementNoun.",
    )
    SecondaryText(
        "Esta vista no sustituye la valoración del equipo de salud.",
    )
}

@Composable
internal fun MeasurementHistoryAverageMetric(
    average: String?,
    modifier: Modifier = Modifier,
    unit: String = "mg/dL",
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(
            "Promedio del periodo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                average ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MeasurementHistoryInlineMetric(
    label: String,
    value: String,
    support: String,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                support,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun GlucoseEvolutionChart(
    period: GlucoseHistoryPeriod,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    unit: String,
    referenceValue: BigDecimal?,
    fitAllDays: Boolean = false,
) {
    val (_, plotDays, axis) = remember(period.history, referenceValue) {
        projectGlucoseWeekPlot(
            week = period.history,
            referenceValue = referenceValue,
        )
    }
    val density = LocalDensity.current
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val compactMonth = period.kind == GlucoseHistoryPeriodKind.MONTH
    val plotHeight = if (compactMonth) 164.dp else 190.dp
    val axisWidth = if (axis == null) 0.dp else 44.dp
    val inset = 12.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            if (axis == null) 0.dp else BitavidaSpacing.ExtraSmall,
        ),
    ) {
        if (axis != null) {
            Box(
                Modifier
                    .width(axisWidth)
                    .height(plotHeight)
                    .clearAndSetSemantics {},
            ) {
                axis.ticks.forEach { tick ->
                    Text(
                        tick.stripTrailingZeros().toPlainString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .measurementHistoryAxisOffset(
                                fraction = 1f - axis.fraction(tick),
                                plotHeight = plotHeight,
                                inset = inset,
                            ),
                    )
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            val visibleDays = if (fitAllDays) period.history.days.size.coerceAtLeast(1) else if (compactMonth) {
                when {
                    density.fontScale >= 1.5f -> 6
                    maxWidth < 280.dp -> 8
                    else -> 10
                }
            } else if (
                (maxWidth < 280.dp) || (density.fontScale >= 1.5f)
            ) {
                3
            } else {
                5
            }
            val laneWidth = if (compactMonth) {
                (maxWidth / visibleDays).coerceAtLeast(
                    if (density.fontScale >= 1.5f) 48.dp else 32.dp,
                )
            } else {
                (maxWidth / visibleDays).coerceAtLeast(
                    if (density.fontScale >= 1.5f) 88.dp else 56.dp,
                )
            }
            val chartWidth = laneWidth * plotDays.size
            val scroll = rememberScrollState()
            val viewportWidth = maxWidth
            val activeIndex = ChronoUnit.DAYS
                .between(period.start, selectedDate)
                .toInt()
                .coerceIn(0, (plotDays.size - 1).coerceAtLeast(0))
            val selection by animateFloatAsState(
                targetValue = activeIndex.toFloat(),
                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                label = "history selected day",
            )
            var positioned by remember(period.start, period.kind) {
                mutableStateOf(value = false)
            }

            LaunchedEffect(
                selectedDate,
                period.start,
                laneWidth,
                viewportWidth,
                scroll.maxValue,
                motionAllowed,
            ) {
                val center = with(density) {
                    (
                        (laneWidth.toPx() * (activeIndex + 0.5f)) -
                            (viewportWidth.toPx() / 2f)
                        ).roundToInt()
                }
                val target = center.coerceIn(0, scroll.maxValue)
                if (positioned && motionAllowed) {
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
            ) {
                Box(Modifier.width(chartWidth)) {
                    HistoryChartCanvas(
                        axis = axis,
                        period = period,
                        selectedPosition = selection,
                        laneWidth = laneWidth,
                        plotHeight = plotHeight,
                        inset = inset,
                        compact = compactMonth,
                        referenceValue = referenceValue,
                    )
                    Row(
                        modifier = Modifier
                            .width(chartWidth)
                            .selectableGroup(),
                    ) {
                        plotDays.forEach { (day, dots) ->
                            MeasurementHistoryDayLane(
                                date = day.date,
                                state = day.state,
                                valueDescriptions = dots.map { (point) ->
                                    point.value.stripTrailingZeros()
                                        .toPlainString() + " " + unit
                                },
                                selected = day.date == selectedDate,
                                laneWidth = laneWidth,
                                plotHeight = plotHeight,
                                compact = compactMonth,
                            ) {
                                onSelectDate(day.date)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChartCanvas(
    axis: GlucosePlotAxis?,
    period: GlucoseHistoryPeriod,
    selectedPosition: Float,
    laneWidth: Dp,
    plotHeight: Dp,
    inset: Dp,
    compact: Boolean,
    referenceValue: BigDecimal?,
) {
    val (_, plotDays) = remember(period.history) {
        projectGlucoseWeekPlot(period.history)
    }
    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(plotHeight)
            .clearAndSetSemantics {},
    ) {
        val lanePixels = laneWidth.toPx()
        val plotTop = inset.toPx()
        val innerHeight = size.height - (plotTop * 2f)
        val selectionInset = if (compact) 1.dp.toPx() else 3.dp.toPx()
        val selectedLeft = (selectedPosition * lanePixels) + selectionInset
        drawRoundRect(
            color = scheme.primaryContainer.copy(
                alpha = if (compact) 0.46f else 0.58f,
            ),
            topLeft = Offset(selectedLeft, 0f),
            size = Size(
                width = (lanePixels - (selectionInset * 2f))
                    .coerceAtLeast(0f),
                height = size.height,
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                if (compact) 7.dp.toPx() else 12.dp.toPx(),
            ),
        )

        val gridFractions = axis?.ticks?.map { 1f - axis.fraction(it) }
            ?: listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        gridFractions.distinct().forEach { fraction ->
            val y = plotTop + (fraction * innerHeight)
            drawLine(
                color = scheme.outline.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if ((axis != null) && (referenceValue != null)) {
            val y = plotTop +
                ((1f - axis.fraction(referenceValue)) * innerHeight)
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

        if (axis != null) {
            val points = buildList {
                plotDays.forEachIndexed { dayIndex, (_, dots) ->
                    dots.forEach { (point, positionWithinDay) ->
                        add(
                            Offset(
                                x = lanePixels * (dayIndex + positionWithinDay),
                                y = plotTop +
                                    (1f - axis.fraction(point.value)) * innerHeight,
                            ),
                        )
                    }
                }
            }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = scheme.primary.copy(
                        alpha = if (compact) 0.44f else 0.52f,
                    ),
                    style = Stroke(
                        width = if (compact) 1.4.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
            points.forEach { point ->
                drawCircle(
                    color = scheme.surface.copy(alpha = 0.92f),
                    radius = if (compact) 4.5.dp.toPx() else 7.dp.toPx(),
                    center = point,
                )
                drawCircle(
                    color = scheme.primary,
                    radius = if (compact) 2.8.dp.toPx() else 4.5.dp.toPx(),
                    center = point,
                )
            }
        }
    }
}

@Composable
internal fun MeasurementHistoryDayLane(
    date: LocalDate,
    state: MeasurementDayState,
    valueDescriptions: List<String>,
    selected: Boolean,
    laneWidth: Dp,
    plotHeight: Dp,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            when (state) {
                MeasurementDayState.WITH_RECORDS -> MaterialTheme.colorScheme.onSurface
                MeasurementDayState.WITHOUT_RECORDS ->
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
                MeasurementDayState.FUTURE ->
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
            }
        },
        animationSpec = tween(duration),
        label = "history day label",
    )
    val labelBackground by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        animationSpec = tween(duration),
        label = "history day label background",
    )
    Column(
        modifier = Modifier
            .width(laneWidth)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                contentDescription = buildString {
                    append(date.format(measurementFullDate))
                    if (valueDescriptions.isEmpty()) {
                        append(
                            if (state == MeasurementDayState.FUTURE) {
                                ", día futuro"
                            } else {
                                ", sin registros"
                            },
                        )
                    } else {
                        append(", ")
                        append(
                            valueDescriptions.joinToString(),
                        )
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(plotHeight))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = labelBackground,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compact) 2.dp else BitavidaSpacing.Small,
                    vertical = BitavidaSpacing.ExtraSmall,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compact) {
                    Text(
                        date.format(measurementWeekday).replace(".", ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
                Text(
                    date.dayOfMonth.toString(),
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    color = labelColor,
                )
            }
        }
    }
}

@Composable
internal fun Modifier.measurementHistoryAxisOffset(
    fraction: Float,
    plotHeight: Dp,
    inset: Dp,
): Modifier {
    val density = LocalDensity.current
    return offset {
        with(density) {
            val labelHeight = 18.dp.toPx()
            val innerHeight = (plotHeight - inset * 2).toPx()
            IntOffset(
                x = 0,
                y = (
                    inset.toPx() + fraction * innerHeight - labelHeight / 2f
                    ).roundToInt(),
            )
        }
    }
}

@Composable
private fun SelectedDayRecords(
    period: GlucoseHistoryPeriod,
    selectedDate: LocalDate,
    onRecord: ((MeasurementRecord) -> Unit)?,
    onOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
    measurementKind: MeasurementKind,
    unit: String,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    AnimatedContent(
        targetState = selectedDate,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        transitionSpec = {
            val forward = targetState > initialState
            val enter = if (motionAllowed) {
                fadeIn(tween(duration)) +
                    slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) {
                        if (forward) it / 16 else -it / 16
                    }
            } else {
                EnterTransition.None
            }
            val exit = if (motionAllowed) {
                fadeOut(tween(BitavidaFluidMotion.Press)) +
                    slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) {
                        if (forward) -it / 16 else it / 16
                    }
            } else {
                ExitTransition.None
            }
            (enter togetherWith exit).using(
                SizeTransform { _, _ -> tween(duration) },
            )
        },
        label = "selected glucose history day",
    ) { date ->
        val events = period.days
            .firstOrNull { it.date == date }
            ?.events
            .orEmpty()
        val isFuture = period.history.days
            .firstOrNull { it.date == date }
            ?.state == MeasurementDayState.FUTURE
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            SectionTitle(
                date.format(measurementFullDate)
                    .replaceFirstChar { it.titlecase(measurementLocale) },
            )
            MeasurementPanel {
                if (events.isEmpty()) {
                    SectionTitle(
                        if (isFuture) "Día futuro" else "Sin eventos",
                        small = true,
                    )
                    SecondaryText(
                        if (isFuture) {
                            "Todavía no hay eventos para este día."
                        } else {
                            "No hay lecturas ni mediciones programadas por confirmar."
                        },
                    )
                } else {
                    events.forEachIndexed { index, event ->
                        if (index > 0) ActionDivider()
                        when (event) {
                            is GlucoseHistoryEvent.Registered ->
                                GlucoseHistoryRecordRow(
                                    event.record,
                                    onRecord,
                                    measurementKind,
                                    unit,
                                )
                            is GlucoseHistoryEvent.NotPerformed ->
                                GlucoseHistoryOccurrenceRow(
                                    occurrence = event.occurrence,
                                    stateLabel = "No se realizó",
                                    reason = event.decision.reason,
                                    visual = TodayStatusVisual.NEGATIVE,
                                    measurementKind = measurementKind,
                                    onOccurrence = onOccurrence,
                                )
                            is GlucoseHistoryEvent.Unconfirmed ->
                                GlucoseHistoryOccurrenceRow(
                                    occurrence = event.occurrence,
                                    stateLabel = "Sin confirmar",
                                    reason = null,
                                    visual = TodayStatusVisual.PENDING,
                                    measurementKind = measurementKind,
                                    onOccurrence = onOccurrence,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlucoseHistoryOccurrenceRow(
    occurrence: GlucoseScheduledOccurrence,
    stateLabel: String,
    reason: String?,
    visual: TodayStatusVisual,
    measurementKind: MeasurementKind,
    onOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
) {
    MeasurementHistoryEventRow(
        visual = visual,
        onClick = onOccurrence?.let { callback ->
            { callback(occurrence) }
        },
    ) {
        Text(
            stateLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SecondaryText(
            listOfNotNull(
                occurrence.scheduledAt.format(
                    DateTimeFormatter.ofPattern("h:mm a", measurementLocale),
                ),
                occurrence.suggestedContext.captureLabel().takeIf {
                    measurementKind != MeasurementKind.WEIGHT
                },
            ).joinToString(" · "),
        )
        reason?.let { SecondaryText("Motivo: $it") }
    }
}

@Composable
private fun GlucoseHistoryRecordRow(
    record: MeasurementRecord,
    onRecord: ((MeasurementRecord) -> Unit)?,
    measurementKind: MeasurementKind,
    unit: String,
) {
    require(record.kind == measurementKind)
    val valueLabel = when (val value = record.value) {
        is MeasurementValue.Glucose -> value.reading + if (
            value.reading.uppercase() in setOf("HI", "LO")
        ) "" else " $unit"
        is MeasurementValue.Weight ->
            value.kilograms.stripTrailingZeros().toPlainString() + " $unit"
        is MeasurementValue.BloodPressure -> error("Serie doble no compatible")
    }
    MeasurementHistoryEventRow(
        visual = TodayStatusVisual.COMPLETE,
        onClick = onRecord?.let { callback -> { callback(record) } },
    ) {
        Text(
            valueLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val context = when (val value = record.value) {
            is MeasurementValue.Glucose -> value.context?.captureLabel()
            is MeasurementValue.Weight -> null
            is MeasurementValue.BloodPressure -> null
        }
        val time = record.occurredAt.format(
            DateTimeFormatter.ofPattern("h:mm a", measurementLocale),
        )
        SecondaryText(listOfNotNull(time, context).joinToString(" · "))
        val note = record.glucoseMetadata?.note ?: record.weightMetadata?.note
        note?.let { SecondaryText(it) }
        val origin = when {
            record.glucoseMetadata?.entryKind == GlucoseEntryKind.SCHEDULED ||
                record.weightMetadata?.entryKind == WeightEntryKind.SCHEDULED ->
                "Medición programada"
            record.glucoseMetadata?.entryKind == GlucoseEntryKind.ADDITIONAL ||
                record.weightMetadata?.entryKind == WeightEntryKind.ADDITIONAL ->
                "Medición adicional"
            else -> null
        }
        origin?.let { SecondaryText(it) }
    }
}

@Composable
internal fun MeasurementHistoryEventRow(
    visual: TodayStatusVisual,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .bitavidaFluidInteraction(
                source = source,
                enabled = true,
                motionAllowed = motionAllowed,
                shape = MaterialTheme.shapes.medium,
                restingElevationDp = 0f,
            )
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
    }
    Row(
        modifier = interactionModifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .padding(vertical = BitavidaSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodayStatusIcon(visual)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            content()
        }
        if (onClick != null) {
            MeasurementIcon(R.drawable.ic_v2_chevron, 16)
        }
    }
}

private fun periodLabel(
    kind: GlucoseHistoryPeriodKind,
    start: LocalDate,
    endInclusive: LocalDate,
): String = when (kind) {
    GlucoseHistoryPeriodKind.WEEK ->
        "${start.format(periodDate)} – ${endInclusive.format(periodDate)}"
    GlucoseHistoryPeriodKind.MONTH ->
        start.format(DateTimeFormatter.ofPattern("MMMM yyyy", measurementLocale))
            .replaceFirstChar { it.titlecase(measurementLocale) }
}

private fun BigDecimal.asReading(): String = stripTrailingZeros().toPlainString()

private val periodDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", measurementLocale)
