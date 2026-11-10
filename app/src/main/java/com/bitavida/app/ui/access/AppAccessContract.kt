package com.bitavida.app.ui.access

import com.bitavida.app.domain.access.AppAccessSessionState
import com.bitavida.app.domain.access.LocalAuthenticationAvailability

data class AppAccessUiState(
    val sessionState: AppAccessSessionState = AppAccessSessionState.UNLOCKED,
    val availability: LocalAuthenticationAvailability =
        LocalAuthenticationAvailability.CHECKING,
    val notice: AppAccessNotice? = null,
)

enum class AppAccessNotice {
    AUTHENTICATION_CANCELLED,
    AUTHENTICATION_FAILED,
    AUTHENTICATION_LOCKED,
    AUTHENTICATOR_TEMPORARILY_UNAVAILABLE,
    AUTHENTICATOR_UNSUPPORTED,
    DEVICE_SECURITY_SETUP_REQUIRED,
    SESSION_EXPIRED,
}
