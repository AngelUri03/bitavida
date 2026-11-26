package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * A diagnostic, not a clinical validator: flags observable editorial patterns for human review.
 * Legacy entries are reported, not silently declared safe or made to pass a word-count quota.
 */
class ProcedureEditorialAuditTest {
    private val guides = BundledEducationRepository(loadModelText = { "" }).procedures()
    private val boilerplate = setOf(
        "procedimiento medico especializado",
        "prueba de imagen ocular",
        "se realiza mediante equipo especializado",
        "el medico determina el seguimiento",
        "el equipo revisa los resultados",
        "sirve para evaluar al paciente",
    )

    private data class Finding(val guide: String, val section: String, val reason: String)

    private fun audit(input: List<ProcedureGuide>): List<Finding> {
        val findings = mutableListOf<Finding>()
        val texts = mutableMapOf<String, MutableList<Pair<String, String>>>()
        val questions = mutableMapOf<String, MutableSet<String>>()
        input.forEach { guide ->
            val fields = linkedMapOf(
                "what" to guide.description, "purpose" to guide.purpose,
                "during" to guide.during, "method" to guide.method,
                "preparation" to guide.preparation, "after" to guide.after,
                "important" to guide.important,
            ) + guide.extraSections.associate { it.id to it.body }
            fields.forEach { (section, body) ->
                val normalized = EducationTokenizer.normalize(body)
                if (normalized.isBlank()) findings += Finding(guide.id, section, "empty")
                if (normalized in boilerplate) findings += Finding(guide.id, section, "generic")
                // A short section is a review signal, not proof of poor quality.
                if (normalized.isNotBlank() && normalized.length < 70)
                    findings += Finding(guide.id, section, "short-review")
                if (normalized.isNotBlank()) texts.getOrPut(normalized) { mutableListOf() }.add(guide.id to section)
            }
            guide.questions.forEach { question ->
                questions.getOrPut(EducationTokenizer.normalize(question)) { mutableSetOf() }.add(guide.id)
            }
            if (guide.relatedQuestions.isEmpty())
                findings += Finding(guide.id, "related", "uses-generic-related-questions")
            guide.relatedQuestions.forEach { question ->
                questions.getOrPut(EducationTokenizer.normalize(question.question)) { mutableSetOf() }.add(guide.id)
                if (question.id.isBlank() || question.question.isBlank() ||
                    procedureSectionText(guide, question.sectionId) == null) {
                    findings += Finding(guide.id, "related", "invalid-related-question")
                }
            }
            if (guide.relatedQuestions.map { it.id }.distinct().size != guide.relatedQuestions.size)
                findings += Finding(guide.id, "related", "duplicate-related-id")
            if (!guide.source.reference.startsWith("https://"))
                findings += Finding(guide.id, "source", "invalid-url")
            val sources = (listOf(guide.source) + guide.additionalSources).map { it.reference }.toSet()
            val linked = guide.sectionSourceReferences.values.flatten() +
                guide.extraSections.flatMap { it.sourceReferences }
            if (linked.any { it !in sources }) findings += Finding(guide.id, "source", "unresolved-reference")
        }
        texts.values.filter { it.size > 1 }.forEach { occurrences ->
            occurrences.forEach { (id, section) -> findings += Finding(id, section, "repeated") }
        }
        questions.values.filter { it.size > 1 }.forEach { ids ->
            ids.forEach { findings += Finding(it, "questions", "repeated-question") }
        }
        return findings
    }

    @Test fun auditDetectsDefectsAndReportsLegacyFindingsWithoutApprovingThem() {
        val oct = guides.single { it.id == "oct" }
        val bad = oct.copy(id = "fixture", method = "Prueba de imagen ocular.", source = oct.source.copy(reference = ""))
        val defects = audit(listOf(bad))
        assertTrue(defects.any { it.guide == "fixture" && it.reason == "generic" })
        assertTrue(defects.any { it.guide == "fixture" && it.section == "method" && it.reason == "short-review" })
        assertTrue(defects.any { it.guide == "fixture" && it.reason == "invalid-url" })
        assertTrue(audit(listOf(oct.copy(relatedQuestions = emptyList())))
            .any { it.reason == "uses-generic-related-questions" })
        val octFindings = audit(listOf(oct))
        assertTrue(octFindings.none { it.reason in setOf("empty", "generic", "invalid-url", "unresolved-reference") })

        val legacy = audit(guides)
        println("Diagnóstico editorial: ${legacy.size} hallazgos para revisión humana")
        legacy.forEach { println("${it.guide}\t${it.section}\t${it.reason}") }
    }
}
