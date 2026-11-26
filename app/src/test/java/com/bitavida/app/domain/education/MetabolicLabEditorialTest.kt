package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class MetabolicLabEditorialTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures().associateBy { it.id }

    @Test fun lipidsAndLiverPanelExplainDifferentComponents() {
        val lipids = guides.getValue("perfil-lipidos")
        val liver = guides.getValue("funcion-hepatica")
        assertTrue(lipids.method.contains("LDL") && lipids.method.contains("HDL"))
        assertTrue(lipids.method.contains("triglicéridos"))
        assertTrue(liver.method.contains("albúmina") && liver.method.contains("bilirrubina"))
        assertTrue(liver.extraSections.single { it.id == "components" }.body.contains("enzimas"))
    }

    @Test fun vitaminB12ExplainsNervesAndDoesNotAdviseStoppingTreatment() {
        val guide = guides.getValue("vitamina-b12")
        assertTrue(guide.description.contains("nervios"))
        assertTrue(guide.important.contains("No se debe suspender metformina"))
        assertTrue(guide.extraSections.single { it.id == "absorption" }.body.contains("alimentos"))
    }

    @Test fun ketoneGuidesExplainSamplesAndUrgentSymptomsWithoutDiagnosing() {
        val blood = guides.getValue("cetonas-sangre")
        val urine = guides.getValue("cetonas-orina")
        assertTrue(blood.during.contains("vena"))
        assertTrue(urine.method.contains("tira"))
        listOf(blood, urine).forEach {
            assertTrue(it.important.contains("atención médica inmediata"))
            assertTrue(it.extraSections.single { section -> section.id == "comparison" }.body.contains("sangre"))
            assertTrue(it.purpose.contains("no", ignoreCase = true))
        }
    }

    @Test fun allGuidesRetainSpecificSourcesAndPendingClinicalReview() {
        listOf("perfil-lipidos", "funcion-hepatica", "vitamina-b12", "cetonas-sangre", "cetonas-orina").forEach { id ->
            val guide = guides.getValue(id)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertEquals(id, EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertFalse(id, guide.contentVersion.isNullOrBlank())
            assertTrue(id, guide.sectionSourceReferences.isNotEmpty())
            assertTrue(id, guide.sectionSourceReferences.values.flatten().all(references::contains))
            assertTrue(id, guide.extraSections.all { it.sourceReferences.isNotEmpty() && it.sourceReferences.all(references::contains) })
        }
    }
}
