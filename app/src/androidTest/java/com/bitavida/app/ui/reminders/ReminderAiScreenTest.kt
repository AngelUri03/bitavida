package com.bitavida.app.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.bitavida.app.data.education.ReminderEducationLibrary
import com.bitavida.app.domain.education.*
import com.bitavida.app.ui.education.*
import com.bitavida.app.ui.theme.BitavidaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prepared UI checks. Execution requires an explicitly authorized device session. */
class ReminderAiScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightHomeShowsOrganizationIdentityAndLibraryAccess() = home(false)
    @Test fun darkHomeShowsOrganizationIdentityAndLibraryAccess() = home(true)

    private fun home(dark: Boolean) {
        val state = BitavidaAiUiState(
            navigationContext = EducationGroup.REMINDERS, group = EducationGroup.REMINDERS)
        compose.setContent {
            BitavidaTheme(darkTheme = dark) {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        BitavidaAiContent(state, {}, motionOverride = false)
                    }
                }
            }
        }
        compose.onNodeWithText("Bitavida IA").assertIsDisplayed()
        compose.onNodeWithText("IA en tu dispositivo · Sin Internet").assertIsDisplayed()
        compose.onNodeWithText(state.suggestions[2].question).assertDoesNotExist()
        compose.onNodeWithText("Biblioteca de Recordatorios").performScrollTo().assertIsDisplayed()
    }

    @Test fun guidesKeepPracticalExamplesReadOnlyAndSecondary() {
        val entry = ReminderEducationLibrary.entries.values.first { it.reminderExamples.isNotEmpty() }
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            BitavidaTheme {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        AiAnswerCard(
                            EducationResult.Answer(entry),
                            { if (it is BitavidaAiAction.ToggleDetails) expanded = !expanded },
                            false, moreExpanded = expanded,
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(entry.explanation).assertIsDisplayed()
        compose.onNodeWithTag("reminder_guide_complementary").assertDoesNotExist()
        compose.onNodeWithText("Crear recordatorio").assertDoesNotExist()
        compose.onNodeWithText("Usar sugerencia").assertDoesNotExist()
        compose.onNodeWithText("Información complementaria").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_guide_complementary").assertExists()
        compose.onNodeWithText(entry.reminderExamples.first().title).assertExists()
        compose.onNodeWithText("Crear recordatorio").assertDoesNotExist()
    }

    @Test fun libraryUsesCompactTitlesAndOpensAnIndependentTopic() {
        val entry = ReminderEducationLibrary.entries.values.first()
        var selected: EducationTopic? = null
        compose.setContent {
            BitavidaTheme {
                Surface {
                    AiPageList(
                        AiPageEntry(1, AiPage.LIBRARY),
                        BitavidaAiUiState(navigationContext = EducationGroup.REMINDERS),
                        ReminderEducationLibrary.entries.values.toList(),
                        { if (it is BitavidaAiAction.ReadTopic) selected = it.topic },
                        rememberLazyListState(), false,
                    )
                }
            }
        }
        compose.onNodeWithText(entry.explanation).assertDoesNotExist()
        compose.onNodeWithTag("reminder_guide_" + entry.topic.name).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(entry.topic, selected) }
    }
}
