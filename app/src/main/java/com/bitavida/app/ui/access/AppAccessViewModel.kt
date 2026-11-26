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
import kotlinx.coroutines.flow.asStateFlow
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
    private val preferenceController = com.bitavida.app.domain.access.BiometricPreferenceChangeController()
    private val mutablePreferenceRequest = MutableStateFlow<com.bitavida.app.domain.access.BiometricPreferenceRequest?>(null)
    val preferenceRequest = mutablePreferenceRequest.asStateFlow()
    private val mutableRecoveryRequest = MutableStateFlow<String?>(null)
    val recoveryRequest = mutableRecoveryRequest.asStateFlow()

    fun beginOrphanRecovery(id: String): Boolean {
        if (mutablePreferenceRequest.value != null || mutableRecoveryRequest.value != null || !isUnlocked) return false
        mutableRecoveryRequest.value = id
        return true
    }

    fun finishOrphanRecovery(id: String) {
        if (mutableRecoveryRequest.value == id) mutableRecoveryRequest.value = null
    }

    fun beginBiometricPreferenceChange(current: Boolean, target: Boolean, hasCredential: Boolean,
        strongAvailable: Boolean): com.bitavida.app.domain.access.BiometricPreferenceStart {
        if (!isUnlocked) return com.bitavida.app.domain.access.BiometricPreferenceStart.Busy
        val result = preferenceController.start(current, target, hasCredential, strongAvailable)
        mutablePreferenceRequest.value = preferenceController.pending
        return result
    }

    fun finishBiometricPreferenceChange(id: String, method: com.bitavida.app.domain.access.PreferenceVerificationMethod, succeeded: Boolean, hasCredential: Boolean,
        strongAvailable: Boolean): Boolean? {
        val result = preferenceController.finish(id, method, succeeded, isUnlocked, hasCredential, strongAvailable)
        mutablePreferenceRequest.value = preferenceController.pending
        return result
    }

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

    val isEntryAuthenticationExpected: Boolean
        get() = sessionController.isProtectionEnabled &&
            sessionController.isProtectionActive &&
            sessionController.state.value ==
                com.bitavida.app.domain.access.AppAccessSessionState.AUTHENTICATING

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

    /** Called only after a successful, persisted verification of the new protection policy. */
    fun startVerifiedProtectedSession() {
        sessionController.startProtectedSession()
        if (sessionController.beginAuthentication()) sessionController.authenticationSucceeded()
        presentationState.value = presentationState.value.copy(notice = null)
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
        if (mutablePreferenceRequest.value != null || mutableRecoveryRequest.value != null) return false
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
        if (!isEntryAuthenticationExpected) return
        presentationState.value = presentationState.value.copy(
            notice = AppAccessNotice.AUTHENTICATION_FAILED,
        )
    }

    fun onAuthenticationError(error: LocalAuthenticationError) {
        if (!isEntryAuthenticationExpected) return
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
