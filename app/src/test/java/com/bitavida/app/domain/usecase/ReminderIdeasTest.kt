package com.bitavida.app.domain.usecase

import com.bitavida.app.data.education.ReminderEducationLibrary
import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.model.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderIdeasTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)

    @Test fun creationPickerReadsTheExistingCatalogueWithoutInferenceOrClinicalAccess() {
        val repository = object : EducationRepository {
            override suspend fun classify(question: String): IntentPrediction = error("No inference needed for browsing")
            override fun entry(topic: EducationTopic) = ReminderEducationLibrary.entries.getValue(topic)
            override fun drugs(): List<DrugEducation> = error("No clinical access")
        }
        val groups = reminderIdeaGroups(AskBitavidaAi(repository))
        assertEquals(ReminderEducationCategory.entries.toSet(), groups.map { it.category }.toSet())
        assertEquals(ReminderEducationCategory.SHOPPING, groups.first().category)
        assertTrue(groups.all { it.ideas.isNotEmpty() })
        assertTrue(groups.all { group -> group.ideas.map { it.title }.distinct().size == group.ideas.size })
        val related = matchingReminderIdeas(groups, ReminderEducationCategory.SHOPPING, "papeles")
        assertTrue(related.isNotEmpty())
        assertTrue(related.all { EducationTokenizer.normalize(it.title).contains("document") })
        assertTrue(matchingReminderIdeas(groups, ReminderEducationCategory.SHOPPING, "baterias").isNotEmpty())
        assertTrue(matchingReminderIdeas(groups, ReminderEducationCategory.SHOPPING, "zzzzqqqq").isEmpty())
    }

    @Test fun genericWordsAloneNeverRaiseApproximateDuplicateWarnings() {
        val records = listOf(record("generic", "Comprar artículos"), record("other", "Organizar cosas"))
        assertTrue(similarActiveCareReminders("Comprar artículos del cuidado", records, "person-example").isEmpty())
        assertTrue(similarActiveCareReminders("Organizar cosas pendientes", records, "person-example").isEmpty())
    }

    @Test fun titleComparisonToleratesDeclaredSynonymsAndTyposButKeepsPatientsAndHistoryIsolated() {
        val records = listOf(
            record("match", "Comprar pilas para el glucómetro"),
            record("closed", "Comprar baterias del glucometro").copy(status = CareReminderStatus.DONE, closedAt = now),
            record("other-person", "Comprar baterias del glucometro").copy(personUnderCareId = "other-example"),
            record("different", "Comprar documentos para el traslado"))
        assertEquals(listOf("match"),
            similarActiveCareReminders("Comprar baterías del glucometro", records, "person-example").map { it.id })
        assertEquals(listOf("match"),
            similarActiveCareReminders("Comprar pilas para el glucometor", records, "person-example").map { it.id })
        assertTrue(similarActiveCareReminders("Comprar pilas del glucometro", records, "person-example", "match").isEmpty())
        assertTrue(similarActiveCareReminders("Comprar bolsas de otro material", records, "person-example").isEmpty())
    }

    private fun record(id: String, title: String) = CareReminder(id, "person-example", title, null,
        now.plusHours(1), false, CareReminderStatus.PENDING, now, now, null, 1)
}
