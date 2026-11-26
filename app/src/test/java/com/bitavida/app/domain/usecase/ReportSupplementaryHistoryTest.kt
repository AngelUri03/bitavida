package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.Note
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ReportSupplementaryHistoryTest {
    private val day = LocalDate.of(2026, 10, 1)
    private val first = day.atTime(8, 0)
    private val changed = day.plusDays(2).atTime(10, 0)

    @Test fun notesAndRemindersUseVersionsKnownAtCutoffAndNeverExposePhotoIds() {
        val initialNote = Note("note-id", "person", "Nota ficticia", "Contenido inicial",
            "private-photo-id", first, first, 1)
        val nextNote = initialNote.copy(content = "Contenido posterior", modifiedAt = changed, revision = 2)
        val initialReminder = CareReminder("reminder-id", "person", "Pendiente ficticio", null,
            day.plusDays(1).atTime(9, 0), false, CareReminderStatus.PENDING, first, first, null, 1)
        val nextReminder = initialReminder.copy(status = CareReminderStatus.DONE,
            modifiedAt = changed, closedAt = changed, revision = 2)
        val history = ReportSupplementaryHistory(
            listOf(ReportNoteHistory(nextNote, listOf(initialNote, nextNote))),
            listOf(ReportReminderHistory(nextReminder, listOf(initialReminder, nextReminder))),
        )
        fun report(cutoff: LocalDateTime) = buildFollowUpReport(day, day.plusDays(5), "person",
            MedicalCareOverview(emptyList(), emptyList()), emptyList(), emptyList(),
            zoneId = ZoneOffset.UTC, now = cutoff, supplementaryHistory = history)

        val early = report(day.plusDays(1).atTime(23, 0))
        assertEquals("Contenido inicial · Fotografía adjunta; no se incluye en el informe",
            early.rows.single { it.sourceId == "note:note-id" }.detail)
        assertTrue(early.rows.single { it.sourceId == "reminder:reminder-id" }.detail.startsWith("Pendiente"))
        assertFalse(early.rows.any { it.section == "Auditoría y correcciones" })
        assertFalse(early.rows.any { it.detail.contains("private-photo-id") || it.detail.contains("Contenido posterior") })

        val later = report(day.plusDays(5).atTime(23, 0))
        assertTrue(later.rows.single { it.sourceId == "note:note-id" }.detail.contains("Contenido posterior"))
        assertTrue(later.rows.single { it.sourceId == "reminder:reminder-id" }.detail.startsWith("Realizado"))
        assertEquals(2, later.rows.count { it.section == "Auditoría y correcciones" })
        assertTrue(later.rows.any { it.section == "Auditoría y correcciones" && it.detail.contains("Contenido inicial") })
        assertFalse(later.rows.any { it.detail.contains("private-photo-id") })
    }

    @Test fun deletionAndMissingHistoryDoNotInventAnEarlierValue() {
        val original = Note("deleted", "person", "Nota ficticia", "Texto", null, first, first, 1)
        val deleted = original.copy(modifiedAt = changed, deletedAt = changed, revision = 2)
        val noHistory = Note("unknown", "person", "Texto posterior", "No reconstruible", null,
            first, changed, 2)
        val other = original.copy(id = "other", personUnderCareId = "other")
        val history = ReportSupplementaryHistory(notes = listOf(
            ReportNoteHistory(deleted, listOf(original, deleted)),
            ReportNoteHistory(noHistory, emptyList()),
            ReportNoteHistory(other, listOf(other)),
        ))
        val early = supplementaryReportRows(day, day.plusDays(5), day.plusDays(1).atTime(23, 0), "person", history)
        assertTrue(early.any { it.sourceId == "note:deleted" })
        assertTrue(early.any { it.sourceId == "note-unknown:unknown" && it.detail.contains("no está disponible") })
        assertFalse(early.any { it.title.contains("Texto posterior") || it.sourceId?.contains("other") == true })
        val later = supplementaryReportRows(day, day.plusDays(5), day.plusDays(5).atTime(23, 0), "person", history)
        assertFalse(later.any { it.sourceId == "note:deleted" })
        assertTrue(later.any { it.section == "Auditoría y correcciones" && it.detail.contains("Nota eliminada") })
    }
}
