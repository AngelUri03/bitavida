package com.bitavida.app.ui.reminders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prepared visual-contract/interaction tests. They do not write records or contact Android alarms. */
class ReminderDetailHierarchyTest {
    @get:Rule val compose = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val example = CareReminder(
        "detail-example", "person-example", "Preparar documentos ficticios", "Una nota ficticia.",
        now.plusHours(2), true, CareReminderStatus.PENDING, now, now, null, 1,
    )

    @Test fun pendingDetailKeepsAllActionsAsExplicitAccessibleRequests() {
        var dispatched: RemindersAction? = null
        compose.setContent {
            BitavidaTheme(darkTheme = true) {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ReminderDetailContent(example, state(example), { dispatched = it }, false)
                    }
                }
            }
        }
        compose.onNodeWithText("Marcar como hecho").assertDoesNotExist()
        compose.onNodeWithText("Posponer").assertDoesNotExist()
        val actions = listOf(
            "Editar" to RemindersAction.Edit,
            "Crear otro igual" to RemindersAction.CreateAnother,
            "Descartar recordatorio" to RemindersAction.RequestDiscard,
        )
        actions.forEach { (label, action) ->
            compose.onNodeWithText(label).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(action, dispatched) }
        }
    }

    @Test fun closedDetailOnlyAllowsDuplicationInLightAndDarkWithoutReopeningTheRecord() {
        val record = mutableStateOf(example.copy(status = CareReminderStatus.DONE, closedAt = now.plusDays(1)))
        val dark = mutableStateOf(false)
        var dispatched: RemindersAction? = null
        compose.setContent {
            BitavidaTheme(darkTheme = dark.value) {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ReminderDetailContent(record.value, state(record.value), { dispatched = it }, false)
                    }
                }
            }
        }
        compose.onNodeWithText("Hecho").assertIsDisplayed()
        assertClosedActions()
        compose.onNodeWithText("Crear otro igual").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RemindersAction.CreateAnother, dispatched) }
        compose.runOnIdle {
            dark.value = true
            record.value = record.value.copy(status = CareReminderStatus.DISCARDED)
        }
        compose.onNodeWithText("Descartado").performScrollTo().assertIsDisplayed()
        assertClosedActions()
        compose.onNodeWithText("Crear otro igual").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    private fun assertClosedActions() {
        compose.onNodeWithText("Marcar como hecho").assertDoesNotExist()
        compose.onNodeWithText("Posponer").assertDoesNotExist()
        compose.onNodeWithText("Editar").assertDoesNotExist()
        compose.onNodeWithText("Descartar recordatorio").assertDoesNotExist()
        compose.onNodeWithText("Revisar título").assertDoesNotExist()
    }

    private fun state(record: CareReminder) = RemindersUiState(
        records = listOf(record), personUnderCareId = record.personUnderCareId,
        selectedId = record.id, sheet = ReminderSheet.DETAIL, now = now, loading = false,
    )
}
