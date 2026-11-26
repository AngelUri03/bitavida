package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class OcularRenalRelatedQuestionTest {
    @Test fun questionsResolveToSectionsAnsweringTheirOwnTopic() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val selected = listOf("inyeccion-ojo", "laser-retina", "biopsia-renal", "hemodialisis", "dialisis-peritoneal")
            .map { id -> guides.single { it.id == id } }
        val answers = selected.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(20, answers.size)
        assertTrue(answers.getValue("inyeccion-ojo-que")!!.contains("medicamento dentro del ojo"))
        assertTrue(answers.getValue("inyeccion-ojo-preparacion")!!.contains("anestésico"))
        assertTrue(answers.getValue("inyeccion-ojo-molestias")!!.contains("irritación"))
        assertTrue(answers.getValue("inyeccion-ojo-alerta")!!.contains("de inmediato"))
        assertTrue(answers.getValue("laser-retina-que")!!.contains("vasos anormales"))
        assertTrue(answers.getValue("laser-retina-sentir")!!.contains("destellos"))
        assertTrue(answers.getValue("laser-retina-diferencia")!!.contains("inyección"))
        assertTrue(answers.getValue("laser-retina-riesgos")!!.contains("visión lateral"))
        assertTrue(answers.getValue("biopsia-renal-que")!!.contains("tejido del riñón"))
        assertTrue(answers.getValue("biopsia-renal-como")!!.contains("aguja"))
        assertTrue(answers.getValue("biopsia-renal-riesgo")!!.contains("alergias"))
        assertTrue(answers.getValue("biopsia-renal-despues")!!.contains("pulso"))
        assertTrue(answers.getValue("biopsia-renal-despues")!!.contains("sangrado"))
        assertTrue(answers.getValue("hemodialisis-que")!!.contains("desechos"))
        assertTrue(answers.getValue("hemodialisis-sesion")!!.contains("filtro"))
        assertTrue(answers.getValue("hemodialisis-limites")!!.contains("no cura"))
        assertTrue(answers.getValue("hemodialisis-acceso")!!.contains("fístula"))
        assertTrue(answers.getValue("hemodialisis-acceso")!!.contains("catéter"))
        assertTrue(answers.getValue("peritoneal-que")!!.contains("peritoneo"))
        assertTrue(answers.getValue("peritoneal-diferencia")!!.contains("hemodiálisis"))
        assertTrue(answers.getValue("peritoneal-sesion")!!.contains("solución"))
        assertTrue(answers.getValue("peritoneal-sesion")!!.contains("se reemplaza"))
        assertTrue(answers.getValue("peritoneal-alerta")!!.contains("infección"))
        selected.forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
        }
    }
}
