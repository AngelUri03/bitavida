package com.bitavida.app.data.access

import android.content.Context
import androidx.core.content.edit

class AppProtectionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    val isProtectionEnabled: Boolean
        get() = preferences.getBoolean(KEY_PROTECTION_ENABLED, false)

    fun setProtectionEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_PROTECTION_ENABLED, enabled)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "app_access_preferences"
        const val KEY_PROTECTION_ENABLED = "protection_enabled"
    }
}
