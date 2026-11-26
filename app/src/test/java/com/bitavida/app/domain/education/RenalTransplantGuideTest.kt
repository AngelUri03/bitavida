package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class RenalTransplantGuideTest {
    @Test fun transplantExplainsEvaluationSurgeryRecoveryAndRejectionWithoutPersonalRecommendations() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val guide = guides.single { it.id == "trasplante-renal" }
        assertTrue(guide.preparation.contains("varias visitas") && guide.preparation.contains("no suspendas"))
        assertTrue(guide.during.contains("anestesia general") && guide.during.contains("vejiga"))
        assertTrue(guide.method.contains("riñones propios permanecen") && guide.method.contains("donante viva"))
        assertTrue(guide.after.contains("diálisis") && guide.after.contains("más tiempo"))
        assertTrue(guide.important.contains("rechazo") && guide.important.contains("sin molestias"))
        assertTrue(guide.important.contains("no los inicies, cambies ni suspendas"))
        val answers = guide.relatedQuestions.associate { it.id to procedureSectionText(guide, it.sectionId) }
        assertEquals(5, answers.size)
        assertTrue(answers.getValue("trasplante-renal-que")!!.contains("donante"))
        assertTrue(answers.getValue("trasplante-renal-cirugia")!!.contains("anestesia general"))
        assertTrue(answers.getValue("trasplante-renal-rinones")!!.contains("riñones propios permanecen"))
        assertTrue(answers.getValue("trasplante-renal-funcion")!!.contains("más tiempo"))
        assertTrue(answers.getValue("trasplante-renal-rechazo")!!.contains("defensas"))
        assertEquals("22 de noviembre de 2026", guide.reviewedOn)
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        assertEquals("https://www.niddk.nih.gov/health-information/kidney-disease/kidney-failure/kidney-transplant", guide.source.reference)
        assertTrue(guide.sectionSourceReferences.getValue("during").contains(guide.source.reference))
        assertTrue(searchProcedureGuides("trasplante de riñon", guides).any { it.id == guide.id })
    }
}
