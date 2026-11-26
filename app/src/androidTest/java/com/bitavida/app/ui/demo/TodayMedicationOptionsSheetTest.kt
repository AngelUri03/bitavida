package com.bitavida.app.ui.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.usecase.MedicationOccurrenceTiming
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prepared Compose coverage; device execution is left to the developer. */
class TodayMedicationOptionsSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun eachMenuChoiceOpensOnlyItsRequestedFlow() {
        val actions = mutableListOf<String>()
        rule.setContent {
            BitavidaTheme {
                TodayMedicationOptionsSheet(
                    item(), false, true,
                    onOtherTime = { actions += "time" },
                    onWithNote = { actions += "note" },
                    onNotTaken = { actions += "not_taken" },
                    onConsult = { actions += "consult" },
                    onDismiss = { actions += "dismiss" },
                )
            }
        }
        rule.onNodeWithText("Opciones de la toma").assertIsDisplayed()
        rule.onNodeWithTag("today_medication_other_time").performClick()
        rule.onNodeWithTag("today_medication_with_note").performClick()
        rule.onNodeWithTag("today_medication_not_taken").performClick()
        rule.onNodeWithTag("today_medication_consult").performClick()
        rule.runOnIdle { assertEquals(listOf("time", "note", "not_taken", "consult"), actions) }
    }

    private fun item(): TodayMedicationItem {
        val starts = LocalDateTime.of(2026, 11, 20, 9, 0)
        return TodayMedicationItem(UpcomingMedicationAdministration(
            id = "fictional-dose", medicationPlanId = "fictional-plan",
            medicationPlanRevisionId = "fictional-revision", administrationId = "fictional-slot",
            medicationName = "Medicamento ficticio", form = MedicationProductForm.TABLET,
            amount = MedicationAmount("1", "tableta"), timing = MedicationOccurrenceTiming.EXACT_TIME,
            startsAt = starts, endsAt = null, activityRelation = null, customActivity = null,
        ))
    }
}
