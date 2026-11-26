package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class RenalGuideDepthTest {
    @Test fun guidesExplainTheActualExperienceAndTheirLimits() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val biopsy = guides.single { it.id == "biopsia-renal" }
        val hemo = guides.single { it.id == "hemodialisis" }
        val peritoneal = guides.single { it.id == "dialisis-peritoneal" }

        assertTrue(biopsy.during.contains("ultrasonido") && biopsy.during.contains("anestesia local"))
        assertTrue(biopsy.after.contains("observación") && biopsy.after.contains("sangrado"))
        assertTrue(biopsy.preparation.contains("alergias") && biopsy.preparation.contains("no suspendas"))
        assertTrue(biopsy.important.contains("atención médica inmediata"))
        assertTrue(hemo.method.contains("fibras") && hemo.method.contains("sangre vuelve"))
        assertTrue(hemo.preparation.contains("fístula") && hemo.preparation.contains("capacitación"))
        assertTrue(hemo.important.contains("no cura") && hemo.important.contains("no todas", ignoreCase = true))
        assertTrue(peritoneal.purpose.contains("hemodiálisis") && peritoneal.during.contains("llenura"))
        assertTrue(peritoneal.method.contains("manualmente") && peritoneal.method.contains("automatizada"))
        assertTrue(peritoneal.preparation.contains("catéter") && peritoneal.preparation.contains("capacitación"))
        assertTrue(peritoneal.important.contains("turbio") && peritoneal.important.contains("atención inmediata"))

        listOf(biopsy, hemo, peritoneal).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.source.reference.contains("niddk.nih.gov"))
            assertTrue(guide.sectionSourceReferences.getValue("during").contains(guide.source.reference))
            guide.relatedQuestions.forEach { question ->
                assertFalse(procedureSectionText(guide, question.sectionId).isNullOrBlank())
            }
        }
    }
}
