package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class LabEditorialReviewTest {
    @Test fun reviewedLabsRetainSpecificExplanationsAndSources() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val random = guides.single { it.id == "glucosa-aleatoria" }
        val tolerance = guides.single { it.id == "tolerancia-glucosa" }
        val albumin = guides.single { it.id == "albumina-creatinina" }

        assertTrue(random.preparation.contains("después de comer"))
        assertTrue(procedureSectionText(random, "comparison")!!.contains("aproximadamente tres meses"))
        assertTrue(tolerance.during.contains("bebida") && tolerance.during.contains("primera muestra"))
        assertTrue(procedureSectionText(tolerance, "waiting")!!.contains("avisar al personal"))
        assertTrue(albumin.method.contains("concentrada o diluida"))
        assertTrue(procedureSectionText(albumin, "comparison")!!.contains("filtración estimada"))
        listOf(random, tolerance, albumin).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            val sources = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertTrue(guide.relatedQuestions.all { !procedureSectionText(guide, it.sectionId).isNullOrBlank() })
        }
    }
}
