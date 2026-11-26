package com.bitavida.app.data.notes

import com.bitavida.app.data.local.secure.NoteDao
import com.bitavida.app.data.local.secure.NoteEntity
import com.bitavida.app.data.local.secure.NoteImageEntity
import com.bitavida.app.data.local.secure.NoteRevisionEntity
import com.bitavida.app.domain.model.NoteDraft
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteAuthorizationException
import com.bitavida.app.domain.usecase.NoteCommand
import com.bitavida.app.domain.usecase.NoteProblem
import com.bitavida.app.domain.usecase.NoteValidationException
import com.bitavida.app.domain.usecase.applyNoteCommand
import com.bitavida.app.domain.usecase.visibleNotes
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Runs production transaction bodies using query doubles; it does not claim device SQLCipher coverage. */
class NotePersistenceTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val actor = NoteActor("fictional-profile", "fictional-caregiver", "fictional-person", true)
    private fun create(imageId: String? = null) =
        NoteCommand.Create("create", "note", NoteDraft("Lista ficticia", "Contenido de ejemplo", imageId))
    private fun image(id: String) = NoteImageEntity(id, actor.profileId, actor.personUnderCareId,
        byteArrayOf(1, 2, 3), now.toString())
    private suspend fun NoteDao.apply(command: NoteCommand, at: LocalDateTime = now) =
        apply(actor, command, noteRequestFingerprint(command), at)

    @Test fun storageMappingPreservesEveryFieldIncludingLogicalDeletionAndLegacyIdentity() {
        val note = applyNoteCommand(emptyList(), actor.personUnderCareId, create("private-image"), now)
            .copy(legacyIndicationId = "legacy-source", deletedAt = now.plusHours(1), revision = 2)
        assertEquals(note, note.toEntity(actor).toDomain())
        assertEquals(actor.profileId, note.toEntity(actor).profileId)
    }

    @Test fun exactRetriesReturnOriginalSnapshotWithoutDuplicatingOrOverwritingLaterEdits() = runBlocking {
        val dao = QueryDouble(actor)
        val original = dao.apply(create())
        val edit = NoteCommand.Edit("edit", original.id, 1, NoteDraft("Título editado", original.content))
        val updated = dao.apply(edit, now.plusMinutes(1))
        assertEquals(original, dao.apply(create(), now.plusDays(1)))
        assertEquals(updated, dao.load(actor).single())
        assertEquals(listOf(original, updated), dao.revisions(original.id).map { it.snapshot })
    }

    @Test fun reportHistoryIsAuthorizedReadOnlyAndKeepsAllVersions() = runBlocking {
        val dao = QueryDouble(actor)
        dao.storeImage(actor, image("private"))
        val original = dao.apply(create("private"))
        val updated = dao.apply(NoteCommand.Edit("edit-report", original.id, 1,
            NoteDraft("Título posterior", "Contenido posterior", "private")), now.plusMinutes(1))
        val imageCount = dao.images.size
        val revisionCount = dao.history.size
        val result = dao.loadReportHistory(actor)
        assertEquals(listOf(updated), result.current)
        assertEquals(listOf(original, updated), result.revisions.map { it.snapshot })
        assertEquals(imageCount, dao.images.size)
        assertEquals(revisionCount, dao.history.size)
        dao.authorized = false
        expectDenied { dao.loadReportHistory(actor) }
    }

    @Test fun reusingOperationIdForDifferentInputIsRejectedBeforeChangingStoredNote() = runBlocking {
        val dao = QueryDouble(actor)
        val original = dao.apply(create())
        val changed = create().copy(draft = NoteDraft("Otro título"))
        expectProblem(NoteProblem.CONFLICT) { dao.apply(changed) }
        assertEquals(original, dao.load(actor).single())
        assertEquals(1, dao.history.size)
    }

    @Test fun editingOrDeletingWithStaleRevisionPreservesCurrentNoteAndHistory() = runBlocking {
        val dao = QueryDouble(actor)
        val original = dao.apply(create())
        val edit = NoteCommand.Edit("edit", original.id, 1, NoteDraft("Título actualizado"))
        val updated = dao.apply(edit)
        expectProblem(NoteProblem.CONFLICT) {
            dao.apply(NoteCommand.Edit("stale-edit", original.id, 1, NoteDraft("Texto viejo")))
        }
        expectProblem(NoteProblem.CONFLICT) { dao.apply(NoteCommand.Delete("stale-delete", original.id, 1)) }
        assertEquals(updated, dao.load(actor).single())
        assertEquals(2, dao.history.size)
    }

    @Test fun authorizationIsRecheckedForReadsImagesAndIdempotentRetries() = runBlocking {
        val dao = QueryDouble(actor)
        dao.storeImage(actor, image("image"))
        val command = create("image")
        dao.apply(command)
        dao.authorized = false
        expectDenied { dao.apply(command) }
        expectDenied { dao.load(actor) }
        expectDenied { dao.readImage(actor, "image") }
        expectDenied { dao.discardImage(actor, "image") }
        expectDenied { dao.storeImage(actor, image("another")) }
        assertEquals(1, dao.history.size)
        assertEquals(1, dao.images.size)
    }

    @Test fun imageMustBelongToSameAuthorizedProfileAndPerson() = runBlocking {
        val dao = QueryDouble(actor)
        dao.insertImage(image("foreign-profile").copy(profileId = "other-fictional-profile"))
        dao.insertImage(image("foreign-person").copy(personUnderCareId = "other-fictional-person"))
        expectProblem(NoteProblem.INVALID_IMAGE) { dao.apply(create("foreign-profile")) }
        expectProblem(NoteProblem.INVALID_IMAGE) { dao.apply(create("foreign-person")) }
        assertNull(dao.readImage(actor, "foreign-profile"))
        assertNull(dao.readImage(actor, "foreign-person"))
        assertTrue(dao.current.isEmpty())
        assertTrue(dao.history.isEmpty())
    }

    @Test fun imageReplacementRetainsPreviousSnapshotAndCannotDiscardAnyReferencedImage() = runBlocking {
        val dao = QueryDouble(actor)
        dao.storeImage(actor, image("first"))
        dao.storeImage(actor, image("second"))
        val original = dao.apply(create("first"))
        val edited = dao.apply(NoteCommand.Edit("edit", original.id, 1,
            NoteDraft(original.title, original.content, "second")))
        dao.discardImage(actor, "first")
        dao.discardImage(actor, "second")
        assertEquals("second", edited.imageId)
        assertEquals("first", dao.revisions(original.id).first().snapshot.imageId)
        assertEquals(setOf("first", "second"), dao.images.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), dao.readImage(actor, "first"))
    }

    @Test fun logicalDeletionKeepsAuditContentAndImageWithoutLeavingVisibleNote() = runBlocking {
        val dao = QueryDouble(actor)
        dao.storeImage(actor, image("kept"))
        val original = dao.apply(create("kept"))
        val deleted = dao.apply(NoteCommand.Delete("delete", original.id, 1), now.plusHours(1))
        dao.discardImage(actor, "kept")
        assertEquals(original.content, deleted.content)
        assertEquals(now.plusHours(1).toString(), deleted.deletedAt)
        assertEquals("DELETED", dao.revisions(original.id).last().changeKind)
        assertTrue(visibleNotes(dao.load(actor).map { it.toDomain() }, actor.personUnderCareId).isEmpty())
        assertArrayEquals(byteArrayOf(1, 2, 3), dao.readImage(actor, "kept"))
        assertEquals(deleted, dao.apply(NoteCommand.Delete("delete", original.id, 1), now.plusHours(2)))
    }

    @Test fun discardedDraftImagesAreRemovedWhileCurrentAndHistoricalImagesRemainDurable() = runBlocking {
        val dao = QueryDouble(actor)
        dao.storeImage(actor, image("draft"))
        dao.storeImage(actor, image("linked"))
        val original = dao.apply(create("linked"))
        dao.apply(NoteCommand.Edit("remove-image", original.id, 1, NoteDraft(original.title, original.content)))
        dao.discardImage(actor, "draft")
        dao.pruneImages(actor, now.plusDays(2).toString())
        assertNull(dao.readImage(actor, "draft"))
        assertArrayEquals(byteArrayOf(1, 2, 3), dao.readImage(actor, "linked"))
        assertEquals(setOf("linked"), dao.images.keys)
    }

    @Test fun characterLimitsAreRevalidatedBeforeAnyNoteOrRevisionWrite() = runBlocking {
        val dao = QueryDouble(actor)
        expectProblem(NoteProblem.TITLE_TOO_LONG) {
            dao.apply(create().copy(draft = NoteDraft("x".repeat(101))))
        }
        assertTrue(dao.current.isEmpty())
        assertTrue(dao.history.isEmpty())
        val original = dao.apply(create())
        expectProblem(NoteProblem.CONTENT_TOO_LONG) {
            dao.apply(NoteCommand.Edit("oversized", original.id, original.revision,
                NoteDraft(original.title, "x".repeat(2_001))))
        }
        assertEquals(original, dao.load(actor).single())
        assertEquals(1, dao.history.size)
        val legacy = original.copy(title = " ".repeat(2) + "y".repeat(200), content = "z".repeat(4_000))
        dao.current[original.id] = legacy
        val retained = dao.apply(NoteCommand.Edit("legacy-retained", legacy.id, legacy.revision,
            NoteDraft(legacy.title, legacy.content)))
        assertEquals(legacy.title, retained.title)
        assertEquals(legacy.content, retained.content)
    }

    @Test fun fingerprintDistinguishesNullsFieldBoundariesImagesAndRevision() {
        val base = create()
        val variants = listOf<NoteCommand>(base,
            base.copy(draft = base.draft.copy(content = null)),
            base.copy(draft = base.draft.copy(content = "")),
            base.copy(draft = base.draft.copy(title = "x", content = "y:z")),
            base.copy(draft = base.draft.copy(title = "x:y", content = "z")),
            base.copy(draft = base.draft.copy(imageId = "image")),
            NoteCommand.Edit(base.operationId, base.id, 1, base.draft),
            NoteCommand.Edit(base.operationId, base.id, 2, base.draft))
        assertEquals(variants.size, variants.map(::noteRequestFingerprint).distinct().size)
    }

    private suspend fun expectProblem(expected: NoteProblem, block: suspend () -> Unit) {
        try { block(); fail("Expected $expected") }
        catch (error: NoteValidationException) { assertEquals(expected, error.problem) }
    }

    private suspend fun expectDenied(block: suspend () -> Unit) {
        try { block(); fail("Expected authorization") } catch (_: NoteAuthorizationException) { }
    }

    private class QueryDouble(private val owner: NoteActor) : NoteDao() {
        val current = linkedMapOf<String, NoteEntity>()
        val history = linkedMapOf<String, NoteRevisionEntity>()
        val images = linkedMapOf<String, NoteImageEntity>()
        var authorized = true
        override suspend fun currentRelationship(profile: String, actor: String, person: String) =
            if (authorized && profile == owner.profileId && actor == owner.caregiverId && person == owner.personUnderCareId) 1 else 0
        override suspend fun records(profile: String, person: String) =
            current.values.filter { it.profileId == profile && it.personUnderCareId == person }
        override suspend fun operation(operationId: String) = history[operationId]
        override suspend fun revisions(id: String) = history.values.filter { it.noteId == id }.sortedBy { it.snapshot.revision }
        override suspend fun image(id: String, profile: String, person: String) =
            images[id]?.takeIf { it.profileId == profile && it.personUnderCareId == person }?.let { it.copy(jpeg = it.jpeg.copyOf()) }
        override suspend fun ownsImage(id: String, profile: String, person: String) =
            if (image(id, profile, person) != null) 1 else 0
        override suspend fun insert(entity: NoteEntity) { check(entity.id !in current); current[entity.id] = entity }
        override suspend fun update(entity: NoteEntity): Int {
            if (entity.id !in current) return 0
            current[entity.id] = entity
            return 1
        }
        override suspend fun insertRevision(entity: NoteRevisionEntity) {
            check(entity.operationId !in history)
            check(history.values.none { it.noteId == entity.noteId && it.snapshot.revision == entity.snapshot.revision })
            history[entity.operationId] = entity
        }
        override suspend fun insertImage(entity: NoteImageEntity) {
            check(entity.id !in images)
            images[entity.id] = entity.copy(jpeg = entity.jpeg.copyOf())
        }
        override suspend fun discardUnlinkedImage(id: String, profile: String, person: String) {
            val candidate = images[id] ?: return
            if (candidate.profileId == profile && candidate.personUnderCareId == person &&
                current.values.none { it.imageId == id } && history.values.none { it.snapshot.imageId == id }) images.remove(id)
        }
        override suspend fun discardAbandonedImages(profile: String, person: String, before: String) {
            images.values.filter { it.importedAt < before }.map { it.id }
                .forEach { discardUnlinkedImage(it, profile, person) }
        }
    }
}
