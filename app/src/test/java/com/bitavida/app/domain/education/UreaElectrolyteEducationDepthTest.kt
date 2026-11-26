package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class UreaElectrolyteEducationDepthTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun individualGuidesExplainDifferentMeasurementsAndLinkTheirQuestions() {
        val urea = guides.single { it.id == "urea-sangre" }
        val ions = guides.single { it.id == "electrolitos" }
        assertTrue(urea.extraSections.any { it.id == "route" && it.body.contains("orina") })
        assertTrue(ions.extraSections.any { it.id == "different" && it.body.contains("potasio") })
        for (guide in listOf(urea, ions)) {
            val sectionIds = guide.extraSections.map { it.id }.toSet()
            assertTrue(guide.relatedQuestions.size >= 3)
            assertTrue(guide.relatedQuestions.all { it.sectionId in sectionIds })
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
