package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class AngiographyPreoperativeEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun angiographyAndPreoperativeVisitDescribeActualExperienceAndLimits() {
        val eye = guides.single { it.id == "angiografia-retina" }
        val preop = guides.single { it.id == "valoracion-preoperatoria" }
        assertTrue(eye.description.contains("vena"))
        assertTrue(eye.during.contains("cámara"))
        assertTrue(eye.important.contains("alérgica"))
        assertTrue(eye.extraSections.any { it.id == "comparison" && it.body.contains("OCT") })
        assertTrue(preop.description.contains("no garantiza"))
        assertTrue(preop.during.contains("medicamentos"))
        assertTrue(preop.important.contains("No suspendas"))
        for (guide in listOf(eye, preop)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
