package com.bitavida.app.ui.education

import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.usecase.AskBitavidaAi
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BitavidaAiNavigationTest {
    private val catalog = EducationTopic.entries.filter { it.group != EducationGroup.MEDICATIONS && it.group != EducationGroup.OBSERVATIONS && it.group != EducationGroup.REMINDERS }.map {
        EducationEntry(it, it.question, "Contenido ficticio de ${it.group.label}", "Ejemplo ficticio",
            emptyList(), EducationSource("Fuente ficticia", "Prueba local"))
    }

    @Test fun libraryAndDetailRestoreDraftFiltersScrollAndDisclosuresOnBack() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = model(scope)
            val homeId = vm.state.value.current.id
            vm.onAction(BitavidaAiAction.QuestionChanged("Pregunta ficticia pendiente"))
            vm.rememberScroll(homeId, 0, 180)
            vm.onAction(BitavidaAiAction.OpenLibrary(EducationGroup.PRESSURE))
            val libraryId = vm.state.value.current.id
            vm.onAction(BitavidaAiAction.SearchLibrary("presion"))
            vm.rememberScroll(libraryId, 4, 71)
            vm.onAction(BitavidaAiAction.ReadTopic(EducationTopic.PRESSURE_NUMBERS))
            val detailId = vm.state.value.current.id
            vm.rememberScroll(detailId, 0, 420)
            vm.onAction(BitavidaAiAction.ToggleSource(detailId))
            vm.onAction(BitavidaAiAction.ToggleDetails(detailId)) // detail defaults to expanded
            vm.onAction(BitavidaAiAction.ReadTopic(EducationTopic.PRESSURE_REST))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(detailId, vm.state.value.current.id)
            assertEquals(420, vm.scrollOffset(detailId))
            assertEquals(true, vm.state.value.sourceVisibility[detailId])
            assertEquals(false, vm.state.value.detailsVisibility[detailId])
            assertEquals(EducationTopic.PRESSURE_NUMBERS,
                (vm.state.value.current.answer as EducationResult.Answer).entry.topic)
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(libraryId, vm.state.value.current.id)
            assertEquals("presion", vm.state.value.librarySearch)
            assertEquals(EducationGroup.PRESSURE, vm.state.value.libraryGroup)
            assertEquals(4, vm.scrollIndex(libraryId))
            assertEquals(71, vm.scrollOffset(libraryId))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(homeId, vm.state.value.current.id)
            assertEquals("Pregunta ficticia pendiente", vm.state.value.question)
            assertEquals(180, vm.scrollOffset(homeId))
            assertFalse(vm.state.value.canGoBack)
        } finally { scope.cancel() }
    }

    @Test fun classificationOpensSeparateAnswerAndDirectReadingOpensDetailWithoutInference() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val repository = object : EducationRepository {
                override suspend fun classify(question: String): IntentPrediction {
                    calls++
                    return IntentPrediction(EducationTopic.PRESSURE_PURPOSE, 0.98, 1.0)
                }
                override fun entry(topic: EducationTopic) = catalog.first { it.topic == topic }
            }
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            vm.onAction(BitavidaAiAction.Ask("Pregunta ficticia"))
            assertEquals(AiPage.ANSWER, vm.state.value.current.page)
            assertEquals("Pregunta ficticia", vm.state.value.current.submittedQuestion)
            assertEquals(1, calls)
            vm.onAction(BitavidaAiAction.ReadTopic(EducationTopic.GLUCOSE_PURPOSE))
            assertEquals(AiPage.DETAIL, vm.state.value.current.page)
            assertEquals(1, calls)
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(AiPage.ANSWER, vm.state.value.current.page)
            assertEquals("Pregunta ficticia", vm.state.value.current.submittedQuestion)
            vm.onAction(BitavidaAiAction.QuestionChanged("x".repeat(501)))
            vm.onAction(BitavidaAiAction.NewQuestion)
            assertEquals(AiPage.HOME, vm.state.value.current.page)
            assertEquals("", vm.state.value.question)
            assertFalse(vm.state.value.inputLimitReached)
        } finally { scope.cancel() }
    }

    @Test fun backAndExitCancelPendingInferenceAndClearRemovesSessionData() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val gate = CompletableDeferred<IntentPrediction>()
            val repository = object : EducationRepository {
                override suspend fun classify(question: String) = gate.await()
                override fun entry(topic: EducationTopic) = catalog.first { it.topic == topic }
            }
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            vm.onAction(BitavidaAiAction.Ask("Pregunta ficticia"))
            vm.onAction(BitavidaAiAction.Clear)
            gate.complete(IntentPrediction(EducationTopic.PRESSURE_PURPOSE, 0.98, 1.0))
            assertEquals(1, vm.state.value.pages.size)
            assertEquals(AiPage.HOME, vm.state.value.current.page)
            assertNull(vm.state.value.result)
            assertEquals("", vm.state.value.question)
            vm.rememberScroll(999L, 10, 100)
            assertEquals(0, vm.scrollOffset(999L))
        } finally { scope.cancel() }
    }

    @Test fun localSearchCombinesCategoryAndAccentInsensitiveWords() {
        val pressure = filterEducationLibrary(catalog, EducationGroup.PRESSURE, "PRESIÓN")
        assertTrue(pressure.isNotEmpty())
        assertTrue(pressure.all { it.topic.group == EducationGroup.PRESSURE })
        val fasting = filterEducationLibrary(catalog, null, "ayunas?")
        assertEquals(listOf(EducationTopic.GLUCOSE_FASTING), fasting.map { it.topic })
        assertTrue(filterEducationLibrary(catalog, null, "zzzzzz").isEmpty())
        assertEquals(30, filterEducationLibrary(catalog, null, "").size)
    }

    private fun model(scope: CoroutineScope): BitavidaAiViewModel {
        val repository = object : EducationRepository {
            override suspend fun classify(question: String) = IntentPrediction(null, 0.0, 0.0)
            override fun entry(topic: EducationTopic) = catalog.first { it.topic == topic }
        }
        return BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
    }
}
