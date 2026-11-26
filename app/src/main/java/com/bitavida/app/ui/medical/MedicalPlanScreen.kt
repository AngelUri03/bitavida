package com.bitavida.app.ui.medical

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.bitavida.app.ui.components.BitavidaLimitedTextField
import com.bitavida.app.domain.validation.CaptureLimit
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.InformationOrigin
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MedicalIndication
import com.bitavida.app.domain.model.MedicalIndicationStatus
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationIntakeRecord
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationPlanChangeKind
import com.bitavida.app.domain.model.MedicationPlanRevision
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderRuntimeState
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.lifecycleTransitions
import com.bitavida.app.domain.model.pendingReactivation
import com.bitavida.app.domain.usecase.MedicalCareField
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.domain.usecase.projectMedicationAdministrations
import com.bitavida.app.domain.validation.MedicationValueRules
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaConnectedSurface
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.components.PlanCareThreadCurve
import com.bitavida.app.ui.components.PlanCareThreadPainter
import com.bitavida.app.ui.components.animatePlanCareThreadPoint
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.components.BitavidaNavigationItem
import com.bitavida.app.ui.components.BitavidaStatusPill
import com.bitavida.app.ui.components.BitavidaThreadAction
import com.bitavida.app.ui.components.BitavidaThreadNavigation
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MedicalPlanRoute(
    viewModel: MedicalPlanViewModel,
    caregiverDisplayName: String,
    personUnderCareDisplayName: String,
    onTodaySelected: () -> Unit,
    modifier: Modifier = Modifier,
    reminderRuntimeState: MedicationReminderRuntimeState =
        MedicationReminderRuntimeState.DISABLED,
    onRequestReminderAccess: () -> Unit = {},
    glucoseViewModel: com.bitavida.app.ui.measurements.GlucoseViewModel? = null,
    bloodPressureViewModel:
        com.bitavida.app.ui.measurements.BloodPressureViewModel? = null,
    weightViewModel:
        com.bitavida.app.ui.measurements.WeightViewModel? = null,
    bitavidaAiViewModel:
        com.bitavida.app.ui.education.BitavidaAiViewModel? = null,
    observationsViewModel:
        com.bitavida.app.ui.observations.ObservationsViewModel? = null,
    remindersViewModel: com.bitavida.app.ui.reminders.RemindersViewModel? = null,
    onRemindersBack: (() -> Unit)? = null,
    activeReminderCount: Int? = null,
    activeMedicationCount: Int? = null,
    activeMeasurementCount: Int? = null,
    activeObservationCount: Int? = null,
    notesViewModel: com.bitavida.app.ui.notes.NotesViewModel? = null,
    onAppointmentsSelected: (() -> Unit)? = null,
    onReportSelected: (() -> Unit)? = null,
    onPlanExit: () -> Unit = onTodaySelected,
    onCalendarDetailExit: (() -> Unit)? = null,
    onPlanRootSelected: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    BackHandler(enabled = !(state.screen == MedicalPlanScreen.SECTION &&
        ((state.selectedSection in setOf(PlanSection.OBSERVATIONS, PlanSection.CARE_TASKS) &&
        observationsViewModel != null) ||
        (state.selectedSection == PlanSection.REMINDERS && remindersViewModel != null) ||
        (state.selectedSection == PlanSection.OTHER_INDICATIONS && notesViewModel != null)))) {
        when {
            state.sensitiveConsentDialogVisible ->
                viewModel.onAction(MedicalPlanAction.DeclineSensitiveConsent)
            state.medicationExitConfirmationVisible ->
                viewModel.onAction(
                    MedicalPlanAction.ContinueEditingMedicationRegistration,
                )
            state.medicationLifecycleDraft != null -> {
                if (!state.isSaving) {
                    viewModel.onAction(
                        MedicalPlanAction.DismissMedicationLifecycle,
                    )
                }
            }
            state.medicationIntakeDraft?.let { draft ->
                !draft.inlineEntry || state.screen == MedicalPlanScreen.MEDICATION_DETAIL
            } == true ->
                viewModel.onAction(
                    MedicalPlanAction.DismissMedicationIntake,
                )
            state.screen == MedicalPlanScreen.MEDICATION_DETAIL && onCalendarDetailExit != null -> onCalendarDetailExit()
            state.screen == MedicalPlanScreen.OVERVIEW -> onPlanExit()
            else -> viewModel.onAction(MedicalPlanAction.Back)
        }
    }
    MedicalPlanScreen(
        state = state,
        caregiverDisplayName = caregiverDisplayName,
        personUnderCareDisplayName = personUnderCareDisplayName,
        onAction = viewModel::onAction,
        onTodaySelected = onTodaySelected,
        reminderRuntimeState = reminderRuntimeState,
        onRequestReminderAccess = onRequestReminderAccess,
        glucoseViewModel = glucoseViewModel,
        bloodPressureViewModel = bloodPressureViewModel,
        weightViewModel = weightViewModel,
        bitavidaAiViewModel = bitavidaAiViewModel,
        observationsViewModel = observationsViewModel,
        remindersViewModel = remindersViewModel,
        onRemindersBack = onRemindersBack,
        activeReminderCount = activeReminderCount,
        activeMedicationCount = activeMedicationCount,
        activeMeasurementCount = activeMeasurementCount,
        activeObservationCount = activeObservationCount,
        notesViewModel = notesViewModel,
        onAppointmentsSelected = onAppointmentsSelected,
        onReportSelected = onReportSelected,
        onCalendarDetailExit = onCalendarDetailExit,
        onPlanRootSelected = onPlanRootSelected,
        modifier = modifier,
    )
}

@Composable
fun MedicalPlanScreen(
    state: MedicalPlanUiState,
    caregiverDisplayName: String,
    personUnderCareDisplayName: String,
    onAction: (MedicalPlanAction) -> Unit,
    onTodaySelected: () -> Unit,
    modifier: Modifier = Modifier,
    reminderRuntimeState: MedicationReminderRuntimeState =
        MedicationReminderRuntimeState.DISABLED,
    onRequestReminderAccess: () -> Unit = {},
    glucoseViewModel: com.bitavida.app.ui.measurements.GlucoseViewModel? = null,
    bloodPressureViewModel:
        com.bitavida.app.ui.measurements.BloodPressureViewModel? = null,
    weightViewModel:
        com.bitavida.app.ui.measurements.WeightViewModel? = null,
    bitavidaAiViewModel:
        com.bitavida.app.ui.education.BitavidaAiViewModel? = null,
    observationsViewModel:
        com.bitavida.app.ui.observations.ObservationsViewModel? = null,
    remindersViewModel: com.bitavida.app.ui.reminders.RemindersViewModel? = null,
    onRemindersBack: (() -> Unit)? = null,
    activeReminderCount: Int? = null,
    activeMedicationCount: Int? = null,
    activeMeasurementCount: Int? = null,
    activeObservationCount: Int? = null,
    notesViewModel: com.bitavida.app.ui.notes.NotesViewModel? = null,
    onAppointmentsSelected: (() -> Unit)? = null,
    onReportSelected: (() -> Unit)? = null,
    onCalendarDetailExit: (() -> Unit)? = null,
    onPlanRootSelected: (() -> Unit)? = null,
) {
    val items = listOf(
        BitavidaNavigationItem(stringResource(R.string.nav_today), R.drawable.ic_v2_home),
        BitavidaNavigationItem(stringResource(R.string.nav_plan), R.drawable.ic_v2_clipboard),
        BitavidaNavigationItem(stringResource(R.string.nav_appointments), R.drawable.ic_v2_calendar),
        BitavidaNavigationItem(stringResource(R.string.nav_report), R.drawable.ic_v2_chart),
    )
    val scroll = rememberScrollState()
    val medicationListScroll = rememberScrollState()
    val medicationListMemory = remember { MedicationListMemory() }
    var medicationHelpVisible by rememberSaveable { mutableStateOf(false) }
    var observationHelpVisible by rememberSaveable { mutableStateOf(false) }
    var reminderHelpVisible by rememberSaveable { mutableStateOf(false) }
    val remindersScrollMemory = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val openMedicationHelp: () -> Unit = {
        bitavidaAiViewModel?.let { ai ->
            observationHelpVisible = false
            reminderHelpVisible = false
            ai.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear)
            ai.onAction(com.bitavida.app.ui.education.BitavidaAiAction.NavigationContext(
                com.bitavida.app.domain.education.EducationGroup.MEDICATIONS))
            medicationHelpVisible = true
        }
    }
    val openObservationHelp: () -> Unit = {
        bitavidaAiViewModel?.let { ai ->
            medicationHelpVisible = false
            reminderHelpVisible = false
            ai.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear)
            ai.onAction(com.bitavida.app.ui.education.BitavidaAiAction.NavigationContext(
                com.bitavida.app.domain.education.EducationGroup.OBSERVATIONS))
            observationHelpVisible = true
        }
    }
    val openReminderHelp: () -> Unit = {
        bitavidaAiViewModel?.let { ai ->
            medicationHelpVisible = false
            observationHelpVisible = false
            ai.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear)
            ai.onAction(com.bitavida.app.ui.education.BitavidaAiAction.NavigationContext(
                com.bitavida.app.domain.education.EducationGroup.REMINDERS))
            reminderHelpVisible = true
        }
    }
    val activeScroll = if (state.screen == MedicalPlanScreen.SECTION &&
        state.selectedSection == PlanSection.MEDICATIONS
    ) medicationListScroll else scroll
    val motionAllowed = rememberBitavidaMotionAllowed(override = null)
    val transitionDuration =
        if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    // Do not abandon drafts or in-flight writes when switching to a new top-level destination.
    val glucoseNavigation = glucoseViewModel?.state?.collectAsState()?.value
    val pressureNavigation = bloodPressureViewModel?.state?.collectAsState()?.value
    val weightNavigation = weightViewModel?.state?.collectAsState()?.value
    val observationNavigation = observationsViewModel?.state?.collectAsState()?.value
    val reminderNavigation = remindersViewModel?.state?.collectAsState()?.value
    val noteNavigation = notesViewModel?.state?.collectAsState()?.value
    val canLeaveForDestination = !state.isSaving && state.medicationLifecycleDraft == null &&
        state.medicationIntakeDraft == null && state.statusChangeDraft == null &&
        !state.sensitiveConsentDialogVisible && !state.medicationExitConfirmationVisible &&
        glucoseNavigation?.let { it.draft == null && it.pendingFollowUpDraft == null && !it.saving &&
            !it.followUpSaving && !it.occurrenceDecisionSaving && !it.consentVisible && it.selectedOccurrence == null &&
            it.selectedRecordId == null } != false &&
        pressureNavigation?.let { it.draft == null && it.pendingFollowUpDraft == null && !it.saving &&
            !it.followUpSaving && !it.occurrenceDecisionSaving && !it.consentVisible && it.selectedOccurrence == null &&
            it.selectedRecordId == null } != false &&
        weightNavigation?.let { it.draft == null && it.pendingFollowUpDraft == null && !it.saving &&
            !it.followUpSaving && !it.occurrenceDecisionSaving && !it.consentVisible && it.selectedOccurrence == null &&
            it.selectedRecordId == null && !it.targetSheetVisible && !it.targetSaving } != false &&
        observationNavigation?.let { it.draft == null && it.scheduleDraft == null && !it.saving &&
            !it.photoPreparing && !it.consentVisible && it.suspensionObservationId == null } != false &&
        reminderNavigation?.let { it.form == null && it.sheet == null && !it.saving } != false &&
        noteNavigation?.let { it.form == null && it.sheet == null && !it.saving && !it.imagePreparing } != false
    var lastVisitedSection by rememberSaveable {
        mutableStateOf<PlanSection?>(null)
    }
    var planThreadRevealed by rememberSaveable {
        mutableStateOf(value = false)
    }
    var planThreadPosition by rememberSaveable { mutableFloatStateOf(0f) }
    var returnedSection by remember { mutableStateOf<PlanSection?>(null) }
    var overviewScrollPosition by rememberSaveable { mutableIntStateOf(0) }
    var previousScreen by remember { mutableStateOf(state.screen) }
    LaunchedEffect(
        state.screen,
        state.selectedSection,
        state.selectedIndicationId,
        state.selectedMedicationPlanId,
    ) {
        when {
            state.screen == MedicalPlanScreen.SECTION &&
                previousScreen == MedicalPlanScreen.OVERVIEW -> {
                overviewScrollPosition = scroll.value
                returnedSection = null
                scroll.scrollTo(0)
            }
            state.screen == MedicalPlanScreen.OVERVIEW &&
                previousScreen == MedicalPlanScreen.SECTION -> {
                scroll.scrollTo(overviewScrollPosition)
                returnedSection = lastVisitedSection
            }
            state.screen != MedicalPlanScreen.OVERVIEW -> scroll.scrollTo(0)
        }
        previousScreen = state.screen
    }
    LaunchedEffect(
        state.medicationRegistrationStep,
        state.medicationScheduleSubstep,
    ) {
        if (state.screen == MedicalPlanScreen.MEDICATION_REGISTRATION) {
            scroll.scrollTo(0)
        }
    }

    BitavidaFluidBackground(
        modifier = modifier.fillMaxSize(),
        scrollPixels = { activeScroll.value.toFloat() },
        motionAllowed = motionAllowed,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("medical_plan_screen"),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                if (state.screen != MedicalPlanScreen.MEDICATION_REGISTRATION) {
                    BitavidaThreadNavigation(
                        items = items,
                        selectedIndex = if (onCalendarDetailExit != null) 0 else 1,
                        currentSectionLabel =
                            stringResource(R.string.nav_current_section, items[if (onCalendarDetailExit != null) 0 else 1].label),
                        isDestinationEnabled = { index ->
                            when (index) {
                                0, 1 -> true
                                2 -> onAppointmentsSelected != null && canLeaveForDestination
                                3 -> onReportSelected != null && canLeaveForDestination
                                else -> false
                            }
                        },
                        onSelected = { index ->
                            if (index == 0 || index == 1 ||
                                (canLeaveForDestination && ((index == 2 && onAppointmentsSelected != null) ||
                                    (index == 3 && onReportSelected != null)))) {
                                if (medicationHelpVisible || observationHelpVisible || reminderHelpVisible) {
                                    medicationHelpVisible = false
                                    observationHelpVisible = false
                                    reminderHelpVisible = false
                                    bitavidaAiViewModel?.onAction(
                                        com.bitavida.app.ui.education.BitavidaAiAction.Clear,
                                    )
                                }
                                observationsViewModel?.onAction(
                                    com.bitavida.app.ui.observations.ObservationsAction.EnterCatalog,
                                )
                                remindersViewModel?.onAction(com.bitavida.app.ui.reminders.RemindersAction.OpenMain)
                                notesViewModel?.onAction(com.bitavida.app.ui.notes.NotesAction.DismissSheet)
                                onAction(MedicalPlanAction.OpenOverview)
                                when (index) {
                                    0 -> onTodaySelected()
                                    1 -> onPlanRootSelected?.invoke()
                                    2 -> onAppointmentsSelected?.invoke()
                                    3 -> onReportSelected?.invoke()
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            SharedTransitionLayout(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            ) {
                val sharedScope = this
                if ((medicationHelpVisible || observationHelpVisible || reminderHelpVisible) && bitavidaAiViewModel != null) {
                    val educationContext = when {
                        reminderHelpVisible -> com.bitavida.app.domain.education.EducationGroup.REMINDERS
                        observationHelpVisible -> com.bitavida.app.domain.education.EducationGroup.OBSERVATIONS
                        else -> com.bitavida.app.domain.education.EducationGroup.MEDICATIONS
                    }
                    com.bitavida.app.ui.education.BitavidaAiRoute(
                        viewModel = bitavidaAiViewModel,
                        navigationContext = educationContext,
                        modifier = Modifier.fillMaxSize().padding(horizontal = BitavidaSpacing.ScreenHorizontal),
                        onExit = {
                            medicationHelpVisible = false
                            observationHelpVisible = false
                            reminderHelpVisible = false
                            bitavidaAiViewModel.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear)
                        },
                        exitLabel = when {
                            reminderHelpVisible -> "Recordatorios"
                            observationHelpVisible -> "Observaciones"
                            else -> "Medicamentos"
                        },
                    )
                } else AnimatedContent(
                    modifier = Modifier.fillMaxSize(),
                    targetState = state.screen to state.selectedSection,
                    transitionSpec = {
                        val enteringSection =
                            targetState.first == MedicalPlanScreen.SECTION &&
                                initialState.first == MedicalPlanScreen.OVERVIEW
                        val direction = if (enteringSection) 1 else -1
                        (
                            fadeIn(tween(transitionDuration)) +
                                slideInHorizontally(tween(transitionDuration)) {
                                    direction * (it / 10)
                                }
                            ).togetherWith(
                            fadeOut(tween(transitionDuration / 2)) +
                                slideOutHorizontally(tween(transitionDuration)) {
                                    -direction * (it / 14)
                                },
                        )
                    },
                    label = "plan_destination",
                ) { (screen, section) ->
                    if (
                        screen == MedicalPlanScreen.SECTION &&
                        section == PlanSection.MEASUREMENTS
                    ) {
                        com.bitavida.app.ui.measurements.MeasurementsScreen(
                            personUnderCareDisplayName =
                                personUnderCareDisplayName,
                            onBack = {
                                onAction(MedicalPlanAction.Back)
                            },
                            onTodaySelected = onTodaySelected,
                            modifier = Modifier.fillMaxSize(),
                            embeddedInPlanScaffold = true,
                            onRequestNotificationPermission =
                                onRequestReminderAccess,
                            glucoseViewModel = glucoseViewModel,
                            bloodPressureViewModel =
                                bloodPressureViewModel,
                            weightViewModel = weightViewModel,
                            bitavidaAiViewModel = bitavidaAiViewModel,
                        )
                    } else if (screen == MedicalPlanScreen.SECTION &&
                        section == PlanSection.REMINDERS && remindersViewModel != null
                    ) {
                        remindersScrollMemory.SaveableStateProvider("reminders_lists") {
                            com.bitavida.app.ui.reminders.RemindersRoute(
                                viewModel = remindersViewModel,
                                patientName = personUnderCareDisplayName,
                                onBack = onRemindersBack ?: { onAction(MedicalPlanAction.Back) },
                                onOpenHelp = openReminderHelp,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (screen == MedicalPlanScreen.SECTION &&
                        section == PlanSection.OTHER_INDICATIONS && notesViewModel != null
                    ) {
                        com.bitavida.app.ui.notes.NotesRoute(
                            viewModel = notesViewModel,
                            patientName = personUnderCareDisplayName,
                            onBack = { onAction(MedicalPlanAction.Back) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (screen == MedicalPlanScreen.SECTION &&
                        section in setOf(PlanSection.OBSERVATIONS, PlanSection.CARE_TASKS) &&
                        observationsViewModel != null
                    ) {
                        com.bitavida.app.ui.observations.ObservationsRoute(
                            viewModel = observationsViewModel,
                            personName = personUnderCareDisplayName,
                            onBack = { onAction(MedicalPlanAction.Back) },
                            onTodaySelected = onTodaySelected,
                            motionAllowed = motionAllowed,
                            embeddedInPlanScaffold = true,
                            onRequestNotificationPermission = onRequestReminderAccess,
                            onOpenEducation = openObservationHelp,
                        )
                    } else {
                        val iconLink = motionAllowed && (
                            (screen == MedicalPlanScreen.SECTION &&
                                section == PlanSection.MEDICATIONS) ||
                                screen == MedicalPlanScreen.MEDICATION_DETAIL
                            )
                        CompositionLocalProvider(
                        LocalMedicationListMemory provides medicationListMemory,
                        LocalMedicationIconTransition provides if (iconLink) {
                            MedicationIconTransition(sharedScope, this)
                        } else null,
                    ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(
                                if (screen == MedicalPlanScreen.SECTION &&
                                    section == PlanSection.MEDICATIONS
                                ) medicationListScroll else scroll,
                            )
                            .padding(horizontal = BitavidaSpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
                    ) {
                        if (screen != MedicalPlanScreen.SECTION || section != PlanSection.MEDICATIONS) {
                            Spacer(Modifier.height(BitavidaSpacing.Small))
                        }
                        Header(
                            screen = screen,
                            section = section,
                            person = personUnderCareDisplayName,
                            motionAllowed = motionAllowed,
                            onBack = {
                                if (screen == MedicalPlanScreen.MEDICATION_DETAIL && onCalendarDetailExit != null) {
                                    if (!state.isSaving && state.medicationIntakeDraft == null && state.medicationLifecycleDraft == null) onCalendarDetailExit()
                                } else if (screen == MedicalPlanScreen.MEDICATION_DETAIL &&
                                    state.usesApprovedMedicationDetailLayout(LocalDateTime.now())
                                ) {
                                    if (!state.isSaving) {
                                        onAction(MedicalPlanAction.OpenSection(PlanSection.MEDICATIONS))
                                    }
                                } else onAction(MedicalPlanAction.Back)
                            },
                            compactMedicationHeader = screen == MedicalPlanScreen.MEDICATION_DETAIL &&
                                state.usesApprovedMedicationDetailLayout(LocalDateTime.now()),
                            medicationHistoryName = if (
                                screen == MedicalPlanScreen.MEDICATION_HISTORY
                            ) {
                                state.selectedMedicationPlan?.currentRevision?.medicationName
                            } else {
                                null
                            },
                            onBackEnabled = !state.isSaving,
                            titleOverride = if (
                                screen == MedicalPlanScreen.MEDICATION_REGISTRATION &&
                                state.editingExisting
                            ) {
                                "Revisar medicamento"
                            } else {
                                null
                            },
                            onContinue = when {
                                screen != MedicalPlanScreen.MEDICATION_REGISTRATION -> null
                                state.editingExisting &&
                                    state.medicationRegistrationStep ==
                                    MedicationRegistrationStep.REVIEW -> {
                                    { onAction(MedicalPlanAction.SaveGuidedMedication) }
                                }
                                state.medicationRegistrationStep in setOf(
                                    MedicationRegistrationStep.NAME,
                                    MedicationRegistrationStep.FORM,
                                    MedicationRegistrationStep.DETAILS,
                                    MedicationRegistrationStep.SCHEDULE,
                                    MedicationRegistrationStep.REMINDERS,
                                ) -> {
                                    {
                                        onAction(
                                            MedicalPlanAction.ContinueMedicationRegistration(
                                                expectedStep = state.medicationRegistrationStep,
                                            ),
                                        )
                                    }
                                }
                                else -> null
                            },
                            onContinueEnabled = !state.isSaving &&
                                (
                                    !state.editingExisting ||
                                        state.medicationRegistrationStep !=
                                        MedicationRegistrationStep.REVIEW ||
                                        state.hasMedicationChanges
                                ),
                            onContinueLabel = when {
                                state.editingExisting &&
                                    state.medicationRegistrationStep ==
                                    MedicationRegistrationStep.REVIEW ->
                                    "Confirmar cambios"
                                state.editingExisting -> "Aplicar"
                                state.medicationRegistrationStep ==
                                    MedicationRegistrationStep.REMINDERS ->
                                    "Revisar"
                                state.medicationRegistrationStep ==
                                    MedicationRegistrationStep.SCHEDULE &&
                                    state.medicationScheduleSubstep ==
                                    MedicationScheduleSubstep.VALIDITY ->
                                    "Recordatorios"
                                else -> "Continuar"
                            },
                        )
                        state.globalError?.let {
                            ErrorCard(it) { onAction(MedicalPlanAction.Retry) }
                        }
                        state.effect?.let { effect ->
                            val guidedMedicationSuccessAnimating =
                                state.screen ==
                                    MedicalPlanScreen.MEDICATION_REGISTRATION &&
                                    effect in setOf(
                                        MedicalPlanEffect.MedicationStored,
                                        MedicalPlanEffect.MedicationUpdated,
                                    )
                            if (!guidedMedicationSuccessAnimating) {
                                LaunchedEffect(effect) {
                                    onAction(MedicalPlanAction.EffectConsumed)
                                }
                            }
                        }
                        if (state.isLoading) {
                            Loading()
                        } else if (state.globalError == null) {
                            when (screen) {
                                MedicalPlanScreen.OVERVIEW ->
                                    Overview(
                                        onSectionSelected = { selectedSection ->
                                            lastVisitedSection = selectedSection
                                            if (selectedSection == PlanSection.REMINDERS) {
                                                remindersViewModel?.onAction(com.bitavida.app.ui.reminders.RemindersAction.OpenMain)
                                            }
                                            if (selectedSection == PlanSection.OBSERVATIONS) {
                                                observationsViewModel?.onAction(
                                                    com.bitavida.app.ui.observations.ObservationsAction.EnterCatalog,
                                                )
                                            }
                                            onAction(
                                                MedicalPlanAction.OpenSection(
                                                    selectedSection,
                                                ),
                                            )
                                        },
                                        returningSection = returnedSection,
                                        threadAlreadyRevealed = planThreadRevealed,
                                        onThreadRevealed = {
                                            planThreadRevealed = true
                                        },
                                        initialThreadPosition = planThreadPosition,
                                        onThreadPositionSaved = { position ->
                                            planThreadPosition = position
                                        },
                                        motionAllowed = motionAllowed,
                                        activeReminderCount = activeReminderCount,
                                        activeMedicationCount = activeMedicationCount,
                                        activeMeasurementCount = activeMeasurementCount,
                                        activeObservationCount = activeObservationCount,
                                    )
                                MedicalPlanScreen.SECTION ->
                                    PlanSectionContent(
                                        state = state,
                                        section = section,
                                        motionAllowed = motionAllowed,
                                        reminderRuntimeState =
                                            reminderRuntimeState,
                                        onRequestReminderAccess =
                                            onRequestReminderAccess,
                                        onAction = onAction,
                                        onMedicationHelp = openMedicationHelp,
                                    )
                                MedicalPlanScreen.INDICATION_EDITOR ->
                                    IndicationEditor(state, onAction)
                                MedicalPlanScreen.INDICATION_DETAIL ->
                                    IndicationDetail(state, caregiverDisplayName, onAction)
                                MedicalPlanScreen.INDICATION_HISTORY ->
                                    IndicationHistory(state, caregiverDisplayName)
                                MedicalPlanScreen.MEDICATION_EDITOR ->
                                    MedicationEditor(state, onAction)
                                MedicalPlanScreen.MEDICATION_HISTORY ->
                                    MedicationHistory(
                                        state = state,
                                        motionAllowed = motionAllowed,
                                        onAction = onAction,
                                    )
                                MedicalPlanScreen.MEDICATION_DETAIL -> {
                                    MedicationDetail(
                                        state = state,
                                        caregiver = caregiverDisplayName,
                                        reminderRuntimeState =
                                            reminderRuntimeState,
                                        onRequestReminderAccess =
                                            onRequestReminderAccess,
                                        onAction = onAction,
                                        motionAllowed = motionAllowed,
                                    )
                                    val name = state.selectedMedicationPlan?.currentRevision?.medicationName
                                    if (name != null && bitavidaAiViewModel?.registeredDrugGuides(name)?.isNotEmpty() == true) {
                                        com.bitavida.app.ui.education.RegisteredMedicationGuideAction(motionAllowed) {
                                            openMedicationHelp()
                                            bitavidaAiViewModel.onAction(
                                                com.bitavida.app.ui.education.BitavidaAiAction.OpenRegisteredDrug(name))
                                        }
                                    }
                                }
                                MedicalPlanScreen.MEDICATION_MANAGEMENT ->
                                    MedicationManagement(
                                        state = state,
                                        onAction = onAction,
                                        motionAllowed = motionAllowed,
                                    )
                                MedicalPlanScreen.MEDICATION_REGISTRATION ->
                                    MedicationRegistration(state, onAction, motionAllowed)
                            }
                        }
                        Spacer(Modifier.height(BitavidaSpacing.Large))
                    }
                    }
                    }
                }
            }
        }
    }
    state.statusChangeDraft?.let {
        StatusDialog(
            draft = it,
            errors = state.fieldErrors,
            saving = state.isSaving,
            onChange = { draft ->
                onAction(MedicalPlanAction.StatusChangeDraftChanged(draft))
            },
            onConfirm = { onAction(MedicalPlanAction.ConfirmStatusChange) },
            onDismiss = { onAction(MedicalPlanAction.DismissStatusChange) },
        )
    }
    state.medicationLifecycleDraft?.let { draft ->
        val onLifecycleChange: (MedicationLifecycleDraft) -> Unit = {
            onAction(MedicalPlanAction.MedicationLifecycleDraftChanged(it))
        }
        val onLifecycleConfirm = {
            onAction(MedicalPlanAction.ConfirmMedicationLifecycle)
        }
        val onLifecycleDismiss = {
            onAction(MedicalPlanAction.DismissMedicationLifecycle)
        }
        val medication = state.selectedMedicationPlan
        when {
            medication != null &&
                draft.targetStatus == MedicationTreatmentStatus.ACTIVE -> {
                MedicationReactivationDialog(
                    draft = draft,
                    medication = medication,
                    indication = medication.medicalIndicationId?.let { id ->
                        state.overview.indications.singleOrNull {
                            it.id == id
                        }
                    },
                    errors = state.fieldErrors,
                    saving = state.isSaving,
                    motionAllowed = motionAllowed,
                    hasPendingReactivation = medication.pendingReactivation(
                        LocalDateTime.now().atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli(),
                    ) != null,
                    onChange = onLifecycleChange,
                    onConfirm = onLifecycleConfirm,
                    onDismiss = onLifecycleDismiss,
                    onCorrectData = {
                        onAction(
                            MedicalPlanAction.OpenCorrectMedicationFromReactivation,
                        )
                    },
                    onChangeIndication = {
                        onAction(
                            MedicalPlanAction.OpenUpdateMedicationIndicationFromReactivation,
                        )
                    },
                    onCancelScheduled = {
                        onAction(
                            MedicalPlanAction.CancelScheduledMedicationReactivation,
                        )
                    },
                )
            }
            medication != null &&
                draft.targetStatus in setOf(
                    MedicationTreatmentStatus.SUSPENDED,
                    MedicationTreatmentStatus.REMOVED,
                ) -> {
                MedicationLifecycleConfirmationSheet(
                    draft = draft,
                    medication = medication,
                    errors = state.fieldErrors,
                    saving = state.isSaving,
                    motionAllowed = motionAllowed,
                    onChange = onLifecycleChange,
                    onConfirm = onLifecycleConfirm,
                    onDismiss = onLifecycleDismiss,
                )
            }
            else -> {
                MedicationLifecycleDialog(
                    draft = draft,
                    errors = state.fieldErrors,
                    saving = state.isSaving,
                    motionAllowed = motionAllowed,
                    onChange = onLifecycleChange,
                    onConfirm = onLifecycleConfirm,
                    onDismiss = onLifecycleDismiss,
                )
            }
        }
    }
    state.medicationIntakeDraft?.takeUnless { it.inlineEntry }?.let { draft ->
        state.selectedMedicationPlan?.let { medication ->
            MedicationIntakeSheet(
                draft = draft,
                medication = medication,
                errors = state.fieldErrors,
                saving = state.isSaving,
                motionAllowed = motionAllowed,
                onChange = {
                    onAction(MedicalPlanAction.MedicationIntakeDraftChanged(it))
                },
                onConfirm = {
                    onAction(MedicalPlanAction.SaveMedicationIntake)
                },
                onDismiss = {
                    onAction(MedicalPlanAction.DismissMedicationIntake)
                },
            )
        }
    }
    if (state.medicationExitConfirmationVisible) {
        MedicationRegistrationExitSheet(
            editingExisting = state.editingExisting,
            motionAllowed = motionAllowed,
            onDiscard = {
                onAction(MedicalPlanAction.DiscardMedicationRegistration)
            },
            onContinueEditing = {
                onAction(
                    MedicalPlanAction.ContinueEditingMedicationRegistration,
                )
            },
        )
    }
    MedicationSensitiveConsentDialog(state, onAction)
}

@Composable
private fun Header(
    screen: MedicalPlanScreen,
    section: PlanSection?,
    person: String,
    motionAllowed: Boolean,
    onBack: () -> Unit,
    onContinue: (() -> Unit)?,
    onContinueLabel: String = "Continuar",
    onContinueEnabled: Boolean = true,
    titleOverride: String? = null,
    compactMedicationHeader: Boolean = false,
    medicationHistoryName: String? = null,
    onBackEnabled: Boolean = true,
) {
    if (compactMedicationHeader) {
        CurrentMedicationBack(onBack, motionAllowed, enabled = onBackEnabled)
        return
    }
    medicationHistoryName?.let { name ->
        CurrentMedicationBack(
            onBack = onBack,
            motionAllowed = motionAllowed,
            enabled = onBackEnabled,
            label = name,
        )
        return
    }
    val focusManager = LocalFocusManager.current
    var iconEntered by remember(screen, section, motionAllowed) {
        mutableStateOf(!motionAllowed)
    }
    LaunchedEffect(screen, section, motionAllowed) {
        iconEntered = true
    }
    val iconScale by animateFloatAsState(
        targetValue = if (iconEntered) 1f else 0.9f,
        animationSpec = tween(
            durationMillis =
                if (motionAllowed) BitavidaFluidMotion.StateTransition else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "plan_header_icon_scale",
    )

    if (screen == MedicalPlanScreen.MEDICATION_REGISTRATION) {
        GuidedRegistrationHeader(
            title = titleOverride ?: headerTitle(screen, section),
            personLabel = "Acompañando a ${person.firstName()}",
            icon = painterResource(headerIcon(screen, section)),
            iconScale = iconScale,
            focusManager = focusManager,
            onBack = onBack,
            onContinue = onContinue,
            continueLabel = onContinueLabel,
            continueEnabled = onContinueEnabled,
            backEnabled = onBackEnabled,
        )
        return
    }

    if (screen == MedicalPlanScreen.SECTION && section == PlanSection.MEDICATIONS) {
        CareSectionHeader(
            title = titleOverride ?: headerTitle(screen, section),
            person = person,
            iconRes = headerIcon(screen, section),
            onBack = onBack,
            motionAllowed = motionAllowed,
            backEnabled = onBackEnabled,
            iconScale = iconScale,
        )
        return
    }

    if (screen != MedicalPlanScreen.OVERVIEW) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) {
                Text("Atrás")
            }
            onContinue?.let { continueAction ->
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        continueAction()
                    },
                    enabled = onContinueEnabled,
                    modifier = Modifier.heightIn(
                        min = BitavidaSpacing.MinimumTouchTarget,
                    ),
                ) {
                    Text(onContinueLabel)
                    Image(
                        painter = painterResource(R.drawable.ic_v2_chevron),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        colorFilter = ColorFilter.tint(
                            MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BitavidaIconContainer(
            painter = painterResource(headerIcon(screen, section)),
            contentDescription = null,
            containerColor = MaterialTheme.bitavidaColors.paleBlue,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
                alpha = iconScale
            },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
        ) {
            Text(
                text = titleOverride ?: headerTitle(screen, section),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when (screen) {
                    MedicalPlanScreen.OVERVIEW ->
                        "Organiza las indicaciones y actividades de cuidado."
                    MedicalPlanScreen.SECTION ->
                        section?.description().orEmpty()
                    else -> "Acompañando a ${person.firstName()}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (
                screen == MedicalPlanScreen.OVERVIEW ||
                screen == MedicalPlanScreen.SECTION
            ) {
                Text(
                    "Acompañando a ${person.firstName()}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun Overview(
    onSectionSelected: (PlanSection) -> Unit,
    returningSection: PlanSection?,
    threadAlreadyRevealed: Boolean,
    onThreadRevealed: () -> Unit,
    initialThreadPosition: Float,
    onThreadPositionSaved: (Float) -> Unit,
    motionAllowed: Boolean,
    activeReminderCount: Int?,
    activeMedicationCount: Int?,
    activeMeasurementCount: Int?,
    activeObservationCount: Int?,
) {
    val sections = remember {
        listOf(
            PlanSection.MEDICATIONS,
            PlanSection.MEASUREMENTS,
            PlanSection.OBSERVATIONS,
            PlanSection.REMINDERS,
            PlanSection.OTHER_INDICATIONS,
        )
    }
    var entered by remember(motionAllowed) { mutableStateOf(!motionAllowed) }
    var pendingSection by remember { mutableStateOf<PlanSection?>(null) }
    var returningPulseSection by remember(returningSection) {
        mutableStateOf(returningSection)
    }
    val anchors = remember { PlanThreadAnchors() }
    val overviewWidthPx = anchors.widthPx
    val revealProgress = remember {
        Animatable(if (threadAlreadyRevealed || !motionAllowed) 1.25f else 0f)
    }
    val pointProgress = remember {
        Animatable(initialThreadPosition.coerceIn(0f, 1f))
    }
    var pointDirection by remember {
        mutableIntStateOf(if (initialThreadPosition < 0.5f) 1 else -1)
    }
    val selectionProgress = remember { Animatable(0f) }
    val returnProgress = remember { Animatable(0f) }
    val anchorSnapshot = anchors.bounds
    val anchorsReady = sections.all(anchorSnapshot::containsKey)
    val curveAmplitudePx = with(LocalDensity.current) { 22.dp.toPx() }
    val threadGeometry = remember(sections, anchorSnapshot, curveAmplitudePx) {
        buildPlanThreadGeometry(
            sections = sections,
            boundsInContent = anchorSnapshot,
            curveAmplitudePx = curveAmplitudePx,
        )
    }

    DisposableEffect(pointProgress) {
        onDispose {
            onThreadPositionSaved(pointProgress.value.coerceIn(0f, 1f))
        }
    }
    LaunchedEffect(motionAllowed) {
        entered = true
    }
    LaunchedEffect(anchorsReady, motionAllowed) {
        if (!anchorsReady) return@LaunchedEffect
        if (threadAlreadyRevealed || !motionAllowed) {
            revealProgress.snapTo(1.25f)
        } else {
            onThreadRevealed()
            revealProgress.animateTo(
                targetValue = 1.25f,
                animationSpec = tween(
                    durationMillis = BitavidaFluidMotion.ThreadReveal + 80,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    LaunchedEffect(returningSection, threadGeometry, motionAllowed) {
        val target = returningSection?.let { threadGeometry?.stationProgress?.get(it) }
        if (returningSection == null || target == null) return@LaunchedEffect
        pointProgress.snapTo(target)
        returnProgress.snapTo(0f)
        if (motionAllowed) {
            returnProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = BitavidaFluidMotion.Selection,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            returnProgress.snapTo(1f)
        }
        returningPulseSection = null
    }
    LaunchedEffect(
        threadGeometry,
        overviewWidthPx,
        motionAllowed,
        pendingSection,
        returningPulseSection,
    ) {
        if (
            !motionAllowed ||
            threadGeometry == null ||
            overviewWidthPx <= 0f ||
            pendingSection != null ||
            returningPulseSection != null
        ) {
            return@LaunchedEffect
        }
        if (!threadAlreadyRevealed) {
            delay((BitavidaFluidMotion.ThreadReveal / 2).milliseconds)
        }
        animatePlanCareThreadPoint(
            progress = pointProgress,
            pathLength = { threadGeometry.totalLength },
            referenceWidth = { overviewWidthPx },
            onDirection = { pointDirection = it },
        )
    }

    val activeSection = pendingSection ?: returningPulseSection
    val activeProgress = when {
        pendingSection != null -> selectionProgress.value
        returningPulseSection != null -> returnProgress.value
        else -> 0f
    }
    LaunchedEffect(pendingSection, threadGeometry, motionAllowed) {
        val section = pendingSection ?: return@LaunchedEffect
        val target = threadGeometry?.stationProgress?.get(section) ?: return@LaunchedEffect
        selectionProgress.snapTo(0f)
        if (motionAllowed) {
            val travelDistance = abs(target - pointProgress.value)
            val directedDuration =
                (180f + travelDistance * 220f).toInt()
                    .coerceIn(180, 400)
            pointDirection = if (target >= pointProgress.value) 1 else -1
            coroutineScope {
                launch {
                    selectionProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = directedDuration,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                pointProgress.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = directedDuration,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        } else {
            pointProgress.snapTo(target)
            selectionProgress.snapTo(1f)
        }
        onThreadPositionSaved(pointProgress.value.coerceIn(0f, 1f))
        onSectionSelected(section)
    }
    val selectSection: (PlanSection) -> Unit = { section ->
        if (pendingSection == null) pendingSection = section
    }
    val stationPulse: (PlanSection) -> Float = { section ->
        val index = sections.indexOf(section)
        (1f - abs(revealProgress.value * 4f - index)).coerceIn(0f, 1f)
    }
    val pointPosition = { pointProgress.value }
    val flowDirection = { pointDirection }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        PlanEntrance(visible = entered, order = 0, motionAllowed = motionAllowed) {
            Text(
                "Áreas de tu plan",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned(anchors::onContentPositioned),
        ) {
            PlanStationThread(
                geometry = threadGeometry,
                revealProgress = revealProgress.value.coerceIn(0f, 1f),
                activeSection = activeSection,
                activeProgress = activeProgress,
                pointProgress = pointPosition,
                pointDirection = flowDirection,
                motionAllowed = motionAllowed,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                PlanEntrance(visible = entered, order = 1, motionAllowed = motionAllowed) {
                    FeaturedPlanStation(
                        section = PlanSection.MEDICATIONS,
                        activeCount = activeMedicationCount,
                        onSelected = selectSection,
                        motionAllowed = motionAllowed,
                        activeSection = activeSection,
                        activeProgress = activeProgress,
                        arrivalPulse = stationPulse(PlanSection.MEDICATIONS),
                        stationProgress =
                            threadGeometry?.stationProgress?.get(PlanSection.MEDICATIONS) ?: 0f,
                        pointProgress = pointPosition,
                        onAnchorPositioned = anchors::onIconPositioned,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PlanEntrance(visible = entered, order = 2, motionAllowed = motionAllowed) {
                    FeaturedPlanStation(
                        section = PlanSection.MEASUREMENTS,
                        activeCount = activeMeasurementCount,
                        onSelected = selectSection,
                        motionAllowed = motionAllowed,
                        activeSection = activeSection,
                        activeProgress = activeProgress,
                        arrivalPulse = stationPulse(PlanSection.MEASUREMENTS),
                        stationProgress =
                            threadGeometry?.stationProgress?.get(PlanSection.MEASUREMENTS) ?: 0f,
                        pointProgress = pointPosition,
                        onAnchorPositioned = anchors::onIconPositioned,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                sections.drop(2).forEachIndexed { index, section ->
                    PlanEntrance(
                        visible = entered,
                        order = index + 3,
                        motionAllowed = motionAllowed,
                    ) {
                        CompactPlanStation(
                            section = section,
                            onSelected = selectSection,
                            motionAllowed = motionAllowed,
                            activeSection = activeSection,
                            activeProgress = activeProgress,
                            arrivalPulse = stationPulse(section),
                            stationProgress =
                                threadGeometry?.stationProgress?.get(section) ?: 0f,
                            pointProgress = pointPosition,
                            onAnchorPositioned = anchors::onIconPositioned,
                            activeCount = when (section) {
                                PlanSection.OBSERVATIONS -> activeObservationCount
                                PlanSection.REMINDERS -> activeReminderCount
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanEntrance(
    visible: Boolean,
    order: Int,
    motionAllowed: Boolean,
    content: @Composable () -> Unit,
) {
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    val delay = if (motionAllowed) order * 45 else 0
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth(),
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = duration,
                delayMillis = delay,
                easing = FastOutSlowInEasing,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = duration,
                delayMillis = delay,
                easing = FastOutSlowInEasing,
            ),
            initialOffsetY = { it / 10 },
        ),
        exit = fadeOut(tween(duration / 2)),
    ) {
        content()
    }
}

// Keep measured icon frames in the same scrolling content space as the drawing.
// Root bounds are viewport-clipped and collapse for icons outside the visible area.
private class PlanThreadAnchors {
    private var contentCoordinates: LayoutCoordinates? = null
    private val iconCoordinates = mutableMapOf<PlanSection, LayoutCoordinates>()
    var bounds by mutableStateOf<Map<PlanSection, Rect>>(emptyMap())
        private set
    var widthPx by mutableFloatStateOf(0f)
        private set

    fun onContentPositioned(coordinates: LayoutCoordinates) {
        contentCoordinates = coordinates
        widthPx = coordinates.size.width.toFloat()
        updateBounds()
    }

    fun onIconPositioned(section: PlanSection, coordinates: LayoutCoordinates) {
        iconCoordinates[section] = coordinates
        updateBounds()
    }

    private fun updateBounds() {
        val content = contentCoordinates?.takeIf { it.isAttached } ?: return
        bounds = iconCoordinates.filterValues { it.isAttached }.mapValues { (_, icon) ->
            content.localBoundingBoxOf(icon, clipBounds = false)
        }
    }
}

private data class PlanThreadGeometry(
    val sections: List<PlanSection>,
    val segments: List<Path>,
    val fullPath: Path,
    val totalLength: Float,
    val stationProgress: Map<PlanSection, Float>,
)

private fun buildPlanThreadGeometry(
    sections: List<PlanSection>,
    boundsInContent: Map<PlanSection, Rect>,
    curveAmplitudePx: Float,
): PlanThreadGeometry? {
    if (!sections.all(boundsInContent::containsKey)) return null
    val bounds = sections.map(boundsInContent::getValue)
    val fullPath = Path()
    val segments = mutableListOf<Path>()
    val stationDistances = mutableMapOf<PlanSection, Float>()
    val connector = PlanCareThreadCurve()
    var traveled = 0f
    fullPath.moveTo(bounds.first().center.x, bounds.first().center.y)
    stationDistances[sections.first()] = 0f

    bounds.zipWithNext().forEachIndexed { index, (from, to) ->
        val fromEdge = Offset(from.center.x, from.bottom)
        val toEdge = Offset(to.center.x, to.top)
        connector.update(fromEdge.x, fromEdge.y, toEdge.x, toEdge.y, curveAmplitudePx, index)
        val segment = Path().apply {
            moveTo(fromEdge.x, fromEdge.y)
            connector.appendTo(this)
        }
        val segmentMeasure = PathMeasure().apply { setPath(segment, forceClosed = false) }
        segments += segment
        fullPath.lineTo(fromEdge.x, fromEdge.y)
        traveled += from.height / 2f
        connector.appendTo(fullPath)
        traveled += segmentMeasure.length
        fullPath.lineTo(to.center.x, to.center.y)
        traveled += to.height / 2f
        stationDistances[sections[index + 1]] = traveled
    }
    if (traveled <= 0f) return null
    return PlanThreadGeometry(
        sections = sections,
        segments = segments,
        fullPath = fullPath,
        totalLength = traveled,
        stationProgress = stationDistances.mapValues { (_, distance) ->
            (distance / traveled).coerceIn(0f, 1f)
        },
    )
}

@Composable
private fun PlanStationThread(
    geometry: PlanThreadGeometry?,
    revealProgress: Float,
    activeSection: PlanSection?,
    activeProgress: Float,
    pointProgress: () -> Float,
    pointDirection: () -> Int,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.bitavidaColors
    val muted = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .clipToBounds()
            .drawWithCache {
                val currentGeometry = geometry
                if (currentGeometry == null) {
                    onDrawBehind { }
                } else {
                    val painter = PlanCareThreadPainter(density).apply { setPath(currentGeometry.fullPath) }
                    onDrawBehind {
                        painter.drawBase(this, revealProgress, muted, colors.gradientStart, currentGeometry.totalLength)
                        val activeIndex = currentGeometry.sections.indexOf(activeSection)
                        if (activeIndex >= 0 && currentGeometry.segments.isNotEmpty()) {
                            val index = (activeIndex - 1).coerceIn(0, currentGeometry.segments.lastIndex)
                            painter.drawSelection(this, currentGeometry.segments[index], activeProgress, colors.gradientEnd)
                        }
                        if (motionAllowed && revealProgress > 0.05f) {
                            painter.drawFlow(
                                this, pointProgress(), pointDirection(), currentGeometry.sections.size,
                                colors.gradientStart, colors.gradientEnd, length = currentGeometry.totalLength,
                            )
                        }
                    }
                }
            },
    )
}

private val PlanStationCardInset = 24.dp
private val PlanStationTextInset = 30.dp
private val PlanStationCompactTextInset = 38.dp

@Composable
private fun PlanStationTitle(section: PlanSection, activeCount: Int?, featured: Boolean) {
    val style = if (featured) MaterialTheme.typography.titleMedium
        else MaterialTheme.typography.titleSmall
    if (activeCount == null) {
        Text(section.title(), style = style, color = MaterialTheme.colorScheme.onSurface)
        return
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 230.dp || fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                Text(section.title(), style = style, color = MaterialTheme.colorScheme.onSurface)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    PlanActiveCountChip(section, activeCount)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    section.title(), Modifier.weight(1f), style = style,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PlanActiveCountChip(section, activeCount)
            }
        }
    }
}

@Composable
private fun PlanActiveCountChip(section: PlanSection, count: Int) {
    val tag = when (section) {
        PlanSection.MEDICATIONS -> "plan_medication_active_count"
        PlanSection.MEASUREMENTS -> "plan_measurement_active_count"
        PlanSection.OBSERVATIONS -> "plan_observation_active_count"
        PlanSection.REMINDERS -> "plan_reminder_active_count"
        else -> return
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            "$count " + if (count == 1) "activo" else "activos",
            modifier = Modifier.testTag(tag)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(
                    horizontal = BitavidaSpacing.Small,
                    vertical = BitavidaSpacing.ExtraSmall,
                ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeaturedPlanStation(
    section: PlanSection,
    onSelected: (PlanSection) -> Unit,
    motionAllowed: Boolean,
    activeSection: PlanSection?,
    activeProgress: Float,
    arrivalPulse: Float,
    stationProgress: Float,
    pointProgress: () -> Float,
    onAnchorPositioned: (PlanSection, LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    activeCount: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val response = Modifier.bitavidaFluidInteraction(
        source = interactionSource,
        enabled = true,
        motionAllowed = motionAllowed,
    )
    val actionPulse = sectionActionPulse(
        section = section,
        activeSection = activeSection,
        activeProgress = activeProgress,
        motionAllowed = motionAllowed,
    )
    val cardSurfaceAlpha =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.95f else 0.93f

    Box(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onSelected(section) },
            ),
    ) {
        BitavidaConnectedSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PlanStationCardInset)
                .then(response)
                .heightIn(min = 88.dp),
            containerColor = (
                when (section) {
                    PlanSection.MEDICATIONS -> MaterialTheme.bitavidaColors.mint
                    else -> MaterialTheme.bitavidaColors.paleBlue
                }
                ).copy(alpha = cardSurfaceAlpha),
            contentModifier = Modifier.padding(
                start = PlanStationTextInset,
                end = BitavidaSpacing.ExtraSmall,
            ),
        ) {
            PlanStationTitle(section, activeCount, featured = true)
            Spacer(Modifier.height(BitavidaSpacing.ExtraSmall))
            Text(
                section.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlanStationIcon(
            section = section,
            pressed = pressed,
            actionPulse = actionPulse,
            arrivalPulse = arrivalPulse,
            stationProgress = stationProgress,
            pointProgress = pointProgress,
            motionAllowed = motionAllowed,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            onAnchorPositioned = onAnchorPositioned,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = stationIconOffset(section)),
        )
    }
}

@Composable
private fun CompactPlanStation(
    section: PlanSection,
    onSelected: (PlanSection) -> Unit,
    motionAllowed: Boolean,
    activeSection: PlanSection?,
    activeProgress: Float,
    arrivalPulse: Float,
    stationProgress: Float,
    pointProgress: () -> Float,
    onAnchorPositioned: (PlanSection, LayoutCoordinates) -> Unit,
    activeCount: Int?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val response = Modifier.bitavidaFluidInteraction(
        source = interactionSource,
        enabled = true,
        motionAllowed = motionAllowed,
        restingElevationDp = 0f,
    )
    val actionPulse = sectionActionPulse(
        section = section,
        activeSection = activeSection,
        activeProgress = activeProgress,
        motionAllowed = motionAllowed,
    )
    val cardSurfaceAlpha =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.95f else 0.93f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onSelected(section) },
            ),
    ) {
        BitavidaConnectedSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PlanStationCardInset)
                .then(response)
                .heightIn(min = 76.dp),
            containerColor =
                MaterialTheme.colorScheme.surface.copy(alpha = cardSurfaceAlpha),
            contentModifier = Modifier.padding(
                start = PlanStationCompactTextInset,
                end = BitavidaSpacing.ExtraSmall,
                top = BitavidaSpacing.ExtraSmall,
                bottom = BitavidaSpacing.ExtraSmall,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                ) {
                    PlanStationTitle(section, activeCount, featured = false)
                    Text(
                        section.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        PlanStationIcon(
            section = section,
            pressed = pressed,
            actionPulse = actionPulse,
            arrivalPulse = arrivalPulse,
            stationProgress = stationProgress,
            pointProgress = pointProgress,
            motionAllowed = motionAllowed,
            containerColor = MaterialTheme.bitavidaColors.paleBlue,
            onAnchorPositioned = onAnchorPositioned,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = stationIconOffset(section)),
        )
    }
}

@Composable
private fun PlanStationIcon(
    section: PlanSection,
    pressed: Boolean,
    actionPulse: Float,
    arrivalPulse: Float,
    stationProgress: Float,
    pointProgress: () -> Float,
    motionAllowed: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    onAnchorPositioned: (PlanSection, LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed && motionAllowed) 0.96f else 1f,
        animationSpec = tween(
            durationMillis = if (motionAllowed) BitavidaFluidMotion.Press else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "plan_station_icon_press",
    )
    val colors = MaterialTheme.bitavidaColors
    val iconColor = MaterialTheme.colorScheme.primary
    val activeIconColor =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            colors.gradientEnd
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val passage = {
        if (motionAllowed) {
            planStationPassage(pointProgress(), stationProgress)
        } else {
            0f
        }
    }

    // Measure an unscaled frame so passage/press feedback cannot move the anchors.
    Box(
        modifier = modifier
            .size(44.dp)
            .onGloballyPositioned { coordinates ->
                onAnchorPositioned(section, coordinates)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pulse = maxOf(actionPulse, arrivalPulse)
                    val scale = pressedScale * (1f + pulse * 0.035f)
                    scaleX = scale
                    scaleY = scale
                }
                .drawWithCache {
                    val cornerRadius = CornerRadius(10.dp.toPx())
                    onDrawBehind {
                        drawRoundRect(
                            color = containerColor,
                            cornerRadius = cornerRadius,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val symbolScale = 1f + passage() * 0.075f
                        scaleX = symbolScale
                        scaleY = symbolScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(section.iconRes()),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(iconColor),
                )
                Image(
                    painter = painterResource(section.iconRes()),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.30f * passage()
                        },
                    colorFilter = ColorFilter.tint(activeIconColor),
                )
            }
        }
    }
}

private fun planStationPassage(
    pointProgress: Float,
    stationProgress: Float,
): Float {
    val proximity =
        (1f - abs(pointProgress - stationProgress) / 0.055f)
            .coerceIn(0f, 1f)
    return proximity * proximity * (3f - 2f * proximity)
}

private fun sectionActionPulse(
    section: PlanSection,
    activeSection: PlanSection?,
    activeProgress: Float,
    motionAllowed: Boolean,
): Float =
    if (activeSection == section && motionAllowed) {
        1f - abs(activeProgress * 2f - 1f)
    } else {
        0f
    }

private fun stationIconOffset(section: PlanSection) = when (section) {
    PlanSection.MEDICATIONS -> 0.dp
    PlanSection.MEASUREMENTS -> 8.dp
    PlanSection.OBSERVATIONS -> 0.dp
    PlanSection.CARE_TASKS -> 6.dp
    PlanSection.REMINDERS -> 6.dp
    PlanSection.OTHER_INDICATIONS -> 2.dp
}

@Composable
private fun PlanSectionContent(
    state: MedicalPlanUiState,
    section: PlanSection?,
    motionAllowed: Boolean,
    reminderRuntimeState: MedicationReminderRuntimeState,
    onRequestReminderAccess: () -> Unit,
    onAction: (MedicalPlanAction) -> Unit,
    onMedicationHelp: () -> Unit = {},
) {
    val selectedSection = section ?: return
    when (selectedSection) {
        PlanSection.MEDICATIONS -> MedicationSection(
            state = state,
            motionAllowed = motionAllowed,
            reminderRuntimeState = reminderRuntimeState,
            onRequestReminderAccess = onRequestReminderAccess,
            onAction = onAction,
            onHelp = onMedicationHelp,
        )
        PlanSection.OTHER_INDICATIONS -> PlanSectionEmpty(selectedSection, motionAllowed)
        PlanSection.MEASUREMENTS,
        PlanSection.OBSERVATIONS,
        PlanSection.CARE_TASKS,
        PlanSection.REMINDERS,
        -> PlanSectionEmpty(selectedSection, motionAllowed)
    }
}

@Composable
private fun PlanSectionEmpty(
    section: PlanSection,
    motionAllowed: Boolean,
) {
    var entered by remember(section, motionAllowed) { mutableStateOf(!motionAllowed) }
    LaunchedEffect(section, motionAllowed) {
        entered = true
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        PlanEntrance(visible = entered, order = 0, motionAllowed = motionAllowed) {
            Text(
                section.emptyTitle(),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        PlanEntrance(visible = entered, order = 1, motionAllowed = motionAllowed) {
            Text(
                section.emptyBody(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun headerTitle(screen: MedicalPlanScreen, section: PlanSection?): String =
    when (screen) {
        MedicalPlanScreen.OVERVIEW -> "Plan de cuidado"
        MedicalPlanScreen.SECTION -> section?.title().orEmpty()
        MedicalPlanScreen.INDICATION_EDITOR -> "Transcribir indicación"
        MedicalPlanScreen.INDICATION_DETAIL -> "Detalle de indicación"
        MedicalPlanScreen.INDICATION_HISTORY -> "Historial de correcciones"
        MedicalPlanScreen.MEDICATION_EDITOR -> "Organizar medicamento"
        MedicalPlanScreen.MEDICATION_DETAIL -> "Detalle del medicamento"
        MedicalPlanScreen.MEDICATION_MANAGEMENT -> "Gestionar tratamiento"
        MedicalPlanScreen.MEDICATION_HISTORY -> "Historial del medicamento"
        MedicalPlanScreen.MEDICATION_REGISTRATION ->
            "Agregar o modificar medicamento"
    }

private fun headerIcon(screen: MedicalPlanScreen, section: PlanSection?): Int =
    when (screen) {
        MedicalPlanScreen.OVERVIEW -> R.drawable.ic_v2_clipboard
        MedicalPlanScreen.SECTION -> section?.iconRes() ?: R.drawable.ic_v2_clipboard
        MedicalPlanScreen.MEDICATION_EDITOR,
        MedicalPlanScreen.MEDICATION_DETAIL,
        MedicalPlanScreen.MEDICATION_MANAGEMENT,
        MedicalPlanScreen.MEDICATION_HISTORY,
        MedicalPlanScreen.MEDICATION_REGISTRATION,
        -> R.drawable.ic_v2_pill
        else -> R.drawable.ic_v2_file
    }

private fun PlanSection.title(): String = when (this) {
    PlanSection.MEDICATIONS -> "Medicamentos"
    PlanSection.MEASUREMENTS -> "Mediciones"
    PlanSection.OBSERVATIONS -> "Observaciones"
    PlanSection.CARE_TASKS -> "Tareas de cuidado"
    PlanSection.REMINDERS -> "Recordatorios"
    PlanSection.OTHER_INDICATIONS -> "Notas"
}

private fun PlanSection.description(): String = when (this) {
    PlanSection.MEDICATIONS -> "Identificación y organización de medicamentos."
    PlanSection.MEASUREMENTS -> "Glucosa, presión, peso y otros registros."
    PlanSection.OBSERVATIONS -> "Cambios y aspectos que necesitas vigilar."
    PlanSection.CARE_TASKS -> "Actividades e instrucciones de cuidado."
    PlanSection.REMINDERS -> "Pendientes del cuidado cotidiano."
    PlanSection.OTHER_INDICATIONS -> "Apuntes e imágenes del cuidado cotidiano."
}

private fun PlanSection.iconRes(): Int = when (this) {
    PlanSection.MEDICATIONS -> R.drawable.ic_v2_pill
    PlanSection.MEASUREMENTS -> R.drawable.ic_v2_droplet
    PlanSection.OBSERVATIONS -> R.drawable.ic_v2_stethoscope
    PlanSection.CARE_TASKS -> R.drawable.ic_v2_clipboard
    PlanSection.REMINDERS -> R.drawable.ic_v2_clock
    PlanSection.OTHER_INDICATIONS -> R.drawable.ic_v2_file
}

private fun PlanSection.emptyTitle(): String = when (this) {
    PlanSection.MEDICATIONS -> "Aún no hay medicamentos registrados."
    PlanSection.MEASUREMENTS -> "Aún no hay mediciones registradas."
    PlanSection.OBSERVATIONS -> "Aún no hay observaciones configuradas."
    PlanSection.CARE_TASKS -> "Aún no hay tareas registradas."
    PlanSection.REMINDERS -> "¿Algo que necesites recordar?"
    PlanSection.OTHER_INDICATIONS -> "Tus apuntes, en un solo lugar"
}

private fun PlanSection.emptyBody(): String = when (this) {
    PlanSection.MEDICATIONS ->
        "Agrega los medicamentos indicados para organizar su seguimiento."
    PlanSection.MEASUREMENTS ->
        "Aquí podrás consultar los registros de glucosa, presión y otros controles."
    PlanSection.OBSERVATIONS -> "Aquí encontrarás los aspectos que necesitas vigilar."
    PlanSection.CARE_TASKS -> "Aquí se organizarán las actividades de cuidado."
    PlanSection.REMINDERS -> "Organiza pendientes que te ayuden a cuidar."
    PlanSection.OTHER_INDICATIONS ->
        "Conserva textos e imágenes que te ayuden en el cuidado cotidiano."
}

@Composable
private fun IndicationCard(
    indication: MedicalIndication,
    medicationCount: Int,
    onClick: (() -> Unit)? = null,
) {
    val current = indication.currentRevision
    BitavidaConnectedSurface(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "INDICACIÓN MÉDICA TRANSCRITA",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            BitavidaStatusPill(
                current.status.label(),
                isPositive = current.status == MedicalIndicationStatus.ACTIVE,
            )
        }
        Spacer(Modifier.height(BitavidaSpacing.Small))
        Text(current.originalText, style = MaterialTheme.typography.titleMedium, maxLines = 4)
        Text(
            "${current.indicatedOn.display()} · $medicationCount medicamento(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IndicationEditor(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val d = state.indicationDraft
    Card("TRANSCRIPCIÓN MÉDICA", "Conserva la redacción original",
        "No resumas, interpretes ni corrijas médicamente el texto.") {
        Field(d.originalText, { onAction(MedicalPlanAction.IndicationDraftChanged(d.copy(originalText = it))) },
            "Indicación original ficticia", "Obligatoria · máximo 4,000 caracteres",
            MedicalCareField.ORIGINAL_INDICATION in state.fieldErrors, minLines = 4,
            limit = CaptureLimit.TRANSCRIPTION,
        )
        MedicalDateSelectionField(
            value = d.indicatedOn,
            label = "Fecha indicada por el cuidador",
            optional = false,
            isError = MedicalCareField.INDICATED_ON in state.fieldErrors,
            onValueChange = {
                onAction(MedicalPlanAction.IndicationDraftChanged(d.copy(indicatedOn = it)))
            },
        )
        Field(d.declaredProfessionalOrSource,
            { onAction(MedicalPlanAction.IndicationDraftChanged(d.copy(declaredProfessionalOrSource = it))) },
            "Profesional o fuente declarada (opcional)", "Bitavida no verifica esta declaración",
            MedicalCareField.DECLARED_SOURCE in state.fieldErrors,
            limit = CaptureLimit.SOURCE,
        )
    }
    Card("VIGENCIA DECLARADA", "Registra solo lo conocido", "Deja vacío lo que no se conozca.") {
        MedicalDateSelectionField(
            value = d.validFrom,
            label = "Vigente desde (opcional)",
            optional = true,
            isError = MedicalCareField.VALIDITY in state.fieldErrors,
            onValueChange = {
                onAction(MedicalPlanAction.IndicationDraftChanged(d.copy(validFrom = it)))
            },
        )
        MedicalDateSelectionField(
            value = d.validUntil,
            label = "Vigente hasta (opcional)",
            optional = true,
            isError = MedicalCareField.VALIDITY in state.fieldErrors,
            onValueChange = {
                onAction(MedicalPlanAction.IndicationDraftChanged(d.copy(validUntil = it)))
            },
        )
        if (state.editingExisting) {
            Field(d.changeReason,
                { onAction(MedicalPlanAction.IndicationDraftChanged(d.copy(changeReason = it))) },
                "Motivo de la corrección", "La versión anterior se conservará",
                MedicalCareField.CHANGE_REASON in state.fieldErrors, minLines = 2,
                limit = CaptureLimit.REASON,
            )
        }
    }
    FictionCheck(
        d.fictitiousDataAcknowledged,
        MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT in state.fieldErrors,
    ) { onAction(MedicalPlanAction.IndicationDraftChanged(d.copy(fictitiousDataAcknowledged = it))) }
    BitavidaThreadAction(
        label = when {
            state.isSaving -> "Guardando…"
            state.editingExisting -> "Guardar nueva revisión"
            else -> "Guardar indicación ficticia"
        },
        iconRes = R.drawable.ic_v2_plus,
        onClick = { onAction(MedicalPlanAction.SaveIndication) },
        modifier = Modifier.fillMaxWidth(),
        emphasized = true,
        enabled = !state.isSaving,
    )
}

@Composable
private fun IndicationDetail(
    state: MedicalPlanUiState,
    caregiver: String,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val indication = state.selectedIndication ?: return Missing()
    val r = indication.currentRevision
    Card("INDICACIÓN MÉDICA TRANSCRITA", r.originalText,
        "No validada por un profesional dentro de Bitavida.") {
        Line("Estado declarado", r.status.label())
        Line("Fecha indicada", r.indicatedOn.display())
        Line("Fuente declarada", r.declaredProfessionalOrSource ?: "No registrada")
        Line("Vigencia", range(r.validFrom, r.validUntil))
        Line("Registrado por", caregiver)
        Line("Captura real", r.capturedAtEpochMillis.displayInstant())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        TextButton(
            onClick = { onAction(MedicalPlanAction.OpenEditIndication) },
            modifier = Modifier.weight(1f).heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) { Text("Corregir") }
        TextButton(
            onClick = { onAction(MedicalPlanAction.OpenIndicationHistory) },
            modifier = Modifier.weight(1f).heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) { Text("Historial (${indication.revisions.size})") }
    }
    StatusActions(r.status) { onAction(MedicalPlanAction.RequestStatusChange(it)) }
    HorizontalDivider()
    Text("Medicamentos organizados", style = MaterialTheme.typography.titleLarge)
    val plans = state.overview.plansFor(indication.id)
    if (plans.isEmpty()) {
        BitavidaConnectedSurface {
            Text("No hay medicamentos ficticios asociados.", style = MaterialTheme.typography.bodyLarge)
            Text(
                "La organización manual no genera recordatorios.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        plans.forEach { plan ->
            MedicationCard(plan) { onAction(MedicalPlanAction.OpenMedication(plan.id)) }
        }
    }
    BitavidaThreadAction(
        "Organizar medicamento ficticio",
        R.drawable.ic_v2_pill,
        { onAction(MedicalPlanAction.OpenCreateMedication) },
        Modifier.fillMaxWidth(),
        emphasized = true,
    )
}

@Composable
private fun IndicationHistory(state: MedicalPlanUiState, caregiver: String) {
    val indication = state.selectedIndication ?: return Missing()
    Text(
        "Cada versión se conserva sin sobrescribir la anterior.",
        style = MaterialTheme.typography.bodyLarge,
    )
    indication.revisions.asReversed().forEach { r ->
        BitavidaConnectedSurface {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "VERSIÓN ${r.revisionNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                BitavidaStatusPill(
                    r.status.label(),
                    isPositive = r.status == MedicalIndicationStatus.ACTIVE,
                )
            }
            Spacer(Modifier.height(BitavidaSpacing.Small))
            Text(r.originalText, style = MaterialTheme.typography.bodyLarge)
            r.changeReason?.let { Line("Motivo", it) }
            Line("Autor", caregiver)
            Line("Capturada", r.capturedAtEpochMillis.displayInstant())
        }
    }
}

@Composable
private fun MedicationCard(
    plan: MedicationPlan,
    onClick: (() -> Unit)? = null,
) {
    val revision = plan.currentRevision
    val configuration = revision.configuration
    BitavidaConnectedSurface(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.bitavidaColors.mint,
        onClick = onClick,
    ) {
        Text(
            "ORGANIZACIÓN DEL CUIDADOR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (configuration != null) {
                MedicationProductIcon(
                    form = configuration.form,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_v2_pill),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    colorFilter = ColorFilter.tint(
                        MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    revision.medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    configuration?.form?.displayName()
                        ?: revision.unitOrPresentation
                            .medicationPresentationDisplayName()
                        ?: revision.doseAsIndicated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            configuration?.frequencySummary()
                ?: revision.frequencyAsIndicated
                ?: revision.scheduledDoses.joinToString(" · ") {
                    it.time.toString()
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MedicationEditor(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val d = state.medicationDraft
    val original = state.selectedMedicationPlan?.currentRevision?.configuration
    fun change(next: MedicationDraft) =
        onAction(MedicalPlanAction.MedicationDraftChanged(next))
    if (state.editingExisting) {
        Card(
            "DATOS DEL MEDICAMENTO",
            "Editar sin cambiar la programación",
            "Las indicaciones y horarios se gestionan por separado.",
        ) {
            Field(
                d.medicationName,
                { change(d.copy(medicationName = it)) },
                "Nombre del medicamento",
                "No se autocompleta dosis ni horario",
                MedicalCareField.MEDICATION_NAME in state.fieldErrors,
                limit = CaptureLimit.MEDICATION_NAME,
            )
            Text(
                "Forma o presentación",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column {
                MedicationForm.entries.forEach { form ->
                    TextButton(
                        onClick = { change(d.copy(form = form)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min =
                                    BitavidaSpacing.MinimumTouchTarget,
                            ),
                    ) {
                        Text(
                            if (d.form == form) {
                                "✓ ${form.editorLabel()}"
                            } else {
                                form.editorLabel()
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                MedicationValueField(
                    value = d.concentrationValue,
                    onValueChange = { change(d.copy(concentrationValue = it)) },
                    label = "Concentración (opcional)",
                    isError = MedicalCareField.DOSE_AS_INDICATED in state.fieldErrors &&
                        d.concentrationValue.isNotEmpty() &&
                        !MedicationValueRules.isValidReplacement(
                            d.concentrationValue, original?.concentrationValue,
                        ),
                    modifier = Modifier.weight(1f),
                )
                Field(
                    d.concentrationUnit,
                    { change(d.copy(concentrationUnit = it)) },
                    "Unidad",
                    "Ej. mg",
                    false,
                    modifier = Modifier.weight(1f),
                    limit = CaptureLimit.UNIT,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                MedicationValueField(
                    value = d.baseAmountValue,
                    onValueChange = { change(d.copy(baseAmountValue = it)) },
                    label = "Cantidad base",
                    isError = MedicalCareField.DOSE_AS_INDICATED in state.fieldErrors &&
                        !MedicationValueRules.isValidReplacement(
                            d.baseAmountValue, original?.baseAmount?.value,
                        ),
                    modifier = Modifier.weight(1f),
                )
                Field(
                    d.baseAmountUnit,
                    { change(d.copy(baseAmountUnit = it)) },
                    "Unidad",
                    "Ej. tableta",
                    MedicalCareField.UNIT_OR_PRESENTATION in
                        state.fieldErrors,
                    modifier = Modifier.weight(1f),
                    limit = CaptureLimit.UNIT,
                )
            }
            Field(
                d.originalAdditionalInstructions,
                {
                    change(
                        d.copy(originalAdditionalInstructions = it),
                    )
                },
                "Notas o restricciones",
                "Transcribe solo lo registrado",
                MedicalCareField.ADDITIONAL_INSTRUCTIONS in
                    state.fieldErrors,
                minLines = 3,
                limit = CaptureLimit.ADDITIONAL_INSTRUCTIONS,
            )
            Field(
                d.changeReason,
                { change(d.copy(changeReason = it)) },
                "Motivo de la actualización",
                "La versión anterior se conservará",
                MedicalCareField.CHANGE_REASON in state.fieldErrors,
                minLines = 2,
                limit = CaptureLimit.REASON,
            )
        }
    } else {
        Card(
            "TRANSCRIPCIÓN MÉDICA",
            "Copia los datos indicados",
            "Bitavida no completa cantidades, unidades ni frecuencia.",
        ) {
            Field(
                d.medicationName,
                { change(d.copy(medicationName = it)) },
                "Nombre ficticio del medicamento",
                "Escríbelo manualmente",
                MedicalCareField.MEDICATION_NAME in state.fieldErrors,
                limit = CaptureLimit.MEDICATION_NAME,
            )
            Field(
                d.doseAsIndicated,
                { change(d.copy(doseAsIndicated = it)) },
                "Dosis tal como fue indicada",
                "No se calcula ni sugiere",
                MedicalCareField.DOSE_AS_INDICATED in state.fieldErrors,
                limit = CaptureLimit.TRANSCRIBED_DOSE,
            )
            Field(
                d.unitOrPresentation,
                { change(d.copy(unitOrPresentation = it)) },
                "Unidad o presentación",
                "Solo si se conoce",
                MedicalCareField.UNIT_OR_PRESENTATION in
                    state.fieldErrors,
                limit = CaptureLimit.SHORT_LABEL,
            )
            MedicationScheduleSelectionField(
                value = d.explicitSchedules,
                isError = MedicalCareField.EXPLICIT_SCHEDULES in state.fieldErrors,
                onValueChange = { change(d.copy(explicitSchedules = it)) },
            )
            MedicalDateSelectionField(
                value = d.startsOn,
                label = "Fecha de inicio",
                optional = false,
                isError = MedicalCareField.MEDICATION_DATES in state.fieldErrors,
                onValueChange = { change(d.copy(startsOn = it)) },
            )
        }
        FictionCheck(
            d.fictitiousDataAcknowledged,
            MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT in
                state.fieldErrors,
        ) {
            change(d.copy(fictitiousDataAcknowledged = it))
        }
    }
    BitavidaThreadAction(
        label = when {
            state.isSaving -> "Guardando…"
            state.editingExisting -> "Guardar nueva revisión"
            else -> "Guardar organización ficticia"
        },
        iconRes = R.drawable.ic_v2_pill,
        onClick = { onAction(MedicalPlanAction.SaveMedication) },
        modifier = Modifier.fillMaxWidth(),
        emphasized = true,
        enabled = !state.isSaving,
    )
}

@Composable
private fun MedicationDetail(
    state: MedicalPlanUiState,
    caregiver: String,
    reminderRuntimeState: MedicationReminderRuntimeState,
    onRequestReminderAccess: () -> Unit,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    // Retain the outgoing identity until AnimatedContent finishes the return transition.
    var outgoingPlan by remember { mutableStateOf(state.selectedMedicationPlan) }
    SideEffect { state.selectedMedicationPlan?.let { outgoingPlan = it } }
    val plan = state.selectedMedicationPlan ?: outgoingPlan ?: return Missing()
    val revision = plan.currentRevision
    val configuration = revision.configuration
    var programmingExpanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    var historicalInformationExpanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    var entered by remember(plan.id, motionAllowed) { mutableStateOf(!motionAllowed) }
    var now by remember(plan.id) { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(plan.id, motionAllowed) { entered = true }
    LaunchedEffect(plan.id, revision.revisionNumber) {
        while (isActive) {
            val currentEpochMillis = System.currentTimeMillis()
            now = Instant.ofEpochMilli(currentEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            val nextTransitionDelay = plan.lifecycleTransitions()
                .asSequence()
                .map { it.effectiveAtEpochMillis }
                .filter { it > currentEpochMillis }
                .minOrNull()
                ?.minus(currentEpochMillis)
            delay(
                nextTransitionDelay
                    ?.coerceIn(250L, 15_000L)
                    ?: 15_000L,
            )
        }
    }
    val indicationStatus = plan.medicalIndicationId?.let { id ->
        state.overview.indications.singleOrNull { it.id == id }?.currentRevision?.status
    }
    val displayState = plan.displayState(now, indicationStatus)
    if (displayState == MedicationPlanDisplayState.REMOVED) return Missing()
    val renderedDisplayState = remember(plan.id) {
        arrayOf(displayState)
    }
    val stateChanged = renderedDisplayState[0] != displayState
    val stateTransitionProgress = remember(plan.id, displayState) {
        Animatable(
            if (motionAllowed && stateChanged) 0f else 1f,
        )
    }
    SideEffect {
        renderedDisplayState[0] = displayState
    }
    LaunchedEffect(
        plan.id,
        displayState,
        motionAllowed,
        stateTransitionProgress,
    ) {
        if (!motionAllowed || !stateChanged) {
            stateTransitionProgress.snapTo(1f)
        } else {
            stateTransitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 275,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    val stateTransitionOffsetPx = with(LocalDensity.current) {
        10.dp.toPx()
    }
    val stateTransitionModifier = Modifier.graphicsLayer {
        val progress = stateTransitionProgress.value
        alpha = progress
        translationY = (1f - progress) * stateTransitionOffsetPx
    }
    val records = state.overview.intakesFor(plan.id)
    if (
        displayState in setOf(
            MedicationPlanDisplayState.CURRENT,
            MedicationPlanDisplayState.UPCOMING,
            MedicationPlanDisplayState.SUSPENDED,
            MedicationPlanDisplayState.COMPLETED,
        )
    ) {
        key(plan.id, revision.id, displayState) {
            when (displayState) {
                MedicationPlanDisplayState.CURRENT -> CurrentMedicationDetail(
                    state, plan, now, reminderRuntimeState, motionAllowed,
                    stateTransitionModifier, onRequestReminderAccess, onAction,
                )
                MedicationPlanDisplayState.UPCOMING -> UpcomingMedicationDetail(
                    state, plan, now, reminderRuntimeState, motionAllowed,
                    stateTransitionModifier, onRequestReminderAccess, onAction,
                )
                MedicationPlanDisplayState.SUSPENDED -> SuspendedMedicationDetail(
                    state, plan, now, reminderRuntimeState, motionAllowed,
                    stateTransitionModifier, onRequestReminderAccess, onAction,
                )
                MedicationPlanDisplayState.COMPLETED -> CompletedMedicationDetail(
                    state, plan, now, reminderRuntimeState, motionAllowed,
                    stateTransitionModifier, onRequestReminderAccess, onAction,
                )
                else -> Unit
            }
        }
        return
    }
    // The projector remains the only authority for scheduled occurrences.
    val recentOccurrences = projectMedicationAdministrations(
        plans = listOf(plan),
        rangeStart = now.toLocalDate().minusDays(1).atStartOfDay(),
        rangeEnd = now,
        limit = 512,
    )
    val justSaved = records.singleOrNull { it.id == state.lastQuickMedicationIntakeId }
    val occurrence = recentOccurrences.firstOrNull {
        it.startsAt == justSaved?.currentRevision?.scheduledAt
    } ?: recentOccurrences.lastOrNull {
        it.endsAt?.let { end -> !end.isBefore(now) } == true
    } ?: recentOccurrences.lastOrNull { scheduled ->
        scheduled.startsAt.toLocalDate() == now.toLocalDate() &&
            records.none { it.currentRevision.scheduledAt == scheduled.startsAt }
    }
        ?: projectMedicationAdministrations(
            plans = listOf(plan),
            rangeStart = now,
            rangeEnd = now.plusDays(90),
            limit = 1,
        ).firstOrNull()
    val occurrenceRecord = occurrence?.let { scheduled ->
        records.singleOrNull { it.currentRevision.scheduledAt == scheduled.startsAt }
    }
    PlanEntrance(entered, 0, motionAllowed) {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            DetailInlineAction(
                R.drawable.ic_v2_clipboard,
                if (displayState == MedicationPlanDisplayState.SUSPENDED) {
                    "Gestionar tratamiento · Reanudar"
                } else "Gestionar tratamiento",
            ) { onAction(MedicalPlanAction.OpenMedicationManagement) }
            MedicationIdentityHero(
                revision, configuration, displayState.statusLabel,
                false,
            )
        }
    }
    PlanEntrance(entered, 1, motionAllowed) {
        BitavidaConnectedSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            MedicationAdministrationFocus(
                plan, displayState, configuration, occurrence, occurrenceRecord,
                state.lastQuickMedicationIntakeId, state.isSaving, now, motionAllowed, onAction,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MedicationProgrammingSummary(
                revision, configuration, reminderRuntimeState,
                programmingExpanded, motionAllowed,
                { programmingExpanded = !programmingExpanded }, onRequestReminderAccess,
            )
        }
    }
    PlanEntrance(entered, 2, motionAllowed) {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            MedicationRecentActivity(
                records.sortedByDescending {
                    it.currentRevision.actualAt ?: it.currentRevision.scheduledAt
                }.take(2),
                { onAction(MedicalPlanAction.OpenMedicationHistory) },
            )
            MedicationHistoricalInformation(
                plan, caregiver, historicalInformationExpanded, motionAllowed,
                { historicalInformationExpanded = !historicalInformationExpanded },
            )
        }
    }
}

@Composable
private fun MedicationIdentityHero(
    revision: MedicationPlanRevision,
    configuration: MedicationConfiguration?,
    statusLabel: String,
    statusIsPositive: Boolean,
) {
    val fontScale = LocalDensity.current.fontScale
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = BitavidaSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val form = configuration?.form ?: MedicationProductForm.entries
                .singleOrNull { it.name == revision.unitOrPresentation }
            MedicationListProductIcon(
                form,
                Modifier.medicationIconTransition(revision.medicationPlanId)
                    .size(
                        if (configuration == null) 56.dp
                        else if (fontScale >= 1.5f) 48.dp else 64.dp,
                    ),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    configuration?.form?.displayName()
                        ?: revision.unitOrPresentation.medicationPresentationDisplayName().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    revision.medicationName,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(
            listOfNotNull(
                configuration?.let {
                    listOfNotNull(it.concentrationValue, it.concentrationUnit)
                        .joinToString(" ").takeIf(String::isNotBlank)
                },
                configuration?.baseAmount?.let { "${it.value} ${it.unit} indicados" }
                    ?: revision.doseAsIndicated,
            ).joinToString(" · "),
            modifier = Modifier.fillMaxWidth(),
            softWrap = true,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BitavidaStatusPill(text = statusLabel, isPositive = statusIsPositive)
    }
}

@Composable
private fun MedicationAdministrationFocus(
    plan: MedicationPlan,
    displayState: MedicationPlanDisplayState,
    configuration: MedicationConfiguration?,
    occurrence: UpcomingMedicationAdministration?,
    occurrenceRecord: MedicationIntakeRecord?,
    lastQuickMedicationIntakeId: String?,
    isSaving: Boolean,
    now: LocalDateTime,
    motionAllowed: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val operational = displayState == MedicationPlanDisplayState.CURRENT
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    when {
        displayState == MedicationPlanDisplayState.SUSPENDED -> {
            MedicationCompactState(
                "Tratamiento suspendido",
                "No se programan nuevas administraciones. Puedes reanudar desde Gestionar tratamiento.",
            )
        }
        displayState == MedicationPlanDisplayState.COMPLETED ||
            displayState == MedicationPlanDisplayState.OUTSIDE_RECORDED_PERIOD -> {
            MedicationCompactState(
                "Tratamiento finalizado",
                plan.currentRevision.endsOn?.let { "Cierre · ${it.display()}. " }.orEmpty() +
                    "Los registros se conservan en Historial y calendario.",
            )
        }
        occurrence != null -> {
            val timing = medicationDetailTiming(
                occurrence.startsAt, occurrence.endsAt, now, occurrenceRecord != null,
            )
            val future = timing == MedicationDetailTiming.FUTURE
            val label = when (timing) {
                MedicationDetailTiming.RECORDED -> "ADMINISTRACIÓN REGISTRADA"
                MedicationDetailTiming.FUTURE -> "PRÓXIMA ADMINISTRACIÓN"
                MedicationDetailTiming.CURRENT_WINDOW -> "VENTANA VIGENTE"
                MedicationDetailTiming.AT_RECORDED_TIME -> "DISPONIBLE PARA REGISTRAR"
                MedicationDetailTiming.PAST_UNCONFIRMED -> "PASADA · SIN CONFIRMAR"
            }
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                if (displayState == MedicationPlanDisplayState.UPCOMING) {
                    Text("Inicio · ${plan.currentRevision.startsOn.display()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedContent(
                    targetState = occurrence.primaryMomentLabel(),
                    transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                    label = "detail_administration",
                ) { moment ->
                    Text(moment, Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(occurrence.startsAt.toLocalDate().display(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                // Only repeat the amount when this occurrence has a specific override.
                if (occurrence.amount != configuration?.baseAmount) {
                    Text("Cantidad para este momento · ${occurrence.amount.value} ${occurrence.amount.unit}",
                        modifier = Modifier.fillMaxWidth(),
                        softWrap = true,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    when {
                        occurrenceRecord != null -> occurrenceRecord.currentRevision.outcome.label()
                        future -> "Todavía futura · disponible para consulta"
                        timing == MedicationDetailTiming.CURRENT_WINDOW -> "Dentro del horario registrado · sin confirmar"
                        timing == MedicationDetailTiming.AT_RECORDED_TIME -> "Horario programado actual · sin confirmar"
                        else -> "Sin registro; esto no indica si ocurrió."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (operational && timing.canQuickRegister) {
                    QuickIntakeControls(occurrence, !isSaving, isSaving, onAction)
                    DetailInlineAction(R.drawable.ic_v2_clock, "Más opciones de registro") {
                        onAction(MedicalPlanAction.OpenMedicationIntakeOptions(occurrence))
                    }
                } else if (occurrenceRecord != null) {
                    DetailInlineAction(R.drawable.ic_v2_file, "Consultar o corregir registro") {
                        onAction(MedicalPlanAction.OpenEditMedicationIntake(occurrenceRecord.id))
                    }
                }
                AnimatedVisibility(
                    visible = lastQuickMedicationIntakeId != null &&
                        lastQuickMedicationIntakeId == occurrenceRecord?.id,
                    enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
                    exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✓ Registro guardado", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        TextButton(
                            onClick = { onAction(MedicalPlanAction.UndoQuickMedicationIntake) },
                            enabled = !isSaving,
                        ) { Text("Deshacer") }
                    }
                }
                if (future && operational) {
                    DetailInlineAction(R.drawable.ic_v2_plus, "Registro manual de otra administración") {
                        onAction(MedicalPlanAction.OpenRegisterMedicationIntake)
                    }
                }
            }
        }
        configuration?.frequency == MedicationFrequencyKind.AS_NEEDED -> {
            MedicationCompactState("Uso cuando sea necesario",
                configuration.asNeededInstruction ?: "Registra únicamente una administración que ocurrió.")
            DetailInlineAction(R.drawable.ic_v2_plus, "Registrar administración") {
                onAction(MedicalPlanAction.OpenRegisterMedicationIntake)
            }
        }
        else -> {
            MedicationCompactState("Sin próxima administración",
                if (displayState == MedicationPlanDisplayState.UPCOMING) {
                    "El tratamiento comienza ${plan.currentRevision.startsOn.display()}."
                } else "Consulta la programación o registra una administración manualmente.")
            if (operational) {
                DetailInlineAction(R.drawable.ic_v2_plus, "Registro manual") {
                    onAction(MedicalPlanAction.OpenRegisterMedicationIntake)
                }
            }
        }
    }
}

@Composable
private fun QuickIntakeControls(
    occurrence: UpcomingMedicationAdministration,
    enabled: Boolean,
    isSaving: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val motionAllowed = rememberBitavidaMotionAllowed(override = null)
    val fontScale = LocalDensity.current.fontScale
    val action: @Composable (MedicationIntakeOutcome, String, String, Modifier) -> Unit =
        { outcome, symbol, label, modifier ->
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    // Recheck wall time at activation, including after a device clock change.
                    if (!occurrence.startsAt.isAfter(LocalDateTime.now())) {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                        onAction(MedicalPlanAction.QuickRegisterMedicationIntake(occurrence, outcome))
                    }
                },
                enabled = enabled,
                interactionSource = interaction,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (outcome == MedicationIntakeOutcome.COMPLETED) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.secondary,
                    contentColor = if (outcome == MedicationIntakeOutcome.COMPLETED) {
                        MaterialTheme.colorScheme.onPrimary
                    } else MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                    .bitavidaFluidInteraction(interaction, enabled, motionAllowed),
            ) {
                Text(symbol, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(BitavidaSpacing.Small))
                Text(label)
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        BoxWithConstraints {
            if (maxWidth < 320.dp || fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    action(MedicationIntakeOutcome.COMPLETED, "✓", "Tomado", Modifier.fillMaxWidth())
                    action(MedicationIntakeOutcome.OMITTED, "×", "No se tomó", Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    action(MedicationIntakeOutcome.COMPLETED, "✓", "Tomado", Modifier.weight(1f))
                    action(MedicationIntakeOutcome.OMITTED, "×", "No se tomó", Modifier.weight(1f))
                }
            }
        }
        if (isSaving) {
            Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Guardando registro…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MedicationCompactState(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        Text(title, Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MedicationProgrammingSummary(
    revision: MedicationPlanRevision,
    configuration: MedicationConfiguration?,
    reminderRuntimeState: MedicationReminderRuntimeState,
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggle: () -> Unit,
    onRequestReminderAccess: () -> Unit,
) {
    val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_v2_calendar), null, Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary))
            Text(configuration?.frequencySummary() ?: revision.frequencyAsIndicated ?: "Sin frecuencia registrada",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Text(range(revision.startsOn, revision.endsOn),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(configuration?.reminderPreference?.reminderRuntimeLabel(reminderRuntimeState)
                ?: "Avisos desactivados",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
        ) { Text(if (expanded) "−  Ocultar horarios" else "+  Consultar horarios completos") }
        AnimatedVisibility(
            expanded,
            enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
            exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                MedicationProgrammingDetails(configuration)
                if (configuration?.reminderPreference != null &&
                    configuration.reminderPreference != com.bitavida.app.domain.model.MedicationReminderPreference.NONE &&
                    reminderRuntimeState == MedicationReminderRuntimeState.PERMISSION_REQUIRED) {
                    OutlinedButton(onClick = onRequestReminderAccess) { Text("Permitir avisos") }
                }
            }
        }
    }
}

@Composable
internal fun MedicationProgrammingDetails(
    configuration: MedicationConfiguration?,
) {
    if (configuration == null) {
        Text(
            "No hay una configuración estructurada para mostrar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    if (configuration.administrations.isEmpty()) {
        Text(
            when (configuration.frequency) {
                MedicationFrequencyKind.AS_NEEDED ->
                    "No se crean ocurrencias periódicas para el uso cuando sea necesario."
                MedicationFrequencyKind.EVERY_N_HOURS ->
                    configuration.hourlyInterval?.let { "Cada ${it} horas." }
                        ?: "Intervalo horario configurado."
                else -> "No hay administraciones individuales registradas."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        configuration.administrations.forEachIndexed { index, administration ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.bitavidaColors.paleBlue,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        administration.timingLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    administration.amountOverride?.let {
                        Text(
                            "Cantidad específica · ${it.value} ${it.unit}",
                            modifier = Modifier.fillMaxWidth(),
                            softWrap = true,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    administration.reminderLabel()?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    configuration.hourlyInterval?.let {
        Line("Intervalo", "Cada ${it} horas")
    }
    if (
        configuration.hourlyWindowStart != null &&
        configuration.hourlyWindowEnd != null
    ) {
        Line(
            "Ventana diaria",
            "${configuration.hourlyWindowStart.compactTime()} – " +
                "${configuration.hourlyWindowEnd.compactTime()}" +
                if (configuration.hourlyWindowEndsNextDay) {
                    " · día siguiente"
                } else {
                    ""
                },
        )
    }
    configuration.cycle?.let {
        Line(
            "Ciclo",
            "${it.activeDays} días activos · ${it.restDays} de descanso",
        )
        Line("Referencia", it.referenceDate.display())
    }
    configuration.asNeededInstruction?.let {
        Line("Indicación transcrita", it)
    }
    configuration.asNeededRestrictions?.let {
        Line("Restricciones registradas", it)
    }
}

@Composable
private fun MedicationRecentActivity(
    records: List<MedicationIntakeRecord>,
    onOpenHistory: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Seguimiento", Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        if (records.isEmpty()) {
            Text("Aún no hay registros. Su ausencia no indica si una administración ocurrió.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val lineColor = MaterialTheme.colorScheme.outlineVariant
            Column(
                modifier = Modifier.drawWithCache {
                    onDrawBehind {
                        // A thread connects actual records only; never an empty state.
                        if (records.size > 1) {
                            drawLine(lineColor, Offset(18.dp.toPx(), 18.dp.toPx()),
                                Offset(18.dp.toPx(), size.height - 18.dp.toPx()),
                                strokeWidth = 1.dp.toPx())
                        }
                    }
                },
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            ) {
                records.forEach { RecentMedicationIntake(it) }
            }
        }
        Button(
            onClick = onOpenHistory,
            modifier = Modifier.fillMaxWidth().heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) {
            Image(painterResource(R.drawable.ic_v2_calendar), null, Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary))
            Spacer(Modifier.size(BitavidaSpacing.Small))
            Text("Historial y calendario")
        }
    }
}

@Composable
private fun RecentMedicationIntake(record: MedicationIntakeRecord) {
    val intake = record.currentRevision
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(Modifier.size(36.dp), shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(intake.outcome.compactSymbol(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(intake.outcome.label(), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text((intake.actualAt ?: intake.scheduledAt).display(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (record.revisions.size > 1) BitavidaStatusPill(text = "Corregido", isPositive = false)
            intake.actualAmount?.let {
                Text("${it.value} ${it.unit}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            intake.observation?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailInlineAction(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(
                MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.size(BitavidaSpacing.ExtraSmall))
        Text(label)
    }
}

@Composable
private fun MedicationHistoricalInformation(
    plan: MedicationPlan,
    caregiver: String,
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) {
            Text(
                if (expanded) {
                    "Ocultar información histórica"
                } else {
                    "Información histórica"
                },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(
                tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0),
            ) + expandVertically(tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0)),
            exit = fadeOut(tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0)) +
                shrinkVertically(tween(if (motionAllowed) BitavidaFluidMotion.StateTransition else 0)),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                Line("Fecha de alta", plan.createdAtEpochMillis.displayInstant())
                Line("Autor", caregiver)
                Line("Versiones conservadas", plan.revisions.size.toString())
                Text(
                    "Los cambios completos se consultan desde Historial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MedicationManagement(
    state: MedicalPlanUiState,
    onAction: (MedicalPlanAction) -> Unit,
    motionAllowed: Boolean,
) {
    val plan = state.selectedMedicationPlan ?: return Missing()
    val configuration = plan.currentRevision.configuration
    var entered by remember(plan.id, motionAllowed) {
        mutableStateOf(!motionAllowed)
    }
    LaunchedEffect(plan.id, motionAllowed) {
        entered = true
    }
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val indicationStatus = plan.medicalIndicationId?.let { indicationId ->
        state.overview.indications.singleOrNull { it.id == indicationId }
            ?.currentRevision?.status
    }
    val displayState = plan.displayState(now, indicationStatus)
    val statusLabel = if (
        configuration?.status == MedicationTreatmentStatus.REMOVED &&
        configuration.endsOn?.isAfter(today) == true
    ) {
        "Retiro programado"
    } else {
        displayState.statusLabel
    }

    PlanEntrance(visible = entered, order = 0, motionAllowed = motionAllowed) {
        BitavidaConnectedSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.bitavidaColors.mint,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    BitavidaSpacing.Medium,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                configuration?.let {
                    MedicationProductIcon(
                        form = it.form,
                        modifier = Modifier.size(42.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        plan.currentRevision.medicationName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AnimatedContent(
                        targetState = statusLabel,
                        label = "managed_medication_status",
                    ) {
                        BitavidaStatusPill(
                            text = it,
                            isPositive = displayState in setOf(
                                MedicationPlanDisplayState.CURRENT,
                                MedicationPlanDisplayState.UPCOMING,
                            ),
                        )
                    }
                }
            }
        }
    }

    PlanEntrance(visible = entered, order = 1, motionAllowed = motionAllowed) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Text(
                "Información y nueva indicación",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TreatmentManagementAction(
                iconRes = R.drawable.ic_v2_file,
                title = "Corregir información",
                body = "Corrige un dato capturado incorrectamente.",
                onClick = {
                    onAction(MedicalPlanAction.OpenCorrectMedication)
                },
            )
            TreatmentManagementAction(
                iconRes = R.drawable.ic_v2_stethoscope,
                title = "Actualizar indicación",
                body = "Registra un cambio posterior indicado por el médico.",
                onClick = {
                    onAction(MedicalPlanAction.OpenUpdateMedicationIndication)
                },
            )
        }
    }

    if (configuration?.status != MedicationTreatmentStatus.REMOVED) {
        PlanEntrance(visible = entered, order = 2, motionAllowed = motionAllowed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                Text(
                    "Estado del tratamiento",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (configuration?.status == MedicationTreatmentStatus.SUSPENDED) {
                    TreatmentManagementAction(
                        iconRes = R.drawable.ic_v2_clock,
                        title = "Reanudar tratamiento",
                        body = "Solicita fecha efectiva y no crea tomas retroactivas.",
                        onClick = {
                            onAction(
                                MedicalPlanAction.RequestMedicationLifecycle(
                                    MedicationTreatmentStatus.ACTIVE,
                                ),
                            )
                        },
                    )
                } else if (
                    configuration?.status != MedicationTreatmentStatus.COMPLETED
                ) {
                    TreatmentManagementAction(
                        iconRes = R.drawable.ic_v2_clock,
                        title = "Suspender tratamiento",
                        body = "Detiene nuevas programaciones y conserva el historial.",
                        onClick = {
                            onAction(
                                MedicalPlanAction.RequestMedicationLifecycle(
                                    MedicationTreatmentStatus.SUSPENDED,
                                ),
                            )
                        },
                    )
                }
                if (configuration?.status != MedicationTreatmentStatus.COMPLETED) {
                    TreatmentManagementAction(
                        iconRes = R.drawable.ic_v2_clipboard,
                        title = "Finalizar tratamiento",
                        body = "Registra el cierre declarado sin interpretarlo médicamente.",
                        onClick = {
                            onAction(
                                MedicalPlanAction.RequestMedicationLifecycle(
                                    MedicationTreatmentStatus.COMPLETED,
                                ),
                            )
                        },
                    )
                }
            }
        }

        PlanEntrance(visible = entered, order = 3, motionAllowed = motionAllowed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                HorizontalDivider()
                Text(
                    "Acción delicada",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TreatmentManagementAction(
                    iconRes = R.drawable.ic_v2_file,
                    title = "Eliminar medicamento",
                    body = "Lo retira de las vistas operativas y conserva su historial protegido.",
                    dangerous = true,
                    onClick = {
                        onAction(
                            MedicalPlanAction.RequestMedicationLifecycle(
                                MedicationTreatmentStatus.REMOVED,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TreatmentManagementAction(
    iconRes: Int,
    title: String,
    body: String,
    dangerous: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (dangerous) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    BitavidaConnectedSurface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (dangerous) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BitavidaIconContainer(
                painter = painterResource(iconRes),
                contentDescription = null,
                containerColor = if (dangerous) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.bitavidaColors.paleBlue
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dangerous) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_v2_chevron),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (dangerous) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            )
        }
    }
}

private fun UpcomingMedicationAdministration.primaryMomentLabel(): String = when {
    activityRelation != null ->
        activityRelation.displayName(customActivity)
    endsAt != null ->
        "${startsAt.toLocalTime().compactTime()} – " +
            "${endsAt.toLocalTime().compactTime()}"
    else -> startsAt.toLocalTime().compactTime()
}

private fun MedicationAdministration.timingLabel(): String = when (timing) {
    MedicationAdministrationTiming.EXACT_TIME ->
        exactTime?.compactTime() ?: "Hora exacta"
    MedicationAdministrationTiming.TIME_WINDOW ->
        listOfNotNull(
            windowStart?.compactTime(),
            windowEnd?.compactTime(),
        ).joinToString(" – ") +
            if (windowEndsNextDay) " · día siguiente" else ""
    MedicationAdministrationTiming.ACTIVITY_ONLY ->
        activityRelation?.displayName(customActivity)
            ?: "Relación con actividad"
}

private fun MedicationAdministration.reminderLabel(): String? = when (timing) {
    MedicationAdministrationTiming.EXACT_TIME ->
        if (reminderEnabled == false) {
            "Aviso desactivado"
        } else {
            "Aviso a la misma hora"
        }
    MedicationAdministrationTiming.TIME_WINDOW ->
        noticeTime?.let {
            "Aviso ${it.compactTime()}" +
                if (noticeNextDay) " · día siguiente" else ""
        }
    MedicationAdministrationTiming.ACTIVITY_ONLY ->
        activityNoticeTime?.let { "Aviso ${it.compactTime()}" }
}

private fun LocalTime.compactTime(): String = format(
    DateTimeFormatter.ofPattern(
        "h:mm a",
        Locale.forLanguageTag("es-MX"),
    ),
)

private fun MedicationIntakeOutcome.compactSymbol(): String = when (this) {
    MedicationIntakeOutcome.COMPLETED -> "✓"
    MedicationIntakeOutcome.PARTIAL -> "½"
    MedicationIntakeOutcome.OMITTED -> "—"
    MedicationIntakeOutcome.REFUSED -> "×"
    MedicationIntakeOutcome.UNCONFIRMED -> "?"
}



@Composable
private fun MedicationHistory(
    state: MedicalPlanUiState,
    motionAllowed: Boolean,
    onAction: (MedicalPlanAction) -> Unit,
) {
    if (state.selectedMedicationPlan == null) return Missing()
    if (state.medicationChangesOnly) {
        MedicationChangesHistory(
            plans = state.selectedMedicationIdentityPlans,
            motionAllowed = motionAllowed,
        )
        return
    }
    MedicationIntakeCalendar(
        state = state,
        motionAllowed = motionAllowed,
        onAction = onAction,
    )
}



@Composable
private fun MedicationLifecycleDialog(
    draft: MedicationLifecycleDraft,
    errors: Set<MedicalCareField>,
    saving: Boolean,
    motionAllowed: Boolean,
    onChange: (MedicationLifecycleDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val effectiveDate = runCatching {
        LocalDate.parse(draft.effectiveOn)
    }.getOrNull()
    val title = when (draft.targetStatus) {
        MedicationTreatmentStatus.ACTIVE -> "Reactivar medicamento"
        MedicationTreatmentStatus.SUSPENDED -> "Suspender medicamento"
        MedicationTreatmentStatus.COMPLETED -> "Finalizar medicamento"
        MedicationTreatmentStatus.REMOVED -> "Eliminar medicamento"
    }
    MedicationLifecycleSheetFrame(
        medication = null,
        title = title,
        saving = saving,
        motionAllowed = motionAllowed,
        onDismiss = onDismiss,
    ) {
        val dateTimePicker = LocalBitavidaDateTimePickerController.current
        Text(
            when (draft.targetStatus) {
                MedicationTreatmentStatus.ACTIVE ->
                    "Volverá a generar administraciones desde la fecha indicada."
                MedicationTreatmentStatus.SUSPENDED ->
                    "Dejará de generar administraciones hasta que se reactive."
                MedicationTreatmentStatus.COMPLETED ->
                    "Dejará de generar administraciones futuras."
                MedicationTreatmentStatus.REMOVED ->
                    "Dejará de aparecer en las vistas operativas. Sus registros permanecerán protegidos."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (
            draft.targetStatus == MedicationTreatmentStatus.ACTIVE ||
            draft.targetStatus == MedicationTreatmentStatus.REMOVED
        ) {
            TextButton(
                onClick = {
                    showMedicationDatePicker(dateTimePicker, effectiveDate) {
                        onChange(draft.copy(effectiveOn = it.toString()))
                    }
                },
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) {
                Text(effectiveDate?.displayLabel() ?: "Elegir fecha")
            }
            if (MedicalCareField.MEDICATION_DATES in errors) {
                Text(
                    "Selecciona una fecha válida.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        BitavidaLimitedTextField(
            value = draft.reason,
            limit = CaptureLimit.REASON,
            onValueChange = { onChange(draft.copy(reason = it)) },
            label = { Text("Motivo u observación") },
            supportingText = { Text("Se conservará en el historial") },
            isError = MedicalCareField.CHANGE_REASON in errors,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onConfirm,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = BitavidaSpacing.PrimaryControlHeight),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(if (saving) "Guardando…" else "Confirmar")
        }
        OutlinedButton(
            onClick = onDismiss,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            shape = MaterialTheme.shapes.large,
        ) {
            Text("Cancelar")
        }
    }
}

private fun MedicationForm.editorLabel(): String = when (this) {
    MedicationForm.TABLET -> "Tableta"
    MedicationForm.CAPSULE -> "Cápsula"
    MedicationForm.ORAL_LIQUID -> "Jarabe o solución"
    MedicationForm.DROPS -> "Gotas"
    MedicationForm.INJECTABLE -> "Inyectable"
    MedicationForm.INHALER -> "Inhalador"
    MedicationForm.TOPICAL -> "Crema o ungüento"
    MedicationForm.PATCH -> "Parche"
    MedicationForm.POWDER -> "Polvo o sobres"
    MedicationForm.OTHER -> "Otra presentación"
}

private fun MedicationIntakeOutcome.label(): String = when (this) {
    MedicationIntakeOutcome.COMPLETED -> "Realizada completa"
    MedicationIntakeOutcome.PARTIAL -> "Realizada parcial"
    MedicationIntakeOutcome.OMITTED -> "Omitida"
    MedicationIntakeOutcome.REFUSED -> "Rechazada"
    MedicationIntakeOutcome.UNCONFIRMED -> "No fue posible confirmarla"
}

@Composable
private fun StatusActions(
    current: MedicalIndicationStatus,
    onRequest: (MedicalIndicationStatus) -> Unit,
) {
    Card("ESTADO DECLARADO", "Actualizar sin borrar historial",
        "Bitavida registra la declaración; no la interpreta.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            if (current != MedicalIndicationStatus.SUSPENDED) StatusButton("Suspender", Modifier.weight(1f)) {
                onRequest(MedicalIndicationStatus.SUSPENDED)
            }
            if (current != MedicalIndicationStatus.COMPLETED) StatusButton("Finalizar", Modifier.weight(1f)) {
                onRequest(MedicalIndicationStatus.COMPLETED)
            }
            if (current != MedicalIndicationStatus.ACTIVE) StatusButton("Reactivar", Modifier.weight(1f)) {
                onRequest(MedicalIndicationStatus.ACTIVE)
            }
        }
    }
}

@Composable
private fun StatusButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
    ) { Text(text) }
}

@Composable
private fun StatusDialog(
    draft: StatusChangeDraft,
    errors: Set<MedicalCareField>,
    saving: Boolean,
    onChange: (StatusChangeDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${draft.targetStatus.action()} indicación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                Text("Se añadirá una revisión y se conservará la versión anterior.")
                Field(draft.reason, { onChange(draft.copy(reason = it)) },
                    "Motivo declarado", "Obligatorio",
                    MedicalCareField.CHANGE_REASON in errors, minLines = 2,
                    limit = CaptureLimit.REASON,
                )
                FictionCheck(
                    draft.fictitiousDataAcknowledged,
                    MedicalCareField.FICTITIOUS_DATA_ACKNOWLEDGEMENT in errors,
                ) { onChange(draft.copy(fictitiousDataAcknowledged = it)) }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !saving,
                modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) { Text(if (saving) "Guardando…" else "Guardar revisión") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) { Text("Cancelar") }
        },
    )
}

@Composable
private fun Card(
    eyebrow: String,
    title: String,
    body: String,
    content: @Composable () -> Unit,
) {
    BitavidaConnectedSurface(Modifier.fillMaxWidth()) {
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(BitavidaSpacing.Small))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(body, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(BitavidaSpacing.Default))
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) { content() }
    }
}

@Composable
private fun MedicalDateSelectionField(
    value: String,
    label: String,
    optional: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    val selectedDate = remember(value) {
        runCatching { LocalDate.parse(value) }.getOrNull()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        OutlinedButton(
            onClick = {
                dateTimePicker.showDate(
                    selectedDate = selectedDate ?: LocalDate.now(),
                    onConfirm = { onValueChange(it.toString()) },
                )
            },
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                when {
                    selectedDate != null -> selectedDate.displayLabel()
                    value.isNotBlank() -> value
                    else -> "Elegir fecha"
                },
            )
        }
        if (optional && value.isNotBlank()) {
            TextButton(
                onClick = { onValueChange("") },
                modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            ) {
                Text("Quitar fecha")
            }
        }
        if (isError) {
            Text(
                "Revisa la fecha seleccionada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MedicationScheduleSelectionField(
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    val scheduleTokens = remember(value) {
        value.split(",").map(String::trim).filter(String::isNotEmpty)
    }
    val schedules = remember(scheduleTokens) {
        scheduleTokens.mapNotNull { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        Text(
            "Horarios explícitos",
            style = MaterialTheme.typography.labelLarge,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (schedules.isEmpty()) {
            Text(
                "Agrega cada horario exactamente como fue indicado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                schedules.forEach { time ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                MedicationTime(time.hour, time.minute).displayLabel(context),
                                modifier = Modifier.padding(start = BitavidaSpacing.Small),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            TextButton(
                                onClick = {
                                    onValueChange(
                                        schedules.filterNot { it == time }
                                            .joinToString(", ") { it.toString() },
                                    )
                                },
                                modifier = Modifier.heightIn(
                                    min = BitavidaSpacing.MinimumTouchTarget,
                                ),
                            ) {
                                Text("Quitar")
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                dateTimePicker.showTime(
                    selectedTime = schedules.lastOrNull()
                        ?: java.time.LocalTime.now().withSecond(0).withNano(0),
                    onConfirm = { selected ->
                        val next = if (selected in schedules) schedules else schedules + selected
                        onValueChange(next.joinToString(", ") { it.toString() })
                    },
                )
            },
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
            shape = MaterialTheme.shapes.large,
        ) {
            Text("Agregar horario")
        }
        if (isError || schedules.size != scheduleTokens.size) {
            Text(
                "Agrega al menos un horario válido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    support: String,
    error: Boolean,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    limit: CaptureLimit = CaptureLimit.BRIEF_NOTE,
) {
    BitavidaLimitedTextField(
        value = value,
        limit = limit,
        onValueChange = onChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BitavidaSpacing.PrimaryControlHeight),
        label = { Text(label) },
        supportingText = { Text(if (error) "Revisa este dato." else support) },
        isError = error,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = keyboardType,
        ),
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun FictionCheck(checked: Boolean, error: Boolean, onChange: (Boolean) -> Unit) {
    BitavidaConnectedSurface(
        containerColor = if (error) MaterialTheme.bitavidaColors.roseStrong
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked,
                onChange,
                Modifier.size(BitavidaSpacing.MinimumTouchTarget),
            )
            Text(
                "Confirmo que usaré únicamente datos ficticios de desarrollo.",
                Modifier.weight(1f),
            )
        }
        if (error) {
            Text(
                "La confirmación es obligatoria; los datos clínicos reales están bloqueados.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxWidth().padding(BitavidaSpacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        CircularProgressIndicator()
        Text("Preparando tu plan…")
    }
}

@Composable
private fun Missing() {
    BitavidaConnectedSurface {
        Text("No se encontró este registro.", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorCard(error: MedicalPlanGlobalError, retry: () -> Unit) {
    BitavidaConnectedSurface(containerColor = MaterialTheme.bitavidaColors.roseStrong) {
        Text(
            when (error) {
                MedicalPlanGlobalError.STORAGE_UNAVAILABLE ->
                    "No se pudo abrir la información guardada en este dispositivo."
                MedicalPlanGlobalError.INTEGRITY_ERROR ->
                    "No se pudo leer toda la información guardada."
                MedicalPlanGlobalError.CONCURRENT_CHANGE -> "El registro cambió antes de guardar."
                MedicalPlanGlobalError.AMBIGUOUS_MEDICATION_OCCASION ->
                    "Hay varias declaraciones para esta ocasión. Selecciona un registro en el historial para editarlo."
                MedicalPlanGlobalError.RECORD_NOT_FOUND -> "El registro no está disponible."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = retry,
            modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget),
        ) { Text("Reintentar") }
    }
}

private fun MedicalIndicationStatus.label() = when (this) {
    MedicalIndicationStatus.ACTIVE -> "Activa"
    MedicalIndicationStatus.SUSPENDED -> "Suspendida"
    MedicalIndicationStatus.COMPLETED -> "Finalizada"
}

private fun MedicalIndicationStatus.action() = when (this) {
    MedicalIndicationStatus.ACTIVE -> "Reactivar"
    MedicalIndicationStatus.SUSPENDED -> "Suspender"
    MedicalIndicationStatus.COMPLETED -> "Finalizar"
}

private val displayDate = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.forLanguageTag("es-MX"))
private fun LocalDate.display(): String = format(displayDate)
private val displayDateTime = DateTimeFormatter.ofPattern(
    "d MMM uuuu · h:mm a",
    Locale.forLanguageTag("es-MX"),
)
private fun LocalDateTime.display(): String = format(displayDateTime)
private fun Long.displayInstant(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.forLanguageTag("es-MX")))

private fun range(start: LocalDate?, end: LocalDate?): String = when {
    start != null && end != null -> "${start.display()} a ${end.display()}"
    start != null -> "Desde ${start.display()}"
    end != null -> "Hasta ${end.display()}"
    else -> "No registrada"
}

private fun String.firstName() = trim().substringBefore(" ").ifBlank { this }

@Preview(name = "Plan · Claro", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlanLightPreview() {
    BitavidaTheme {
        MedicalPlanScreen(
            MedicalPlanUiState(isLoading = false),
            "Ana Prueba",
            "Elena Prueba",
            {},
            {},
            activeReminderCount = 2,
            activeMedicationCount = 3,
            activeMeasurementCount = 1,
            activeObservationCount = 0,
        )
    }
}

@Preview(
    name = "Plan · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PlanDarkPreview() {
    BitavidaTheme(darkTheme = true) {
        MedicalPlanScreen(
            MedicalPlanUiState(isLoading = false),
            "Ana Prueba",
            "Elena Prueba",
            {},
            {},
            activeReminderCount = 2,
            activeMedicationCount = 3,
            activeMeasurementCount = 1,
            activeObservationCount = 0,
        )
    }
}

@Preview(name = "Plan · Compacto", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun PlanCompactPreview() {
    BitavidaTheme {
        MedicalPlanScreen(
            MedicalPlanUiState(isLoading = false),
            "Ana Prueba",
            "Elena Prueba",
            {},
            {},
            activeReminderCount = 2,
            activeMedicationCount = 3,
            activeMeasurementCount = 1,
            activeObservationCount = 0,
        )
    }
}

@Preview(
    name = "Plan · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun PlanLargeTextPreview() {
    BitavidaTheme {
        MedicalPlanScreen(
            MedicalPlanUiState(isLoading = false),
            "Ana Prueba",
            "Elena Prueba",
            {},
            {},
            activeReminderCount = 2,
            activeMedicationCount = 3,
            activeMeasurementCount = 1,
            activeObservationCount = 0,
        )
    }
}

@Preview(
    name = "Detalle medicamento premium · Claro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Preview(
    name = "Detalle medicamento premium · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Detalle medicamento premium · Compacto",
    showBackground = true,
    widthDp = 320,
    heightDp = 720,
)
@Preview(
    name = "Detalle medicamento premium · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun MedicationPremiumDetailPreviews() {
    val plan = premiumMedicationPreviewPlan()
    BitavidaTheme {
        MedicalPlanScreen(
            state = MedicalPlanUiState(
                screen = MedicalPlanScreen.MEDICATION_DETAIL,
                overview = MedicalCareOverview(
                    indications = emptyList(),
                    medicationPlans = listOf(plan),
                ),
                selectedMedicationPlanId = plan.id,
                isLoading = false,
            ),
            caregiverDisplayName = "Ana Prueba",
            personUnderCareDisplayName = "Elena Prueba",
            onAction = {},
            onTodaySelected = {},
        )
    }
}

@Preview(
    name = "Gestionar tratamiento · Claro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Preview(
    name = "Gestionar tratamiento · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Gestionar tratamiento · Compacto",
    showBackground = true,
    widthDp = 320,
    heightDp = 720,
)
@Preview(
    name = "Gestionar tratamiento · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun MedicationManagementPreviews() {
    val plan = premiumMedicationPreviewPlan()
    BitavidaTheme {
        MedicalPlanScreen(
            state = MedicalPlanUiState(
                screen = MedicalPlanScreen.MEDICATION_MANAGEMENT,
                overview = MedicalCareOverview(
                    indications = emptyList(),
                    medicationPlans = listOf(plan),
                ),
                selectedMedicationPlanId = plan.id,
                isLoading = false,
            ),
            caregiverDisplayName = "Ana Prueba",
            personUnderCareDisplayName = "Elena Prueba",
            onAction = {},
            onTodaySelected = {},
        )
    }
}

private fun premiumMedicationPreviewPlan(): MedicationPlan {
    val today = LocalDate.now()
    val administrationTime = LocalTime.now()
        .plusHours(1)
        .withSecond(0)
        .withNano(0)
    val configuration = MedicationConfiguration(
        form = MedicationProductForm.TABLET,
        concentrationValue = "10",
        concentrationUnit = "mg",
        baseAmount = MedicationAmount("1", "tableta"),
        frequency = MedicationFrequencyKind.EVERY_DAY,
        startsOn = today,
        reminderPreference =
            com.bitavida.app.domain.model.MedicationReminderPreference.NORMAL,
        administrations = listOf(
            MedicationAdministration(
                id = "premium-preview-administration",
                timing = MedicationAdministrationTiming.EXACT_TIME,
                exactTime = administrationTime,
                amountOverride = MedicationAmount("1/2", "tableta"),
            ),
        ),
    )
    return MedicationPlan(
        id = "premium-preview-plan",
        personUnderCareId = "premium-preview-person",
        medicalIndicationId = null,
        medicalContentOrigin =
            InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
        scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
        createdAtEpochMillis = 1L,
        createdByActorId = "premium-preview-caregiver",
        contentMode = ClinicalContentMode.REAL_CLINICAL_DATA,
        revisions = listOf(
            MedicationPlanRevision(
                id = "premium-preview-revision",
                medicationPlanId = "premium-preview-plan",
                revisionNumber = 1,
                medicationName = "Medicamento ficticio",
                doseAsIndicated = "1 tableta",
                unitOrPresentation = "TABLET",
                frequencyAsIndicated = "Todos los días",
                startsOn = today,
                endsOn = null,
                originalAdditionalInstructions = null,
                scheduledDoses = emptyList(),
                capturedAtEpochMillis = 1L,
                authorActorId = "premium-preview-caregiver",
                changeKind = MedicationPlanChangeKind.CREATED,
                changeReason = null,
                configuration = configuration,
            ),
        ),
    )
}

@Preview(name = "Detalle según necesidad · Claro", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Detalle según necesidad · Oscuro", showBackground = true, widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Detalle según necesidad · Compacto", showBackground = true, widthDp = 320, heightDp = 720)
@Preview(name = "Detalle según necesidad · Texto 200%", showBackground = true, widthDp = 390, heightDp = 844, fontScale = 2f)
@Composable
private fun MedicationAsNeededPreviews() {
    val base = premiumMedicationPreviewPlan()
    val current = base.currentRevision
    val plan = base.copy(revisions = listOf(current.copy(
        medicationName = "Medicamento ficticio según necesidad",
        configuration = requireNotNull(current.configuration).copy(
            frequency = MedicationFrequencyKind.AS_NEEDED,
            asNeededInstruction = "Indicación ficticia transcrita para la vista previa",
            administrations = emptyList(),
        ),
    )))
    BitavidaTheme {
        MedicalPlanScreen(
            state = MedicalPlanUiState(
                screen = MedicalPlanScreen.MEDICATION_DETAIL,
                overview = MedicalCareOverview(emptyList(), listOf(plan)),
                selectedMedicationPlanId = plan.id,
                isLoading = false,
            ),
            caregiverDisplayName = "Ana Prueba",
            personUnderCareDisplayName = "Elena Prueba",
            onAction = {}, onTodaySelected = {},
        )
    }
}

@Preview(name = "Cambios del medicamento · Claro", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Cambios del medicamento · Oscuro", showBackground = true, widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Cambios del medicamento · Compacto", showBackground = true, widthDp = 320, heightDp = 720)
@Preview(name = "Cambios del medicamento · Texto 200%", showBackground = true, widthDp = 390, heightDp = 844, fontScale = 2f)
@Composable
private fun MedicationChangesPreviews() {
    val base = premiumMedicationPreviewPlan()
    val original = base.currentRevision
    val corrected = original.copy(
        id = "revision-ficticia-corregida",
        revisionNumber = 2,
        medicationName = "Nombre ficticio corregido",
        capturedAtEpochMillis = original.capturedAtEpochMillis + 60_000,
        changeKind = MedicationPlanChangeKind.BASE_DETAILS_CORRECTED,
        changeReason = "Corrección ficticia del nombre",
    )
    BitavidaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
            MedicationChangesHistory(
                plan = base.copy(revisions = listOf(original, corrected)),
                motionAllowed = true,
            )
        }
    }
}

@Preview(
    name = "Historial medicamento · Claro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Preview(
    name = "Historial medicamento · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Historial medicamento · Compacto",
    showBackground = true,
    widthDp = 320,
    heightDp = 720,
)
@Preview(
    name = "Historial medicamento · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun MedicationHistoryPreviews() {
    val configuration = MedicationConfiguration(
        form = MedicationProductForm.TABLET,
        concentrationValue = "10",
        concentrationUnit = "mg",
        baseAmount = MedicationAmount("1", "tableta"),
        frequency = MedicationFrequencyKind.EVERY_DAY,
        startsOn = LocalDate.of(2026, 11, 1),
        administrations = listOf(
            MedicationAdministration(
                id = "preview-administration",
                timing =
                    MedicationAdministrationTiming.EXACT_TIME,
                exactTime = java.time.LocalTime.of(8, 0),
            ),
        ),
    )
    val plan = MedicationPlan(
        id = "preview-plan",
        personUnderCareId = "preview-person",
        medicalIndicationId = null,
        medicalContentOrigin =
            InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION,
        scheduleOrigin = InformationOrigin.CAREGIVER_RECORD,
        createdAtEpochMillis = 1L,
        createdByActorId = "preview-caregiver",
        contentMode = ClinicalContentMode.FICTITIOUS_DEVELOPMENT,
        revisions = listOf(
            MedicationPlanRevision(
                id = "preview-revision",
                medicationPlanId = "preview-plan",
                revisionNumber = 1,
                medicationName = "Medicamento de ejemplo",
                doseAsIndicated = "1 tableta",
                unitOrPresentation = "TABLET",
                frequencyAsIndicated = "Todos los días",
                startsOn = configuration.startsOn,
                endsOn = null,
                originalAdditionalInstructions = null,
                scheduledDoses = emptyList(),
                capturedAtEpochMillis = 1L,
                authorActorId = "preview-caregiver",
                changeKind = MedicationPlanChangeKind.CREATED,
                changeReason = null,
                configuration = configuration,
            ),
        ),
    )
    BitavidaTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
            ) {
                MedicationHistory(
                    state = MedicalPlanUiState(
                        screen = MedicalPlanScreen.MEDICATION_HISTORY,
                        overview = MedicalCareOverview(
                            indications = emptyList(),
                            medicationPlans = listOf(plan),
                        ),
                        selectedMedicationPlanId = plan.id,
                        isLoading = false,
                    ),
                    motionAllowed = true,
                    onAction = {},
                )
            }
        }
    }
}

@Preview(
    name = "Sección vacía · Claro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Preview(
    name = "Sección vacía · Oscuro",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Sección vacía · Compacto",
    showBackground = true,
    widthDp = 320,
    heightDp = 720,
)
@Preview(
    name = "Sección vacía · Texto 200%",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    fontScale = 2f,
)
@Composable
private fun PlanSectionEmptyPreviews() {
    BitavidaTheme {
        MedicalPlanScreen(
            state = MedicalPlanUiState(
                screen = MedicalPlanScreen.SECTION,
                selectedSection = PlanSection.MEDICATIONS,
                isLoading = false,
            ),
            caregiverDisplayName = "Ana Prueba",
            personUnderCareDisplayName = "Elena Prueba",
            onAction = {},
            onTodaySelected = {},
        )
    }
}
