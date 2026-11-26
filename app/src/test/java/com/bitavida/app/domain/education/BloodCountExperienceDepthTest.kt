package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class BloodCountExperienceDepthTest {
    @Test fun distinguishesRedCellMeasuresAndWhiteCellDifferential() {
        val guide = BundledEducationRepository(loadModelText = { "" })
            .procedures().single { it.id == "biometria-hematica" }
        val red = procedureSectionText(guide, "red-cell-measures")!!
        val white = procedureSectionText(guide, "white-cell-differential")!!
        assertTrue(red.contains("proporción de la sangre"))
        assertTrue(red.contains("tamaño promedio"))
        assertTrue(white.contains("cantidades o porcentajes"))
        assertTrue(white.contains("no identifica por sí sola una enfermedad"))
        listOf("red-cell-measures", "white-cell-differential").forEach { id ->
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
