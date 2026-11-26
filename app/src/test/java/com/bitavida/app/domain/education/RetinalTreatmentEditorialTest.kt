package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class RetinalTreatmentEditorialTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures().associateBy { it.id }

    @Test fun injectionExplainsMedicationAnesthesiaAndWhenToContactTheTeam() {
        val guide = guides.getValue("inyeccion-ojo")
        assertTrue(guide.description.contains("vítreo"))
        assertTrue(guide.during.contains("anestésico"))
        assertTrue(guide.important.contains("empeoran"))
        assertTrue(guide.important.contains("de inmediato"))
        assertTrue(guide.extraSections.any { it.id == "medicine" && it.body.contains("anti-VEGF") })
    }

    @Test fun retinalLaserExplainsExperienceRisksAndDistinguishesImaging() {
        val guide = guides.getValue("laser-retina")
        assertTrue(guide.during.contains("destellos"))
        assertTrue(guide.important.contains("visión lateral"))
        assertTrue(guide.important.contains("nocturna"))
        assertTrue(guide.extraSections.any { it.id == "comparison" && it.body.contains("OCT") })
    }

    @Test fun eachTreatmentHasTraceableSourcesAndRemainsPendingClinicalReview() {
        listOf("inyeccion-ojo", "laser-retina").forEach { id ->
            val guide = guides.getValue(id)
            val urls = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertFalse(guide.contentVersion.isNullOrBlank())
            assertTrue(guide.source.reference.contains("nei.nih.gov"))
            assertTrue(guide.sectionSourceReferences.values.flatten().all(urls::contains))
            assertTrue(guide.extraSections.all { it.sourceReferences.isNotEmpty() && it.sourceReferences.all(urls::contains) })
        }
    }
}
