package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ConsultAndGlucoseRelatedQuestionTest {
    @Test fun relatedQuestionsOpenSectionsThatAddressEachQuestion() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val selected = listOf("consulta-familiar", "consulta-nutricion", "educacion-autocuidado", "glucosa-ayunas", "glucosa-aleatoria")
            .map { id -> guides.single { it.id == id } }
        val answers = selected.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(17, answers.size)
        assertTrue(answers.getValue("familiar-que")!!.contains("dudas"))
        assertTrue(answers.getValue("familiar-llevar")!!.contains("medicamentos"))
        assertTrue(answers.getValue("familiar-acompanar")!!.contains("privacidad"))
        assertTrue(answers.getValue("nutricion-temas")!!.contains("alimentos"))
        assertTrue(answers.getValue("nutricion-rutina")!!.contains("presupuesto"))
        assertTrue(answers.getValue("nutricion-preparacion")!!.contains("no cambien alimentos"))
        assertTrue(answers.getValue("autocuidado-que")!!.contains("comprender la diabetes"))
        assertTrue(answers.getValue("autocuidado-temas")!!.contains("bienestar emocional"))
        assertTrue(answers.getValue("autocuidado-formato")!!.contains("grupo"))
        assertTrue(answers.getValue("ayunas-toma")!!.contains("vena del brazo"))
        assertTrue(answers.getValue("ayunas-preparacion")!!.contains("unidad"))
        assertTrue(answers.getValue("ayunas-comparacion")!!.contains("HbA1c"))
        assertTrue(answers.getValue("ayunas-error")!!.contains("avisa al personal"))
        assertTrue(answers.getValue("azar-que")!!.contains("momento"))
        assertTrue(answers.getValue("azar-comida")!!.contains("después de comer"))
        assertTrue(answers.getValue("azar-comparacion")!!.contains("aproximadamente tres meses"))
        assertTrue(answers.getValue("azar-momento")!!.contains("hora", ignoreCase = true) ||
            answers.getValue("azar-momento")!!.contains("cuándo"))
        selected.forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
        }
    }
}
