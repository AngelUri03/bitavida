package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class ProcedureGuideCatalogTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun catalogHasUniqueStableIdsAndStructuredSourcedContent() {
        assertEquals(guides.size, guides.map { it.id }.toSet().size)
        assertEquals(guides.size, guides.map { EducationTokenizer.normalize(it.title) }.toSet().size)
        assertTrue("Se esperan al menos 80 fichas distintas", guides.size >= 80)
        guides.forEach { guide ->
            assertTrue(guide.id.matches(Regex("[a-z0-9-]+")))
            assertTrue(guide.title.isNotBlank())
            assertTrue(guide.categories.isNotEmpty())
            assertTrue(guide.description.isNotBlank())
            assertTrue(guide.purpose.isNotBlank())
            assertTrue(guide.during.isNotBlank())
            assertTrue(guide.method.isNotBlank())
            assertTrue(guide.preparation.isNotBlank())
            assertTrue(guide.after.isNotBlank())
            assertTrue(guide.important.isNotBlank())
            assertTrue(guide.questions.size in 3..5)
            assertTrue(guide.source.reference.startsWith("https://"))
            assertTrue(guide.reviewedOn.isNotBlank())
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }

    @Test fun categoriesAreCoveredAndSurgeryCanBeCrossListedWithoutDuplicatingGuide() {
        assertEquals(ProcedureCategory.entries.toSet(), guides.flatMap { it.categories }.toSet())
        val vitrectomy = guides.single { it.id == "vitrectomia" }
        assertTrue(ProcedureCategory.EYES in vitrectomy.categories)
        assertTrue(ProcedureCategory.SURGERY in vitrectomy.categories)
        assertTrue(vitrectomy.important.contains("no es una cirugía rutinaria"))
    }

    @Test fun cataractGuideExplainsExperienceAndKeepsClinicalReviewPending() {
        val guide = guides.single { it.id == "cataratas" }
        assertTrue(guide.description.contains("cristalino"))
        assertTrue(guide.during.contains("despierta"))
        assertTrue(guide.after.contains("revisiones") || guide.after.contains("revisión"))
        assertTrue(guide.important.contains("pérdida de visión"))
        assertTrue(guide.source.reference.contains("nei.nih.gov"))
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
    }

    @Test fun peripheralAngioplastyIsDistinctFromVeinTreatmentAndHasSpecificSource() {
        val guide = guides.single { it.id == "angioplastia-pierna" }
        assertTrue(guide.description.contains("arteria"))
        assertTrue(guide.description.contains("venas"))
        assertTrue(guide.during.contains("stent"))
        assertEquals("https://www.plymouthhospitals.nhs.uk/display-pil/pil-leg-angioplasty-7919", guide.source.reference)
    }

    @Test fun a1cPreparationDoesNotInventAFast() {
        val guide = guides.single { it.id == "hba1c" }
        assertTrue(guide.description.contains("tres meses"))
        assertTrue(guide.preparation.contains("no requiere ayuno"))
        assertTrue(guide.preparation.contains("otros estudios"))
    }
}
