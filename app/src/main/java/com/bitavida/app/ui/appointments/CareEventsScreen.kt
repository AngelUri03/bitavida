package com.bitavida.app.ui.appointments

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.medical.CareSectionHeader
import com.bitavida.app.ui.medical.CurrentDetailButton
import com.bitavida.app.ui.medical.DetailButtonKind
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class EventsPage { HOME, CALENDAR, HISTORY }
private val spanish = Locale.forLanguageTag("es-MX")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)
private fun dateLabel(date: LocalDate) = date.format(dateFormat).replaceFirstChar { it.uppercaseChar() }

@Composable
internal fun CareEventsGuidesHost(
    viewModel: com.bitavida.app.ui.education.BitavidaAiViewModel,
    initialIds: List<String>,
    motion: Boolean,
    bottomBar: @Composable () -> Unit,
    onExit: () -> Unit,
) {
    com.bitavida.app.ui.components.BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = motion) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, bottomBar = bottomBar) { padding ->
            com.bitavida.app.ui.education.BitavidaAiRoute(
                viewModel = viewModel,
                navigationContext = null,
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .padding(horizontal = com.bitavida.app.ui.theme.BitavidaSpacing.ScreenHorizontal),
                procedureContext = true,
                procedureInitialIds = initialIds,
                exitLabel = "Citas",
                onExit = onExit,
            )
        }
    }
}

@Composable
fun CareEventsRoute(viewModel: CareEventsViewModel, person: String, onBack: () -> Unit,
    bottomBar: @Composable (Boolean) -> Unit = {}, onCalendarDetailExit: (() -> Unit)? = null,
    onCancellationComplete: (() -> Unit)? = null,
    aiViewModel: com.bitavida.app.ui.education.BitavidaAiViewModel? = null) {
    val state by viewModel.state.collectAsState()
    var seenCancellation by remember { mutableLongStateOf(state.cancellationGeneration) }
    LaunchedEffect(state.cancellationGeneration) {
        if (state.cancellationGeneration != seenCancellation) {
            seenCancellation = state.cancellationGeneration
            onCancellationComplete?.invoke()
        }
    }
    val dispatch: (CareEventsAction) -> Unit = { action ->
        val current = viewModel.state.value
        if (onCalendarDetailExit != null && action == CareEventsAction.Dismiss && !current.saving) {
            val editedId = current.editing?.id
            val closingDetail = current.selected != null && current.draft == null && !current.consentRequested
            viewModel.onAction(action)
            if (editedId != null) viewModel.onAction(CareEventsAction.Open(editedId))
            else if (closingDetail) onCalendarDetailExit()
        } else viewModel.onAction(action)
    }
    CareEventsScreen(state, dispatch, person, onBack, bottomBar,
        reopenEditedOnClose = onCalendarDetailExit == null, historyPhotoActor = viewModel.actor,
        aiViewModel = aiViewModel) { event ->
        CareEventAttachments(actor = viewModel.actor, event = event)
    }
}

@Composable
fun CareEventsScreen(state: CareEventsState, onAction: (CareEventsAction) -> Unit, person: String,
    onBack: () -> Unit, bottomBar: @Composable (Boolean) -> Unit = {},
    reopenEditedOnClose: Boolean = true,
    historyPhotoActor: com.bitavida.app.domain.repository.CareEventActor? = null,
    aiViewModel: com.bitavida.app.ui.education.BitavidaAiViewModel? = null,
    attachments: @Composable (CareEvent) -> Unit = {}) {
    var pageName by rememberSaveable { mutableStateOf(EventsPage.HOME.name) }
    var showGuides by rememberSaveable { mutableStateOf(false) }
    var guideIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var seenCancellation by rememberSaveable { mutableLongStateOf(state.cancellationGeneration) }
    LaunchedEffect(state.cancellationGeneration) {
        if (state.cancellationGeneration != seenCancellation) {
            seenCancellation = state.cancellationGeneration
            pageName = EventsPage.HOME.name
        }
    }
    val page = EventsPage.entries.firstOrNull { it.name == pageName } ?: EventsPage.HOME
    val now by rememberAppointmentClock(state.events)
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedText)
    val motion = rememberBitavidaMotionAllowed(null)
    val modal = state.draft != null || state.selected != null || state.consentRequested
    fun back() {
        if (modal) onAction(CareEventsAction.Dismiss)
        else if (page != EventsPage.HOME) pageName = EventsPage.HOME.name
        else onBack()
    }
    if (showGuides && aiViewModel != null) {
        // The shared AI session must not remain active when a global tab leaves this screen.
        DisposableEffect(aiViewModel) {
            onDispose { aiViewModel.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear) }
        }
        CareEventsGuidesHost(
            viewModel = aiViewModel,
            initialIds = guideIds,
            motion = motion,
            bottomBar = { bottomBar(!state.saving) },
            onExit = {
                showGuides = false
                guideIds = emptyList()
            },
        )
        return
    }
    if (state.draft != null) {
        com.bitavida.app.ui.components.BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = motion) {
            CareEventWizard(state, onAction, person, onClose = {
                val editedId = state.editing?.id
                onAction(CareEventsAction.Dismiss)
                if (editedId != null && reopenEditedOnClose) onAction(CareEventsAction.Open(editedId))
            })
            CareEventOverlays(state, onAction, attachments)
        }
        return
    }
    if (state.selected != null) {
        val related = remember(state.selected?.draft?.title, aiViewModel) {
            aiViewModel?.registeredProcedureGuides(state.selected?.draft?.title.orEmpty()).orEmpty()
        }
        CareEventDetailScreen(state = state, onAction = onAction, attachments = attachments,
            onGuide = if (related.isNotEmpty()) ({ guideIds = related.map { it.id }; showGuides = true }) else null)
        CareEventOverlays(state, onAction, attachments)
        return
    }
    BackHandler { back() }
    com.bitavida.app.ui.components.BitavidaFluidBackground(Modifier.fillMaxSize(), motionAllowed = motion) {
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = { bottomBar(!state.saving && !modal) }) { padding ->
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                if (motion) fadeIn() togetherWith fadeOut()
                else EnterTransition.None togetherWith ExitTransition.None
            },
            label = "Pantalla de citas",
        ) { visiblePage ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("header") {
                if (visiblePage == EventsPage.HOME) CareEventsRootHeader(person)
                else CareEventsHeader(
                    title = when (visiblePage) { EventsPage.CALENDAR -> "Calendario de citas"; EventsPage.HISTORY -> "Historial"; else -> "Citas" },
                    subtitle = when (visiblePage) { EventsPage.CALENDAR -> "Consulta tus citas y estudios"; EventsPage.HISTORY -> "Consulta tus eventos anteriores"; else -> "" },
                    person = person, onBack = { back() },
                )
            }
            if (state.loading) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error != null && state.draft == null) item("error") {
                Column { Text(state.error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { onAction(CareEventsAction.Reload) }) { Text("Reintentar") } }
            }
            when (visiblePage) {
                EventsPage.HOME -> careEventsHome(
                    state = state, now = now, motion = motion, photoActor = historyPhotoActor,
                    onCreate = { onAction(CareEventsAction.Create(CareEventKind.REVIEW_TO_SCHEDULE)) },
                    onCalendar = {
                        selectedText = LocalDate.now().toString()
                        pageName = EventsPage.CALENDAR.name
                    },
                    onHistory = { pageName = EventsPage.HISTORY.name },
                    onHelp = { if (aiViewModel != null) { guideIds = emptyList(); showGuides = true } },
                    onOpen = { onAction(CareEventsAction.Open(it)) },
                )
                EventsPage.HISTORY -> {
                    val groups = appointmentHistoryGroups(state.events, now)
                    if (groups.isEmpty()) item("emptyHistory") { EmptyEvents("Aún no hay eventos anteriores", "Aquí encontrarás tus consultas y estudios anteriores.", null, {}) }
                    groups.forEach { (month, events) ->
                        item("history_month_${month ?: "undated"}") {
                            Text(month?.format(DateTimeFormatter.ofPattern("MMMM yyyy", spanish))
                                ?.replaceFirstChar { it.uppercaseChar() } ?: "Sin fecha registrada",
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                        }
                        items(events, key = { "history_${it.id}" }) { event ->
                            val hasPhotos = rememberHistoryHasPhotos(historyPhotoActor, event)
                            CareEventHistoryCard(event, now, hasPhotos) { onAction(CareEventsAction.Open(event.id)) }
                        }
                    }
                }
                EventsPage.CALENDAR -> {
                    item("calendar") { AppointmentCalendar(selected, state.events, motion) { selectedText = it.toString() } }
                    item("date") { Text(dateLabel(selected), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() }) }
                    val rows = eventsOnDate(state.events, selected)
                    if (rows.isEmpty()) item("emptyDay") { EmptyEvents("Sin citas ni estudios", "No hay eventos programados para este día.", null, {}) }
                    items(rows, key = { "day_${it.id}" }) { event -> CareEventListCard(event) { onAction(CareEventsAction.Open(event.id)) } }
                }
            }
        }
        }
    }
    CareEventOverlays(state, onAction, attachments)
    }
}

/** Read attachment presence only for visible history cards, without loading image bytes. */
@Composable
private fun rememberHistoryHasPhotos(
    actor: com.bitavida.app.domain.repository.CareEventActor?,
    event: CareEvent,
): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { com.bitavida.app.data.appointments.CareEventPhotoStore(context.applicationContext) }
    val hasPhotos by produceState(false, actor, event.id, event.revision) {
        value = if (actor == null) false else try {
            store.list(actor, event.id).isNotEmpty()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        }
    }
    return hasPhotos
}

/** Refresh on resume and at the next appointment; periodic rechecks handle device clock changes. */
@Composable
private fun rememberAppointmentClock(events: List<CareEvent>): State<LocalDateTime> {
    val owner = LocalLifecycleOwner.current
    return produceState(LocalDateTime.now(), owner, events) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val current = LocalDateTime.now()
                value = current
                val next = events.mapNotNull { it.draft.scheduledAt }
                    .filter { it.isAfter(current) }.minOrNull()
                val untilNext = next?.let { Duration.between(current, it).toMillis().coerceAtLeast(1L) } ?: 60_000L
                delay(minOf(untilNext, 60_000L))
            }
        }
    }
}

@Composable
private fun CareEventsRootHeader(person: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(com.bitavida.app.ui.theme.BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.bitavida.app.ui.components.BitavidaIconContainer(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_v2_calendar),
            contentDescription = null,
            containerColor = MaterialTheme.bitavidaColors.paleBlue,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(com.bitavida.app.ui.theme.BitavidaSpacing.ExtraSmall),
        ) {
            Text("Citas", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Text("Consultas, estudios y seguimiento médico", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Acompañando a ${person.trim().substringBefore(' ')}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyEvents(title: String, description: String, action: String?, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), shadowElevation = 2.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementIcon(R.drawable.ic_v2_calendar, 24, MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) CurrentDetailButton(DetailButtonKind.FILLED, onClick, Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Preview(showBackground = true)
@Composable private fun CareEventsLightPreview() { BitavidaTheme { CareEventsScreen(CareEventsState(loading = false), {}, "Persona de ejemplo", {}) } }
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun CareEventsDarkPreview() { BitavidaTheme(darkTheme = true) { CareEventsScreen(CareEventsState(loading = false), {}, "Persona de ejemplo", {}) } }
@Preview(showBackground = true, widthDp = 320)
@Composable private fun CareEventsCompactPreview() { BitavidaTheme { CareEventsScreen(CareEventsState(loading = false), {}, "Persona de ejemplo", {}) } }
@Preview(showBackground = true, fontScale = 2f)
@Composable private fun CareEventsLargePreview() { BitavidaTheme { CareEventsScreen(CareEventsState(loading = false), {}, "Persona de ejemplo", {}) } }
