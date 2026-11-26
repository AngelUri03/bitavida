package com.bitavida.app.ui.education

import com.bitavida.app.data.education.ReminderEducationLibrary
import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.usecase.AskBitavidaAi
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReminderEducationNavigationTest {
    private val topics = EducationTopic.entries.filter { it.group == EducationGroup.REMINDERS }

    @Test fun askingUsesTheReminderContextAndARealRecoveredEntry() = runBlocking {
        var receivedContext: EducationGroup? = null
        var legacyCalls = 0
        val repository = object : EducationRepository {
            override suspend fun classify(question: String): IntentPrediction {
                legacyCalls++
                return IntentPrediction(null, 0.0, 0.0)
            }
            override suspend fun classify(question: String, context: EducationGroup?): IntentPrediction {
                receivedContext = context
                return IntentPrediction(topics.first(), 0.99, 1.0)
            }
            override fun entry(topic: EducationTopic) = ReminderEducationLibrary.entries.getValue(topic)
        }
        val session = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, session)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.REMINDERS))
            vm.onAction(BitavidaAiAction.Ask("Quiero organizar mis pendientes cotidianos"))
            assertEquals(EducationGroup.REMINDERS, receivedContext)
            assertEquals(0, legacyCalls)
            val answer = vm.state.value.current.answer as EducationResult.Answer
            assertEquals(EducationGroup.REMINDERS, answer.entry.topic.group)
            assertTrue(answer.entry.reminderExamples.isNotEmpty())
        } finally { session.cancel() }
    }

    @Test fun libraryPreservesCategorySearchAndScrollWhenReturningFromAnExampleTopic() {
        val repository = object : EducationRepository {
            override suspend fun classify(question: String) = IntentPrediction(null, 0.0, 0.0)
            override fun entry(topic: EducationTopic) = ReminderEducationLibrary.entries.getValue(topic)
        }
        val session = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, session)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.REMINDERS))
            assertTrue(vm.state.value.reminderContext)
            assertTrue(vm.state.value.suggestions.all { it.group == EducationGroup.REMINDERS })
            vm.onAction(BitavidaAiAction.OpenLibrary())
            val libraryId = vm.state.value.current.id
            vm.onAction(BitavidaAiAction.SelectReminderCategory(ReminderEducationCategory.DOCUMENTS))
            vm.onAction(BitavidaAiAction.SearchLibrary("documentos"))
            vm.rememberScroll(libraryId, 3, 17)
            vm.onAction(BitavidaAiAction.ReadTopic(topics.first()))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(libraryId, vm.state.value.current.id)
            assertEquals(ReminderEducationCategory.DOCUMENTS, vm.state.value.reminderCategory)
            assertEquals("documentos", vm.state.value.librarySearch)
            assertEquals(3, vm.scrollIndex(libraryId))
            assertEquals(17, vm.scrollOffset(libraryId))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(AiPage.HOME, vm.state.value.current.page)
            vm.onAction(BitavidaAiAction.Clear)
            assertNull(vm.state.value.reminderCategory)
            assertEquals("", vm.state.value.question)
        } finally { session.cancel() }
    }

    @Test fun complementaryAndRelatedSectionsRememberTheirPageAndClearWithTheSession() {
        val repository = object : EducationRepository {
            override suspend fun classify(question: String) = IntentPrediction(null, 0.0, 0.0)
            override fun entry(topic: EducationTopic) = ReminderEducationLibrary.entries.getValue(topic)
        }
        val session = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, session)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.REMINDERS))
            vm.onAction(BitavidaAiAction.ReadTopic(topics.first()))
            val firstPage = vm.state.value.current.id
            vm.onAction(BitavidaAiAction.ToggleDetails(firstPage))
            vm.onAction(BitavidaAiAction.ToggleRelated(firstPage))
            assertEquals(true, vm.state.value.detailsVisibility[firstPage])
            vm.onAction(BitavidaAiAction.ReadTopic(topics[1]))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(firstPage, vm.state.value.current.id)
            assertEquals(true, vm.state.value.detailsVisibility[firstPage])
            assertEquals(true, vm.state.value.relatedVisibility[firstPage])
            vm.onAction(BitavidaAiAction.NewQuestion)
            assertTrue(vm.state.value.detailsVisibility.isEmpty())
            assertTrue(vm.state.value.relatedVisibility.isEmpty())
        } finally { session.cancel() }
    }

    @Test fun anUnrelatedConfidentPredictionBecomesRelatedQuestionsWithinReminders() {
        val repository = object : EducationRepository {
            override suspend fun classify(question: String) =
                IntentPrediction(EducationTopic.PRESSURE_PURPOSE, 0.99, 1.0)
            override fun entry(topic: EducationTopic) = ReminderEducationLibrary.entries[topic] ?: EducationEntry(
                topic, "Tema ficticio", "Explicación ficticia", null, emptyList(),
                EducationSource("Fuente ficticia", "Referencia ficticia"))
        }
        val session = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, session)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.REMINDERS))
            vm.onAction(BitavidaAiAction.Ask("Duda organizativa ambigua"))
            val fallback = vm.state.value.current.answer as EducationResult.Fallback
            assertTrue(fallback.suggestions.isNotEmpty())
            assertTrue(fallback.suggestions.all { it.group == EducationGroup.REMINDERS })
        } finally { session.cancel() }
    }
}
