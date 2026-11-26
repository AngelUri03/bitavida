package com.bitavida.app.ui.reminders

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.repository.CareReminderActor
import com.bitavida.app.domain.repository.CareReminderRepository
import com.bitavida.app.domain.usecase.CareReminderCommand
import com.bitavida.app.ui.medical.MedicalPlanScreen
import com.bitavida.app.ui.medical.MedicalPlanUiState
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prepared device tests. Execution requires the user's explicit instrumented-test request. */
class RemindersNavigationTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val actor = CareReminderActor("fictional-profile", "fictional-caregiver", "fictional-person", true)

    @Test fun previousRemindersAndBackUseTheExistingRoute() {
        val previous = reminder("closed", CareReminderStatus.DONE)
        val viewModel = RemindersViewModel(
            repository = ReadOnlyReminderRepository(listOf(previous)),
            actor = actor,
            nowProvider = { now },
        )
        var exits = 0
        rule.setContent {
            BitavidaTheme {
                RemindersRoute(viewModel, "Persona ficticia", onBack = { exits++ })
            }
        }
        rule.waitUntil { !viewModel.state.value.loading }
        rule.onNodeWithTag("reminders_MAIN").assertExists()
        rule.onNodeWithText("Recordatorios anteriores").performScrollTo().performClick()
        rule.onNodeWithTag("reminders_PREVIOUS").assertExists()
        rule.onNodeWithText("Hecho", substring = true).assertIsDisplayed()

        Espresso.pressBack()
        rule.onNodeWithTag("reminders_MAIN").assertExists()
        rule.runOnIdle { assertEquals(RemindersPage.MAIN, viewModel.state.value.page) }
        Espresso.pressBack()
        rule.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun lightAndDarkThemesKeepPendingActionsWithoutAnActiveCounter() {
        val dark = mutableStateOf(false)
        val record = reminder("active", CareReminderStatus.PENDING)
        rule.setContent {
            BitavidaTheme(darkTheme = dark.value) {
                RemindersScreen(
                    state = RemindersUiState(records = listOf(record),
                        personUnderCareId = actor.personUnderCareId, loading = false, now = now),
                    onAction = {}, onBack = {}, patientName = "Persona ficticia",
                )
            }
        }
        rule.onNodeWithText("1 de 10 activos").assertDoesNotExist()
        rule.onNodeWithTag("reminder_active").assertIsDisplayed()
        rule.onNodeWithTag("reminder_complete_active").assertIsDisplayed()
        rule.runOnIdle { dark.value = true }
        rule.onNodeWithTag("reminder_active").assertIsDisplayed()
        rule.onNodeWithTag("reminder_complete_active").assertIsDisplayed()
        rule.onNodeWithText("Pendientes del cuidado cotidiano").assertIsDisplayed()
    }

    @Test fun planOverviewCounterIncludesPastPendingAndUpdatesWithTheSharedState() {
        val dark = mutableStateOf(false)
        val reminders = mutableStateOf(RemindersUiState(
            records = listOf(
                reminder("past-active", CareReminderStatus.PENDING).copy(scheduledAt = now.minusHours(1)),
                reminder("done", CareReminderStatus.DONE),
                reminder("discarded", CareReminderStatus.DISCARDED),
                reminder("other-person", CareReminderStatus.PENDING).copy(personUnderCareId = "other-fictional-person"),
            ),
            personUnderCareId = actor.personUnderCareId, loading = false, now = now,
        ))
        rule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                BitavidaTheme(darkTheme = dark.value) {
                    MedicalPlanScreen(
                        state = MedicalPlanUiState(isLoading = false),
                        caregiverDisplayName = "Cuidadora ficticia",
                        personUnderCareDisplayName = "Persona ficticia",
                        onAction = {}, onTodaySelected = {},
                        activeReminderCount = reminders.value.active.size,
                    )
                }
            }
        }
        rule.onNodeWithTag("plan_reminder_active_count", useUnmergedTree = true)
            .performScrollTo().assertTextEquals("1 activo")
        rule.runOnIdle {
            dark.value = true
            reminders.value = reminders.value.copy(
                records = reminders.value.records + reminder("upcoming-active", CareReminderStatus.PENDING),
            )
        }
        rule.onNodeWithTag("plan_reminder_active_count", useUnmergedTree = true)
            .performScrollTo().assertTextEquals("2 activos")
        rule.runOnIdle {
            reminders.value = reminders.value.copy(records = reminders.value.records.map {
                if (it.id == "past-active") it.copy(status = CareReminderStatus.DONE, closedAt = now) else it
            })
        }
        rule.onNodeWithTag("plan_reminder_active_count", useUnmergedTree = true)
            .performScrollTo().assertTextEquals("1 activo")
    }

    private fun reminder(id: String, status: CareReminderStatus) = CareReminder(
        id, actor.personUnderCareId, "Preparar documentos ficticios", null,
        now.plusHours(1), false, status, now.minusDays(1), now,
        if (status == CareReminderStatus.PENDING) null else now, 1,
    )

    private class ReadOnlyReminderRepository(private val records: List<CareReminder>) : CareReminderRepository {
        override suspend fun load(actor: CareReminderActor) = records
        override suspend fun apply(actor: CareReminderActor, command: CareReminderCommand, now: LocalDateTime): CareReminder =
            error("This navigation fixture never writes records.")
    }
}
