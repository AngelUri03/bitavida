package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CareReminderExperienceTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)

    @Test fun groupsAreExclusiveChronologicalAndExcludeClosedAndOtherPatients() {
        val records = listOf(
            reminder("future-late", now.plusDays(3)), reminder("today-late", now.plusHours(2)),
            reminder("past-today", now.minusMinutes(1)), reminder("past-old", now.minusDays(1)),
            reminder("today-now", now), reminder("future-first", now.plusDays(1)),
            reminder("closed", now).copy(status = CareReminderStatus.DONE),
            reminder("other-patient", now).copy(personUnderCareId = "other"),
        )
        val groups = groupedCareReminders(records, "person-example", now)
        assertEquals(CareReminderTemporalGroup.entries, groups.map { it.kind })
        assertEquals(listOf("today-now", "today-late"), groups[0].records.map { it.id })
        assertEquals(listOf("future-first", "future-late"), groups[1].records.map { it.id })
        assertEquals(listOf("past-old", "past-today"), groups[2].records.map { it.id })
        assertEquals(6, groups.sumOf { it.records.size })
        assertTrue(records.all { it.closedAt == null })
    }

    @Test fun passageOfTimeMovesPresentationWithoutChangingPersistedStatus() {
        val record = reminder("one", now.plusMinutes(1))
        assertEquals(CareReminderTemporalGroup.TODAY, groupedCareReminders(listOf(record), "person-example", now).single().kind)
        assertEquals(CareReminderTemporalGroup.PAST, groupedCareReminders(listOf(record), "person-example", now.plusMinutes(2)).single().kind)
        assertEquals(CareReminderStatus.PENDING, record.status)
        assertTrue(groupedCareReminders(emptyList(), "person-example", now).isEmpty())
    }

    @Test fun carouselPrioritizesTodayThenFutureThenEarlierAndNeverShowsClosed() {
        val result = careRemindersForTodayCarousel(listOf(
            reminder("old", now.minusDays(2)), reminder("tomorrow", now.plusDays(1)),
            reminder("today", now.plusHours(1)), reminder("done", now).copy(status = CareReminderStatus.DONE),
        ), now)
        assertEquals(listOf("today", "tomorrow", "old"), result.map { it.id })
    }

    @Test fun allPostponementShortcutsUseActualRequestTimeAcrossMidnightAndYear() {
        val tap = LocalDateTime.of(2026, 12, 31, 23, 55, 37)
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 10, 37),
            reminderPostponementTime(ReminderPostponePreset.FIFTEEN_MINUTES, tap))
        assertEquals(tap.plusHours(1), reminderPostponementTime(ReminderPostponePreset.ONE_HOUR, tap))
        assertEquals(tap.plusDays(1), reminderPostponementTime(ReminderPostponePreset.TOMORROW, tap))
        ReminderPostponePreset.entries.forEach { assertTrue(reminderPostponementTime(it, tap).isAfter(tap)) }
    }

    @Test fun similarityUnderstandsAccentsSynonymsAndOrdinaryTypingErrorsWithoutExactMatching() {
        val record = reminder("battery", now).copy(title = "Comprar pilas para el glucómetro")
        assertEquals(listOf("battery"), similarActiveCareReminders("comprar baterias del glucometro", listOf(record), "person-example").map { it.id })
        assertEquals(listOf("battery"), similarActiveCareReminders("comprar pilas para el glucomtero", listOf(record), "person-example").map { it.id })
        val document = reminder("docs", now).copy(title = "Preparar documentos")
        assertEquals(listOf(document), similarActiveCareReminders("preparar papeles", listOf(document), "person-example"))
    }

    @Test fun similarityNeverIncludesSelfHistoryOtherPatientsOrOnlyGenericVerbMatches() {
        val pending = reminder("pending", now).copy(title = "Comprar pilas para el glucómetro")
        val records = listOf(pending,
            pending.copy(id = "done", status = CareReminderStatus.DONE),
            pending.copy(id = "discarded", status = CareReminderStatus.DISCARDED),
            pending.copy(id = "other", personUnderCareId = "other"))
        assertTrue(similarActiveCareReminders(pending.title, records, "person-example", excludingId = "pending").isEmpty())
        assertTrue(similarActiveCareReminders("Comprar documentos para el traslado", records, "person-example").isEmpty())
        assertTrue(similarActiveCareReminders("", records, "person-example").isEmpty())
    }

    private fun reminder(id: String, scheduled: LocalDateTime) =
        CareReminder(id, "person-example", "Preparar objetos " + id, null, scheduled, false,
            CareReminderStatus.PENDING, now, now, null, 1)
}
