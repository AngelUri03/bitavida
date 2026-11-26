package com.bitavida.app.ui.notes

import org.junit.Assert.*
import org.junit.Test

class NotesPhotoRequestTest {
    @Test fun restorationContainsOnlyOpaqueCorrelationMetadata() {
        val request = NotesPhotoRequest("request-example", NotesPhotoSource.CAMERA,
            "draft-example", "operation-example", "image-example")
        val pending = PendingNotesPhoto(request, "camera-example")
        assertEquals(pending, PendingNotesPhoto.restore(pending.savedValues()))
        assertEquals(6, pending.savedValues().size)
        assertFalse(pending.savedValues().any { it.startsWith("content:") })
    }

    @Test fun malformedOrMismatchedSourcesAreRejected() {
        assertNull(PendingNotesPhoto.restore(emptyList()))
        assertNull(PendingNotesPhoto.restore(listOf("request", "OTHER", "draft", "op", "", "")))
        assertNull(PendingNotesPhoto.restore(listOf("request", "CAMERA", "draft", "op", "", "")))
        assertNull(PendingNotesPhoto.restore(listOf("request", "GALLERY", "draft", "op", "", "camera")))
        assertNull(PendingNotesPhoto.restore(listOf("", "GALLERY", "draft", "op", "", "")))
    }
}
