package com.bitavida.app.data.notes

import android.content.Context
import android.net.Uri
import com.bitavida.app.data.images.encodePrivateImage
import com.bitavida.app.data.local.secure.NoteImageEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.repository.NoteActor
import com.bitavida.app.domain.repository.NoteAuthorizationException
import com.bitavida.app.domain.repository.NoteStorageException
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Gallery/camera bytes are normalized and copied into private SQLCipher storage, not retained as URIs. */
class NoteImageStore(context: Context) {
    private val appContext = context.applicationContext

    suspend fun importImage(actor: NoteActor, uri: Uri): String {
        var importedId: String? = null
        try {
            return operation {
                if (!actor.authorizationCurrent) throw NoteAuthorizationException()
                require(uri.scheme == "content")
                val source = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count == -1) break
                        total += count
                        require(total <= MAX_SOURCE_BYTES)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Image unavailable")
                val jpeg = try { encodePrivateImage(source) } finally { source.fill(0) }
                val id = UUID.randomUUID().toString()
                importedId = id
                try {
                    SecureDatabaseProvider.open(appContext).noteDao().storeImage(actor,
                        NoteImageEntity(id, actor.profileId, actor.personUnderCareId, jpeg, LocalDateTime.now().toString()))
                } finally { jpeg.fill(0) }
                id
            }
        } catch (cancelled: CancellationException) {
            importedId?.let { id ->
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { SecureDatabaseProvider.open(appContext).noteDao().discardImage(actor, id) }
                }
            }
            throw cancelled
        }
    }

    suspend fun read(actor: NoteActor, id: String): ByteArray? = operation {
        SecureDatabaseProvider.open(appContext).noteDao().readImage(actor, id)
    }

    /** Never deletes an image referenced by a saved note or an earlier immutable snapshot. */
    suspend fun discard(actor: NoteActor, id: String) = operation {
        SecureDatabaseProvider.open(appContext).noteDao().discardImage(actor, id)
    }

    private suspend fun <T> operation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: NoteAuthorizationException) { throw error }
        catch (error: Exception) { throw NoteStorageException(error) }
    }

    private companion object { const val MAX_SOURCE_BYTES = 16 * 1024 * 1024 }
}
