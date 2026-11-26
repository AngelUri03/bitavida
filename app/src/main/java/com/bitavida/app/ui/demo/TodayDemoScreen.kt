package com.bitavida.app.ui.demo

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.MedicationOccurrenceTiming
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.domain.validation.limitTextCharacters
import com.bitavida.app.ui.brand.BitavidaMark
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.BitavidaHeaderFlow
import com.bitavida.app.ui.components.BitavidaNavigationItem
import com.bitavida.app.ui.components.BitavidaThreadNavigation
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.measurements.captureLabel
import com.bitavida.app.ui.measurements.icon
import com.bitavida.app.ui.measurements.label
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.medical.displayName
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import androidx.compose.ui.platform.LocalLocale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class TodayDemoState { Loading, Empty, HasPlan, Error }

private val LocalTodayEntryProgress = staticCompositionLocalOf<() -> Float> { { 1f } }

@Composable
private fun Modifier.todayEntry(section: Boolean = false): Modifier {
    val progress = LocalTodayEntryProgress.current
    return graphicsLayer {
        val fraction = ((progress() - if (section) 0.06f else 0f) /
            if (section) 0.94f else 1f).coerceIn(0f, 1f)
        alpha = fraction
        translationY = (1f - fraction) * BitavidaSpacing.Small.toPx()
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun TodayDemoScreen(
    modifier: Modifier = Modifier,
    caregiverDisplayName: String = "Ana",
    personUnderCareDisplayName: String = "María",
    currentDateTime: LocalDateTime = LocalDateTime.now(),
    state: TodayDemoState = TodayDemoState.Empty,
    medicationActivities: List<UpcomingMedicationAdministration> = emptyList(),
    observationActivities: List<ObservationOccurrence> = emptyList(),
    onObservationSelected: (ObservationOccurrence) -> Unit = {},
    motionEnabledOverride: Boolean? = null,
    onPlanSelected: () -> Unit = {},
    onMedicationSelected: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    careReminders: List<CareReminder> = emptyList(),
    onReminderSelected: (String) -> Unit = {},
    onReminderEdit: ((String) -> Unit)? = null,
    onReminderDiscard: ((String) -> Unit)? = null,
    onAllReminders: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    projection: TodayCareProjection? = null,
    notes: List<Note> = emptyList(),
    onMedicationTaken: (TodayMedicationItem) -> Unit = {},
    onMedicationNotTaken: (TodayMedicationItem) -> Unit = {},
    onMeasurementRegister: (TodayMeasurementItem) -> Unit = {},
    onMeasurementNotPerformed: (TodayMeasurementItem) -> Unit = {},
    onObservationRegister: (ObservationOccurrence) -> Unit = onObservationSelected,
    onObservationNotPerformed: (ObservationOccurrence) -> Unit = {},
    onReminderDone: (CareReminder) -> Unit = {},
    onReminderPostpone: (CareReminder) -> Unit = {},
    onNoteSelected: (Note) -> Unit = {},
    onAllNotes: () -> Unit = {},
    readNoteImage: suspend (String) -> ByteArray? = { null },
    notesOverlayVisible: Boolean = false,
    actionsEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    onMedicationOptions: (TodayMedicationItem) -> Unit = {},
    medicationFeedback: String? = null,
    onUndoMedication: (() -> Unit)? = null,
    medicationSavingId: String? = null,
    medicationFeedbackIsError: Boolean = false,
    medicationConfirmedId: String? = null,
    initialExpandedSections: Set<String> = emptySet(),
    selectedDate: java.time.LocalDate = currentDateTime.toLocalDate(),
    onDateSelected: (java.time.LocalDate) -> Unit = {},
    onCalendarOpen: (() -> Unit)? = null,
    onProfileSelected: (() -> Unit)? = null,
    onNotificationsSelected: (() -> Unit)? = null,
    unreadNotifications: Int = 0,
    onAppointmentsSelected: (() -> Unit)? = null,
    onReportSelected: (() -> Unit)? = null,
    onAiSelected: (() -> Unit)? = null,
    careEvents: List<CareEvent> = emptyList(),
    onCareEventSelected: (String) -> Unit = {},
) = TodayTypography {
    TodayAgendaContent(
        modifier, caregiverDisplayName, personUnderCareDisplayName, currentDateTime, selectedDate, onDateSelected,
        state, projection, medicationActivities, observationActivities, careReminders, notes,
        motionEnabledOverride, listState, actionsEnabled, onPlanSelected, onRetry,
        onMedicationTaken, onMedicationOptions, onMeasurementRegister, onMeasurementNotPerformed,
        onObservationRegister, onObservationNotPerformed, onReminderDone, onReminderPostpone,
        onReminderSelected, onReminderEdit, onReminderDiscard, onAllNotes, onNoteSelected, readNoteImage, onProfileSelected, onNotificationsSelected,
        onAppointmentsSelected, onReportSelected, medicationFeedback, medicationFeedbackIsError,
        onUndoMedication, medicationSavingId, medicationConfirmedId, notesOverlayVisible, onCalendarOpen, onAiSelected,
        unreadNotifications, careEvents, onCareEventSelected,
    )
}

private fun LazyListScope.morePending(key: String, count: Int, expanded: Boolean, motion: Boolean, onToggle: () -> Unit) {
    if (count > 3) item("more_" + key, contentType = "group_footer") {
        val action: @Composable () -> Unit = {
            CurrentDetailButton(DetailButtonKind.OUTLINED, onToggle,
                modifier = Modifier.fillMaxWidth().testTag("today_more_" + key).semantics {
                    stateDescription = if (expanded) "Sección expandida" else "Sección contraída"
                },
                motionAllowedOverride = motion,
                contentPadding = PaddingValues(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.Small)) {
                Text(if (expanded) "Mostrar menos" else
                    "Ver " + (count - 3) + if (count - 3 == 1) " pendiente más" else " pendientes más",
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(BitavidaSpacing.Small))
                MeasurementIcon(if (expanded) R.drawable.ic_v2_close else R.drawable.ic_v2_plus, 16,
                    MaterialTheme.colorScheme.primary)
            }
        }
        if (key == "medications") {
            Box(Modifier.padding(start = BitavidaSpacing.Large)) { action() }
        } else TodaySectionPiece(first = false, last = true,
            Modifier.padding(start = BitavidaSpacing.Large)) {
            Box(Modifier.fillMaxWidth().padding(BitavidaSpacing.Small)) { action() }
        }
    }
}

/** Height follows the actual confirmation animation, including padding; no empty lazy slot remains. */
@Composable
private fun TodayMedicationEntry(
    item: TodayMedicationItem,
    schedule: String,
    enabled: Boolean,
    motion: Boolean,
    saving: Boolean,
    confirmed: Boolean,
    onTaken: () -> Unit,
    onOptions: () -> Unit,
    onVisualExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(item.id) { Animatable(0f) }
    val exit = rememberUpdatedState(onVisualExit)
    LaunchedEffect(confirmed, motion) {
        if (confirmed) {
            if (motion) progress.animateTo(1f, tween(BitavidaFluidMotion.ThreadReveal + BitavidaFluidMotion.Selection, easing = LinearEasing))
            else progress.snapTo(1f)
            exit.value()
        } else progress.snapTo(0f)
    }
    TodayMedicationSurface(
        item.id, item.occurrence.medicationName,
        item.occurrence.amount.value + " " + item.occurrence.amount.unit, schedule,
        enabled, motion, onTaken, onOptions,
        modifier = modifier.then(if (confirmed) Modifier.clipToBounds() else Modifier).layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val collapse = if (confirmed) ((progress.value - 0.62f) / 0.38f).coerceIn(0f, 1f) else 0f
            val remaining = 1f - FastOutSlowInEasing.transform(collapse)
            layout(placeable.width, (placeable.height * remaining).roundToInt()) {
                placeable.placeRelative(0, 0)
            }
        }.padding(bottom = BitavidaSpacing.Small),
        saving = saving, confirmed = confirmed, confirmationProgress = { progress.value },
        scheduleIcon = when (item.occurrence.activityRelation.takeIf {
            item.occurrence.timing == MedicationOccurrenceTiming.ACTIVITY_NOTICE
        }) {
            MedicationActivityRelation.BEFORE_BREAKFAST, MedicationActivityRelation.WITH_BREAKFAST,
            MedicationActivityRelation.AFTER_BREAKFAST, MedicationActivityRelation.BEFORE_LUNCH,
            MedicationActivityRelation.WITH_LUNCH, MedicationActivityRelation.AFTER_LUNCH,
            MedicationActivityRelation.BEFORE_DINNER, MedicationActivityRelation.WITH_DINNER,
            MedicationActivityRelation.AFTER_DINNER -> R.drawable.ic_v2_utensils
            else -> R.drawable.ic_v2_clock
        },
    )
}

@Composable
private fun TodayThreadMarker(
    state: TodayThreadState,
    key: String,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val lineHeightDp = with(LocalDensity.current) { lineHeight.toDp() }
    val color = MaterialTheme.bitavidaColors.gradientStart
    Box(modifier.width(BitavidaSpacing.Large).height(lineHeightDp), contentAlignment = Alignment.Center) {
        Spacer(Modifier.size(16.dp).testTag("today_thread_node_" + key)
            .todayThreadAnchor(state, when (key) {
                "medications" -> "medication_heading"
                "measurements" -> "measurement_heading"
                "observations" -> "observation_heading"
                "reminders" -> "reminder_heading"
                "notes_heading" -> "notes"
                else -> key
            })
            .clearAndSetSemantics { }
            .drawWithCache { onDrawBehind { drawCircle(color) } })
    }
}

@Composable
private fun TodayModuleHeading(
    title: String, count: Int?, icon: Int, key: String,
    threadState: TodayThreadState,
    modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null,
) {
    val large = LocalDensity.current.fontScale >= 1.5f
    val style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val titleColor = if (key == "clear_day") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val iconTop = with(LocalDensity.current) { (style.lineHeight.toDp() - 20.dp).coerceAtLeast(0.dp) / 2 }
    Row(modifier.fillMaxWidth().padding(top = BitavidaSpacing.Large, bottom = BitavidaSpacing.Small)
        .todayEntry(section = true).testTag("today_" + key),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        TodayThreadMarker(threadState, key, style.lineHeight)
        Box(Modifier.padding(top = iconTop)) {
            MeasurementIcon(icon, 20, MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
            if (large) {
                Text(title, Modifier.fillMaxWidth().semantics { heading() },
                    style = style, color = titleColor)
                if (count != null || trailing != null) Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    if (count != null) Text(
                        if (key == "notes_heading") "$count " + if (count == 1) "nota" else "notas"
                        else "$count " + if (count == 1) "pendiente" else "pendientes",
                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    trailing?.invoke()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    Text(title, Modifier.weight(1f, fill = false).alignByBaseline().semantics { heading() },
                        style = style, color = titleColor)
                    if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline().semantics { contentDescription = "$count elementos" })
                }
            }
        }
        if (!large) trailing?.invoke()
    }
}

@Composable
private fun TodayClearDay(hadSchedule: Boolean, onPlanSelected: () -> Unit, motion: Boolean,
    threadState: TodayThreadState, modifier: Modifier) {
    Column(modifier.testTag(if (hadSchedule) "today_caught_up" else "empty_journey")) {
        TodayModuleHeading(
            if (hadSchedule) "¡Día al corriente!" else "Hoy no tienes actividades programadas",
            null, if (hadSchedule) R.drawable.ic_v2_home else R.drawable.ic_v2_calendar,
            "clear_day", threadState)
        TodaySectionPiece(first = true, last = true, Modifier.padding(start = BitavidaSpacing.Large)) {
            Column(Modifier.padding(BitavidaSpacing.Default),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                Text(if (hadSchedule) "No quedan pendientes por resolver hoy" else
                    "Tu plan permanece disponible para organizar los próximos cuidados.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!hadSchedule) CurrentDetailButton(DetailButtonKind.OUTLINED, onPlanSelected,
                    Modifier.testTag("empty_primary_action"), motionAllowedOverride = motion) {
                    Text("Consultar Plan de cuidado")
                }
            }
        }
    }
}

@Composable
private fun TodayNotesCarousel(notes: List<Note>, onOpen: (Note) -> Unit, onAll: () -> Unit,
    motion: Boolean, enabled: Boolean, threadState: TodayThreadState) {
    Column(Modifier.testTag("today_notes")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 330.dp || LocalDensity.current.fontScale >= 1.5f
            TodayModuleHeading("Notas importantes", notes.size, R.drawable.ic_v2_file, "notes_heading", threadState,
                trailing = {
                    CurrentDetailButton(DetailButtonKind.OUTLINED, onAll, enabled = enabled,
                        motionAllowedOverride = motion,
                        modifier = Modifier.semantics { contentDescription = "Ver todas las notas" },
                        contentPadding = PaddingValues(horizontal = BitavidaSpacing.Small, vertical = BitavidaSpacing.Small)) {
                        if (!compact) {
                            Text("Ver todas", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(BitavidaSpacing.ExtraSmall))
                        }
                        MeasurementIcon(R.drawable.ic_v2_chevron, 16, MaterialTheme.colorScheme.primary)
                    }
                })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = BitavidaSpacing.Large)) {
            val cardWidth = minOf(maxWidth * if (LocalDensity.current.fontScale >= 1.5f) 0.92f else 0.84f, 340.dp)
            LazyRow(Modifier.fillMaxWidth().testTag("today_notes_carousel"),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                items(notes, key = { it.id }, contentType = { "note" }) { note ->
                    Surface(onClick = { onOpen(note) }, enabled = enabled,
                        modifier = Modifier.width(cardWidth).heightIn(min = 48.dp).testTag("today_note_" + note.id),
                        color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)), shadowElevation = 1.dp) {
                        Column(Modifier.padding(BitavidaSpacing.Default),
                            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small), verticalAlignment = Alignment.Top) {
                                MeasurementIcon(R.drawable.ic_v2_file, 18, MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(note.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                MeasurementIcon(R.drawable.ic_v2_chevron, 14, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            note.content?.takeIf(String::isNotBlank)?.let {
                                Text(limitTextCharacters(it, 160), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            if (note.content.isNullOrBlank() && note.imageId != null) Text("Nota con imagen",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayMedicationFeedback(
    message: String, undo: (() -> Unit)?, enabled: Boolean, motion: Boolean, error: Boolean, modifier: Modifier,
) {
    Surface(modifier.fillMaxWidth().testTag("today_medication_feedback"),
        color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        BoxWithConstraints(Modifier.padding(BitavidaSpacing.Medium)) {
            val label: @Composable (Modifier) -> Unit = { labelModifier ->
                Row(labelModifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    if (error) MeasurementIcon(R.drawable.ic_v2_close, 18, MaterialTheme.colorScheme.error)
                    else Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            val action: @Composable () -> Unit = {
                if (undo != null) CurrentDetailButton(
                    DetailButtonKind.OUTLINED, undo, enabled = enabled, motionAllowedOverride = motion,
                    modifier = Modifier.testTag("today_undo_medication"),
                ) { Text("Deshacer") }
            }
            if (maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    label(Modifier.fillMaxWidth())
                    action()
                }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                label(Modifier.weight(1f))
                action()
            }
        }
    }
}

@Composable
private fun TodayEditorialHeader(caregiver: String, patient: String, now: LocalDateTime,
    motion: Boolean) {
    val locale = LocalLocale.current.platformLocale
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE · d 'de' MMMM", locale) }
    val date = now.format(dateFormatter)
        .replaceFirstChar { it.uppercaseChar() }
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Column(Modifier.todayEntry(), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val brand: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    BitavidaMark(Modifier.size(30.dp), strokeWidth = 3.4.dp, eventRadius = 2.6.dp)
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.bitavidaColors.brand)
                }
            }
            if (maxWidth < 350.dp || largeText) Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                brand()
                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                brand()
                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val greeting = when (now.hour) {
            in 5..11 -> R.string.today_greeting
            in 12..18 -> R.string.today_greeting_afternoon
            else -> R.string.today_greeting_evening
        }
        Text(stringResource(greeting, caregiver.firstNameForToday()),
            Modifier.padding(top = BitavidaSpacing.Small).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.today_patient_context, patient.firstNameForToday()),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BitavidaHeaderFlow(motionAllowed = motion, bidirectional = true, organic = true)
    }
}

private fun String.firstNameForToday(): String = trim().substringBefore(' ').ifBlank { trim() }

@Composable
private fun todayTime(value: LocalDateTime): String {
    val locale = LocalLocale.current.platformLocale
    val twentyFourHour = DateFormat.is24HourFormat(LocalContext.current)
    val formatter = remember(locale, twentyFourHour) {
        DateTimeFormatter.ofPattern(if (twentyFourHour) "HH:mm" else "h:mm a", locale)
    }
    return value.format(formatter)
}

@Composable
private fun windowScheduleLabel(start: LocalDateTime, end: LocalDateTime?, now: LocalDateTime): String {
    val prefix = if (start.toLocalDate().isBefore(now.toLocalDate())) "Desde ayer · " else ""
    return prefix + todayTime(start) + if (end == null) "" else " – " + todayTime(end) +
        if (end.toLocalDate() != start.toLocalDate()) " · día siguiente" else ""
}

@Composable
private fun medicationScheduleLabel(item: UpcomingMedicationAdministration, now: LocalDateTime): String =
    if (item.timing == MedicationOccurrenceTiming.ACTIVITY_NOTICE) {
        (item.activityRelation?.displayName(item.customActivity) ?: "Actividad indicada") + " · " + todayTime(item.startsAt)
    } else windowScheduleLabel(item.startsAt, item.endsAt, now)

@Composable
private fun TodayLoading() {
    Row(Modifier.fillMaxWidth().padding(vertical = BitavidaSpacing.Large),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        Text("Preparando tu día…", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodayError(onRetry: () -> Unit, motion: Boolean) {
    MeasurementPanel {
        Text("No pudimos abrir tu día", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        Text("Intenta de nuevo para consultar la información guardada en este dispositivo.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CurrentDetailButton(DetailButtonKind.OUTLINED, onRetry, Modifier.fillMaxWidth(),
            motionAllowedOverride = motion) { Text("Reintentar") }
    }
}

@Preview(name = "Mi día · Claro", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TodayDemoLightPreview() { BitavidaTheme { TodayDemoScreen() } }

@Preview(name = "Mi día · Oscuro", showBackground = true, widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TodayDemoDarkPreview() { BitavidaTheme(darkTheme = true) { TodayDemoScreen() } }

@Preview(name = "Mi día · Compacto", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
fun TodayDemoCompactPreview() { BitavidaTheme { TodayDemoScreen() } }

@Preview(name = "Mi día · Texto 200 %", showBackground = true, widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
fun TodayDemoLargeTextPreview() { BitavidaTheme { TodayDemoScreen() } }

@Preview(name = "Mi día · Vacío", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TodayDemoEmptyPreview() { BitavidaTheme { TodayDemoScreen(state = TodayDemoState.Empty) } }

@Preview(name = "Mi día · Sin movimiento", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TodayDemoReducedMotionPreview() { BitavidaTheme { TodayDemoScreen(motionEnabledOverride = false) } }
