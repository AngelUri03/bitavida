package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class FootSurgeryRelatedQuestionsTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun footSurgeryQuestionsResolveToTheirOwnSpecificContent() {
        val ids = listOf("drenaje-absceso-pie", "cirugia-osteomielitis-pie", "amputacion-menor-pie", "amputacion-mayor-pierna")
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
        assertTrue(procedureSectionText(selected[0], "what")!!.contains("pus"))
        assertTrue(procedureSectionText(selected[1], "what")!!.contains("infección del hueso"))
        assertTrue(procedureSectionText(selected[2], "important")!!.contains("fantasma"))
        assertTrue(procedureSectionText(selected[3], "rehabilitation")!!.contains("prótesis"))
    }
}
