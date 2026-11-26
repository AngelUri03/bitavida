package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class GlucoseKidneyHeartGuideTest {
    @Test fun threeStudiesDescribeTheirDistinctExperienceAndLimitationsOffline() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val a1c = guides.single { it.id == "hba1c" }
        val kidney = guides.single { it.id == "creatinina-egfr" }
        val stress = guides.single { it.id == "prueba-esfuerzo" }

        assertTrue(a1c.description.contains("tres meses") && a1c.description.contains("glóbulos rojos"))
        assertTrue(a1c.method.contains("ayunas") && a1c.important.contains("transfusiones"))
        assertTrue(a1c.preparation.contains("no requiere ayuno") && a1c.preparation.contains("otros estudios"))
        assertTrue(kidney.during.contains("muestra") && kidney.during.contains("no necesita otra extracción"))
        assertTrue(kidney.method.contains("estimación") && kidney.important.contains("músculo"))
        assertTrue(stress.during.contains("brazalete") && stress.method.contains("bicicleta"))
        assertTrue(stress.important.contains("coméntalo de inmediato al personal"))
        assertTrue(stress.preparation.contains("no suspendas ni modifiques"))
        listOf(a1c, kidney, stress).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.contentVersion.orEmpty().startsWith("2.0-"))
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("method").all { it in references })
        }
        assertTrue(searchProcedureGuides("azucar de tres meses", guides).any { it.id == a1c.id })
        assertTrue(searchProcedureGuides("prueba caminadora", guides).any { it.id == stress.id })
    }
}
