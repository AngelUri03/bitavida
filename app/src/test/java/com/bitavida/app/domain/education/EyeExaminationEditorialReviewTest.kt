package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class EyeExaminationEditorialReviewTest {
    @Test fun reviewedEyeGuidesRetainRelevantExplanationsAndPendingClinicalReview() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val acuity = guides.single { it.id == "agudeza-visual" }
        val pressure = guides.single { it.id == "presion-ocular" }
        val vitrectomy = guides.single { it.id == "vitrectomia" }

        assertTrue(acuity.during.contains("otro ojo"))
        assertTrue(procedureSectionText(acuity, "limitations")!!.contains("retina"))
        assertTrue(procedureSectionText(pressure, "methods")!!.contains("soplo"))
        assertTrue(pressure.important.contains("no confirma ni descarta"))
        assertTrue(vitrectomy.during.contains("anestesia general"))
        assertTrue(procedureSectionText(vitrectomy, "bubble")!!.contains("gran altitud"))
        listOf(acuity, pressure, vitrectomy).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            val sources = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertTrue(guide.relatedQuestions.all { !procedureSectionText(guide, it.sectionId).isNullOrBlank() })
        }
    }
}
