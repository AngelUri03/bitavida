package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.usecase.AskBitavidaAi
import org.junit.Assert.*
import org.junit.Test

/** Exercises the actual bundled catalogue; it does not certify clinical review. */
class OctEducationIntegrationTest {
    private val repository = BundledEducationRepository(
        loadModelText = { error("OCT catalogue lookup must not load the classifier") },
    )
    private val ask = AskBitavidaAi(repository)
    private val oct = repository.procedures().single { it.id == "oct" }

    private fun textOfGuide(guide: ProcedureGuide): String =
        (listOfNotNull(
            guide.summary, guide.description, guide.purpose, guide.during, guide.method,
            guide.preparation, guide.after, guide.important,
        ) + guide.questions + guide.extraSections.map { it.body })
            .joinToString(" ")
            .let(EducationTokenizer::normalize)

    @Test fun bundledOctKeepsDocumentedSourcesAndPendingClinicalReview() {
        assertEquals(1, repository.procedures().count { it.id == "oct" })
        assertEquals(ProcedureCategory.EYES, oct.categories.first())
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, oct.reviewStatus)
        assertTrue(oct.contentVersion?.isNotBlank() == true)

        val references = listOf(oct.source) + oct.additionalSources
        assertTrue(references.any { it.reference.contains("asrs.org") })
        assertTrue(references.all { it.reference.startsWith("https://") && it.consultedOn.isNotBlank() })
        assertTrue(oct.extraSections.all { section ->
            section.id.isNotBlank() && section.title.isNotBlank() && section.body.isNotBlank() &&
                section.sourceReferences.all { ref -> references.any { it.reference == ref } }
        })
        assertEquals(oct.extraSections.size, oct.extraSections.map { it.id }.toSet().size)
        assertTrue(oct.extraSections.all { it.sourceReferences.isNotEmpty() })
        assertTrue(setOf("what", "purpose", "during", "method", "preparation", "after")
            .all { oct.sectionSourceReferences[it].orEmpty().isNotEmpty() })
        assertTrue(oct.sectionSourceReferences.values.flatten().all { ref ->
            references.any { it.reference == ref }
        })
    }

    @Test fun octExplainsAnatomyExperiencePreparationAndAlternatives() {
        val content = textOfGuide(oct)
        listOf("retina", "macula", "luz", "capas", "diabetes", "liquido",
            "barbilla", "frente", "mirada", "gotas", "dilatacion",
            "vision", "retinografia", "angiografia").forEach { concept ->
            assertTrue("La guía local debe explicar: $concept", content.contains(concept))
        }
        assertNotEquals("Prueba de imagen ocular.", oct.method.trim())
        assertTrue("La preparación debe abordar la dilatación",
            EducationTokenizer.normalize(oct.preparation).contains("dilat"))
        assertTrue("Las preguntas deben ser propias de OCT", oct.questions.any {
            EducationTokenizer.normalize(it).contains("imagen") ||
                EducationTokenizer.normalize(it).contains("liquido")
        })
    }

    @Test fun centralAndAppointmentContextsRetrieveTheSameBundledOct() {
        val central = ask.centralProcedureQuestion("¿Qué es una OCT?")
        val appointment = ask.askProcedure("¿Qué es una OCT?")
        assertTrue(central is ProcedureQuestionResult.Match)
        assertTrue(appointment is ProcedureQuestionResult.Match)
        assertEquals(oct, (central as ProcedureQuestionResult.Match).guide)
        assertEquals(oct, (appointment as ProcedureQuestionResult.Match).guide)
    }

    @Test fun octAnswersSelectDocumentedSectionsWithoutInferringOtherSubjects() {
        val questions = mapOf(
            "¿Cómo es el aparato?" to "method",
            "¿Duele?" to "during",
            "¿Necesita gotas?" to "preparation",
            "¿Cuánto dura?" to "method",
            "¿Qué pasa después?" to "after",
            "¿Qué pueden encontrar?" to "findings",
            "¿Es lo mismo que una retinografía?" to "comparison",
            "¿Por qué la piden en diabetes?" to "purpose",
        )
        questions.forEach { (question, expected) ->
            assertEquals(question, expected, octFollowupSection(question))
            assertFalse(procedureSectionText(oct, expected).isNullOrBlank())
        }
        assertTrue(procedureSectionText(oct, octFollowupSection("¿Duele?")!!).orEmpty().contains("no duele"))
        assertTrue(procedureSectionText(oct, octFollowupSection("¿Cuánto dura?")!!).orEmpty().contains("minutos"))
        val painQuestion = oct.relatedQuestions.single { it.id == "oct-pain" }
        val durationQuestion = oct.relatedQuestions.single { it.id == "oct-duration" }
        assertTrue(procedureSectionText(oct, painQuestion.sectionId).orEmpty().contains("no duele"))
        assertTrue(procedureSectionText(oct, durationQuestion.sectionId).orEmpty().contains("minutos"))
        assertNull(octFollowupSection("¿Duele la metformina?"))
        assertNull(octFollowupSection("¿Qué debo hacer?"))
        assertNull(octFollowupSection("¿Es segura para mi abuela?"))
    }

    @Test fun namedOctDoesNotBypassClinicalGuardsOrResolveAmbiguousBypass() {
        assertEquals(ProcedureQuestionResult.Urgent,
            ask.askProcedure("Mi familiar no respira y tiene una OCT"))
        assertEquals(ProcedureQuestionResult.OutsideScope,
            ask.askProcedure("Mi mamá tiene visión borrosa, ¿necesita una OCT?"))
        assertEquals(ProcedureQuestionResult.OutsideScope,
            ask.centralProcedureQuestion("¿Debo suspender medicamentos antes de una OCT?"))

        val bypass = ask.centralProcedureQuestion("¿Qué es una cirugía de bypass?")
        assertTrue(bypass is ProcedureQuestionResult.Choices)
        assertTrue((bypass as ProcedureQuestionResult.Choices).guides.map { it.id }
            .containsAll(listOf("bypass-coronario", "bypass-gastrico")))
    }
}
