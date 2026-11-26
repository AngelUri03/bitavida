package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class RenalVascularConsultEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun consultationsExplainTestsAndAvoidAssumingProcedures() {
        val renal = guides.single { it.id == "consulta-nefrologia" }
        val vascular = guides.single { it.id == "consulta-cirugia-vascular" }
        assertTrue(renal.purpose.contains("no significa"))
        assertTrue(renal.during.contains("orina"))
        assertTrue(vascular.description.contains("no implica"))
        assertTrue(vascular.during.contains("ultrasonido"))
        for (guide in listOf(renal, vascular)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
