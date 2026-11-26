package com.bitavida.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationGuideCatalogTest {
    @Test
    fun everyFixedObservationHasOneSpecificOfflineGuide() {
        assertEquals(27, ObservationGuideCatalog.all.size)
        assertEquals(
            ObservationCatalog.all.map(ObservationDefinition::id),
            ObservationGuideCatalog.all.map(ObservationGuide::observationId),
        )
        ObservationGuideCatalog.all.forEach { guide ->
            assertTrue(guide.observationId, guide.lookFor.size >= 3)
            assertTrue(guide.observationId, guide.examples.size >= 2)
            assertTrue(guide.observationId, guide.usefulInformation.size >= 3)
            val text = (guide.lookFor + guide.examples + guide.usefulInformation).joinToString(" ")
            listOf("diagnóstico", "tratamiento", "dosis recomendada", "causado por").forEach {
                assertFalse(guide.observationId, text.contains(it, ignoreCase = true))
            }
        }
    }

    @Test
    fun photographsAreOptionalForAllCatalogEntries() {
        assertEquals(27, ObservationCatalog.all.count(ObservationDefinition::photoAllowed))
    }
}
