package com.bitavida.app.ui.measurements

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.education.EducationGroup
import com.bitavida.app.ui.education.BitavidaAiRoute
import com.bitavida.app.ui.education.BitavidaAiViewModel
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.confirmed
import com.bitavida.app.domain.model.confirmedBloodPressure
import com.bitavida.app.domain.model.confirmedWeight
import com.bitavida.app.domain.model.MeasurementFollowUp
import com.bitavida.app.domain.model.toMeasurementFollowUp
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.bloodPressureMeasurementContexts
import com.bitavida.app.domain.model.glucoseMeasurementContexts
import com.bitavida.app.domain.usecase.MeasurementScheduleHistory
import com.bitavida.app.domain.usecase.projectMeasurementOverview
import com.bitavida.app.domain.usecase.recordsForMeasurementKind
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaNavigationItem
import com.bitavida.app.ui.components.BitavidaThreadNavigation
import com.bitavida.app.ui.medical.CareSectionHeader
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class CalendarCaptureTransition { IGNORE, RESTORE_DETAIL, RETURN_TO_CALENDAR }

/** Effects from another module or an earlier capture must not change the calendar route. */
internal fun calendarCaptureTransition(tracked: MeasurementKind?, source: MeasurementKind, saved: Boolean): CalendarCaptureTransition =
    if (tracked != source) CalendarCaptureTransition.IGNORE
    else if (saved) CalendarCaptureTransition.RETURN_TO_CALENDAR else CalendarCaptureTransition.RESTORE_DETAIL

internal enum class MeasurementDestination {
    MAIN,
    DETAIL,
    FOLLOW_UP,
    OTHER,
    HISTORY,
    GLUCOSE_HISTORY,
    BLOOD_PRESSURE_HISTORY,
    WEIGHT_HISTORY,
    HELP,
}

internal fun measurementBackDestination(
    destination: MeasurementDestination,
    helpFromDetail: Boolean,
): MeasurementDestination? = when (destination) {
    MeasurementDestination.MAIN -> null
    MeasurementDestination.DETAIL -> MeasurementDestination.MAIN
    MeasurementDestination.FOLLOW_UP,
    MeasurementDestination.GLUCOSE_HISTORY,
    MeasurementDestination.BLOOD_PRESSURE_HISTORY,
    MeasurementDestination.WEIGHT_HISTORY,
    -> MeasurementDestination.DETAIL
    MeasurementDestination.HELP -> if (helpFromDetail) {
        MeasurementDestination.DETAIL
    } else {
        MeasurementDestination.MAIN
    }
    else -> MeasurementDestination.MAIN
}

/**
 * Glucose capture uses the host's retained ViewModel and encrypted repository.
 * Other capture and follow-up actions remain enabled only through real host callbacks.
 */
@Composable
fun MeasurementsScreen(
    personUnderCareDisplayName: String,
    onBack: () -> Unit,
    onTodaySelected: () -> Unit,
    modifier: Modifier = Modifier,
    embeddedInPlanScaffold: Boolean = false,
    records: List<MeasurementRecord> = emptyList(),
    followUps: List<MeasurementFollowUp> = emptyList(),
    today: LocalDate = LocalDate.now(),
    onRegisterMeasurement: ((MeasurementKind) -> Unit)? = null,
    onFollowUpAction: ((MeasurementKind, MeasurementFollowUpAction) -> Unit)? = null,
    onRequestNotificationPermission: () -> Unit = {},
    glucoseViewModel: GlucoseViewModel? = null,
    bloodPressureViewModel: BloodPressureViewModel? = null,
    weightViewModel: WeightViewModel? = null,
    bitavidaAiViewModel: BitavidaAiViewModel? = null,
    onCalendarDetailExit: (() -> Unit)? = null,
    onCalendarDetailRestore: (() -> Unit)? = null,
) {
    val calendarExit by rememberUpdatedState(onCalendarDetailExit)
    val calendarRestore by rememberUpdatedState(onCalendarDetailRestore)
    val glucoseState = glucoseViewModel?.state?.collectAsState()?.value
    val bloodPressureState =
        bloodPressureViewModel?.state?.collectAsState()?.value
    val weightState = weightViewModel?.state?.collectAsState()?.value
    var calendarDraftKind by rememberSaveable { mutableStateOf<MeasurementKind?>(null) }
    LaunchedEffect(calendarExit, glucoseState?.draft, bloodPressureState?.draft, weightState?.draft) {
        if (calendarExit != null) {
            when {
                glucoseState?.draft != null -> calendarDraftKind = MeasurementKind.CAPILLARY_GLUCOSE
                bloodPressureState?.draft != null -> calendarDraftKind = MeasurementKind.BLOOD_PRESSURE
                weightState?.draft != null -> calendarDraftKind = MeasurementKind.WEIGHT
            }
        }
    }
    val managedKinds = setOf(
        MeasurementKind.CAPILLARY_GLUCOSE,
        MeasurementKind.BLOOD_PRESSURE,
        MeasurementKind.WEIGHT,
    )
    val displayRecords = records.filterNot {
        it.kind in managedKinds
    } + glucoseState?.records.orEmpty() +
        bloodPressureState?.records.orEmpty() +
        weightState?.records.orEmpty()
    val recordsByKind = remember(displayRecords) {
        MeasurementKind.entries.associateWith { kind ->
            recordsForMeasurementKind(displayRecords, kind)
        }
    }
    val persistedGlucoseFollowUp = glucoseState?.followUp
        ?.takeIf { it.currentRevision.active }
        ?.toMeasurementFollowUp()
    val persistedBloodPressureFollowUp =
        bloodPressureState?.followUp
            ?.takeIf { it.currentRevision.active }
            ?.toMeasurementFollowUp(MeasurementKind.BLOOD_PRESSURE)
    val persistedWeightFollowUp = weightState?.followUp
        ?.takeIf { it.currentRevision.active }
        ?.toMeasurementFollowUp(MeasurementKind.WEIGHT)
    val displayFollowUps = followUps.filterNot { (kind) ->
        kind in managedKinds
    } + listOfNotNull(
        persistedGlucoseFollowUp,
        persistedBloodPressureFollowUp,
        persistedWeightFollowUp,
    )
    var destination by rememberSaveable { mutableStateOf(MeasurementDestination.MAIN) }
    var selectedKind by rememberSaveable { mutableStateOf(MeasurementKind.CAPILLARY_GLUCOSE) }
    var helpFromDetail by rememberSaveable { mutableStateOf(value = false) }
    var hasOpenedMeasurement by rememberSaveable { mutableStateOf(false) }
    var followUpChangeKind by rememberSaveable {
        mutableStateOf(GlucoseFollowUpChangeKind.CREATED)
    }
    var showFollowUpHistory by rememberSaveable {
        mutableStateOf(value = false)
    }
    val homeScroll = rememberScrollState()
    val detailScroll = rememberScrollState()
    val destinationScroll = rememberScrollState()
    val calendarScroll = rememberScrollState()
    val detailState = rememberSaveableStateHolder()
    val scroll = when (destination) {
        MeasurementDestination.MAIN -> homeScroll
        MeasurementDestination.DETAIL -> detailScroll
        MeasurementDestination.HISTORY -> calendarScroll
        else -> destinationScroll
    }
    val overview = remember(displayRecords, today, displayFollowUps) {
        projectMeasurementOverview(displayRecords, today, displayFollowUps)
    }
    val selected = (overview.primary + overview.secondary).first { (kind) ->
        kind == selectedKind
    }
    val motionAllowed = rememberBitavidaMotionAllowed(null)
    val navigationScope = rememberCoroutineScope()
    val navigationOffset = remember { Animatable(0f) }
    val navigationAlpha = remember { Animatable(1f) }
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    val navigateTo: (MeasurementDestination) -> Unit = navigate@ { target ->
        if ((target == destination) || (navigationJob?.isActive == true)) return@navigate
        if (destination == MeasurementDestination.HELP && target != MeasurementDestination.HELP) {
            bitavidaAiViewModel?.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear)
        }
        val direction = when {
            destination == MeasurementDestination.MAIN -> 1f
            target == MeasurementDestination.MAIN -> -1f
            destination == MeasurementDestination.DETAIL -> 1f
            target == MeasurementDestination.DETAIL -> -1f
            target.ordinal > destination.ordinal -> 1f
            else -> -1f
        }
        navigationJob = navigationScope.launch {
            if (!motionAllowed) {
                destination = target
                navigationOffset.snapTo(0f)
                navigationAlpha.snapTo(1f)
                return@launch
            }
            coroutineScope {
                launch {
                    navigationOffset.animateTo(
                        -direction,
                        tween(120, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    navigationAlpha.animateTo(
                        0f,
                        tween(120, easing = FastOutSlowInEasing),
                    )
                }
            }
            navigationOffset.snapTo(direction)
            destination = target
            navigationAlpha.snapTo(0f)
            coroutineScope {
                launch {
                    navigationOffset.animateTo(
                        0f,
                        tween(170, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    navigationAlpha.animateTo(
                        1f,
                        tween(170, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }
    val navigationTravel = with(LocalDensity.current) { 28.dp.toPx() }
    val dispatchGlucose: (GlucoseAction) -> Unit = { action ->
        val closeDetail = calendarExit != null && (action == GlucoseAction.DismissOccurrence || action == GlucoseAction.DismissRecord) && glucoseViewModel?.state?.value?.draft == null
        if (calendarExit != null && action == GlucoseAction.ConfirmDeleteSelectedRecord) calendarDraftKind = MeasurementKind.CAPILLARY_GLUCOSE
        glucoseViewModel?.onAction(action)
        if (calendarExit != null && glucoseViewModel?.state?.value?.draft != null) calendarDraftKind = MeasurementKind.CAPILLARY_GLUCOSE
        if (closeDetail) calendarExit?.invoke()
    }
    val dispatchPressure: (BloodPressureAction) -> Unit = { action ->
        val closeDetail = calendarExit != null && (action == BloodPressureAction.DismissOccurrence || action == BloodPressureAction.DismissRecord) && bloodPressureViewModel?.state?.value?.draft == null
        if (calendarExit != null && action == BloodPressureAction.ConfirmDeleteSelectedRecord) calendarDraftKind = MeasurementKind.BLOOD_PRESSURE
        bloodPressureViewModel?.onAction(action)
        if (calendarExit != null && bloodPressureViewModel?.state?.value?.draft != null) calendarDraftKind = MeasurementKind.BLOOD_PRESSURE
        if (closeDetail) calendarExit?.invoke()
    }
    val dispatchWeight: (WeightAction) -> Unit = { action ->
        val closeDetail = calendarExit != null && (action == WeightAction.DismissOccurrence || action == WeightAction.DismissRecord) && weightViewModel?.state?.value?.draft == null
        if (calendarExit != null && action == WeightAction.ConfirmDeleteSelectedRecord) calendarDraftKind = MeasurementKind.WEIGHT
        weightViewModel?.onAction(action)
        if (calendarExit != null && weightViewModel?.state?.value?.draft != null) calendarDraftKind = MeasurementKind.WEIGHT
        if (closeDetail) calendarExit?.invoke()
    }
    val back: () -> Unit = {
        when {
            glucoseState?.draft != null ->
                glucoseViewModel.onAction(GlucoseAction.RequestClose)
            bloodPressureState?.draft != null ->
                bloodPressureViewModel.onAction(
                    BloodPressureAction.RequestClose,
                )
            weightState?.draft != null ->
                weightViewModel.onAction(WeightAction.RequestClose)
            weightState?.targetSheetVisible == true ->
                weightViewModel.onAction(WeightAction.DismissTarget)
            glucoseState?.selectedRecord != null ->
                dispatchGlucose(GlucoseAction.DismissRecord)
            bloodPressureState?.selectedRecord != null ->
                dispatchPressure(BloodPressureAction.DismissRecord)
            weightState?.selectedRecord != null ->
                dispatchWeight(WeightAction.DismissRecord)
            glucoseState?.selectedOccurrence != null ->
                dispatchGlucose(GlucoseAction.DismissOccurrence)
            bloodPressureState?.selectedOccurrence != null ->
                dispatchPressure(BloodPressureAction.DismissOccurrence)
            weightState?.selectedOccurrence != null ->
                dispatchWeight(WeightAction.DismissOccurrence)
            else -> {
                val previous = measurementBackDestination(
                    destination = destination,
                    helpFromDetail = helpFromDetail,
                )
                if (previous == null) {
                    onBack()
                } else {
                    navigateTo(previous)
                }
                if (
                    (destination == MeasurementDestination.HELP) &&
                    helpFromDetail
                ) {
                    helpFromDetail = false
                }
            }
        }
    }
    fun finishCalendarCapture(source: MeasurementKind, saved: Boolean) {
        when (calendarCaptureTransition(calendarDraftKind, source, saved)) {
            CalendarCaptureTransition.IGNORE -> Unit
            CalendarCaptureTransition.RESTORE_DETAIL -> { calendarDraftKind = null; calendarRestore?.invoke() }
            CalendarCaptureTransition.RETURN_TO_CALENDAR -> { calendarDraftKind = null; calendarExit?.invoke() }
        }
    }
    LaunchedEffect(glucoseViewModel) {
        glucoseViewModel?.effects?.collect { effect ->
            when (effect) {
                GlucoseEffect.FOLLOW_UP_SAVED -> navigateTo(MeasurementDestination.DETAIL)
                GlucoseEffect.SAVED, GlucoseEffect.UPDATED, GlucoseEffect.DELETED -> finishCalendarCapture(MeasurementKind.CAPILLARY_GLUCOSE, saved = true)
                GlucoseEffect.CLOSED -> finishCalendarCapture(MeasurementKind.CAPILLARY_GLUCOSE, saved = false)
                GlucoseEffect.NOT_PERFORMED_CONFIRMED -> Unit
            }
        }
    }
    LaunchedEffect(bloodPressureViewModel) {
        bloodPressureViewModel?.effects?.collect { effect ->
            when (effect) {
                BloodPressureEffect.FOLLOW_UP_SAVED -> navigateTo(MeasurementDestination.DETAIL)
                BloodPressureEffect.SAVED, BloodPressureEffect.UPDATED, BloodPressureEffect.DELETED -> finishCalendarCapture(MeasurementKind.BLOOD_PRESSURE, saved = true)
                BloodPressureEffect.CLOSED -> finishCalendarCapture(MeasurementKind.BLOOD_PRESSURE, saved = false)
                BloodPressureEffect.NOT_PERFORMED_CONFIRMED -> Unit
            }
        }
    }
    LaunchedEffect(weightViewModel) {
        weightViewModel?.effects?.collect { effect ->
            when (effect) {
                WeightEffect.FOLLOW_UP_SAVED -> navigateTo(MeasurementDestination.DETAIL)
                WeightEffect.SAVED, WeightEffect.UPDATED, WeightEffect.DELETED -> finishCalendarCapture(MeasurementKind.WEIGHT, saved = true)
                WeightEffect.CLOSED -> finishCalendarCapture(MeasurementKind.WEIGHT, saved = false)
                WeightEffect.NOT_PERFORMED_CONFIRMED -> Unit
            }
        }
    }
    BackHandler(onBack = back)
    LaunchedEffect(selectedKind) { detailScroll.scrollTo(0) }
    LaunchedEffect(destination) {
        if (
            (destination == MeasurementDestination.HELP) ||
            (destination == MeasurementDestination.GLUCOSE_HISTORY) ||
            (destination == MeasurementDestination.BLOOD_PRESSURE_HISTORY) ||
            (destination == MeasurementDestination.WEIGHT_HISTORY)
        ) {
            destinationScroll.scrollTo(0)
        }
    }

    val screenContent: @Composable (Modifier) -> Unit = { contentModifier ->
        if (destination == MeasurementDestination.HELP && bitavidaAiViewModel != null) {
            BitavidaAiRoute(
                viewModel = bitavidaAiViewModel,
                navigationContext = if (hasOpenedMeasurement || helpFromDetail) {
                    when (selectedKind) {
                        MeasurementKind.CAPILLARY_GLUCOSE -> EducationGroup.GLUCOSE
                        MeasurementKind.BLOOD_PRESSURE -> EducationGroup.PRESSURE
                        MeasurementKind.WEIGHT -> EducationGroup.WEIGHT
                    }
                } else null,
                modifier = contentModifier.padding(horizontal = BitavidaSpacing.ScreenHorizontal),
                onExit = back,
                exitLabel = if (helpFromDetail) selectedKind.label() else "Mediciones",
            )
        } else Column(
            modifier = contentModifier
                    .graphicsLayer {
                        translationX = navigationOffset.value * navigationTravel
                        alpha = navigationAlpha.value
                    }
                    .verticalScroll(scroll)
                    .padding(horizontal = BitavidaSpacing.ScreenHorizontal)
                    .padding(bottom = BitavidaSpacing.Large)
                    .testTag("measurements_screen"),
                verticalArrangement = Arrangement.spacedBy(
                    if (
                        (destination == MeasurementDestination.MAIN) ||
                        (destination == MeasurementDestination.DETAIL) ||
                        (destination == MeasurementDestination.HISTORY)
                    ) {
                        BitavidaSpacing.Default
                    } else {
                        BitavidaSpacing.Large
                    },
                ),
            ) {
                val guidedFollowUp =
                    (destination == MeasurementDestination.FOLLOW_UP) &&
                        !showFollowUpHistory &&
                        (
                            followUpChangeKind !=
                                GlucoseFollowUpChangeKind.REMOVED
                            )
                if (destination == MeasurementDestination.MAIN) {
                    CareSectionHeader(
                        title = "Mediciones",
                        subtitle = "Seguimiento de diabetes tipo 2",
                        person = personUnderCareDisplayName,
                        iconRes = R.drawable.ic_v2_droplet,
                        onBack = back,
                        motionAllowed = motionAllowed,
                    )
                } else if (!guidedFollowUp) Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    CurrentMedicationBack(
                        onBack = back,
                        motionAllowed = motionAllowed,
                        enabled = (glucoseState?.saving != true) &&
                            (bloodPressureState?.saving != true) &&
                            (weightState?.saving != true),
                        label = if (
                            (
                                destination == MeasurementDestination.FOLLOW_UP ||
                                    destination ==
                                    MeasurementDestination.GLUCOSE_HISTORY ||
                                    destination ==
                                    MeasurementDestination.BLOOD_PRESSURE_HISTORY ||
                                    destination ==
                                    MeasurementDestination.WEIGHT_HISTORY
                                ) ||
                            (
                                destination == MeasurementDestination.HELP &&
                                    helpFromDetail
                                )
                        ) {
                            selectedKind.label()
                        } else {
                            "Mediciones"
                        },
                    )
                    if (destination != MeasurementDestination.HELP) MeasurementHeader(
                        title = when (destination) {
                            MeasurementDestination.MAIN -> "Mediciones"
                            MeasurementDestination.DETAIL -> selectedKind.label()
                            MeasurementDestination.FOLLOW_UP -> "Configurar seguimiento"
                            MeasurementDestination.OTHER -> "Otras mediciones"
                            MeasurementDestination.HISTORY -> "Calendario e historial"
                            MeasurementDestination.GLUCOSE_HISTORY -> "Historial de glucosa"
                            MeasurementDestination.BLOOD_PRESSURE_HISTORY ->
                                "Historial de presión arterial"
                            MeasurementDestination.WEIGHT_HISTORY ->
                                "Historial de peso"
                            MeasurementDestination.HELP -> "Guías y ayuda"
                        },
                        person = personUnderCareDisplayName,
                        showSubtitle = false,
                        kind = if (destination == MeasurementDestination.DETAIL) selectedKind else null,
                        followUp = if (destination == MeasurementDestination.DETAIL) selected.followUp else null,
                    )
                }
                val measurementsLoading =
                    glucoseState?.loading == true ||
                        bloodPressureState?.loading == true ||
                        weightState?.loading == true
                val measurementsFailed =
                    glucoseState?.loadFailed == true ||
                        bloodPressureState?.loadFailed == true ||
                        weightState?.loadFailed == true
                if ((measurementsLoading || measurementsFailed) && destination != MeasurementDestination.HELP) {
                    MeasurementPanel {
                        SectionTitle(
                            if (measurementsLoading) {
                                "Cargando mediciones…"
                            } else {
                                "No se pudieron cargar las mediciones"
                            },
                            small = true,
                        )
                        if (measurementsFailed) {
                            MeasurementButton(
                                "Reintentar",
                                {
                                    glucoseViewModel?.onAction(
                                        GlucoseAction.Reload,
                                    )
                                    bloodPressureViewModel?.onAction(
                                        BloodPressureAction.Reload,
                                    )
                                    weightViewModel?.onAction(WeightAction.Reload)
                                },
                            )
                        }
                    }
                } else when (destination) {
                    MeasurementDestination.MAIN -> MeasurementsHome(
                        overview = overview,
                        today = today,
                        onDetail = {
                            selectedKind = it
                            hasOpenedMeasurement = true
                            navigateTo(MeasurementDestination.DETAIL)
                        },
                        onHistory = { navigateTo(MeasurementDestination.HISTORY) },
                        onOther = { navigateTo(MeasurementDestination.OTHER) },
                    ) {
                        helpFromDetail = false
                        navigateTo(MeasurementDestination.HELP)
                    }
                    MeasurementDestination.DETAIL -> {
                        detailState.SaveableStateProvider(selectedKind.name) {
                            val register: ((MeasurementKind) -> Unit)? =
                                when (selectedKind) {
                                    MeasurementKind.CAPILLARY_GLUCOSE ->
                                        glucoseViewModel?.let { model ->
                                            {
                                                model.onAction(
                                                    GlucoseAction.Open(
                                                        selected.followUp,
                                                    ),
                                                )
                                            }
                                        } ?: onRegisterMeasurement
                                    MeasurementKind.BLOOD_PRESSURE ->
                                        bloodPressureViewModel?.let { model ->
                                            {
                                                model.onAction(
                                                    BloodPressureAction.Open(
                                                        selected.followUp,
                                                    ),
                                                )
                                            }
                                        } ?: onRegisterMeasurement
                                    MeasurementKind.WEIGHT ->
                                        weightViewModel?.let { model ->
                                            {
                                                model.onAction(
                                                    WeightAction.Open(
                                                        selected.followUp,
                                                    ),
                                                )
                                            }
                                        } ?: onRegisterMeasurement
                                }
                            MeasurementDetail(
                                summary = selected,
                                records = recordsByKind.getValue(selectedKind),
                                today = today,
                                onRegister = register,
                                onFollowUpAction = { kind, action ->
                                    val managed =
                                        (
                                            kind ==
                                                MeasurementKind.CAPILLARY_GLUCOSE &&
                                                glucoseViewModel != null
                                            ) ||
                                            (
                                                kind ==
                                                    MeasurementKind.BLOOD_PRESSURE &&
                                                    bloodPressureViewModel != null
                                                ) ||
                                            (
                                                kind == MeasurementKind.WEIGHT &&
                                                    weightViewModel != null
                                                )
                                    if (managed) {
                                        showFollowUpHistory =
                                            action ==
                                                MeasurementFollowUpAction.VIEW_CHANGES
                                        followUpChangeKind =
                                            when (action) {
                                                MeasurementFollowUpAction.CONFIGURE ->
                                                    GlucoseFollowUpChangeKind.CREATED
                                                MeasurementFollowUpAction.CORRECT ->
                                                    GlucoseFollowUpChangeKind.CORRECTED
                                                MeasurementFollowUpAction.CHANGE_INDICATIONS ->
                                                    GlucoseFollowUpChangeKind.INDICATION_CHANGED
                                                MeasurementFollowUpAction.VIEW_CHANGES ->
                                                    GlucoseFollowUpChangeKind.CORRECTED
                                                MeasurementFollowUpAction.SUSPEND ->
                                                    GlucoseFollowUpChangeKind.REMOVED
                                            }
                                        navigateTo(
                                            MeasurementDestination.FOLLOW_UP,
                                        )
                                    } else {
                                        onFollowUpAction?.invoke(kind, action)
                                    }
                                },
                                onHelp = {
                                    helpFromDetail = true
                                    navigateTo(MeasurementDestination.HELP)
                                },
                                onHistory = {
                                    navigateTo(
                                        when (selectedKind) {
                                            MeasurementKind.BLOOD_PRESSURE ->
                                                MeasurementDestination
                                                    .BLOOD_PRESSURE_HISTORY
                                            MeasurementKind.WEIGHT ->
                                                MeasurementDestination.WEIGHT_HISTORY
                                            else ->
                                                MeasurementDestination.GLUCOSE_HISTORY
                                        },
                                    )
                                },
                                glucoseFollowUp = glucoseState?.followUp,
                                glucoseDecisions = glucoseState
                                    ?.occurrenceDecisions.orEmpty(),
                                onRegisterOccurrence =
                                    glucoseViewModel?.let { model ->
                                        { occurrence ->
                                            model.onAction(
                                                GlucoseAction.Open(
                                                    glucoseState?.followUp
                                                        ?.toMeasurementFollowUp(),
                                                    occurrence.confirmed(),
                                                ),
                                            )
                                        }
                                    },
                                onNotPerformed =
                                    glucoseViewModel?.let { model ->
                                        { occurrence, reason ->
                                            model.onAction(
                                                GlucoseAction
                                                    .ConfirmNotPerformed(
                                                        occurrence,
                                                        reason,
                                                    ),
                                            )
                                        }
                                    },
                                bloodPressureFollowUp =
                                    bloodPressureState?.followUp,
                                bloodPressureDecisions =
                                    bloodPressureState
                                        ?.occurrenceDecisions.orEmpty(),
                                onRegisterBloodPressureOccurrence =
                                    bloodPressureViewModel?.let { model ->
                                        { occurrence ->
                                            model.onAction(
                                                BloodPressureAction.Open(
                                                    bloodPressureState
                                                        ?.followUp
                                                        ?.toMeasurementFollowUp(
                                                            MeasurementKind
                                                                .BLOOD_PRESSURE,
                                                        ),
                                                    occurrence
                                                        .confirmedBloodPressure(),
                                                ),
                                            )
                                        }
                                    },
                                onBloodPressureNotPerformed =
                                    bloodPressureViewModel?.let { model ->
                                        { occurrence, reason ->
                                            model.onAction(
                                                BloodPressureAction
                                                    .ConfirmNotPerformed(
                                                        occurrence,
                                                        reason,
                                                    ),
                                            )
                                        }
                                    },
                                weightFollowUp = weightState?.followUp,
                                weightDecisions =
                                    weightState?.occurrenceDecisions.orEmpty(),
                                onRegisterWeightOccurrence =
                                    weightViewModel?.let { model ->
                                        { occurrence ->
                                            model.onAction(
                                                WeightAction.Open(
                                                    weightState?.followUp
                                                        ?.toMeasurementFollowUp(
                                                            MeasurementKind.WEIGHT,
                                                        ),
                                                    occurrence.confirmedWeight(),
                                                ),
                                            )
                                        }
                                    },
                                onWeightNotPerformed =
                                    weightViewModel?.let { model ->
                                        { occurrence, reason ->
                                            model.onAction(
                                                WeightAction.ConfirmNotPerformed(
                                                    occurrence,
                                                    reason,
                                                ),
                                            )
                                        }
                                    },
                                weightTarget = weightState?.target,
                                onConfigureWeightTarget =
                                    weightViewModel?.let { model ->
                                        {
                                            model.onAction(
                                                WeightAction.OpenTarget,
                                            )
                                        }
                                    },
                            )
                        }
                    }
                    MeasurementDestination.FOLLOW_UP -> {
                        val followUp = when (selectedKind) {
                            MeasurementKind.CAPILLARY_GLUCOSE ->
                                glucoseState?.followUp
                            MeasurementKind.BLOOD_PRESSURE ->
                                bloodPressureState?.followUp
                            MeasurementKind.WEIGHT -> weightState?.followUp
                        }
                        val saving = when (selectedKind) {
                            MeasurementKind.CAPILLARY_GLUCOSE ->
                                glucoseState?.followUpSaving == true
                            MeasurementKind.BLOOD_PRESSURE ->
                                bloodPressureState?.followUpSaving == true
                            MeasurementKind.WEIGHT ->
                                weightState?.followUpSaving == true
                        }
                        val error = when (selectedKind) {
                            MeasurementKind.CAPILLARY_GLUCOSE ->
                                glucoseState?.followUpError
                            MeasurementKind.BLOOD_PRESSURE ->
                                bloodPressureState?.followUpError
                            MeasurementKind.WEIGHT -> weightState?.followUpError
                        }
                        val title = selectedKind.label()
                        val icon = when (selectedKind) {
                            MeasurementKind.CAPILLARY_GLUCOSE ->
                                R.drawable.ic_v2_droplet
                            MeasurementKind.BLOOD_PRESSURE ->
                                R.drawable.ic_measurement_pressure
                            MeasurementKind.WEIGHT ->
                                R.drawable.ic_measurement_weight
                        }
                        val contexts = when (selectedKind) {
                            MeasurementKind.CAPILLARY_GLUCOSE ->
                                glucoseMeasurementContexts
                            MeasurementKind.BLOOD_PRESSURE ->
                                bloodPressureMeasurementContexts
                            MeasurementKind.WEIGHT -> emptyList()
                        }
                        when {
                            showFollowUpHistory ->
                                GlucoseFollowUpHistoryContent(
                                    followUp = followUp,
                                    showMeasurementContext =
                                        selectedKind != MeasurementKind.WEIGHT,
                                )
                            followUp == null ||
                                followUpChangeKind !=
                                    GlucoseFollowUpChangeKind.REMOVED ->
                                GlucoseFollowUpContent(
                                    existing = followUp,
                                    person = personUnderCareDisplayName,
                                    today = today,
                                    saving = saving,
                                    error = error,
                                    changeKind = when {
                                        followUp == null ->
                                            GlucoseFollowUpChangeKind.CREATED
                                        followUpChangeKind ==
                                            GlucoseFollowUpChangeKind.CREATED ->
                                            GlucoseFollowUpChangeKind
                                                .INDICATION_CHANGED
                                        else -> followUpChangeKind
                                    },
                                    onBack = back,
                                    measurementTitle = title,
                                    measurementIcon = icon,
                                    contextOptions = contexts,
                                    showMeasurementContext =
                                        selectedKind != MeasurementKind.WEIGHT,
                                    scheduleRecommendation = if (
                                        selectedKind == MeasurementKind.WEIGHT
                                    ) {
                                        "Para comparar tus registros, procura " +
                                            "pesarte a una hora similar y con " +
                                            "la misma báscula."
                                    } else {
                                        null
                                    },
                                    onRequestNotificationPermission =
                                        onRequestNotificationPermission,
                                ) { draft ->
                                    when (selectedKind) {
                                        MeasurementKind.CAPILLARY_GLUCOSE ->
                                            glucoseViewModel?.onAction(
                                                GlucoseAction.SaveFollowUp(draft),
                                            )
                                        MeasurementKind.BLOOD_PRESSURE ->
                                            bloodPressureViewModel?.onAction(
                                                BloodPressureAction
                                                    .SaveFollowUp(draft),
                                            )
                                        MeasurementKind.WEIGHT ->
                                            weightViewModel?.onAction(
                                                WeightAction.SaveFollowUp(draft),
                                            )
                                    }
                                }
                            else -> GlucoseDeleteFollowUpContent(
                                saving = saving,
                                error = error,
                            ) {
                                when (selectedKind) {
                                    MeasurementKind.CAPILLARY_GLUCOSE ->
                                        glucoseViewModel?.onAction(
                                            GlucoseAction.DeleteFollowUp(""),
                                        )
                                    MeasurementKind.BLOOD_PRESSURE ->
                                        bloodPressureViewModel?.onAction(
                                            BloodPressureAction
                                                .DeleteFollowUp(""),
                                        )
                                    MeasurementKind.WEIGHT ->
                                        weightViewModel?.onAction(
                                            WeightAction.DeleteFollowUp(""),
                                        )
                                }
                            }
                        }
                    }
                    MeasurementDestination.OTHER -> OtherMeasurementsComingSoon()
                    MeasurementDestination.GLUCOSE_HISTORY -> GlucoseHistoryContent(
                        records = recordsByKind.getValue(
                            MeasurementKind.CAPILLARY_GLUCOSE,
                        ),
                        today = today,
                        followUp = glucoseState?.followUp,
                        decisions = glucoseState?.occurrenceDecisions.orEmpty(),
                        currentMoment = LocalDateTime.now(),
                        onRecord = if (glucoseViewModel == null) {
                            null
                        } else {
                            { (recordId) ->
                                glucoseViewModel.onAction(
                                    GlucoseAction.OpenRecord(recordId),
                                )
                            }
                        },
                        onOccurrence = if (glucoseViewModel == null) {
                            null
                        } else {
                            { occurrence ->
                                glucoseViewModel.onAction(
                                    GlucoseAction.OpenOccurrence(occurrence),
                                )
                            }
                        },
                    )
                    MeasurementDestination.BLOOD_PRESSURE_HISTORY ->
                        BloodPressureHistoryContent(
                            records = recordsByKind.getValue(
                                MeasurementKind.BLOOD_PRESSURE,
                            ),
                            today = today,
                            followUp =
                                bloodPressureState?.followUp,
                            decisions = bloodPressureState
                                ?.occurrenceDecisions.orEmpty(),
                            now = LocalDateTime.now(),
                            onRecord =
                                bloodPressureViewModel?.let { model ->
                                    { (recordId) ->
                                        model.onAction(
                                            BloodPressureAction
                                                .OpenRecord(
                                                    recordId,
                                                ),
                                        )
                                    }
                                },
                            onOccurrence =
                                bloodPressureViewModel?.let { model ->
                                    { occurrence ->
                                        model.onAction(
                                            BloodPressureAction
                                                .OpenOccurrence(
                                                    occurrence,
                                                ),
                                        )
                                    }
                                },
                        )
                    MeasurementDestination.WEIGHT_HISTORY ->
                        GlucoseHistoryContent(
                            records = recordsByKind.getValue(
                                MeasurementKind.WEIGHT,
                            ),
                            today = today,
                            followUp = weightState?.followUp,
                            decisions =
                                weightState?.occurrenceDecisions.orEmpty(),
                            currentMoment = LocalDateTime.now(),
                            onRecord = weightViewModel?.let { model ->
                                { (recordId) ->
                                    model.onAction(
                                        WeightAction.OpenRecord(recordId),
                                    )
                                }
                            },
                            onOccurrence = weightViewModel?.let { model ->
                                { occurrence ->
                                    model.onAction(
                                        WeightAction.OpenOccurrence(occurrence),
                                    )
                                }
                            },
                            measurementKind = MeasurementKind.WEIGHT,
                            unit = "kg",
                            measurementNoun = "peso",
                            referenceValue =
                                weightState?.target?.kilograms,
                            referenceLabel = "Objetivo actual",
                        )
                    MeasurementDestination.HISTORY -> {
                        detailState.SaveableStateProvider("calendar-history") {
                            MeasurementCalendarContent(
                                records = displayRecords,
                                schedules = listOf(
                                    MeasurementScheduleHistory(
                                        MeasurementKind.CAPILLARY_GLUCOSE,
                                        glucoseState?.followUp,
                                        glucoseState?.occurrenceDecisions.orEmpty(),
                                    ),
                                    MeasurementScheduleHistory(
                                        MeasurementKind.BLOOD_PRESSURE,
                                        bloodPressureState?.followUp,
                                        bloodPressureState?.occurrenceDecisions.orEmpty(),
                                    ),
                                    MeasurementScheduleHistory(
                                        MeasurementKind.WEIGHT,
                                        weightState?.followUp,
                                        weightState?.occurrenceDecisions.orEmpty(),
                                    ),
                                ),
                                today = today,
                                enabledSecondary = overview.secondary.map { it.kind },
                                actionableKinds = buildSet {
                                    if (glucoseViewModel != null) add(MeasurementKind.CAPILLARY_GLUCOSE)
                                    if (bloodPressureViewModel != null) add(MeasurementKind.BLOOD_PRESSURE)
                                    if (weightViewModel != null) add(MeasurementKind.WEIGHT)
                                },
                                onEvent = { event ->
                                    val record = event.record
                                    val occurrence = event.occurrence
                                    when (event.kind) {
                                        MeasurementKind.CAPILLARY_GLUCOSE -> when {
                                            record != null -> glucoseViewModel?.onAction(
                                                GlucoseAction.OpenRecord(record.id),
                                            )
                                            occurrence != null -> glucoseViewModel?.onAction(
                                                GlucoseAction.OpenOccurrence(occurrence),
                                            )
                                        }
                                        MeasurementKind.BLOOD_PRESSURE -> when {
                                            record != null -> bloodPressureViewModel?.onAction(
                                                BloodPressureAction.OpenRecord(record.id),
                                            )
                                            occurrence != null -> bloodPressureViewModel?.onAction(
                                                BloodPressureAction.OpenOccurrence(occurrence),
                                            )
                                        }
                                        MeasurementKind.WEIGHT -> when {
                                            record != null -> weightViewModel?.onAction(
                                                WeightAction.OpenRecord(record.id),
                                            )
                                            occurrence != null -> weightViewModel?.onAction(
                                                WeightAction.OpenOccurrence(occurrence),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    MeasurementDestination.HELP -> InformationCard(
                        "Bitavida IA",
                        "La biblioteca educativa no está disponible en esta vista.",
                    )
                }
            }
    }

    if (embeddedInPlanScaffold) {
        screenContent(modifier.fillMaxSize())
    } else {
        BitavidaFluidBackground(
            modifier = modifier.fillMaxSize(),
            scrollPixels = { scroll.value.toFloat() },
            motionAllowed = motionAllowed,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    val items = listOf(
                        BitavidaNavigationItem(
                            stringResource(R.string.nav_today),
                            R.drawable.ic_v2_home,
                        ),
                        BitavidaNavigationItem(
                            stringResource(R.string.nav_plan),
                            R.drawable.ic_v2_clipboard,
                        ),
                        BitavidaNavigationItem(
                            stringResource(R.string.nav_appointments),
                            R.drawable.ic_v2_calendar,
                        ),
                        BitavidaNavigationItem(
                            stringResource(R.string.nav_report),
                            R.drawable.ic_v2_chart,
                        ),
                    )
                    BitavidaThreadNavigation(
                        items = items,
                        selectedIndex = 1,
                        currentSectionLabel =
                            stringResource(R.string.nav_current_section, items[1].label),
                        onSelected = {
                            when (it) {
                                0 -> onTodaySelected()
                                1 -> onBack()
                            }
                        },
                    )
                },
            ) { padding ->
                screenContent(
                    Modifier.fillMaxSize().padding(padding)
                        .then(if (destination == MeasurementDestination.HELP) Modifier.imePadding() else Modifier),
                )
            }
            MeasurementSheets(
                glucoseState = glucoseState,
                glucoseViewModel = glucoseViewModel,
                bloodPressureState = bloodPressureState,
                bloodPressureViewModel = bloodPressureViewModel,
                weightState = weightState,
                weightViewModel = weightViewModel,
                motionAllowed = motionAllowed,
                onGlucoseAction = dispatchGlucose, onPressureAction = dispatchPressure, onWeightAction = dispatchWeight,
            )
        }
    }
    if (embeddedInPlanScaffold) {
        MeasurementSheets(
            glucoseState = glucoseState,
            glucoseViewModel = glucoseViewModel,
            bloodPressureState = bloodPressureState,
            bloodPressureViewModel = bloodPressureViewModel,
            weightState = weightState,
            weightViewModel = weightViewModel,
            motionAllowed = motionAllowed,
            onGlucoseAction = dispatchGlucose, onPressureAction = dispatchPressure, onWeightAction = dispatchWeight,
        )
    }
}

@Composable
private fun MeasurementSheets(
    glucoseState: GlucoseUiState?,
    glucoseViewModel: GlucoseViewModel?,
    bloodPressureState: BloodPressureUiState?,
    bloodPressureViewModel: BloodPressureViewModel?,
    weightState: WeightUiState?,
    weightViewModel: WeightViewModel?,
    motionAllowed: Boolean,
    onGlucoseAction: (GlucoseAction) -> Unit,
    onPressureAction: (BloodPressureAction) -> Unit,
    onWeightAction: (WeightAction) -> Unit,
) {
    when {
        glucoseState?.draft != null && glucoseViewModel != null ->
            GlucoseRegistrationSheet(
                glucoseState,
                motionAllowed,
                onGlucoseAction,
            )
        bloodPressureState?.draft != null &&
            bloodPressureViewModel != null ->
            BloodPressureRegistrationSheet(
                bloodPressureState,
                motionAllowed,
                onPressureAction,
            )
        weightState?.draft != null && weightViewModel != null ->
            WeightRegistrationSheet(
                weightState,
                motionAllowed,
                onWeightAction,
            )
        weightState?.targetSheetVisible == true &&
            weightViewModel != null ->
            WeightTargetSheet(
                weightState,
                motionAllowed,
                onWeightAction,
            )
        glucoseState?.selectedRecord != null &&
            glucoseViewModel != null ->
            GlucoseRecordManagementSheet(
                glucoseState,
                motionAllowed,
                onGlucoseAction,
            )
        bloodPressureState?.selectedRecord != null &&
            bloodPressureViewModel != null ->
            BloodPressureRecordManagementSheet(
                bloodPressureState,
                motionAllowed,
                onPressureAction,
            )
        weightState?.selectedRecord != null && weightViewModel != null ->
            WeightRecordManagementSheet(
                weightState,
                motionAllowed,
                onWeightAction,
            )
        glucoseState?.selectedOccurrence != null &&
            glucoseViewModel != null ->
            GlucoseOccurrenceManagementSheet(
                glucoseState,
                motionAllowed,
                onGlucoseAction,
            )
        bloodPressureState?.selectedOccurrence != null &&
            bloodPressureViewModel != null ->
            BloodPressureOccurrenceManagementSheet(
                bloodPressureState,
                motionAllowed,
                onPressureAction,
            )
        weightState?.selectedOccurrence != null && weightViewModel != null ->
            WeightOccurrenceManagementSheet(
                weightState,
                motionAllowed,
                onWeightAction,
            )
    }
}

@Composable
internal fun MeasurementHeader(
    title: String,
    person: String,
    showSubtitle: Boolean,
    kind: MeasurementKind? = null,
    followUp: MeasurementFollowUp? = null,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val text: @Composable ColumnScope.() -> Unit = {
        val titleContent: @Composable () -> Unit = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (
            kind == MeasurementKind.CAPILLARY_GLUCOSE ||
            kind == MeasurementKind.BLOOD_PRESSURE
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                titleContent()
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (followUp == null) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.bitavidaColors.paleBlue,
                ) {
                    Text(
                        text = when {
                            followUp == null -> "Sin seguimiento"
                            followUp.suspended -> "Suspendido"
                            else -> "En seguimiento"
                        },
                        modifier = Modifier.padding(
                            horizontal = BitavidaSpacing.Small,
                            vertical = BitavidaSpacing.ExtraSmall,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else titleContent()
        if (showSubtitle) {
            Text(
                "Seguimiento de diabetes tipo 2",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Acompañando a " + person.trim().substringBefore(' '),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            MeasurementBadge(kind ?: MeasurementKind.CAPILLARY_GLUCOSE)
            text()
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeasurementBadge(kind ?: MeasurementKind.CAPILLARY_GLUCOSE)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                content = text,
            )
        }
    }
}
