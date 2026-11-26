package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class SpecialistConsultRelatedQuestionTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun topicSpecificQuestionsResolveToTheirDocumentedAnswers() {
        val ids = listOf(
            "consulta-endocrinologia",
            "consulta-nefrologia",
            "consulta-cardiologia",
            "consulta-neurologia",
            "consulta-geriatria",
        )
        val selected = ids.map { id -> guides.single { it.id == id } }
        selected.forEach { guide ->
            assertEquals(3, guide.relatedQuestions.size)
            assertEquals(3, guide.relatedQuestions.map { it.id }.toSet().size)
            guide.relatedQuestions.forEach { question ->
                assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
            }
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
        assertTrue(procedureSectionText(selected[0], "insulin")!!.contains("páncreas"))
        assertTrue(procedureSectionText(selected[1], "purpose")!!.contains("diálisis"))
        assertTrue(procedureSectionText(selected[2], "tests")!!.contains("electrocardiograma"))
        assertTrue(procedureSectionText(selected[3], "examination")!!.contains("reflejo"))
        assertTrue(procedureSectionText(selected[4], "function")!!.contains("actividades"))
    }
}
