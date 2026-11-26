package com.bitavida.app.data.access

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses an isolated preference file and never touches the installed access setting. */
@RunWith(AndroidJUnit4::class)
class AppProtectionPreferencesTest {
    @Test fun legacySwitchOffDisablesWholeGateAndStaysDisabledAfterRecreation() {
        withPreferences { raw ->
            assertTrue(raw.edit().putBoolean("protection_enabled", true)
                .putBoolean("biometric_allowed", false)
                .putString("pending_biometric_preference", "abandoned").commit())
            assertFalse(AppProtectionPreferences(raw).loadProtectionEnabled())
            assertFalse(AppProtectionPreferences(raw).isProtectionEnabled)
            assertNull(AppProtectionPreferences(raw).pendingPreferenceRequestId)
        }
    }

    @Test fun freshAndEnabledLegacyPoliciesRemainCorrect() {
        withPreferences { raw ->
            assertFalse(AppProtectionPreferences(raw).loadProtectionEnabled())
            assertTrue(AppProtectionPreferences(raw).setProtectionEnabled(true))
            assertTrue(AppProtectionPreferences(raw).isProtectionEnabled)
            // The old biometric flag no longer switches a migrated policy to credential-only.
            assertTrue(raw.edit().putBoolean("biometric_allowed", false).commit())
            assertTrue(AppProtectionPreferences(raw).isProtectionEnabled)
        }
        withPreferences { raw ->
            assertTrue(raw.edit().putBoolean("protection_enabled", true).commit())
            assertTrue(AppProtectionPreferences(raw).loadProtectionEnabled())
        }
    }

    @Test fun recreationRetainsMatchingRequestButFreshProcessDiscardsOnlyItsToken() {
        withPreferences { raw ->
            val policy = AppProtectionPreferences(raw)
            policy.loadProtectionEnabled()
            assertTrue(policy.setProtectionEnabled(true))
            assertTrue(policy.recordPendingPreference("active"))
            assertTrue(AppProtectionPreferences(raw).clearAbandonedPreference("active"))
            assertEquals("active", policy.pendingPreferenceRequestId)
            assertTrue(AppProtectionPreferences(raw).clearAbandonedPreference(null))
            assertNull(policy.pendingPreferenceRequestId)
            assertTrue(policy.isProtectionEnabled)
        }
        withPreferences { raw ->
            val policy = AppProtectionPreferences(raw)
            policy.loadProtectionEnabled()
            assertTrue(policy.recordPendingPreference("abandoned"))
            assertTrue(policy.clearAbandonedPreference(null))
            assertFalse(policy.isProtectionEnabled)
            assertNull(policy.pendingPreferenceRequestId)
        }
    }

    @Test fun verifiedChangeAndPendingRemovalArePersistedTogether() {
        withPreferences { raw ->
            val policy = AppProtectionPreferences(raw)
            policy.loadProtectionEnabled()
            assertTrue(policy.setProtectionEnabled(true))
            assertTrue(policy.recordPendingPreference("current"))
            assertFalse(policy.completePreferenceChange("stale", false))
            assertTrue(policy.isProtectionEnabled)
            assertEquals("current", policy.pendingPreferenceRequestId)
            assertTrue(policy.completePreferenceChange("current", false))
            val recreated = AppProtectionPreferences(raw)
            assertFalse(recreated.isProtectionEnabled)
            assertNull(recreated.pendingPreferenceRequestId)
        }
    }

    private inline fun withPreferences(block: (android.content.SharedPreferences) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-access-policy-" + UUID.randomUUID()
        val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try { block(raw) } finally { context.deleteSharedPreferences(name) }
    }
}
