package com.bitavida.app.domain.education

import com.bitavida.app.data.education.EducationModelCodec
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MultinomialNaiveBayesTest {
    private val artifact = projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText()
    private val parameters = EducationModelCodec.decode(artifact)
    private val classifier = MultinomialNaiveBayes(parameters)
    private val diagnostic = resourceRows("/education/evaluation.tsv")

    @Test fun diagnosticMeasuresParaphrasesColloquialQuestionsAndSpellingWithoutHidingRejections() {
        val known = diagnostic.filter { it[0] != "__UNKNOWN__" }
        val results = known.map { (expected, question) -> expected to classifier.classify(question) }
        val correct = results.count { (expected, result) -> result.topic?.name == expected }
        val wrong = results.count { (expected, result) -> result.topic != null && result.topic.name != expected }
        assertEquals(90, known.size)
        assertTrue("Diagnostic correct coverage must remain at least 70%", correct >= 63)
        assertTrue("At most 5/90 accepted mistakes in this deliberately difficult diagnostic", wrong <= 5)
        val typoResults = known.filterIndexed { index, _ -> index % 3 == 2 }
        val typoCorrect = typoResults.count { (expected, question) -> classifier.classify(question).topic?.name == expected }
        assertTrue("Most typo formulations must be recognized", typoCorrect >= 21)
        println("Bitavida IA V3 diagnostic: correct=$correct/90, wrong=$wrong, rejected=${90-correct-wrong}, typoCorrect=$typoCorrect/30")
    }

    @Test fun trainingCalibrationAndDiagnosticHaveSeparateQuestions() {
        val training = projectFile("tools/bitavida-ai/training.tsv").readLines().filter { it.isNotBlank() }
        val calibration = projectFile("tools/bitavida-ai/calibration.tsv").readLines().filter { it.isNotBlank() }
        fun key(line: String) = EducationTokenizer.normalize(line.substringAfter('\t')).replace(Regex("[¿?.,]"), "").trim()
        val trainingQuestions = training.map(::key).toSet()
        val calibrationQuestions = calibration.map(::key).toSet()
        assertTrue("Deduplicated corpus must retain at least 500 distinct formulations", training.size >= 500)
        assertEquals(training.size, training.map(::key).toSet().size)
        assertEquals(70, calibration.size)
        assertTrue(trainingQuestions.intersect(calibrationQuestions).isEmpty())
        diagnostic.forEach { (_, question) ->
            val normalized = key("label\t$question")
            assertFalse(normalized in trainingQuestions)
            assertFalse(normalized in calibrationQuestions)
        }
    }

    @Test fun packagedStatisticsAreExactlyReproducibleWithoutTrainingAtRuntime() {
        val labels = parameters.topics.map { it.name } + "__UNKNOWN__"
        val training = projectFile("tools/bitavida-ai/training.tsv").readLines().filter { it.isNotBlank() }
        val documents = MutableList(labels.size) { 0 }
        val counts = sortedMapOf<String, MutableList<Int>>()
        training.forEach { line ->
            val (label, question) = line.split('\t')
            val index = labels.indexOf(label)
            assertTrue(index >= 0)
            documents[index]++
            EducationTokenizer.features(EducationTokenizer.tokens(question)).forEach { feature ->
                val row = counts.getOrPut(feature) { MutableList(labels.size) { 0 } }
                row[index]++
            }
        }
        assertEquals(documents, parameters.documentCounts)
        assertEquals(counts, parameters.tokenCounts)
        assertEquals(69, parameters.topics.size)
        assertEquals(0.1, parameters.alpha, 0.0)
        assertTrue(artifact.toByteArray(Charsets.UTF_8).size < 1_200 * 1024)
    }

    @Test fun independentCalibrationRetainsConservativeRejection() {
        val rows = projectFile("tools/bitavida-ai/calibration.tsv").readLines().map { it.split('\t') }
        val wrong = rows.count { (expected, q) ->
            val actual = classifier.classify(q).topic?.name
            actual != null && actual != expected
        }
        assertEquals(0, wrong)
    }

    @Test fun unknownAndAmbiguousDiagnosticQuestionsDoNotInventAnswers() {
        val unknown = diagnostic.filter { it[0] == "__UNKNOWN__" }
        assertEquals(20, unknown.size)
        unknown.forEach { (_, question) -> assertNull(question, classifier.classify(question).topic) }
        val ambiguity = classifier.classify("Qué es glucosa o presión")
        assertNull(ambiguity.topic)
        assertTrue(ambiguity.suggestions.size in 2..3)
    }

    @Test fun allUserExamplesHaveRealLearnedPredictions() {
        resourceRows("/education/acceptance.tsv").forEach { (expected, question) ->
            val prediction = classifier.classify(question)
            val topic = EducationTopic.valueOf(expected)
            assertTrue(question, prediction.topic == topic ||
                prediction.topic == null && topic in prediction.suggestions)
        }
    }

    @Test fun observationParaphrasesTyposAndUnknownQuestionsRemainConservative() {
        resourceRows("/education/observation_acceptance.tsv").forEach { (expected, question) ->
            val prediction = classifier.classify(question)
            if (expected == "__UNKNOWN__") assertNull(question, prediction.topic)
            else assertEquals(question, EducationTopic.valueOf(expected), prediction.topic)
        }
    }

    @Test fun accentsAndCapitalizationAreEquivalentAndRepeatedWordsCannotForceCertainty() {
        val first = classifier.classify("Que significan los numeros de la presion").topic
        assertEquals(first, classifier.classify("¿QUÉ SIGNIFICAN LOS NÚMEROS DE LA PRESIÓN?").topic)
        assertEquals(EducationTopic.PRESSURE_NUMBERS, first)
        assertNull(classifier.classify("glucosa ".repeat(40)).topic)
        assertNull(classifier.classify("presión arterial ".repeat(40)).topic)
        assertNull(classifier.classify("¿? 🧵 ✦").topic)
        assertNull(classifier.classify("a".repeat(501)).topic)
    }

    @Test fun malformedOrMismatchedArtifactsAreRejected() {
        val lines = artifact.lines().toMutableList()
        lines[2] = "-1" + lines[2].substringAfter('\t').let { "\t$it" }
        listOf(
            "", artifact.replace("BITAVIDA_MNB", "OTHER"),
            artifact.replace("\t3\t0.1", "\t99\t0.1"), lines.joinToString("\n"),
            artifact + artifact.lineSequence().drop(3).first() + "\n",
        ).forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) { EducationModelCodec.decode(malformed) }
        }
    }

    private fun resourceRows(path: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream(path)).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { it.split('\t') }.toList()
        }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
