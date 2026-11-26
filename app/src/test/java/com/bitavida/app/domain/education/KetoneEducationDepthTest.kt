package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class KetoneEducationDepthTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun ketoneGuidesExplainSamplesLimitsAndUrgentSymptoms() {
        val blood = guides.single { it.id == "cetonas-sangre" }
        val urine = guides.single { it.id == "cetonas-orina" }
        assertTrue(blood.extraSections.any { it.id == "meaning" && it.body.contains("no siempre descarta") })
        assertTrue(blood.extraSections.any { it.id == "care" && it.body.contains("tira específica") })
        assertTrue(urine.extraSections.any { it.id == "collection" && it.body.contains("recipiente") })
        assertTrue(urine.extraSections.any { it.id == "limits" && it.body.contains("atención urgente") })
        assertTrue(urine.preparation.contains("Confirma"))
        for (guide in listOf(blood, urine)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
