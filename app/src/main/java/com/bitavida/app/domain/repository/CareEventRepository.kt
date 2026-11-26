package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareEventDraft
import java.time.LocalDateTime

data class CareEventActor(val profileId: String, val caregiverId: String, val personUnderCareId: String, val authorizationCurrent: Boolean)

sealed interface CareEventCommand {
    val operationId: String
    val id: String
    data class Create(override val operationId: String, override val id: String, val draft: CareEventDraft) : CareEventCommand
    data class Revise(override val operationId: String, override val id: String, val expectedRevision: Int, val draft: CareEventDraft) : CareEventCommand
}

/** Ephemeral encoded photo selected for the same atomic commit as its care event. */
const val CARE_EVENT_MAX_PHOTO_BYTES: Int = 2 * 1024 * 1024
class CareEventPhotoInput(val id: String, val jpeg: ByteArray, val replacesId: String? = null)

interface CareEventRepository {
    suspend fun load(actor: CareEventActor): List<CareEvent>
    suspend fun history(actor: CareEventActor, id: String): List<CareEvent>
    suspend fun hasSensitiveConsent(actor: CareEventActor): Boolean
    suspend fun acceptSensitiveConsent(actor: CareEventActor, acceptedAtEpochMillis: Long)
    suspend fun save(actor: CareEventActor, command: CareEventCommand, now: LocalDateTime): CareEvent
    suspend fun saveWithPhotos(actor: CareEventActor, command: CareEventCommand, now: LocalDateTime,
        photos: List<CareEventPhotoInput>, removedPhotoIds: Set<String> = emptySet()): CareEvent {
        require(photos.isEmpty() && removedPhotoIds.isEmpty())
        return save(actor, command, now)
    }
}

class CareEventSensitiveConsentException : Exception("Sensitive data consent is required.")
class CareEventAuthorizationException : Exception("Care event operation is not authorized.")
class CareEventStorageException(cause: Throwable? = null) : Exception("Care event storage unavailable.", cause)
