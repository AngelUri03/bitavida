package com.bitavida.app.ui.observations

import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult

enum class ObservationPhotoSource { CAMERA, GALLERY }

/** Only opaque identifiers cross the external-activity boundary; the clinical draft remains in memory. */
data class ObservationPhotoRequest(
    val source: ObservationPhotoSource,
    val draftId: String,
    val replacingPhotoId: String? = null,
)

internal data class PendingObservationPhoto(
    val request: ObservationPhotoRequest,
    val cameraToken: String? = null,
) {
    fun savedValues(): List<String> = listOf(request.source.name, request.draftId,
        request.replacingPhotoId.orEmpty(), cameraToken.orEmpty())

    companion object {
        fun restore(values: List<String>): PendingObservationPhoto? {
            if (values.size != 4 || values[1].isBlank()) return null
            val source = ObservationPhotoSource.entries.firstOrNull { it.name == values[0] } ?: return null
            val token = values[3].takeIf(String::isNotEmpty)
            if ((source == ObservationPhotoSource.CAMERA) != (token != null)) return null
            return PendingObservationPhoto(ObservationPhotoRequest(source, values[1],
                values[2].takeIf(String::isNotEmpty)), token)
        }
    }
}

/** A stale result never binds a photo to another draft, a changed replacement or a closed review. */
internal fun observationWithImportedPhoto(
    current: ObservationRecord?,
    request: ObservationPhotoRequest,
    importedId: String,
): ObservationRecord? {
    if (current == null || current.id != request.draftId || importedId.isBlank() ||
        current.result == ObservationResult.NOT_PERFORMED ||
        !ObservationCatalog.get(current.observationId).photoAllowed) return null
    val replaced = request.replacingPhotoId
    if (replaced != null && replaced !in current.localPhotoIds) return null
    if (replaced == null && current.localPhotoIds.size >= 3) return null
    val photos = if (replaced == null) current.localPhotoIds + importedId
        else current.localPhotoIds.map { if (it == replaced) importedId else it }
    return current.copy(localPhotoIds = photos.distinct())
}
