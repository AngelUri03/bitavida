package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class FollowupConsultationEducationTest {
    @Test fun threeLocalGuidesDescribeDifferentVisitsWithoutMakingClinicalDecisions() {
        val guides = BundledEducationRepository { error("Library reading must not load the model") }.procedures()
        val internal = guides.single { it.id == "consulta-interna" }
        val eyes = guides.single { it.id == "consulta-oftalmologia" }
        val feet = guides.single { it.id == "pies-integral" }

        assertTrue(internal.description.contains("personas adultas"))
        assertTrue(internal.important.contains("no autoriza a cambiar medicamentos"))
        assertTrue(internal.source.reference.contains("acponline.org"))
        assertTrue(eyes.method.contains("retina") && eyes.method.contains("gotas"))
        assertTrue(eyes.after.contains("borrosa") && eyes.after.contains("traslado"))
        assertTrue(eyes.important.contains("información diferente"))
        assertTrue(feet.method.contains("monofilamento") && feet.method.contains("diapasón"))
        assertTrue(feet.during.contains("zapatos y calcetines"))
        assertTrue(feet.important.contains("No intentes cortar callos"))
        assertEquals("2.0-consulta-interna", internal.contentVersion)
        assertEquals("2.1-consulta-oftalmologia", eyes.contentVersion)
        assertEquals("2.0-pies-integral", feet.contentVersion)
        listOf(internal, eyes, feet).forEach { guide ->
            assertEquals("22 de noviembre de 2026", guide.reviewedOn)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
            val references = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            assertTrue(guide.sectionSourceReferences.getValue("during").all { it in references })
        }
        assertTrue(searchProcedureGuides("revision de pies", guides).any { it.id == feet.id })
        assertTrue(searchProcedureGuides("medicina interna", guides).any { it.id == internal.id })
    }
}
