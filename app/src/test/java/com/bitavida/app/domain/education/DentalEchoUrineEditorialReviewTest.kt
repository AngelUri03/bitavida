package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class DentalEchoUrineEditorialReviewTest {
    @Test fun reviewedGuidesRetainDistinctExplanationsAndReferences() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val dental = guides.single { it.id == "consulta-odontologica" }
        val echo = guides.single { it.id == "ecocardiograma" }
        val culture = guides.single { it.id == "urocultivo" }

        assertTrue(dental.during.contains("espejo") && dental.method.contains("no es lo mismo"))
        assertTrue(dental.important.contains("distintas causas"))
        assertTrue(echo.during.contains("transductor") && echo.method.contains("ondas sonoras"))
        assertTrue(procedureSectionText(echo, "types")!!.contains("esófago"))
        assertTrue(culture.method.contains("crecimiento de bacterias"))
        assertTrue(procedureSectionText(culture, "comparison")!!.contains("examen general"))
        listOf(dental, echo, culture).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in references })
            assertTrue(guide.relatedQuestions.all { !procedureSectionText(guide, it.sectionId).isNullOrBlank() })
        }
    }
}
