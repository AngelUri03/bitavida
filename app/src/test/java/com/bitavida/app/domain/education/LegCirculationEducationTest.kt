package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class LegCirculationEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun pressureIndexAndDopplerRemainDistinctAndQuestionsResolve() {
        val index = guides.single { it.id == "indice-tobillo-brazo" }
        val doppler = guides.single { it.id == "doppler-arterial-pierna" }
        assertTrue(index.description.contains("presión arterial"))
        assertTrue(index.during.contains("manguitos"))
        assertTrue(index.method.contains("no usa rayos X"))
        assertTrue(doppler.extraSections.any { it.id == "comparison" && it.body.contains("presiones") })
        for (guide in listOf(index, doppler)) {
            assertEquals(3, guide.relatedQuestions.size)
            guide.relatedQuestions.forEach { question ->
                assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
            }
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
