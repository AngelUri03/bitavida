package com.bitavida.app.ui.measurements

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.BloodPressureEntryKind
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.BloodPressureHistoryPeriod
import com.bitavida.app.domain.usecase.BloodPressureHistoryStatistics
import com.bitavida.app.domain.usecase.GlucoseHistoryEvent
import com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind
import com.bitavida.app.domain.usecase.projectBloodPressureHistoryPeriod
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun BloodPressureHistoryContent(
    records: List<MeasurementRecord>,
    today: LocalDate,
    followUp: GlucoseFollowUp?,
    decisions: List<GlucoseOccurrenceDecision>,
    now: LocalDateTime,
    onRecord: ((MeasurementRecord) -> Unit)?,
    onOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
) {
    var kindName by rememberSaveable {
        mutableStateOf(GlucoseHistoryPeriodKind.WEEK.name)
    }
    val kind = GlucoseHistoryPeriodKind.valueOf(kindName)
    var anchorText by rememberSaveable { mutableStateOf(today.toString()) }
    val anchor = LocalDate.parse(anchorText)
    val period = remember(
        records, anchor, kind, today, followUp, decisions, now,
    ) {
        projectBloodPressureHistoryPeriod(
            records, anchor, kind, today, followUp, decisions, now,
        )
    }
    var selectedText by rememberSaveable(period.start, period.endInclusive) {
        mutableStateOf(
            today.coerceIn(period.start, period.endInclusive).toString(),
        )
    }
    val selected = LocalDate.parse(selectedText)
    val shift: (Int) -> Unit = { direction ->
        anchorText = when (kind) {
            GlucoseHistoryPeriodKind.WEEK ->
                anchor.plusWeeks(direction.toLong()).toString()
            GlucoseHistoryPeriodKind.MONTH ->
                anchor.plusMonths(direction.toLong()).toString()
        }
    }

    BloodPressureHistoryAnalysis(
        period = period,
        selectedDate = selected,
        today = today,
        onSelectDate = { selectedText = it.toString() },
        onPeriodKind = {
            kindName = it.name
            anchorText = today.toString()
            selectedText = today.toString()
        },
        onPrevious = { shift(-1) },
    ) {
        shift(1)
    }
    BloodPressureSelectedDayRecords(
        period, selected, onRecord, onOccurrence,
    )
    Text(
        "Lecturas y estados confirmados por la persona cuidadora",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BloodPressureHistoryAnalysis(
    period: BloodPressureHistoryPeriod,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPeriodKind: (GlucoseHistoryPeriodKind) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    MeasurementPanel(
        color = MaterialTheme.bitavidaColors.paleBlue,
    ) {
        MeasurementHistoryAnalysisHeader(
            kind = period.kind,
            onKind = onPeriodKind,
        )
        MeasurementHistoryPeriodNavigation(
            kind = period.kind,
            start = period.start,
            endInclusive = period.endInclusive,
            canMoveForward = period.endInclusive < today,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        PressureHistoryMetrics(period.statistics)
        PressureSeriesLegend()
        BloodPressureHistoryChart(period, selectedDate, onSelectDate)
        SecondaryText(
            if (period.kind == GlucoseHistoryPeriodKind.WEEK) {
                "Semana completa · desliza para recorrer los siete días."
            } else {
                "Desliza para recorrer todos los días del mes."
            },
        )
    }
}

@Composable
private fun PressureHistoryMetrics(
    statistics: BloodPressureHistoryStatistics,
) {
    val average = if (
        (statistics.averageSystolic != null) &&
        (statistics.averageDiastolic != null)
    ) {
        statistics.averageSystolic.stripTrailingZeros().toPlainString() +
            " / " +
            statistics.averageDiastolic.stripTrailingZeros().toPlainString()
    } else {
        "—"
    }
    MeasurementHistoryAverageMetric(
        average = average,
        unit = "mmHg",
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked =
            (maxWidth < 280.dp) || (LocalDensity.current.fontScale >= 1.5f)
        if (stacked) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                PressureRangeMetric(
                    label = "Sistólica",
                    minimum = statistics.minimumSystolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    maximum = statistics.maximumSystolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                PressureRangeMetric(
                    label = "Diastólica",
                    minimum = statistics.minimumDiastolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    maximum = statistics.maximumDiastolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    indicatorColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                PressureRangeMetric(
                    label = "Sistólica",
                    minimum = statistics.minimumSystolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    maximum = statistics.maximumSystolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                PressureRangeMetric(
                    label = "Diastólica",
                    minimum = statistics.minimumDiastolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    maximum = statistics.maximumDiastolic
                        ?.stripTrailingZeros()
                        ?.toPlainString(),
                    indicatorColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    MeasurementHistoryInlineMetric(
        label = "Mediciones",
        value = statistics.totalMeasurements.toString(),
        support = "en el periodo",
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryText(
        "Los datos mostrados ayudan a visualizar el comportamiento de la presión.",
    )
    SecondaryText(
        "Esta vista no sustituye la valoración del equipo de salud.",
    )
}

@Composable
private fun PressureRangeMetric(
    label: String,
    minimum: String?,
    maximum: String?,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(BitavidaSpacing.Small),
            verticalArrangement =
                Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(9.dp)) {
                    drawCircle(indicatorColor)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            PressureRangeValue(
                label = "Mínimo",
                value = minimum,
            )
            PressureRangeValue(
                label = "Máximo",
                value = maximum,
            )
        }
    }
}

@Composable
private fun PressureRangeValue(
    label: String,
    value: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.let { "$it mmHg" } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun PressureSeriesLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressureSeriesLegendItem(
            color = MaterialTheme.colorScheme.primary,
            label = "Sistólica",
        )
        PressureSeriesLegendItem(
            color = MaterialTheme.colorScheme.tertiary,
            label = "Diastólica",
        )
    }
}

@Composable
private fun PressureSeriesLegendItem(
    color: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BloodPressureHistoryChart(
    period: BloodPressureHistoryPeriod,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    fitAllDays: Boolean = false,
) {
    val density = LocalDensity.current
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val compactMonth = period.kind == GlucoseHistoryPeriodKind.MONTH
    val plotHeight = if (compactMonth) 164.dp else 190.dp
    val axisWidth = if (period.plot.axis == null) 0.dp else 44.dp
    val inset = 12.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            if (period.plot.axis == null) 0.dp else BitavidaSpacing.ExtraSmall,
        ),
    ) {
        period.plot.axis?.let { axis ->
            Box(
                Modifier
                    .width(axisWidth)
                    .height(plotHeight)
                    .clearAndSetSemantics {},
            ) {
                axis.ticks.forEach { tick ->
                    Text(
                        text = tick.stripTrailingZeros().toPlainString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
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
            val visibleDays = if (fitAllDays) period.plot.days.size.coerceAtLeast(1) else if (compactMonth) {
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
            val chartWidth = laneWidth * period.plot.days.size
            val scroll = rememberScrollState()
            val viewportWidth = maxWidth
            val activeIndex = period.plot.days.indexOfFirst { (day) ->
                day.date == selectedDate
            }.coerceAtLeast(0)
            val selection by androidx.compose.animation.core.animateFloatAsState(
                targetValue = activeIndex.toFloat(),
                animationSpec = tween(
                    duration,
                    easing = FastOutSlowInEasing,
                ),
                label = "pressure history selected day",
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
                    if (
                        (scroll.maxValue > 0) ||
                        (period.plot.days.size <= visibleDays)
                    ) {
                        positioned = true
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(scroll),
            ) {
                Box(Modifier.width(chartWidth)) {
                    BloodPressureHistoryChartCanvas(
                        period = period,
                        selectedPosition = selection,
                        laneWidth = laneWidth,
                        plotHeight = plotHeight,
                        inset = inset,
                        compact = compactMonth,
                    )
                    Row(
                        modifier = Modifier.width(chartWidth)
                            .selectableGroup(),
                    ) {
                        period.plot.days.forEach { (day, readings) ->
                            MeasurementHistoryDayLane(
                                date = day.date,
                                state = day.state,
                                valueDescriptions = readings.map {
                                        (_, systolic, diastolic) ->
                                    systolic.stripTrailingZeros()
                                        .toPlainString() + " / " +
                                        diastolic.stripTrailingZeros()
                                            .toPlainString() + " mmHg"
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
private fun BloodPressureHistoryChartCanvas(
    period: BloodPressureHistoryPeriod,
    selectedPosition: Float,
    laneWidth: androidx.compose.ui.unit.Dp,
    plotHeight: androidx.compose.ui.unit.Dp,
    inset: androidx.compose.ui.unit.Dp,
    compact: Boolean,
) {
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
            size = androidx.compose.ui.geometry.Size(
                width = (lanePixels - (selectionInset * 2f))
                    .coerceAtLeast(0f),
                height = size.height,
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                if (compact) 7.dp.toPx() else 12.dp.toPx(),
            ),
        )

        val axis = period.plot.axis
        val gridFractions = axis?.ticks?.map {
            1f - axis.fraction(it)
        } ?: listOf(0f, .25f, .5f, .75f, 1f)
        gridFractions.distinct().forEach { fraction ->
            val y = plotTop + (fraction * innerHeight)
            drawLine(
                color = scheme.outline.copy(alpha = .22f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if (axis == null) return@Canvas

        val systolicSegments = mutableListOf<List<Offset>>()
        val diastolicSegments = mutableListOf<List<Offset>>()
        var systolicSegment = mutableListOf<Offset>()
        var diastolicSegment = mutableListOf<Offset>()
        period.plot.days.forEachIndexed { dayIndex, (_, readings) ->
            if (readings.isEmpty()) {
                if (systolicSegment.isNotEmpty()) {
                    systolicSegments += systolicSegment
                    diastolicSegments += diastolicSegment
                    systolicSegment = mutableListOf()
                    diastolicSegment = mutableListOf()
                }
            } else {
                readings.forEachIndexed {
                        readingIndex, (_, systolic, diastolic) ->
                    val position =
                        (readingIndex + 1f) / (readings.size + 1f)
                    val x = lanePixels * (dayIndex + position)
                    systolicSegment += Offset(
                        x,
                        plotTop +
                            (1f - axis.fraction(systolic)) * innerHeight,
                    )
                    diastolicSegment += Offset(
                        x,
                        plotTop +
                            (1f - axis.fraction(diastolic)) * innerHeight,
                    )
                }
            }
        }
        if (systolicSegment.isNotEmpty()) {
            systolicSegments += systolicSegment
            diastolicSegments += diastolicSegment
        }

        fun drawSeries(
            segments: List<List<Offset>>,
            color: Color,
        ) {
            segments.forEach { points ->
                if (points.size > 1) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach {
                            lineTo(it.x, it.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = color.copy(
                            alpha = if (compact) .56f else .68f,
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = if (compact) {
                                1.4.dp.toPx()
                            } else {
                                2.dp.toPx()
                            },
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                }
                points.forEach { point ->
                    drawCircle(
                        color = scheme.surface.copy(alpha = .94f),
                        radius = if (compact) {
                            4.5.dp.toPx()
                        } else {
                            7.dp.toPx()
                        },
                        center = point,
                    )
                    drawCircle(
                        color = color,
                        radius = if (compact) {
                            2.8.dp.toPx()
                        } else {
                            4.5.dp.toPx()
                        },
                        center = point,
                    )
                }
            }
        }

        drawSeries(systolicSegments, scheme.primary)
        drawSeries(diastolicSegments, scheme.tertiary)
    }
}

@Composable
private fun BloodPressureSelectedDayRecords(
    period: BloodPressureHistoryPeriod,
    selectedDate: LocalDate,
    onRecord: ((MeasurementRecord) -> Unit)?,
    onOccurrence: ((GlucoseScheduledOccurrence) -> Unit)?,
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
        label = "selected pressure history day",
    ) { date ->
        val events = period.days
            .firstOrNull { (candidateDate) -> candidateDate == date }
            ?.events
            .orEmpty()
        val isFuture = period.history.days
            .firstOrNull { (candidateDate) -> candidateDate == date }
            ?.state == com.bitavida.app.domain.usecase.MeasurementDayState.FUTURE
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            SectionTitle(
                date.format(
                    DateTimeFormatter.ofPattern(
                        "EEEE d 'de' MMMM",
                        measurementLocale,
                    ),
                ).replaceFirstChar { it.titlecase(measurementLocale) },
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
                                PressureHistoryRecordRow(event.record, onRecord)
                            is GlucoseHistoryEvent.NotPerformed ->
                                PressureHistoryOccurrenceRow(
                                    occurrence = event.occurrence,
                                    stateLabel = "No se realizó",
                                    reason = event.decision.reason,
                                    visual = com.bitavida.app.ui.medical.TodayStatusVisual.NEGATIVE,
                                    onOccurrence = onOccurrence,
                                )
                            is GlucoseHistoryEvent.Unconfirmed ->
                                PressureHistoryOccurrenceRow(
                                    occurrence = event.occurrence,
                                    stateLabel = "Sin confirmar",
                                    reason = null,
                                    visual = com.bitavida.app.ui.medical.TodayStatusVisual.PENDING,
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
private fun PressureHistoryRecordRow(
    record: MeasurementRecord,
    onRecord: ((MeasurementRecord) -> Unit)?,
) {
    val value = record.value as MeasurementValue.BloodPressure
    MeasurementHistoryEventRow(
        visual = com.bitavida.app.ui.medical.TodayStatusVisual.COMPLETE,
        onClick = onRecord?.let { callback -> { callback(record) } },
    ) {
        Text(
            "${value.systolic} / ${value.diastolic} mmHg",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val context = record.bloodPressureMetadata?.actualContext?.captureLabel()
        val time = record.occurredAt.format(
            DateTimeFormatter.ofPattern("h:mm a", measurementLocale),
        )
        SecondaryText(listOfNotNull(time, context).joinToString(" · "))
        record.bloodPressureMetadata?.note?.let { SecondaryText(it) }
        val origin = when (record.bloodPressureMetadata?.entryKind) {
            BloodPressureEntryKind.SCHEDULED -> "Medición programada"
            BloodPressureEntryKind.ADDITIONAL -> "Medición adicional"
            else -> null
        }
        origin?.let { SecondaryText(it) }
    }
}

@Composable
private fun PressureHistoryOccurrenceRow(
    occurrence: GlucoseScheduledOccurrence,
    stateLabel: String,
    reason: String?,
    visual: com.bitavida.app.ui.medical.TodayStatusVisual,
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
            occurrence.scheduledAt.format(
                DateTimeFormatter.ofPattern("h:mm a", measurementLocale),
            ) + " · " + occurrence.suggestedContext.captureLabel(),
        )
        reason?.let { SecondaryText("Motivo: $it") }
    }
}

