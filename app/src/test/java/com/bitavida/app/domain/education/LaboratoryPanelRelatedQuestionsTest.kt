package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class LaboratoryPanelRelatedQuestionsTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun questionsUseTheMatchingSourceSection() {
        val ids = listOf("panel-metabolico", "biometria-hematica", "vitamina-b12", "cetonas-sangre", "cetonas-orina")
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
        assertTrue(procedureSectionText(selected[0], "comparison")!!.contains("panel"))
        assertTrue(procedureSectionText(selected[1], "comparison")!!.contains("HbA1c"))
        assertTrue(procedureSectionText(selected[2], "absorption")!!.contains("nutrientes"))
        assertTrue(procedureSectionText(selected[3], "meaning")!!.contains("no siempre descarta"))
        assertTrue(procedureSectionText(selected[4], "limits")!!.contains("urgente"))
    }
}
