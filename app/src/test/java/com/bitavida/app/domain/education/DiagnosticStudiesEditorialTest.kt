package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class DiagnosticStudiesEditorialTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures().associateBy { it.id }

    @Test fun oralGlucoseTestDescribesSamplesDrinkAndPreparation() {
        val guide = guides.getValue("tolerancia-glucosa")
        assertTrue(guide.during.contains("primera muestra"))
        assertTrue(guide.during.contains("bebida"))
        assertTrue(guide.preparation.contains("ayuno"))
        assertTrue(guide.important.contains("no cambies"))
    }

    @Test fun woundCultureDistinguishesGrowthFromSensitivity() {
        val guide = guides.getValue("cultivo-herida")
        assertTrue(guide.method.contains("crecimiento"))
        assertTrue(guide.method.contains("sensibilidad"))
        assertTrue(guide.during.contains("personal", ignoreCase = true))
        assertTrue(guide.after.contains("No inicies"))
    }

    @Test fun ultrasoundGuidesExplainDifferentStructuresAndExperiences() {
        val renal = guides.getValue("ultrasonido-renal")
        val heart = guides.getValue("ecocardiograma")
        val leg = guides.getValue("doppler-arterial-pierna")
        assertTrue(renal.during.contains("gel") && renal.during.contains("transductor"))
        assertTrue(renal.preparation.contains("vejiga"))
        assertTrue(heart.important.contains("transesofágico"))
        assertTrue(heart.method.contains("electrocardiograma"))
        assertTrue(leg.during.contains("sonda"))
        assertTrue(leg.description.contains("arterias"))
    }

    @Test fun eachStudyHasTraceableSourcesAndPendingClinicalReview() {
        listOf("tolerancia-glucosa", "cultivo-herida", "ultrasonido-renal", "ecocardiograma", "doppler-arterial-pierna").forEach { id ->
            val guide = guides.getValue(id)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertEquals(id, EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertFalse(id, guide.contentVersion.isNullOrBlank())
            assertTrue(id, guide.sectionSourceReferences.isNotEmpty())
            assertTrue(id, guide.sectionSourceReferences.values.flatten().all(references::contains))
            assertTrue(id, guide.extraSections.isNotEmpty())
            assertTrue(id, guide.extraSections.all { section -> section.sourceReferences.isNotEmpty() && section.sourceReferences.all(references::contains) })
            assertFalse(id, references.any { "/spanish/ency/" in it })
        }
    }
}
