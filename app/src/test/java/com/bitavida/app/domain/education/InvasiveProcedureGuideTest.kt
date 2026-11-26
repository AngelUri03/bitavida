package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class InvasiveProcedureGuideTest {
    @Test fun coronaryStudiesAndDialysisAccessHaveDistinctDescriptionsAndSourcesOffline() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val angiography = guides.single { it.id == "angiografia-coronaria" }
        val angioplasty = guides.single { it.id == "angioplastia-coronaria" }
        val fistula = guides.single { it.id == "fistula-av" }

        assertTrue(angiography.description.contains("rayos X") && angiography.during.contains("contraste"))
        assertTrue(angiography.method.contains("no") && angiography.important.contains("riesgos"))
        assertTrue(angioplasty.during.contains("balón") && angioplasty.description.contains("malla"))
        assertTrue(angioplasty.important.contains("sangrado") && angioplasty.important.contains("no suspendas", ignoreCase = true))
        assertTrue(fistula.description.contains("arteria y una vena"))
        assertTrue(fistula.method.contains("no significa") && fistula.after.contains("tiempo"))
        assertTrue(fistula.important.contains("No toda persona con diabetes"))
        assertTrue(ProcedureCategory.SURGERY in angioplasty.categories)
        assertTrue(ProcedureCategory.SURGERY in fistula.categories)
        listOf(angiography, angioplasty, fistula).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("during").all { it in references })
        }
        assertTrue(searchProcedureGuides("stent del corazon", guides).any { it.id == angioplasty.id })
        assertTrue(searchProcedureGuides("fistula para dialisis", guides).any { it.id == fistula.id })
    }
}
