package com.bitavida.app.ui.reminders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.education.ReminderEducationCategory
import com.bitavida.app.domain.usecase.ReminderIdeaGroup
import com.bitavida.app.domain.education.ReminderSuggestion
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.usecase.ReminderPostponePreset
import com.bitavida.app.ui.components.BitavidaDateTimePickerHost
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prepared interaction tests; no real patient, alarm, image or database is accessed. */
class RemindersPremiumInteractionsTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)

    @Test fun groupsAndQuickCompletionReflectTheUpdatedProjectionInBothThemes() {
        val record = reminder("today", "Preparar documentos ficticios", now.plusHours(1))
        val state = mutableStateOf(RemindersUiState(records = listOf(record),
            personUnderCareId = "person-example", loading = false, now = now))
        val dark = mutableStateOf(false)
        var dispatched: RemindersAction? = null
        rule.setContent {
            BitavidaTheme(darkTheme = dark.value) {
                RemindersScreen(state.value, {
                    dispatched = it
                    if (it is RemindersAction.QuickComplete) state.value = state.value.copy(records =
                        state.value.records.map { item -> item.copy(status = CareReminderStatus.DONE, closedAt = now) })
                }, {}, "Persona ficticia")
            }
        }
        rule.onNodeWithText("Hoy").assertIsDisplayed()
        rule.onNodeWithText("Próximos").assertDoesNotExist()
        rule.onNodeWithText("Pendientes anteriores").assertDoesNotExist()
        rule.runOnIdle {
            dark.value = true
            state.value = state.value.copy(now = now.plusHours(2))
        }
        rule.onNodeWithText("Pendientes anteriores").assertIsDisplayed()
        rule.onNodeWithText("Hoy").assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(now = now) }
        rule.onNodeWithTag("reminder_complete_today").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(RemindersAction.QuickComplete("today"), dispatched) }
        rule.onNodeWithText("0 de 10 activos").assertDoesNotExist()
        rule.onNodeWithText("Hoy").assertDoesNotExist()
        rule.onNodeWithTag("reminder_today").assertDoesNotExist()
    }

    @Test fun compactLargeTextKeepsVisibleAlternativesForQuickActions() {
        val record = reminder("compact", "Reunir artículos para una salida familiar ficticia", now.plusHours(1))
        rule.setContent {
            BitavidaTheme(darkTheme = true) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    Box(Modifier.width(320.dp)) {
                        RemindersScreen(RemindersUiState(records = listOf(record),
                            personUnderCareId = "person-example", loading = false, now = now),
                            {}, {}, "Persona ficticia")
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Marcar como hecho: " + record.title)
            .performScrollTo().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Posponer: " + record.title)
            .performScrollTo().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Más opciones: " + record.title).assertDoesNotExist()
        rule.onNodeWithContentDescription("Aviso del celular desactivado", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("reminder_compact").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test fun postponementPresetIsAnExplicitAction() {
        val record = reminder("postpone", "Coordinar apoyo ficticio", now.plusHours(1))
        var dispatched: RemindersAction? = null
        rule.setContent {
            BitavidaTheme {
                ReminderSheets(RemindersUiState(records = listOf(record),
                    personUnderCareId = "person-example", loading = false, now = now,
                    selectedId = record.id, sheet = ReminderSheet.POSTPONE,
                    form = ReminderForm(record.id, "op-example", record.title, "", now.plusMinutes(1), false, 1)),
                    { dispatched = it })
            }
        }
        rule.onNodeWithTag("postpone_FIFTEEN_MINUTES").performClick()
        rule.runOnIdle {
            assertEquals(RemindersAction.ApplyPostponePreset(ReminderPostponePreset.FIFTEEN_MINUTES), dispatched)
        }
    }

    @Test fun compactFormUsesOneCtaAndRequiresExplicitDuplicateOverride() {
        val similar = reminder("similar", "Comprar pilas para el dispositivo ficticio", now.plusHours(1))
        var dispatched: RemindersAction? = null
        rule.setContent {
            BitavidaTheme {
                BitavidaDateTimePickerHost {
                    ReminderSheets(RemindersUiState(records = listOf(similar),
                        personUnderCareId = "person-example", loading = false, now = now,
                        sheet = ReminderSheet.FORM,
                        form = ReminderForm("new", "operation-example", "Comprar baterías para el dispositivo ficticio",
                            "", now.plusHours(2), false),
                        similarReminders = listOf(similar)), { dispatched = it })
                }
            }
        }
        rule.onNodeWithText("Aviso del celular").assertDoesNotExist()
        rule.onNodeWithText("Obligatorio").assertDoesNotExist()
        rule.onNodeWithText("Cancelar").assertDoesNotExist()
        rule.onNodeWithText("Detalles (opcional)").assertDoesNotExist()
        rule.onNodeWithText("Ya tienes un recordatorio parecido.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Consultarlo").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(RemindersAction.ViewSimilar("similar"), dispatched) }
        rule.onNodeWithText("Crear otro de todas maneras").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(RemindersAction.SaveAnyway, dispatched) }
    }

    @Test fun choosingALocalIdeaOnlyRequestsAnUpdateToTheExistingDraft() {
        var dispatched: RemindersAction? = null
        val title = "Preparar documentos ficticios"
        rule.setContent {
            BitavidaTheme {
                ReminderSheets(RemindersUiState(personUnderCareId = "person-example",
                    loading = false, now = now, sheet = ReminderSheet.SUGGESTIONS,
                    form = ReminderForm("draft-example", "operation-example", "", "Detalle ficticio", now.plusHours(2), true),
                    ideaCategory = ReminderEducationCategory.DOCUMENTS,
                    ideaCatalog = listOf(ReminderIdeaGroup(ReminderEducationCategory.DOCUMENTS, listOf(
                        ReminderSuggestion("suggestion-example", title, "Reúne los documentos que tú decidiste llevar."),
                    )))), { dispatched = it })
            }
        }
        rule.onNodeWithText(title).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(RemindersAction.UseIdea(title), dispatched) }
    }

    private fun reminder(id: String, title: String, at: LocalDateTime) =
        CareReminder(id, "person-example", title, null, at, false, CareReminderStatus.PENDING,
            now.minusDays(1), now, null, 1)
}
