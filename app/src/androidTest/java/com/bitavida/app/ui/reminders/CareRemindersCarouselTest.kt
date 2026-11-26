package com.bitavida.app.ui.reminders

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.ui.demo.TodayDemoScreen
import com.bitavida.app.ui.demo.TodayDemoState
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Prepared integration tests; they do not access a real profile or its encrypted database. */
class CareRemindersCarouselTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)

    @Test fun todayCarouselUpdatesImmediatelyAndRetainsItsOwnNavigation() {
        val first = reminder("first", "Comprar insumos ficticios", now.plusHours(1))
        val later = reminder("later", "Coordinar ayuda ficticia", now.plusDays(1))
        val records = mutableStateOf(listOf(later, first,
            reminder("done", "Pendiente ficticio cerrado", now, CareReminderStatus.DONE)))
        val dark = mutableStateOf(false)
        var opened: String? = null
        var allOpened = false
        rule.setContent {
            BitavidaTheme(darkTheme = dark.value) {
                TodayDemoScreen(
                    state = TodayDemoState.Empty,
                    caregiverDisplayName = "Cuidadora ficticia",
                    personUnderCareDisplayName = "Persona ficticia",
                    currentDateTime = now,
                    motionEnabledOverride = false,
                    careReminders = records.value,
                    onReminderSelected = { opened = it },
                    onAllReminders = { allOpened = true },
                )
            }
        }
        rule.onNodeWithText("Recordatorios del cuidado").performScrollTo()
        // Input order is deliberately reversed: the earliest card must be visible first.
        rule.onNodeWithTag("care_carousel_first").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("first", opened) }
        rule.onNodeWithText("Pendiente ficticio cerrado").assertDoesNotExist()
        rule.onNodeWithText("Ver todos").performClick()
        rule.runOnIdle { assertTrue(allOpened) }

        rule.runOnIdle {
            records.value = listOf(first.copy(status = CareReminderStatus.DONE, closedAt = now), later)
            dark.value = true
        }
        rule.onNodeWithTag("care_carousel_first").assertDoesNotExist()
        rule.onNodeWithTag("care_carousel_later").assertIsDisplayed()
        rule.onNodeWithTag("care_carousel_later").performClick()
        rule.runOnIdle { assertEquals("later", opened) }

        rule.runOnIdle { records.value = records.value.map { it.copy(status = CareReminderStatus.DISCARDED, closedAt = now) } }
        rule.onNodeWithTag("care_reminders_carousel").assertDoesNotExist()
    }

    private fun reminder(
        id: String,
        title: String,
        scheduledAt: LocalDateTime,
        status: CareReminderStatus = CareReminderStatus.PENDING,
    ) = CareReminder(id, "fictional-person", title, null, scheduledAt, false, status,
        now.minusDays(1), now, if (status == CareReminderStatus.PENDING) null else now, 1)
}
