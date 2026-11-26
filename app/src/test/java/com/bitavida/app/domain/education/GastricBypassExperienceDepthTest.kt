package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class GastricBypassExperienceDepthTest {
    @Test fun explainsFoodRouteAndLongTermNutrition() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "bypass-gastrico" }
        val route = procedureSectionText(guide, "digestive-route")!!
        val followup = procedureSectionText(guide, "long-term-followup")!!
        assertTrue(route.contains("jugos digestivos"))
        assertTrue(route.contains("primer tramo intestinal"))
        assertTrue(followup.contains("absorción de algunos nutrientes"))
        assertTrue(followup.contains("no los ajustes por cuenta propia"))
        listOf("digestive-route", "long-term-followup").forEach { id ->
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
