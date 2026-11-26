package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class SpecialtyRelatedQuestionTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun specificQuestionsUseExistingAnswersInTheirOwnGuide() {
        val ids = listOf("examen-orina", "retinografia", "ultrasonido-renal", "consulta-cirugia-vascular")
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
        assertTrue(procedureSectionText(selected[0], "comparison")!!.contains("urocultivo"))
        assertTrue(procedureSectionText(selected[1], "comparison")!!.contains("OCT"))
        assertTrue(procedureSectionText(selected[2], "limitations")!!.contains("no mide"))
        assertTrue(procedureSectionText(selected[3], "important")!!.contains("no significa"))
    }
}
