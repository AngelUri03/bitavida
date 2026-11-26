package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

/** Reports missing curation; does not judge clinical accuracy of a section. */
class DrugRelatedQuestionAuditTest {
    private fun invalidLinks(drug: DrugEducation): List<DrugRelatedQuestion> =
        drug.relatedQuestions.filter { question ->
            question.question.isBlank() ||
                drug.sections.none { it.intent == question.intent && it.body.isNotBlank() }
        }

    @Test fun relatedQuestionsOnlyLinkToExistingDocumentedSections() {
        val drugs = BundledEducationRepository(loadModelText = { error("Classifier not needed") }).drugs()
        drugs.forEach { drug ->
            assertTrue(drug.id, invalidLinks(drug).isEmpty())
            assertEquals(drug.id, drug.relatedQuestions.size,
                drug.relatedQuestions.map { EducationTokenizer.normalize(it.question) }.distinct().size)
        }
        val incomplete = drugs.first().copy(
            relatedQuestions = listOf(DrugRelatedQuestion("", DrugIntent.IDENTIFY)),
        )
        assertEquals(1, invalidLinks(incomplete).size)
        val missing = drugs.filter { it.relatedQuestions.isEmpty() }
        println("Fichas farmacológicas con preguntas genéricas: ${missing.size}")
        missing.forEach { println(it.id) }
    }
}
