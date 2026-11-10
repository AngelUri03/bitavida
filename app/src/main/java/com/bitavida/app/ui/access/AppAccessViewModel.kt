package com.bitavida.app.ui.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitavida.app.domain.access.AppAccessSessionController
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.domain.access.LocalAuthenticationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AppAccessViewModel(
    private val sessionController: AppAccessSessionController = AppAccessSessionController(),
) : ViewModel() {
    private data class PresentationState(
        val availability: LocalAuthenticationAvailability =
            LocalAuthenticationAvailability.CHECKING,
        val notice: AppAccessNotice? = null,
    )

    private val presentationState = MutableStateFlow(PresentationState())

    val uiState: StateFlow<AppAccessUiState> = combine(
        sessionController.state,
        presentationState,
    ) { sessionState, presentation ->
        AppAccessUiState(
            sessionState = sessionState,
            availability = presentation.availability,
            notice = presentation.notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppAccessUiState(),
    )

    val isUnlocked: Boolean
        get() = sessionController.state.value ==
            com.bitavida.app.domain.access.AppAccessSessionState.UNLOCKED

    val isProtectionActive: Boolean
        get() = sessionController.isProtectionActive

    fun setProtectionEnabled(enabled: Boolean) {
        sessionController.setProtectionEnabled(enabled)
        if (!enabled) {
            presentationState.value = presentationState.value.copy(notice = null)
        }
    }

    fun startProtectedSession() {
        presentationState.value = presentationState.value.copy(notice = null)
        sessionController.startProtectedSession()
    }

    fun updateAvailability(availability: LocalAuthenticationAvailability) {
        val currentNotice = presentationState.value.notice
        presentationState.value = presentationState.value.copy(
            availability = availability,
            notice = when (availability) {
                LocalAuthenticationAvailability.SETUP_REQUIRED ->
                    AppAccessNotice.DEVICE_SECURITY_SETUP_REQUIRED
                LocalAuthenticationAvailability.TEMPORARILY_UNAVAILABLE ->
                    AppAccessNotice.AUTHENTICATOR_TEMPORARILY_UNAVAILABLE
                LocalAuthenticationAvailability.UNSUPPORTED ->
                    AppAccessNotice.AUTHENTICATOR_UNSUPPORTED
                LocalAuthenticationAvailability.CHECKING -> currentNotice
                LocalAuthenticationAvailability.AVAILABLE -> when (currentNotice) {
                    AppAccessNotice.DEVICE_SECURITY_SETUP_REQUIRED,
                    AppAccessNotice.AUTHENTICATOR_TEMPORARILY_UNAVAILABLE,
                    AppAccessNotice.AUTHENTICATOR_UNSUPPORTED,
                    -> null
                    else -> currentNotice
                }
            },
        )
    }

    fun requestAuthentication(): Boolean {
        if (presentationState.value.availability !=
            LocalAuthenticationAvailability.AVAILABLE
        ) {
            return false
        }
        presentationState.value = presentationState.value.copy(notice = null)
        return sessionController.beginAuthentication()
    }

    fun onAuthenticationSucceeded() {
        if (sessionController.authenticationSucceeded()) {
            presentationState.value = presentationState.value.copy(notice = null)
        }
    }

    fun onAuthenticationAttemptRejected() {
        presentationState.value = presentationState.value.copy(
            notice = AppAccessNotice.AUTHENTICATION_FAILED,
        )
    }

    fun onAuthenticationError(error: LocalAuthenticationError) {
        val notice = when (error) {
            LocalAuthenticationError.CANCELLED ->
                AppAccessNotice.AUTHENTICATION_CANCELLED
            LocalAuthenticationError.FAILED ->
                AppAccessNotice.AUTHENTICATION_FAILED
            LocalAuthenticationError.LOCKOUT ->
                AppAccessNotice.AUTHENTICATION_LOCKED
            LocalAuthenticationError.TEMPORARILY_UNAVAILABLE ->
                AppAccessNotice.AUTHENTICATOR_TEMPORARILY_UNAVAILABLE
            LocalAuthenticationError.UNSUPPORTED ->
                AppAccessNotice.AUTHENTICATOR_UNSUPPORTED
            LocalAuthenticationError.SETUP_REQUIRED ->
                AppAccessNotice.DEVICE_SECURITY_SETUP_REQUIRED
        }
        presentationState.value = presentationState.value.copy(notice = notice)
        sessionController.authenticationEndedWithoutSuccess()
    }

    fun onBackgrounded(elapsedRealtimeMillis: Long) {
        sessionController.onBackgrounded(elapsedRealtimeMillis)
    }

    fun onForegrounded(elapsedRealtimeMillis: Long) {
        if (sessionController.onForegrounded(elapsedRealtimeMillis)) {
            presentationState.value = presentationState.value.copy(
                notice = AppAccessNotice.SESSION_EXPIRED,
            )
        }
    }

}
