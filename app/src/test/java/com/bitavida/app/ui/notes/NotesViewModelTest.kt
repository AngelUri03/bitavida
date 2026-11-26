package com.bitavida.app.ui.notes

import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteRepository
import com.bitavida.app.domain.usecase.NoteCommand
import com.bitavida.app.domain.usecase.applyNoteCommand
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class NotesViewModelTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val actor = NoteActor("profile-example", "caregiver-example", "person-example", true)

    @Test fun createsTextAndTitleOnlyNotesAndEditsWithStableIdentity() = runBlocking {
        val repo = FakeNotes()
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.ChangeTitle("  Apuntes de la salida  "))
        vm.onAction(NotesAction.ChangeContent("Llevar su libro favorito."))
        vm.onAction(NotesAction.Save)
        yield()
        val original = vm.state.value.notes.single()
        assertEquals("Apuntes de la salida", original.title)
        assertEquals("Llevar su libro favorito.", original.content)
        assertNull(vm.state.value.form)
        vm.onAction(NotesAction.Open(original.id))
        vm.onAction(NotesAction.Edit)
        vm.onAction(NotesAction.ChangeTitle("Libro favorito"))
        vm.onAction(NotesAction.ChangeContent(""))
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals(original.id, vm.state.value.notes.single().id)
        assertEquals(2, vm.state.value.notes.single().revision)
        assertNull(vm.state.value.notes.single().content)
        assertEquals(1, repo.records.size)
    }

    @Test fun titleEditsRespectUnicodeLimitAndPreserveAnUntouchedLegacyBody() = runBlocking {
        val legacy = note("legacy").copy(content = "x".repeat(4_000))
        val repo = FakeNotes().apply { records += legacy }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Open(legacy.id))
        vm.onAction(NotesAction.Edit)
        val title = "👨‍👩‍👧‍👦".repeat(100)
        vm.onAction(NotesAction.ChangeTitle(title))
        assertEquals(title, vm.state.value.form!!.title)
        vm.onAction(NotesAction.ChangeTitle(title + "x"))
        assertEquals(title, vm.state.value.form!!.title)
        assertEquals(legacy.content, vm.state.value.form!!.content)
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals(title, vm.state.value.notes.single().title)
        assertEquals(legacy.content, vm.state.value.notes.single().content)
    }

    @Test fun blankTitleIsValidatedInlineWithoutPersisting() = runBlocking {
        val repo = FakeNotes()
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.ChangeTitle("   "))
        vm.onAction(NotesAction.Save)
        assertTrue(vm.state.value.titleError)
        assertNotNull(vm.state.value.error)
        assertNotNull(vm.state.value.form)
        assertTrue(repo.records.isEmpty())
        assertEquals(0, repo.attempts.size)
    }

    @Test fun doubleTapAndDismissDuringSaveProduceOneOperation() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeNotes().apply { beforeWrite = { gate.await() } }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.ChangeTitle("Idea para compartir"))
        vm.onAction(NotesAction.Save)
        vm.onAction(NotesAction.Save)
        vm.onAction(NotesAction.DismissSheet)
        yield()
        assertTrue(vm.state.value.saving)
        assertNotNull(vm.state.value.form)
        assertEquals(1, repo.attempts.size)
        gate.complete(Unit)
        yield()
        assertFalse(vm.state.value.saving)
        assertEquals(1, vm.state.value.notes.size)
        assertNull(vm.state.value.sheet)
    }

    @Test fun failedSaveRetainsDraftAndRetriesTheSameOperation() = runBlocking {
        val repo = FakeNotes().apply { fail = true }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.ChangeTitle("Información compartida"))
        val draft = vm.state.value.form
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals(draft, vm.state.value.form)
        assertNotNull(vm.state.value.error)
        repo.fail = false
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals(1, vm.state.value.notes.size)
        assertEquals(repo.attempts.first().operationId, repo.attempts.last().operationId)
    }

    @Test fun deleteRequiresConfirmationAndRetainsTheOriginalRecord() = runBlocking {
        val original = note("one")
        val repo = FakeNotes().apply { records += original }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Open(original.id))
        vm.onAction(NotesAction.RequestDelete)
        assertEquals(NotesSheet.DELETE, vm.state.value.sheet)
        assertNull(repo.records.single().deletedAt)
        vm.onAction(NotesAction.CancelDelete)
        assertEquals(NotesSheet.DETAIL, vm.state.value.sheet)
        assertEquals(0, repo.attempts.size)
        vm.onAction(NotesAction.RequestDelete)
        vm.onAction(NotesAction.ConfirmDelete)
        vm.onAction(NotesAction.ConfirmDelete)
        yield()
        assertTrue(vm.state.value.notes.isEmpty())
        assertEquals(now, repo.records.single().deletedAt)
        assertEquals(original.title, repo.records.single().title)
        assertEquals(original.content, repo.records.single().content)
        assertEquals(1, repo.attempts.size)
    }

    @Test fun dismissingAnEditDiscardsItsImageAndKeepsThePersistedOriginal() = runBlocking {
        val discarded = mutableSetOf<String>()
        val original = note("one").copy(imageId = "original-image")
        val repo = FakeNotes().apply { records += original }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this,
            discardImage = { discarded += it })
        yield()
        vm.onAction(NotesAction.Open(original.id))
        vm.onAction(NotesAction.Edit)
        vm.onAction(NotesAction.ChangeTitle("Título temporal"))
        vm.onAction(NotesAction.RequestImage(NotesPhotoSource.GALLERY))
        val request = withTimeout(1_000) { vm.photoRequests.first() }
        assertTrue(vm.acceptImportedImage(request, "temporary-image"))
        assertEquals("temporary-image", vm.state.value.form?.imageId)
        vm.onAction(NotesAction.DismissSheet)
        yield()
        assertEquals(original, repo.records.single())
        assertEquals(setOf("temporary-image"), discarded)
        assertNull(vm.state.value.form)
    }

    @Test fun replacingAndRemovingAnImageCleansOnlyStagedImages() = runBlocking {
        val discarded = mutableSetOf<String>()
        val vm = NotesViewModel(FakeNotes(), actor, nowProvider = { now }, scopeOverride = this,
            discardImage = { discarded += it })
        yield()
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.ChangeTitle("Nota con imagen"))
        vm.onAction(NotesAction.RequestImage(NotesPhotoSource.GALLERY))
        val first = withTimeout(1_000) { vm.photoRequests.first() }
        assertTrue(vm.state.value.imagePreparing)
        vm.onAction(NotesAction.Save)
        assertTrue(vm.state.value.notes.isEmpty())
        assertTrue(vm.acceptImportedImage(first, "first-image"))
        vm.onAction(NotesAction.RequestImage(NotesPhotoSource.CAMERA))
        val second = withTimeout(1_000) { vm.photoRequests.first() }
        assertTrue(vm.acceptImportedImage(second, "second-image"))
        yield()
        assertTrue(discarded.contains("first-image"))
        vm.onAction(NotesAction.RemoveImage)
        yield()
        assertNull(vm.state.value.form?.imageId)
        assertEquals(setOf("first-image", "second-image"), discarded)
        vm.onAction(NotesAction.Save)
        yield()
        assertNull(vm.state.value.notes.single().imageId)
    }

    @Test fun savedImageIsRetainedAndLateResultsNeverAttachToAnotherDraft() = runBlocking {
        val discarded = mutableSetOf<String>()
        val repo = FakeNotes()
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this,
            discardImage = { discarded += it })
        yield()
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.RequestImage(NotesPhotoSource.CAMERA))
        val stale = withTimeout(1_000) { vm.photoRequests.first() }
        vm.onAction(NotesAction.DismissSheet)
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.ChangeTitle("Una nueva nota"))
        assertFalse(vm.isImageRequestCurrent(stale))
        assertFalse(vm.acceptImportedImage(stale, "stale-image"))
        assertNull(vm.state.value.form?.imageId)
        vm.onAction(NotesAction.RequestImage(NotesPhotoSource.GALLERY))
        val current = withTimeout(1_000) { vm.photoRequests.first() }
        assertTrue(vm.acceptImportedImage(current, "saved-image"))
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals("saved-image", vm.state.value.notes.single().imageId)
        assertFalse(discarded.contains("saved-image"))
    }

    @Test fun cancelledGalleryPreservesExistingImageAndAllowsSaving() = runBlocking {
        val original = note("one").copy(imageId = "original-image")
        val discarded = mutableSetOf<String>()
        val repo = FakeNotes().apply { records += original }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this,
            discardImage = { discarded += it })
        yield()
        vm.onAction(NotesAction.Open(original.id))
        vm.onAction(NotesAction.Edit)
        vm.onAction(NotesAction.RequestImage(NotesPhotoSource.GALLERY))
        val cancelled = withTimeout(1_000) { vm.photoRequests.first() }
        vm.finishImageRequest(cancelled)
        assertFalse(vm.state.value.imagePreparing)
        assertEquals("original-image", vm.state.value.form?.imageId)
        assertTrue(discarded.isEmpty())
        vm.onAction(NotesAction.ChangeContent("Se conserva la imagen original."))
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals("original-image", vm.state.value.notes.single().imageId)
        assertTrue(discarded.isEmpty())
    }

    @Test fun anotherPatientsOrDeletedNotesNeverEnterTheMosaic() = runBlocking {
        val repo = FakeNotes().apply {
            records += note("visible")
            records += note("other").copy(personUnderCareId = "another-person")
            records += note("deleted").copy(deletedAt = now)
        }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        assertEquals(listOf("visible"), vm.state.value.notes.map { it.id })
        vm.onAction(NotesAction.Open("other"))
        assertNull(vm.state.value.sheet)
    }

    @Test fun lateInitialLoadCannotOverwriteACommittedNote() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeNotes().apply { beforeRead = { gate.await() } }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Create)
        vm.onAction(NotesAction.ChangeTitle("Nota reciente"))
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals("Nota reciente", vm.state.value.notes.single().title)
        gate.complete(Unit)
        yield()
        assertEquals("Nota reciente", vm.state.value.notes.single().title)
        assertFalse(vm.state.value.loading)
    }

    @Test fun editingALegacyNoteKeepsItsTranscribedProvenance() = runBlocking {
        val repo = FakeNotes().apply { records += note("legacy").copy(legacyIndicationId = "legacy-example") }
        val vm = NotesViewModel(repo, actor, nowProvider = { now }, scopeOverride = this)
        yield()
        vm.onAction(NotesAction.Open("legacy"))
        vm.onAction(NotesAction.Edit)
        vm.onAction(NotesAction.ChangeContent("Apunte actualizado"))
        vm.onAction(NotesAction.Save)
        yield()
        assertEquals("legacy-example", vm.state.value.notes.single().legacyIndicationId)
    }

    private fun note(id: String) = Note(id, actor.personUnderCareId, "Apunte ficticio",
        "Información declarada para esta prueba.", null, now.minusDays(1), now.minusDays(1), 1)

    private class FakeNotes : NoteRepository {
        val records = mutableListOf<Note>()
        val attempts = mutableListOf<NoteCommand>()
        private val operations = mutableMapOf<String, Note>()
        var beforeRead: suspend () -> Unit = {}
        var beforeWrite: suspend () -> Unit = {}
        var fail = false
        override suspend fun load(actor: NoteActor): List<Note> {
            val snapshot = records.toList()
            beforeRead()
            return snapshot
        }
        override suspend fun apply(actor: NoteActor, command: NoteCommand, now: LocalDateTime): Note {
            attempts += command
            beforeWrite()
            if (fail) error("Fictitious storage failure")
            operations[command.operationId]?.let { return it }
            return applyNoteCommand(records, actor.personUnderCareId, command, now).also { saved ->
                records.removeAll { it.id == saved.id }
                records += saved
                operations[command.operationId] = saved
            }
        }
    }
}
