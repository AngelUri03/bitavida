package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class CardiacProceduresEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun holterAndCoronaryBypassExplainDifferentExperiencesAndSources() {
        val holter = guides.single { it.id == "holter" }
        val bypass = guides.single { it.id == "bypass-coronario" }
        assertTrue(holter.during.contains("electrodos"))
        assertTrue(holter.method.contains("diario"))
        assertTrue(holter.important.contains("emergencias"))
        assertTrue(bypass.description.contains("arterias coronarias"))
        assertTrue(bypass.during.contains("anestesia general"))
        assertTrue(bypass.after.contains("cuidados intensivos"))
        for (guide in listOf(holter, bypass)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
