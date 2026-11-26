package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ArterialDopplerDepthTest {
    @Test fun distinguishesArteriesFromVeinsAndExplainsLimitsOfImages() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "doppler-arterial-pierna" }
        val vessels = procedureSectionText(guide, "artery-vein")!!
        val information = procedureSectionText(guide, "flow-information")!!
        assertTrue(vessels.contains("desde el corazón"))
        assertTrue(vessels.contains("devolverla hacia el corazón"))
        assertTrue(vessels.contains("no son automáticamente intercambiables"))
        assertTrue(information.contains("velocidad"))
        assertTrue(information.contains("no permite al cuidador decidir"))
        listOf("artery-vein", "flow-information").forEach { id ->
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
