package com.bitavida.app.ui.medical

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prepared UI regression; not executed without an explicit instrumented-test request. */
class PlanOverviewCountersTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fourCountersShareSingularPluralAndUpdateAcrossThemesWhileNotesKeepsItsDestination() {
        val dark = mutableStateOf(false)
        val medicationCount = mutableStateOf(3)
        var opened: PlanSection? = null
        rule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                BitavidaTheme(darkTheme = dark.value) {
                    MedicalPlanScreen(
                        state = MedicalPlanUiState(isLoading = false),
                        caregiverDisplayName = "Cuidadora ficticia",
                        personUnderCareDisplayName = "Persona ficticia",
                        onAction = { if (it is MedicalPlanAction.OpenSection) opened = it.section },
                        onTodaySelected = {},
                        activeMedicationCount = medicationCount.value,
                        activeMeasurementCount = 1,
                        activeObservationCount = 0,
                        activeReminderCount = 2,
                    )
                }
            }
        }
        assertCount("medication", "3 activos")
        assertCount("measurement", "1 activo")
        assertCount("observation", "0 activos")
        assertCount("reminder", "2 activos")
        rule.runOnIdle { dark.value = true; medicationCount.value = 1 }
        assertCount("medication", "1 activo")
        rule.onNodeWithText("Otras indicaciones").assertDoesNotExist()
        rule.onNodeWithText("Notas").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(PlanSection.OTHER_INDICATIONS, opened) }
    }

    private fun assertCount(module: String, label: String) {
        rule.onNodeWithTag("plan_${module}_active_count", useUnmergedTree = true)
            .performScrollTo().assertTextEquals(label)
    }
}
