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

class AndroidLocalAuthenticator(
    private val activity: FragmentActivity,
    private val callback: Callback,
) {
    interface Callback {
        fun onAuthenticationSucceeded()
        fun onAuthenticationAttemptRejected()
        fun onAuthenticationError(error: LocalAuthenticationError)
    }

    private val keyguardManager =
        activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val biometricManager = BiometricManager.from(activity)
    private var credentialFallbackInProgress = false

    private val credentialConfirmationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        credentialFallbackInProgress = false
        if (result.resultCode == Activity.RESULT_OK) {
            callback.onAuthenticationSucceeded()
        } else {
            callback.onAuthenticationError(LocalAuthenticationError.CANCELLED)
        }
    }

    private val biometricPrompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult,
            ) {
                callback.onAuthenticationSucceeded()
            }

            override fun onAuthenticationFailed() {
                callback.onAuthenticationAttemptRejected()
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence,
            ) {
                if (
                    (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) &&
                    (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) &&
                    keyguardManager.isDeviceSecure
                ) {
                    authenticateWithDeviceCredential()
                    return
                }
                callback.onAuthenticationError(errorCode.toDomainError())
            }
        },
    )

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
        if (credentialFallbackInProgress) return
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
            keyguardManager.isDeviceSecure -> authenticateWithDeviceCredential()
            else -> callback.onAuthenticationError(
                LocalAuthenticationError.SETUP_REQUIRED,
            )
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
    private fun authenticateWithDeviceCredential() {
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Desbloquear Bitavida",
            "Confirma tu PIN, patrón o contraseña del dispositivo.",
        )
        if (intent == null) {
            callback.onAuthenticationError(LocalAuthenticationError.SETUP_REQUIRED)
            return
        }
        credentialFallbackInProgress = true
        credentialConfirmationLauncher.launch(intent)
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
