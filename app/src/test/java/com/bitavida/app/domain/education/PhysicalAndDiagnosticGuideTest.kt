package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class PhysicalAndDiagnosticGuideTest {
    @Test fun threeGuidesExplainTheExperienceAndCanBeReadOffline() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val filament = guides.single { it.id == "monofilamento" }
        val retina = guides.single { it.id == "fondo-ojo" }
        val electrocardiogram = guides.single { it.id == "electrocardiograma" }

        assertTrue(filament.description.contains("no es una aguja"))
        assertTrue(filament.during.contains("zapatos y calcetines"))
        assertTrue(filament.method.contains("normalmente no es doloroso"))
        assertTrue(retina.during.contains("gotas") && retina.during.contains("lentes de aumento"))
        assertTrue(retina.method.contains("retinografía") && retina.method.contains("OCT"))
        assertTrue(retina.after.contains("borrosa") && retina.after.contains("acompañante"))
        assertTrue(electrocardiogram.during.contains("electrodos") && electrocardiogram.during.contains("quieto"))
        assertTrue(electrocardiogram.method.contains("no envía descargas eléctricas"))
        assertTrue(electrocardiogram.source.reference.contains("nhlbi.nih.gov"))
        listOf(filament, retina, electrocardiogram).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("method").all { it in references })
        }
        assertTrue(searchProcedureGuides("electro del corazon", guides).any { it.id == electrocardiogram.id })
        assertTrue(searchProcedureGuides("hilito en los pies", guides).any { it.id == filament.id })
    }
}
