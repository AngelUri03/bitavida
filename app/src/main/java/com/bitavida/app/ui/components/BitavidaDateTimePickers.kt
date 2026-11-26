package com.bitavida.app.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class BitavidaDatePickerConstraints(
    val minDate: LocalDate? = null,
    val maxDate: LocalDate? = null,
    val yearRange: IntRange = 1900..2100,
) {
    fun allows(date: LocalDate): Boolean =
        (date.year in yearRange) &&
            (minDate == null || !date.isBefore(minDate)) &&
            (maxDate == null || !date.isAfter(maxDate))

    fun allowsMonth(month: YearMonth): Boolean =
        (month.year in yearRange) &&
            (minDate == null || !month.atEndOfMonth().isBefore(minDate)) &&
            (maxDate == null || !month.atDay(1).isAfter(maxDate))
}

data class BitavidaTimePickerConstraints(
    val minTime: LocalTime? = null,
    val maxTime: LocalTime? = null,
    val minuteStep: Int = 1,
    val invalidSelectionMessage: String = "La hora seleccionada no está disponible.",
) {
    init {
        require(minuteStep in 1..30)
    }

    fun allows(time: LocalTime): Boolean =
        time.minute % minuteStep == 0 &&
            (minTime == null || !time.isBefore(minTime)) &&
            (maxTime == null || !time.isAfter(maxTime))
}

internal fun bitavidaCalendarCells(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
): List<LocalDate?> {
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = MutableList<LocalDate?>(leading) { null }
    repeat(month.lengthOfMonth()) { day -> cells += month.atDay(day + 1) }
    while (cells.size % 7 != 0) cells += null
    return cells
}

/** First tap starts a new interval; a tap before the start begins again at that date. */
internal data class BitavidaDateRangeSelection(val start: LocalDate, val end: LocalDate?)

internal fun bitavidaDateRangeAfterSelection(
    current: BitavidaDateRangeSelection,
    selected: LocalDate,
): BitavidaDateRangeSelection =
    if (current.end != null || selected.isBefore(current.start)) BitavidaDateRangeSelection(selected, null)
    else BitavidaDateRangeSelection(current.start, selected)

internal fun bitavidaHour24(displayHour: Int, isPm: Boolean): Int =
    if (isPm) (displayHour % 12) + 12 else displayHour % 12

internal fun bitavidaHour12(hour24: Int): Int = when (val hour = hour24 % 12) {
    0 -> 12
    else -> hour
}

@Composable
fun BitavidaDatePicker(
    selectedDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    constraints: BitavidaDatePickerConstraints = BitavidaDatePickerConstraints(),
    motionAllowedOverride: Boolean? = null,
    locale: Locale = Locale.getDefault(),
    initialMonthYearVisible: Boolean = false,
    monthSelectionOnly: Boolean = false,
    selectedEndDate: LocalDate? = null,
    onConfirmRange: ((LocalDate, LocalDate) -> Unit)? = null,
    showHeader: Boolean = true,
    rangeConfirmLabel: String = "Usar este periodo",
    onDisplayedMonthChange: ((YearMonth) -> Unit)? = null,
    rangePreviewOtherDate: LocalDate? = null,
    rangePreviewIsStart: Boolean = true,
    showConfirmAction: Boolean = true,
    onDateSelected: ((LocalDate) -> Unit)? = null,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(motionAllowedOverride)
    val duration = if (motionAllowed) 240 else 0
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    var pendingText by rememberSaveable(selectedDate) { mutableStateOf(selectedDate.toString()) }
    var pendingEndText by rememberSaveable(selectedDate, selectedEndDate) {
        mutableStateOf(selectedEndDate?.toString())
    }
    var monthText by rememberSaveable(selectedDate) {
        mutableStateOf(YearMonth.from(selectedDate).toString())
    }
    var jumpVisible by rememberSaveable { mutableStateOf(initialMonthYearVisible || monthSelectionOnly) }
    val pending = LocalDate.parse(pendingText)
    val pendingEnd = pendingEndText?.let(LocalDate::parse)
    val rangeMode = onConfirmRange != null
    val displayedMonth = YearMonth.parse(monthText)
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val previewStart = rangePreviewOtherDate?.let { if (rangePreviewIsStart) pending else it }
    val previewEnd = rangePreviewOtherDate?.let { if (rangePreviewIsStart) it else pending }
    val validPreview = previewStart != null && previewEnd != null && !previewEnd.isBefore(previewStart)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        if (showHeader) PickerHeader(
            title = if (jumpVisible) "Mes y año" else if (rangeMode) "Seleccionar periodo" else "Seleccionar fecha",
            onBack = if (jumpVisible && !monthSelectionOnly) ({ jumpVisible = false }) else onBack,
        )
        AnimatedContent(
            targetState = jumpVisible,
            transitionSpec = {
                if (!motionAllowed) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (
                        fadeIn(tween(duration)) +
                            slideInHorizontally(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                                initialOffsetX = { if (targetState) it / 8 else -it / 8 },
                            )
                        ).togetherWith(
                        fadeOut(tween(duration / 2)) +
                            slideOutHorizontally(
                                animationSpec = tween(duration),
                                targetOffsetX = { if (targetState) -it / 10 else it / 10 },
                            ),
                    ).using(SizeTransform(clip = false) { _, _ -> tween(duration) })
                }
            },
            label = "bitavida_date_picker_mode",
        ) { showMonthYear ->
            if (showMonthYear) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    MonthYearJump(
                        displayedMonth = displayedMonth,
                        constraints = constraints,
                        locale = locale,
                        onMonthChange = { month ->
                            monthText = month.toString()
                            onDisplayedMonthChange?.invoke(month)
                            if (!showHeader && !monthSelectionOnly) jumpVisible = false
                        },
                        compactYearNavigation = monthSelectionOnly,
                    )
                    if (monthSelectionOnly && showConfirmAction) {
                        PickerActions(
                            confirmLabel = "Usar este mes",
                            confirmEnabled = constraints.allowsMonth(displayedMonth),
                            onConfirm = {
                                val firstDay = displayedMonth.atDay(1)
                                val allowedDay = constraints.minDate?.let { maxOf(firstDay, it) } ?: firstDay
                                onConfirm(allowedDay)
                            },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    MonthNavigation(
                        month = displayedMonth,
                        locale = locale,
                        previousEnabled = constraints.allowsMonth(displayedMonth.minusMonths(1)),
                        nextEnabled = constraints.allowsMonth(displayedMonth.plusMonths(1)),
                        onPrevious = { monthText = displayedMonth.minusMonths(1).toString() },
                        onNext = { monthText = displayedMonth.plusMonths(1).toString() },
                        onJump = { jumpVisible = true },
                    )
                    if (rangeMode) {
                        val format = DateTimeFormatter.ofPattern("d MMM uuuu", locale)
                        val rangeLabel = if (pendingEnd == null) {
                            "Inicio: ${pending.format(format)}. Selecciona la fecha final."
                        } else {
                            "${pending.format(format)} – ${pendingEnd.format(format)}"
                        }
                        Text(rangeLabel, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedContent(
                        targetState = displayedMonth,
                        transitionSpec = {
                            if (!motionAllowed) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val direction = if (targetState > initialState) 1 else -1
                                (
                                    fadeIn(tween(duration)) +
                                        slideInHorizontally(
                                            animationSpec = tween(
                                                duration,
                                                easing = FastOutSlowInEasing,
                                            ),
                                            initialOffsetX = { (it / 7) * direction },
                                        )
                                    ).togetherWith(
                                    fadeOut(tween(duration / 2)) +
                                        slideOutHorizontally(
                                            animationSpec = tween(duration),
                                            targetOffsetX = { (-it / 9) * direction },
                                        ),
                                ).using(SizeTransform(clip = false) { _, _ -> tween(duration) })
                            }
                        },
                        label = "bitavida_calendar_month",
                    ) { month ->
                        CalendarGrid(
                            month = month,
                            firstDayOfWeek = firstDay,
                            selectedDate = pending,
                            today = today,
                            locale = locale,
                            constraints = constraints,
                            motionAllowed = motionAllowed,
                            rangeStart = if (rangeMode) pending else previewStart.takeIf { validPreview },
                            rangeEnd = if (rangeMode) pendingEnd else previewEnd.takeIf { validPreview },
                            onSelect = { date ->
                                if (rangeMode) {
                                    val next = bitavidaDateRangeAfterSelection(
                                        BitavidaDateRangeSelection(pending, pendingEnd), date,
                                    )
                                    pendingText = next.start.toString()
                                    pendingEndText = next.end?.toString()
                                } else {
                                    pendingText = date.toString()
                                    onDateSelected?.invoke(date)
                                }
                            },
                        )
                    }
                    if (showConfirmAction) PickerActions(
                        confirmLabel = if (rangeMode) rangeConfirmLabel else "Usar esta fecha",
                        confirmEnabled = constraints.allows(pending) &&
                            (!rangeMode || (pendingEnd != null && constraints.allows(pendingEnd) && !pendingEnd.isBefore(pending))),
                        onConfirm = {
                            if (rangeMode && pendingEnd != null) onConfirmRange?.invoke(pending, pendingEnd)
                            else if (!rangeMode) onConfirm(pending)
                        },
                    )
                }
            }
        }
    }
}
@Composable
private fun MonthNavigation(
    month: YearMonth,
    locale: Locale,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
) {
    val monthLabel = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("MMMM uuuu", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickerArrowButton(
            description = "Mes anterior",
            enabled = previousEnabled,
            forward = false,
            onClick = onPrevious,
        )
        TextButton(
            onClick = onJump,
            modifier = Modifier.weight(1f)
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                monthLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        PickerArrowButton(
            description = "Mes siguiente",
            enabled = nextEnabled,
            forward = true,
            onClick = onNext,
        )
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    constraints: BitavidaDatePickerConstraints,
    motionAllowed: Boolean,
    rangeStart: LocalDate? = null,
    rangeEnd: LocalDate? = null,
    onSelect: (LocalDate) -> Unit,
) {
    val cells = remember(month, firstDayOfWeek) {
        bitavidaCalendarCells(month, firstDayOfWeek)
    }
    val weekdays = remember(firstDayOfWeek, locale) {
        (0L..6L).map { offset ->
            (firstDayOfWeek + offset).getDisplayName(TextStyle.NARROW, locale)
                .replaceFirstChar { it.titlecase(locale) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gridWidth = max(maxWidth.value, 336f).dp
        val gridScroll = rememberScrollState()
        val density = LocalDensity.current
        LaunchedEffect(month, selectedDate, gridWidth, maxWidth, motionAllowed) {
            if (YearMonth.from(selectedDate) != month || gridWidth <= maxWidth) return@LaunchedEffect
            val selectedColumn =
                (selectedDate.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
            val target = with(density) {
                ((gridWidth / 7f) * (selectedColumn + 0.5f) - maxWidth / 2f)
                    .toPx()
                    .toInt()
                    .coerceAtLeast(0)
            }
            if (motionAllowed) gridScroll.animateScrollTo(target) else gridScroll.scrollTo(target)
        }
        Box(
            Modifier.fillMaxWidth().horizontalScroll(gridScroll),
        ) {
            Column(
                modifier = Modifier.width(gridWidth),
                verticalArrangement = Arrangement.Top,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f)
                                .heightIn(min = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                                Box(
                                    Modifier.weight(1f)
                                        .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                                )
                            } else {
                                CalendarDay(
                                    date = date,
                                    selected = if (rangeStart != null) date == rangeStart || date == rangeEnd else date == selectedDate,
                                    withinRange = rangeStart != null && rangeEnd != null && date > rangeStart && date < rangeEnd,
                                    rangeDescription = if (rangeStart == null) null else when (date) {
                                        rangeStart -> if (rangeEnd == rangeStart) "inicio y fin del periodo" else "inicio del periodo"
                                        rangeEnd -> "fin del periodo"
                                        else -> if (rangeEnd != null && date > rangeStart && date < rangeEnd) "dentro del periodo" else null
                                    },
                                    today = date == today,
                                    enabled = constraints.allows(date),
                                    locale = locale,
                                    motionAllowed = motionAllowed,
                                    onClick = { onSelect(date) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    selected: Boolean,
    withinRange: Boolean = false,
    rangeDescription: String? = null,
    today: Boolean,
    enabled: Boolean,
    locale: Locale,
    motionAllowed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = if (motionAllowed) 220 else 0
    val container by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary
            withinRange -> MaterialTheme.colorScheme.primaryContainer
            today -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        tween(duration),
        label = "calendar_day_container",
    )
    val content by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            withinRange -> MaterialTheme.colorScheme.onPrimaryContainer
            today -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        tween(duration),
        label = "calendar_day_content",
    )
    val description = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu", locale))
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics {
                contentDescription = description +
                    (if (today) ", hoy" else "") +
                    (rangeDescription?.let { ", $it" } ?: "")
                this.selected = selected || withinRange
            },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = if (today && !selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun MonthYearJump(
    displayedMonth: YearMonth,
    constraints: BitavidaDatePickerConstraints,
    locale: Locale,
    onMonthChange: (YearMonth) -> Unit,
    compactYearNavigation: Boolean = false,
) {
    val firstYear = maxOf(
        constraints.yearRange.first,
        constraints.minDate?.year ?: constraints.yearRange.first,
    )
    val lastYear = minOf(
        constraints.yearRange.last,
        constraints.maxDate?.year ?: constraints.yearRange.last,
    )
    fun selectYear(requestedYear: Int) {
        val year = requestedYear.coerceIn(firstYear, lastYear)
        val candidate = displayedMonth.withYear(year)
        if (constraints.allowsMonth(candidate)) {
            onMonthChange(candidate)
        } else {
            (1..12)
                .asSequence()
                .filter { constraints.allowsMonth(YearMonth.of(year, it)) }
                .minByOrNull { abs(it - displayedMonth.monthValue) }
                ?.let { onMonthChange(YearMonth.of(year, it)) }
        }
    }

    val pageStart = firstYear + ((displayedMonth.year - firstYear) / 12) * 12
    val pageEnd = minOf(pageStart + 11, lastYear)
    val visibleYears = remember(pageStart, pageEnd) { (pageStart..pageEnd).toList() }
    val monthNames = remember(locale) {
        (1..12).map { month ->
            YearMonth.of(2000, month).month
                .getDisplayName(TextStyle.SHORT, locale)
                .replaceFirstChar { it.titlecase(locale) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerArrowButton(
                description = "Años anteriores",
                enabled = if (compactYearNavigation) displayedMonth.year > firstYear else pageStart > firstYear,
                forward = false,
                onClick = { selectYear(displayedMonth.year - if (compactYearNavigation) 1 else 12) },
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compactYearNavigation) Text(
                    "Año",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (compactYearNavigation) displayedMonth.year.toString() else "$pageStart – $pageEnd",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            PickerArrowButton(
                description = "Años siguientes",
                enabled = if (compactYearNavigation) displayedMonth.year < lastYear else pageEnd < lastYear,
                forward = true,
                onClick = { selectYear(displayedMonth.year + if (compactYearNavigation) 1 else 12) },
            )
        }
        if (!compactYearNavigation) repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                repeat(4) { column ->
                    val index = row * 4 + column
                    val year = visibleYears.getOrNull(index)
                    if (year == null) {
                        Box(
                            modifier = Modifier.weight(1f)
                                .height(BitavidaSpacing.MinimumTouchTarget),
                        )
                    } else {
                        PickerChoiceChip(
                            label = year.toString(),
                            selected = year == displayedMonth.year,
                            onClick = { selectYear(year) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (!compactYearNavigation) Text(
            "Mes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(4) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                repeat(3) { column ->
                    val monthNumber = row * 3 + column + 1
                    val candidate = YearMonth.of(displayedMonth.year, monthNumber)
                    PickerChoiceChip(
                        label = monthNames[monthNumber - 1],
                        selected = monthNumber == displayedMonth.monthValue,
                        enabled = constraints.allowsMonth(candidate),
                        onClick = { onMonthChange(candidate) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun BitavidaTimePicker(
    selectedTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    constraints: BitavidaTimePickerConstraints = BitavidaTimePickerConstraints(),
    motionAllowedOverride: Boolean? = null,
    locale: Locale = Locale.getDefault(),
    initialDirectInput: Boolean = false,
) {
    val motionAllowed = rememberBitavidaMotionAllowed(motionAllowedOverride)
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val periodLabels = remember(locale) {
        val formatter = DateTimeFormatter.ofPattern("a", locale)
        listOf(
            LocalTime.MIDNIGHT.format(formatter),
            LocalTime.NOON.format(formatter),
        )
    }
    var hour24 by rememberSaveable(selectedTime) { mutableIntStateOf(selectedTime.hour) }
    var minute by rememberSaveable(selectedTime) { mutableIntStateOf(selectedTime.minute) }
    var directInput by rememberSaveable { mutableStateOf(initialDirectInput) }
    var hourText by rememberSaveable(selectedTime, is24Hour) {
        mutableStateOf(
            if (is24Hour) selectedTime.hour.toString().padStart(2, '0')
            else bitavidaHour12(selectedTime.hour).toString(),
        )
    }
    var minuteText by rememberSaveable(selectedTime) {
        mutableStateOf(selectedTime.minute.toString().padStart(2, '0'))
    }
    val isPm = hour24 >= 12
    val hourRange = if (is24Hour) 0..23 else 1..12
    val parsedHour = hourText.toIntOrNull()
    val parsedMinute = minuteText.toIntOrNull()
    val directValid = parsedHour in hourRange && parsedMinute in 0..59 &&
        (parsedMinute ?: 0) % constraints.minuteStep == 0
    val pending = LocalTime.of(hour24, minute)
    val allowed = constraints.allows(pending)
    var confirmationAttempted by rememberSaveable(selectedTime) { mutableStateOf(value = false) }
    LaunchedEffect(hourText, minuteText, hour24, minute) {
        confirmationAttempted = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        PickerHeader(title = "Seleccionar hora", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    directInput = !directInput
                    hourText = if (is24Hour) hour24.toString().padStart(2, '0')
                    else bitavidaHour12(hour24).toString()
                    minuteText = minute.toString().padStart(2, '0')
                },
                modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    if (directInput) "Usar ruedas" else "Escribir hora",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        AnimatedContent(
            targetState = directInput,
            modifier = Modifier.fillMaxWidth().heightIn(min = 184.dp),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                if (!motionAllowed) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                }
            },
            label = "bitavida_time_input_mode",
        ) { direct ->
            if (direct) {
                DirectTimeInput(
                    hourText = hourText,
                    minuteText = minuteText,
                    is24Hour = is24Hour,
                    isPm = isPm,
                    periodLabels = periodLabels,
                    hourValid = parsedHour in hourRange,
                    minuteValid = parsedMinute in 0..59 &&
                        (parsedMinute ?: 0) % constraints.minuteStep == 0,
                    onHourChange = { value ->
                        if (value.length <= 2 && value.all(Char::isDigit)) {
                            hourText = value
                            value.toIntOrNull()?.takeIf { it in hourRange }?.let { hour ->
                                hour24 = if (is24Hour) hour else bitavidaHour24(hour, isPm)
                            }
                        }
                    },
                    onMinuteChange = { value ->
                        if (value.length <= 2 && value.all(Char::isDigit)) {
                            minuteText = value
                            value.toIntOrNull()?.takeIf {
                                it in 0..59 && it % constraints.minuteStep == 0
                            }?.let { minute = it }
                        }
                    },
                    onPeriodChange = { pm ->
                        val displayHour = parsedHour?.takeIf { it in 1..12 }
                            ?: bitavidaHour12(hour24)
                        hour24 = bitavidaHour24(displayHour, pm)
                    },
                )
            } else {
                TimeWheels(
                    hour24 = hour24,
                    minute = minute,
                    is24Hour = is24Hour,
                    constraints = constraints,
                    periodLabels = periodLabels,
                    motionAllowed = motionAllowed,
                    onHourChange = { hour ->
                        hour24 = hour
                        hourText = if (is24Hour) hour.toString().padStart(2, '0')
                            else bitavidaHour12(hour).toString()
                    },
                    onMinuteChange = {
                        minute = it
                        minuteText = it.toString().padStart(2, '0')
                    },
                )
            }
        }
        if (confirmationAttempted && !allowed) {
            Text(
                constraints.invalidSelectionMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        PickerActions(
            confirmLabel = "Usar esta hora",
            confirmEnabled = !directInput || directValid,
            onConfirm = {
                confirmationAttempted = true
                if (allowed && (!directInput || directValid)) onConfirm(pending)
            },
        )
    }
}

@Composable
private fun TimeWheels(
    hour24: Int,
    minute: Int,
    is24Hour: Boolean,
    constraints: BitavidaTimePickerConstraints,
    periodLabels: List<String>,
    motionAllowed: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    val hourValues = if (is24Hour) (0..23).toList() else (1..12).toList()
    val minuteValues = buildList {
        addAll(0..59 step constraints.minuteStep)
        if (minute !in this) add(minute)
    }.sorted()
    val displayHour = if (is24Hour) hour24 else bitavidaHour12(hour24)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickerWheel(
            label = "Hora",
            values = hourValues,
            selectedValue = displayHour,
            valueLabel = { if (is24Hour) it.toString().padStart(2, '0') else it.toString() },
            motionAllowed = motionAllowed,
            onSelect = { selected ->
                onHourChange(
                    if (is24Hour) selected
                    else bitavidaHour24(selected, hour24 >= 12),
                )
            },
            modifier = Modifier.weight(1f),
        )
        Text(":", style = MaterialTheme.typography.headlineSmall)
        PickerWheel(
            label = "Minutos",
            visibleLabel = if (largeText) "Min" else "Minutos",
            values = minuteValues,
            selectedValue = minute,
            valueLabel = { it.toString().padStart(2, '0') },
            motionAllowed = motionAllowed,
            onSelect = onMinuteChange,
            modifier = Modifier.weight(1f),
        )
        if (!is24Hour) {
            PickerWheel(
                label = "Periodo",
                visibleLabel = if (largeText) "A/P" else "Periodo",
                values = listOf(0, 1),
                selectedValue = if (hour24 >= 12) 1 else 0,
                valueLabel = { periodLabels[it] },
                motionAllowed = motionAllowed,
                onSelect = { period ->
                    onHourChange(bitavidaHour24(bitavidaHour12(hour24), period == 1))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PickerWheel(
    label: String,
    values: List<Int>,
    selectedValue: Int,
    valueLabel: (Int) -> String,
    motionAllowed: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleLabel: String = label,
) {
    val selectedIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val paddedValues = remember(values) { listOf<Int?>(null) + values + null }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentSelectedValue by rememberUpdatedState(selectedValue)
    val currentOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(selectedValue, motionAllowed) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        if (motionAllowed) listState.animateScrollToItem(selectedIndex) else {
            listState.scrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(listState, paddedValues) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val layout = listState.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                val centered = layout.visibleItemsInfo.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - center)
                }
                centered?.let { paddedValues.getOrNull(it.index) }?.let { value ->
                    if (value != currentSelectedValue) currentOnSelect(value)
                }
            }
        }
    }

    Column(
        modifier = modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(
            visibleLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth().height(144.dp),
        ) {
            items(
                count = paddedValues.size,
                key = { index -> paddedValues[index]?.let { "$label-$it" } ?: "$label-space-$index" },
            ) { index ->
                val value = paddedValues[index]
                if (value == null) {
                    Box(Modifier.fillMaxWidth().height(BitavidaSpacing.MinimumTouchTarget))
                } else {
                    val selected = value == selectedValue
                    val container by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        tween(if (motionAllowed) 220 else 0),
                        label = "time_wheel_selection",
                    )
                    Surface(
                        onClick = { currentOnSelect(value) },
                        modifier = Modifier.fillMaxWidth()
                            .height(BitavidaSpacing.MinimumTouchTarget)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .semantics { this.selected = selected },
                        shape = MaterialTheme.shapes.medium,
                        color = container,
                        border = if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                valueLabel(value),
                                style = if (selected) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectTimeInput(
    hourText: String,
    minuteText: String,
    is24Hour: Boolean,
    isPm: Boolean,
    periodLabels: List<String>,
    hourValid: Boolean,
    minuteValid: Boolean,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit,
    onPeriodChange: (Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val compact = maxWidth < 400.dp || fontScale >= 1.5f
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerNumberField(
                    value = hourText,
                    label = "Hora",
                    error = !hourValid,
                    onValueChange = onHourChange,
                    modifier = Modifier.weight(1f),
                )
                Text(":", style = MaterialTheme.typography.headlineSmall)
                PickerNumberField(
                    value = minuteText,
                    label = "Minutos",
                    error = !minuteValid,
                    onValueChange = onMinuteChange,
                    modifier = Modifier.weight(1f),
                )
                if (!is24Hour && !compact) {
                    PeriodInput(
                        isPm = isPm,
                        periodLabels = periodLabels,
                        onPeriodChange = onPeriodChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!is24Hour && compact) {
                PeriodInput(
                    isPm = isPm,
                    periodLabels = periodLabels,
                    onPeriodChange = onPeriodChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PeriodInput(
    isPm: Boolean,
    periodLabels: List<String>,
    onPeriodChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        listOf(false, true).forEachIndexed { index, pm ->
            PickerChoiceChip(
                label = periodLabels[index],
                selected = isPm == pm,
                onClick = { onPeriodChange(pm) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PickerChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                .padding(horizontal = BitavidaSpacing.ExtraSmall, vertical = BitavidaSpacing.ExtraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PickerNumberField(
    value: String,
    label: String,
    error: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = BitavidaSpacing.Small),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                interactionSource = interaction,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun PickerHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_v2_arrow_back),
                contentDescription = "Volver",
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PickerArrowButton(
    description: String,
    enabled: Boolean,
    forward: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget)
            .semantics { contentDescription = description },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_v2_arrow_back),
            contentDescription = null,
            modifier = Modifier.size(22.dp)
                .graphicsLayer { rotationZ = if (forward) 180f else 0f },
            colorFilter = ColorFilter.tint(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        )
    }
}

@Composable
private fun PickerActions(
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    Button(
        onClick = onConfirm,
        enabled = confirmEnabled,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BitavidaSpacing.PrimaryControlHeight),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
    }
}
