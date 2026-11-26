package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class VaccineGuideFollowupTest {
    @Test fun remainingVaccinesHaveSpecificExplanationsAndAnswerableRelatedQuestionsOffline() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val zoster = guides.single { it.id == "vacuna-zoster" }
        val tetanus = guides.single { it.id == "vacuna-tetanos" }
        val covid = guides.single { it.id == "vacuna-covid" }

        assertTrue(zoster.description.contains("varicela") && zoster.purpose.contains("erupción"))
        assertTrue(tetanus.description.contains("toxina") && tetanus.description.contains("herida"))
        assertTrue(tetanus.method.contains("difteria") && tetanus.method.contains("tos ferina"))
        assertTrue(covid.description.contains("SARS-CoV-2") && covid.important.contains("dolor de pecho", ignoreCase = true))
        assertTrue(covid.method.contains("no decide") && covid.method.contains("calendario"))
        val answers = listOf(zoster, tetanus, covid).flatMap { guide ->
            guide.relatedQuestions.map { it.id to procedureSectionText(guide, it.sectionId) }
        }.toMap()
        assertTrue(answers.getValue("zoster-varicela")!!.contains("varicela"))
        assertTrue(answers.getValue("zoster-molestias")!!.contains("dolor"))
        assertTrue(answers.getValue("zoster-citas")!!.contains("no calcula"))
        assertTrue(answers.getValue("tetanos-causa")!!.contains("toxina"))
        assertTrue(answers.getValue("tetanos-combinadas")!!.contains("difteria"))
        assertTrue(answers.getValue("tetanos-herida")!!.contains("no sustituye"))
        assertTrue(answers.getValue("covid-proposito")!!.contains("enfermedad grave"))
        assertTrue(answers.getValue("covid-funcionamiento")!!.contains("sistema inmunitario"))
        assertTrue(answers.getValue("covid-molestias")!!.contains("dolor"))
        listOf(zoster, tetanus, covid).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.size >= 3)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("important").all { it in references })
        }
        assertTrue(searchProcedureGuides("vacuna culebrilla", guides).any { it.id == zoster.id })
        assertTrue(searchProcedureGuides("vacuna tetanos", guides).any { it.id == tetanus.id })
        assertTrue(searchProcedureGuides("vacuna covid", guides).any { it.id == covid.id })
    }
}
