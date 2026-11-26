package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class DiabetesFollowupGuideTest {
    @Test fun followupAndFootExaminationsExplainDifferentPurposesOffline() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val consultation = guides.single { it.id == "consulta-diabetes" }
        val tuningFork = guides.single { it.id == "diapason" }
        val pulses = guides.single { it.id == "pulsos-pie" }

        assertTrue(consultation.during.contains("medicamentos o suplementos"))
        assertTrue(consultation.after.contains("resultados siguen pendientes"))
        assertTrue(consultation.important.contains("no interpreta cifras"))
        assertTrue(tuningFork.method.contains("vibración") && tuningFork.method.contains("monofilamento"))
        assertTrue(tuningFork.important.contains("no permite conocer por sí solo la causa"))
        assertTrue(pulses.method.contains("no requiere una aguja ni una incisión"))
        assertTrue(pulses.important.contains("no confirma por sí solo una enfermedad"))
        assertEquals("2.0-consulta-diabetes", consultation.contentVersion)
        assertEquals("2.1-diapason", tuningFork.contentVersion)
        assertEquals("2.1-pulsos-pie", pulses.contentVersion)
        listOf(consultation, tuningFork, pulses).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertEquals(5, guide.questions.size)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("during").all { it in references })
        }
        assertTrue(searchProcedureGuides("prueba de vibracion", guides).any { it.id == tuningFork.id })
        assertTrue(searchProcedureGuides("pulsos en pies", guides).any { it.id == pulses.id })
    }
}
