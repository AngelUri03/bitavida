package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class BoneInfectionSurgeryDepthTest {
    @Test fun explainsSampleAndPossibleReconstructionWithoutAssumingTreatment() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "cirugia-osteomielitis-pie" }
        val sample = procedureSectionText(guide, "bone-sample")!!
        val followup = procedureSectionText(guide, "repair-followup")!!
        assertTrue(sample.contains("cultivo puede ayudar"))
        assertTrue(sample.contains("exploración y los demás estudios"))
        assertTrue(followup.contains("puede quedar un espacio"))
        assertTrue(followup.contains("no todas las personas", ignoreCase = true))
        listOf("bone-sample", "repair-followup").forEach { id ->
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
