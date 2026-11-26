package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class DialysisAccessEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun accessesExplainVenousAndAbdominalRoutesWithoutEquatingThem() {
        val venous = guides.single { it.id == "cateter-hemodialisis" }
        val peritoneal = guides.single { it.id == "cateter-peritoneal" }
        assertTrue(venous.description.contains("vena"))
        assertTrue(venous.extraSections.any { it.id == "difference" && it.body.contains("fístula") })
        assertTrue(peritoneal.description.contains("abdomen"))
        assertTrue(peritoneal.extraSections.any { it.id == "difference" && it.body.contains("vena") })
        for (guide in listOf(venous, peritoneal)) {
            assertTrue(guide.during.contains("anestesia"))
            assertTrue(guide.important.contains("urgente"))
            assertTrue(guide.questions.size in 3..5)
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
