package com.bitavida.app.ui.notes

enum class NotesPhotoSource { CAMERA, GALLERY }

/** Only opaque request metadata crosses gallery/camera recreation; no note text is serialized. */
data class NotesPhotoRequest(
    val requestId: String,
    val source: NotesPhotoSource,
    val draftId: String,
    val operationId: String,
    val replacingImageId: String? = null,
)

internal data class PendingNotesPhoto(
    val request: NotesPhotoRequest,
    val cameraToken: String? = null,
) {
    fun savedValues(): List<String> = listOf(request.requestId, request.source.name, request.draftId,
        request.operationId, request.replacingImageId.orEmpty(), cameraToken.orEmpty())

    companion object {
        fun restore(values: List<String>): PendingNotesPhoto? {
            if (values.size != 6 || values[0].isBlank() || values[2].isBlank() || values[3].isBlank()) return null
            val source = NotesPhotoSource.entries.firstOrNull { it.name == values[1] } ?: return null
            val token = values[5].takeIf(String::isNotEmpty)
            if ((source == NotesPhotoSource.CAMERA) != (token != null)) return null
            return PendingNotesPhoto(NotesPhotoRequest(values[0], source, values[2], values[3],
                values[4].takeIf(String::isNotEmpty)), token)
        }
    }
}
