package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import org.junit.Assert.*
import org.junit.Test

/** Inventory of loaded entries and metadata, not a measure of editorial or clinical quality. */
class EducationalInventoryTest {
    @Test fun bundledCatalogCountsEntriesAndDocumentsCurationState() {
        val repository = BundledEducationRepository(loadModelText = { error("No model needed") })
        val general = EducationTopic.entries.map(repository::entry)
        val drugs = repository.drugs()
        val procedures = repository.procedures()
        assertEquals(EducationTopic.entries.size, general.size)
        assertEquals(drugs.size, drugs.map { it.id }.distinct().size)
        assertEquals(procedures.size, procedures.map { it.id }.distinct().size)
        println("General entries: ${general.size}")
        general.groupingBy { it.topic.group }.eachCount().forEach { (group, count) ->
            println("${group.name}: $count")
        }
        println("Drug entries: ${drugs.size}")
        println("Drug entries with curated related questions: ${drugs.count { it.relatedQuestions.isNotEmpty() }}")
        val undated = drugs.filter { drug -> drug.sources.any { it.reviewedOn == "Fecha no documentada" } }
        println("Drug entries with undocumented source date: ${undated.size}")
        undated.forEach { println("Undated drug source: ${it.id}") }
        println("Procedure entries: ${procedures.size}")
        println("Procedure entries with curated related questions: ${procedures.count { it.relatedQuestions.isNotEmpty() }}")
        println("Procedure entries with documented editorial date: ${procedures.count { it.reviewedOn != "Fecha no documentada" }}")
        procedures.filter { it.reviewedOn == "Fecha no documentada" }
            .forEach { println("Undated procedure review: ${it.id}") }
        println("Procedure entries pending clinical review: ${procedures.count { it.reviewStatus == EducationReviewStatus.PENDING_HUMAN_REVIEW }}")
    }
}
