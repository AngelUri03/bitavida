package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class EyeSurgeryEditorialTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures().associateBy { it.id }

    @Test fun cataractExplainsTheLensExperienceAndSpecificWarningSigns() {
        val guide = guides.getValue("cataratas")
        assertTrue(guide.description.contains("cristalino"))
        assertTrue(guide.description.contains("lente intraocular"))
        assertTrue(guide.during.contains("despierta"))
        assertTrue(guide.important.contains("pérdida de visión"))
        assertTrue(guide.extraSections.any { it.id == "risks" && it.body.contains("consentimiento") })
    }

    @Test fun vitrectomyExplainsRetinaVitreousAndGasBubbleSafeguards() {
        val guide = guides.getValue("vitrectomia")
        assertTrue(guide.description.contains("vítreo"))
        assertTrue(guide.description.contains("retina"))
        assertTrue(guide.during.contains("anestesia"))
        val bubble = guide.extraSections.single { it.id == "bubble" }
        assertTrue(bubble.body.contains("vuelos"))
        assertTrue(bubble.body.contains("no todas", ignoreCase = true))
        assertTrue(guide.important.contains("no es una cirugía rutinaria"))
    }

    @Test fun surgicalExplanationsHaveVersionedTraceableSourcesAndRemainPendingReview() {
        listOf("cataratas", "vitrectomia").forEach { id ->
            val guide = guides.getValue(id)
            val sourceUrls = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertFalse(guide.contentVersion.isNullOrBlank())
            assertTrue(guide.sectionSourceReferences.isNotEmpty())
            assertTrue(guide.sectionSourceReferences.values.flatten().all(sourceUrls::contains))
            assertTrue(guide.extraSections.all { it.sourceReferences.isNotEmpty() && it.sourceReferences.all(sourceUrls::contains) })
        }
    }
}
