package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ImagingPreoperativeRelatedQuestionsTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun specificQuestionsResolveToRelevantAnswers() {
        val ids = listOf("angiografia-retina", "holter", "radiografia-pie", "resonancia-pie", "valoracion-preoperatoria")
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
        assertTrue(procedureSectionText(selected[0], "what")!!.contains("vena"))
        assertTrue(procedureSectionText(selected[1], "method")!!.contains("diario"))
        assertTrue(procedureSectionText(selected[2], "during")!!.contains("incomodar"))
        assertTrue(procedureSectionText(selected[3], "method")!!.contains("Algunas"))
        assertTrue(procedureSectionText(selected[4], "important")!!.contains("No suspendas"))
    }
}
