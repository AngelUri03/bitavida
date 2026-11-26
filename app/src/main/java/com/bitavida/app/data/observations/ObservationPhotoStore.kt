package com.bitavida.app.data.observations

import android.content.Context
import android.net.Uri
import com.bitavida.app.data.images.encodePrivateImage
import com.bitavida.app.data.local.secure.ObservationPhotoEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.repository.GLUCOSE_SENSITIVE_CONSENT_VERSION
import com.bitavida.app.domain.repository.GlucoseActor
import com.bitavida.app.domain.repository.GlucoseAuthorizationException
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Picker imports are copied into SQLCipher; original URI and EXIF never enter storage. */
class ObservationPhotoStore(context: Context) {
    private val appContext = context.applicationContext

    suspend fun importPhoto(actor: GlucoseActor, uri: Uri): String = operation {
        require(actor.authorizationCurrent)
        require(uri.scheme == "content")
        val source = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count: Int
            var total = 0
            while (stream.read(buffer).also { count = it } != -1) {
                total += count
                require(total <= MAX_SOURCE_BYTES)
                bytes.write(buffer, 0, count)
            }
            bytes.toByteArray()
        } ?: error("Image unavailable")
        val jpeg = try { encodePrivateImage(source) } finally { source.fill(0) }
        val id = UUID.randomUUID().toString()
        try {
            SecureDatabaseProvider.open(appContext).observationDao().storePhoto(
                ObservationPhotoEntity(id, actor.profileId, actor.personUnderCareId, jpeg),
                actor.caregiverId, GLUCOSE_SENSITIVE_CONSENT_VERSION,
            )
        } finally { jpeg.fill(0) }
        id
    }

    suspend fun read(actor: GlucoseActor, id: String): ByteArray? = operation {
        val dao = SecureDatabaseProvider.open(appContext).observationDao()
        if (!actor.authorizationCurrent || dao.authorized(actor.profileId, actor.caregiverId,
                actor.personUnderCareId, GLUCOSE_SENSITIVE_CONSENT_VERSION) != 1
        ) throw GlucoseAuthorizationException()
        dao.photo(id, actor.profileId, actor.personUnderCareId)?.jpeg
    }

    /** Reads only a photo linked to this persisted observation and authorized person. */
    suspend fun readForReport(actor: GlucoseActor, recordId: String, photoId: String): ByteArray? = operation {
        val dao = SecureDatabaseProvider.open(appContext).observationDao()
        if (!actor.authorizationCurrent || dao.authorized(actor.profileId, actor.caregiverId,
                actor.personUnderCareId, GLUCOSE_SENSITIVE_CONSENT_VERSION) != 1
        ) throw GlucoseAuthorizationException()
        dao.linkedPhoto(recordId, photoId, actor.profileId, actor.personUnderCareId)?.jpeg
    }

    /** Never removes an image attached to a saved observation. */
    suspend fun discard(actor: GlucoseActor, id: String) = operation {
        if (!actor.authorizationCurrent) throw GlucoseAuthorizationException()
        val dao = SecureDatabaseProvider.open(appContext).observationDao()
        if (dao.currentRelationship(actor.profileId, actor.caregiverId, actor.personUnderCareId) != 1) {
            throw GlucoseAuthorizationException()
        }
        dao.discardUnlinkedPhoto(id, actor.profileId, actor.personUnderCareId)
    }

    private suspend fun <T> operation(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: GlucoseAuthorizationException) { throw error }
        catch (error: Exception) { throw ObservationStorageException(error) }
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 16 * 1024 * 1024
    }
}
