package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class BariatricWoundRelatedQuestionsTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun specificQuestionsResolveToAnswersAboutTheirOwnProcedure() {
        val ids = listOf("manga-gastrica", "bypass-gastrico", "curacion-ulcera-pie", "desbridamiento-herida", "descarga-presion-pie")
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
        assertTrue(procedureSectionText(selected[0], "difference")!!.contains("intestino"))
        assertTrue(procedureSectionText(selected[1], "after")!!.contains("nutricional"))
        assertTrue(procedureSectionText(selected[2], "difference")!!.contains("desbridamiento"))
        assertTrue(procedureSectionText(selected[3], "expectation")!!.contains("área mayor"))
        assertTrue(procedureSectionText(selected[4], "difference")!!.contains("infección"))
    }
}
