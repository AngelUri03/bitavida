package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.data.education.EducationModelCodec
import com.bitavida.app.data.education.ReminderEducationLibrary
import com.bitavida.app.domain.usecase.AskBitavidaAi
import com.bitavida.app.domain.usecase.AssistReminderTitle
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReminderEducationTest {
    private fun sourceFile(path: String): File = listOf(File(path), File("app", path))
        .first { it.isFile }
    private fun model(name: String) = sourceFile("src/main/res/raw/$name").readText()
    private fun repository() = BundledEducationRepository(
        loadModelText = { model("bitavida_education_model.tsv") },
        loadReminderModelText = { model("bitavida_reminder_model.tsv") },
    )

    @Test fun libraryHasAtLeastTwentyNonclinicalTopicsAcrossSevenBrowsingCategories() {
        val entries = ReminderEducationLibrary.entries.values
        assertTrue(entries.size >= 20)
        assertEquals(ReminderEducationCategory.entries.toSet(), entries.map { it.reminderCategory }.toSet())
        assertEquals(EducationTopic.entries.filter { it.group == EducationGroup.REMINDERS }.toSet(),
            entries.map { it.topic }.toSet())
        entries.forEach { entry ->
            assertTrue(entry.title.isNotBlank())
            assertTrue(entry.explanation.isNotBlank())
            assertTrue(entry.details.isNotEmpty())
            assertTrue(entry.reminderExamples.size in 1..3)
            assertTrue(entry.reminderExamples.all { it.title.isNotBlank() && it.explanation.isNotBlank() })
            assertTrue(entry.related.all { it.group == EducationGroup.REMINDERS && it != entry.topic })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, entry.reviewStatus)
        }
    }

    @Test fun localSourceDoesNotInventAConsultationDate() {
        ReminderEducationLibrary.entries.values.forEach { entry ->
            assertEquals("Fecha no documentada", entry.source.consultedOn)
        }
    }

    @Test fun consultationPreparationAndReminderUseAreAvailableOfflineWithDistinctPurposes() {
        val repository = repository()
        val outing = repository.entry(EducationTopic.REMINDER_OUTING)
        val appUse = repository.entry(EducationTopic.REMINDER_APP_USE)

        assertTrue(outing.details.any { it.contains("preguntas") })
        assertTrue(outing.details.any { it.contains("no interpreta") })
        assertTrue(outing.additionalSources.any { it.reference.contains("nia.nih.gov") })
        assertTrue(appUse.explanation.contains("no se envía una notificación"))
        assertTrue(appUse.details.any { it.contains("Pasar la fecha") })
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, outing.reviewStatus)
    }

    @Test fun organizationalGuidesExplainActualReminderDecisionsAndPreserveCaregiverAgency() {
        val repository = repository()
        val past = repository.entry(EducationTopic.REMINDER_FORGOTTEN)
        val review = repository.entry(EducationTopic.REMINDER_REVIEW)
        val postpone = repository.entry(EducationTopic.REMINDER_POSTPONE)
        val family = repository.entry(EducationTopic.REMINDER_FAMILY)
        val handoff = repository.entry(EducationTopic.REMINDER_HANDOFF)
        val modules = repository.entry(EducationTopic.REMINDER_MODULES)

        assertTrue(past.details.any { it.contains("siguen activos") && it.contains("Recordatorios anteriores") })
        assertTrue(review.details.any { it.contains("no se convierte automáticamente") })
        assertTrue(postpone.details.any { it.contains("guardar el cambio de inmediato") })
        assertTrue(postpone.details.any { it.contains("Guardar nueva fecha") })
        assertFalse(postpone.details.any { it.contains("revisar la fecha antes de guardar") })
        assertTrue(family.details.any { it.contains("no envía una invitación") })
        assertTrue(handoff.details.any { it.contains("no manda el texto") })
        assertTrue(modules.details.any { it.contains("no confirma por sí solo") })
        listOf(family, handoff).forEach { entry ->
            assertTrue(entry.additionalSources.any {
                it.reference == "https://www.nia.nih.gov/health/caregiving/sharing-caregiving-responsibilities"
            })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, entry.reviewStatus)
        }
    }

    @Test fun preparationAndShoppingGuidesDistinguishDraftsFromCompletedActions() {
        val repository = repository()
        val preparation = repository.entry(EducationTopic.REMINDER_PREPARATION)
        val documents = repository.entry(EducationTopic.REMINDER_DOCUMENTS)
        val shopping = repository.entry(EducationTopic.REMINDER_SHOPPING)
        val supplies = repository.entry(EducationTopic.REMINDER_SUPPLIES)
        val battery = repository.entry(EducationTopic.REMINDER_BATTERIES)
        val title = repository.entry(EducationTopic.REMINDER_CLEAR_TITLE)

        assertTrue(preparation.details.any { it.contains("no decide si se requiere ayuno") })
        assertTrue(preparation.additionalSources.any { it.reference.contains("how-prepare-doctors-appointment") })
        assertTrue(documents.details.any { it.contains("no envía copias") })
        assertTrue(documents.additionalSources.any { it.reference.contains("taking-someone-doctors-appointment") })
        assertTrue(shopping.details.any { it.contains("no hace pedidos") })
        assertTrue(supplies.details.any { it.contains("no inspecciona el hogar") })
        assertTrue(battery.details.any { it.contains("no compra pilas") })
        assertTrue(title.details.any { it.contains("puede completar el título del borrador") && it.contains("no guarda") })
        listOf(preparation, documents, shopping, supplies, battery, title).forEach {
            assertEquals("1.2-organizacion", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun routineGuidesExplainPendingActionsWithoutInventingExternalOperations() {
        val entries = ReminderEducationLibrary.entries
        val ideas = entries.getValue(EducationTopic.REMINDER_IDEAS)
        val routine = entries.getValue(EducationTopic.REMINDER_ROUTINE)
        val folder = entries.getValue(EducationTopic.REMINDER_FOLDER)
        val pickup = entries.getValue(EducationTopic.REMINDER_PICKUP)
        val call = entries.getValue(EducationTopic.REMINDER_CALL)
        val objects = entries.getValue(EducationTopic.REMINDER_OBJECTS)
        val smallTasks = entries.getValue(EducationTopic.REMINDER_SMALL_TASKS)

        assertTrue(ideas.details.any { it.contains("completar el título del borrador") })
        assertTrue(routine.details.any { it.contains("incluidos los que tienen la hora pasada") })
        assertTrue(folder.details.any { it.contains("no fotografía ni envía documentos") })
        assertTrue(pickup.details.any { it.contains("no comprueba horarios") })
        assertTrue(call.details.any { it.contains("no marca un número") })
        assertTrue(objects.details.any { it.contains("no comprueba por sí mismo") })
        assertTrue(smallTasks.details.any { it.contains("no fusiona ambos") })
        listOf(ideas, routine, folder, pickup, call, objects, smallTasks).forEach {
            assertEquals("1.2-organizacion", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun examplesAreOptionalTextWithoutClinicalSchedulesOrInventedPeople() {
        val examples = ReminderEducationLibrary.entries.values.flatMap { it.reminderExamples }
        assertEquals(examples.size, examples.map { it.id }.distinct().size)
        examples.forEach { example ->
            val text = EducationTokenizer.normalize(example.title)
            assertFalse(text, text.any(Char::isDigit))
            assertFalse(text, Regex("\\b(dosis|insulina|tomar|inyectar|tratamiento|procedimiento|medir)\\b").containsMatchIn(text))
            assertFalse(text, Regex("\\b(ana|maria|juan|hospital|clinica)\\b").containsMatchIn(text))
        }
    }

    @Test fun suggestionExplanationsDescribeDifferentPurposesRatherThanRepeatingOneInstruction() {
        val examples = ReminderEducationLibrary.entries.values.flatMap { it.reminderExamples }
        assertTrue(examples.map { it.explanation }.distinct().size >= 40)
        assertTrue(examples.all { it.explanation.length >= 25 && it.explanation != it.title })
        assertTrue(examples.none { it.explanation.startsWith("Idea opcional:") })
    }

    @Test fun contextualArtifactUsesTheSameMnbSchemaAndLeavesOriginalTopicSetSeparate() {
        val original = EducationModelCodec.decode(model("bitavida_education_model.tsv"))
        val reminders = EducationModelCodec.decode(model("bitavida_reminder_model.tsv"))
        assertEquals(69, original.topics.size)
        assertTrue(original.topics.none { it.group == EducationGroup.REMINDERS })
        assertEquals(22, reminders.topics.size)
        assertTrue(reminders.topics.all { it.group == EducationGroup.REMINDERS })
        assertTrue(original.tokenCounts.isNotEmpty())
        assertTrue(reminders.tokenCounts.isNotEmpty())
    }

    @Test fun paraphrasesAccentlessQuestionsAndTyposUseLearnedClassification() = runBlocking {
        val repo = repository()
        val rows = sourceFile("src/test/resources/education/reminders-evaluation.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        rows.filter { it[0] != "__UNKNOWN__" }.forEach { row ->
            val prediction = repo.classify(row[1], EducationGroup.REMINDERS)
            assertEquals(row[1] + " · " + prediction, EducationTopic.valueOf(row[0]), prediction.topic)
        }
    }

    @Test fun clinicalRequestsNeverReturnMedicalAnswersInReminderContext() = runBlocking {
        val ask = AskBitavidaAi(repository())
        listOf("Que dosis de insulina debo usar", "Interpreta mi glucosa de 180",
            "Que medicina debo tomar si tengo dolor", "Quiero cambiar la dosis de mis pastillas",
            "Que tratamiento me recomiendas", "Que procedimiento medico hay que realizar").forEach { text ->
            val result = ask(text, EducationGroup.REMINDERS)
            assertTrue(text, result is EducationResult.Fallback)
            assertEquals(text, EducationFallback.OUTSIDE_SCOPE, (result as EducationResult.Fallback).reason)
            assertTrue(result.suggestions.all { it.group == EducationGroup.REMINDERS })
        }
    }

    @Test fun moduleDifferenceIsEducationRatherThanKeywordBlocking() = runBlocking {
        val result = AskBitavidaAi(repository())(
            "Por que mis medicinas no deben registrarse en recordatorios", EducationGroup.REMINDERS)
        assertTrue(result is EducationResult.Answer)
        assertEquals(EducationTopic.REMINDER_MODULES, (result as EducationResult.Answer).entry.topic)
    }

    @Test fun uncertainRequestsOfferRelatedQuestionsInsteadOfInventingAnAnswer() = runBlocking {
        val result = AskBitavidaAi(repository())("zzzz qqqqq wwww", EducationGroup.REMINDERS)
        assertTrue(result is EducationResult.Fallback)
        assertEquals(EducationFallback.UNCERTAIN, (result as EducationResult.Fallback).reason)
        assertTrue(result.suggestions.isNotEmpty())
        assertTrue(result.suggestions.all { it.group == EducationGroup.REMINDERS })
    }

    @Test fun titleAssistanceIsCompactAndAsksForAnActionInsteadOfInventingOne() = runBlocking {
        val assist = AssistReminderTitle(AskBitavidaAi(repository()))
        val generic = assist("pilas del aparato")
        assertEquals("Comprar pilas para el dispositivo", generic.suggestedTitle)
        assertFalse(generic.suggestedTitle.orEmpty().contains("glucómetro"))
        assertTrue(generic.suggestions.isEmpty())
        val explicitDevice = assist("Pilas del glucometro")
        assertEquals("Comprar pilas para el glucómetro", explicitDevice.suggestedTitle)
        val documents = assist("papeles abuelito")
        assertNull(documents.suggestedTitle)
        assertEquals(listOf("Preparar documentos", "Recoger documentos", "Organizar documentos"), documents.clarifications)
        assertEquals(ReminderEducationCategory.DOCUMENTS, documents.category)
        val unspecified = assist("Test")
        assertNull(unspecified.suggestedTitle)
        assertTrue(unspecified.clarifications.isEmpty())
        assertTrue(unspecified.relatedTopics.isEmpty())
        assertNull(unspecified.message)
    }

    @Test fun allLibraryEntriesCanBeReadOfflineWithoutClassificationOrClinicalData() {
        val ask = AskBitavidaAi(repository())
        ReminderEducationLibrary.entries.keys.forEach {
            val result = ask.read(it)
            assertTrue(result.fromLibrary)
            assertEquals(it, result.entry.topic)
        }
    }

    @Test fun reminderContextNeverReadsDrugCatalog() = runBlocking {
        val delegate = repository()
        val isolated = object : EducationRepository {
            override suspend fun classify(question: String) = error("Unscoped classifier must not be used")
            override suspend fun classify(question: String, context: EducationGroup?) =
                delegate.classify(question, context)
            override fun entry(topic: EducationTopic) = ReminderEducationLibrary.entries.getValue(topic)
            override fun drugs(): List<DrugEducation> = error("No clinical catalogue access")
        }
        val result = AskBitavidaAi(isolated)("preparar documentos", EducationGroup.REMINDERS)
        assertTrue(result is EducationResult.Answer)
    }
}
