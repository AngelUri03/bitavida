package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class BariatricEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun sleeveAndBypassExplainDifferentAnatomyAndExperience() {
        val sleeve = guides.single { it.id == "manga-gastrica" }
        val bypass = guides.single { it.id == "bypass-gastrico" }
        assertTrue(sleeve.description.contains("retira"))
        assertTrue(sleeve.description.contains("recorrido habitual"))
        assertTrue(bypass.description.contains("intestino delgado"))
        assertTrue(bypass.description.contains("coronario"))
        for (guide in listOf(sleeve, bypass)) {
            assertTrue(guide.during.contains("anestesia general"))
            assertTrue(guide.method.contains("laparoscopia"))
            assertTrue(guide.after.contains("nutrici"))
            assertTrue(guide.important.contains("fuga"))
            assertTrue(guide.preparation.contains("no "))
            assertTrue(guide.questions.size in 3..5)
            assertTrue(guide.extraSections.any { it.id == "difference" })
            assertTrue(guide.sectionSourceReferences.values.flatten().all {
                it == guide.source.reference || guide.additionalSources.any { source -> source.reference == it }
            })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
