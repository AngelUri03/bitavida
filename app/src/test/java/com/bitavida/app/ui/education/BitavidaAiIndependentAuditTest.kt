package com.bitavida.app.ui.education

import com.bitavida.app.data.education.EducationModelCodec
import com.bitavida.app.domain.education.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Root-authored audit locked before observing the expanded classifier. Never training data. */
class BitavidaAiIndependentAuditTest {
    @Test fun independentAuditReportsErrorsAndRejectionsSeparately() {
        val parameters = EducationModelCodec.decode(projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText())
        val classifier = MultinomialNaiveBayes(parameters)
        val rows = checkNotNull(javaClass.getResourceAsStream("/education/audit.tsv"))
            .bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.map { it.split('\t') }.toList() }
        val training = projectFile("tools/bitavida-ai/training.tsv").readLines()
            .filter { it.isNotBlank() }.map { EducationTokenizer.normalize(it.substringAfter('\t')) }.toSet()
        assertEquals(EducationTopic.entries.filter { it.group != EducationGroup.MEDICATIONS && it.group != EducationGroup.OBSERVATIONS && it.group != EducationGroup.REMINDERS }.toSet(), rows.map { EducationTopic.valueOf(it[0]) }.toSet())
        rows.forEach { assertFalse(EducationTokenizer.normalize(it[1]) in training) }
        val results = rows.map { row -> EducationTopic.valueOf(row[0]) to classifier.classify(row[1]) }
        val correct = results.count { (expected, result) -> result.topic == expected }
        val rejected = results.count { it.second.topic == null }
        val wrong = results.size - correct - rejected
        println("Independent root audit: correct=$correct/30, rejected=$rejected, wrongAccepted=$wrong")
        // Conservative release floor fixed before running this audit, not a clinical quality claim.
        assertTrue("At least 70% correct coverage on this independent fixture", correct >= 21)
        assertTrue("At most three wrong accepted topics in this fixture", wrong <= 3)
        results.forEach { assertTrue(it.second.posterior.isFinite()) }
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
