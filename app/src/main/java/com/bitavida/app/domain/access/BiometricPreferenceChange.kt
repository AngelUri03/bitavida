package com.bitavida.app.domain.access

/** The system prompt never stores credentials or opens an access session. */
enum class PreferenceVerificationMethod { STRONG_BIOMETRIC, DEVICE_CREDENTIAL }

data class BiometricPreferenceRequest(
    val id: String,
    val target: Boolean,
    val method: PreferenceVerificationMethod,
)

sealed interface BiometricPreferenceStart {
    data class Authenticate(val request: BiometricPreferenceRequest) : BiometricPreferenceStart
    data object NoChange : BiometricPreferenceStart
    data object Busy : BiometricPreferenceStart
    data object CredentialRequired : BiometricPreferenceStart
    data object BiometricUnavailable : BiometricPreferenceStart
}

/** Retained during recreation; a fresh process cannot approve an old prompt. */
class BiometricPreferenceChangeController {
    var pending: BiometricPreferenceRequest? = null
        private set

    fun start(current: Boolean, target: Boolean, hasDeviceCredential: Boolean,
        strongBiometricAvailable: Boolean): BiometricPreferenceStart {
        if (pending != null) return BiometricPreferenceStart.Busy
        if (current == target) return BiometricPreferenceStart.NoChange
        if (!hasDeviceCredential) return BiometricPreferenceStart.CredentialRequired
        val method = if (target && strongBiometricAvailable) PreferenceVerificationMethod.STRONG_BIOMETRIC
            else PreferenceVerificationMethod.DEVICE_CREDENTIAL
        val request = BiometricPreferenceRequest(java.util.UUID.randomUUID().toString(), target, method)
        pending = request
        return BiometricPreferenceStart.Authenticate(request)
    }

    /** Returns a candidate only after a current, successful and correctly scoped verification. */
    fun finish(id: String, method: PreferenceVerificationMethod, succeeded: Boolean, stillUnlocked: Boolean,
        hasDeviceCredential: Boolean, strongBiometricAvailable: Boolean): Boolean? {
        val request = pending ?: return null
        if (request.id != id || request.method != method) return null
        pending = null
        if (!succeeded || !stillUnlocked || !hasDeviceCredential ||
            (method == PreferenceVerificationMethod.STRONG_BIOMETRIC && !strongBiometricAvailable)) return null
        return request.target
    }
}
