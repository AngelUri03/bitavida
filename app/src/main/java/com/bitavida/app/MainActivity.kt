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
import com.bitavida.app.data.access.AppProtectionPreferences
import com.bitavida.app.domain.access.AppAccessSessionState
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.domain.access.LocalAuthenticationError
import com.bitavida.app.platform.access.AndroidLocalAuthenticator
import com.bitavida.app.ui.access.AppAccessNotice
import com.bitavida.app.ui.access.AppAccessViewModel
import com.bitavida.app.ui.access.AppUnlockScreen
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.careprofile.CareProfileFlowRoute
import com.bitavida.app.ui.careprofile.CareProfileViewModel
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.demo.TodayDemoScreen
import com.bitavida.app.ui.theme.BitavidaTheme

class MainActivity : FragmentActivity() {
    private val careProfileViewModel: CareProfileViewModel by viewModels()
    private val appAccessViewModel: AppAccessViewModel by viewModels()

    private lateinit var localAuthenticator: AndroidLocalAuthenticator
    private lateinit var appProtectionPreferences: AppProtectionPreferences
    private var protectionEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                var showCareProfileFlow by rememberSaveable { mutableStateOf(true) }
                val accessState by appAccessViewModel.uiState.collectAsState()
                val motionAllowed = rememberBitavidaMotionAllowed(override = null)
                val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0

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
                    showCareProfileFlow -> MainDestination.ONBOARDING
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
                        MainDestination.ONBOARDING ->
                            CareProfileFlowRoute(
                                viewModel = careProfileViewModel,
                                onExit = {
                                    if (
                                        protectionEnabled &&
                                        !appAccessViewModel.isProtectionActive
                                    ) {
                                        appAccessViewModel.startProtectedSession()
                                    }
                                    showCareProfileFlow = false
                                },
                                protectionEnabled = protectionEnabled,
                                protectionAvailability = accessState.availability,
                                onProtectionEnabledChange = ::setProtectionPreference,
                                onOpenSecuritySetup =
                                    localAuthenticator::openSecuritySetup,
                            )
                        MainDestination.ACCESS ->
                            AppUnlockScreen(
                                state = accessState,
                                onUnlock = ::requestAuthentication,
                                onOpenSecuritySetup =
                                    localAuthenticator::openSecuritySetup,
                            )
                        MainDestination.PROTECTED_CONTENT -> TodayDemoScreen()
                    }
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
        ONBOARDING,
        ACCESS,
        PROTECTED_CONTENT,
    }
}
