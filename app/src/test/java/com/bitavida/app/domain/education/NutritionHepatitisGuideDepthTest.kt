package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class NutritionHepatitisGuideDepthTest {
    @Test fun explanationsAddressEverydayQuestionsWithoutPrescribingCare() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val nutrition = guides.single { it.id == "consulta-nutricion" }
        val vaccine = guides.single { it.id == "vacuna-hepatitis-b" }

        assertTrue(procedureSectionText(nutrition, "routine")!!.contains("masticar"))
        assertTrue(procedureSectionText(nutrition, "agreement")!!.contains("no decide porciones"))
        assertTrue(procedureSectionText(vaccine, "infection")!!.contains("no trata una infección ya existente"))
        assertTrue(procedureSectionText(vaccine, "contact")!!.contains("compartir cubiertos"))
        assertTrue(procedureSectionText(vaccine, "record")!!.contains("no demuestran disponibilidad"))
        assertEquals("2.1-consulta-nutricion", nutrition.contentVersion)
        assertEquals("2.1-vacuna-hepatitis-b", vaccine.contentVersion)
        listOf(nutrition, vaccine).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            guide.extraSections.forEach { section -> assertTrue(section.sourceReferences.isNotEmpty()) }
            guide.relatedQuestions.forEach { question ->
                assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
            }
        }
    }
}
