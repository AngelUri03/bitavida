package com.bitavida.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bitavida.app.data.access.AppProtectionPreferences
import com.bitavida.app.data.careprofile.EncryptedCareProfileRepository
import com.bitavida.app.domain.access.AppAccessSessionState
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.domain.access.LocalAuthenticationError
import com.bitavida.app.platform.access.AndroidLocalAuthenticator
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
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.demo.TodayDemoScreen
import com.bitavida.app.ui.theme.BitavidaTheme

class MainActivity : FragmentActivity() {
    private val appAccessViewModel: AppAccessViewModel by viewModels()

    private lateinit var careProfileViewModel: CareProfileViewModel
    private lateinit var localAuthenticator: AndroidLocalAuthenticator
    private lateinit var appProtectionPreferences: AppProtectionPreferences
    private var protectionEnabled by mutableStateOf(false)

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
        localAuthenticator = AndroidLocalAuthenticator(
            activity = this,
            callback = object : AndroidLocalAuthenticator.Callback {
                override fun onAuthenticationSucceeded() {
                    appAccessViewModel.onAuthenticationSucceeded()
                }

                override fun onAuthenticationAttemptRejected() {
                    appAccessViewModel.onAuthenticationAttemptRejected()
                }

                override fun onAuthenticationError(error: LocalAuthenticationError) {
                    appAccessViewModel.onAuthenticationError(error)
                }
            },
        )

        val initialAvailability = localAuthenticator.availability()
        appAccessViewModel.updateAvailability(initialAvailability)
        val storedPreference = appProtectionPreferences.isProtectionEnabled
        protectionEnabled =
            storedPreference &&
                initialAvailability == LocalAuthenticationAvailability.AVAILABLE
        if (storedPreference != protectionEnabled) {
            appProtectionPreferences.setProtectionEnabled(protectionEnabled)
        }
        appAccessViewModel.setProtectionEnabled(protectionEnabled)
        if (protectionEnabled && !appAccessViewModel.isProtectionActive) {
            appAccessViewModel.startProtectedSession()
        }

        setContent {
            BitavidaTheme {
                val accessState by appAccessViewModel.uiState.collectAsState()
                val startupState by careProfileViewModel.startupState.collectAsState()
                val onboardingVisible by
                    careProfileViewModel.onboardingVisible.collectAsState()
                val motionAllowed = rememberBitavidaMotionAllowed(override = null)
                val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
                var restrictionDialogDismissed by rememberSaveable {
                    mutableStateOf(false)
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
                                TodayDemoScreen(
                                    caregiverDisplayName =
                                        ready.profile.caregiver.displayName,
                                    personUnderCareDisplayName =
                                        ready.profile.personUnderCare.displayName,
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

    override fun onStart() {
        super.onStart()
        appAccessViewModel.onForegrounded(SystemClock.elapsedRealtime())
    }

    override fun onResume() {
        super.onResume()
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
        val effectivePreference =
            requested && availability == LocalAuthenticationAvailability.AVAILABLE
        appProtectionPreferences.setProtectionEnabled(effectivePreference)
        protectionEnabled = effectivePreference
        appAccessViewModel.setProtectionEnabled(effectivePreference)
    }

    private fun refreshAuthenticationAvailability() {
        val availability = localAuthenticator.availability()
        appAccessViewModel.updateAvailability(availability)
        if (
            protectionEnabled &&
            availability != LocalAuthenticationAvailability.AVAILABLE
        ) {
            appProtectionPreferences.setProtectionEnabled(false)
            protectionEnabled = false
            appAccessViewModel.setProtectionEnabled(false)
        }
    }

    private fun requestAuthentication() {
        val availability = localAuthenticator.availability()
        appAccessViewModel.updateAvailability(availability)
        if (appAccessViewModel.requestAuthentication()) {
            localAuthenticator.authenticate()
        }
    }

    private enum class MainDestination {
        ACCESS,
        STARTUP,
        ONBOARDING,
        PROTECTED_CONTENT,
    }
}
