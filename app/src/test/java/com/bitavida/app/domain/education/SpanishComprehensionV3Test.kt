package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.data.education.EducationModelCodec
import com.bitavida.app.domain.usecase.AskBitavidaAi
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpanishComprehensionV3Test {
    private val classifier = MultinomialNaiveBayes(
        EducationModelCodec.decode(projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText()),
    )

    @Test fun allSixUserVariantsSelectTheSameTopicDirectly() = runBlocking {
        val ask = AskBitavidaAi(BundledEducationRepository {
            projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText()
        })
        // These are functional acceptance examples, not the hidden generalization audit.
        listOf(
            "¿Por qué medir la presión?",
            "Por que medir la presion",
            "porque medir la presión",
            "Para que sirve medir la presion",
            "porq medir presion",
            "¿Por qué debo checarme la presión?",
        ).forEach { question ->
            assertEquals(question, EducationTopic.PRESSURE_PURPOSE, classifier.classify(question).topic)
            val response = ask(question)
            assertTrue(question, response is EducationResult.Answer)
            assertEquals(EducationTopic.PRESSURE_PURPOSE, (response as EducationResult.Answer).entry.topic)
            assertFalse(response.fromLibrary)
        }
    }

    @Test fun functionalAbbreviationsUnicodeAndPunctuationProduceEquivalentFeatures() {
        val canonical = EducationTokenizer.tokens("¿Por qué medir la presión?")
        listOf(
            "POR QUE MEDIR LA PRESION",
            "  porque   medir la presión...  ",
            "porq medir presion",
            "pq medir presio\u0301n",
            "xq medir presión",
            "porke medir presion",
            "por ke medir presion",
            "\uFEFFｐｏｒｑ ｍｅｄｉｒ ｌａ ｐｒｅｓｉｏｎ",
            "por que medir la pre\u200Bsión",
        ).forEach { assertEquals(it, canonical, EducationTokenizer.tokens(it)) }
        assertEquals(
            EducationTokenizer.tokens("Que significan las cifras"),
            EducationTokenizer.tokens("¿QUÉ SIGNIﬁCAN LAS CIFRAS?"),
        )
    }

    @Test fun orderLightTyposColloquialAndIncompleteQuestionsDoNotNeedAnExactCatalogSentence() {
        val rows = listOf(
            EducationTopic.PRESSURE_PURPOSE to "La presion por que medirla",
            EducationTopic.PRESSURE_PURPOSE to "medir presion para que sirve",
            EducationTopic.PRESSURE_PURPOSE to "por qué medir la prseion",
            EducationTopic.PRESSURE_PURPOSE to "pq medir la precion",
            EducationTopic.PRESSURE_PURPOSE to "¿Para qué sirve checar la tensión?",
            EducationTopic.GLUCOSE_PURPOSE to "En qué ayuda medirse el azúcar",
            EducationTopic.WEIGHT_TRACKING to "para que llevar registro de kilos",
            EducationTopic.WEIGHT_BMI to "imc que es",
            EducationTopic.MEASUREMENT_CORRECTIONS to "corregir registro",
            EducationTopic.WEIGHT_CONDITIONS to "misma bascula",
        )
        val training = projectFile("tools/bitavida-ai/training.tsv").readLines()
            .map { EducationTokenizer.normalize(it.substringAfter('\t')) }.toSet()
        rows.forEach { (topic, question) ->
            assertFalse("This paraphrase must not be a literal normalized training copy", EducationTokenizer.normalize(question) in training)
            assertEquals(question, topic, classifier.classify(question).topic)
        }
    }

    @Test fun punctuationAndCaseInvarianceAlsoHoldAcrossOtherEducationalCategories() {
        listOf(
            EducationTopic.GLUCOSE_PURPOSE to "Para qué sirve medir el azúcar",
            EducationTopic.PRESSURE_NUMBERS to "Qué significan los dos números de la presión",
            EducationTopic.WEIGHT_TRACKING to "Por qué registrar mi peso",
            EducationTopic.WEIGHT_BMI to "Qué es el IMC",
        ).forEach { (topic, question) ->
            val normalized = EducationTokenizer.normalize(question)
            listOf(question, normalized, "¿${question.uppercase()}?", "  $normalized !!!").forEach {
                assertEquals(it, topic, classifier.classify(it).topic)
            }
        }
    }

    @Test fun ambiguityAndUnrelatedQuestionsRemainRecoverableRejections() {
        val ambiguous = classifier.classify("Qué es glucosa o presión")
        assertNull(ambiguous.topic)
        assertEquals(setOf(EducationTopic.GLUCOSE_WHAT, EducationTopic.PRESSURE_WHAT), ambiguous.suggestions.toSet())
        listOf(
            "Cuál es la mejor marca de medidor",
            "Puedes decirme la temperatura",
            "Quiero saber qué tiempo hará mañana",
            "Cómo reparar la pila del glucómetro",
        ).forEach { assertNull(it, classifier.classify(it).topic) }
    }

    private fun projectFile(path: String): File = File(path).takeIf { it.isFile } ?: File("..", path)
}
