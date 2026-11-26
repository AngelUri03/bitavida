package com.bitavida.app.ui.demo

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import com.bitavida.app.ui.components.BitavidaFluidMotion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaDatePicker
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaNavigationItem
import com.bitavida.app.ui.components.BitavidaThreadNavigation
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.captureLabel
import com.bitavida.app.ui.measurements.icon
import com.bitavida.app.ui.measurements.label
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private data class AgendaRow(val key: String, val kind: TodayActivityKind, val time: LocalDateTime, val value: Any)
private data class MedicationConfirmation(val item: TodayMedicationItem, val wasPrevious: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayAgendaContent(
    modifier: Modifier, caregiver: String, patient: String, now: LocalDateTime, selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit, state: TodayDemoState, projection: TodayCareProjection?,
    medicationActivities: List<UpcomingMedicationAdministration>, observationActivities: List<ObservationOccurrence>,
    reminders: List<CareReminder>, notes: List<Note>, motionOverride: Boolean?, listState: LazyListState,
    actionsEnabled: Boolean, onPlan: () -> Unit, onRetry: () -> Unit,
    onMedicationTaken: (TodayMedicationItem) -> Unit, onMedicationOptions: (TodayMedicationItem) -> Unit,
    onMeasurementRegister: (TodayMeasurementItem) -> Unit, onMeasurementNotPerformed: (TodayMeasurementItem) -> Unit,
    onObservationRegister: (ObservationOccurrence) -> Unit, onObservationNotPerformed: (ObservationOccurrence) -> Unit,
    onReminderDone: (CareReminder) -> Unit, onReminderPostpone: (CareReminder) -> Unit,
    onReminderSelected: (String) -> Unit, onReminderEdit: ((String) -> Unit)?,
    onReminderDiscard: ((String) -> Unit)?, onNotes: () -> Unit, onNoteSelected: (Note) -> Unit,
    readNoteImage: suspend (String) -> ByteArray?, onProfile: (() -> Unit)?,
    onNotifications: (() -> Unit)?, onAppointments: (() -> Unit)?, onReport: (() -> Unit)?,
    feedback: String?, feedbackError: Boolean, onUndo: (() -> Unit)?, savingId: String?, confirmedId: String?,
    notesOverlayVisible: Boolean, onCalendarOpen: (() -> Unit)?, onAiSelected: (() -> Unit)?,
    unreadNotifications: Int, careEvents: List<CareEvent>, onCareEventSelected: (String) -> Unit,
) {
    val motion = rememberBitavidaMotionAllowed(motionOverride)
    val entrance = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) entrance.animateTo(1f, tween(BitavidaFluidMotion.ThreadReveal)) else entrance.snapTo(1f)
    }
    var tappedMedication by remember { mutableStateOf<TodayMedicationItem?>(null) }
    var tappedWasPrevious by remember { mutableStateOf(false) }
    // A second activity may be started while a previous persisted confirmation is still visible.
    val retainedConfirmations = remember { mutableStateMapOf<String, MedicationConfirmation>() }
    val exitingConfirmations = remember { mutableStateListOf<String>() }
    val retainedSnapshot = retainedConfirmations.toMap()
    val exitingKeys = exitingConfirmations.toSet()
    LaunchedEffect(feedbackError, savingId) {
        if (feedbackError && savingId == null) tappedMedication = null
    }
    val data = projection ?: remember(medicationActivities, observationActivities, reminders, selectedDate) {
        val meds = medicationActivities.filter { it.startsAt.toLocalDate() == selectedDate }.map(::TodayMedicationItem)
        val obs = observationActivities.filter { it.scheduledAt.toLocalDate() == selectedDate && it.record == null }
        val rem = reminders.filter { it.scheduledAt.toLocalDate() == selectedDate && it.status == CareReminderStatus.PENDING }
        TodayCareProjection(medications = meds, observations = obs, reminders = rem,
            hasScheduledActivities = meds.isNotEmpty() || obs.isNotEmpty() || rem.isNotEmpty(),
            progress = TodayCareProgress(total = meds.size + obs.size + rem.size))
    }
    // Undo or an authoritative correction may restore a pending occurrence while it is exiting.
    // In that case the pending row immediately regains its stable key and actions.
    LaunchedEffect(data.medications, exitingKeys, retainedSnapshot, confirmedId) {
        data.medications.forEach { item ->
            val key = "med:${item.id}"
            if (key in exitingConfirmations || item.id in retainedConfirmations) {
                exitingConfirmations.remove(key)
                retainedConfirmations.remove(item.id)
                if (tappedMedication?.id == item.id && confirmedId != item.id) tappedMedication = null
            }
        }
    }
    val all = remember(data, confirmedId, tappedMedication, careEvents, retainedSnapshot) {
        buildList {
            data.medications.forEach { add(AgendaRow("med:${it.id}", TodayActivityKind.MEDICATION, it.occurrence.startsAt, it)) }
            data.measurements.forEach { add(AgendaRow("measurement:${it.id}", TodayActivityKind.MEASUREMENT, it.occurrence.scheduledAt, it)) }
            data.observations.forEach { add(AgendaRow("observation:${it.id}", TodayActivityKind.OBSERVATION, it.scheduledAt, it)) }
            data.reminders.forEach { add(AgendaRow("reminder:${it.id}", TodayActivityKind.REMINDER, it.scheduledAt, it)) }
            careEvents.forEach { event ->
                val moment = event.draft.scheduledAt ?: event.draft.scheduledDate?.atTime(java.time.LocalTime.MAX)
                if (moment != null) add(AgendaRow("care_event:${event.id}", TodayActivityKind.CARE_EVENT, moment, event))
            }
            data.resolvedActivities.filter { it.kind == TodayActivityKind.MEDICATION &&
                ((it.id == confirmedId && it.id == tappedMedication?.id) || it.id in retainedSnapshot) &&
                data.medications.none { pending -> pending.id == it.id } &&
                it.outcome == TodayActivityOutcome.PERFORMED }
                .forEach { add(AgendaRow("med:${it.id}", it.kind, it.scheduledAt, it)) }
        }.sortedWith(compareBy<AgendaRow> { it.time }.thenBy { it.key })
    }
    var filter by rememberSaveable { mutableStateOf("Todas") }
    var expanded by rememberSaveable(selectedDate, filter) { mutableStateOf(false) }
    var previousExpanded by rememberSaveable(selectedDate, filter) { mutableStateOf(false) }
    val categories = listOf("Todas", "Medicamentos", "Mediciones", "Observaciones", "Recordatorios") +
        if (careEvents.isNotEmpty()) listOf("Citas y Estudios") else emptyList()
    LaunchedEffect(categories) {
        if (filter !in categories) filter = "Todas"
    }
    fun category(kind: TodayActivityKind) = when (kind) {
        TodayActivityKind.MEDICATION -> "Medicamentos"
        TodayActivityKind.MEASUREMENT -> "Mediciones"
        TodayActivityKind.OBSERVATION -> "Observaciones"
        TodayActivityKind.REMINDER -> "Recordatorios"
        TodayActivityKind.CARE_EVENT -> "Citas y Estudios"
    }
    val filtered = all.filter { filter == "Todas" || category(it.kind) == filter }
    fun isPrevious(row: AgendaRow): Boolean {
        if (row.value is TodayResolvedActivity) {
            val retained = retainedSnapshot[row.value.id]
            if (retained != null) return retained.wasPrevious
            if (row.value.id == tappedMedication?.id) return tappedWasPrevious
        }
        val end = when (val event = row.value) {
            is TodayMedicationItem -> event.occurrence.endsAt
            is TodayMeasurementItem -> event.occurrence.windowEndAt
            is ObservationOccurrence -> event.windowEndAt
            else -> null
        }
        return (end ?: row.time).isBefore(now)
    }
    val previous = filtered.filter(::isPrevious)
    val currentAndFuture = filtered.filterNot(::isPrevious)
    // Keep a committed card in its original slot through confirmation. During its single exit,
    // select its replacement at the same time so the rest of the list does not move twice.
    val targetKeys = activityTargetKeys(
        currentAndFuture.map { it.key }, previous.map { it.key }, expanded, previousExpanded, exitingKeys,
    )
    val targetKeySet = targetKeys.toSet()
    val latestTargetKeys by rememberUpdatedState(targetKeySet)
    // Keep outgoing rows only while they remain in the real projection and animate out.
    // The thread itself stays mounted, so its progress is not restarted by a filter change.
    val retainedKeys = remember(selectedDate) { mutableStateListOf<String>() }
    val availableKeys = all.map { it.key }
    val transitionKeys = reconcileActivityTransitionKeys(availableKeys, targetKeys, retainedKeys, motion)
    val rowsByKey = all.associateBy { it.key }
    val visible = transitionKeys.mapNotNull(rowsByKey::get)
    LaunchedEffect(targetKeys, availableKeys, motion) {
        val next = reconcileActivityTransitionKeys(availableKeys, targetKeys, retainedKeys, motion)
        retainedKeys.clear()
        retainedKeys.addAll(next)
    }
    val currentVisible = visible.filterNot(::isPrevious)
    val previousVisible = visible.filter(::isPrevious)
    // Match the actual layout order: previous rows are rendered after both expansion controls.
    val orderedVisible = currentVisible + previousVisible
    val navigation = listOf(
        BitavidaNavigationItem("Mi día", R.drawable.ic_v2_home),
        BitavidaNavigationItem("Plan", R.drawable.ic_v2_clipboard),
        BitavidaNavigationItem("Citas", R.drawable.ic_v2_calendar),
        BitavidaNavigationItem("Informe", R.drawable.ic_v2_chart))
    BitavidaFluidBackground(modifier.fillMaxSize(), scrollPixels = { listState.firstVisibleItemScrollOffset.toFloat() }, motionAllowed = motion) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                com.bitavida.app.ui.components.TodayAgendaNavigation(navigation, selectedIndex = 0,
                    isEnabled = { it < 2 || (it == 2 && onAppointments != null) || (it == 3 && onReport != null) },
                    onSelected = { when (it) { 1 -> onPlan(); 2 -> onAppointments?.invoke(); 3 -> onReport?.invoke() } })
            },
            snackbarHost = {
                if (feedback != null) Surface(shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(feedback, Modifier.weight(1f), color = if (feedbackError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if (onUndo != null) TextButton(onClick = onUndo, enabled = actionsEnabled) { Text("Deshacer") }
                    }
                }
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item("header") { AgendaHeader(caregiver, patient, now, onProfile,
                    Modifier.graphicsLayer { alpha = entrance.value; translationY = (1f - entrance.value) * 8.dp.toPx() }) }
                if (state == TodayDemoState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant) }
                else if (state == TodayDemoState.Error) item { TextButton(onClick = onRetry) { Text("Reintentar") } }
                else {
                    item("progress") { AgendaProgress(data.progress, Modifier.graphicsLayer {
                        alpha = entrance.value; translationY = (1f - entrance.value) * 12.dp.toPx()
                    }) }
                    item("date") {
                        val locale = LocalLocale.current.platformLocale
                        val date = selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text((if (selectedDate == now.toLocalDate()) "Hoy, " else "") + date,
                                Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                            OutlinedButton(onClick = { onCalendarOpen?.invoke() }, enabled = onCalendarOpen != null, contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                MeasurementIcon(R.drawable.ic_v2_calendar, 20, MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp)); Text("Calendario")
                            }
                        }
                    }
                    item("filters") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { label ->
                                val count = if (label == "Todas") all.size else all.count { category(it.kind) == label }
                                FilterChip(selected = filter == label, onClick = { filter = label },
                                    label = { Text("$label ($count)", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = if (label == "Todas") null else ({
                                        MeasurementIcon(when (label) {
                                            "Medicamentos" -> R.drawable.ic_v2_pill
                                            "Mediciones" -> R.drawable.ic_v2_droplet
                                            "Observaciones" -> R.drawable.ic_v2_stethoscope
                                            "Citas y Estudios" -> R.drawable.ic_v2_calendar
                                            else -> R.drawable.ic_v2_clock
                                        }, 18, if (filter == label) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                    }),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        iconColor = MaterialTheme.colorScheme.primary,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary),
                                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = filter == label,
                                        borderColor = MaterialTheme.colorScheme.outline,
                                        selectedBorderColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                    item("activities") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TodayActivityThread(
                                keys = orderedVisible.map { it.key }, motion = motion,
                                outcomes = orderedVisible.associate { row ->
                                    val outcome = (row.value as? TodayResolvedActivity)?.outcome
                                    (row.key as Any) to when (outcome) {
                                        TodayActivityOutcome.PERFORMED, TodayActivityOutcome.PARTIALLY_PERFORMED -> TodayActivityNodeOutcome.PERFORMED
                                        TodayActivityOutcome.NOT_PERFORMED, TodayActivityOutcome.DISCARDED -> TodayActivityNodeOutcome.NOT_PERFORMED
                                        TodayActivityOutcome.AMBIGUOUS, TodayActivityOutcome.UNCONFIRMED,
                                        null -> TodayActivityNodeOutcome.PENDING
                                    }
                                },
                            ) { threadState ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val activity: @Composable (Int, AgendaRow) -> Unit = { index, row ->
                                        key(row.key) {
                                            val targeted = row.key in targetKeySet
                                            val resolved = row.value as? TodayResolvedActivity
                                            val show = targeted && row.key !in exitingKeys
                                            val animate = remember(row.key) { index < 8 }
                                            TodayActivityReveal(
                                                visible = show, ordinal = if (exitingKeys.isNotEmpty()) 0 else index,
                                                motion = motion, animate = animate,
                                                modifier = if (show) Modifier else Modifier.clearAndSetSemantics {},
                                                onHidden = {
                                                    if (row.key !in latestTargetKeys) retainedKeys.remove(row.key)
                                                    if (resolved != null && row.key !in latestTargetKeys) {
                                                        retainedConfirmations.remove(resolved.id)
                                                        if (tappedMedication?.id == resolved.id) tappedMedication = null
                                                        exitingConfirmations.remove(row.key)
                                                        retainedKeys.remove(row.key)
                                                    }
                                                },
                                            ) {
                                                AgendaActivity(row, threadState, actionsEnabled && show, savingId,
                                                    confirmation = retainedSnapshot[resolved?.id]?.item ?:
                                                        tappedMedication?.takeIf { it.id == confirmedId }, motion = motion,
                                                    onConfirmationEnd = { if (row.key !in exitingConfirmations) exitingConfirmations.add(row.key) },
                                                    medication = { item ->
                                                        tappedMedication?.takeIf { it.id == confirmedId }?.let { prior ->
                                                            retainedConfirmations[prior.id] = MedicationConfirmation(prior, tappedWasPrevious)
                                                        }
                                                        tappedWasPrevious = isPrevious(row)
                                                        tappedMedication = item
                                                        onMedicationTaken(item)
                                                    }, medicationOptions = onMedicationOptions,
                                                    measurement = onMeasurementRegister, measurementNo = onMeasurementNotPerformed,
                                                    observation = onObservationRegister, observationNo = onObservationNotPerformed,
                                                    reminder = onReminderDone, postpone = onReminderPostpone, reminderDetails = onReminderSelected,
                                                    reminderEdit = onReminderEdit, reminderDiscard = onReminderDiscard, careEventSelected = onCareEventSelected)
                                            }
                                        }
                                    }
                                    currentVisible.forEachIndexed { index, row -> activity(index, row) }
                                    val currentCount = currentAndFuture.count { it.key !in exitingKeys }
                                    val previousCount = previous.count { it.key !in exitingKeys }
                                    val duration = if (motion) BitavidaFluidMotion.StateTransition else 0
                                    AnimatedVisibility(visible = currentCount > 5,
                                        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                                        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
                                        TodayExpansionButton(expanded, (currentCount - 5).coerceAtLeast(0),
                                            { expanded = !expanded }, motion, Modifier.padding(start = 24.dp))
                                    }
                                    previousVisible.forEachIndexed { index, row -> activity(index, row) }
                                    AnimatedVisibility(visible = previousCount > 0,
                                        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                                        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
                                        TodayExpansionButton(previousExpanded, previousCount,
                                            { previousExpanded = !previousExpanded }, motion,
                                            Modifier.padding(start = 24.dp), previous = true)
                                    }
                                }
                            }
                            val showEmpty = filtered.isEmpty() ||
                                (exitingKeys.isNotEmpty() && filtered.all { it.value is TodayResolvedActivity && it.key in exitingKeys })
                            val emptyDuration = if (motion) BitavidaFluidMotion.StateTransition else 0
                            AnimatedVisibility(visible = showEmpty,
                                enter = expandVertically(tween(emptyDuration)) + fadeIn(tween(emptyDuration)),
                                exit = shrinkVertically(tween(emptyDuration)) + fadeOut(tween(emptyDuration))) {
                                Text(if (data.pendingCount == 0 && data.hasScheduledActivities) "¡Día al corriente! No quedan pendientes por resolver hoy." else if (all.isEmpty()) "No hay actividades programadas para esta fecha." else "No hay actividades en esta categoría.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item("notes") {
                        TodayNotesCarousel(notes = notes, readImage = readNoteImage, onNoteSelected = onNoteSelected,
                            onAllNotes = onNotes, motion = motion, overlayVisible = notesOverlayVisible)
                    }
                    if (onAiSelected != null) item("bitavida_ai") {
                        TodayAiCard(listState = listState, motion = motion, onClick = onAiSelected)
                    }
                }
            }
        }
    }

}

@Composable
private fun AgendaIcon(icon: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.bitavidaColors.paleBlue) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            MeasurementIcon(icon, 25, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AgendaHeader(caregiver: String, patient: String, now: LocalDateTime,
    onProfile: (() -> Unit)?, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_bitavida_mark), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Bitavida", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            if (onProfile != null) Surface(onClick = onProfile,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Configuración" },
                shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Box(contentAlignment = Alignment.Center) {
                    MeasurementIcon(R.drawable.ic_v2_settings, 24, MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(now.format(DateTimeFormatter.ofPattern("EEEE · d 'de' MMMM", locale)).replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val greeting = when (now.hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
        Text("$greeting, ${caregiver.trim().substringBefore(' ')}", Modifier.padding(top = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall)
        Text("Acompañando a ${patient.trim().substringBefore(' ')}", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AgendaProgress(progress: TodayCareProgress, modifier: Modifier = Modifier) {
    val fraction = if (progress.total > 0) progress.attended.toFloat() / progress.total else 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progreso del día")
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AgendaIcon(R.drawable.ic_v2_calendar)
            Column(Modifier.weight(1f)) {
                Text("Mi día de cuidado", style = MaterialTheme.typography.titleMedium)
                Text(if (progress.total == 0) "Sin actividades programadas" else "${progress.attended} de ${progress.total} actividades atendidas",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
            if (progress.total > 0) Text("${(animated * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AgendaActivity(row: AgendaRow, threadState: TodayActivityThreadState, enabled: Boolean, savingId: String?,
    confirmation: TodayMedicationItem?, motion: Boolean, onConfirmationEnd: () -> Unit,
    medication: (TodayMedicationItem) -> Unit, medicationOptions: (TodayMedicationItem) -> Unit,
    measurement: (TodayMeasurementItem) -> Unit, measurementNo: (TodayMeasurementItem) -> Unit,
    observation: (ObservationOccurrence) -> Unit, observationNo: (ObservationOccurrence) -> Unit,
    reminder: (CareReminder) -> Unit, postpone: (CareReminder) -> Unit, reminderDetails: (String) -> Unit,
    reminderEdit: ((String) -> Unit)?, reminderDiscard: ((String) -> Unit)?, careEventSelected: (String) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val format = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val time = if (row.value is CareEvent && row.value.draft.scheduledAt == null) "Sin hora" else row.time.format(DateTimeFormatter.ofPattern(format, locale))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.size(16.dp).todayActivityNode(threadState, row.key))
        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 64.dp))
        when (val value = row.value) {
            is TodayMedicationItem -> TodayCompactActivity(value.occurrence.medicationName,
                value.occurrence.amount.value + " " + value.occurrence.amount.unit, R.drawable.ic_v2_pill,
                "Registrar toma", enabled && savingId != value.id, { medication(value) },
                listOf(TodayCompactAction("Más opciones") { medicationOptions(value) }), Modifier.weight(1f),
                onLongPress = { medicationOptions(value) })
            is TodayMeasurementItem -> TodayCompactActivity(value.kind.label(),
                value.occurrence.suggestedContext.captureLabel(), value.kind.icon(), "Registrar", enabled,
                { measurement(value) }, listOf(TodayCompactAction("No se realizó") { measurementNo(value) }), Modifier.weight(1f))
            is ObservationOccurrence -> TodayCompactActivity(ObservationCatalog.get(value.observationId).name,
                "", R.drawable.ic_v2_stethoscope, "Registrar", enabled, { observation(value) },
                listOf(TodayCompactAction("No se realizó") { observationNo(value) }), Modifier.weight(1f))
            is CareReminder -> TodayCompactActivity(value.title, "", R.drawable.ic_v2_clock, "Marcar como hecho", enabled,
                { reminder(value) }, buildList {
                    add(TodayCompactAction("Posponer") { postpone(value) })
                    if (reminderEdit != null) add(TodayCompactAction("Editar") { reminderEdit(value.id) })
                    add(TodayCompactAction("Ver detalle") { reminderDetails(value.id) })
                    if (reminderDiscard != null) add(TodayCompactAction("Descartar") { reminderDiscard(value.id) })
                }, Modifier.weight(1f))
            is CareEvent -> TodayCompactActivity(value.draft.title,
                if (value.draft.kind == CareEventKind.APPOINTMENT) "Cita confirmada" else "Estudio programado",
                R.drawable.ic_v2_calendar, "Ver detalle", enabled, { careEventSelected(value.id) },
                emptyList(), Modifier.weight(1f))
            is TodayResolvedActivity -> if (value.kind == TodayActivityKind.MEDICATION && confirmation?.id == value.id &&
                value.outcome == TodayActivityOutcome.PERFORMED) {
                val progress = remember(value.id) { Animatable(0f) }
                val finish by rememberUpdatedState(onConfirmationEnd)
                LaunchedEffect(value.id, motion) {
                    if (motion) progress.animateTo(1f, tween(BitavidaFluidMotion.ThreadReveal + BitavidaFluidMotion.Selection))
                    else progress.snapTo(1f)
                    finish()
                }
                TodayCompactActivity(confirmation.occurrence.medicationName,
                    confirmation.occurrence.amount.value + " " + confirmation.occurrence.amount.unit,
                    R.drawable.ic_v2_pill, "Registrar toma", false, {}, emptyList(), Modifier.weight(1f),
                    confirmed = true, confirmationProgress = { progress.value })
            } else Surface(Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AgendaIcon(when (value.kind) { TodayActivityKind.MEDICATION -> R.drawable.ic_v2_pill
                        TodayActivityKind.MEASUREMENT -> R.drawable.ic_v2_droplet
                        TodayActivityKind.OBSERVATION -> R.drawable.ic_v2_stethoscope
                        TodayActivityKind.REMINDER -> R.drawable.ic_v2_clock
                        TodayActivityKind.CARE_EVENT -> R.drawable.ic_v2_calendar })
                    Column(Modifier.weight(1f)) {
                        Text(value.title, style = MaterialTheme.typography.titleSmall)
                        value.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(when (value.outcome) { TodayActivityOutcome.PERFORMED -> "Realizada"
                            TodayActivityOutcome.PARTIALLY_PERFORMED -> "Parcial"
                            TodayActivityOutcome.NOT_PERFORMED -> "No realizada"
                            TodayActivityOutcome.DISCARDED -> "Descartada"
                            TodayActivityOutcome.AMBIGUOUS -> "Varias declaraciones"
                            TodayActivityOutcome.UNCONFIRMED -> "Sin confirmar" },
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
