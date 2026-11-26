package com.bitavida.app.ui.demo

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.icon
import com.bitavida.app.ui.measurements.label
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** A reference to an existing occurrence or record; no data is stored by the calendar. */
internal data class CareCalendarEvent(
    val key: String,
    val kind: TodayActivityKind,
    val title: String,
    val detail: String?,
    val scheduledAt: LocalDateTime,
    val status: String,
    val reference: Any,
    val actualAt: LocalDateTime? = null,
    val spontaneous: Boolean = false,
    val unlinked: Boolean = false,
)

internal fun careCalendarEvents(data: TodayCareProjection, now: LocalDateTime, careEvents: List<CareEvent> = emptyList(),
    selectedDate: LocalDate? = null): List<CareCalendarEvent> =
    buildList {
        data.medications.forEach { item ->
            add(CareCalendarEvent("med:${item.id}", TodayActivityKind.MEDICATION,
                item.occurrence.medicationName, item.occurrence.amount.value + " " + item.occurrence.amount.unit,
                item.occurrence.startsAt, if (item.occurrence.startsAt > now) "Programada" else "Sin confirmar", item))
        }
        data.measurements.forEach { item ->
            add(CareCalendarEvent("measurement:${item.id}", TodayActivityKind.MEASUREMENT,
                item.kind.label(), null, item.occurrence.scheduledAt,
                if (item.occurrence.scheduledAt > now) "Programada" else "Sin confirmar", item))
        }
        data.observations.forEach { item ->
            add(CareCalendarEvent("observation:${item.id}", TodayActivityKind.OBSERVATION,
                ObservationCatalog.get(item.observationId).name, null, item.scheduledAt,
                if (item.scheduledAt > now) "Programada" else "Sin confirmar", item))
        }
        data.reminders.forEach { item ->
            add(CareCalendarEvent("reminder:${item.id}", TodayActivityKind.REMINDER, item.title, item.details,
                item.scheduledAt, "Pendiente", item))
        }
        careEvents.forEach { item ->
            val moment = item.draft.scheduledAt ?: item.draft.scheduledDate?.atTime(java.time.LocalTime.MAX)
            if (moment != null) add(CareCalendarEvent("care_event:${item.id}", TodayActivityKind.CARE_EVENT,
                item.draft.title, item.draft.location.takeIf(String::isNotBlank), moment,
                when (item.draft.status) {
                    CareEventStatus.CONFIRMED -> "Cita confirmada"
                    CareEventStatus.COMPLETED -> "Cita realizada"
                    CareEventStatus.CANCELLED -> "Cita cancelada"
                    CareEventStatus.SCHEDULED -> "Estudio programado"
                    CareEventStatus.PERFORMED -> "Estudio realizado"
                    CareEventStatus.RESULT_PENDING -> "Resultado pendiente"
                    CareEventStatus.RESULT_AVAILABLE -> "Resultado disponible"
                    CareEventStatus.REVIEWED -> "Revisado en consulta"
                    else -> "Registrado"
                }, item))
        }
        (data.resolvedActivities.filterNot { it.outcome == TodayActivityOutcome.AMBIGUOUS } +
            data.spontaneousActivities + data.unlinkedActivities + data.additionalActivities).forEach { item ->
            val status = when (item.outcome) {
                TodayActivityOutcome.PERFORMED -> when (item.kind) {
                    TodayActivityKind.MEDICATION -> "Se tomó"
                    TodayActivityKind.REMINDER -> "Hecho"
                    else -> "Registrada"
                }
                TodayActivityOutcome.PARTIALLY_PERFORMED -> "Toma parcial"
                TodayActivityOutcome.NOT_PERFORMED -> if (item.kind == TodayActivityKind.MEDICATION) "No se tomó" else "No se realizó"
                TodayActivityOutcome.DISCARDED -> "Descartado"
                TodayActivityOutcome.AMBIGUOUS -> "Varias declaraciones"
                TodayActivityOutcome.UNCONFIRMED -> "Sin confirmar"
            }
            val keyPrefix = if (item.spontaneous) "spontaneous:${item.kind}" else if (item.unlinked) "unlinked:${item.kind}" else when (item.kind) {
                TodayActivityKind.MEDICATION -> "med"
                TodayActivityKind.MEASUREMENT -> "measurement"
                TodayActivityKind.OBSERVATION -> "observation"
                TodayActivityKind.REMINDER -> "reminder"
                TodayActivityKind.CARE_EVENT -> "care_event"
            }
            add(CareCalendarEvent("$keyPrefix:${item.id}", item.kind, item.title,
                item.secondary, item.scheduledAt, status, item, item.actualAt, item.spontaneous, item.unlinked))
        }
    }.filter { selectedDate == null || it.scheduledAt.toLocalDate() == selectedDate }
        .sortedWith(compareBy<CareCalendarEvent> { it.scheduledAt }.thenBy { it.key })

/** Date consultation only. The host owns selection, event projection and navigation to actual detail. */
@Composable
internal fun CareCalendarScreen(
    selectedDate: LocalDate,
    now: LocalDateTime,
    projection: TodayCareProjection,
    eventDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onOpenEvent: ((CareCalendarEvent) -> Unit)? = null,
    canOpenEvent: (CareCalendarEvent) -> Boolean = { true },
    modifier: Modifier = Modifier,
    careEvents: List<CareEvent> = emptyList(),
    bottomBar: @Composable () -> Unit = {},
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate).toString()) }
    val month = YearMonth.parse(monthText)
    var category by rememberSaveable { mutableStateOf("Todas") }
    val locale = LocalLocale.current.platformLocale
    val motion = rememberBitavidaMotionAllowed(override = null)
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val weekCount = (leading + month.lengthOfMonth() + 6) / 7
    val events = remember(projection, now, careEvents, selectedDate) { careCalendarEvents(projection, now, careEvents, selectedDate) }
    fun label(kind: TodayActivityKind) = when (kind) {
        TodayActivityKind.MEDICATION -> "Medicamentos"
        TodayActivityKind.MEASUREMENT -> "Mediciones"
        TodayActivityKind.OBSERVATION -> "Observaciones"
        TodayActivityKind.REMINDER -> "Recordatorios"
        TodayActivityKind.CARE_EVENT -> "Citas y Estudios"
    }
    val filtered = events.filter { category == "Todas" || label(it.kind) == category }
    LaunchedEffect(month) { onMonthChanged(month) }
    fun moveMonth(next: YearMonth) { monthText = next.toString() }
    androidx.activity.compose.BackHandler(onBack = onBack)
    TodayTypography {
        Scaffold(modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("title") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            MeasurementIcon(R.drawable.ic_v2_arrow_back, 24, MaterialTheme.colorScheme.primary)
                        }
                        Text("Calendario de cuidado", Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = {
                            val today = now.toLocalDate()
                            moveMonth(YearMonth.from(today))
                            onDateSelected(today)
                        }) { Text("Hoy", color = MaterialTheme.colorScheme.primary) }
                    }
                }
                item("month") {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { moveMonth(month.minusMonths(1)) }) {
                                    Box(Modifier.graphicsLayer { rotationZ = 180f }) {
                                        MeasurementIcon(R.drawable.ic_v2_chevron, 20, MaterialTheme.colorScheme.primary)
                                    }
                                }
                                AnimatedContent(targetState = month, modifier = Modifier.weight(1f),
                                    transitionSpec = { if (motion) androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                                        else EnterTransition.None togetherWith ExitTransition.None }, label = "mes") { current ->
                                    Text(current.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                                        .replaceFirstChar { it.uppercaseChar() },
                                        Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                }
                                IconButton(onClick = { moveMonth(month.plusMonths(1)) }) {
                                    MeasurementIcon(R.drawable.ic_v2_chevron, 20, MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                                Row {
                                    repeat(7) { index ->
                                        val weekday = firstDay.plus(index.toLong())
                                        Box(Modifier.width(48.dp).height(32.dp), contentAlignment = Alignment.Center) {
                                            Text(weekday.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                repeat(weekCount) { week ->
                                    Row {
                                        repeat(7) { column ->
                                            val number = week * 7 + column - leading + 1
                                            if (number !in 1..month.lengthOfMonth()) {
                                                Spacer(Modifier.size(48.dp))
                                            } else {
                                                val date = month.atDay(number)
                                                val isSelected = date == selectedDate
                                                val isToday = date == now.toLocalDate()
                                                val hasEvents = date in eventDates
                                                Surface(onClick = { onDateSelected(date) },
                                                    modifier = Modifier.size(48.dp).semantics {
                                                        selected = isSelected
                                                        contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", locale)) +
                                                            (if (isToday) ", hoy" else "") +
                                                            (if (hasEvents) ", tiene actividades" else "")
                                                    }, shape = CircleShape,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    border = if (isToday && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center) {
                                                        Text(number.toString(), style = MaterialTheme.typography.labelMedium)
                                                        if (hasEvents) Surface(Modifier.size(4.dp), shape = CircleShape,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) {}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item("selected-day") {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale))
                        .replaceFirstChar { it.uppercaseChar() }, Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium)
                }
                item("filters") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("Todas", "Medicamentos", "Mediciones", "Observaciones", "Recordatorios") +
                            if (careEvents.isNotEmpty()) listOf("Citas y Estudios") else emptyList()).forEach { text ->
                            FilterChip(selected = category == text, onClick = { category = text },
                                label = { Text(text, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = if (text == "Todas") null else ({
                                    MeasurementIcon(when (text) {
                                        "Medicamentos" -> R.drawable.ic_v2_pill
                                        "Mediciones" -> R.drawable.ic_v2_droplet
                                        "Observaciones" -> R.drawable.ic_v2_stethoscope
                                        else -> R.drawable.ic_v2_clock
                                    }, 18, if (category == text) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                }),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    iconColor = MaterialTheme.colorScheme.primary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = category == text,
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                if (filtered.isEmpty()) item("empty") {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("No hay actividades en esta fecha para el filtro seleccionado.",
                            Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(filtered, key = { it.key }) { event ->
                    CalendarEventCard(event, onOpenEvent?.takeIf { canOpenEvent(event) }?.let { callback -> { callback(event) } })
                }
            }
        }
    }
}

@Composable
private fun CalendarEventCard(event: CareCalendarEvent, onOpen: (() -> Unit)?) {
    val locale = LocalLocale.current.platformLocale
    val format = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val formatter = remember(locale, format) { DateTimeFormatter.ofPattern(format, locale) }
    val icon = when (event.kind) {
        TodayActivityKind.MEDICATION -> R.drawable.ic_v2_pill
        TodayActivityKind.MEASUREMENT -> (event.reference as? TodayMeasurementItem)?.kind?.icon() ?:
            (event.reference as? TodayResolvedActivity)?.measurementKind?.icon() ?: R.drawable.ic_v2_droplet
        TodayActivityKind.OBSERVATION -> R.drawable.ic_v2_stethoscope
        TodayActivityKind.REMINDER -> R.drawable.ic_v2_clock
        TodayActivityKind.CARE_EVENT -> R.drawable.ic_v2_calendar
    }
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.bitavidaColors.paleBlue) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    MeasurementIcon(icon, 24, MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall)
                (event.reference as? CareEvent)?.draft?.let { draft ->
                    Text(com.bitavida.app.ui.appointments.careEventTypeLabel(draft),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (draft.consultationType == com.bitavida.app.domain.model.CareConsultationType.SPECIALIST)
                        draft.specialty.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                }
                val time = if ((event.reference as? CareEvent)?.draft?.scheduledAt == null && event.reference is CareEvent) {
                    "Hora no indicada"
                } else if (event.unlinked) {
                    event.actualAt?.format(formatter)?.let { "Registro · $it" } ?: "Registro · Sin hora indicada"
                } else if (event.spontaneous) {
                    event.actualAt?.format(formatter)?.let { "Registro espontáneo · $it" } ?: "Registro espontáneo · Sin hora indicada"
                } else "Programada · ${event.scheduledAt.format(formatter)}"
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!event.spontaneous && !event.unlinked && event.actualAt != null)
                    Text("Hora registrada · ${event.actualAt.format(formatter)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                event.detail?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(event.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (onOpen != null) MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
        }
    }
    if (onOpen == null) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp) { body() }
    else Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp) { body() }
}

@Preview(showBackground = true)
@Composable private fun CareCalendarLightPreview() {
    BitavidaTheme { CareCalendarScreen(LocalDate.of(2026, 11, 21), LocalDateTime.of(2026, 11, 21, 12, 0),
        TodayCareProjection(), emptySet(), {}, {}, {}) }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun CareCalendarDarkPreview() {
    BitavidaTheme(darkTheme = true) { CareCalendarScreen(LocalDate.of(2026, 11, 21), LocalDateTime.of(2026, 11, 21, 12, 0),
        TodayCareProjection(), emptySet(), {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 320)
@Composable private fun CareCalendarCompactPreview() {
    BitavidaTheme { CareCalendarScreen(LocalDate.of(2026, 11, 21), LocalDateTime.of(2026, 11, 21, 12, 0),
        TodayCareProjection(), emptySet(), {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun CareCalendarLargeTextPreview() {
    BitavidaTheme { CareCalendarScreen(LocalDate.of(2026, 11, 21), LocalDateTime.of(2026, 11, 21, 12, 0),
        TodayCareProjection(), emptySet(), {}, {}, {}) }
}
