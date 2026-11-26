package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class EyeAndFootRelatedQuestionTest {
    @Test fun specificQuestionsOpenSectionsWithRelevantAnswers() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val selected = listOf("diapason", "pulsos-pie", "fondo-ojo", "cataratas", "vitrectomia")
            .map { id -> guides.single { it.id == id } }
        val answers = selected.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(18, answers.size)
        assertTrue(answers.getValue("diapason-sensacion")!!.contains("vibraciones"))
        assertTrue(answers.getValue("diapason-diferencia")!!.contains("monofilamento"))
        assertTrue(answers.getValue("diapason-exploracion")!!.contains("dedo gordo"))
        assertTrue(answers.getValue("pulsos-que")!!.contains("arteria"))
        assertTrue(answers.getValue("pulsos-revision")!!.contains("dedos"))
        assertTrue(answers.getValue("pulsos-limitacion")!!.contains("no confirma"))
        assertTrue(answers.getValue("fondo-gotas")!!.contains("gotas"))
        assertTrue(answers.getValue("fondo-efectos")!!.contains("borrosa"))
        assertTrue(answers.getValue("fondo-diferencia")!!.contains("retinografía"))
        assertTrue(answers.getValue("fondo-observar")!!.contains("retina"))
        assertTrue(answers.getValue("cataratas-que")!!.contains("cristalino"))
        assertTrue(answers.getValue("cataratas-despierto")!!.contains("despierta"))
        assertTrue(answers.getValue("cataratas-recuperacion")!!.contains("borrosa"))
        assertTrue(answers.getValue("cataratas-alerta")!!.contains("dolor intenso"))
        assertTrue(answers.getValue("vitrectomia-que")!!.contains("vítreo"))
        assertTrue(answers.getValue("vitrectomia-gas")!!.contains("vuelos"))
        assertTrue(answers.getValue("vitrectomia-catarata")!!.contains("cristalino"))
        assertTrue(answers.getValue("vitrectomia-despues")!!.contains("borrosa"))
        selected.forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
        }
    }
}
