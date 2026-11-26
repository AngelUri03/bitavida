package com.bitavida.app.ui.settings

import com.bitavida.app.domain.access.AppAccessPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentDiagnosticGrantTest {
    @Test fun grantExpiresNoLaterThanTheApplicationBackgroundTimeout() {
        val issued = 10_000L
        val timeout = AppAccessPolicy.DEFAULT_BACKGROUND_TIMEOUT_MILLIS
        assertFalse(diagnosticGrantValid(null, issued))
        assertFalse(diagnosticGrantValid(issued, issued - 1))
        assertTrue(diagnosticGrantValid(issued, issued))
        assertTrue(diagnosticGrantValid(issued, issued + timeout - 1))
        assertFalse(diagnosticGrantValid(issued, issued + timeout))
        assertFalse(diagnosticGrantValid(issued, issued + timeout + 1))
    }
}
