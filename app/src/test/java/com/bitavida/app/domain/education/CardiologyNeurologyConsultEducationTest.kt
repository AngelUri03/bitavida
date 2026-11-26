package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class CardiologyNeurologyConsultEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun consultationsExplainExamAndUrgentChangesWithoutDiagnosis() {
        val cardio = guides.single { it.id == "consulta-cardiologia" }
        val neuro = guides.single { it.id == "consulta-neurologia" }
        assertTrue(cardio.during.contains("electrocardiograma"))
        assertTrue(cardio.important.contains("atención urgente"))
        assertTrue(neuro.during.contains("reflejos"))
        assertTrue(neuro.important.contains("emergencia"))
        assertTrue(neuro.extraSections.any { it.id == "examination" && it.body.contains("reflejo") })
        for (guide in listOf(cardio, neuro)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
