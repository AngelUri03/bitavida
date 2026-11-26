package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class SurgicalRelatedQuestionTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun specificQuestionsResolveAgainstTheirOwnGuide() {
        val ids = listOf("cateter-hemodialisis", "cateter-peritoneal", "bypass-coronario")
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
        assertTrue(procedureSectionText(selected[0], "difference")!!.contains("fístula"))
        assertTrue(procedureSectionText(selected[1], "difference")!!.contains("abdomen"))
        assertTrue(procedureSectionText(selected[2], "during")!!.contains("dormida"))
    }
}
