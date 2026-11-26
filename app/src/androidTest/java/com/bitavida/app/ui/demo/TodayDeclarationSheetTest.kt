package com.bitavida.app.ui.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.bitavida.app.domain.model.*
import com.bitavida.app.ui.measurements.GlucoseOccurrenceManagementSheet
import com.bitavida.app.ui.measurements.GlucoseUiState
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

/** Prepared device coverage; not executed automatically. */
class TodayDeclarationSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun todayNoSeRealizoStartsAtTheApprovedReasonAndConfirmationStep() {
        val event = GlucoseScheduledOccurrence(
            "fictional-event", "fictional-follow-up", "fictional-revision", "fictional-slot",
            GlucoseFollowUpTimingType.EXACT_TIME, LocalDateTime.of(2026, 11, 20, 9, 0),
            suggestedContext = GlucoseContext.FASTING, reminderAt = null,
        )
        rule.setContent {
            BitavidaTheme {
                GlucoseOccurrenceManagementSheet(
                    GlucoseUiState(loading = false, selectedOccurrence = event), false, {},
                    startNotPerformed = true,
                )
            }
        }
        rule.onNodeWithText("Motivo opcional").assertIsDisplayed()
        rule.onNodeWithText("Confirmar que no se realizó").assertIsDisplayed()
        rule.onNodeWithText("Registrar medición").assertDoesNotExist()
        rule.onNodeWithText("Historial de la declaración").assertDoesNotExist()
    }
}
