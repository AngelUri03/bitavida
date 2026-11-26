package com.bitavida.app.ui.observations

import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.domain.model.ObservationTimePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ObservationDraftPhotoCleanupTest {
    private val draft = ObservationRecord(
        id = "fictional-draft",
        observationId = "OBS-001",
        personUnderCareId = "fictional-person",
        occurredOn = LocalDate.of(2026, 11, 20),
        occurredAt = LocalTime.of(9, 0),
        timePrecision = ObservationTimePrecision.EXACT,
        result = ObservationResult.NO_CHANGES,
        answers = emptyMap(),
        description = "",
        localPhotoIds = listOf("fictional-photo"),
        createdAt = LocalDate.of(2026, 11, 20).atTime(9, 0),
        actorId = "fictional-caregiver",
    )

    @Test
    fun closingTheFormOffersItsPhotosForUnlinkedOnlyCleanup() {
        assertEquals(draft.localPhotoIds, closedObservationDraftPhotoIds(draft, null))
    }

    @Test
    fun anotherDraftDoesNotKeepPhotosFromTheClosedForm() {
        val next = draft.copy(id = "another-fictional-draft", localPhotoIds = emptyList())
        assertEquals(draft.localPhotoIds, closedObservationDraftPhotoIds(draft, next))
    }

    @Test
    fun sameDraftAcrossRotationOrFieldChangesNeverDiscardsItsPhotos() {
        assertTrue(closedObservationDraftPhotoIds(draft, draft).isEmpty())
        assertTrue(closedObservationDraftPhotoIds(draft, draft.copy(description = "Texto ficticio")).isEmpty())
        assertTrue(closedObservationDraftPhotoIds(null, draft).isEmpty())
    }
}
