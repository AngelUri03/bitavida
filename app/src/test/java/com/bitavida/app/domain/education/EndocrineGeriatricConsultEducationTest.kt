package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class EndocrineGeriatricConsultEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun consultationsExplainHormonesAndIndividualOlderAdultCare() {
        val endocrine = guides.single { it.id == "consulta-endocrinologia" }
        val geriatric = guides.single { it.id == "consulta-geriatria" }
        assertTrue(endocrine.description.contains("insulina"))
        assertTrue(endocrine.purpose.contains("no es una consulta obligatoria"))
        assertTrue(geriatric.during.contains("caídas"))
        assertTrue(geriatric.important.contains("no define la autonomía"))
        for (guide in listOf(endocrine, geriatric)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
