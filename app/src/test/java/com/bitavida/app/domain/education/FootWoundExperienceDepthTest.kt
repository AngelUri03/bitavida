package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class FootWoundExperienceDepthTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun woundGuidesExplainAssessmentComfortPressureAndDevices() {
        val wound = guides.single { it.id == "desbridamiento-herida" }
        val offloading = guides.single { it.id == "descarga-presion-pie" }

        assertTrue(procedureSectionText(wound, "assessment")!!.contains("circulación"))
        assertTrue(procedureSectionText(wound, "assessment")!!.contains("apósito"))
        assertTrue(procedureSectionText(wound, "comfort")!!.contains("falta de dolor"))
        assertTrue(procedureSectionText(offloading, "pressure")!!.contains("sensibilidad"))
        assertTrue(procedureSectionText(offloading, "pressure")!!.contains("apósito"))
        assertTrue(procedureSectionText(offloading, "devices")!!.contains("desplazarse"))

        listOf(wound, offloading).forEach { guide ->
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(3, guide.relatedQuestions.size)
            guide.relatedQuestions.forEach { question ->
                assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
            }
            guide.extraSections.forEach { section ->
                assertTrue(section.sourceReferences.isNotEmpty())
            }
        }
    }
}
