package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class RenalLabEditorialTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures().associateBy { it.id }

    @Test fun albuminRatioUsesUrineAndDistinguishesBloodFiltration() {
        val guide = guides.getValue("albumina-creatinina")
        assertTrue(guide.description.contains("orina"))
        assertTrue(guide.method.contains("misma muestra"))
        assertTrue(guide.extraSections.single { it.id == "comparison" }.body.contains("sangre"))
        assertTrue(guide.important.contains("cifra aislada"))
    }

    @Test fun urineCultureDiffersFromGeneralExamination() {
        val culture = guides.getValue("urocultivo")
        val general = guides.getValue("examen-orina")
        assertTrue(culture.description.contains("microorganismos"))
        assertTrue(culture.method.contains("tiempo"))
        assertTrue(general.method.contains("tira"))
        assertTrue(general.extraSections.single { it.id == "comparison" }.body.contains("cultivo"))
        assertTrue(culture.extraSections.single { it.id == "comparison" }.body.contains("examen general"))
    }

    @Test fun bloodUreaAndElectrolytesExplainDifferentSubstances() {
        val urea = guides.getValue("urea-sangre")
        val minerals = guides.getValue("electrolitos")
        assertTrue(urea.description.contains("proteínas"))
        assertTrue(urea.preparation.contains("otros análisis"))
        assertTrue(minerals.description.contains("carga eléctrica"))
        assertTrue(minerals.method.contains("potasio"))
        assertTrue(minerals.important.contains("No intentes"))
    }

    @Test fun allFiveGuidesHaveVersionedTraceableSourcesAndPendingHumanReview() {
        listOf("albumina-creatinina", "urocultivo", "urea-sangre", "electrolitos", "examen-orina").forEach { id ->
            val guide = guides.getValue(id)
            val refs = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertEquals(id, EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertFalse(id, guide.contentVersion.isNullOrBlank())
            assertTrue(id, guide.questions.size in 3..5)
            assertTrue(id, guide.sectionSourceReferences.isNotEmpty())
            assertTrue(id, guide.sectionSourceReferences.values.flatten().all(refs::contains))
            assertTrue(id, guide.extraSections.all { it.sourceReferences.isNotEmpty() && it.sourceReferences.all(refs::contains) })
        }
    }
}
