package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.usecase.AskBitavidaAi
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Frozen independent synthetic audit; never used for training or confidence selection. */
class PharmacyIndependentAuditTest {
    @Test fun originalIndependentQuestionsRemainAReportedRegression() = audit("pharmacy_audit.tsv", 46)

    @Test fun newConfirmationSetIsEvaluatedAfterTheModelFreeze() = audit("pharmacy_confirmation_audit.tsv", 30)

    private fun audit(resource: String, expectedRows: Int) = runBlocking {
        val ask = AskBitavidaAi(BundledEducationRepository {
            projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText()
        })
        val training = projectFile("tools/bitavida-ai/training.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { EducationTokenizer.normalize(it.substringAfter('\t')) }.toSet()
        val rows = requireNotNull(javaClass.getResourceAsStream("/education/$resource"))
            .bufferedReader().use { reader -> reader.readLines() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
        var direct = 0
        var expectedDirect = 0
        var recoveredKnown = 0
        var choices = 0
        var unknown = 0
        var outside = 0
        var rejectedApproximate = 0
        // Historical holdouts stay unchanged. Their former typo suggestions now must abstain
        // because approximate spelling is not an authorized way to link a drug guide.
        val historicalTypos = setOf(
            "¿Para qué se emplea metfornina en diabetes?",
            "Explícame la función de empagliflosina.",
            "¿Qué reacciones adversas causa glibenclanida?",
            "¿Qué debo conocer de sitagliptna?",
            "¿Cómo funciona pioglitasona?",
            "¿Cuál es la finalidad de dapaglifozina?",
            "¿Qué tipo de sustancia es metfomina?",
            "Quiero conocer los efectos indeseables de linaglptina.",
        )
        val abstentions = mutableListOf<String>()
        val wrong = mutableListOf<String>()
        rows.forEach { line ->
            val columns = line.split('\t')
            assertEquals(4, columns.size)
            val (kind, ids, intent, question) = columns
            assertFalse("Audit must not duplicate training: $question",
                EducationTokenizer.normalize(question) in training)
            val result = ask(question)
            if (question in historicalTypos) {
                if (result is EducationResult.DrugOptions && result.candidates.isEmpty()) rejectedApproximate++
                else wrong += "$question => ${result.javaClass.simpleName}, expected abstention for approximate spelling"
                return@forEach
            }
            when (kind) {
                "ANSWER" -> {
                    expectedDirect++
                    when (result) {
                        is EducationResult.DrugAnswer -> if (
                            result.drug.id == ids && result.intent.name == intent
                        ) {
                            direct++
                            recoveredKnown++
                        } else wrong += "$question => ${result.drug.id}/${result.intent} expected $ids/$intent"
                        is EducationResult.DrugOptions -> {
                            if (result.candidates.any { it.id == ids }) recoveredKnown++
                            else wrong += "$question => missing documented ingredient $ids"
                            abstentions += "$question => ${result.javaClass.simpleName}"
                        }
                        is EducationResult.Answer -> wrong += "$question => ${result.entry.topic}"
                        else -> abstentions += "$question => ${result.javaClass.simpleName}"
                    }
                }
                "OPTIONS" -> if (result is EducationResult.DrugOptions &&
                    ids.split(';').all { id -> result.candidates.any { it.id == id } }) choices++
                    else wrong += "$question => ${result.javaClass.simpleName}, expected confirmation of $ids"
                "UNKNOWN" -> if ((result is EducationResult.Fallback && result.reason == EducationFallback.UNCERTAIN) ||
                    (result is EducationResult.DrugOptions && result.candidates.isEmpty())) unknown++
                    else wrong += "$question => ${result.javaClass.simpleName}, expected unknown"
                "OUTSIDE_SCOPE" -> if (result is EducationResult.Fallback &&
                    result.reason == EducationFallback.OUTSIDE_SCOPE) outside++
                    else wrong += "$question => ${result.javaClass.simpleName}, expected scope rejection"
                else -> error("Unknown audit kind")
            }
        }
        val expectedChoices = rows.count { it.substringBefore('\t') == "OPTIONS" &&
            it.substringAfterLast('\t') !in historicalTypos }
        val expectedRejected = rows.count { it.substringAfterLast('\t') in historicalTypos }
        assertEquals(expectedRejected, rejectedApproximate)
        val expectedUnknown = rows.count { it.substringBefore('\t') == "UNKNOWN" }
        val expectedOutside = rows.count { it.substringBefore('\t') == "OUTSIDE_SCOPE" }
        println("PHARMACY_AUDIT $resource direct=$direct/$expectedDirect choices=$choices/$expectedChoices approximateRejected=$rejectedApproximate/$expectedRejected unknown=$unknown/$expectedUnknown outside=$outside/$expectedOutside recovered=$recoveredKnown/$expectedDirect")
        abstentions.forEach { println("ABSTENTION $it") }
        wrong.forEach { println("WRONG $it") }
        assertEquals("Wrong accepted content or missing safety/confirmation: ${wrong.joinToString()}", emptyList<String>(), wrong)
        // The catalogue must be reached without a manual search even when intention is uncertain.
        // Direct intent coverage is reported separately, rather than hiding abstentions in accuracy.
        assertEquals("Every known ingredient must be directly answered or explicitly offered", expectedDirect, recoveredKnown)
        assertEquals(expectedRows, rows.size)
    }

    private fun projectFile(path: String): File = File(path).takeIf { it.isFile } ?: File("..", path)
}
