package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.data.education.LocalEducationLibrary
import com.bitavida.app.data.education.ObservationEducationLibrary
import com.bitavida.app.data.education.ReminderEducationLibrary
import com.bitavida.app.domain.usecase.AskBitavidaAi
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AskBitavidaAiTest {
    private val repository = BundledEducationRepository {
        val path = "app/src/main/res/raw/bitavida_education_model.tsv"
        (File(path).takeIf { it.isFile } ?: File("..", path)).readText()
    }
    private val ask = AskBitavidaAi(repository)

    @Test fun personalReadingsAndClinicalRequestsNeverSelectAnEducationalAnswer() = runBlocking {
        val fictionalQuestions = listOf(
            "Mi glucosa ficticia es 999, es normal",
            "Qué significan estos números de presión 111/77",
            "Peso ficticio 88 kg, debo bajar",
            "Qué dosis de insulina corresponde",
            "Qué medicamento tomar para presión",
            "Me duele el pecho y tengo un mareo",
            "Creo que es una urgencia",
            "Qué tratamiento recomiendas para el azúcar",
            "Cómo bajar la glucosa en ayunas",
            "Mi presión es normal",
            "¿Qué significan １２３ en mi glucómetro?",
            "Cómo ajustar la dosis",
            "Mi abuela se desmayó, qué hago",
        )
        fictionalQuestions.forEach { question ->
            assertEquals(
                EducationResult.Fallback(EducationFallback.OUTSIDE_SCOPE),
                ask(question),
            )
        }
    }

    @Test fun educationalQuestionsUseTheLearnedClassifier() = runBlocking {
        val result = ask("Me explicas las dos cifras de tensión")
        assertTrue(result is EducationResult.Answer)
        assertEquals(EducationTopic.PRESSURE_NUMBERS, (result as EducationResult.Answer).entry.topic)
        assertFalse(result.fromLibrary)
        val observation = ask("Por qué registrar una sensación de desmayo")
        assertTrue(observation is EducationResult.Answer)
        assertEquals(EducationTopic.OBS_006_DIZZINESS,
            (observation as EducationResult.Answer).entry.topic)
        val unknown = ask("Necesito saber el clima de mañana")
        assertTrue(unknown is EducationResult.Fallback)
        assertEquals(EducationFallback.UNCERTAIN, (unknown as EducationResult.Fallback).reason)
    }

    @Test fun libraryIsCompleteVersionedAndExplicitlyPendingHumanReview() {
        val library = LocalEducationLibrary.entries + ObservationEducationLibrary.entries + ReminderEducationLibrary.entries
        assertEquals(EducationTopic.entries.toSet(), library.keys)
        EducationTopic.entries.forEach { topic ->
            val answer = ask.read(topic)
            assertTrue(answer.fromLibrary)
            assertEquals(topic, answer.entry.topic)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, answer.entry.reviewStatus)
            assertTrue(answer.entry.explanation.isNotBlank())
            assertTrue(answer.entry.source.title.isNotBlank())
            assertTrue(answer.entry.source.reference.isNotBlank())
            assertTrue(answer.entry.version.isNotBlank())
            assertTrue(answer.entry.related.isNotEmpty())
            assertTrue(answer.entry.related.all { it in library && it != topic })
        }
    }

    @Test fun emptyOrLongInputDoesNotLoadTheModel() = runBlocking {
        val failingRepository = BundledEducationRepository { error("Model must remain unloaded") }
        val boundedAsk = AskBitavidaAi(failingRepository)
        assertEquals(EducationResult.Fallback(EducationFallback.UNCERTAIN), boundedAsk(""))
        assertEquals(EducationResult.Fallback(EducationFallback.UNCERTAIN), boundedAsk("a".repeat(501)))
        // Direct library browsing remains available if the model cannot load.
        assertEquals(EducationTopic.WEIGHT_TRACKING, boundedAsk.read(EducationTopic.WEIGHT_TRACKING).entry.topic)
    }
}
