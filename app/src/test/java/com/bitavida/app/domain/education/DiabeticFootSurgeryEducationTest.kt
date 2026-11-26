package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class DiabeticFootSurgeryEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun abscessAndBoneInfectionHaveDistinctExplanationsAndTraceableSources() {
        val abscess = guides.single { it.id == "drenaje-absceso-pie" }
        val bone = guides.single { it.id == "cirugia-osteomielitis-pie" }
        assertTrue(abscess.description.contains("pus"))
        assertTrue(abscess.during.contains("anestesia local"))
        assertTrue(abscess.important.contains("urgente"))
        assertTrue(bone.description.contains("infección del hueso"))
        assertTrue(bone.important.contains("sin cirugía"))
        assertTrue(bone.during.contains("muestras"))
        for (guide in listOf(abscess, bone)) {
            assertTrue(guide.questions.size in 3..5)
            assertTrue(guide.extraSections.isNotEmpty())
            val references = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in references })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in references })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
