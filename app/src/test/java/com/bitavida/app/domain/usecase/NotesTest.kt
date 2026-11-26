package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MAX_NOTE_CONTENT_LENGTH
import com.bitavida.app.domain.model.MAX_NOTE_TITLE_LENGTH
import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.model.NoteDraft
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteAuthorizationException
import com.bitavida.app.domain.repository.NoteRepository
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotesTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val person = "fictional-person"
    private val actor = NoteActor("fictional-profile", "fictional-caregiver", person, true)
    private fun create(draft: NoteDraft = NoteDraft("Lista ficticia", "Objetos de ejemplo")) =
        NoteCommand.Create("create", "note", draft)
    private fun saved() = applyNoteCommand(emptyList(), person, create(), now)

    @Test fun createsFreeTextNoteWithoutTaskFieldsOrRequiredBodyAndImage() {
        val note = applyNoteCommand(emptyList(), person, create(NoteDraft("  Lista ficticia  ", "  ")), now)
        assertEquals("Lista ficticia", note.title)
        assertNull(note.content)
        assertNull(note.imageId)
        assertNull(note.deletedAt)
        assertEquals(now, note.createdAt)
        assertEquals(now, note.modifiedAt)
        assertEquals(1, note.revision)
    }

    @Test fun acceptsTextAndOpaqueOptionalImageWithExactCaptureLimits() {
        val draft = NoteDraft("x".repeat(MAX_NOTE_TITLE_LENGTH), "y".repeat(MAX_NOTE_CONTENT_LENGTH), "private-image")
        val note = applyNoteCommand(emptyList(), person, create(draft), now)
        assertEquals(draft.title, note.title)
        assertEquals(draft.content, note.content)
        assertEquals(draft.imageId, note.imageId)
        assertProblem(NoteProblem.TITLE_TOO_LONG) {
            applyNoteCommand(emptyList(), person, create(draft.copy(title = draft.title + "x")), now)
        }
        assertProblem(NoteProblem.CONTENT_TOO_LONG) {
            applyNoteCommand(emptyList(), person, create(draft.copy(content = draft.content + "x")), now)
        }
        assertProblem(NoteProblem.TITLE_REQUIRED) {
            applyNoteCommand(emptyList(), person, create(NoteDraft("  ")), now)
        }
        assertProblem(NoteProblem.INVALID_IMAGE) {
            applyNoteCommand(emptyList(), person, create(NoteDraft("Lista", imageId = "")), now)
        }
    }

    @Test fun editingPreservesIdentityCreationAndLegacyProvenanceWhileCreatingNewVersion() {
        val original = saved().copy(imageId = "image-before", legacyIndicationId = "legacy-source")
        val edited = applyNoteCommand(listOf(original), person,
            NoteCommand.Edit("edit", original.id, original.revision, NoteDraft("Otro título", "Texto nuevo", original.imageId)),
            now.plusHours(1))
        assertEquals(original.id, edited.id)
        assertEquals(original.createdAt, edited.createdAt)
        assertEquals(original.legacyIndicationId, edited.legacyIndicationId)
        assertEquals(original.imageId, edited.imageId)
        assertEquals(2, edited.revision)
        assertEquals(now.plusHours(1), edited.modifiedAt)
        assertEquals("Objetos de ejemplo", original.content)
    }

    @Test fun imageReplacementAndRemovalAreExplicitDraftChanges() {
        val original = saved().copy(imageId = "first")
        val replacement = applyNoteCommand(listOf(original), person,
            NoteCommand.Edit("replace", original.id, 1, NoteDraft(original.title, original.content, "second")), now)
        val removed = applyNoteCommand(listOf(replacement), person,
            NoteCommand.Edit("remove", original.id, 2, NoteDraft(original.title, original.content, null)), now)
        assertEquals("first", original.imageId)
        assertEquals("second", replacement.imageId)
        assertNull(removed.imageId)
    }

    @Test fun deletionRetainsOriginalContentAndImageButRemovesNoteFromVisibleProjection() {
        val original = saved().copy(imageId = "kept-image")
        val deleted = applyNoteCommand(listOf(original), person, NoteCommand.Delete("delete", original.id, 1), now.plusDays(1))
        assertEquals(original.title, deleted.title)
        assertEquals(original.content, deleted.content)
        assertEquals(original.imageId, deleted.imageId)
        assertEquals(now.plusDays(1), deleted.deletedAt)
        assertEquals(2, deleted.revision)
        assertTrue(visibleNotes(listOf(deleted), person).isEmpty())
        assertProblem(NoteProblem.DELETED) {
            applyNoteCommand(listOf(deleted), person, NoteCommand.Edit("edit", deleted.id, 2, NoteDraft("Reescribir")), now)
        }
    }

    @Test fun staleRevisionsDuplicateIdsAndOtherPatientsCannotBeOverwritten() {
        val original = saved()
        assertProblem(NoteProblem.CONFLICT) { applyNoteCommand(listOf(original), person, create(), now) }
        assertProblem(NoteProblem.CONFLICT) {
            applyNoteCommand(listOf(original), person, NoteCommand.Delete("delete", original.id, 2), now)
        }
        assertProblem(NoteProblem.NOT_FOUND) {
            applyNoteCommand(listOf(original), "other-fictional-person", NoteCommand.Delete("delete", original.id, 1), now)
        }
        assertEquals(1, original.revision)
    }

    @Test fun visibleProjectionIsPatientScopedAndOrderedByLatestModification() {
        val original = saved()
        val recent = original.copy(id = "recent", modifiedAt = now.plusHours(1))
        val deleted = original.copy(id = "deleted", deletedAt = now.plusHours(2))
        val other = original.copy(id = "other", personUnderCareId = "another-fictional-person")
        assertEquals(listOf(recent, original), visibleNotes(listOf(original, deleted, other, recent), person))
    }

    @Test fun clockRollbackCannotMoveRevisionChronologyBeforePriorVersion() {
        val original = saved()
        val edited = applyNoteCommand(listOf(original), person,
            NoteCommand.Edit("edit", original.id, 1, NoteDraft("Otro título")), now.minusDays(1))
        val deleted = applyNoteCommand(listOf(edited), person, NoteCommand.Delete("delete", edited.id, 2), now.minusDays(2))
        assertEquals(now, edited.modifiedAt)
        assertEquals(now, deleted.modifiedAt)
        assertEquals(now, deleted.deletedAt)
    }

    @Test fun useCasesRejectStaleAuthorizationBeforeReadingOrMutatingRepository() = runBlocking {
        val repository = RepositoryDouble(listOf(saved()))
        val denied = actor.copy(authorizationCurrent = false)
        try { GetNotes(repository)(denied); fail("Expected authorization") } catch (_: NoteAuthorizationException) { }
        try { ManageNotes(repository)(denied, create(), now); fail("Expected authorization") } catch (_: NoteAuthorizationException) { }
        assertEquals(0, repository.calls)
        assertEquals(listOf(saved()), GetNotes(repository)(actor))
        assertEquals(1, repository.calls)
    }

    private fun assertProblem(expected: NoteProblem, block: () -> Unit) {
        try { block(); fail("Expected $expected") }
        catch (error: NoteValidationException) { assertEquals(expected, error.problem) }
    }

    private class RepositoryDouble(private val records: List<Note>) : NoteRepository {
        var calls = 0
        override suspend fun load(actor: NoteActor): List<Note> { calls++; return records }
        override suspend fun apply(actor: NoteActor, command: NoteCommand, now: LocalDateTime): Note {
            calls++
            return applyNoteCommand(records, actor.personUnderCareId, command, now)
        }
    }
}
