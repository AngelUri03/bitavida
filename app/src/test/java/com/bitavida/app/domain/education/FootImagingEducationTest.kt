package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class FootImagingEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun footImagingExplainsRadiationAndMagneticSafety() {
        val xray = guides.single { it.id == "radiografia-pie" }
        val mri = guides.single { it.id == "resonancia-pie" }
        assertTrue(xray.description.contains("radiación"))
        assertTrue(xray.during.contains("quieto"))
        assertTrue(xray.important.contains("embarazo"))
        assertTrue(mri.description.contains("ondas de radio"))
        assertTrue(mri.important.contains("implantes"))
        assertTrue(mri.during.contains("audífonos"))
        for (guide in listOf(xray, mri)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
