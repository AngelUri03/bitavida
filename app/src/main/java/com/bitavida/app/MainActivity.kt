package com.bitavida.app

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.bitavida.app.domain.access.AppAccessSessionState
import com.bitavida.app.domain.access.LocalAuthenticationError
import com.bitavida.app.platform.access.AndroidLocalAuthenticator
import com.bitavida.app.ui.access.AppAccessViewModel
import com.bitavida.app.ui.access.AppUnlockScreen
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.careprofile.CareProfileFlowRoute
import com.bitavida.app.ui.careprofile.CareProfileViewModel
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.demo.TodayDemoScreen
import com.bitavida.app.ui.theme.BitavidaTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : FragmentActivity() {
    private val careProfileViewModel: CareProfileViewModel by viewModels()
    private val appAccessViewModel: AppAccessViewModel by viewModels()

    private lateinit var localAuthenticator: AndroidLocalAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        localAuthenticator = AndroidLocalAuthenticator(
            activity = this,
            callback = object : AndroidLocalAuthenticator.Callback {
                override fun onAuthenticationSucceeded() {
                    appAccessViewModel.onAuthenticationSucceeded()
                    applyTaskPrivacy()
                }

                override fun onAuthenticationAttemptRejected() {
                    appAccessViewModel.onAuthenticationAttemptRejected()
                }

                override fun onAuthenticationError(error: LocalAuthenticationError) {
                    appAccessViewModel.onAuthenticationError(error)
                    applyTaskPrivacy()
                }
            },
        )
        appAccessViewModel.updateAvailability(localAuthenticator.availability())

        setContent {
            BitavidaTheme {
                var showCareProfileFlow by rememberSaveable { mutableStateOf(true) }
                var revealProtectedContent by remember { mutableStateOf(false) }
                val accessState by appAccessViewModel.uiState.collectAsState()
                val motionAllowed = rememberBitavidaMotionAllowed(override = null)
                val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0

                LaunchedEffect(showCareProfileFlow) {
                    if (
                        !showCareProfileFlow &&
                        !appAccessViewModel.isProtectionActive
                    ) {
                        appAccessViewModel.startProtectedSession()
                        applyTaskPrivacy()
                    }
                }

                LaunchedEffect(
                    showCareProfileFlow,
                    accessState.sessionState,
                    motionAllowed,
                ) {
                    if (
                        !showCareProfileFlow &&
                        accessState.sessionState == AppAccessSessionState.UNLOCKED
                    ) {
                        if (motionAllowed) delay(420.milliseconds)
                        revealProtectedContent = true
                    } else {
                        revealProtectedContent = false
                    }
                }

                LaunchedEffect(accessState.sessionState) {
                    applyTaskPrivacy()
                }

                BackHandler(enabled = !showCareProfileFlow) {
                    appAccessViewModel.lockNow()
                    applyTaskPrivacy()
                }

                val destination = when {
                    showCareProfileFlow -> MainDestination.ONBOARDING
                    revealProtectedContent -> MainDestination.PROTECTED_CONTENT
                    else -> MainDestination.ACCESS
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
                                    appAccessViewModel.startProtectedSession()
                                    applyTaskPrivacy()
                                    showCareProfileFlow = false
                                },
                            )
                        MainDestination.ACCESS ->
                            AppUnlockScreen(
                                state = accessState,
                                onUnlock = ::requestAuthentication,
                                onOpenSecuritySetup = localAuthenticator::openSecuritySetup,
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
        if (::localAuthenticator.isInitialized) {
            appAccessViewModel.updateAvailability(localAuthenticator.availability())
        }
        applyTaskPrivacy()
    }

    override fun onPause() {
        if (appAccessViewModel.isProtectionActive) protectTaskSnapshot()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyTaskPrivacy()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            appAccessViewModel.onBackgrounded(SystemClock.elapsedRealtime())
        }
        super.onStop()
    }

    private fun requestAuthentication() {
        val availability = localAuthenticator.availability()
        appAccessViewModel.updateAvailability(availability)
        if (appAccessViewModel.requestAuthentication()) {
            localAuthenticator.authenticate()
        }
    }

    private fun protectTaskSnapshot() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun applyTaskPrivacy() {
        val normalCaptureAllowed = !appAccessViewModel.isProtectionActive ||
            (appAccessViewModel.isUnlocked && hasWindowFocus())
        if (normalCaptureAllowed) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            protectTaskSnapshot()
        }
    }

    private enum class MainDestination {
        ONBOARDING,
        ACCESS,
        PROTECTED_CONTENT,
    }
}
