package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class LipidLiverEducationDepthTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun panelsExplainMeaningAndLimitationsOfDifferentValues() {
        val lipids = guides.single { it.id == "perfil-lipidos" }
        val liver = guides.single { it.id == "funcion-hepatica" }
        assertTrue(lipids.extraSections.any { it.id == "report" && it.body.contains("LDL fue calculado") })
        assertTrue(lipids.preparation.contains("si se solicita ayuno"))
        assertTrue(liver.extraSections.any { it.id == "patterns" && it.body.contains("no señala por sí sola") })
        assertTrue(liver.important.contains("no identifica"))
        for (guide in listOf(lipids, liver)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
