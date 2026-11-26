package com.bitavida.app.data.notes

import android.content.Context
import com.bitavida.app.data.local.secure.NoteEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.model.Note
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteAuthorizationException
import com.bitavida.app.domain.repository.NoteRepository
import com.bitavida.app.domain.repository.NoteStorageException
import com.bitavida.app.domain.usecase.NoteCommand
import com.bitavida.app.domain.usecase.NoteValidationException
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Notes and previous snapshots reuse the single Room/SQLCipher store. */
class EncryptedNoteRepository(context: Context) : NoteRepository {
    private val appContext = context.applicationContext

    override suspend fun load(actor: NoteActor): List<Note> = storage {
        val dao = SecureDatabaseProvider.open(appContext).noteDao()
        dao.pruneImages(actor, LocalDateTime.now().minusDays(1).toString())
        dao.load(actor).map { it.toDomain() }
    }

    override suspend fun apply(actor: NoteActor, command: NoteCommand, now: LocalDateTime): Note = storage {
        SecureDatabaseProvider.open(appContext).noteDao().apply(actor, command, noteRequestFingerprint(command), now).toDomain()
    }

    private suspend fun <T> storage(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: NoteValidationException) { throw error }
        catch (error: NoteAuthorizationException) { throw error }
        catch (error: Exception) { throw NoteStorageException(error) }
    }
}

internal fun NoteEntity.toDomain() = Note(id, personUnderCareId, title, content, imageId,
    LocalDateTime.parse(createdAt), LocalDateTime.parse(modifiedAt), revision,
    deletedAt?.let(LocalDateTime::parse), legacyIndicationId)

internal fun Note.toEntity(actor: NoteActor) = NoteEntity(id, actor.profileId, actor.caregiverId,
    personUnderCareId, title, content, imageId, createdAt.toString(), modifiedAt.toString(), revision,
    deletedAt?.toString(), legacyIndicationId)

internal fun noteRequestFingerprint(command: NoteCommand): String {
    val fields: List<String?> = listOf(command.operationId, command.id) + when (command) {
        is NoteCommand.Create -> listOf("CREATE", command.draft.title, command.draft.content, command.draft.imageId)
        is NoteCommand.Edit -> listOf("EDIT", command.expectedRevision.toString(),
            command.draft.title, command.draft.content, command.draft.imageId)
        is NoteCommand.Delete -> listOf("DELETE", command.expectedRevision.toString())
    }
    val canonical = fields.joinToString("") { value -> if (value == null) "-1:" else "${value.length}:$value" }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
