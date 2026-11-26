package com.bitavida.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The switch reflects the committed access setting, including during a cancelled change. */
class ProtectionSettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun switchDisplaysWholeAccessProtectionAndDoesNotChangeBeforeConfirmation() {
        var protection by mutableStateOf(true)
        var requested: Boolean? = null
        rule.setContent {
            BitavidaTheme {
                BitavidaSettingsScreen(
                    caregiver = "Cuidadora ficticia",
                    patient = "Paciente ficticio",
                    protectionEnabled = protection,
                    notificationsAllowed = false,
                    onBack = {},
                    onSystemNotificationSettings = {},
                    onBiometricChange = { requested = it },
                    section = "security",
                )
            }
        }

        val accessSwitch = rule.onNodeWithContentDescription("Proteger acceso a Bitavida")
        accessSwitch.assertIsOn().performClick()
        rule.runOnIdle { assertEquals(false, requested) }
        // A rejected or cancelled system confirmation has not committed a new setting.
        accessSwitch.assertIsOn()
        rule.onNodeWithText("Protección activada").assertIsDisplayed()

        rule.runOnIdle { protection = false }
        accessSwitch.assertIsOff()
        rule.onNodeWithText("Protección desactivada").assertIsDisplayed()
        rule.onNodeWithText(
            "Si está activada, confirma con la biometría o el código configurados en tu teléfono.",
        ).assertExists()
    }
}
