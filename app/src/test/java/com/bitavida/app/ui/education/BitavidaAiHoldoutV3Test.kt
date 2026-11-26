package com.bitavida.app.ui.education

import com.bitavida.app.data.education.EducationModelCodec
import com.bitavida.app.domain.education.EducationTopic
import com.bitavida.app.domain.education.MultinomialNaiveBayes
import java.io.File
import java.text.Normalizer
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Prepared by a different author and hidden from model development until parameter freeze. */
class BitavidaAiHoldoutV3Test {
    @Test fun separateQuestionsReportCorrectAnswersAbstentionsAndAcceptedMistakes() {
        val rows = checkNotNull(javaClass.getResourceAsStream("/education/audit_v3.tsv"))
            .bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.map { it.split('\t') }.toList() }
        val classifier = MultinomialNaiveBayes(EducationModelCodec.decode(
            projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText(),
        ))
        val known = rows.filter { it[0] != "__UNKNOWN__" }
        val unknown = rows.filter { it[0] == "__UNKNOWN__" }
        assertEquals(60, known.size)
        assertEquals(20, unknown.size)
        assertEquals(EducationTopic.entries.filter { it.group != com.bitavida.app.domain.education.EducationGroup.MEDICATIONS && it.group != com.bitavida.app.domain.education.EducationGroup.OBSERVATIONS && it.group != com.bitavida.app.domain.education.EducationGroup.REMINDERS }.toSet(), known.map { EducationTopic.valueOf(it[0]) }.toSet())
        val results = known.map { row -> row[0] to classifier.classify(row[1]) }
        val correct = results.count { (expected, prediction) -> prediction.topic?.name == expected }
        val rejected = results.count { it.second.topic == null }
        val wrong = known.size - correct - rejected
        val acceptedUnknown = unknown.map { it[1] to classifier.classify(it[1]) }.filter { it.second.topic != null }
        acceptedUnknown.forEach { println("V3 UNKNOWN ACCEPTED: ${it.first} => ${it.second.topic}") }
        val wrongUnknown = acceptedUnknown.size
        println("V3 holdout: correct=$correct/60, rejected=$rejected, wrongAccepted=$wrong, unknownAccepted=$wrongUnknown/20")
        // Floors fixed before inspecting this dataset's predictions, not clinical performance claims.
        assertTrue("At least 75% correct coverage", correct >= 45)
        assertTrue("At most three accepted mistakes in this synthetic fixture", wrong <= 3)
        assertEquals("Unknown or ambiguous questions must not retrieve a confirmed answer", 0, wrongUnknown)
    }

    @Test fun heldOutQuestionsAreDistinctFromTrainingAndCalibration() {
        val heldOut = checkNotNull(javaClass.getResourceAsStream("/education/audit_v3.tsv"))
            .bufferedReader().useLines { it.filter(String::isNotBlank).toList() }
        val training = projectFile("tools/bitavida-ai/training.tsv").readLines().filter(String::isNotBlank).map(::key).toSet()
        val calibration = projectFile("tools/bitavida-ai/calibration.tsv").readLines().filter(String::isNotBlank).map(::key).toSet()
        assertEquals(80, heldOut.map(::key).toSet().size)
        heldOut.forEach { row ->
            assertFalse("No literal overlap with training", key(row) in training)
            assertFalse("No literal overlap with calibration", key(row) in calibration)
        }
    }

    private fun key(row: String) = Normalizer.normalize(row.substringAfter('\t').lowercase(Locale.ROOT),
        Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
