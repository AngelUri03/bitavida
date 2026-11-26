package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the shipped guide's content and provenance; it is not a clinical review. */
class MetforminEmpagliflozinEditorialTest {
    @Test fun packagedGuideExplainsBothComponentsAndFormulationSpecificSafety() {
        val guide = BundledEducationRepository {
            error("Reading a local guide must not initialize the classifier")
        }.drugs().single { it.id == "metformin_empagliflozin" }
        fun section(intent: DrugIntent) =
            guide.sections.single { it.intent == intent }.body.lowercase()

        assertTrue(section(DrugIntent.MECHANISM).contains("hígado"))
        assertTrue(section(DrugIntent.MECHANISM).contains("riñones"))
        assertTrue(section(DrugIntent.SIDE_EFFECTS).contains("aunque la glucosa no parezca muy elevada"))
        assertTrue(section(DrugIntent.CONTRAINDICATIONS).contains("profesional debe interpretar"))
        assertTrue(section(DrugIntent.FORMULATIONS).contains("xr de esta marca"))
        assertTrue(section(DrugIntent.FORMULATIONS).contains("sin partirla ni triturarla"))
        assertTrue(guide.statusNote.contains("pendiente de revisión clínica", ignoreCase = true))
        assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        assertTrue(guide.sources.single().url.contains("0fdd0255-0055-65f3-b2c0-db8fbb87beae"))
    }
}
