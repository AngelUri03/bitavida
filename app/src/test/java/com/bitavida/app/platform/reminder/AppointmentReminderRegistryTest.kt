package com.bitavida.app.platform.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class AppointmentReminderRegistryTest {
    @Test fun reschedulingALegacyTokenDoesNotPreserveItsPreviousElapsedAlarm() {
        val registered = setOf("legacy-id|100", "other-id|90")
        val planned = listOf("legacy-id" to 200L, "other-id" to 90L)
        val preserved = preserveScheduledReminderEntries(
            registered, planned, setOf("legacy-id", "other-id"), restoreElapsed = false, nowMillis = 150L,
        )
        assertEquals(setOf("other-id|90"), preserved)
    }

    @Test fun restoredLegacyAlarmMustStillMatchItsPersistedTime() {
        val previous = setOf("legacy-id|100")
        val changed = listOf("legacy-id" to 200L)
        assertEquals(emptySet<String>(), preserveScheduledReminderEntries(
            previous, changed, setOf("legacy-id"), restoreElapsed = true, nowMillis = 150L,
        ))
        assertEquals(previous, preserveScheduledReminderEntries(
            previous, listOf("legacy-id" to 100L), setOf("legacy-id"), restoreElapsed = true, nowMillis = 150L,
        ))
    }

    @Test fun switchingToMultipleTokensAndCancellationNeverPreservesLegacyAlarm() {
        val previous = setOf("legacy-id|100")
        assertEquals(emptySet<String>(), preserveScheduledReminderEntries(
            previous, listOf("opaque-30" to 100L, "opaque-120" to 50L),
            setOf("opaque-30", "opaque-120"), restoreElapsed = true, nowMillis = 150L,
        ))
        assertEquals(emptySet<String>(), preserveScheduledReminderEntries(
            previous, emptyList(), emptySet(), restoreElapsed = true, nowMillis = 150L,
        ))
    }

    @Test fun pendingInexactAlarmIsPreservedOnlyForTheSameSchedule() {
        val entries = setOf("current|100", "future|300", "broken")
        val planned = listOf("current" to 100L, "future" to 300L)
        assertEquals(setOf("current|100"), preserveScheduledReminderEntries(
            entries, planned, setOf("current", "future"), restoreElapsed = false, nowMillis = 150L,
        ))
        assertEquals(setOf("current|100", "future|300"), preserveScheduledReminderEntries(
            entries, planned, setOf("current", "future"), restoreElapsed = true, nowMillis = 150L,
        ))
    }
}
