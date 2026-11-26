package com.bitavida.app.ui.observations

import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.domain.model.ObservationTimePrecision
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationPhotoRequestTest {
    private val record = ObservationRecord(
        id = "fictional-draft",
        observationId = "OBS-002",
        personUnderCareId = "fictional-person",
        occurredOn = LocalDate.of(2026, 11, 18),
        occurredAt = LocalTime.of(10, 30),
        timePrecision = ObservationTimePrecision.EXACT,
        result = ObservationResult.CHANGES,
        answers = emptyMap(),
        description = "Ampolla ficticia observada.",
        createdAt = LocalDateTime.of(2026, 11, 18, 10, 30),
        actorId = "fictional-caregiver",
        localPhotoIds = listOf("old-photo"),
    )

    @Test fun pendingCameraRequestRestoresOnlyOpaqueIdentifiers() {
        val pending = PendingObservationPhoto(
            ObservationPhotoRequest(
                ObservationPhotoSource.CAMERA,
                record.id,
                replacingPhotoId = "old-photo",
            ),
            cameraToken = "12345678-1234-1234-1234-123456789abc",
        )
        assertEquals(pending, PendingObservationPhoto.restore(pending.savedValues()))
        assertFalse(pending.savedValues().any { it.contains(record.description) })
    }

    @Test fun importedPhotoReplacesOnlyTheRequestedPhotoForTheSameDraft() {
        val request = ObservationPhotoRequest(
            ObservationPhotoSource.GALLERY,
            record.id,
            replacingPhotoId = "old-photo",
        )
        val updated = observationWithImportedPhoto(record, request, "new-photo")
        assertEquals(listOf("new-photo"), updated!!.localPhotoIds)
        assertNull(observationWithImportedPhoto(record.copy(id = "other"), request, "new-photo"))
        assertNull(observationWithImportedPhoto(
            record.copy(result = ObservationResult.NOT_PERFORMED),
            request,
            "new-photo",
        ))
    }

    @Test fun photoContractUsesPickerAndPrivateNarrowFileProviderWithoutBroadPermissions() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val source = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationPhotos.kt",
        ).readText()
        val paths = projectFile("app/src/main/res/xml/observation_camera_paths.xml").readText()
        assertTrue(source.contains("ActivityResultContracts.TakePicture()"))
        assertTrue(source.contains("ActivityResultContracts.PickVisualMedia()"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android:grantUriPermissions=\"true\""))
        assertFalse(manifest.contains("android.permission.CAMERA"))
        assertFalse(manifest.contains("android.permission.READ_MEDIA_IMAGES"))
        assertFalse(manifest.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertTrue(paths.contains("path=\"observation_camera/\""))
        assertFalse(paths.contains("path=\".\""))
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
