package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ConsultationGuideEducationTest {
    @Test fun consultationGuidesReachTheBundledRepositoryWithDistinctSpecificInformation() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val family = guides.single { it.id == "consulta-familiar" }
        val nutrition = guides.single { it.id == "consulta-nutricion" }
        val education = guides.single { it.id == "educacion-autocuidado" }
        val dental = guides.single { it.id == "consulta-odontologica" }

        assertTrue(family.during.contains("dificultades") && family.after.contains("referencia"))
        assertTrue(nutrition.during.contains("alimentos y bebidas") && nutrition.important.contains("presupuesto"))
        assertTrue(education.during.contains("bienestar emocional") && education.method.contains("grupo"))
        assertTrue(dental.during.contains("espejo pequeño") && dental.method.contains("no es lo mismo"))
        assertTrue(dental.additionalSources.any { it.reference.contains("nidcr.nih.gov") })
        assertTrue(education.source.reference.contains("cdc.gov"))
        assertTrue(nutrition.source.reference.contains("niddk.nih.gov"))
        val entries = listOf(family, nutrition, education, dental)
        assertEquals(entries.size, entries.map { it.during }.distinct().size)
        assertEquals(entries.size, entries.map { it.questions }.distinct().size)
        assertEquals("2.0-consulta-familiar", family.contentVersion)
        assertEquals("2.1-consulta-nutricion", nutrition.contentVersion)
        assertEquals("2.0-educacion-autocuidado", education.contentVersion)
        assertEquals("2.0-consulta-odontologica", dental.contentVersion)
        entries.forEach { guide ->
            assertTrue(ProcedureCategory.CONSULTS in guide.categories)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            assertTrue(guide.sectionSourceReferences.getValue("during").isNotEmpty())
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in references })
        }
        assertTrue(searchProcedureGuides("revision dental", guides).any { it.id == dental.id })
        assertTrue(searchProcedureGuides("nutriologo", guides).any { it.id == nutrition.id })
    }
}
