package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.usecase.AskBitavidaAi
import org.junit.Assert.*
import org.junit.Test

class MedicationConceptEducationTest {
    @Test fun mechanismAndAppGuideExplainDifferentRolesWithoutMakingDecisions() {
        val repository = BundledEducationRepository { error("Direct reading must not load a classifier") }
        val ask = AskBitavidaAi(repository)
        val mechanism = ask.read(EducationTopic.MEDICATION_MECHANISM).entry
        val appGuide = ask.read(EducationTopic.MEDICATION_APP_USE).entry

        assertTrue(mechanism.details.any { it.contains("producción de glucosa del hígado") })
        assertTrue(mechanism.details.any { it.contains("riñones recuperan parte de la glucosa") })
        assertTrue(mechanism.details.any { it.contains("incretinas") && it.contains("DPP-4") })
        assertTrue(mechanism.details.any { it.contains("no permite cambiar su tratamiento") })
        assertTrue(mechanism.additionalSources.any { it.reference.contains("212614Orig1s000PharmR.pdf") })
        assertTrue(appGuide.importance!!.contains("plan registra") && appGuide.importance.contains("toma es una declaración"))
        assertTrue(appGuide.details.any { it.contains("no declaran por sí solas") })
        assertTrue(appGuide.details.any { it.contains("coincide de forma exacta normalizada") })
        assertTrue(appGuide.details.any { it.contains("no modifica la indicación") })
        listOf(mechanism, appGuide).forEach {
            assertEquals("1.2", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun identificationPurposeAndSafetyRemainEducationalAndOffline() {
        val repository = BundledEducationRepository { error("Direct reading must not load a classifier") }
        val ask = AskBitavidaAi(repository)
        val identification = ask.read(EducationTopic.MEDICATION_IDENTIFY).entry
        val purpose = ask.read(EducationTopic.MEDICATION_PURPOSE).entry
        val adverseEffects = ask.read(EducationTopic.MEDICATION_SIDE_EFFECTS).entry
        val consultation = ask.read(EducationTopic.MEDICATION_CONSULTATION).entry

        assertTrue(identification.details.any { it.contains("coincidencias exactas normalizadas") })
        assertTrue(identification.details.any { it.contains("no autoriza sustituir") })
        assertTrue(purpose.details.any { it.contains("usos generales") && it.contains("No reemplaza") })
        assertTrue(adverseEffects.details.any { it.contains("pérdida de conciencia o convulsión") })
        assertTrue(adverseEffects.details.any { it.contains("no ajustes ni suspendas") })
        assertTrue(adverseEffects.additionalSources.any { it.reference.contains("fda.gov") })
        assertTrue(consultation.details.any { it.contains("no ofrece una regla universal") })
        listOf(identification, purpose, adverseEffects, consultation).forEach {
            assertEquals("1.2", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
            assertTrue(it.source.reference.startsWith("https://"))
            assertEquals("22 de noviembre de 2026", it.source.consultedOn)
        }
    }

    @Test fun curatedConceptGuidesLoadDirectlyOfflineWithTheirSpecificEvidence() {
        val repository = BundledEducationRepository { error("Direct reading must not load a classifier") }
        val ask = AskBitavidaAi(repository)
        val contraindications = ask.read(EducationTopic.MEDICATION_CONTRAINDICATIONS).entry
        val precautions = ask.read(EducationTopic.MEDICATION_PRECAUTIONS).entry
        val interactions = ask.read(EducationTopic.MEDICATION_INTERACTIONS).entry
        val presentations = ask.read(EducationTopic.MEDICATION_FORMULATIONS).entry

        assertTrue(contraindications.details.any { it.contains("no debe utilizarse") })
        assertTrue(contraindications.details.any { it.contains("reacción no deseada") })
        assertTrue(contraindications.source.reference.contains("fdas-labeling-resources"))
        assertTrue(precautions.details.any { it.contains("no demuestra por sí solo") })
        assertTrue(precautions.source.reference.contains("nia.nih.gov"))
        assertTrue(interactions.details.any { it.contains("no cruza automáticamente") })
        assertTrue(interactions.details.any { it.contains("no aparezca en esta biblioteca") })
        assertTrue(presentations.details.any { it.contains("acción rápida, corta, intermedia y prolongada") })
        assertTrue(presentations.details.any { it.contains("no autorizan a intercambiar") })
        listOf(contraindications, precautions, interactions, presentations).forEach {
            assertEquals("1.2", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
            assertTrue(it.source.reference.startsWith("https://"))
            assertEquals("22 de noviembre de 2026", it.source.consultedOn)
        }
    }
}
