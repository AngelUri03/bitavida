package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ConsultAndCardiacRelatedQuestionTest {
    @Test fun questionsOpenSectionsWithRelevantExplanations() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val selected = listOf("consulta-oftalmologia", "consulta-odontologica", "ecocardiograma", "prueba-esfuerzo", "angiografia-coronaria")
            .map { id -> guides.single { it.id == id } }
        val answers = selected.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(17, answers.size)
        assertTrue(answers.getValue("oftalmo-que")!!.contains("visión borrosa"))
        assertTrue(answers.getValue("oftalmo-gotas")!!.contains("no siempre", ignoreCase = true) ||
            answers.getValue("oftalmo-gotas")!!.contains("no todas", ignoreCase = true))
        assertTrue(answers.getValue("oftalmo-despues")!!.contains("borrosa"))
        assertTrue(answers.getValue("dental-revision")!!.contains("espejo"))
        assertTrue(answers.getValue("dental-molestias")!!.contains("dentadura"))
        assertTrue(answers.getValue("dental-tratamiento")!!.contains("no es lo mismo"))
        assertTrue(answers.getValue("eco-que")!!.contains("válvulas"))
        assertTrue(answers.getValue("eco-aparato")!!.contains("transductor"))
        assertTrue(answers.getValue("eco-diferencia")!!.contains("electrocardiograma"))
        assertTrue(answers.getValue("eco-tipos")!!.contains("esófago"))
        assertTrue(answers.getValue("esfuerzo-como")!!.contains("bicicleta"))
        assertTrue(answers.getValue("esfuerzo-vigilancia")!!.contains("presión"))
        assertTrue(answers.getValue("esfuerzo-molestias")!!.contains("de inmediato"))
        assertTrue(answers.getValue("angiografia-coronaria-que")!!.contains("coronarias"))
        assertTrue(answers.getValue("angiografia-coronaria-diferencia")!!.contains("angioplastia"))
        assertTrue(answers.getValue("angiografia-coronaria-preparacion")!!.contains("alergias"))
        assertTrue(answers.getValue("angiografia-coronaria-recuperacion")!!.contains("presión"))
        selected.forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
        }
    }
}
