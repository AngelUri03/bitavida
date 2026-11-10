package com.bitavida.app.domain.access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppAccessSessionState {
    LOCKED,
    AUTHENTICATING,
    UNLOCKED,
}

enum class LocalAuthenticationAvailability {
    CHECKING,
    AVAILABLE,
    SETUP_REQUIRED,
    TEMPORARILY_UNAVAILABLE,
    UNSUPPORTED,
}

enum class LocalAuthenticationError {
    CANCELLED,
    FAILED,
    LOCKOUT,
    TEMPORARILY_UNAVAILABLE,
    UNSUPPORTED,
    SETUP_REQUIRED,
}

data class AppAccessPolicy(
    val backgroundTimeoutMillis: Long = DEFAULT_BACKGROUND_TIMEOUT_MILLIS,
) {
    init {
        require(backgroundTimeoutMillis >= 0L)
    }

    fun hasExpired(backgroundedAtMillis: Long, foregroundedAtMillis: Long): Boolean =
        foregroundedAtMillis - backgroundedAtMillis >= backgroundTimeoutMillis

    companion object {
        const val DEFAULT_BACKGROUND_TIMEOUT_MILLIS = 60_000L
    }
}

class AppAccessSessionController(
    private val policy: AppAccessPolicy = AppAccessPolicy(),
) {
    private val mutableState = MutableStateFlow(AppAccessSessionState.UNLOCKED)
    val state: StateFlow<AppAccessSessionState> = mutableState.asStateFlow()

    private var backgroundedAtMillis: Long? = null
    var isProtectionEnabled: Boolean = false
        private set
    var isProtectionActive: Boolean = false
        private set

    fun setProtectionEnabled(enabled: Boolean) {
        isProtectionEnabled = enabled
        if (!enabled) {
            isProtectionActive = false
            backgroundedAtMillis = null
            mutableState.value = AppAccessSessionState.UNLOCKED
        }
    }

    fun startProtectedSession() {
        backgroundedAtMillis = null
        if (!isProtectionEnabled) {
            isProtectionActive = false
            mutableState.value = AppAccessSessionState.UNLOCKED
            return
        }
        isProtectionActive = true
        mutableState.value = AppAccessSessionState.LOCKED
    }

    fun beginAuthentication(): Boolean {
        if (!isProtectionEnabled || !isProtectionActive) return false
        if (mutableState.value != AppAccessSessionState.LOCKED) return false
        mutableState.value = AppAccessSessionState.AUTHENTICATING
        return true
    }

    fun authenticationSucceeded(): Boolean {
        if (mutableState.value != AppAccessSessionState.AUTHENTICATING) return false
        mutableState.value = AppAccessSessionState.UNLOCKED
        return true
    }

    fun authenticationEndedWithoutSuccess() {
        if (mutableState.value == AppAccessSessionState.AUTHENTICATING) {
            mutableState.value = AppAccessSessionState.LOCKED
        }
    }

    fun lockImmediately() {
        if (!isProtectionEnabled || !isProtectionActive) {
            mutableState.value = AppAccessSessionState.UNLOCKED
            return
        }
        mutableState.value = AppAccessSessionState.LOCKED
    }

    fun onBackgrounded(elapsedRealtimeMillis: Long) {
        if (
            isProtectionEnabled &&
            isProtectionActive &&
            mutableState.value == AppAccessSessionState.UNLOCKED
        ) {
            backgroundedAtMillis = elapsedRealtimeMillis
        }
    }

    fun onForegrounded(elapsedRealtimeMillis: Long): Boolean {
        if (!isProtectionEnabled || !isProtectionActive) return false
        val backgroundedAt = backgroundedAtMillis ?: return false
        backgroundedAtMillis = null
        if (!policy.hasExpired(backgroundedAt, elapsedRealtimeMillis)) return false
        lockImmediately()
        return true
    }
}
