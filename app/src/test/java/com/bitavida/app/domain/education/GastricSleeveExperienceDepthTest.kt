package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class GastricSleeveExperienceDepthTest {
    @Test fun explainsStomachChangesAndIndividualizedFoodFollowup() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "manga-gastrica" }
        assertTrue(procedureSectionText(guide, "stomach-change")!!.contains("no puede volver a colocarse"))
        assertTrue(procedureSectionText(guide, "stomach-change")!!.contains("apetito"))
        assertTrue(procedureSectionText(guide, "food-followup")!!.contains("líquidos"))
        assertTrue(procedureSectionText(guide, "food-followup")!!.contains("cuándo avanzar para cada persona"))
        listOf("stomach-change", "food-followup").forEach { id ->
            assertTrue(guide.extraSections.single { it.id == id }.sourceReferences.isNotEmpty())
        }
        assertEquals(3, guide.relatedQuestions.size)
        guide.relatedQuestions.forEach { question ->
            assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
        }
        assertEquals("22 de noviembre de 2026", guide.reviewedOn)
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
    }
}
