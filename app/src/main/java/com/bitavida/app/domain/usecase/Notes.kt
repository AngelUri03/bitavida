package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.MAX_NOTE_CONTENT_LENGTH
import com.bitavida.app.domain.model.MAX_NOTE_TITLE_LENGTH
import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.model.NoteDraft
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteAuthorizationException
import com.bitavida.app.domain.repository.NoteRepository
import com.bitavida.app.domain.validation.fitsTextLimit
import java.time.LocalDateTime

sealed interface NoteCommand {
    val operationId: String
    val id: String

    data class Create(override val operationId: String, override val id: String, val draft: NoteDraft) : NoteCommand
    data class Edit(override val operationId: String, override val id: String, val expectedRevision: Int, val draft: NoteDraft) : NoteCommand
    data class Delete(override val operationId: String, override val id: String, val expectedRevision: Int) : NoteCommand
}

enum class NoteProblem { TITLE_REQUIRED, TITLE_TOO_LONG, CONTENT_TOO_LONG, NOT_FOUND, DELETED, CONFLICT, INVALID_OPERATION, INVALID_IMAGE }
class NoteValidationException(val problem: NoteProblem) : Exception(problem.name)

class GetNotes(private val repository: NoteRepository) {
    suspend operator fun invoke(actor: NoteActor): List<Note> {
        if (!actor.authorizationCurrent) throw NoteAuthorizationException()
        return visibleNotes(repository.load(actor), actor.personUnderCareId)
    }
}

class ManageNotes(private val repository: NoteRepository) {
    suspend operator fun invoke(actor: NoteActor, command: NoteCommand, now: LocalDateTime = LocalDateTime.now()): Note {
        if (!actor.authorizationCurrent) throw NoteAuthorizationException()
        return repository.apply(actor, command, now)
    }
}

fun visibleNotes(records: List<Note>, personUnderCareId: String): List<Note> =
    records.filter { it.personUnderCareId == personUnderCareId && it.deletedAt == null }
        .distinctBy { it.id }.sortedWith(compareByDescending<Note> { it.modifiedAt }.thenBy { it.id })

/** Pure transition shared by the encrypted transaction and tests. No storage or Android APIs. */
fun applyNoteCommand(records: List<Note>, personUnderCareId: String, command: NoteCommand, now: LocalDateTime): Note {
    fun fail(problem: NoteProblem): Nothing = throw NoteValidationException(problem)
    if (personUnderCareId.isBlank() || command.id.isBlank() || command.operationId.isBlank()) fail(NoteProblem.INVALID_OPERATION)
    val original = records.singleOrNull { it.id == command.id && it.personUnderCareId == personUnderCareId }
    if (command is NoteCommand.Create && original != null) fail(NoteProblem.CONFLICT)
    if (command !is NoteCommand.Create) {
        if (original == null) fail(NoteProblem.NOT_FOUND)
        if (original.deletedAt != null) fail(NoteProblem.DELETED)
        val expected = when (command) {
            is NoteCommand.Edit -> command.expectedRevision
            is NoteCommand.Delete -> command.expectedRevision
            is NoteCommand.Create -> error("Already checked")
        }
        if (expected != original.revision) fail(NoteProblem.CONFLICT)
    }
    val draft = when (command) {
        is NoteCommand.Create -> command.draft
        is NoteCommand.Edit -> command.draft
        is NoteCommand.Delete -> null
    }
    draft?.let {
        if (it.title.isBlank()) fail(NoteProblem.TITLE_REQUIRED)
        if (!fitsTextLimit(it.title, MAX_NOTE_TITLE_LENGTH, original?.title)) fail(NoteProblem.TITLE_TOO_LONG)
        if (!fitsTextLimit(it.content.orEmpty(), MAX_NOTE_CONTENT_LENGTH, original?.content)) fail(NoteProblem.CONTENT_TOO_LONG)
        if (it.imageId != null && it.imageId.isBlank()) fail(NoteProblem.INVALID_IMAGE)
    }
    return when (command) {
        is NoteCommand.Create -> Note(command.id, personUnderCareId, command.draft.title.trim(),
            command.draft.content?.trim()?.takeIf(String::isNotEmpty), command.draft.imageId, now, now, 1)
        is NoteCommand.Edit -> requireNotNull(original).copy(
            title = if (command.draft.title == original.title) original.title else command.draft.title.trim(),
            content = if (command.draft.content.orEmpty() == original.content.orEmpty()) original.content
                else command.draft.content?.trim()?.takeIf(String::isNotEmpty),
            imageId = command.draft.imageId, modifiedAt = maxOf(now, original.modifiedAt), revision = original.revision + 1)
        is NoteCommand.Delete -> requireNotNull(original).copy(
            deletedAt = maxOf(now, original.modifiedAt), modifiedAt = maxOf(now, original.modifiedAt), revision = original.revision + 1)
    }
}
