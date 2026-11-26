package com.bitavida.app.ui.appointments

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventDraft
import com.bitavida.app.domain.model.CareEventKind
import com.bitavida.app.domain.model.CareEventStatus
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val calendarLocale = Locale.forLanguageTag("es-MX")
private val calendarMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", calendarLocale)
private val calendarDateFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", calendarLocale)
private val weekdayNames = listOf(
    "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo",
)

internal fun appointmentCalendarGridWidth(available: Dp): Dp = maxOf(available, 352.dp)

internal fun appointmentCalendarDayDiameter(cellWidth: Dp): Dp =
    minOf(40.dp, (cellWidth - 8.dp).coerceAtLeast(0.dp))

/** The displayed selection owns the visible month; no event or date is persisted here. */
@Composable
internal fun AppointmentCalendar(
    selectedDate: LocalDate,
    events: List<CareEvent>,
    motion: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val month = YearMonth.from(selectedDate)
    val days = remember(month) { monthDays(month) }
    val markedDates = remember(events) {
        events.filter(::calendarEligible).mapNotNull(::eventDate).toSet()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onSelect(moveMonth(selectedDate, -1)) },
                    modifier = Modifier.semantics { contentDescription = "Mes anterior" },
                ) {
                    Box(Modifier.graphicsLayer { rotationZ = 180f }) {
                        MeasurementIcon(R.drawable.ic_v2_chevron, 20, MaterialTheme.colorScheme.primary)
                    }
                }
                AnimatedContent(
                    targetState = month,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        if (motion) fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                        else EnterTransition.None togetherWith ExitTransition.None
                    },
                    label = "Mes del calendario",
                ) { visible ->
                    Text(
                        text = visible.format(calendarMonthFormat).replaceFirstChar { it.uppercaseChar() },
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { onSelect(moveMonth(selectedDate, 1)) },
                    modifier = Modifier.semantics { contentDescription = "Mes siguiente" },
                ) {
                    MeasurementIcon(R.drawable.ic_v2_chevron, 20, MaterialTheme.colorScheme.primary)
                }
            }

            // Seven 48 dp targets cannot fit in the available card width on a 320 dp phone.
            // A bounded horizontal scroll preserves their size rather than overlapping targets.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gridWidth = appointmentCalendarGridWidth(maxWidth)
                Column(
                    Modifier.horizontalScroll(rememberScrollState()).width(gridWidth)
                        .padding(horizontal = 8.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        weekdayNames.forEach { name ->
                            Text(
                                text = name.take(1),
                                modifier = Modifier.weight(1f).semantics { contentDescription = name },
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    days.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(Modifier.weight(1f).height(48.dp))
                                } else {
                                    CalendarDay(
                                        date = date,
                                        chosen = date == selectedDate,
                                        today = date == LocalDate.now(),
                                        hasEvent = date in markedDates,
                                        motion = motion,
                                        modifier = Modifier.weight(1f),
                                        onSelect = onSelect,
                                    )
                                }
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
    chosen: Boolean,
    today: Boolean,
    hasEvent: Boolean,
    motion: Boolean,
    modifier: Modifier,
    onSelect: (LocalDate) -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = if (motion) tween(160) else snap(),
        label = "Selección de fecha",
    )
    val textColor = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val description = buildString {
        append(date.format(calendarDateFormat).replaceFirstChar { it.uppercaseChar() })
        if (today) append(", hoy")
        if (hasEvent) append(", tiene eventos")
    }
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = { onSelect(date) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                selected = chosen
                contentDescription = description
            },
            color = Color.Transparent,
            contentColor = textColor,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                val diameter = appointmentCalendarDayDiameter(maxWidth)
                Box(
                    modifier = Modifier.size(diameter)
                        .background(background, CircleShape)
                        .then(
                            if (today) Modifier.border(
                                if (chosen) 2.dp else 1.dp,
                                if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
                }
                if (hasEvent) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).size(3.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

private fun previewCalendarEvents(): List<CareEvent> {
    val date = LocalDate.of(2026, 11, 24).atTime(10, 30)
    return listOf(
        CareEvent(
            id = "ejemplo", personUnderCareId = "persona-ejemplo",
            draft = CareEventDraft(
                kind = CareEventKind.APPOINTMENT, title = "Consulta de ejemplo",
                status = CareEventStatus.CONFIRMED, scheduledAt = date,
            ),
            createdAt = date, modifiedAt = date, revision = 1,
        ),
    )
}

@Preview(showBackground = true)
@Composable private fun AppointmentCalendarLightPreview() {
    BitavidaTheme { AppointmentCalendar(LocalDate.of(2026, 11, 24), previewCalendarEvents(), false) {} }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun AppointmentCalendarDarkPreview() {
    BitavidaTheme(darkTheme = true) { AppointmentCalendar(LocalDate.of(2026, 11, 24), previewCalendarEvents(), false) {} }
}

@Preview(showBackground = true, widthDp = 320)
@Composable private fun AppointmentCalendarEmptyCompactPreview() {
    BitavidaTheme { AppointmentCalendar(LocalDate.of(2026, 2, 3), emptyList(), false) {} }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 2f)
@Composable private fun AppointmentCalendarLargeTextPreview() {
    BitavidaTheme { AppointmentCalendar(LocalDate.of(2026, 11, 24), previewCalendarEvents(), false) {} }
}
