package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class AmputationEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun amputationGuidesExplainDifferentLevelsRehabilitationAndSources() {
        val minor = guides.single { it.id == "amputacion-menor-pie" }
        val major = guides.single { it.id == "amputacion-mayor-pierna" }
        assertTrue(minor.description.contains("dedo"))
        assertTrue(minor.after.contains("zapato"))
        assertTrue(major.description.contains("rodilla"))
        assertTrue(major.extraSections.any { it.id == "phantom" && it.body.contains("No significa") })
        for (guide in listOf(minor, major)) {
            assertTrue(guide.during.contains("anestesia"))
            assertTrue(guide.important.contains("dolor"))
            assertTrue(guide.questions.size in 3..5)
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
