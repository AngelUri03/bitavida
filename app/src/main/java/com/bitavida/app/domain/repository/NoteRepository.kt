package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.usecase.NoteCommand
import java.time.LocalDateTime

data class NoteActor(
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val authorizationCurrent: Boolean,
)

interface NoteRepository {
    /** Includes logically deleted notes so retained revisions remain verifiable. */
    suspend fun load(actor: NoteActor): List<Note>
    /** Authorization, image ownership, revision and idempotency are checked in one transaction. */
    suspend fun apply(actor: NoteActor, command: NoteCommand, now: LocalDateTime): Note
}

class NoteAuthorizationException : Exception("Note operation is not authorized.")
class NoteStorageException(cause: Throwable? = null) : Exception("Note storage unavailable.", cause)
