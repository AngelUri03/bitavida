package com.bitavida.app.data.appointments

import android.content.Context
import android.net.Uri
import com.bitavida.app.data.images.encodePrivateImage
import com.bitavida.app.data.images.PrivateImagePolicy
import com.bitavida.app.data.local.secure.CareEventPhotoEntity
import com.bitavida.app.data.local.secure.SecureDatabaseProvider
import com.bitavida.app.domain.repository.CareEventActor
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imports only images; originals, URIs and metadata are never persisted. */
class CareEventPhotoStore(context: Context) {
    private val appContext = context.applicationContext

    suspend fun list(actor: CareEventActor, eventId: String): List<String> = withContext(Dispatchers.IO) {
        SecureDatabaseProvider.open(appContext).careEventPhotoDao().list(actor, eventId)
    }

    suspend fun read(actor: CareEventActor, eventId: String, id: String): ByteArray? = withContext(Dispatchers.IO) {
        SecureDatabaseProvider.open(appContext).careEventPhotoDao().read(actor, eventId, id)
    }

    fun discardCameraCapture(token: String?) {
        com.bitavida.app.platform.observations.ObservationCameraFiles.delete(appContext, token)
    }

    /** Reads and sanitizes a picker result without retaining the URI or original metadata. */
    suspend fun prepare(actor: CareEventActor, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        require(actor.authorizationCurrent && uri.scheme == "content")
        val source = appContext.contentResolver.openInputStream(uri)?.use { readCareEventPhoto(it) }
            ?: error("Image unavailable")
        try { encodePrivateImage(source, PrivateImagePolicy.CARE_EVENT) } finally { source.fill(0) }
    }

    suspend fun import(actor: CareEventActor, eventId: String, revision: Int, uri: Uri) = withContext(Dispatchers.IO) {
        val jpeg = prepare(actor, uri)
        try {
            SecureDatabaseProvider.open(appContext).careEventPhotoDao().add(actor,
                CareEventPhotoEntity(UUID.randomUUID().toString(), eventId, actor.profileId,
                    actor.personUnderCareId, actor.caregiverId, revision, System.currentTimeMillis(), jpeg))
        } finally { jpeg.fill(0) }
    }
}
