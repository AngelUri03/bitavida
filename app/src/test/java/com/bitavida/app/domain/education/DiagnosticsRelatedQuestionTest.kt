package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsRelatedQuestionTest {
    @Test fun relatedQuestionsOpenSectionsWithSpecificAnswers() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val selected = listOf("agudeza-visual", "presion-ocular", "fistula-av", "urocultivo", "angioplastia-coronaria")
            .map { id -> guides.single { it.id == id } }
        val answers = selected.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(15, answers.size)
        assertTrue(answers.getValue("agudeza-que")!!.contains("distinguen detalles"))
        assertTrue(answers.getValue("agudeza-como")!!.contains("otro ojo"))
        assertTrue(answers.getValue("agudeza-limitacion")!!.contains("retina"))
        assertTrue(answers.getValue("tonometria-que")!!.contains("presión dentro del ojo"))
        assertTrue(answers.getValue("tonometria-metodos")!!.contains("soplo"))
        assertTrue(answers.getValue("tonometria-glaucoma")!!.contains("no confirma ni descarta"))
        assertTrue(answers.getValue("fistula-que")!!.contains("arteria y una vena"))
        assertTrue(answers.getValue("fistula-proposito")!!.contains("fortalecerse"))
        assertTrue(answers.getValue("fistula-uso")!!.contains("necesita tiempo"))
        assertTrue(answers.getValue("urocultivo-que")!!.contains("bacterias"))
        assertTrue(answers.getValue("urocultivo-laboratorio")!!.contains("crecimiento de bacterias"))
        assertTrue(answers.getValue("urocultivo-diferencia")!!.contains("examen general"))
        assertTrue(answers.getValue("angioplastia-coronaria-stent")!!.contains("malla"))
        assertTrue(answers.getValue("angioplastia-coronaria-proceso")!!.contains("balón"))
        assertTrue(answers.getValue("angioplastia-coronaria-diferencia")!!.contains("bypass"))
        selected.forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
        }
    }
}
