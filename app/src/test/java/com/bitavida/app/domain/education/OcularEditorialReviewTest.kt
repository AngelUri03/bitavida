package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class OcularEditorialReviewTest {
    @Test fun reviewedOcularGuidesRetainSpecificContentSourcesAndClinicalReviewStatus() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val injection = guides.single { it.id == "inyeccion-ojo" }
        val laser = guides.single { it.id == "laser-retina" }
        val cataract = guides.single { it.id == "cataratas" }

        assertTrue(injection.during.contains("anestésico") && injection.important.contains("de inmediato"))
        assertTrue(procedureSectionText(injection, "medicine")!!.contains("anti-VEGF"))
        assertTrue(laser.during.contains("destellos") && laser.important.contains("visión lateral"))
        assertTrue(procedureSectionText(laser, "comparison")!!.contains("retinografía"))
        assertTrue(cataract.during.contains("despierta") && cataract.important.contains("dolor intenso"))
        assertTrue(procedureSectionText(cataract, "recovery")!!.contains("borrosa"))
        listOf(injection, laser, cataract).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.source.reference.contains("nei.nih.gov"))
            guide.relatedQuestions.forEach { assertFalse(procedureSectionText(guide, it.sectionId).isNullOrBlank()) }
        }
    }
}
