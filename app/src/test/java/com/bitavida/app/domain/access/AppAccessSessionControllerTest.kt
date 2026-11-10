package com.bitavida.app.domain.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccessSessionControllerTest {
    @Test
    fun onlySuccessfulAuthenticationCanUnlock() {
        val controller = AppAccessSessionController()

        assertEquals(AppAccessSessionState.LOCKED, controller.state.value)
        assertFalse(controller.beginAuthentication())
        assertFalse(controller.authenticationSucceeded())

        controller.startProtectedSession()
        assertTrue(controller.beginAuthentication())
        assertEquals(AppAccessSessionState.AUTHENTICATING, controller.state.value)

        controller.authenticationEndedWithoutSuccess()
        assertEquals(AppAccessSessionState.LOCKED, controller.state.value)

        assertTrue(controller.beginAuthentication())
        assertTrue(controller.authenticationSucceeded())
        assertEquals(AppAccessSessionState.UNLOCKED, controller.state.value)
    }

    @Test
    fun locksAfterSixtyRealSecondsInBackground() {
        val controller = AppAccessSessionController(
            policy = AppAccessPolicy(backgroundTimeoutMillis = 60_000L),
        )
        controller.startProtectedSession()
        controller.beginAuthentication()
        controller.authenticationSucceeded()

        controller.onBackgrounded(elapsedRealtimeMillis = 1_000L)
        assertFalse(controller.onForegrounded(elapsedRealtimeMillis = 60_999L))
        assertEquals(AppAccessSessionState.UNLOCKED, controller.state.value)

        controller.onBackgrounded(elapsedRealtimeMillis = 70_000L)
        assertTrue(controller.onForegrounded(elapsedRealtimeMillis = 130_000L))
        assertEquals(AppAccessSessionState.LOCKED, controller.state.value)
    }

    @Test
    fun manualLockIsImmediate() {
        val controller = AppAccessSessionController()
        controller.startProtectedSession()
        controller.beginAuthentication()
        controller.authenticationSucceeded()

        controller.lockImmediately()

        assertEquals(AppAccessSessionState.LOCKED, controller.state.value)
    }
}
