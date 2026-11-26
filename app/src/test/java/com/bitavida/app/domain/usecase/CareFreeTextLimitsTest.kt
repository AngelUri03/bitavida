package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.CareReminderDraft
import com.bitavida.app.domain.model.CareReminderStatus
import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.model.NoteDraft
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CareFreeTextLimitsTest {
    private val now = LocalDateTime.of(2026, 11, 20, 10, 0)
    private val person = "fictional-person"
    private val family = "👨‍👩‍👧‍👦"
    private val accented = "a\u0301"

    @Test fun reminderCreationAcceptsExactGraphemeLimitsAndRejectsOneExtraCharacter() {
        val draft = CareReminderDraft(family.repeat(100), accented.repeat(500), now.plusHours(1))
        val saved = reminderCreate(draft)
        assertEquals(draft.title, saved.title)
        assertEquals(draft.details, saved.details)
        reminderProblem(CareReminderProblem.TITLE_TOO_LONG) { reminderCreate(draft.copy(title = draft.title + "x")) }
        reminderProblem(CareReminderProblem.DETAILS_TOO_LONG) { reminderCreate(draft.copy(details = draft.details + "x")) }
    }

    @Test fun reminderEditingRechecksBothTextFieldsInsideTheSharedTransactionTransition() {
        val original = reminderCreate()
        reminderProblem(CareReminderProblem.TITLE_TOO_LONG) {
            reminderEdit(original, "x".repeat(101), original.details)
        }
        reminderProblem(CareReminderProblem.DETAILS_TOO_LONG) {
            reminderEdit(original, original.title, "x".repeat(501))
        }
        val edited = reminderEdit(original, accented.repeat(100), family.repeat(500))
        assertEquals(original.id, edited.id)
        assertEquals(2, edited.revision)
        assertEquals(family.repeat(500), edited.details)
    }

    @Test fun historicalReminderTextIsNotTruncatedWhenChangingOnlyDateAndCanBeCorrected() {
        val original = reminderCreate().copy(title = " ".repeat(2) + "x".repeat(180) + " ",
            details = " ".repeat(2) + "y".repeat(900) + " ")
        val retained = reminderEdit(original, original.title, original.details)
        assertEquals(original.title, retained.title)
        assertEquals(original.details, retained.details)
        reminderProblem(CareReminderProblem.TITLE_TOO_LONG) {
            reminderEdit(original, original.title + "z", original.details)
        }
        val corrected = reminderEdit(original, "Título corregido", "Texto conservado y corregido explícitamente")
        assertEquals("Título corregido", corrected.title)
        assertEquals(original.id, corrected.id)
        assertEquals(" ".repeat(2) + "x".repeat(180) + " ", original.title)
    }

    @Test fun closingAndPostponingHistoricalRemindersDoNotRewriteTheirContent() {
        val original = reminderCreate().copy(title = "x".repeat(150), details = "y".repeat(800))
        val postponed = applyCareReminderCommand(listOf(original), person,
            CareReminderCommand.Postpone("later", original.id, original.revision, now.plusDays(1)), now)
        val closed = applyCareReminderCommand(listOf(postponed), person,
            CareReminderCommand.Close("done", postponed.id, postponed.revision, CareReminderStatus.DONE), now)
        assertEquals(original.title, closed.title)
        assertEquals(original.details, closed.details)
        assertEquals(now, closed.closedAt)
    }

    @Test fun duplicatingHistoricalReminderContentUsesNewRecordLimits() {
        val historical = reminderCreate().copy(title = "x".repeat(120), details = "y".repeat(700))
        reminderProblem(CareReminderProblem.TITLE_TOO_LONG) {
            reminderCreate(CareReminderDraft(historical.title, historical.details, now.plusHours(1)))
        }
    }

    @Test fun noteCreateAndEditUseOneHundredAndTwoThousandGraphemes() {
        val draft = NoteDraft(family.repeat(100), accented.repeat(2_000))
        val created = noteCreate(draft)
        assertEquals(draft.title, created.title)
        assertEquals(draft.content, created.content)
        noteProblem(NoteProblem.TITLE_TOO_LONG) { noteCreate(draft.copy(title = draft.title + "x")) }
        noteProblem(NoteProblem.CONTENT_TOO_LONG) { noteCreate(draft.copy(content = draft.content + "x")) }
        noteProblem(NoteProblem.TITLE_TOO_LONG) { noteEdit(created, draft.copy(title = draft.title + "x")) }
        noteProblem(NoteProblem.CONTENT_TOO_LONG) { noteEdit(created, draft.copy(content = draft.content + "x")) }
    }

    @Test fun editingImageOrOtherFieldKeepsHistoricalNoteTextByteForByte() {
        val original = noteCreate().copy(title = "  " + "x".repeat(160) + "  ",
            content = "  " + "y".repeat(4_000) + "  ")
        val edited = noteEdit(original, NoteDraft(original.title, original.content, "new-private-image"))
        assertEquals(original.title, edited.title)
        assertEquals(original.content, edited.content)
        assertEquals("new-private-image", edited.imageId)
        noteProblem(NoteProblem.CONTENT_TOO_LONG) {
            noteEdit(original, NoteDraft(original.title, original.content + "x"))
        }
        val corrected = noteEdit(original, NoteDraft("Título corregido", "Contenido corregido"))
        assertEquals("Contenido corregido", corrected.content)
        assertEquals(4_004, original.content!!.length)
    }

    @Test fun historicalNoteDeletionStillPreservesTheFullStoredSnapshot() {
        val original = noteCreate().copy(title = "x".repeat(200), content = "y".repeat(8_000))
        val deleted = applyNoteCommand(listOf(original), person,
            NoteCommand.Delete("delete", original.id, original.revision), now)
        assertEquals(original.title, deleted.title)
        assertEquals(original.content, deleted.content)
        assertTrue(visibleNotes(listOf(deleted), person).isEmpty())
    }

    private fun reminderCreate(draft: CareReminderDraft =
        CareReminderDraft("Pendiente ficticio", "Detalle ficticio", now.plusHours(1))) =
        applyCareReminderCommand(emptyList(), person, CareReminderCommand.Create("create-reminder", "reminder", draft), now)

    private fun reminderEdit(original: CareReminder, title: String, details: String?) =
        applyCareReminderCommand(listOf(original), person,
            CareReminderCommand.Edit("edit-reminder", original.id, original.revision,
                CareReminderDraft(title, details, now.plusHours(2))), now)

    private fun noteCreate(draft: NoteDraft = NoteDraft("Nota ficticia", "Contenido ficticio")) =
        applyNoteCommand(emptyList(), person, NoteCommand.Create("create-note", "note", draft), now)

    private fun noteEdit(original: Note, draft: NoteDraft) =
        applyNoteCommand(listOf(original), person, NoteCommand.Edit("edit-note", original.id, original.revision, draft), now)

    private fun reminderProblem(expected: CareReminderProblem, operation: () -> Unit) {
        try { operation(); fail("Expected $expected") }
        catch (error: CareReminderValidationException) { assertEquals(expected, error.problem) }
    }

    private fun noteProblem(expected: NoteProblem, operation: () -> Unit) {
        try { operation(); fail("Expected $expected") }
        catch (error: NoteValidationException) { assertEquals(expected, error.problem) }
    }
}
