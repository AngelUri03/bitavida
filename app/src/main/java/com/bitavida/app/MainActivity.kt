package com.bitavida.app

import android.Manifest
import kotlinx.coroutines.ensureActive
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bitavida.app.data.access.AppProtectionPreferences
import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.usecase.AskBitavidaAi
import com.bitavida.app.ui.education.BitavidaAiViewModel
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.data.medical.EncryptedMedicalCareRepository
import com.bitavida.app.data.measurements.EncryptedBloodPressureRepository
import com.bitavida.app.data.measurements.EncryptedGlucoseRepository
import com.bitavida.app.data.measurements.EncryptedWeightRepository
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.ui.measurements.BloodPressureViewModel
import com.bitavida.app.ui.measurements.GlucoseViewModel
import com.bitavida.app.ui.measurements.WeightViewModel
import com.bitavida.app.domain.access.AppAccessSessionState
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.domain.access.LocalAuthenticationError
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.model.MedicationReminderRuntimeState
import com.bitavida.app.platform.access.AndroidLocalAuthenticator
import com.bitavida.app.platform.reminder.MedicationReminderScheduler
import com.bitavida.app.platform.reminder.CareReminderScheduler
import com.bitavida.app.ui.access.AppAccessNotice
import com.bitavida.app.ui.access.AppAccessViewModel
import com.bitavida.app.ui.access.AppUnlockScreen
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.careprofile.CareProfileEffect
import com.bitavida.app.ui.careprofile.CareProfileFlowRoute
import com.bitavida.app.ui.careprofile.CareProfileRestrictionDialog
import com.bitavida.app.ui.careprofile.CareProfileStartupScreen
import com.bitavida.app.ui.careprofile.CareProfileStartupState
import com.bitavida.app.ui.careprofile.CareProfileViewModel
import com.bitavida.app.ui.components.BitavidaDateTimePickerHost
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.demo.*
import com.bitavida.app.ui.medical.MedicalPlanAction
import com.bitavida.app.ui.medical.MedicalPlanRoute
import com.bitavida.app.ui.medical.MedicalPlanViewModel
import com.bitavida.app.ui.observations.observationProjectionNow
import com.bitavida.app.ui.theme.BitavidaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private val appAccessViewModel: AppAccessViewModel by viewModels()
    private val localAi: AskBitavidaAi by lazy {
        val modelResources = applicationContext.resources
        AskBitavidaAi(BundledEducationRepository(
            loadModelText = {
                modelResources.openRawResource(R.raw.bitavida_education_model)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            },
            loadReminderModelText = {
                modelResources.openRawResource(R.raw.bitavida_reminder_model)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            },
        ))
    }
    private val bitavidaAiViewModel: BitavidaAiViewModel by viewModels {
        BitavidaAiViewModel.factory(localAi)
    }

    private lateinit var careProfileViewModel: CareProfileViewModel
    private var medicalPlanViewModel: MedicalPlanViewModel? = null
    private var glucoseViewModel: GlucoseViewModel? = null
    private var bloodPressureViewModel: BloodPressureViewModel? = null
    private var weightViewModel: WeightViewModel? = null
    private var observationsViewModel: com.bitavida.app.ui.observations.ObservationsViewModel? = null
    private var remindersViewModel: com.bitavida.app.ui.reminders.RemindersViewModel? = null
    private var notesViewModel: com.bitavida.app.ui.notes.NotesViewModel? = null
    private lateinit var localAuthenticator: AndroidLocalAuthenticator
    private lateinit var appProtectionPreferences: AppProtectionPreferences
    private var protectionEnabled by mutableStateOf(false)
    private var biometricNotice by mutableStateOf<String?>(null)
    private var reminderAccessRevision by mutableIntStateOf(0)
    private var notificationPermissionRequested = false

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1042
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        careProfileViewModel = ViewModelProvider(
            owner = this,
            factory = CareProfileViewModel.factory(
                EncryptedCareProfileRepository(applicationContext),
            ),
        )[CareProfileViewModel::class.java]
        appProtectionPreferences = AppProtectionPreferences(applicationContext)
        // Load and migrate the single policy before composing or launching any prompt.
        protectionEnabled = appProtectionPreferences.loadProtectionEnabled()
        // A callback cannot survive process death without its retained controller.
        // Keep requests retained across Activity recreation; discard only an orphaned token.
        val retainedPreferenceId = appAccessViewModel.preferenceRequest.value?.id
            ?: appAccessViewModel.recoveryRequest.value
        appProtectionPreferences.clearAbandonedPreference(retainedPreferenceId)
        appAccessViewModel.setProtectionEnabled(protectionEnabled)
        if (protectionEnabled && !appAccessViewModel.isProtectionActive) {
            appAccessViewModel.startProtectedSession()
        }
        localAuthenticator = AndroidLocalAuthenticator(
            activity = this,
            callback = object : AndroidLocalAuthenticator.Callback {
                override fun pendingPreferenceRequestId(): String? =
                    appProtectionPreferences.pendingPreferenceRequestId

                override fun isEntryAuthenticationExpected(): Boolean =
                    appAccessViewModel.isEntryAuthenticationExpected

                override fun onAuthenticationSucceeded() = appAccessViewModel.onAuthenticationSucceeded()
                override fun onAuthenticationAttemptRejected() = appAccessViewModel.onAuthenticationAttemptRejected()
                override fun onAuthenticationError(error: LocalAuthenticationError) = appAccessViewModel.onAuthenticationError(error)

                override fun onPreferenceAuthenticationSucceeded(id: String, method: com.bitavida.app.domain.access.PreferenceVerificationMethod) =
                    finishBiometricPreferenceChange(id, method, true)

                override fun onPreferenceAuthenticationAttemptRejected(id: String) {
                    if (appAccessViewModel.preferenceRequest.value?.id == id) {
                        biometricNotice = "La verificación no coincidió. Inténtalo de nuevo."
                    }
                }

                override fun onPreferenceAuthenticationError(id: String, method: com.bitavida.app.domain.access.PreferenceVerificationMethod, error: LocalAuthenticationError) =
                    finishBiometricPreferenceChange(id, method, false)
            },
        )

        appAccessViewModel.updateAvailability(localAuthenticator.availability())

        setContent {
            BitavidaTheme {
                BitavidaDateTimePickerHost {
                // Retirement is OS-only and also runs while protected content is locked.
                LaunchedEffect(reminderAccessRevision) {
                    try {
                        withContext(Dispatchers.IO) {
                            CareReminderScheduler(applicationContext).cancelExistingNotices()
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Retry unfinished legacy cancellation on the next resume.
                    }
                }
                val accessState by appAccessViewModel.uiState.collectAsState()
                val biometricRequest by appAccessViewModel.preferenceRequest.collectAsState()
                val biometricRecovery by appAccessViewModel.recoveryRequest.collectAsState()
                val startupState by careProfileViewModel.startupState.collectAsState()
                val onboardingVisible by
                    careProfileViewModel.onboardingVisible.collectAsState()
                val motionAllowed = rememberBitavidaMotionAllowed(override = null)
                val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
                var restrictionDialogDismissed by rememberSaveable {
                    mutableStateOf(false)
                }
                var protectedContentDestination by rememberSaveable {
                    mutableStateOf(ProtectedContentDestination.TODAY)
                }
                val reminderScheduler = remember {
                    MedicationReminderScheduler(applicationContext)
                }
                var reminderRuntimeState by rememberSaveable {
                    mutableStateOf(MedicationReminderRuntimeState.DISABLED)
                }
                val reminderSettingsLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                    ) {
                        reminderAccessRevision++
                    }
                val requestReminderAccess: () -> Unit = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val notificationPermission =
                            Manifest.permission.POST_NOTIFICATIONS
                        if (
                            checkSelfPermission(notificationPermission) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            val permanentlyDenied =
                                notificationPermissionRequested &&
                                    !ActivityCompat
                                        .shouldShowRequestPermissionRationale(
                                            this@MainActivity,
                                            notificationPermission,
                                        )
                            if (permanentlyDenied) {
                                reminderSettingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                    ).putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        packageName,
                                    ),
                                )
                            } else {
                                notificationPermissionRequested = true
                                ActivityCompat.requestPermissions(
                                    this@MainActivity,
                                    arrayOf(notificationPermission),
                                    NOTIFICATION_PERMISSION_REQUEST_CODE,
                                )
                            }
                        } else if (
                            !getSystemService(NotificationManager::class.java)
                                .areNotificationsEnabled()
                        ) {
                            reminderSettingsLauncher.launch(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                            )
                        } else {
                            reminderAccessRevision++
                        }
                    } else if (
                        !getSystemService(NotificationManager::class.java)
                            .areNotificationsEnabled()
                    ) {
                        reminderSettingsLauncher.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                        )
                    } else {
                        reminderAccessRevision++
                    }
                }

                LaunchedEffect(
                    protectionEnabled,
                    accessState.sessionState,
                    accessState.notice,
                ) {
                    val shouldRequestAuthentication =
                        protectionEnabled &&
                            appAccessViewModel.isProtectionActive &&
                            accessState.sessionState == AppAccessSessionState.LOCKED &&
                            (
                                accessState.notice == null ||
                                    accessState.notice == AppAccessNotice.SESSION_EXPIRED
                                )
                    if (shouldRequestAuthentication) requestAuthentication()
                }

                val accessRequired =
                    protectionEnabled &&
                        appAccessViewModel.isProtectionActive &&
                        accessState.sessionState != AppAccessSessionState.UNLOCKED
                val destination = when {
                    accessRequired -> MainDestination.ACCESS
                    startupState is CareProfileStartupState.Loading ||
                        startupState is CareProfileStartupState.Error ->
                        MainDestination.STARTUP
                    startupState is CareProfileStartupState.NoProfile ||
                        onboardingVisible ->
                        MainDestination.ONBOARDING
                    else -> MainDestination.PROTECTED_CONTENT
                }
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        (
                            fadeIn(tween(duration)) +
                                slideInHorizontally(tween(duration)) { it / 24 }
                            ).togetherWith(
                            fadeOut(tween(duration / 2)) +
                                slideOutHorizontally(tween(duration)) { -it / 28 },
                        )
                    },
                    label = "main_secure_destination",
                ) { target ->
                    when (target) {
                        MainDestination.ACCESS ->
                            AppUnlockScreen(
                                state = accessState,
                                onUnlock = ::requestAuthentication,
                                onOpenSecuritySetup =
                                    localAuthenticator::openSecuritySetup,
                            )
                        MainDestination.STARTUP ->
                            CareProfileStartupScreen(
                                state = startupState,
                                onRetry = careProfileViewModel::retryLoading,
                            )
                        MainDestination.ONBOARDING ->
                            CareProfileFlowRoute(
                                viewModel = careProfileViewModel,
                                onExit = { effect ->
                                    when (effect) {
                                        CareProfileEffect.Cancelled -> finish()
                                        CareProfileEffect.Completed -> {
                                            if (
                                                protectionEnabled &&
                                                !appAccessViewModel.isProtectionActive
                                            ) {
                                                appAccessViewModel
                                                    .startProtectedSession()
                                            }
                                            careProfileViewModel
                                                .finishOnboardingNavigation()
                                        }
                                    }
                                },
                                protectionEnabled = protectionEnabled,
                                protectionAvailability = accessState.availability,
                                onProtectionEnabledChange =
                                    ::setProtectionPreference,
                                onOpenSecuritySetup =
                                    localAuthenticator::openSecuritySetup,
                            )
                        MainDestination.PROTECTED_CONTENT -> {
                            val ready = startupState as? CareProfileStartupState.Ready
                            if (ready != null) {
                                val planViewModel = medicalPlanViewModel(ready.profile)
                                val glucoseViewModel =
                                    glucoseViewModel(ready.profile)
                                val bloodPressureViewModel =
                                    bloodPressureViewModel(ready.profile)
                                val weightViewModel =
                                    weightViewModel(ready.profile)
                                val observationsViewModel = observationsViewModel(ready.profile)
                                val remindersViewModel = remindersViewModel(ready.profile)
                                val notesViewModel = notesViewModel(ready.profile)
                                val glucoseState by glucoseViewModel.state.collectAsState()
                                val bloodPressureState by bloodPressureViewModel.state.collectAsState()
                                val weightState by weightViewModel.state.collectAsState()
                                val remindersState by remindersViewModel.state.collectAsState()
                                val observationState by observationsViewModel.state.collectAsState()
                                val notesState by notesViewModel.state.collectAsState()
                                val planState by planViewModel.state.collectAsState()
                                val todayMedicationFeedback = rememberTodayMedicationFeedback(planState.todayMedicationFeedback) { requestId ->
                                    planViewModel.onAction(MedicalPlanAction.DismissTodayMedicationFeedback(requestId))
                                }
                                com.bitavida.app.ui.observations.ObservationDraftPhotoCleanup(observationsViewModel)
                                val clockSnapshot = listOf(
                                    planState.overview,
                                    glucoseState.followUp, glucoseState.records, glucoseState.occurrenceDecisions,
                                    bloodPressureState.followUp, bloodPressureState.records, bloodPressureState.occurrenceDecisions,
                                    weightState.followUp, weightState.records, weightState.occurrenceDecisions,
                                    observationState.snapshot, observationState.snapshotObservedAt, remindersState.records,
                                )
                                val projectDay: (java.time.LocalDateTime) -> TodayCareProjection = { moment ->
                                    projectTodayCare(
                                        overview = planState.overview,
                                        glucoseFollowUp = glucoseState.followUp,
                                        glucoseRecords = glucoseState.records,
                                        glucoseDecisions = glucoseState.occurrenceDecisions,
                                        bloodPressureFollowUp = bloodPressureState.followUp,
                                        bloodPressureRecords = bloodPressureState.records,
                                        bloodPressureDecisions = bloodPressureState.occurrenceDecisions,
                                        weightFollowUp = weightState.followUp,
                                        weightRecords = weightState.records,
                                        weightDecisions = weightState.occurrenceDecisions,
                                        observations = observationState.snapshot,
                                        reminders = remindersState.records,
                                        personUnderCareId = ready.profile.personUnderCare.id,
                                        now = moment,
                                        observationNow = observationState.observationProjectionNow(moment),
                                    )
                                }
                                val clockZone = java.time.ZoneId.systemDefault()
                                val lifecycleBoundaries = remember(planState.overview.medicationPlans, clockZone) {
                                    planState.overview.medicationPlans.flatMap { it.revisions }.mapNotNull {
                                        it.lifecycleEffectiveAtEpochMillis?.let { epoch ->
                                            java.time.LocalDateTime.ofInstant(
                                                java.time.Instant.ofEpochMilli(epoch), clockZone)
                                        }
                                    }
                                }
                                val careNow by rememberTodayCareClock(clockSnapshot, lifecycleBoundaries, projectDay)
                                val todayProjection = remember(clockSnapshot, careNow) { projectDay(careNow) }
                                // Null follows the real current day across midnight; another date is a saved consultation.
                                var selectedAgendaDateIso by rememberSaveable(ready.profile.id) { mutableStateOf<String?>(null) }
                                var careCalendarOpen by rememberSaveable(ready.profile.id) { mutableStateOf(false) }
                                var calendarMeasurementKey by rememberSaveable(ready.profile.id) { mutableStateOf<String?>(null) }
                                val calendarStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                                var centralAiOpen by rememberSaveable(ready.profile.id) { mutableStateOf(false) }
                                var settingsOpen by rememberSaveable(ready.profile.id) { mutableStateOf(false) }
                                var settingsSection by rememberSaveable(ready.profile.id) { mutableStateOf("menu") }
                                var reportOpen by rememberSaveable(ready.profile.id) { mutableStateOf(false) }
                                var appointmentsOpen by rememberSaveable(ready.profile.id) { mutableStateOf(false) }
                                val reportSession = remember(ready.profile.id) { com.bitavida.app.ui.report.FollowUpReportSession() }
                                val careEventsViewModel = remember(ready.profile.id) {
                                    val profile = ready.profile
                                    ViewModelProvider(this@MainActivity,
                                        com.bitavida.app.ui.appointments.CareEventsViewModel.factory(
                                            applicationContext,
                                            com.bitavida.app.domain.repository.CareEventActor(
                                                profile.id, profile.caregiver.id, profile.personUnderCare.id, profile.consent.isCurrent,
                                            ),
                                        )).get("care_events:" + profile.id,
                                        com.bitavida.app.ui.appointments.CareEventsViewModel::class.java)
                                }
                                val careEventsState by careEventsViewModel.state.collectAsState()
                                val notificationsAllowed = remember(reminderAccessRevision) {
                                    getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                                }
                                var careCalendarMonth by rememberSaveable(ready.profile.id) { mutableStateOf(java.time.YearMonth.from(careNow).toString()) }
                                val selectedAgendaDate = selectedAgendaDateIso?.let(java.time.LocalDate::parse)
                                    ?: careNow.toLocalDate()
                                val selectedAgendaIsToday = selectedAgendaDate == careNow.toLocalDate()
                                // Citas pertenece a su propio módulo y calendario, no a Mi día.
                                val scheduledCareEvents = emptyList<com.bitavida.app.domain.model.CareEvent>()
                                val calendarCareEvents = emptyList<com.bitavida.app.domain.model.CareEvent>()
                                val agendaProjection = remember(clockSnapshot, careNow, selectedAgendaDate) {
                                    if (selectedAgendaIsToday) todayProjection else projectDay(
                                        selectedAgendaDate.atTime(
                                            if (selectedAgendaDate < careNow.toLocalDate()) java.time.LocalTime.MAX
                                            else careNow.toLocalTime(),
                                        ),
                                    )
                                }
                                LaunchedEffect(careNow, remindersViewModel) {
                                    remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Tick(careNow))
                                }
                                val activeCounts = remember(
                                    planState.overview, glucoseState.followUp, bloodPressureState.followUp,
                                    weightState.followUp, observationState.snapshot, remindersState.records,
                                    careNow, ready.profile.personUnderCare.id,
                                ) {
                                    com.bitavida.app.ui.medical.countPlanActiveItems(
                                        overview = planState.overview,
                                        glucose = glucoseState.followUp,
                                        bloodPressure = bloodPressureState.followUp,
                                        weight = weightState.followUp,
                                        observations = observationState.snapshot,
                                        reminders = remindersState.records,
                                        personUnderCareId = ready.profile.personUnderCare.id,
                                        now = careNow,
                                    )
                                }
                                val todayListState = androidx.compose.foundation.lazy.rememberLazyListState()
                                var todayMedicationOptions by remember { mutableStateOf<TodayMedicationItem?>(null) }
                                LaunchedEffect(protectedContentDestination, todayProjection.medications, selectedAgendaDate) {
                                    if (protectedContentDestination != ProtectedContentDestination.TODAY || !selectedAgendaIsToday ||
                                        todayProjection.medications.none { it.id == todayMedicationOptions?.id }) {
                                        todayMedicationOptions = null
                                    }
                                }
                                var todayObservationCaptureActive by rememberSaveable { mutableStateOf(false) }
                                var remindersOpenedFromToday by rememberSaveable { mutableStateOf(false) }
                                LaunchedEffect(planState.screen) {
                                    if (planState.screen == com.bitavida.app.ui.medical.MedicalPlanScreen.OVERVIEW) {
                                        remindersOpenedFromToday = false
                                    }
                                }
                                LaunchedEffect(observationState.snapshot, observationState.loading,
                                    observationState.loadError, reminderAccessRevision) {
                                    if (!observationState.loading && observationState.loadError == null) {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                com.bitavida.app.platform.reminder.ObservationReminderScheduler(applicationContext)
                                                    .reconcileLatest(observationsViewModel.actor)
                                            }
                                        } catch (error: kotlinx.coroutines.CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            // Keep persisted activities available; the next lifecycle event retries alarms.
                                        }
                                    }
                                }
                                LaunchedEffect(
                                    planState.overview.medicationPlans,
                                    planState.isLoading,
                                    planState.globalError,
                                    reminderAccessRevision,
                                ) {
                                    if (!planState.isLoading && planState.globalError == null) {
                                        reminderRuntimeState = withContext(Dispatchers.Default) {
                                            reminderScheduler.reconcile(
                                                planState.overview.medicationPlans,
                                            ).state
                                        }
                                    }
                                }
                                val rootNavigationBusy = planState.isSaving || glucoseState.saving ||
                                    glucoseState.occurrenceDecisionSaving || bloodPressureState.saving ||
                                    bloodPressureState.occurrenceDecisionSaving || weightState.saving ||
                                    weightState.occurrenceDecisionSaving || observationState.saving ||
                                    remindersState.saving || notesState.saving
                                val rootNavigationItems = remember {
                                    listOf(
                                        com.bitavida.app.ui.components.BitavidaNavigationItem("Mi día", R.drawable.ic_v2_home),
                                        com.bitavida.app.ui.components.BitavidaNavigationItem("Plan", R.drawable.ic_v2_clipboard),
                                        com.bitavida.app.ui.components.BitavidaNavigationItem("Citas", R.drawable.ic_v2_calendar),
                                        com.bitavida.app.ui.components.BitavidaNavigationItem("Informe", R.drawable.ic_v2_chart),
                                    )
                                }
                                fun openRootTab(index: Int) {
                                    if (rootNavigationBusy || index !in 0..3) return
                                    if (careCalendarOpen) calendarStateHolder.removeState("care_calendar")
                                    calendarMeasurementKey = null
                                    careCalendarOpen = false
                                    centralAiOpen = false
                                    settingsOpen = false
                                    appointmentsOpen = index == 2
                                    reportOpen = index == 3
                                    selectedAgendaDateIso = null
                                    todayObservationCaptureActive = false
                                    todayMedicationOptions = null
                                    remindersOpenedFromToday = false
                                    if (index == 1) {
                                        planViewModel.onAction(MedicalPlanAction.OpenOverview)
                                        protectedContentDestination = ProtectedContentDestination.PLAN
                                    } else {
                                        protectedContentDestination = ProtectedContentDestination.TODAY
                                    }
                                }
                                fun openCalendarMeasurement(event: com.bitavida.app.ui.demo.CareCalendarEvent): Boolean {
                                    val ref = event.reference
                                    val kind = when (ref) {
                                        is com.bitavida.app.ui.demo.TodayMeasurementItem -> ref.kind
                                        is com.bitavida.app.ui.demo.TodayResolvedActivity -> ref.measurementKind
                                        else -> null
                                    } ?: return false
                                    val occurrence = when (ref) {
                                        is com.bitavida.app.ui.demo.TodayMeasurementItem -> ref.occurrence
                                        is com.bitavida.app.ui.demo.TodayResolvedActivity -> ref.measurementOccurrence
                                        else -> null
                                    }
                                    val recordId = (ref as? com.bitavida.app.ui.demo.TodayResolvedActivity)?.recordId
                                    return when (kind) {
                                        com.bitavida.app.domain.model.MeasurementKind.CAPILLARY_GLUCOSE -> {
                                            if (recordId != null) glucoseViewModel.onAction(com.bitavida.app.ui.measurements.GlucoseAction.OpenRecord(recordId))
                                            else if (occurrence != null) glucoseViewModel.onAction(com.bitavida.app.ui.measurements.GlucoseAction.OpenOccurrence(occurrence))
                                            (recordId != null && glucoseViewModel.state.value.selectedRecordId == recordId) ||
                                                (recordId == null && occurrence != null && glucoseViewModel.state.value.selectedOccurrence?.id == occurrence.id)
                                        }
                                        com.bitavida.app.domain.model.MeasurementKind.BLOOD_PRESSURE -> {
                                            if (recordId != null) bloodPressureViewModel.onAction(com.bitavida.app.ui.measurements.BloodPressureAction.OpenRecord(recordId))
                                            else if (occurrence != null) bloodPressureViewModel.onAction(com.bitavida.app.ui.measurements.BloodPressureAction.OpenOccurrence(occurrence))
                                            (recordId != null && bloodPressureViewModel.state.value.selectedRecordId == recordId) ||
                                                (recordId == null && occurrence != null && bloodPressureViewModel.state.value.selectedOccurrence?.id == occurrence.id)
                                        }
                                        com.bitavida.app.domain.model.MeasurementKind.WEIGHT -> {
                                            if (recordId != null) weightViewModel.onAction(com.bitavida.app.ui.measurements.WeightAction.OpenRecord(recordId))
                                            else if (occurrence != null) weightViewModel.onAction(com.bitavida.app.ui.measurements.WeightAction.OpenOccurrence(occurrence))
                                            (recordId != null && weightViewModel.state.value.selectedRecordId == recordId) ||
                                                (recordId == null && occurrence != null && weightViewModel.state.value.selectedOccurrence?.id == occurrence.id)
                                        }
                                    }
                                }
                                fun restoreCalendarMeasurement(): Boolean {
                                    val key = calendarMeasurementKey ?: return false
                                    val event = com.bitavida.app.ui.demo.careCalendarEvents(agendaProjection, careNow, calendarCareEvents).singleOrNull { it.key == key } ?: return false
                                    return openCalendarMeasurement(event)
                                }
                                AnimatedContent(
                                    targetState = protectedContentDestination,
                                    modifier = Modifier.background(
                                        MaterialTheme.colorScheme.background,
                                    ),
                                    transitionSpec = {
                                        val enteringPlan =
                                            targetState == ProtectedContentDestination.PLAN
                                        (
                                            fadeIn(tween(duration)) +
                                                slideInHorizontally(tween(duration)) {
                                                    if (enteringPlan) it / 8 else -it / 8
                                                }
                                            ).togetherWith(
                                            fadeOut(tween(duration / 2)) +
                                                slideOutHorizontally(tween(duration)) {
                                                    if (enteringPlan) -it / 12 else it / 12
                                                },
                                        )
                                    },
                                    label = "protected_content_destination",
                                ) { protectedTarget ->
                                    when (protectedTarget) {
                                        ProtectedContentDestination.TODAY -> TodayTypography {
                                            val busy = planState.isSaving || glucoseState.saving ||
                                                glucoseState.occurrenceDecisionSaving || bloodPressureState.saving ||
                                                bloodPressureState.occurrenceDecisionSaving || weightState.saving ||
                                                weightState.occurrenceDecisionSaving || observationState.saving ||
                                                remindersState.saving || notesState.saving
                                            val openPlanRoot: () -> Unit = {
                                                if (!busy) {
                                                    if (todayObservationCaptureActive) {
                                                        observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.CloseDraft)
                                                    }
                                                    todayObservationCaptureActive = false
                                                    planViewModel.onAction(MedicalPlanAction.OpenOverview)
                                                    protectedContentDestination = ProtectedContentDestination.PLAN
                                                }
                                            }
                                            if (todayObservationCaptureActive && observationState.draft != null) {
                                                com.bitavida.app.ui.observations.ObservationsRoute(
                                                    viewModel = observationsViewModel,
                                                    personName = ready.profile.personUnderCare.displayName,
                                                    onBack = {
                                                        observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.CloseDraft)
                                                        todayObservationCaptureActive = false
                                                    },
                                                    onTodaySelected = {
                                                        observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.CloseDraft)
                                                        todayObservationCaptureActive = false
                                                    },
                                                    motionAllowed = motionAllowed,
                                                    fromToday = true,
                                                    onPlanSelected = openPlanRoot,
                                                    onRequestNotificationPermission = requestReminderAccess,
                                                )
                                            } else if (settingsOpen) {
                                                val names by careProfileViewModel.namesState.collectAsState()
                                                AnimatedContent(targetState = names.open, transitionSpec = {
                                                    val forward = targetState
                                                    (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { if (forward) it / 10 else -it / 10 })
                                                        .togetherWith(fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { if (forward) -it / 10 else it / 10 })
                                                }, label = "datos personales") { editingNames ->
                                                if (editingNames) {
                                                    com.bitavida.app.ui.careprofile.CareProfileNamesScreen(
                                                        names, careProfileViewModel::changeCaregiverName,
                                                        careProfileViewModel::changePersonName, careProfileViewModel::saveNames,
                                                        careProfileViewModel::closeNamesEditor,
                                                    )
                                                } else {
                                                    com.bitavida.app.ui.settings.BitavidaSettingsScreen(
                                                        caregiver = ready.profile.caregiver.displayName,
                                                        patient = ready.profile.personUnderCare.displayName,
                                                        protectionEnabled = protectionEnabled,
                                                        notificationsAllowed = notificationsAllowed,
                                                        notificationChannels = listOf(
                                                            "medication_reminders_normal" to "Medicamentos",
                                                            "medication_reminders_prominent" to "Medicamentos destacados",
                                                            "glucose_measurements" to "Glucosa",
                                                            "blood_pressure_measurements" to "Presión arterial",
                                                            "weight_measurements" to "Peso",
                                                            "observation_reviews" to "Observaciones",
                                                            "appointments_and_studies" to "Citas y estudios",
                                                        ).filter { (id, _) ->
                                                            getSystemService(NotificationManager::class.java).getNotificationChannel(id) != null
                                                        },
                                                        onNotificationChannelSettings = { channelId ->
                                                            reminderSettingsLauncher.launch(
                                                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                                                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
                                                            )
                                                        },
                                                        onBack = { if (biometricRequest == null && biometricRecovery == null) settingsOpen = false },
                                                        onSystemNotificationSettings = {
                                                            reminderSettingsLauncher.launch(
                                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                                                        },
                                                        onEditNames = if (biometricRequest == null && biometricRecovery == null) careProfileViewModel::openNamesEditor else null,
                                                                                    biometricBusy = biometricRequest != null || biometricRecovery != null,
                                                        biometricMessage = biometricNotice,
                                                        onBiometricChange = ::startBiometricPreferenceChange,
                                                        section = settingsSection,
                                                        onSectionChange = { settingsSection = it },
                                                                                    onDebugReportDiagnostic = if (
                                                            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                                                            packageManager.resolveActivity(
                                                                Intent().setClassName(packageName, "com.bitavida.app.diagnostic.DebugReportDiagnosticActivity"), 0
                                                            ) != null &&
                                                            biometricRequest == null && biometricRecovery == null &&
                                                            accessState.sessionState == AppAccessSessionState.UNLOCKED
                                                        ) {
                                                            {
                                                                val deviceUnlocked = !getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked
                                                                if (appAccessViewModel.isUnlocked &&
                                                                    appAccessViewModel.preferenceRequest.value == null &&
                                                                    appAccessViewModel.recoveryRequest.value == null &&
                                                                    deviceUnlocked
                                                                ) {
                                                                    com.bitavida.app.ui.settings.DevelopmentDiagnosticGrant.issue()
                                                                    startActivity(Intent().setClassName(
                                                                        packageName, "com.bitavida.app.diagnostic.DebugReportDiagnosticActivity"
                                                                    ))
                                                                }
                                                            }
                                                        } else null,
                                                    )
                                                }
                                                }
                                            } else if (appointmentsOpen) {
                                                com.bitavida.app.ui.appointments.CareEventsRoute(
                                                    viewModel = careEventsViewModel,
                                                    aiViewModel = bitavidaAiViewModel,
                                                    person = ready.profile.personUnderCare.displayName,
                                                    onBack = { appointmentsOpen = false },
                                                    bottomBar = { allowed ->
                                                        com.bitavida.app.ui.components.TodayAgendaNavigation(
                                                            items = rootNavigationItems, selectedIndex = 2,
                                                            isEnabled = { allowed && !rootNavigationBusy },
                                                            onSelected = { if (allowed && it != 2) openRootTab(it) },
                                                        )
                                                    },
                                                )
                                            } else if (reportOpen) {
                                                androidx.compose.runtime.LaunchedEffect(careEventsState.events, careEventsState.loading, careEventsState.saving) {
                                                    if (!careEventsState.loading && !careEventsState.saving &&
                                                        careEventsState.error == null && careEventsState.reportHistory == null &&
                                                        !careEventsState.reportHistoryLoading && careEventsState.reportHistoryError == null) {
                                                        careEventsViewModel.onAction(com.bitavida.app.ui.appointments.CareEventsAction.LoadReportHistory)
                                                    }
                                                }
                                                com.bitavida.app.ui.report.FollowUpReportRoute(
                                                    caregiverName = ready.profile.caregiver.displayName,
                                                    patientName = ready.profile.personUnderCare.displayName,
                                                    personUnderCareId = ready.profile.personUnderCare.id,
                                                    overview = planState.overview,
                                                    measurements = glucoseState.records + bloodPressureState.records + weightState.records,
                                                    observations = observationState.snapshot.records,
                                                    observationPhoto = { photoId ->
                                                        com.bitavida.app.ui.observations.ObservationStoredPhoto(observationsViewModel, photoId)
                                                    },
                                                    loadObservationPhoto = { recordId, photoId ->
                                                        com.bitavida.app.data.observations.ObservationPhotoStore(applicationContext)
                                                            .readForReport(observationsViewModel.actor, recordId, photoId)
                                                    },
                                                    reminders = remindersState.records,
                                                    supplementaryRefreshKey = notesState.notes,
                                                    loadSupplementaryHistory = {
                                                        com.bitavida.app.data.medical.ReportSupplementaryHistoryReader(applicationContext)
                                                            .load(notesViewModel.actor, remindersViewModel.actor)
                                                    },
                                                    loadMeasurementHistories = {
                                                        val actor = observationsViewModel.actor
                                                        val readings = com.bitavida.app.data.measurements.MeasurementReportHistoryReader(applicationContext).load(actor)
                                                        val schedules = com.bitavida.app.data.measurements.MeasurementScheduleReportHistoryReader(applicationContext).load(actor)
                                                        readings to schedules
                                                    },
                                                    onOpenPlan = { openRootTab(1) },
                                                    measurementSchedules = listOf(
                                                        com.bitavida.app.domain.usecase.ReportMeasurementSchedule(
                                                            com.bitavida.app.domain.model.MeasurementKind.CAPILLARY_GLUCOSE,
                                                            glucoseState.followUp, glucoseState.occurrenceDecisions),
                                                        com.bitavida.app.domain.usecase.ReportMeasurementSchedule(
                                                            com.bitavida.app.domain.model.MeasurementKind.BLOOD_PRESSURE,
                                                            bloodPressureState.followUp, bloodPressureState.occurrenceDecisions),
                                                        com.bitavida.app.domain.usecase.ReportMeasurementSchedule(
                                                            com.bitavida.app.domain.model.MeasurementKind.WEIGHT,
                                                            weightState.followUp, weightState.occurrenceDecisions),
                                                    ),
                                                    observationFollowUps = observationState.snapshot.followUps,
                                                    observationRevisions = observationState.snapshot.recordRevisions,
                                                    currentWeightTarget = weightState.target,
                                                    session = reportSession,
                                                    bottomBar = { allowed ->
                                                        com.bitavida.app.ui.components.TodayAgendaNavigation(
                                                            items = rootNavigationItems, selectedIndex = 3,
                                                            isEnabled = { allowed && !rootNavigationBusy },
                                                            onSelected = { index ->
                                                                if (allowed && !rootNavigationBusy) {
                                                                    if (index == 3) reportSession.rootRequest++ else openRootTab(index)
                                                                }
                                                            },
                                                        )
                                                    },
                                                    onBack = { reportOpen = false },
                                                    events = careEventsState.events,
                                                    careEventHistory = careEventsState.reportHistory,
                                                    sourcesLoading = planState.isLoading || glucoseState.loading ||
                                                        bloodPressureState.loading || weightState.loading ||
                                                        observationState.loading || remindersState.loading || notesState.loading ||
                                                        careEventsState.loading || careEventsState.reportHistoryLoading ||
                                                        (careEventsState.reportHistory == null && careEventsState.reportHistoryError == null && careEventsState.error == null),
                                                    sourcesReady = !planState.isLoading && planState.globalError == null &&
                                                        !glucoseState.loading && !glucoseState.loadFailed &&
                                                        !bloodPressureState.loading && !bloodPressureState.loadFailed &&
                                                        !weightState.loading && !weightState.loadFailed &&
                                                        !observationState.loading && observationState.loadError == null &&
                                                        !remindersState.loading && !remindersState.loadFailed &&
                                                        !notesState.loading && !notesState.loadFailed &&
                                                        !careEventsState.loading && careEventsState.error == null &&
                                                        !careEventsState.reportHistoryLoading && careEventsState.reportHistoryError == null &&
                                                        careEventsState.reportHistory != null,
                                                    onRetrySources = {
                                                        planViewModel.onAction(MedicalPlanAction.Retry)
                                                        glucoseViewModel.onAction(com.bitavida.app.ui.measurements.GlucoseAction.Reload)
                                                        bloodPressureViewModel.onAction(com.bitavida.app.ui.measurements.BloodPressureAction.Reload)
                                                        weightViewModel.onAction(com.bitavida.app.ui.measurements.WeightAction.Reload)
                                                        observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.Reload)
                                                        remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Reload)
                                                        notesViewModel.onAction(com.bitavida.app.ui.notes.NotesAction.Reload)
                                                        careEventsViewModel.onAction(com.bitavida.app.ui.appointments.CareEventsAction.Reload)
                                                    },
                                                )
                                            } else if (careCalendarOpen) {
                                                val month = java.time.YearMonth.parse(careCalendarMonth)
                                                val markedDates by androidx.compose.runtime.produceState(
                                                    initialValue = emptySet<java.time.LocalDate>(),
                                                    key1 = month, key2 = listOf(clockSnapshot, careEventsState.events), key3 = careCalendarOpen,
                                                ) {
                                                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                                        (1..month.lengthOfMonth()).mapNotNull { day ->
                                                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                                            val date = month.atDay(day)
                                                            val projected = projectDay(date.atTime(java.time.LocalTime.MAX))
                                                            date.takeIf {
                                                                com.bitavida.app.ui.demo.careCalendarEvents(projected, careNow, emptyList(), date).isNotEmpty()
                                                            }
                                                        }.toSet()
                                                    }
                                                }
                                                calendarStateHolder.SaveableStateProvider("care_calendar") {
                                                com.bitavida.app.ui.demo.CareCalendarScreen(
                                                    selectedDate = selectedAgendaDate, now = careNow, projection = agendaProjection,
                                                    eventDates = markedDates, careEvents = calendarCareEvents,
                                                    onDateSelected = { selectedAgendaDateIso = it.takeUnless { day -> day == careNow.toLocalDate() }?.toString() },
                                                    onMonthChanged = { careCalendarMonth = it.toString() },
                                                    onBack = { careCalendarOpen = false; selectedAgendaDateIso = null; calendarStateHolder.removeState("care_calendar") },
                                                    bottomBar = {
                                                        com.bitavida.app.ui.components.TodayAgendaNavigation(rootNavigationItems, selectedIndex = 0,
                                                            isEnabled = { !rootNavigationBusy }, onSelected = { openRootTab(it) })
                                                    },
                                                    canOpenEvent = { event ->
                                                        !busy && when (val ref = event.reference) {
                                                            is com.bitavida.app.ui.demo.TodayMedicationItem ->
                                                                planState.overview.medicationPlans.any { it.id == ref.occurrence.medicationPlanId }
                                                            is com.bitavida.app.domain.model.CareReminder ->
                                                                remindersState.records.any { it.id == ref.id }
                                                            is com.bitavida.app.domain.model.CareEvent -> careEventsState.events.any { it.id == ref.id }
                                                            is com.bitavida.app.ui.demo.TodayMeasurementItem,
                                                            is com.bitavida.app.domain.model.ObservationOccurrence -> true
                                                            is com.bitavida.app.ui.demo.TodayResolvedActivity -> when (ref.kind) {
                                                                com.bitavida.app.ui.demo.TodayActivityKind.MEDICATION ->
                                                                    ref.sourceId != null && planState.overview.medicationPlans.any { it.id == ref.sourceId }
                                                                com.bitavida.app.ui.demo.TodayActivityKind.REMINDER ->
                                                                    remindersState.records.any { it.id == (ref.sourceId ?: ref.recordId ?: ref.id) }
                                                                com.bitavida.app.ui.demo.TodayActivityKind.MEASUREMENT -> (ref.recordId != null || ref.measurementOccurrence != null) && ref.measurementKind != null
                                                                com.bitavida.app.ui.demo.TodayActivityKind.OBSERVATION -> ref.recordId != null
                                                                com.bitavida.app.ui.demo.TodayActivityKind.CARE_EVENT -> false
                                                            }
                                                            else -> false
                                                        }
                                                    },
                                                    onOpenEvent = { event ->
                                                        when (val ref = event.reference) {
                                                            is com.bitavida.app.ui.demo.TodayMedicationItem -> {
                                                                planViewModel.onAction(MedicalPlanAction.OpenMedication(ref.occurrence.medicationPlanId))
                                                                protectedContentDestination = ProtectedContentDestination.CALENDAR_MEDICATION
                                                            }
                                                            is com.bitavida.app.domain.model.CareReminder ->
                                                                remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Open(ref.id))
                                                            is com.bitavida.app.domain.model.CareEvent -> {
                                                                careEventsViewModel.onAction(com.bitavida.app.ui.appointments.CareEventsAction.Open(ref.id))
                                                                if (careEventsViewModel.state.value.selected?.id == ref.id) protectedContentDestination = ProtectedContentDestination.CALENDAR_APPOINTMENT
                                                            }
                                                            is com.bitavida.app.ui.demo.TodayMeasurementItem -> {
                                                                if (openCalendarMeasurement(event)) {
                                                                    calendarMeasurementKey = event.key
                                                                    protectedContentDestination = ProtectedContentDestination.CALENDAR_MEASUREMENT
                                                                }
                                                            }
                                                            is com.bitavida.app.domain.model.ObservationOccurrence -> {
                                                                observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.OpenOccurrence(ref))
                                                                protectedContentDestination = ProtectedContentDestination.CALENDAR_OBSERVATION_OCCURRENCE
                                                            }
                                                            is com.bitavida.app.ui.demo.TodayResolvedActivity -> when (ref.kind) {
                                                                com.bitavida.app.ui.demo.TodayActivityKind.MEDICATION -> ref.sourceId?.let { id ->
                                                                    planViewModel.onAction(MedicalPlanAction.OpenMedication(id))
                                                                    protectedContentDestination = ProtectedContentDestination.CALENDAR_MEDICATION
                                                                }
                                                                com.bitavida.app.ui.demo.TodayActivityKind.REMINDER ->
                                                                    remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Open(ref.sourceId ?: ref.recordId ?: ref.id))
                                                                com.bitavida.app.ui.demo.TodayActivityKind.MEASUREMENT -> {
                                                                    if (openCalendarMeasurement(event)) {
                                                                        calendarMeasurementKey = event.key
                                                                        protectedContentDestination = ProtectedContentDestination.CALENDAR_MEASUREMENT
                                                                    }
                                                                }
                                                                com.bitavida.app.ui.demo.TodayActivityKind.OBSERVATION -> ref.recordId?.let { id ->
                                                                    observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.ViewRecord(id))
                                                                    protectedContentDestination = ProtectedContentDestination.CALENDAR_OBSERVATION_RECORD
                                                                }
                                                                com.bitavida.app.ui.demo.TodayActivityKind.CARE_EVENT -> Unit
                                                            }
                                                            else -> Unit
                                                        }
                                                    },
                                                )
                                                }
                                            } else {
                                                AnimatedContent(
                                                    targetState = centralAiOpen,
                                                    modifier = Modifier.fillMaxSize(),
                                                    transitionSpec = {
                                                        val forward = targetState
                                                        (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) {
                                                            if (forward) it / 12 else -it / 12
                                                        }).togetherWith(fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) {
                                                            if (forward) -it / 16 else it / 16
                                                        })
                                                    },
                                                    label = "mi_dia_bitavida_ia",
                                                ) { showAi ->
                                                    val inactive = showAi != centralAiOpen
                                                    val inputGuard = if (inactive) Modifier
                                                        .pointerInput(Unit) {
                                                            awaitPointerEventScope {
                                                                while (true) {
                                                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                                                }
                                                            }
                                                        }.clearAndSetSemantics {} else Modifier
                                                    Box(Modifier.fillMaxSize().then(inputGuard)) {
                                                        if (showAi) {
                                                            DisposableEffect(bitavidaAiViewModel) {
                                                                onDispose {
                                                                    if (!centralAiOpen && protectedContentDestination == ProtectedContentDestination.TODAY &&
                                                                        !careCalendarOpen && !settingsOpen && !appointmentsOpen && !reportOpen) {
                                                                        bitavidaAiViewModel.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear)
                                                                    }
                                                                }
                                                            }
                                                            com.bitavida.app.ui.education.BitavidaAiRoute(
                                                                viewModel = bitavidaAiViewModel,
                                                                navigationContext = null,
                                                                central = true,
                                                                active = centralAiOpen,
                                                                exitLabel = "Mi día",
                                                                onExit = { centralAiOpen = false },
                                                            )
                                                        } else {
                                                TodayDemoScreen(
                                                    caregiverDisplayName = ready.profile.caregiver.displayName,
                                                    personUnderCareDisplayName = ready.profile.personUnderCare.displayName,
                                                    currentDateTime = careNow,
                                                    projection = agendaProjection,
                                                    careEvents = scheduledCareEvents,
                                                    onCareEventSelected = { id ->
                                                        if (!busy) {
                                                            careEventsViewModel.onAction(com.bitavida.app.ui.appointments.CareEventsAction.Open(id))
                                                            appointmentsOpen = true
                                                        }
                                                    },
                                                    onAiSelected = {
                                                        if (!busy) {
                                                            bitavidaAiViewModel.onAction(com.bitavida.app.ui.education.BitavidaAiAction.Clear)
                                                            centralAiOpen = true
                                                        }
                                                    },
                                                    onCalendarOpen = { if (!busy) { selectedAgendaDateIso = null; careCalendarMonth = java.time.YearMonth.from(careNow).toString(); careCalendarOpen = true } },
                                                    selectedDate = selectedAgendaDate,
                                                    onDateSelected = { date ->
                                                        if (!busy) {
                                                            todayMedicationOptions = null
                                                            selectedAgendaDateIso = date.takeUnless { it == careNow.toLocalDate() }?.toString()
                                                        }
                                                    },
                                                    onProfileSelected = { if (!busy) { settingsSection = "menu"; settingsOpen = true } },
                                                    onAppointmentsSelected = { if (!busy) appointmentsOpen = true },
                                                    onReportSelected = { if (!busy) reportOpen = true },
                                                    notes = notesState.notes,
                                                    readNoteImage = notesViewModel::imageBytes,
                                                    notesOverlayVisible = notesState.sheet != null,
                                                    listState = todayListState,
                                                    actionsEnabled = !busy && selectedAgendaIsToday,
                                                    medicationFeedback = todayMedicationFeedback?.takeIf { selectedAgendaIsToday }?.presentationMessage(),
                                                    medicationFeedbackIsError = todayMedicationFeedback?.status ==
                                                        com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus.FAILED,
                                                    medicationConfirmedId = todayMedicationFeedback?.takeIf {
                                                        selectedAgendaIsToday && it.status == com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus.STORED &&
                                                            it.outcome == com.bitavida.app.domain.model.MedicationIntakeOutcome.COMPLETED
                                                    }?.occurrenceId,
                                                    medicationSavingId = todayMedicationFeedback?.takeIf {
                                                        selectedAgendaIsToday && it.status == com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus.SAVING
                                                    }?.occurrenceId,
                                                    onUndoMedication = todayMedicationFeedback?.takeIf {
                                                        selectedAgendaIsToday && it.status == com.bitavida.app.ui.medical.TodayMedicationFeedbackStatus.STORED
                                                    }?.let { receipt -> { undoTodayMedication(planViewModel, receipt) } },
                                                    state = when {
                                                        planState.isLoading || glucoseState.loading ||
                                                            bloodPressureState.loading || weightState.loading ||
                                                            observationState.loading || remindersState.loading -> TodayDemoState.Loading
                                                        planState.globalError != null || glucoseState.loadFailed ||
                                                            bloodPressureState.loadFailed || weightState.loadFailed ||
                                                            observationState.loadError != null || remindersState.loadFailed -> TodayDemoState.Error
                                                        agendaProjection.hasScheduledActivities -> TodayDemoState.HasPlan
                                                        else -> TodayDemoState.Empty
                                                    },
                                                    onMedicationSelected = { id ->
                                                        if (!busy && selectedAgendaIsToday) {
                                                            todayObservationCaptureActive = false
                                                            planViewModel.onAction(MedicalPlanAction.OpenMedication(id))
                                                            protectedContentDestination = ProtectedContentDestination.PLAN
                                                        }
                                                    },
                                                    onMedicationTaken = { item ->
                                                        if (!busy && selectedAgendaIsToday) registerTodayMedication(planViewModel, item.occurrence,
                                                            com.bitavida.app.domain.model.MedicationIntakeOutcome.COMPLETED)
                                                    },
                                                    onMedicationOptions = { item ->
                                                        if (!busy && selectedAgendaIsToday) todayMedicationOptions = todayProjection.medications
                                                            .firstOrNull { it.id == item.id }
                                                    },
                                                    onMedicationNotTaken = { item ->
                                                        if (!busy && selectedAgendaIsToday) openTodayMedicationRegistration(planViewModel, item.occurrence,
                                                            com.bitavida.app.domain.model.MedicationIntakeOutcome.OMITTED)
                                                    },
                                                    onMeasurementRegister = { item ->
                                                        if (!busy && selectedAgendaIsToday) openTodayMeasurementRegistration(item.kind, item.occurrence,
                                                            glucoseViewModel, bloodPressureViewModel, weightViewModel)
                                                    },
                                                    onMeasurementNotPerformed = { item ->
                                                        if (!busy && selectedAgendaIsToday) openTodayMeasurementNotPerformed(item.kind, item.occurrence,
                                                            glucoseViewModel, bloodPressureViewModel, weightViewModel)
                                                    },
                                                    onObservationRegister = { occurrence ->
                                                        if (!busy && selectedAgendaIsToday) {
                                                            todayObservationCaptureActive = true
                                                            observationsViewModel.openScheduled(occurrence)
                                                        }
                                                    },
                                                    onObservationNotPerformed = { occurrence ->
                                                        if (!busy && selectedAgendaIsToday) {
                                                            todayObservationCaptureActive = true
                                                            observationsViewModel.onAction(
                                                                com.bitavida.app.ui.observations.ObservationsAction.NotPerformed(occurrence))
                                                        }
                                                    },
                                                    onReminderDone = { reminder ->
                                                        if (!busy && selectedAgendaIsToday) remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.QuickComplete(reminder.id))
                                                    },
                                                    onReminderPostpone = { reminder ->
                                                        if (!busy && selectedAgendaIsToday) remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.QuickPostpone(reminder.id))
                                                    },
                                                    onReminderSelected = { id ->
                                                        if (!busy && selectedAgendaIsToday) remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Open(id))
                                                    },
                                                    onReminderEdit = { id ->
                                                        if (!busy && selectedAgendaIsToday && remindersViewModel.state.value.active.any { it.id == id }) {
                                                            remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Open(id))
                                                            if (remindersViewModel.state.value.selectedId == id)
                                                                remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Edit)
                                                        }
                                                    },
                                                    onReminderDiscard = { id ->
                                                        if (!busy && selectedAgendaIsToday && remindersViewModel.state.value.active.any { it.id == id }) {
                                                            remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Open(id))
                                                            if (remindersViewModel.state.value.selectedId == id)
                                                                remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.RequestDiscard)
                                                        }
                                                    },
                                                    onAllReminders = {
                                                        todayObservationCaptureActive = false
                                                        remindersOpenedFromToday = true
                                                        remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.OpenMain)
                                                        planViewModel.onAction(MedicalPlanAction.OpenSection(com.bitavida.app.ui.medical.PlanSection.REMINDERS))
                                                        protectedContentDestination = ProtectedContentDestination.PLAN
                                                    },
                                                    onNoteSelected = { note ->
                                                        if (!busy) notesViewModel.onAction(com.bitavida.app.ui.notes.NotesAction.Open(note.id))
                                                    },
                                                    onAllNotes = {
                                                        todayObservationCaptureActive = false
                                                        notesViewModel.onAction(com.bitavida.app.ui.notes.NotesAction.DismissSheet)
                                                        planViewModel.onAction(MedicalPlanAction.OpenSection(com.bitavida.app.ui.medical.PlanSection.OTHER_INDICATIONS))
                                                        protectedContentDestination = ProtectedContentDestination.PLAN
                                                    },
                                                    onPlanSelected = openPlanRoot,
                                                    onRetry = {
                                                        planViewModel.onAction(MedicalPlanAction.Retry)
                                                        glucoseViewModel.onAction(com.bitavida.app.ui.measurements.GlucoseAction.Reload)
                                                        bloodPressureViewModel.onAction(com.bitavida.app.ui.measurements.BloodPressureAction.Reload)
                                                        weightViewModel.onAction(com.bitavida.app.ui.measurements.WeightAction.Reload)
                                                        observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.Reload)
                                                        remindersViewModel.onAction(com.bitavida.app.ui.reminders.RemindersAction.Reload)
                                                        notesViewModel.onAction(com.bitavida.app.ui.notes.NotesAction.Reload)
                                                    },
                                                )
                                                TodayQuickForms(
                                                    planViewModel, glucoseViewModel, bloodPressureViewModel,
                                                    weightViewModel, notesViewModel, motionAllowed,
                                                )
                                                todayMedicationOptions?.let { item ->
                                                    TodayMedicationOptionsSheet(
                                                        item = item,
                                                        motionAllowed = motionAllowed,
                                                        enabled = !busy,
                                                        onOtherTime = {
                                                            todayMedicationOptions = null
                                                            openTodayMedicationRegistration(planViewModel, item.occurrence)
                                                        },
                                                        onWithNote = {
                                                            todayMedicationOptions = null
                                                            openTodayMedicationRegistration(planViewModel, item.occurrence, expandNote = true)
                                                        },
                                                        onNotTaken = {
                                                            todayMedicationOptions = null
                                                            openTodayMedicationRegistration(planViewModel, item.occurrence,
                                                                com.bitavida.app.domain.model.MedicationIntakeOutcome.OMITTED)
                                                        },
                                                        onConsult = {
                                                            if (!busy) {
                                                                todayMedicationOptions = null
                                                                planViewModel.onAction(MedicalPlanAction.OpenMedication(item.occurrence.medicationPlanId))
                                                                protectedContentDestination = ProtectedContentDestination.PLAN
                                                            }
                                                        },
                                                        onDismiss = { todayMedicationOptions = null },
                                                    )
                                                }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        ProtectedContentDestination.CALENDAR_MEASUREMENT -> TodayTypography {
                                            val showingMeasurement = glucoseState.selectedOccurrence != null || glucoseState.selectedRecordId != null || glucoseState.draft != null ||
                                                bloodPressureState.selectedOccurrence != null || bloodPressureState.selectedRecordId != null || bloodPressureState.draft != null ||
                                                weightState.selectedOccurrence != null || weightState.selectedRecordId != null || weightState.draft != null
                                            var needsInitialRestore by remember { mutableStateOf(true) }
                                            LaunchedEffect(needsInitialRestore, glucoseState.loading, bloodPressureState.loading, weightState.loading) {
                                                if (needsInitialRestore && !glucoseState.loading && !bloodPressureState.loading && !weightState.loading) {
                                                    needsInitialRestore = false
                                                    val current = glucoseViewModel.state.value.selectedOccurrence != null || glucoseViewModel.state.value.selectedRecordId != null || glucoseViewModel.state.value.draft != null ||
                                                        bloodPressureViewModel.state.value.selectedOccurrence != null || bloodPressureViewModel.state.value.selectedRecordId != null || bloodPressureViewModel.state.value.draft != null ||
                                                        weightViewModel.state.value.selectedOccurrence != null || weightViewModel.state.value.selectedRecordId != null || weightViewModel.state.value.draft != null
                                                    if (!current && !restoreCalendarMeasurement()) protectedContentDestination = ProtectedContentDestination.TODAY
                                                }
                                            }
                                            androidx.compose.material3.Scaffold(bottomBar = {
                                                com.bitavida.app.ui.components.TodayAgendaNavigation(rootNavigationItems, selectedIndex = 0,
                                                    isEnabled = { !showingMeasurement && !rootNavigationBusy }, onSelected = { openRootTab(it) })
                                            }) { innerPadding ->
                                                com.bitavida.app.ui.measurements.MeasurementsScreen(
                                                    personUnderCareDisplayName = ready.profile.personUnderCare.displayName,
                                                    onBack = { protectedContentDestination = ProtectedContentDestination.TODAY },
                                                    onTodaySelected = { openRootTab(0) }, modifier = Modifier.padding(innerPadding),
                                                    embeddedInPlanScaffold = true, glucoseViewModel = glucoseViewModel,
                                                    bloodPressureViewModel = bloodPressureViewModel, weightViewModel = weightViewModel,
                                                    bitavidaAiViewModel = bitavidaAiViewModel, onRequestNotificationPermission = requestReminderAccess,
                                                    onCalendarDetailExit = { protectedContentDestination = ProtectedContentDestination.TODAY },
                                                    onCalendarDetailRestore = {
                                                        if (!restoreCalendarMeasurement()) protectedContentDestination = ProtectedContentDestination.TODAY
                                                    },
                                                )
                                            }
                                        }
                                        ProtectedContentDestination.CALENDAR_OBSERVATION_OCCURRENCE,
                                        ProtectedContentDestination.CALENDAR_OBSERVATION_RECORD -> TodayTypography {
                                            androidx.compose.material3.Scaffold(bottomBar = {
                                                com.bitavida.app.ui.components.TodayAgendaNavigation(rootNavigationItems, selectedIndex = 0,
                                                    isEnabled = { !rootNavigationBusy && observationState.draft == null },
                                                    onSelected = { observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.EnterCatalog); openRootTab(it) })
                                            }) { innerPadding ->
                                                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                                                com.bitavida.app.ui.observations.ObservationsRoute(
                                                    viewModel = observationsViewModel, personName = ready.profile.personUnderCare.displayName,
                                                    onBack = { observationsViewModel.onAction(com.bitavida.app.ui.observations.ObservationsAction.EnterCatalog); protectedContentDestination = ProtectedContentDestination.TODAY },
                                                    onTodaySelected = { openRootTab(0) }, motionAllowed = motionAllowed,
                                                    embeddedInPlanScaffold = true,
                                                    calendarDetailPage = if (protectedTarget == ProtectedContentDestination.CALENDAR_OBSERVATION_RECORD)
                                                        com.bitavida.app.ui.observations.ObservationsPage.RECORD_DETAIL else com.bitavida.app.ui.observations.ObservationsPage.OCCURRENCE_DETAIL,
                                                )
                                                }
                                            }
                                        }
                                        ProtectedContentDestination.CALENDAR_APPOINTMENT -> TodayTypography {
                                            com.bitavida.app.ui.appointments.CareEventsRoute(
                                                viewModel = careEventsViewModel, person = ready.profile.personUnderCare.displayName,
                                                aiViewModel = bitavidaAiViewModel,
                                                onBack = { protectedContentDestination = ProtectedContentDestination.TODAY },
                                                onCalendarDetailExit = { protectedContentDestination = ProtectedContentDestination.TODAY },
                                                onCancellationComplete = {
                                                    if (careCalendarOpen) calendarStateHolder.removeState("care_calendar")
                                                    careCalendarOpen = false
                                                    appointmentsOpen = true
                                                    protectedContentDestination = ProtectedContentDestination.TODAY
                                                },
                                                bottomBar = { allowed ->
                                                    com.bitavida.app.ui.components.TodayAgendaNavigation(rootNavigationItems, selectedIndex = 0,
                                                        isEnabled = { allowed && !rootNavigationBusy }, onSelected = { openRootTab(it) })
                                                },
                                            )
                                        }
                                        ProtectedContentDestination.PLAN, ProtectedContentDestination.CALENDAR_MEDICATION ->
                                            MedicalPlanRoute(
                                                viewModel = planViewModel,
                                                glucoseViewModel = glucoseViewModel,
                                                bloodPressureViewModel =
                                                    bloodPressureViewModel,
                                                weightViewModel = weightViewModel,
                                                bitavidaAiViewModel = bitavidaAiViewModel,
                                                observationsViewModel = observationsViewModel,
                                                remindersViewModel = remindersViewModel,
                                                notesViewModel = notesViewModel,
                                                activeMedicationCount = activeCounts.medications.takeUnless {
                                                    planState.isLoading || planState.globalError != null
                                                },
                                                activeMeasurementCount = activeCounts.measurements.takeUnless {
                                                    glucoseState.loading || glucoseState.loadFailed ||
                                                        bloodPressureState.loading || bloodPressureState.loadFailed ||
                                                        weightState.loading || weightState.loadFailed
                                                },
                                                activeObservationCount = activeCounts.observations.takeUnless {
                                                    observationState.loading || observationState.loadError != null
                                                },
                                                activeReminderCount = activeCounts.reminders.takeUnless {
                                                    remindersState.loading || remindersState.loadFailed
                                                },
                                                onRemindersBack = {
                                                    planViewModel.onAction(MedicalPlanAction.Back)
                                                    if (remindersOpenedFromToday) {
                                                        protectedContentDestination = ProtectedContentDestination.TODAY
                                                    }
                                                },
                                                caregiverDisplayName =
                                                    ready.profile.caregiver.displayName,
                                                personUnderCareDisplayName =
                                                    ready.profile.personUnderCare.displayName,
                                                onTodaySelected = { openRootTab(0) },
                                                onCalendarDetailExit = if (protectedTarget == ProtectedContentDestination.CALENDAR_MEDICATION) ({
                                                    planViewModel.onAction(MedicalPlanAction.OpenOverview)
                                                    protectedContentDestination = ProtectedContentDestination.TODAY
                                                }) else null,
                                                onPlanRootSelected = { openRootTab(1) },
                                                onPlanExit = {
                                                    todayObservationCaptureActive = false
                                                    protectedContentDestination =
                                                        ProtectedContentDestination.TODAY
                                                },
                                                onAppointmentsSelected = { openRootTab(2) },
                                                onReportSelected = { openRootTab(3) },
                                                reminderRuntimeState =
                                                    reminderRuntimeState,
                                                onRequestReminderAccess =
                                                    requestReminderAccess,
                                            )
                                    }
                                }
                                com.bitavida.app.ui.reminders.ReminderSheets(
                                    state = remindersState,
                                    onAction = remindersViewModel::onAction,
                                )
                            }
                        }
                    }
                }

                val ready = startupState as? CareProfileStartupState.Ready
                if (
                    destination == MainDestination.PROTECTED_CONTENT &&
                    ready != null &&
                    ready.restrictions.isNotEmpty() &&
                    !restrictionDialogDismissed
                ) {
                    CareProfileRestrictionDialog(
                        restrictions = ready.restrictions,
                        onDismiss = { restrictionDialogDismissed = true },
                    )
                }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            reminderAccessRevision++
        }
    }

    override fun onStart() {
        super.onStart()
        com.bitavida.app.ui.settings.DevelopmentDiagnosticGrant.revoke()
        if (::appProtectionPreferences.isInitialized && ::localAuthenticator.isInitialized) {
            synchronizeProtectionPreference()
        }
        appAccessViewModel.onForegrounded(SystemClock.elapsedRealtime())
    }

    override fun onResume() {
        super.onResume()
        reminderAccessRevision++
        if (::localAuthenticator.isInitialized) refreshAuthenticationAvailability()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            appAccessViewModel.onBackgrounded(SystemClock.elapsedRealtime())
        }
        super.onStop()
    }

    private fun setProtectionPreference(requested: Boolean) {
        if (
            !requested &&
            appAccessViewModel.isProtectionActive &&
            !appAccessViewModel.isUnlocked
        ) {
            requestAuthentication()
            return
        }
        val availability = localAuthenticator.availability()
        appAccessViewModel.updateAvailability(availability)
        if (requested && availability != LocalAuthenticationAvailability.AVAILABLE) return
        if (!appProtectionPreferences.setProtectionEnabled(requested)) return
        applyProtectionPreference(requested)
    }

    private fun applyProtectionPreference(enabled: Boolean) {
        if (!enabled) localAuthenticator.cancelEntryAuthentication()
        protectionEnabled = enabled
        appAccessViewModel.setProtectionEnabled(enabled)
    }

    private fun synchronizeProtectionPreference() {
        val enabled = appProtectionPreferences.isProtectionEnabled
        if (enabled != protectionEnabled) {
            applyProtectionPreference(enabled)
            if (enabled && !appAccessViewModel.isProtectionActive) {
                appAccessViewModel.startProtectedSession()
            }
        }
    }

    private fun refreshAuthenticationAvailability() {
        appAccessViewModel.updateAvailability(localAuthenticator.availability())
    }

    private fun requestAuthentication() {
        if (!protectionEnabled || !appAccessViewModel.isProtectionActive) return
        appAccessViewModel.updateAvailability(localAuthenticator.availability())
        if (appAccessViewModel.requestAuthentication()) {
            localAuthenticator.authenticate()
        }
    }

    private fun startBiometricPreferenceChange(target: Boolean) {
        val orphanId = appProtectionPreferences.pendingPreferenceRequestId
        if (orphanId != null && appAccessViewModel.preferenceRequest.value == null) {
            if (!localAuthenticator.hasDeviceCredential()) {
                biometricNotice = "Configura primero un PIN, patrón o contraseña en el dispositivo."
            } else if (appAccessViewModel.beginOrphanRecovery(orphanId)) {
                biometricNotice = "Confirma tu acceso para cancelar el cambio anterior."
                localAuthenticator.authenticatePreferenceCredential(orphanId)
            }
            return
        }
        val result = appAccessViewModel.beginBiometricPreferenceChange(
            current = protectionEnabled,
            target = target,
            hasCredential = localAuthenticator.hasDeviceCredential(),
            strongAvailable = localAuthenticator.hasStrongBiometric(),
        )
        when (result) {
            is com.bitavida.app.domain.access.BiometricPreferenceStart.Authenticate -> {
                val request = result.request
                val method = request.method
                if (!appProtectionPreferences.recordPendingPreference(request.id)) {
                    appAccessViewModel.finishBiometricPreferenceChange(request.id, method, false, false, false)
                    biometricNotice = "No se pudo iniciar el cambio de forma segura. Inténtalo nuevamente."
                    return
                }
                biometricNotice = null
                try {
                    localAuthenticator.confirmBiometricPreference(request.id, request.method)
                } catch (_: Exception) {
                    finishBiometricPreferenceChange(request.id, method, false)
                }
            }
            com.bitavida.app.domain.access.BiometricPreferenceStart.CredentialRequired ->
                biometricNotice = "Configura primero un PIN, patrón o contraseña en el dispositivo."
            com.bitavida.app.domain.access.BiometricPreferenceStart.BiometricUnavailable ->
                biometricNotice = "No hay biometría segura disponible o registrada en el dispositivo."
            com.bitavida.app.domain.access.BiometricPreferenceStart.Busy ->
                biometricNotice = "Termina la verificación actual antes de hacer otro cambio."
            com.bitavida.app.domain.access.BiometricPreferenceStart.NoChange -> Unit
        }
    }

    private fun finishBiometricPreferenceChange(
        id: String, method: com.bitavida.app.domain.access.PreferenceVerificationMethod, succeeded: Boolean,
    ) {
        if (appProtectionPreferences.pendingPreferenceRequestId != id) return
        val pending = appAccessViewModel.preferenceRequest.value
        if (pending == null) {
            if (appAccessViewModel.recoveryRequest.value == id) {
                if (method != com.bitavida.app.domain.access.PreferenceVerificationMethod.DEVICE_CREDENTIAL) return
                appAccessViewModel.finishOrphanRecovery(id)
                val cleared = succeeded && appProtectionPreferences.clearPendingPreference(id)
                biometricNotice = if (cleared) "Cambio anterior cancelado. Puedes intentarlo de nuevo."
                    else "No se cambió la configuración. Confirma tu acceso para volver a intentarlo."
            } else if (method == com.bitavida.app.domain.access.PreferenceVerificationMethod.STRONG_BIOMETRIC) {
                appProtectionPreferences.clearPendingPreference(id)
                biometricNotice = "El cambio anterior no se aplicó. Inténtalo nuevamente."
            }
            refreshAuthenticationAvailability()
            if (protectionEnabled && !appAccessViewModel.isUnlocked) requestAuthentication()
            return
        }
        if (pending.id != id || method != pending.method) return
        val result = appAccessViewModel.finishBiometricPreferenceChange(
            id, method, succeeded, localAuthenticator.hasDeviceCredential(), localAuthenticator.hasStrongBiometric(),
        )
        if (result != null) {
            if (appProtectionPreferences.completePreferenceChange(id, result)) {
                applyProtectionPreference(result)
                if (result) appAccessViewModel.startVerifiedProtectedSession()
                refreshAuthenticationAvailability()
                biometricNotice = if (result) "Protección de acceso activada." else "Protección de acceso desactivada."
            } else {
                biometricNotice = "No se pudo guardar el cambio de forma segura."
            }
        } else {
            val cleared = appProtectionPreferences.clearPendingPreference(id)
            biometricNotice = if (!cleared) "No se pudo finalizar el cambio de forma segura."
                else if (succeeded) "El acceso cambió o el método dejó de estar disponible. Inténtalo de nuevo."
                else "No se cambió la protección de acceso."
        }
        if (protectionEnabled && !appAccessViewModel.isUnlocked) requestAuthentication()
    }

    private fun medicalPlanViewModel(profile: CareProfile): MedicalPlanViewModel {
        medicalPlanViewModel?.let { return it }
        return ViewModelProvider(
            owner = this,
            factory = MedicalPlanViewModel.factory(
                profile = profile,
                repository = EncryptedMedicalCareRepository(applicationContext),
            ),
        )[MedicalPlanViewModel::class.java].also { medicalPlanViewModel = it }
    }

    private fun glucoseViewModel(profile: CareProfile): GlucoseViewModel {
        glucoseViewModel?.let { return it }
        return ViewModelProvider(
            owner = this,
            factory = GlucoseViewModel.factory(
                actor = GlucoseActor(profile.id, profile.caregiver.id, profile.personUnderCare.id, profile.consent.isCurrent),
                repository = EncryptedGlucoseRepository(applicationContext),
            ),
        )[GlucoseViewModel::class.java].also { glucoseViewModel = it }
    }

    private fun bloodPressureViewModel(
        profile: CareProfile,
    ): BloodPressureViewModel {
        bloodPressureViewModel?.let { return it }
        return ViewModelProvider(
            owner = this,
            factory = BloodPressureViewModel.factory(
                actor = GlucoseActor(
                    profile.id,
                    profile.caregiver.id,
                    profile.personUnderCare.id,
                    profile.consent.isCurrent,
                ),
                repository = EncryptedBloodPressureRepository(
                    applicationContext,
                ),
            ),
        )[BloodPressureViewModel::class.java].also {
            bloodPressureViewModel = it
        }
    }

    private fun weightViewModel(
        profile: CareProfile,
    ): WeightViewModel {
        weightViewModel?.let { return it }
        return ViewModelProvider(
            owner = this,
            factory = WeightViewModel.factory(
                actor = GlucoseActor(
                    profile.id,
                    profile.caregiver.id,
                    profile.personUnderCare.id,
                    profile.consent.isCurrent,
                ),
                repository = EncryptedWeightRepository(applicationContext),
            ),
        )[WeightViewModel::class.java].also { weightViewModel = it }
    }

    private fun observationsViewModel(profile: CareProfile): com.bitavida.app.ui.observations.ObservationsViewModel {
        observationsViewModel?.let { return it }
        return ViewModelProvider(
            owner = this,
            factory = com.bitavida.app.ui.observations.ObservationsViewModel.factory(
                actor = GlucoseActor(profile.id, profile.caregiver.id, profile.personUnderCare.id, profile.consent.isCurrent),
                repository = com.bitavida.app.data.observations.EncryptedObservationRepository(applicationContext),
            ),
        )[com.bitavida.app.ui.observations.ObservationsViewModel::class.java].also { observationsViewModel = it }
    }

    private fun remindersViewModel(profile: CareProfile): com.bitavida.app.ui.reminders.RemindersViewModel {
        remindersViewModel?.let { return it }
        val actor = com.bitavida.app.domain.repository.CareReminderActor(
            profile.id, profile.caregiver.id, profile.personUnderCare.id, profile.consent.isCurrent,
        )
        return ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return com.bitavida.app.ui.reminders.RemindersViewModel(
                        repository = com.bitavida.app.data.reminders.EncryptedCareReminderRepository(applicationContext),
                        actor = actor,
                        assistTitle = { text -> com.bitavida.app.domain.usecase.AssistReminderTitle(localAi)(text) },
                        ideaCatalog = com.bitavida.app.domain.usecase.reminderIdeaGroups(localAi),
                    ) as T
                }
            },
        )[com.bitavida.app.ui.reminders.RemindersViewModel::class.java].also { remindersViewModel = it }
    }

    private fun notesViewModel(profile: CareProfile): com.bitavida.app.ui.notes.NotesViewModel {
        notesViewModel?.let { return it }
        val actor = com.bitavida.app.domain.repository.NoteActor(
            profile.id, profile.caregiver.id, profile.personUnderCare.id, profile.consent.isCurrent,
        )
        val images = com.bitavida.app.data.notes.NoteImageStore(applicationContext)
        return ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return com.bitavida.app.ui.notes.NotesViewModel(
                        repository = com.bitavida.app.data.notes.EncryptedNoteRepository(applicationContext),
                        actor = actor,
                        readImage = { id -> images.read(actor, id) },
                        discardImage = { id -> images.discard(actor, id) },
                    ) as T
                }
            },
        )[com.bitavida.app.ui.notes.NotesViewModel::class.java].also { notesViewModel = it }
    }

    private enum class ProtectedContentDestination {
        TODAY,
        PLAN,
        CALENDAR_MEDICATION,
        CALENDAR_MEASUREMENT,
        CALENDAR_OBSERVATION_OCCURRENCE,
        CALENDAR_OBSERVATION_RECORD,
        CALENDAR_APPOINTMENT,
    }

    private enum class MainDestination {
        ACCESS,
        STARTUP,
        ONBOARDING,
        PROTECTED_CONTENT,
    }
}
