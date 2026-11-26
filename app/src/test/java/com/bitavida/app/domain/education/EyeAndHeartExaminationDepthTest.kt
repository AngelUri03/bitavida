package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class EyeAndHeartExaminationDepthTest {
    @Test fun guidesExplainTheExperienceAndLimitsOfTheExaminations() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val ecg = guides.single { it.id == "electrocardiograma" }
        val eye = guides.single { it.id == "consulta-oftalmologia" }

        assertTrue(procedureSectionText(ecg, "signals")!!.contains("señales eléctricas"))
        assertTrue(procedureSectionText(ecg, "signals")!!.contains("no interpreta"))
        assertTrue(procedureSectionText(ecg, "monitor")!!.contains("más tiempo"))
        assertTrue(procedureSectionText(eye, "exam-parts")!!.contains("visión lateral"))
        assertTrue(procedureSectionText(eye, "dilation")!!.contains("retina"))
        assertTrue(procedureSectionText(eye, "dilation")!!.contains("No toda consulta incluye dilatación"))
        assertEquals("2.1-electrocardiograma", ecg.contentVersion)
        assertEquals("2.1-consulta-oftalmologia", eye.contentVersion)
        listOf(ecg, eye).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            guide.relatedQuestions.forEach { assertFalse(procedureSectionText(guide, it.sectionId).isNullOrBlank()) }
        }
    }
}
