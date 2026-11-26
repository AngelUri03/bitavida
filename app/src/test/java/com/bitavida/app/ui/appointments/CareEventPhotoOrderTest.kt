package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.repository.CareEventPhotoInput
import org.junit.Assert.assertEquals
import org.junit.Test

class CareEventPhotoOrderTest {
    @Test fun replacementKeepsItsSlotAndRemovedImagesDisappear() {
        val original = listOf("a", "b", "c")
        val pending = listOf(
            CareEventPhotoInput("replacement", byteArrayOf(1), replacesId = "b"),
            CareEventPhotoInput("new", byteArrayOf(2)),
        )
        assertEquals(listOf("a", "replacement", "c", "new"),
            visibleCareEventPhotoIds(original, setOf("b"), pending))
        assertEquals(listOf("a", "replacement", "new"),
            visibleCareEventPhotoIds(original, setOf("b", "c"), pending))
    }
}
