package com.bitavida.app.ui.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.usecase.AskBitavidaAi
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Functional requirements are regression cases; the hidden V3 audit measures generalization. */
class BitavidaAiNaturalLanguageContractTest {
    private val ask = AskBitavidaAi(BundledEducationRepository {
        projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText()
    })

    @Test fun equivalentPressureQuestionsRecoverTheSameEntryThroughTheRealUseCase() = runBlocking {
        val variants = listOf(
            "¿Por qué medir la presión?",
            "Por que medir la presion",
            "porque medir la presión",
            "Para que sirve medir la presion",
            "porq medir presion",
            "¿Por qué debo checarme la presión?",
        )
        variants.forEach { question ->
            val result = ask(question)
            assertTrue("Must directly answer the functional regression: $question", result is EducationResult.Answer)
            val answer = result as EducationResult.Answer
            assertEquals(EducationTopic.PRESSURE_PURPOSE, answer.entry.topic)
            assertFalse(answer.fromLibrary)
        }
    }

    @Test fun accentsPunctuationUnicodeAndWhitespaceDoNotChangeTheSameQuestion() = runBlocking {
        val variants = listOf(
            "  POR QUÉ   MEDIR LA PRESIÓN  ",
            "¡Por qué medir la presión!",
            "¿Por que\nmedir\tla presion???",
            "Por que medir la presio\u0301n",
            "Ｐｏｒ ｑｕｅ ｍｅｄｉｒ ｌａ ｐｒｅｓｉｏｎ",
        )
        variants.forEach { question ->
            val result = ask(question)
            assertTrue(question, result is EducationResult.Answer)
            assertEquals(EducationTopic.PRESSURE_PURPOSE, (result as EducationResult.Answer).entry.topic)
        }
    }

    @Test fun explicitPersonalClinicalRequestsRemainOutsideTheEducationalScope() = runBlocking {
        listOf(
            "Qué dosis tomar para mi presión",
            "Lectura ficticia de presión 111/77 qué hago",
            "Me duele el pecho",
        ).forEach {
            assertEquals(EducationResult.Fallback(EducationFallback.OUTSIDE_SCOPE), ask(it))
        }
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
