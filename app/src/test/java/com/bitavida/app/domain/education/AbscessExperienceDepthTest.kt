package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class AbscessExperienceDepthTest {
    @Test fun guideExplainsDrainageExperienceAndFollowupWithSources() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "drenaje-absceso-pie" }
        assertTrue(procedureSectionText(guide, "why-drain")!!.contains("profunda"))
        assertTrue(procedureSectionText(guide, "why-drain")!!.contains("antibióticos", ignoreCase = true))
        assertTrue(procedureSectionText(guide, "experience")!!.contains("anestesia local"))
        assertTrue(procedureSectionText(guide, "wound-followup")!!.contains("abierta"))
        listOf("why-drain", "experience", "wound-followup").forEach { id ->
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
