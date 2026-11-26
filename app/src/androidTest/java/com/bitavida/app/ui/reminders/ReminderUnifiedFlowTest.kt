package com.bitavida.app.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.education.ReminderEducationCategory
import com.bitavida.app.domain.education.ReminderSuggestion
import com.bitavida.app.domain.education.ReminderTitleAssistance
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.usecase.ReminderDatePreset
import com.bitavida.app.domain.usecase.ReminderIdeaGroup
import com.bitavida.app.ui.components.BitavidaDateTimePickerHost
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Prepared UI verification with fictional text; never touches an alarm, profile or database. */
class ReminderUnifiedFlowTest {
    @get:Rule val compose = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val form = ReminderForm("draft-example", "operation-example", "", "", now.plusHours(2), false)

    @Test fun emptyFormKeepsCreationFocusedAndDateShortcutsOnlyRequestDateChanges() {
        val actions = mutableListOf<RemindersAction>()
        compose.setContent {
            BitavidaTheme {
                BitavidaDateTimePickerHost {
                    Surface {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            ReminderFormContent(form, RemindersUiState(loading = false), { actions += it }, false)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("¿Necesitas ideas?").assertDoesNotExist()
        compose.onNodeWithTag("reminder_context_help").assertDoesNotExist()
        compose.onNodeWithText("Elegir fecha").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithText("Ayúdame a organizarlo").assertDoesNotExist()
        compose.onNodeWithText("Sugerir con Bitavida IA").assertDoesNotExist()
        compose.onNodeWithText("Preguntas relacionadas").assertDoesNotExist()
        compose.onNodeWithText("Hoy", substring = true).performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(listOf(RemindersAction.SetDatePreset(ReminderDatePreset.TODAY)), actions)
        }
        compose.onNodeWithText("Mañana", substring = true).performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(listOf(
                RemindersAction.SetDatePreset(ReminderDatePreset.TODAY),
                RemindersAction.SetDatePreset(ReminderDatePreset.TOMORROW),
            ), actions)
        }
        compose.onNodeWithText("Guardar recordatorio").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test fun simpleTitleSuggestionCanBeAcceptedOrIgnoredInsideTheForm() {
        var action: RemindersAction? = null
        compose.setContent {
            BitavidaTheme(darkTheme = true) {
                BitavidaDateTimePickerHost {
                    Surface {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            ReminderFormContent(
                                form.copy(title = "Pilas del glucometro"),
                                RemindersUiState(loading = false, assistance = ReminderTitleAssistance(
                                    suggestedTitle = "Comprar pilas para el glucómetro")),
                                { action = it }, false,
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Sugerencia: Comprar pilas para el glucómetro").assertIsDisplayed()
        compose.onNodeWithTag("reminder_use_title").performClick()
        compose.runOnIdle { assertEquals(RemindersAction.UseSuggestedTitle, action) }
        compose.onNodeWithTag("reminder_ignore_title").performClick()
        compose.runOnIdle { assertEquals(RemindersAction.IgnoreTitleSuggestion, action) }
        compose.onNodeWithText("Preguntas relacionadas").assertDoesNotExist()
    }

    @Test fun ideasShowOnlyTheSelectedThemeAsCompactSelectableRows() {
        val chosenTitle = "Preparar documentos ficticios"
        var action: RemindersAction? = null
        compose.setContent {
            BitavidaTheme {
                Surface {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        ReminderIdeasContent(
                            RemindersUiState(loading = false, form = form,
                                ideaCategory = ReminderEducationCategory.DOCUMENTS,
                                ideaCatalog = listOf(
                                    ReminderIdeaGroup(ReminderEducationCategory.DOCUMENTS, listOf(
                                        ReminderSuggestion("example-doc", chosenTitle, "Explicación ficticia"),
                                    )),
                                    ReminderIdeaGroup(ReminderEducationCategory.SHOPPING, listOf(
                                        ReminderSuggestion("example-shop", "Recoger pedido ficticio", "Explicación ficticia"),
                                    )),
                                )),
                            { action = it }, false,
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Recoger pedido ficticio").assertDoesNotExist()
        compose.onNodeWithText(chosenTitle).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RemindersAction.UseIdea(chosenTitle), action) }
        compose.onNodeWithText("Encontrar ideas").assertDoesNotExist()
        compose.onNodeWithText("Preguntas relacionadas").assertDoesNotExist()
    }

    @Test fun postponementOptionsAreAlignedAndHaveVisibleTouchTargets() {
        val record = CareReminder(
            "example-record", "example-person", "Preparar documentos ficticios", null,
            now.plusHours(2), false, CareReminderStatus.PENDING, now, now, null, 1,
        )
        compose.setContent {
            BitavidaTheme(darkTheme = true) {
                BitavidaDateTimePickerHost {
                    Surface {
                        Column(Modifier.width(390.dp).verticalScroll(rememberScrollState())) {
                            ReminderPostponeContent(form.copy(id = record.id),
                                RemindersUiState(loading = false, records = listOf(record),
                                    selectedId = record.id, now = now), {}, false)
                        }
                    }
                }
            }
        }
        val first = compose.onNodeWithTag("postpone_FIFTEEN_MINUTES").assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("postpone_ONE_HOUR").assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val tomorrow = compose.onNodeWithTag("postpone_TOMORROW").assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val custom = compose.onNodeWithTag("postpone_custom").assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        listOf(second, tomorrow, custom).forEach { bounds ->
            assertEquals(first.width, bounds.width, 1f)
            assertEquals(first.height, bounds.height, 1f)
        }
        assertEquals(first.top, second.top, 1f)
        assertEquals(tomorrow.top, custom.top, 1f)
        assertEquals(first.left, tomorrow.left, 1f)
        assertEquals(second.left, custom.left, 1f)
        assertTrue(first.right < second.left)
        assertTrue(first.bottom < tomorrow.top)
    }

    @Test fun compactDoubleTextKeepsTheSaveActionReachableWithoutUnknownContextMessages() {
        compose.setContent {
            BitavidaTheme(darkTheme = true) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    BitavidaDateTimePickerHost {
                        Surface {
                            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                                ReminderFormContent(form.copy(title = "Test"), RemindersUiState(loading = false), {}, false)
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("No encontré suficiente contexto", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Guardar recordatorio").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Preguntas relacionadas").assertDoesNotExist()
    }
}
