package com.bitavida.app.data.access

import android.content.Context
import android.content.SharedPreferences

/** One persisted decision governs access by both biometrics and device credentials. */
class AppProtectionPreferences internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    /**
     * Earlier versions let the settings switch disable biometrics while leaving the
     * credential gate enabled. Honour that visible opt-out exactly once. A pending
     * request from that older implementation cannot be completed in a new process:
     * its target and verification callback were not persisted.
     *
     * This synchronous load occurs before the access UI is composed or any prompt
     * is launched; failure to persist migration is reported rather than guessed.
     */
    fun loadProtectionEnabled(): Boolean {
        if (preferences.getInt(KEY_POLICY_VERSION, 0) < CURRENT_POLICY_VERSION) {
            val enabled = preferences.getBoolean(KEY_PROTECTION_ENABLED, false) &&
                preferences.getBoolean(KEY_BIOMETRIC_ALLOWED, true)
            check(preferences.edit()
                .putBoolean(KEY_PROTECTION_ENABLED, enabled)
                .putBoolean(KEY_BIOMETRIC_ALLOWED, true)
                .putInt(KEY_POLICY_VERSION, CURRENT_POLICY_VERSION)
                .remove(KEY_PENDING_PREFERENCE)
                .commit()) { "Unable to load the access policy" }
        }
        return preferences.getBoolean(KEY_PROTECTION_ENABLED, false)
    }

    val isProtectionEnabled: Boolean
        get() = loadProtectionEnabled()

    /** Non-sensitive correlation token; never authorization or biometric material. */
    val pendingPreferenceRequestId: String?
        get() = preferences.getString(KEY_PENDING_PREFERENCE, null)

    fun recordPendingPreference(id: String): Boolean =
        preferences.edit().putString(KEY_PENDING_PREFERENCE, id).commit()

    fun clearPendingPreference(id: String): Boolean {
        if (pendingPreferenceRequestId != id) return false
        return preferences.edit().remove(KEY_PENDING_PREFERENCE).commit()
    }

    /**
     * A process that lost its retained controller cannot accept the old callback.
     * Discard only its correlation token; never change the applied access policy.
     * A matching request retained across Activity recreation remains active.
     */
    fun clearAbandonedPreference(retainedRequestId: String?): Boolean {
        val stored = pendingPreferenceRequestId ?: return true
        return stored == retainedRequestId || clearPendingPreference(stored)
    }

    /** Atomically complete the verified change; never mark it complete before persistence. */
    fun completePreferenceChange(id: String, enabled: Boolean): Boolean {
        if (pendingPreferenceRequestId != id) return false
        return preferences.edit()
            .putBoolean(KEY_PROTECTION_ENABLED, enabled)
            .putBoolean(KEY_BIOMETRIC_ALLOWED, true)
            .remove(KEY_PENDING_PREFERENCE)
            .commit()
    }

    /** Onboarding controls the same access policy. */
    fun setProtectionEnabled(enabled: Boolean): Boolean =
        preferences.edit()
            .putBoolean(KEY_PROTECTION_ENABLED, enabled)
            .putBoolean(KEY_BIOMETRIC_ALLOWED, true)
            .remove(KEY_PENDING_PREFERENCE)
            .commit()

    private companion object {
        const val PREFERENCES_NAME = "app_access_preferences"
        const val KEY_PROTECTION_ENABLED = "protection_enabled"
        const val KEY_BIOMETRIC_ALLOWED = "biometric_allowed"
        const val KEY_POLICY_VERSION = "access_policy_version"
        const val CURRENT_POLICY_VERSION = 1
        const val KEY_PENDING_PREFERENCE = "pending_biometric_preference"
    }
}
