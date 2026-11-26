package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class VaccineGuideEditorialTest {
    @Test fun relatedQuestionsOpenTheSpecificSectionThatAnswersThem() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val ids = setOf("vacuna-influenza", "vacuna-neumococo", "vacuna-hepatitis-b")
        val answers = guides.filter { it.id in ids }.flatMap { guide ->
            guide.relatedQuestions.map { question -> question.id to procedureSectionText(guide, question.sectionId) }
        }.toMap()

        assertEquals(9, answers.size)
        assertTrue(answers.getValue("influenza-causa")!!.contains("no pueden causar influenza"))
        assertTrue(answers.getValue("influenza-aplicacion")!!.contains("pinchazo"))
        assertTrue(answers.getValue("influenza-urgencia")!!.contains("atención urgente"))
        assertTrue(answers.getValue("neumococo-neumonia")!!.contains("no protegen contra todas"))
        assertTrue(answers.getValue("neumococo-infecciones")!!.contains("infección de los pulmones"))
        assertTrue(answers.getValue("neumococo-molestias")!!.contains("enrojecimiento"))
        assertTrue(answers.getValue("hepatitis-prevencion")!!.contains("no es un tratamiento"))
        assertTrue(answers.getValue("hepatitis-transmision")!!.contains("sangre"))
        assertTrue(answers.getValue("hepatitis-calendario")!!.contains("no calcula fechas"))
    }

    @Test fun threeVaccinesExplainDifferentDiseasesWithoutCreatingPersonalSchedules() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val flu = guides.single { it.id == "vacuna-influenza" }
        val pneumococcus = guides.single { it.id == "vacuna-neumococo" }
        val hepatitis = guides.single { it.id == "vacuna-hepatitis-b" }

        assertTrue(flu.description.contains("infección respiratoria"))
        assertTrue(flu.method.contains("no pueden causar influenza"))
        assertTrue(pneumococcus.description.contains("bacteria") && pneumococcus.description.contains("no protegen"))
        assertTrue(pneumococcus.purpose.contains("membranas"))
        assertTrue(hepatitis.description.contains("hígado") && hepatitis.description.contains("no es un tratamiento"))
        assertTrue(hepatitis.method.contains("no calcula fechas"))
        assertTrue(flu.additionalSources.any { it.reference.contains("imss.gob.mx") })
        assertTrue(pneumococcus.additionalSources.any { it.reference.contains("imss.gob.mx") })
        val entries = listOf(flu, pneumococcus, hepatitis)
        assertEquals(entries.size, entries.map { it.method }.distinct().size)
        assertEquals(entries.size, entries.map { it.questions }.distinct().size)
        entries.forEach { guide ->
            assertTrue(ProcedureCategory.VACCINES in guide.categories)
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("important").all { it in references })
        }
        assertTrue(searchProcedureGuides("vacuna influenza", guides).any { it.id == flu.id })
        assertTrue(searchProcedureGuides("vacuna neumococo", guides).any { it.id == pneumococcus.id })
        assertTrue(searchProcedureGuides("vacuna hepatitis b", guides).any { it.id == hepatitis.id })
    }
}
