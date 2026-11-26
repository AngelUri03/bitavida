package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeExaminationEditorialTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures().associateBy { it.id }

    @Test fun acuityExplainsSeparateEyesAndDoesNotClaimToExamineRetina() {
        val guide = guides.getValue("agudeza-visual")
        assertTrue(guide.during.contains("otro ojo"))
        assertTrue(guide.during.contains("con y sin"))
        assertTrue(guide.extraSections.any { it.id == "limitations" && it.body.contains("retina") })
        assertTrue(guide.questions.any { it.contains("lentes") })
    }

    @Test fun tonometryDifferentiatesContactFromAirAndRequiresWholeExamContext() {
        val guide = guides.getValue("presion-ocular")
        assertTrue(guide.during.contains("soplo"))
        assertTrue(guide.during.contains("tocan suavemente"))
        assertTrue(guide.during.contains("adormecen"))
        assertTrue(guide.important.contains("Una cifra aislada"))
        assertTrue(guide.extraSections.any { it.id == "context" && it.body.contains("nervio óptico") })
    }

    @Test fun fundusPhotosAreNotOCTAndDilationIsNotPresentedAsUniversal() {
        val guide = guides.getValue("retinografia")
        assertTrue(guide.preparation.contains("otras pueden obtenerse sin"))
        assertTrue(guide.during.contains("destello"))
        assertTrue(guide.extraSections.any { it.id == "comparison" && it.body.contains("OCT") })
        assertTrue(guide.source.reference.contains("asrs.org"))
    }

    @Test fun expandedGuidesRetainClinicalReviewAndPointToTheirDocumentedSources() {
        listOf("agudeza-visual", "presion-ocular", "retinografia").forEach { id ->
            val guide = guides.getValue(id)
            val sources = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertEquals(id, EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertFalse(id, guide.contentVersion.isNullOrBlank())
            assertTrue(id, guide.questions.size in 3..5)
            assertEquals(id, setOf("what", "purpose", "during", "method", "preparation", "after", "important", "questions"),
                guide.sectionSourceReferences.keys)
            assertTrue(id, guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(id, guide.extraSections.all { it.sourceReferences.isNotEmpty() && it.sourceReferences.all(sources::contains) })
        }
    }
}
