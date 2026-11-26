package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** Provenance metadata checks: they do not validate the content clinically. */
class ProcedureSourceProvenanceTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun undocumentedConsultationIsNotInferredFromTheEditorialReview() {
        val guide = guides.single { it.id == "consulta-familiar" }
        assertEquals("22 de noviembre de 2026", guide.reviewedOn)
        assertEquals("Fecha no documentada", guide.source.consultedOn)
    }

    @Test fun directlyConsultedSourcesKeepTheirAttributionAndDate() {
        val expected = mapOf(
            "oct" to "ASRS · Guía de imágenes diagnósticas de la retina",
            "ecocardiograma" to "MedlinePlus · Ecocardiografía",
            "consulta-geriatria" to "IMSS · GeriatrIMSS",
            "cateter-hemodialisis" to "NIDDK · Hemodiálisis",
            "cateter-peritoneal" to "NIDDK · Diálisis peritoneal",
        )
        expected.forEach { (id, title) ->
            val source = guides.single { it.id == id }.source
            assertEquals(id, title, source.title)
            assertEquals(id, "22 de noviembre de 2026", source.consultedOn)
        }
    }
}
