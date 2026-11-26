package com.bitavida.app.platform.access

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.bitavida.app.domain.access.LocalAuthenticationAvailability
import com.bitavida.app.domain.access.LocalAuthenticationError
import com.bitavida.app.domain.access.PreferenceVerificationMethod

class AndroidLocalAuthenticator(
    private val activity: FragmentActivity,
    private val callback: Callback,
) {
    interface Callback {
        fun isEntryAuthenticationExpected(): Boolean
        fun onAuthenticationSucceeded()
        fun onAuthenticationAttemptRejected()
        fun onAuthenticationError(error: LocalAuthenticationError)
        fun pendingPreferenceRequestId(): String?
        fun onPreferenceAuthenticationSucceeded(id: String, method: PreferenceVerificationMethod)
        fun onPreferenceAuthenticationAttemptRejected(id: String)
        fun onPreferenceAuthenticationError(id: String, method: PreferenceVerificationMethod, error: LocalAuthenticationError)
    }

    private val keyguardManager =
        activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val biometricManager = BiometricManager.from(activity)
    private var credentialFallbackInProgress = false
    private var entryGeneration = 0L
    private var entryInFlight = callback.isEntryAuthenticationExpected()
    private var credentialGeneration: Long? = if (entryInFlight) entryGeneration else null

    private fun isCurrentEntry(generation: Long): Boolean =
        entryInFlight && generation == entryGeneration && callback.isEntryAuthenticationExpected()

    private val credentialConfirmationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        credentialFallbackInProgress = false
        val generation = credentialGeneration
        credentialGeneration = null
        if (generation != null && isCurrentEntry(generation)) {
            entryInFlight = false
            if (result.resultCode == Activity.RESULT_OK) callback.onAuthenticationSucceeded()
            else callback.onAuthenticationError(LocalAuthenticationError.CANCELLED)
        }
    }

    private var preferenceCredentialId: String? = callback.pendingPreferenceRequestId()
    private val preferenceCredentialLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = preferenceCredentialId
        preferenceCredentialId = null
        if (id != null) {
            if (result.resultCode == Activity.RESULT_OK) callback.onPreferenceAuthenticationSucceeded(id, PreferenceVerificationMethod.DEVICE_CREDENTIAL)
            else callback.onPreferenceAuthenticationError(id, PreferenceVerificationMethod.DEVICE_CREDENTIAL, LocalAuthenticationError.CANCELLED)
        }
    }

    // The callback is bound to an immutable purpose and operation ID, including after recreation.
    private fun createBiometricPrompt(preferenceId: String?, generation: Long): BiometricPrompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (preferenceId == null) {
                    if (isCurrentEntry(generation)) {
                        entryInFlight = false
                        callback.onAuthenticationSucceeded()
                    }
                } else callback.onPreferenceAuthenticationSucceeded(preferenceId, PreferenceVerificationMethod.STRONG_BIOMETRIC)
            }

            override fun onAuthenticationFailed() {
                if (preferenceId == null) {
                    if (isCurrentEntry(generation)) callback.onAuthenticationAttemptRejected()
                } else callback.onPreferenceAuthenticationAttemptRejected(preferenceId)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (preferenceId != null) {
                    callback.onPreferenceAuthenticationError(preferenceId, PreferenceVerificationMethod.STRONG_BIOMETRIC, errorCode.toDomainError())
                    return
                }
                if (!isCurrentEntry(generation)) return
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && keyguardManager.isDeviceSecure) {
                    authenticateWithDeviceCredential(generation)
                    return
                }
                entryInFlight = false
                callback.onAuthenticationError(errorCode.toDomainError())
            }
        },
    )

    private var biometricPromptIsEntry = callback.pendingPreferenceRequestId() == null
    private var biometricPrompt = createBiometricPrompt(callback.pendingPreferenceRequestId(), entryGeneration)

    /** Invalidates callbacks before dismissing a prompt. Credential results are ignored too. */
    fun cancelEntryAuthentication() {
        entryInFlight = false
        entryGeneration++
        credentialGeneration = null
        credentialFallbackInProgress = false
        if (biometricPromptIsEntry) biometricPrompt.cancelAuthentication()
    }

    fun hasDeviceCredential(): Boolean = keyguardManager.isDeviceSecure

    fun hasStrongBiometric(): Boolean =
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun availability(): LocalAuthenticationAvailability {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val status = biometricManager.canAuthenticate(MODERN_AUTHENTICATORS)
            return when {
                status == BiometricManager.BIOMETRIC_SUCCESS ->
                    LocalAuthenticationAvailability.AVAILABLE
                keyguardManager.isDeviceSecure ->
                    LocalAuthenticationAvailability.AVAILABLE
                else -> LocalAuthenticationAvailability.SETUP_REQUIRED
            }
        }

        val biometricStatus = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        )
        if (
            biometricStatus == BiometricManager.BIOMETRIC_SUCCESS ||
            keyguardManager.isDeviceSecure
        ) {
            return LocalAuthenticationAvailability.AVAILABLE
        }
        return LocalAuthenticationAvailability.SETUP_REQUIRED
    }

    fun authenticate() {
        if (credentialFallbackInProgress || !callback.isEntryAuthenticationExpected()) return
        entryInFlight = true
        val generation = ++entryGeneration
        biometricPromptIsEntry = true
        biometricPrompt = createBiometricPrompt(null, generation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (availability() != LocalAuthenticationAvailability.AVAILABLE) {
                callback.onAuthenticationError(availability().toDomainError())
                return
            }
            biometricPrompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Desbloquear Bitavida")
                    .setSubtitle("Confirma tu identidad con Android")
                    .setAllowedAuthenticators(MODERN_AUTHENTICATORS)
                    .setConfirmationRequired(true)
                    .build(),
            )
            return
        }

        val biometricStatus = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        )
        when {
            biometricStatus == BiometricManager.BIOMETRIC_SUCCESS ->
                biometricPrompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Desbloquear Bitavida")
                        .setSubtitle("Usa una biometría segura")
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG,
                        )
                        .setNegativeButtonText(
                            if (keyguardManager.isDeviceSecure) {
                                "Usar bloqueo del dispositivo"
                            } else {
                                "Cancelar"
                            },
                        )
                        .setConfirmationRequired(true)
                        .build(),
                )
            keyguardManager.isDeviceSecure -> authenticateWithDeviceCredential(generation)
            else -> callback.onAuthenticationError(
                LocalAuthenticationError.SETUP_REQUIRED,
            )
        }
    }

    /** Starts a system prompt. The host routes its terminal callback to the retained request. */
    fun confirmBiometricPreference(id: String, method: PreferenceVerificationMethod) {
        if (!hasDeviceCredential()) {
            callback.onPreferenceAuthenticationError(id, method, LocalAuthenticationError.SETUP_REQUIRED)
            return
        }
        if (method == PreferenceVerificationMethod.DEVICE_CREDENTIAL) {
            authenticatePreferenceCredential(id)
            return
        }
        if (!hasStrongBiometric()) {
            callback.onPreferenceAuthenticationError(id, PreferenceVerificationMethod.STRONG_BIOMETRIC, LocalAuthenticationError.SETUP_REQUIRED)
            return
        }
        biometricPromptIsEntry = false
        biometricPrompt = createBiometricPrompt(id, entryGeneration)
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirmar biometría")
                .setSubtitle("Confirma para activar la protección de acceso")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancelar")
                .setConfirmationRequired(true)
                .build(),
        )
    }

    /** Credential-only recovery cancels an abandoned preference change without applying it. */
    @Suppress("DEPRECATION")
    fun authenticatePreferenceCredential(id: String) {
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Confirmar cambio en Bitavida", "Confirma tu PIN, patrón o contraseña del dispositivo.",
        )
        if (intent == null) {
            callback.onPreferenceAuthenticationError(id, PreferenceVerificationMethod.DEVICE_CREDENTIAL, LocalAuthenticationError.SETUP_REQUIRED)
            return
        }
        preferenceCredentialId = id
        try {
            preferenceCredentialLauncher.launch(intent)
        } catch (_: Exception) {
            preferenceCredentialId = null
            callback.onPreferenceAuthenticationError(id, PreferenceVerificationMethod.DEVICE_CREDENTIAL, LocalAuthenticationError.TEMPORARILY_UNAVAILABLE)
        }
    }

    fun openSecuritySetup() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    MODERN_AUTHENTICATORS,
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        runCatching { activity.startActivity(intent) }
            .recoverCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            .onFailure {
                callback.onAuthenticationError(LocalAuthenticationError.UNSUPPORTED)
            }
    }

    @Suppress("DEPRECATION") // Required native fallback for DEVICE_CREDENTIAL on API 26–29.
    private fun authenticateWithDeviceCredential(generation: Long) {
        if (!isCurrentEntry(generation)) return
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Desbloquear Bitavida",
            "Confirma tu PIN, patrón o contraseña del dispositivo.",
        )
        if (intent == null) {
            callback.onAuthenticationError(LocalAuthenticationError.SETUP_REQUIRED)
            return
        }
        credentialFallbackInProgress = true
        credentialGeneration = generation
        try {
            credentialConfirmationLauncher.launch(intent)
        } catch (_: Exception) {
            credentialFallbackInProgress = false
            credentialGeneration = null
            if (isCurrentEntry(generation)) {
                entryInFlight = false
                callback.onAuthenticationError(LocalAuthenticationError.TEMPORARILY_UNAVAILABLE)
            }
        }
    }

    private fun LocalAuthenticationAvailability.toDomainError(): LocalAuthenticationError =
        when (this) {
            LocalAuthenticationAvailability.SETUP_REQUIRED ->
                LocalAuthenticationError.SETUP_REQUIRED
            LocalAuthenticationAvailability.TEMPORARILY_UNAVAILABLE ->
                LocalAuthenticationError.TEMPORARILY_UNAVAILABLE
            LocalAuthenticationAvailability.UNSUPPORTED ->
                LocalAuthenticationError.UNSUPPORTED
            LocalAuthenticationAvailability.CHECKING,
            LocalAuthenticationAvailability.AVAILABLE,
            -> LocalAuthenticationError.FAILED
        }

    private fun Int.toDomainError(): LocalAuthenticationError = when (this) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        -> LocalAuthenticationError.CANCELLED
        BiometricPrompt.ERROR_LOCKOUT,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> LocalAuthenticationError.LOCKOUT
        BiometricPrompt.ERROR_HW_UNAVAILABLE ->
            LocalAuthenticationError.TEMPORARILY_UNAVAILABLE
        BiometricPrompt.ERROR_NO_BIOMETRICS ->
            LocalAuthenticationError.SETUP_REQUIRED
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
            LocalAuthenticationError.SETUP_REQUIRED
        BiometricPrompt.ERROR_HW_NOT_PRESENT ->
            LocalAuthenticationError.UNSUPPORTED
        else -> LocalAuthenticationError.FAILED
    }

    private companion object {
        const val MODERN_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
