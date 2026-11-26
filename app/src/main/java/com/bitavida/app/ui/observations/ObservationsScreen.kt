package com.bitavida.app.ui.observations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.ObservationFollowUpDisplayStatus
import com.bitavida.app.domain.usecase.observationFollowUpStatuses
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.components.*
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.medical.CareSectionHeader
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun ObservationsRoute(
    viewModel: ObservationsViewModel,
    personName: String,
    onBack: () -> Unit,
    onTodaySelected: () -> Unit,
    motionAllowed: Boolean,
    embeddedInPlanScaffold: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenEducation: () -> Unit = {},
    fromToday: Boolean = false,
    onPlanSelected: (() -> Unit)? = null,
    calendarDetailPage: ObservationsPage? = null,
) {
    val state by viewModel.state.collectAsState()
    val now = state.observationProjectionNow(observationMoment())
    val followUpStatuses = remember(state.snapshot.followUps, viewModel.actor.personUnderCareId, now) {
        observationFollowUpStatuses(state.snapshot.followUps, viewModel.actor.personUnderCareId, now)
    }
    ObservationPhotoCoordinator(viewModel)

    val libraryScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val captureScroll = rememberScrollState()
    val guideScroll = rememberScrollState()
    val historyScroll = rememberScrollState()
    val occurrenceScroll = rememberScrollState()
    val recordScroll = rememberScrollState()
    val scheduleScroll = rememberScrollState()
    val changesScroll = rememberScrollState()
    fun scrollFor(page: ObservationsPage) = when (page) {
        ObservationsPage.LIBRARY -> libraryScroll
        ObservationsPage.DETAIL -> detailScroll
        ObservationsPage.REGISTER -> captureScroll
        ObservationsPage.GUIDE -> guideScroll
        ObservationsPage.HISTORY -> historyScroll
        ObservationsPage.OCCURRENCE_DETAIL -> occurrenceScroll
        ObservationsPage.RECORD_DETAIL -> recordScroll
        ObservationsPage.FOLLOW_UP -> scheduleScroll
        ObservationsPage.FOLLOW_UP_HISTORY -> changesScroll
    }
    val currentScroll = scrollFor(state.page)
    LaunchedEffect(state.selectedObservationId) { detailScroll.scrollTo(0) }
    LaunchedEffect(state.selectedRecordId) { recordScroll.scrollTo(0) }
    LaunchedEffect(state.draft?.id) { if (state.draft != null) captureScroll.scrollTo(0) }
    LaunchedEffect(state.scheduleStep) { scheduleScroll.scrollTo(0) }

    val back: () -> Unit = {
        if (!state.saving) when {
            state.consentVisible -> viewModel.onAction(ObservationsAction.DismissConsent)
            state.suspensionObservationId != null -> viewModel.onAction(ObservationsAction.DismissSuspend)
            state.descriptionProposal != null -> viewModel.onAction(ObservationsAction.DismissDescription)
            calendarDetailPage != null && state.page == calendarDetailPage -> onBack()
            state.page != ObservationsPage.LIBRARY -> viewModel.onAction(ObservationsAction.Back)
            else -> onBack()
        }
    }
    BackHandler { back() }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        AnimatedContent(
            targetState = state,
            contentKey = { it.observationDestination() },
            modifier = modifier.fillMaxSize(),
            transitionSpec = {
                if (!motionAllowed || !observationDestinationAnimates(initialState.page, targetState.page)) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val duration = BitavidaFluidMotion.StateTransition
                    val direction = observationNavigationDirection(initialState.page, targetState.page)
                    (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { direction * (it / 10) }) togetherWith
                        (fadeOut(tween(duration / 2)) + slideOutHorizontally(tween(duration)) { -direction * (it / 14) })
                }
            },
            label = "observation_navigation",
        ) { rendered ->
            val active = rendered.observationDestination() == state.observationDestination()
            val dispatch: (ObservationsAction) -> Unit = { action ->
                if (active) viewModel.onAction(action)
            }
            val navigateBack: () -> Unit = { if (active) back() }
            if (rendered.page == ObservationsPage.REGISTER) {
                ObservationRegistrationContent(
                    state = rendered,
                    motionAllowed = motionAllowed,
                    onAction = dispatch,
                    photoContent = {
                        rendered.draft?.let { draft -> ObservationPhotos(viewModel, draft) }
                    },
                    onBack = navigateBack,
                    scrollState = captureScroll,
                    backLabel = if (fromToday) "Mi día" else "Observaciones",
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scrollFor(rendered.page)).imePadding()
                        .padding(horizontal = BitavidaSpacing.ScreenHorizontal, vertical = BitavidaSpacing.Default),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                ) {
                    when (rendered.page) {
                        ObservationsPage.LIBRARY -> CareSectionHeader(
                            "Observaciones", personName, R.drawable.ic_v2_stethoscope,
                            onBack = navigateBack, motionAllowed = motionAllowed,
                            subtitle = "Cambios y señales del paciente",
                        )
                        ObservationsPage.FOLLOW_UP -> Unit
                        else -> CurrentMedicationBack(
                            navigateBack, motionAllowed, enabled = !rendered.saving, label = "Observaciones",
                        )
                    }
                    if (rendered.loading) SecondaryText("Cargando observaciones…")
                    if (rendered.error != null && rendered.draft == null) {
                        CaptureError(rendered.error)
                        if (rendered.loadError != null) MeasurementButton(
                            "Reintentar carga", { dispatch(ObservationsAction.Reload) },
                            style = MeasurementButtonStyle.OUTLINED, enabled = !rendered.saving,
                        )
                    }
                    when (rendered.page) {
                        ObservationsPage.LIBRARY -> ObservationLibraryContent(
                            rendered, motionAllowed, dispatch, onOpenEducation, followUpStatuses,
                        )
                        ObservationsPage.DETAIL -> ObservationDetailContent(
                            rendered, motionAllowed, dispatch,
                            { event -> if (active) viewModel.openScheduled(event) }, now,
                        )
                        ObservationsPage.GUIDE -> ObservationGuideContent(rendered)
                        ObservationsPage.HISTORY -> {
                            SectionTitle("Historial de observaciones")
                            ObservationHistoryContent(rendered, motionAllowed, dispatch, now = now)
                        }
                        ObservationsPage.OCCURRENCE_DETAIL -> ObservationOccurrenceDetailContent(
                            rendered,
                            dispatch,
                            onRegister = { event -> if (active) viewModel.openScheduled(event) },
                        )
                        ObservationsPage.RECORD_DETAIL -> ObservationRecordDetailContent(
                            rendered, motionAllowed, dispatch,
                        ) { ObservationStoredPhoto(viewModel, it) }
                        ObservationsPage.FOLLOW_UP -> ObservationScheduleContent(
                            rendered, motionAllowed, onRequestNotificationPermission, dispatch, personName,
                        )
                        ObservationsPage.FOLLOW_UP_HISTORY -> ObservationFollowUpHistory(rendered)
                    }
                    Spacer(Modifier.height(BitavidaSpacing.Default))
                }
            }
        }
    }
    if (embeddedInPlanScaffold) {
        content(Modifier)
    } else {
        BitavidaFluidBackground(Modifier.fillMaxSize(), { currentScroll.value.toFloat() }, motionAllowed) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    BitavidaThreadNavigation(
                        items = listOf(
                            BitavidaNavigationItem("Mi día", R.drawable.ic_v2_home),
                            BitavidaNavigationItem("Plan", R.drawable.ic_v2_clipboard),
                            BitavidaNavigationItem("Citas", R.drawable.ic_v2_calendar),
                            BitavidaNavigationItem("Informe", R.drawable.ic_v2_chart),
                        ),
                        selectedIndex = if (fromToday) 0 else 1,
                        currentSectionLabel = if (fromToday) "Sección actual: Mi día" else "Sección actual: Plan",
                        onSelected = { if (it == 0) onTodaySelected() else if (it == 1) (onPlanSelected ?: onBack)() },
                        motionEnabledOverride = motionAllowed,
                    )
                },
            ) { padding -> content(Modifier.padding(padding)) }
        }
    }
    if (state.suspensionObservationId != null) {
        ObservationSuspensionSheet(state, motionAllowed, viewModel::onAction)
    }
    if (state.descriptionProposal != null) {
        ObservationDescriptionSheet(state, motionAllowed, viewModel::onAction)
    }
    if (state.consentVisible) ObservationConsentDialog(state, viewModel::onAction)
}

internal data class ObservationDestination(val page: ObservationsPage, val identity: String?)

internal fun ObservationsUiState.observationDestination(): ObservationDestination = ObservationDestination(
    page,
    when (page) {
        ObservationsPage.REGISTER -> draft?.id
        ObservationsPage.GUIDE -> draft?.observationId
        ObservationsPage.OCCURRENCE_DETAIL -> selectedOccurrence?.id
        ObservationsPage.RECORD_DETAIL -> selectedRecordId
        ObservationsPage.DETAIL, ObservationsPage.FOLLOW_UP, ObservationsPage.FOLLOW_UP_HISTORY ->
            selectedObservationId
        ObservationsPage.LIBRARY, ObservationsPage.HISTORY -> null
    },
)

internal fun observationDestinationAnimates(from: ObservationsPage, to: ObservationsPage): Boolean =
    from != ObservationsPage.FOLLOW_UP && to != ObservationsPage.FOLLOW_UP

internal fun observationNavigationDirection(from: ObservationsPage, to: ObservationsPage): Int {
    fun depth(page: ObservationsPage): Int = when (page) {
        ObservationsPage.LIBRARY -> 0
        ObservationsPage.DETAIL -> 1
        ObservationsPage.HISTORY, ObservationsPage.FOLLOW_UP, ObservationsPage.FOLLOW_UP_HISTORY -> 2
        ObservationsPage.OCCURRENCE_DETAIL, ObservationsPage.RECORD_DETAIL -> 3
        ObservationsPage.REGISTER, ObservationsPage.GUIDE -> 4
    }
    return if (depth(to) >= depth(from)) 1 else -1
}

@Composable
internal fun ObservationLibraryContent(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    onOpenEducation: () -> Unit = {},
    followUpStatuses: Map<String, ObservationFollowUpDisplayStatus> = emptyMap(),
) {
    ObservationCatalogSearchHeader(state.searchExpanded, state.saving, motionAllowed) {
        onAction(ObservationsAction.ToggleSearch)
    }
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    AnimatedVisibility(state.searchExpanded,
        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
        BitavidaLimitedTextField(state.query, { onAction(ObservationsAction.Query(it)) }, CaptureLimit.SEARCH,
            Modifier.fillMaxWidth().semantics { contentDescription = "Buscar observaciones" },
            placeholder = { Text("Buscar mareo, herida, sed...") },
            shape = MaterialTheme.shapes.medium, singleLine = true,
            trailingIcon = if (state.query.isNotEmpty()) { {
                TextButton({ onAction(ObservationsAction.Query("")) },
                    Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)) { Text("Limpiar") }
            } } else null)
    }
    GlucoseCaptureChip(
        label = "Solo con seguimiento",
        selected = state.onlyWithFollowUp,
        enabled = !state.saving,
        motionAllowed = motionAllowed,
        onClick = { onAction(ObservationsAction.ToggleFollowUpFilter) },
        modifier = Modifier.testTag("observation_follow_up_filter"),
    )
    val matchingSearch = ObservationCatalog.search(state.query)
    val results = if (state.onlyWithFollowUp) matchingSearch.filter { it.id in followUpStatuses } else matchingSearch
    if (results.isEmpty() && !state.loading) {
        if (state.onlyWithFollowUp) {
            InformationCard("Sin observaciones con seguimiento",
                if (state.query.isBlank()) "Desactiva el filtro para ver todas las observaciones."
                else "No hay seguimientos que coincidan con la búsqueda.")
        } else {
            InformationCard("No encontramos una observación similar.", "Puedes documentarla con tus propias palabras.")
            MeasurementButton("Otro cambio observado", { onAction(ObservationsAction.Open("OBS-027")) },
                Modifier.fillMaxWidth())
        }
    }
    ObservationCategory.entries.forEach { category ->
        val definitions = results.filter { it.category == category }
        val count = ObservationCatalog.all.count { it.category == category && it.id in followUpStatuses }
        AnimatedVisibility(definitions.isNotEmpty(),
            enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
            exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
            val expandedByFilter = state.query.isNotBlank() || state.onlyWithFollowUp
            val expanded = expandedByFilter || category in state.expanded
            MeasurementPanel {
                val interaction = remember { MutableInteractionSource() }
                Row(Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                    .then(if (expandedByFilter) Modifier else Modifier.clickable(
                        interactionSource = interaction, indication = null, role = Role.Button,
                    ) { onAction(ObservationsAction.ToggleCategory(category)) })
                    .semantics(mergeDescendants = true) { stateDescription = if (expanded) "Expandida" else "Contraída" },
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically) {
                    MeasurementIcon(category.icon())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                        SectionTitle(category.title, small = true)
                        SecondaryText(category.description)
                        if (count > 0) ObservationFollowUpBadge("$count en seguimiento")
                    }
                    Box(Modifier.graphicsLayer { rotationZ = if (expanded) 90f else 0f }) {
                        MeasurementIcon(R.drawable.ic_v2_chevron, 18)
                    }
                }
                AnimatedVisibility(expanded,
                    enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                    exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
                    Column {
                        definitions.forEachIndexed { index, definition ->
                            if (index > 0) ActionDivider()
                            ObservationCatalogItem(
                                category = category,
                                definition = definition,
                                status = followUpStatuses[definition.id],
                                motionAllowed = motionAllowed,
                            ) { onAction(ObservationsAction.Open(definition.id)) }
                        }
                    }
                }
            }
        }
    }
    ObservationLibraryActions(
        onHistory = { onAction(ObservationsAction.History) },
        onEducation = onOpenEducation,
    )
}

@Composable
private fun ObservationFollowUpBadge(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(label, Modifier.padding(horizontal = BitavidaSpacing.Small, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ObservationCatalogItem(
    category: ObservationCategory,
    definition: ObservationDefinition,
    status: ObservationFollowUpDisplayStatus?,
    motionAllowed: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .bitavidaFluidInteraction(source, true, motionAllowed, shape = MaterialTheme.shapes.medium, restingElevationDp = 0f)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = BitavidaSpacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeasurementIcon(category.icon())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
            Text(definition.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            SecondaryText(definition.description)
            status?.let {
                ObservationFollowUpBadge(
                    when (it) {
                        ObservationFollowUpDisplayStatus.ACTIVE -> "En seguimiento"
                        ObservationFollowUpDisplayStatus.UPCOMING -> "Por iniciar"
                    }
                )
            }
        }
        MeasurementIcon(R.drawable.ic_v2_chevron, 18)
    }
}

/** Same two-row management group approved for Medicamentos and Mediciones. */
@Composable
private fun ObservationLibraryActions(
    onHistory: () -> Unit,
    onEducation: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            MeasurementManagementRow(
                R.drawable.ic_v2_calendar,
                "Historial de observaciones",
                true,
                onHistory,
            )
            HorizontalDivider(
                Modifier.padding(horizontal = BitavidaSpacing.Medium),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            )
            MeasurementManagementRow(
                R.drawable.ic_bitavida_ai,
                "Guías y ayuda",
                true,
                onEducation,
            )
        }
    }
}

/** Keeps the secondary action compact, stacking naturally when the system uses large text. */
@Composable
internal fun ObservationCatalogSearchHeader(
    expanded: Boolean,
    saving: Boolean,
    motionAllowed: Boolean,
    onToggle: () -> Unit,
) {
    val searchAction: @Composable () -> Unit = {
        com.bitavida.app.ui.medical.CurrentDetailButton(
            kind = com.bitavida.app.ui.medical.DetailButtonKind.OUTLINED,
            onClick = onToggle,
            enabled = !saving,
            motionAllowedOverride = motionAllowed,
            modifier = Modifier.testTag("observation_search_toggle").semantics {
                contentDescription = if (expanded) "Cerrar búsqueda de observaciones" else "Buscar observaciones"
                stateDescription = if (expanded) "Expandida" else "Contraída"
            },
        ) {
            MeasurementIcon(if (expanded) R.drawable.ic_v2_close else R.drawable.ic_observation_search,
                20, LocalContentColor.current)
            Spacer(Modifier.width(BitavidaSpacing.Small))
            Text(if (expanded) "Cerrar" else "Buscar")
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            SecondaryText("Selecciona lo que observaste")
            searchAction()
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SecondaryText("Selecciona lo que observaste") }
            searchAction()
        }
    }
}

@Composable
private fun ObservationConsentDialog(state: ObservationsUiState, onAction: (ObservationsAction) -> Unit) {
    AlertDialog(onDismissRequest = { onAction(ObservationsAction.DismissConsent) },
        title = { Text("Guardar información de salud") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Esta observación se guardará cifrada en este dispositivo. Es información sanitaria sensible.")
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = state.consentAccepted, role = Role.Checkbox,
                onValueChange = { onAction(ObservationsAction.AcceptConsent(it)) },
            ), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.consentAccepted, null)
                Text("Acepto guardar esta información sanitaria en este dispositivo.", Modifier.weight(1f))
            }
            Text("Esta aceptación no declara representación legal ni comparte datos por Internet.",
                style = MaterialTheme.typography.bodySmall)
            state.error?.let { CaptureError(it) }
        } },
        confirmButton = { TextButton({ onAction(ObservationsAction.ConfirmConsent) },
            enabled = state.consentAccepted && !state.saving) { Text(if (state.saving) "Guardando…" else "Aceptar y guardar") } },
        dismissButton = { TextButton({ onAction(ObservationsAction.DismissConsent) },
            enabled = !state.saving) { Text("No guardar") } })
}

@Composable
private fun observationMoment(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = LocalDateTime.now()
                delay((60 - value.second) * 1000L)
            }
        }
    }.value
}

internal fun ObservationResult.label(): String = when (this) {
    ObservationResult.CHANGES -> "Se observaron cambios"
    ObservationResult.NO_CHANGES -> "No se observaron cambios"
    ObservationResult.NOT_PERFORMED -> "No se pudo realizar la revisión"
    ObservationResult.EVENT -> "Acontecimiento registrado"
}

internal fun ObservationCategory.icon(): Int = when (this) {
    ObservationCategory.FEET, ObservationCategory.GENERAL -> R.drawable.ic_v2_stethoscope
    ObservationCategory.DIGESTION -> R.drawable.ic_v2_droplet
    ObservationCategory.MOBILITY -> R.drawable.ic_v2_home
    ObservationCategory.OTHER -> R.drawable.ic_v2_file
    else -> R.drawable.ic_v2_clipboard
}
