package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class BloodLabEditorialTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures().associateBy { it.id }

    @Test fun fastingAndRandomGlucoseDescribeDifferentConditionsWithoutAdjustingTreatment() {
        val fasting = guides.getValue("glucosa-ayunas")
        val random = guides.getValue("glucosa-aleatoria")
        assertTrue(fasting.preparation.contains("ayuno"))
        assertTrue(fasting.preparation.contains("no suspendas"))
        assertTrue(random.preparation.contains("después de comer"))
        assertTrue(random.extraSections.single { it.id == "comparison" }.body.contains("tres meses"))
        assertTrue(fasting.important.contains("suspendas tratamientos"))
    }

    @Test fun bloodCountAndMetabolicPanelExplainTheirDifferentMeasurements() {
        val count = guides.getValue("biometria-hematica")
        val panel = guides.getValue("panel-metabolico")
        assertTrue(count.method.contains("glóbulos rojos"))
        assertTrue(count.method.contains("plaquetas"))
        assertTrue(count.extraSections.single { it.id == "comparison" }.body.contains("HbA1c"))
        assertTrue(panel.method.contains("creatinina"))
        assertTrue(panel.preparation.contains("Algunos paneles"))
        assertTrue(panel.extraSections.single { it.id == "comparison" }.body.contains("básico"))
    }

    @Test fun sourcesAreTrackedAndClinicalReviewRemainsPending() {
        listOf("glucosa-ayunas", "glucosa-aleatoria", "biometria-hematica", "panel-metabolico").forEach { id ->
            val guide = guides.getValue(id)
            val refs = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertEquals(id, EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertFalse(id, guide.contentVersion.isNullOrBlank())
            assertTrue(id, guide.sectionSourceReferences.isNotEmpty())
            assertTrue(id, guide.sectionSourceReferences.values.flatten().all(refs::contains))
            assertTrue(id, guide.extraSections.all { it.sourceReferences.isNotEmpty() && it.sourceReferences.all(refs::contains) })
        }
    }
}
