package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

/** Mechanical triage only; it cannot establish clinical validity or comprehension. */
class GeneralEducationEditorialAuditTest {
    private data class Finding(val topic: EducationTopic, val field: String, val reason: String)

    private fun audit(entries: List<EducationEntry>): List<Finding> {
        val findings = mutableListOf<Finding>()
        val occurrences = mutableMapOf<String, MutableList<Pair<EducationTopic, String>>>()
        val lowValue = setOf(
            "consulta de atencion medica", "procedimiento medico especializado",
            "el equipo revisa al paciente", "sirve para evaluar al paciente",
        )
        entries.forEach { entry ->
            val fields = listOf("explanation" to entry.explanation) +
                entry.details.mapIndexed { index, value -> "detail-$index" to value }
            fields.forEach { (field, value) ->
                val normalized = EducationTokenizer.normalize(value)
                if (normalized.isBlank()) findings += Finding(entry.topic, field, "empty")
                if (normalized in lowValue) findings += Finding(entry.topic, field, "generic")
                if (normalized.isNotBlank()) occurrences.getOrPut(normalized) { mutableListOf() }
                    .add(entry.topic to field)
            }
            if (entry.source.title.isBlank() || entry.source.reference.isBlank())
                findings += Finding(entry.topic, "source", "missing-source")
            if (entry.source.reference.startsWith("http") &&
                !entry.source.reference.startsWith("https://"))
                findings += Finding(entry.topic, "source", "insecure-url")
            if (entry.source.reference.startsWith("https://") &&
                entry.source.consultedOn == "Fecha no documentada")
                findings += Finding(entry.topic, "source", "undocumented-consultation")
            if (entry.related.any { it == entry.topic })
                findings += Finding(entry.topic, "related", "self-reference")
        }
        occurrences.values.filter { it.size > 1 }.forEach { repeated ->
            repeated.forEach { (topic, field) -> findings += Finding(topic, field, "repeated") }
        }
        return findings
    }

    @Test fun detectsGenericContentMissingSourcesAndRepeatedSections() {
        val repository = BundledEducationRepository(loadModelText = { error("No classifier needed") })
        val original = repository.entry(EducationTopic.GLUCOSE_WHAT)
        val fixture = original.copy(
            explanation = "Sirve para evaluar al paciente.",
            details = listOf("La misma explicación.", "La misma explicación."),
            source = EducationSource("", ""),
        )
        val problems = audit(listOf(fixture))
        assertTrue(problems.any { it.reason == "generic" })
        assertTrue(problems.any { it.reason == "missing-source" })
        assertTrue(problems.count { it.reason == "repeated" } == 2)
    }

    @Test fun reportsAllGeneralLibrariesForHumanEditorialReview() {
        val repository = BundledEducationRepository(loadModelText = { error("No classifier needed") })
        val entries = EducationTopic.entries.map(repository::entry)
        assertEquals(EducationTopic.entries.size, entries.size)
        val findings = audit(entries)
        println("Diagnóstico de guías generales: ${findings.size} hallazgos para revisión humana")
        findings.forEach { println("${it.topic.name}\t${it.field}\t${it.reason}") }
        // Findings are recorded rather than hidden or falsely labelled clinically validated.
    }
}
