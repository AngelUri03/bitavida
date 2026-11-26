package com.bitavida.app.ui.reminders

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Prepared interaction and density regression checks; no device execution is part of the delivery. */
class ReminderCardDensityTest {
    @get:Rule val compose = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val reminder = CareReminder("example", "person-example", "Preparar documentos", null,
        now.plusHours(1), true, CareReminderStatus.PENDING, now, now, null, 1)

    @Test fun compactCardHasRealControlsWithoutRedundantRowsOrStandaloneAiEntry() {
        val events = mutableListOf<RemindersAction>()
        compose.setContent {
            BitavidaTheme {
                Surface {
                    RemindersScreen(
                        RemindersUiState(records = listOf(reminder), personUnderCareId = "person-example",
                            loading = false, now = now),
                        { events += it }, {}, "Elena (ejemplo ficticio)",
                    )
                }
            }
        }
        assertTrue(compose.onNodeWithTag("reminder_example").getUnclippedBoundsInRoot().let { it.bottom - it.top } <= 168.dp)
        compose.onNodeWithText("Pendiente").assertDoesNotExist()
        compose.onNodeWithText("Para hoy").assertDoesNotExist()
        compose.onNodeWithTag("reminders_suggest").assertDoesNotExist()
        compose.onNodeWithTag("reminder_complete_example").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(listOf(RemindersAction.QuickComplete("example")), events) }
        compose.onNodeWithTag("reminder_options_example").assertDoesNotExist()
        compose.onNodeWithContentDescription("Aviso del celular activado", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("reminder_example").performClick()
        compose.runOnIdle { assertEquals(RemindersAction.Open("example"), events.last()) }
    }

    @Test fun historyShowsOriginalScheduleAndClosureWithoutActiveActions() {
        compose.setContent {
            BitavidaTheme(darkTheme = true) {
                Surface {
                    ReminderCard(reminder.copy(status = CareReminderStatus.DONE, closedAt = now.plusDays(1)),
                        now, {}, Modifier.fillMaxWidth())
                }
            }
        }
        assertTrue(compose.onNodeWithTag("reminder_example").getUnclippedBoundsInRoot().let { it.bottom - it.top } <= 132.dp)
        compose.onNodeWithTag("reminder_complete_example").assertDoesNotExist()
        compose.onNodeWithTag("reminder_options_example").assertDoesNotExist()
        compose.onNodeWithText("Hecho", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cierre:", substring = true).assertIsDisplayed()
    }
}
