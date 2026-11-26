package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class FootUlcerCareDepthTest {
    @Test fun explainsDressingSensationAndInfectionWithoutAssumingDiagnosis() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "curacion-ulcera-pie" }
        assertTrue(procedureSectionText(guide, "dressing-purpose")!!.contains("absorber sangre"))
        assertTrue(procedureSectionText(guide, "sensation-circulation")!!.contains("no sentir dolor no demuestra"))
        assertTrue(procedureSectionText(guide, "infection")!!.contains("sin que por ello haya una infección clínica"))
        assertTrue(procedureSectionText(guide, "infection")!!.contains("No inicies ni suspendas"))
        listOf("dressing-purpose", "sensation-circulation", "infection").forEach { id ->
            assertTrue(guide.extraSections.single { it.id == id }.sourceReferences.isNotEmpty())
        }
        assertEquals(3, guide.relatedQuestions.size)
        guide.relatedQuestions.forEach { question ->
            assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
        }
        assertEquals("22 de noviembre de 2026", guide.reviewedOn)
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
    }
}
