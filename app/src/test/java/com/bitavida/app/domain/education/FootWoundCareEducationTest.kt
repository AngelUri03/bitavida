package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

class FootWoundCareEducationTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()

    @Test fun woundCareDebridementAndOffloadingExplainDistinctCare() {
        val care = guides.single { it.id == "curacion-ulcera-pie" }
        val debridement = guides.single { it.id == "desbridamiento-herida" }
        val offloading = guides.single { it.id == "descarga-presion-pie" }
        assertTrue(care.description.contains("herida abierta"))
        assertTrue(care.extraSections.any { it.id == "difference" })
        assertTrue(debridement.description.contains("tejido muerto"))
        assertTrue(debridement.important.contains("No intentes"))
        assertTrue(offloading.description.contains("presión"))
        assertTrue(offloading.extraSections.any { it.id == "difference" })
        for (guide in listOf(care, debridement, offloading)) {
            val sources = setOf(guide.source.reference) + guide.additionalSources.map { it.reference }
            assertTrue(guide.sectionSourceReferences.values.flatten().all { it in sources })
            assertTrue(guide.extraSections.flatMap { it.sourceReferences }.all { it in sources })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
    }
}
