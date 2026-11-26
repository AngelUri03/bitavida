package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class MonitoringRelatedQuestionTest {
    @Test fun questionsResolveToRelevantExplanations() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val selected = listOf("tolerancia-glucosa", "creatinina-egfr", "albumina-creatinina", "pies-integral", "consulta-interna")
            .map { id -> guides.single { it.id == id } }
        val answers = selected.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(17, answers.size)
        assertTrue(answers.getValue("tolerancia-como")!!.contains("bebida"))
        assertTrue(answers.getValue("tolerancia-espera")!!.contains("avisar al personal"))
        assertTrue(answers.getValue("tolerancia-comparacion")!!.contains("HbA1c"))
        assertTrue(answers.getValue("egfr-que")!!.contains("cálculo"))
        assertTrue(answers.getValue("egfr-muestra")!!.contains("no necesita otra extracción"))
        assertTrue(answers.getValue("egfr-precision")!!.contains("masa muscular"))
        assertTrue(answers.getValue("egfr-albumina")!!.contains("orina"))
        assertTrue(answers.getValue("uacr-que")!!.contains("albúmina y creatinina"))
        assertTrue(answers.getValue("uacr-muestra")!!.contains("orina"))
        assertTrue(answers.getValue("uacr-relacion")!!.contains("concentrada o diluida"))
        assertTrue(answers.getValue("uacr-diferencia")!!.contains("filtración estimada"))
        assertTrue(answers.getValue("pies-que")!!.contains("plantas, dedos, uñas"))
        assertTrue(answers.getValue("pies-sensibilidad")!!.contains("monofilamento"))
        assertTrue(answers.getValue("pies-cambios")!!.contains("herida"))
        assertTrue(answers.getValue("interna-que")!!.contains("adultas"))
        assertTrue(answers.getValue("interna-revision")!!.contains("medicamentos y suplementos"))
        assertTrue(answers.getValue("interna-llevar")!!.contains("resultados disponibles"))
        selected.forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
        }
    }
}
