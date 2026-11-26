package com.bitavida.app.domain.access

import org.junit.Assert.*
import org.junit.Test

class BiometricPreferenceChangeControllerTest {
    @Test fun disablingRequiresCredentialAndCancellationPreservesSetting() {
        val policy = BiometricPreferenceChangeController()
        assertEquals(BiometricPreferenceStart.CredentialRequired, policy.start(true, false, false, true))
        val request = (policy.start(true, false, true, true) as BiometricPreferenceStart.Authenticate).request
        assertEquals(PreferenceVerificationMethod.DEVICE_CREDENTIAL, request.method)
        assertEquals(BiometricPreferenceStart.Busy, policy.start(true, false, true, true))
        assertNull(policy.finish(request.id, request.method, false, true, true, true))
        assertNull(policy.pending)
    }

    @Test fun enablingUsesStrongBiometricWhenAvailableAndRejectsStaleCallbacks() {
        val policy = BiometricPreferenceChangeController()
        val request = (policy.start(false, true, true, true) as BiometricPreferenceStart.Authenticate).request
        assertEquals(PreferenceVerificationMethod.STRONG_BIOMETRIC, request.method)
        assertNull(policy.finish("stale-request", request.method, true, true, true, true))
        assertEquals(request, policy.pending)
        assertEquals(true, policy.finish(request.id, request.method, true, true, true, true))
        assertNull(policy.finish(request.id, request.method, true, true, true, true))
    }

    @Test fun enablingWorksWithDeviceCredentialWhenNoBiometricIsConfigured() {
        val policy = BiometricPreferenceChangeController()
        val request = (policy.start(false, true, true, false) as BiometricPreferenceStart.Authenticate).request
        assertEquals(PreferenceVerificationMethod.DEVICE_CREDENTIAL, request.method)
        assertEquals(true, policy.finish(request.id, request.method, true, true, true, false))
    }

    @Test fun callbackCannotChangePreferenceIfLockedOrCredentialDisappears() {
        val policy = BiometricPreferenceChangeController()
        val first = (policy.start(true, false, true, true) as BiometricPreferenceStart.Authenticate).request
        assertNull(policy.finish(first.id, first.method, true, false, true, true))
        val second = (policy.start(true, false, true, true) as BiometricPreferenceStart.Authenticate).request
        assertNull(policy.finish(second.id, second.method, true, true, false, true))
        assertNull(policy.pending)
    }

    @Test fun wrongMethodCannotApproveOrCancelRequest() {
        val policy = BiometricPreferenceChangeController()
        val request = (policy.start(true, false, true, true) as BiometricPreferenceStart.Authenticate).request
        assertNull(policy.finish(request.id, PreferenceVerificationMethod.STRONG_BIOMETRIC, true, true, true, true))
        assertEquals(request, policy.pending)
        assertEquals(false, policy.finish(request.id, request.method, true, true, true, true))
    }

    @Test fun enablingFailsIfSelectedBiometricDisappears() {
        val policy = BiometricPreferenceChangeController()
        val request = (policy.start(false, true, true, true) as BiometricPreferenceStart.Authenticate).request
        assertNull(policy.finish(request.id, request.method, true, true, true, false))
        assertNull(policy.pending)
    }

    @Test fun freshControllerCannotApplyPriorProcessResult() {
        val beforeDeath = BiometricPreferenceChangeController()
        val request = (beforeDeath.start(true, false, true, true) as BiometricPreferenceStart.Authenticate).request
        val recreated = BiometricPreferenceChangeController()
        val newer = (recreated.start(true, false, true, true) as BiometricPreferenceStart.Authenticate).request
        assertNotEquals(request.id, newer.id)
        assertNull(recreated.finish(request.id, request.method, true, true, true, true))
        assertEquals(newer, recreated.pending)
    }
}
