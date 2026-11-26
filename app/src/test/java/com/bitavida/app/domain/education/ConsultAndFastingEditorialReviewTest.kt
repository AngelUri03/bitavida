package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ConsultAndFastingEditorialReviewTest {
    @Test fun reviewedGuidesExposeTheDateSourcesAndClinicalReviewStatus() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val family = guides.single { it.id == "consulta-familiar" }
        val education = guides.single { it.id == "educacion-autocuidado" }
        val fasting = guides.single { it.id == "glucosa-ayunas" }

        assertTrue(family.important.contains("privacidad") && family.preparation.contains("medicamentos"))
        assertTrue(education.during.contains("bienestar emocional") && education.important.contains("no diagnostica"))
        assertTrue(fasting.preparation.contains("no suspendas") && fasting.important.contains("avisa al personal"))
        assertTrue(procedureSectionText(fasting, "comparison")!!.contains("HbA1c"))
        assertTrue(family.source.reference.contains("niddk.nih.gov"))
        assertTrue(education.source.reference.contains("cdc.gov"))
        assertTrue(fasting.source.reference.contains("medlineplus.gov"))
        listOf(family, education, fasting).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.relatedQuestions.all { procedureSectionText(guide, it.sectionId) != null })
        }
    }
}
