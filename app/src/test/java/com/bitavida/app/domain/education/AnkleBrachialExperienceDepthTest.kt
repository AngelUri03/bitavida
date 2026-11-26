package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class AnkleBrachialExperienceDepthTest {
    @Test fun explainsMeasurementWalkingAndResultLimitations() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "indice-tobillo-brazo" }
        assertTrue(procedureSectionText(guide, "measurement-experience")!!.contains("soplo"))
        assertTrue(procedureSectionText(guide, "measurement-experience")!!.contains("agujas"))
        assertTrue(procedureSectionText(guide, "walking-test")!!.contains("no forma parte obligatoria"))
        assertTrue(procedureSectionText(guide, "result-limits")!!.contains("no se comprimen"))
        assertTrue(procedureSectionText(guide, "result-limits")!!.contains("ni permite decidir una cirugía"))
        listOf("measurement-experience", "walking-test", "result-limits").forEach { id ->
            assertTrue(guide.extraSections.single { it.id == id }.sourceReferences.isNotEmpty())
        }
        assertEquals(3, guide.relatedQuestions.size)
        guide.relatedQuestions.forEach { question ->
            assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
        }
        assertEquals("22 de noviembre de 2026", guide.reviewedOn)
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
    }
}
