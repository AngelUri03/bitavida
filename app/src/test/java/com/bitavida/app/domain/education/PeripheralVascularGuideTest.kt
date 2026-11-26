package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class PeripheralVascularGuideTest {
    @Test fun vascularGuidesExplainDifferentProceduresAndRelatedQuestionsReachTheirAnswers() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val angioplasty = guides.single { it.id == "angioplastia-pierna" }
        val bypass = guides.single { it.id == "bypass-pierna" }

        assertTrue(angioplasty.during.contains("balón") && angioplasty.during.contains("stent"))
        assertTrue(angioplasty.preparation.contains("alergias") && angioplasty.preparation.contains("No suspendas"))
        assertTrue(bypass.during.contains("vena de la propia persona") && bypass.during.contains("anestesia general"))
        assertTrue(bypass.method.contains("otro camino") && bypass.after.contains("herida"))
        val answers = listOf(angioplasty, bypass).flatMap { guide ->
            guide.relatedQuestions.map { it.id to procedureSectionText(guide, it.sectionId) }
        }.toMap()
        assertTrue(answers.getValue("pierna-angioplastia-como")!!.contains("catéter"))
        assertTrue(answers.getValue("pierna-angioplastia-diferencia")!!.contains("angiografía"))
        assertTrue(answers.getValue("pierna-angioplastia-preparacion")!!.contains("alergias"))
        assertTrue(answers.getValue("pierna-angioplastia-despues")!!.contains("circulación"))
        assertTrue(answers.getValue("pierna-bypass-que")!!.contains("conducto"))
        assertTrue(answers.getValue("pierna-bypass-conducto")!!.contains("anestesia"))
        assertTrue(answers.getValue("pierna-bypass-diferencia")!!.contains("angioplastia"))
        assertTrue(answers.getValue("pierna-bypass-recuperacion")!!.contains("hospital"))
        listOf(angioplasty, bypass).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
            val sources = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("preparation").all { it in sources })
            assertTrue(guide.important.contains("atención urgente"))
        }
        assertTrue(searchProcedureGuides("angioplastia pierna", guides).any { it.id == angioplasty.id })
        assertTrue(searchProcedureGuides("bypass vascular", guides).any { it.id == bypass.id })
    }
}
