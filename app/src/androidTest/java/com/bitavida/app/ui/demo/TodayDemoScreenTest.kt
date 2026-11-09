package com.bitavida.app.ui.demo

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.brand.AnimatedBitavidaMark
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayDemoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalState_confirmsOnlyTheEphemeralDemo() {
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen()
            }
        }

        composeRule.onNodeWithTag("confirm_medication")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithContentDescription(
            "Estado: toma ficticia confirmada en demostración",
        ).assertIsDisplayed()
    }

    @Test
    fun emptyState_explainsMissingDataAndOffersAction() {
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(state = TodayDemoState.Empty)
            }
        }

        composeRule.onNodeWithText("Tu hilo comienza con el primer registro")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("empty_primary_action")
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
    }

    @Test
    fun appointmentEvent_expandsDemonstrationContext() {
        composeRule.setContent {
            BitavidaTheme {
                TodayDemoScreen(motionEnabledOverride = false)
            }
        }

        composeRule.onNodeWithTag("appointment_event").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Esta cita es un ejemplo visual. No existe una agenda, recordatorio ni persistencia asociados.",
        ).assertIsDisplayed()
    }

    @Test
    fun animatedMark_doesNotRestartAfterRecomposition() {
        composeRule.mainClock.autoAdvance = false
        val recompositionTrigger = mutableIntStateOf(0)
        var completions = 0

        composeRule.setContent {
            recompositionTrigger.intValue
            BitavidaTheme {
                AnimatedBitavidaMark(
                    modifier = Modifier.size(120.dp),
                    motionEnabledOverride = true,
                    onAnimationFinished = { completions++ },
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(1, completions) }

        composeRule.runOnUiThread { recompositionTrigger.intValue++ }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(1, completions) }
    }
}
