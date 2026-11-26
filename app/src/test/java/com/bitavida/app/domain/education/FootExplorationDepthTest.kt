package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class FootExplorationDepthTest {
    @Test fun footExplorationsExplainDifferentSensationsAndLimitsOfPulsePalpation() {
        val guides = BundledEducationRepository { error("Reading guides must not load the model") }.procedures()
        val vibration = guides.single { it.id == "diapason" }
        val pulses = guides.single { it.id == "pulsos-pie" }
        assertTrue(procedureSectionText(vibration, "sensations")!!.contains("temperatura"))
        assertTrue(procedureSectionText(vibration, "sensations")!!.contains("monofilamento"))
        assertTrue(procedureSectionText(vibration, "context")!!.contains("ambos"))
        assertTrue(procedureSectionText(pulses, "limits")!!.contains("Incluso cuando se siente un pulso"))
        assertTrue(procedureSectionText(pulses, "other-exams")!!.contains("Doppler"))
        assertTrue(procedureSectionText(pulses, "other-exams")!!.contains("no indica cuál necesita"))
        val refs = (listOf(pulses.source) + pulses.additionalSources).map { it.reference }
        assertTrue(refs.contains("https://pmc.ncbi.nlm.nih.gov/articles/PMC11129855/"))
        listOf(vibration, pulses).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            guide.relatedQuestions.forEach { assertFalse(procedureSectionText(guide, it.sectionId).isNullOrBlank()) }
        }
    }
}
