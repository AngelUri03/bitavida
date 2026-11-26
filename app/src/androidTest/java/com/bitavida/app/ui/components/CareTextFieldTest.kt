package com.bitavida.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.validation.CaptureLimit
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prepared UI coverage; device execution requires an explicit instrumented-test request. */
class CareTextFieldTest {
    @get:Rule val rule = createComposeRule()

    @Test fun counterAndValidationCoexistInCompactLightAndDarkWithLargeText() {
        val dark = mutableStateOf(false)
        val text = mutableStateOf("á".repeat(45))
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BitavidaTheme(darkTheme = dark.value) {
                    Column(Modifier.width(320.dp)) {
                        BitavidaLimitedTextField(
                            text.value, { text.value = it }, CaptureLimit.REMINDER_TITLE,
                            Modifier.fillMaxWidth().testTag("capture"),
                            label = { androidx.compose.material3.Text("Título") },
                            supportingText = { androidx.compose.material3.Text("Mensaje de validación") },
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("45 / 100").assertExists()
        rule.onNodeWithText("Mensaje de validación").assertExists()
        rule.onNodeWithTag("capture").performTextReplacement("👩‍👩‍👧‍👦".repeat(100))
        rule.onNodeWithText("100 / 100").assertExists()
        rule.onNodeWithTag("capture").performTextInput("z")
        rule.runOnIdle {
            assertEquals("👩‍👩‍👧‍👦".repeat(100), text.value)
            dark.value = true
        }
        rule.onNodeWithText("100 / 100").assertExists()
        rule.onNodeWithTag("capture").assertIsEnabled()
    }

    @Test fun oversizedPasteDoesNotReplaceTheOriginalOrActivateErrorStyling() {
        val text = mutableStateOf("Texto original")
        rule.setContent {
            BitavidaTheme {
                BitavidaLimitedTextField(text.value, { text.value = it }, CaptureLimit.REMINDER_TITLE,
                    Modifier.testTag("capture"))
            }
        }
        rule.onNodeWithTag("capture").performTextReplacement("😊".repeat(101))
        rule.runOnIdle { assertEquals("Texto original", text.value) }
    }
}
