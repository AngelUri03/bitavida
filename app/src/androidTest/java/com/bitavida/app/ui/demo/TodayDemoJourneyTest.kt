package com.bitavida.app.ui.demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Rule
import org.junit.Test

/** Verifies that the complete fictitious demo journey remains reachable and accessible. */
class TodayDemoJourneyTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun demoJourneyStatesRemainReachable() {
        val font = mutableFloatStateOf(1f)
        val motion = mutableStateOf(true)
        val dark = mutableStateOf(false)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, font.floatValue),
            ) {
                BitavidaTheme(darkTheme = dark.value) {
                    TodayDemoScreen(motionEnabledOverride = motion.value)
                }
            }
        }

        val screen = rule.onNodeWithTag("today_scroll")
        screen.performTouchInput { swipeUp(durationMillis = 1_000) }
        screen.performTouchInput { swipeDown(durationMillis = 700) }

        rule.onNodeWithTag("confirm_medication")
            .performScrollTo()
            .performClick()
            .assertIsNotEnabled()
            .assertTextContains("Confirmación registrada en demo")

        rule.onNodeWithTag("appointment_event")
            .performScrollTo()
            .performClick()
        rule.onNodeWithText("Ocultar contexto").assertIsDisplayed()

        rule.runOnIdle { dark.value = true }
        rule.waitForIdle()
        rule.onNodeWithText("Ocultar contexto").assertIsDisplayed()

        rule.runOnIdle { font.floatValue = 2f }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Citas").performClick()
        rule.onNodeWithContentDescription("Mi día").assertIsSelected()

        rule.runOnIdle {
            motion.value = false
            font.floatValue = 1f
            dark.value = false
        }
        rule.waitForIdle()
        rule.onNodeWithTag("confirm_medication")
            .assertIsNotEnabled()
            .assertTextContains("Confirmación registrada en demo")
        rule.onNodeWithText("Ocultar contexto").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mi día").assertIsSelected()
    }
}
