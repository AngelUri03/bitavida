package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ProcedureCareRelatedQuestionTest {
    @Test fun relatedQuestionsResolveToSectionsThatAnswerTheQuestion() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val selected = listOf("consulta-diabetes", "hba1c", "monofilamento", "electrocardiograma")
            .map { id -> guides.single { it.id == id } }
        val answers = selected.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(15, answers.size)
        assertTrue(answers.getValue("control-diabetes-conversacion")!!.contains("medicamentos"))
        assertTrue(answers.getValue("control-diabetes-llevar")!!.contains("resultados disponibles"))
        assertTrue(answers.getValue("control-diabetes-acuerdos")!!.contains("acuerdos"))
        assertTrue(answers.getValue("hba1c-que-mide")!!.contains("hemoglobina tiene glucosa"))
        assertTrue(answers.getValue("hba1c-diferencia")!!.contains("momento de la muestra"))
        assertTrue(answers.getValue("hba1c-ayuno")!!.contains("generalmente no requiere ayuno"))
        assertTrue(answers.getValue("hba1c-limitaciones")!!.contains("anemias"))
        assertTrue(answers.getValue("mono-sensibilidad")!!.contains("roces"))
        assertTrue(answers.getValue("mono-experiencia")!!.contains("toca distintas zonas"))
        assertTrue(answers.getValue("mono-dolor")!!.contains("normalmente no es doloroso"))
        assertTrue(answers.getValue("mono-limitacion")!!.contains("no identifica por sí solo"))
        assertTrue(answers.getValue("ecg-que-mide")!!.contains("actividad eléctrica"))
        assertTrue(answers.getValue("ecg-experiencia")!!.contains("pecho, brazos y piernas"))
        assertTrue(answers.getValue("ecg-descarga")!!.contains("no envía descargas"))
        assertTrue(answers.getValue("ecg-limitacion")!!.contains("otro momento"))
        selected.forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
        }
    }
}
