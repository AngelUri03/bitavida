package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class LaboratoryRelatedQuestionTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun specificQuestionsResolveToSectionsOfTheirOwnGuide() {
        val ids = listOf("perfil-lipidos", "funcion-hepatica", "cultivo-herida")
        val selected = ids.map { id -> guides.single { it.id == id } }
        selected.forEach { guide ->
            assertEquals(3, guide.relatedQuestions.size)
            assertEquals(guide.relatedQuestions.size, guide.relatedQuestions.map { it.id }.toSet().size)
            guide.relatedQuestions.forEach { question ->
                assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
            }
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
        assertTrue(procedureSectionText(selected[0], "report")!!.contains("calculado"))
        assertTrue(procedureSectionText(selected[1], "patterns")!!.contains("fosfatasa"))
        assertTrue(procedureSectionText(selected[2], "culture")!!.contains("multipliquen"))
    }
}
