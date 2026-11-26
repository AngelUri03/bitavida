package com.bitavida.app.ui.medical

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.lastTreatmentDate
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val calendarLocale = Locale.forLanguageTag("es-MX")
private val calendarMonthFormatter =
    DateTimeFormatter.ofPattern("MMMM 'de' uuuu", calendarLocale)
private val calendarSelectedDateFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", calendarLocale)
private val calendarAccessibleDateFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu", calendarLocale)
private val calendarWeekdayFormatter =
    DateTimeFormatter.ofPattern("EEE", calendarLocale)

@Composable
internal fun MedicationIntakeCalendar(
    state: MedicalPlanUiState,
    motionAllowed: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val plan = state.selectedMedicationPlan ?: return
    val identityPlans = state.selectedMedicationIdentityPlans
        .ifEmpty { listOf(plan) }
    val today = LocalDate.now()
    val selectedDate = state.selectedMedicationHistoryDate
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val records = identityPlans.flatMap { identityPlan ->
        state.overview.intakesFor(identityPlan.id)
    }
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0

    Text(
        "Calendario de tomas",
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(BitavidaSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            MedicationWeekControls(
                selectedDate = selectedDate,
                today = today,
                motionAllowed = motionAllowed,
                onSelectDate = {
                    onAction(MedicalPlanAction.SelectMedicationHistoryDate(it))
                },
                onPreviousWeek = {
                    onAction(MedicalPlanAction.PreviousMedicationHistoryWeek)
                },
                onNextWeek = {
                    onAction(MedicalPlanAction.NextMedicationHistoryWeek)
                },
            )
            AnimatedContent(
                targetState = weekStart,
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                transitionSpec = {
                    val forward = targetState.isAfter(initialState)
                    (
                        slideInHorizontally(tween(duration)) {
                            if (forward) it else -it
                        } + fadeIn(tween(duration))
                    ).togetherWith(
                        slideOutHorizontally(tween(duration)) {
                            if (forward) -it else it
                        } + fadeOut(tween(duration)),
                    )
                },
                label = "medication_calendar_week",
            ) { shownWeek ->
                MedicationWeekStrip(
                    weekStart = shownWeek,
                    selectedDate = selectedDate,
                    today = today,
                    records = records,
                    plans = identityPlans,
                    duration = duration,
                    onSelectDate = {
                        onAction(MedicalPlanAction.SelectMedicationHistoryDate(it))
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {}
                Text(
                    "Con registros",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    AnimatedContent(
        targetState = selectedDate,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
        },
        label = "medication_calendar_day",
    ) { day ->
        MedicationCalendarDay(
            day = day,
            now = LocalDateTime.now(),
            plan = identityPlans.planForCalendarDate(day, records) ?: plan,
            state = state,
            motionAllowed = motionAllowed,
            onAction = onAction,
        )
    }
}

@Composable
private fun MedicationWeekControls(
    selectedDate: LocalDate,
    today: LocalDate,
    motionAllowed: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    val monthLabel = selectedDate.format(calendarMonthFormatter)
        .replaceFirstChar { it.titlecase(calendarLocale) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                CalendarMonthButton(
                    label = monthLabel,
                    motionAllowed = motionAllowed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    showMedicationDatePicker(dateTimePicker, selectedDate, onSelectDate)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CalendarArrowButton(
                        previous = true,
                        motionAllowed = motionAllowed,
                        onClick = onPreviousWeek,
                    )
                    CalendarTodayButton(
                        motionAllowed = motionAllowed,
                        onClick = { onSelectDate(today) },
                    )
                    CalendarArrowButton(
                        previous = false,
                        motionAllowed = motionAllowed,
                        onClick = onNextWeek,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarMonthButton(
                    label = monthLabel,
                    motionAllowed = motionAllowed,
                    modifier = Modifier.weight(1f),
                ) {
                    showMedicationDatePicker(dateTimePicker, selectedDate, onSelectDate)
                }
                CalendarArrowButton(
                    previous = true,
                    motionAllowed = motionAllowed,
                    onClick = onPreviousWeek,
                )
                CalendarTodayButton(
                    motionAllowed = motionAllowed,
                    onClick = { onSelectDate(today) },
                )
                CalendarArrowButton(
                    previous = false,
                    motionAllowed = motionAllowed,
                    onClick = onNextWeek,
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthButton(
    label: String,
    motionAllowed: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .bitavidaFluidInteraction(
                source = source,
                enabled = true,
                motionAllowed = motionAllowed,
                shape = shape,
                restingElevationDp = 0f,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.ic_v2_calendar),
                null,
                Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
            Text(
                label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Image(
                painterResource(R.drawable.ic_v2_chevron),
                null,
                Modifier.size(16.dp).graphicsLayer { rotationZ = 90f },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun CalendarArrowButton(
    previous: Boolean,
    motionAllowed: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = CircleShape
    Surface(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget)
            .bitavidaFluidInteraction(
                source = source,
                enabled = true,
                motionAllowed = motionAllowed,
                shape = shape,
                restingElevationDp = 0f,
            )
            .semantics {
                contentDescription = if (previous) "Semana anterior" else "Semana siguiente"
            },
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.ic_v2_chevron),
                null,
                Modifier.size(20.dp).graphicsLayer {
                    rotationZ = if (previous) 180f else 0f
                },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun CalendarTodayButton(
    motionAllowed: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraLarge
    OutlinedButton(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .bitavidaFluidInteraction(
                source = source,
                enabled = true,
                motionAllowed = motionAllowed,
                shape = shape,
                restingElevationDp = 0f,
            ),
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text("Hoy", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MedicationWeekStrip(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    records: List<MedicationIntakeRecord>,
    plans: List<MedicationPlan>,
    duration: Int,
    onSelectDate: (LocalDate) -> Unit,
) {
    val selectedIndex = java.time.temporal.ChronoUnit.DAYS
        .between(weekStart, selectedDate).toInt()
    val selectedInWeek = selectedIndex in 0..6
    val animatedIndex by androidx.compose.animation.core.animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, 6).toFloat(),
        animationSpec = tween(duration),
        label = "calendar_day_indicator",
    )
    val indicatorTop = with(LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp()
    } + 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = maxWidth / 7
        if (selectedInWeek) {
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (
                                cellWidth * animatedIndex +
                                    (cellWidth - 44.dp) / 2
                                ).roundToPx(),
                            y = indicatorTop.roundToPx(),
                        )
                    }
                    .size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val selected = date == selectedDate
                val hasRecords = records.any { record ->
                    val recordPlan = plans.singleOrNull {
                        it.id == record.medicationPlanId
                    }
                    recordPlan != null && record.hasDeclaredResponse() &&
                        record.calendarDate(recordPlan) == date
                }
                val numberColor by animateColorAsState(
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    tween(duration),
                    label = "calendar_day_number",
                )
                val dayDescription = buildList {
                    add(date.format(calendarAccessibleDateFormatter))
                    if (date == today) add("hoy")
                    if (selected) add("seleccionado")
                    if (hasRecords) add("con registros")
                }.joinToString(", ")
                Column(
                    modifier = Modifier.weight(1f)
                        .clickable(
                            role = Role.Button,
                            onClick = { onSelectDate(date) },
                        )
                        .semantics {
                            contentDescription = dayDescription
                            this.selected = selected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        date.format(calendarWeekdayFormatter)
                            .replaceFirstChar { it.titlecase(calendarLocale) }
                            .removeSuffix("."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = if (date == today && !selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = numberColor,
                            )
                        }
                    }
                    if (hasRecords) {
                        Surface(
                            modifier = Modifier.size(7.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {}
                    } else {
                        Spacer(Modifier.size(7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationCalendarDay(
    day: LocalDate,
    now: LocalDateTime,
    plan: MedicationPlan,
    state: MedicalPlanUiState,
    motionAllowed: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
) {
    var selectedKey by rememberSaveable(plan.id, day) {
        mutableStateOf<String?>(null)
    }
    val dateLabel = day.format(calendarSelectedDateFormatter)
        .replaceFirstChar { it.titlecase(calendarLocale) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Text(
            dateLabel,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        MedicationIntakeDayPanel(
            state = state,
            medication = plan,
            date = day,
            now = now,
            motionAllowed = motionAllowed,
            selectedKey = selectedKey,
            onSelectedKeyChange = { selectedKey = it },
            historicalSchedule = true,
            allowAsNeededRegistration = false,
            onAction = onAction,
        )
    }
}

private fun List<MedicationPlan>.planForCalendarDate(
    day: LocalDate,
    records: List<MedicationIntakeRecord>,
): MedicationPlan? {
    firstOrNull { plan ->
        records.any { record ->
            record.medicationPlanId == plan.id &&
                record.calendarDate(plan) == day
        }
    }?.let { return it }
    val now = System.currentTimeMillis()
    return filter { plan ->
        val configuration = plan.currentRevision.configuration
        val start = configuration?.startsOn ?: plan.currentRevision.startsOn
        val end = plan.lastTreatmentDate(now)
            ?: configuration?.endsOn
            ?: plan.currentRevision.endsOn
        !day.isBefore(start) && (end == null || !day.isAfter(end))
    }.maxWithOrNull(
        compareBy<MedicationPlan> {
            it.currentRevision.configuration?.startsOn
                ?: it.currentRevision.startsOn
        }.thenBy(MedicationPlan::createdAtEpochMillis),
    )
}

internal fun MedicationIntakeRecord.calendarDate(plan: MedicationPlan): LocalDate {
    val scheduledDay = currentRevision.scheduledAt.toLocalDate()
    val frequency = medicationPlanSnapshotForDay(plan, scheduledDay)
        .currentRevision.configuration?.frequency
    return if (frequency == MedicationFrequencyKind.AS_NEEDED) {
        (currentRevision.actualAt ?: currentRevision.scheduledAt).toLocalDate()
    } else {
        scheduledDay
    }
}

