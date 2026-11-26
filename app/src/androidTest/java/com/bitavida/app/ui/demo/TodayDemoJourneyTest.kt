package com.bitavida.app.ui.demo

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayDemoJourneyTest {
    @get:Rule val rule = createComposeRule()

    @Test fun compactLargeTextAndThemesKeepSpecificActionsAndTheNotesCarouselReachable() {
        val dark = mutableStateOf(false)
        val invoked = mutableListOf<String>()
        val original = todayPreviewProjection(multiple = false)
        val projection = original.copy(
            medications = original.medications.map { item ->
                item.copy(occurrence = item.occurrence.copy(
                    medicationName = "Medicamento ficticio con nombre extenso para revisar la legibilidad",
                ))
            },
            reminders = original.reminders.map {
                it.copy(title = "Preparar documentos y objetos personales para la salida familiar")
            },
        )
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BitavidaTheme(darkTheme = dark.value) {
                    TodayDemoScreen(
                        Modifier.width(320.dp), currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                        projection = projection, notes = todayPreviewNotes(),
                        motionEnabledOverride = false,
                        onMedicationTaken = { invoked += "medication:" + it.id },
                        onMeasurementRegister = { invoked += "measurement:" + it.id },
                        onObservationRegister = { invoked += "observation:" + it.id },
                        onReminderDone = { invoked += "reminder:" + it.id },
                        onReminderSelected = { invoked += "detail:" + it },
                        onNoteSelected = { invoked += "note:" + it.id },
                    )
                }
            }
        }
        listOf(false, true).forEachIndexed { index, darkTheme ->
            rule.runOnIdle { dark.value = darkTheme }

            scrollTo("today_medication_taken_preview-dose")
            rule.onNodeWithText("Registrar toma", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("today_medication_taken_preview-dose")
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()

            scrollTo("today_measurement_register_CAPILLARY_GLUCOSE:preview-reading")
            rule.onNodeWithText("Registrar medición", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("today_measurement_register_CAPILLARY_GLUCOSE:preview-reading")
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()

            scrollTo("today_observation_register_preview-observation")
            rule.onNodeWithText("Registrar revisión", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("today_observation_register_preview-observation")
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()

            scrollTo("today_reminder_open_preview-reminder")
            rule.onNodeWithTag("today_reminder_open_preview-reminder")
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
            scrollTo("today_reminder_done_preview-reminder")
            rule.onNodeWithText("Marcar como hecho", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("today_reminder_done_preview-reminder")
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()

            scrollTo("today_notes_carousel")
            rule.onNodeWithTag("today_notes_carousel").assertExists()
            rule.onNodeWithTag("today_note_preview-note-one")
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()

            rule.runOnIdle {
                val repetitions = index + 1
                assertEquals(repetitions, invoked.count { it == "medication:preview-dose" })
                assertEquals(repetitions, invoked.count { it == "measurement:CAPILLARY_GLUCOSE:preview-reading" })
                assertEquals(repetitions, invoked.count { it == "observation:preview-observation" })
                assertEquals(repetitions, invoked.count { it == "reminder:preview-reminder" })
                assertEquals(repetitions, invoked.count { it == "detail:preview-reminder" })
                assertEquals(repetitions, invoked.count { it == "note:preview-note-one" })
            }
        }
        rule.onNodeWithText("Resueltos de hoy").assertDoesNotExist()
        rule.onNodeWithText("Próximas actividades").assertDoesNotExist()
    }

    @Test fun dismissingTheExistingOptionsSheetAndSelectingNavigationNeverRegistersATake() {
        val selected = mutableStateOf<TodayMedicationItem?>(null)
        var registrations = 0
        var plans = 0
        var dismissals = 0
        rule.setContent {
            BitavidaTheme {
                TodayDemoScreen(
                    currentDateTime = todayPreviewNow, state = TodayDemoState.HasPlan,
                    projection = todayPreviewProjection(multiple = false), motionEnabledOverride = false,
                    onMedicationTaken = { registrations++ },
                    onMedicationOptions = { selected.value = it },
                    onPlanSelected = { plans++ },
                )
                selected.value?.let { item ->
                    TodayMedicationOptionsSheet(
                        item = item, motionAllowed = false, enabled = true,
                        onOtherTime = {}, onWithNote = {}, onNotTaken = {}, onConsult = {},
                        onDismiss = { dismissals++; selected.value = null },
                    )
                }
            }
        }
        rule.onNodeWithText("Mi día").performClick()
        rule.runOnIdle {
            assertEquals(0, plans)
            assertEquals(0, registrations)
        }
        scrollTo("today_medication_taken_preview-dose")
        rule.onNodeWithTag("today_medication_taken_preview-dose").performTouchInput { longClick() }
        rule.onNodeWithText("Opciones de la toma").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, registrations) }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.Dismiss)
        rule.onNodeWithText("Opciones de la toma").assertDoesNotExist()
        rule.onNodeWithTag("today_medication_taken_preview-dose").assertExists()
        rule.onNodeWithText("Plan").performClick()
        rule.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(1, plans)
            assertEquals(0, registrations)
        }
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("today_scroll").performScrollToNode(hasTestTag(tag))
    }
}
