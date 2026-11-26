package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class MinorAmputationExperienceDepthTest {
    @Test fun explainsFollowupSupportAndPhantomSensation() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "amputacion-menor-pie" }
        val support = procedureSectionText(guide, "wound-and-support")!!
        val sensation = procedureSectionText(guide, "phantom-sensation")!!
        assertTrue(support.contains("quién hará las curaciones"))
        assertTrue(support.contains("indicaciones de apoyo corresponden a esa persona"))
        assertTrue(sensation.contains("no siempre duele"))
        assertTrue(sensation.contains("el dolor es real"))
        listOf("wound-and-support", "phantom-sensation").forEach { id ->
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
