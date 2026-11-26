package com.bitavida.app.ui.appointments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import com.bitavida.app.ui.theme.bitavidaColors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.repository.CareEventActor
import com.bitavida.app.data.appointments.CareEventPhotoStore
import kotlinx.coroutines.CancellationException
import com.bitavida.app.domain.model.CareEventCategory
import com.bitavida.app.domain.model.CareConsultationType
import com.bitavida.app.domain.model.CareEventStatus
import com.bitavida.app.domain.model.CareEventPurpose
import com.bitavida.app.domain.model.CareEventTimeMode
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.MeasurementPanel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val eventLocale = Locale.forLanguageTag("es-MX")
private val eventDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", eventLocale)
private val eventLongDateFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", eventLocale)
private val eventTimeFormat = DateTimeFormatter.ofPattern("h:mm a", eventLocale)

private fun categoryText(event: CareEvent): String = careEventTypeLabel(event.draft)
private fun categoryIcon(event: CareEvent) = when (event.draft.purpose) {
    CareEventPurpose.PROCEDURE -> R.drawable.ic_v2_pill
    CareEventPurpose.OTHER -> R.drawable.ic_v2_calendar
    CareEventPurpose.STUDY -> R.drawable.ic_v2_clipboard
    CareEventPurpose.ADMINISTRATIVE -> R.drawable.ic_v2_file
    else -> when (eventCategory(event)) {
        CareEventCategory.GENERAL, CareEventCategory.SPECIALTY -> R.drawable.ic_v2_stethoscope
        CareEventCategory.LABORATORY, CareEventCategory.IMAGING -> R.drawable.ic_v2_clipboard
        CareEventCategory.ADMINISTRATIVE -> R.drawable.ic_v2_file
    }
}
private fun locationText(event: CareEvent) =
    listOf(event.draft.institution, event.draft.location, event.draft.office)
        .filter { it.isNotBlank() }.joinToString(" · ")

private fun eventTimeText(event: CareEvent) =
    event.draft.scheduledAt?.format(eventTimeFormat) ?: if (event.draft.timeMode == CareEventTimeMode.ALL_DAY) "Todo el día" else "Hora no registrada"

private fun labelDate(event: CareEvent, long: Boolean = false) =
    eventDate(event)?.format(if (long) eventLongDateFormat else eventDateFormat)
        ?.replaceFirstChar { it.uppercaseChar() } ?: "Fecha no registrada"

/** Presentation only: the source remains the existing event state. */
internal fun LazyListScope.careEventsHome(
    state: CareEventsState,
    now: LocalDateTime,
    motion: Boolean,
    photoActor: CareEventActor?,
    onCreate: () -> Unit,
    onCalendar: () -> Unit,
    onHistory: () -> Unit,
    onHelp: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val upcoming = upcomingAppointments(state.events, now)
    val empty = !state.loading && state.error == null && state.events.isEmpty()
    val noUpcoming = !state.loading && state.error == null && state.events.isNotEmpty() && upcoming.isEmpty()
    val featured = upcoming.firstOrNull()
    if (empty) {
        item("home_welcome") {
            AppointmentWelcome(
                "Aún no tienes citas registradas",
                "Organiza aquí las consultas, estudios y trámites de salud para tenerlos siempre a la mano.",
                onCreate, motion,
            )
        }
    } else {
        if (noUpcoming) item("home_no_upcoming") {
            AppointmentWelcome("No tienes próximas citas",
                "Cuando registres una nueva consulta o estudio, aparecerá aquí.", onCreate, motion)
        }
        if (featured != null) {
            item("home_featured_header") { AppointmentSectionHeader(onCalendar, motion) }
            item("home_featured") {
                FeaturedAppointment(featured, photoActor, motion) { onOpen(featured.id) }
            }
            val others = upcoming.drop(1)
            if (others.isNotEmpty()) {
                item("home_upcoming_title") {
                    Text("Otras citas programadas", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                }
                items(others, key = { "up_${it.id}" }) { event ->
                    CareEventListCard(event) { onOpen(event.id) }
                }
            }
        }
    }
    if (featured == null && !state.loading) item("home_calendar") {
        CurrentDetailButton(DetailButtonKind.OUTLINED, onCalendar, Modifier.fillMaxWidth(),
            strongOutline = true, motionAllowedOverride = motion) {
            MeasurementIcon(R.drawable.ic_v2_calendar, 20, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Calendario")
        }
    }
    item("home_actions") {
        AppointmentActionGroup(
            showCreate = !empty && !noUpcoming && !state.loading,
            onCreate = onCreate, onHistory = onHistory, onHelp = onHelp,
        )
    }
}

@Composable
internal fun CareEventsHeader(title: String, subtitle: String, person: String, onBack: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.5f
    val back: @Composable () -> Unit = {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_v2_arrow_back),
                contentDescription = "Volver", tint = MaterialTheme.colorScheme.primary)
        }
    }
    val supporting: @Composable () -> Unit = {
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (person.isNotBlank()) Text("Acompañando a $person", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
    }
    if (large) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                back()
                Text(title, Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            supporting()
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top) {
            back()
            Column(Modifier.weight(1f).padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() })
                supporting()
            }
        }
    }
}

@Composable
private fun EventIcon(event: CareEvent, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            MeasurementIcon(categoryIcon(event), 26, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EventChip(event: CareEvent) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(categoryText(event), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun EventMeta(icon: Int, text: String) {
    if (text.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top) {
        MeasurementIcon(icon, 18, MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CareEventListCard(event: CareEvent, onClick: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.5f
    MeasurementPanel(onClick = onClick, contentPadding = 12.dp) {
        if (large) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                EventIcon(event)
                MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
            }
            EventCardContent(event, Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                EventIcon(event)
                EventCardContent(event, Modifier.weight(1f))
                MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EventCardContent(event: CareEvent, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(event.draft.title, style = MaterialTheme.typography.titleSmall)
        EventChip(event)
        if (event.draft.consultationType == CareConsultationType.SPECIALIST && event.draft.specialty.isNotBlank())
            Text(event.draft.specialty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val date = eventDate(event)
        EventMeta(R.drawable.ic_v2_calendar,
            if (date == null) "Fecha no registrada" else "${labelDate(event)} · ${eventTimeText(event)}")
        EventMeta(R.drawable.ic_v2_home, locationText(event))
        if (event.draft.status == CareEventStatus.CANCELLED)
            Text("Cancelada", style = MaterialTheme.typography.labelMedium)
    }
}

/** A past date is a schedule fact, not a claim of attendance. */
@Composable
internal fun CareEventHistoryCard(event: CareEvent, now: LocalDateTime, hasPhotos: Boolean, onClick: () -> Unit) {
    val date = eventDate(event)
    val large = LocalDensity.current.fontScale >= 1.5f
    val dateTile: @Composable () -> Unit = {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.widthIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (date != null) {
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(date.format(DateTimeFormatter.ofPattern("MMM", eventLocale)).uppercase(eventLocale),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else MeasurementIcon(R.drawable.ic_v2_calendar, 24, MaterialTheme.colorScheme.primary)
            }
        }
    }
    val body: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.draft.title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface)
            EventChip(event)
            if (event.draft.consultationType == CareConsultationType.SPECIALIST && event.draft.specialty.isNotBlank())
                Text(event.draft.specialty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EventMeta(R.drawable.ic_v2_clock, if (date == null) "Fecha no registrada" else eventTimeText(event))
            EventMeta(R.drawable.ic_v2_home, locationText(event))
            val status = when {
                event.draft.status == CareEventStatus.CANCELLED -> "Cancelada"
                date == null -> "Sin fecha registrada"
                event.draft.scheduledAt?.let { !it.isAfter(now) } == true || date.isBefore(now.toLocalDate()) -> "Evento pasado"
                else -> "Evento registrado"
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasPhotos) EventMeta(R.drawable.ic_v2_file, "Fotografías adjuntas")
        }
    }
    MeasurementPanel(onClick = onClick, contentPadding = 12.dp) {
        if (large) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                dateTile()
                MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
            }
            body(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                dateTile()
                body(Modifier.weight(1f))
                MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AppointmentSectionHeader(onCalendar: () -> Unit, motion: Boolean) {
    val calendarButton: @Composable () -> Unit = {
        CurrentDetailButton(DetailButtonKind.OUTLINED, onCalendar, strongOutline = true,
            motionAllowedOverride = motion) {
            MeasurementIcon(R.drawable.ic_v2_calendar, 20, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Calendario")
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Siguiente cita", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            calendarButton()
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Siguiente cita", Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            calendarButton()
        }
    }
}

@Composable
private fun FeaturedAppointment(event: CareEvent, actor: CareEventActor?, motion: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { CareEventPhotoStore(context.applicationContext) }
    val photoIds by androidx.compose.runtime.key(actor, event.id, event.revision) {
        produceState<List<String>>(emptyList(), actor, event.id, event.revision) {
            value = if (actor == null) emptyList() else try {
                store.list(actor, event.id)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    var openPhoto by remember(actor, event.id) { mutableStateOf<String?>(null) }
    FeaturedAppointmentContent(event, motion, photoIds.isNotEmpty() && actor != null, onOpen) {
        val first = photoIds.firstOrNull()
        if (first != null && actor != null) {
            FeaturedPhotoThumbnail(photoIds.size, onClick = { openPhoto = first }) {
                CareEventPhotoImage(first, true, Modifier.fillMaxSize()) { store.read(actor, event.id, first) }
            }
        }
    }
    openPhoto?.let { id ->
        if (actor != null) CareEventPhotoDialog(id, onDismiss = { openPhoto = null }) {
            store.read(actor, event.id, id)
        }
    }
}

/** Shared layout for the live card and its design previews. */
@Composable
internal fun FeaturedAppointmentContent(
    event: CareEvent,
    motion: Boolean,
    hasPhoto: Boolean,
    onOpen: () -> Unit,
    photoContent: @Composable () -> Unit = {},
) {
    MeasurementPanel(
        modifier = Modifier.shadow(2.dp, MaterialTheme.shapes.large),
        onClick = onOpen, color = MaterialTheme.bitavidaColors.paleBlue,
        contentPadding = 16.dp, contentSpacing = 8.dp,
    ) {
        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EventIcon(event)
                Text(event.draft.title, style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                EventChip(event)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                EventIcon(event)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.draft.title, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    EventChip(event)
                }
            }
        }

        if (event.draft.consultationType == CareConsultationType.SPECIALIST && event.draft.specialty.isNotBlank())
            Text(event.draft.specialty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val date: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top) {
                MeasurementIcon(R.drawable.ic_v2_calendar, 20, MaterialTheme.colorScheme.primary)
                Text(labelDate(event, long = true), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        val time: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                MeasurementIcon(R.drawable.ic_v2_clock, 20, MaterialTheme.colorScheme.primary)
                Text(eventTimeText(event), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 270.dp && LocalDensity.current.fontScale < 1.4f) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top) {
                    date(Modifier.weight(1f))
                    time()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    date(Modifier.fillMaxWidth())
                    time()
                }
            }
        }

        EventMeta(R.drawable.ic_v2_home,
            listOf(event.draft.institution, event.draft.location, event.draft.office, event.draft.reference)
                .filter { it.isNotBlank() }.distinct().joinToString(" · "))
        EventMeta(R.drawable.ic_v2_file, event.draft.instructions)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CurrentDetailButton(DetailButtonKind.OUTLINED, onOpen, Modifier.weight(1f),
                strongOutline = true, motionAllowedOverride = motion) { Text("Ver detalles") }
            AnimatedVisibility(
                visible = hasPhoto,
                enter = if (motion) fadeIn() else EnterTransition.None,
                exit = if (motion) fadeOut() else ExitTransition.None,
            ) { photoContent() }
        }
    }
}

@Composable
internal fun FeaturedPhotoThumbnail(count: Int, onClick: () -> Unit, image: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(56.dp).semantics(mergeDescendants = true) {
            contentDescription = "Ver fotografía adjunta. $count en total"
        },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            image()
            if (count > 1) Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                shape = CircleShape, color = MaterialTheme.colorScheme.primary,
            ) {
                Text("+${count - 1}", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun AppointmentWelcome(title: String, description: String, onCreate: () -> Unit, motion: Boolean) {
    MeasurementPanel(contentPadding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    MeasurementIcon(R.drawable.ic_v2_calendar, 26, MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CurrentDetailButton(DetailButtonKind.FILLED, onCreate, Modifier.fillMaxWidth(),
            motionAllowedOverride = motion) { Text(if (title.startsWith("Aún")) "Agregar primera cita" else "Agregar cita") }
    }
}

@Composable
private fun AppointmentActionGroup(showCreate: Boolean,
    onCreate: () -> Unit, onHistory: () -> Unit, onHelp: () -> Unit) {
    MeasurementPanel(contentPadding = 0.dp, contentSpacing = 0.dp) {
        if (showCreate) {
            AppointmentActionRow("Agregar cita", R.drawable.ic_v2_plus, onCreate)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .6f))
        }
        AppointmentActionRow("Historial", R.drawable.ic_v2_file, onHistory)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .6f))
        AppointmentActionRow("Guías y ayuda", R.drawable.ic_bitavida_ai, onHelp)
    }
}

@Composable
private fun AppointmentActionRow(label: String, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        MeasurementIcon(icon, 24, MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        MeasurementIcon(R.drawable.ic_v2_chevron, 18, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
