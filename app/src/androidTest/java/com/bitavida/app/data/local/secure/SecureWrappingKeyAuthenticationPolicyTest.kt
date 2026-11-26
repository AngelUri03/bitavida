package com.bitavida.app.data.local.secure

import android.security.keystore.KeyInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Read-only inspection of the existing production wrapping key's policy.
 * No database is opened; no key, envelope, preference, or record is changed.
 * Requires explicit invocation; never creates or replaces an alias.
 */
@RunWith(AndroidJUnit4::class)
class SecureWrappingKeyAuthenticationPolicyTest {
    @Test
    fun existingWrappingKeyDoesNotRequireInteractiveAuthentication() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Explicit key-policy inspection required",
            InstrumentationRegistry.getArguments().getString("bitavida_key_policy") == "inspect-v1",
        )
        check(instrumentation.targetContext.packageName == "com.bitavida.app")
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "com.bitavida.app.secure-spike.database-key.v1"
        assertTrue("Existing wrapping key must be present", store.containsAlias(alias))
        val key = store.getKey(alias, null) as? SecretKey
            ?: error("Existing wrapping key is unavailable")
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertFalse(
            "Existing wrapping key requires user authentication; do not change configuration without key migration",
            info.isUserAuthenticationRequired,
        )
    }
}
