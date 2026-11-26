package com.bitavida.app.ui.education

import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.usecase.AskBitavidaAi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class BitavidaAiViewModelTest {
    @Test fun newestQuestionWinsAndLateClassificationCannotReplaceIt() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val repository = DeferredRepository()
            val model = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            model.onAction(BitavidaAiAction.Ask("primera pregunta"))
            assertTrue(model.state.value.analyzing)
            model.onAction(BitavidaAiAction.Ask("segunda pregunta"))
            repository.second.complete(IntentPrediction(EducationTopic.PRESSURE_NUMBERS, 0.99, 1.0))
            repository.first.complete(IntentPrediction(EducationTopic.GLUCOSE_FASTING, 0.99, 1.0))
            assertEquals("segunda pregunta", model.state.value.question)
            assertFalse(model.state.value.analyzing)
            assertEquals(
                EducationTopic.PRESSURE_NUMBERS,
                (model.state.value.result as EducationResult.Answer).entry.topic,
            )
        } finally { scope.cancel() }
    }

    @Test fun editingOrClearingCancelsThePendingResult() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val repository = DeferredRepository()
            val model = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            model.onAction(BitavidaAiAction.Ask("primera pregunta"))
            model.onAction(BitavidaAiAction.QuestionChanged("otro tema"))
            repository.first.complete(IntentPrediction(EducationTopic.GLUCOSE_FASTING, 0.99, 1.0))
            assertNull(model.state.value.result)
            assertFalse(model.state.value.analyzing)
            assertEquals("otro tema", model.state.value.question)
            model.onAction(BitavidaAiAction.Clear)
            assertEquals("", model.state.value.question)
        } finally { scope.cancel() }
    }

    @Test fun navigationContextOnlyPrioritizesSuggestionsAndKeepsExplicitSelection() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val repository = DeferredRepository()
            val model = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            model.onAction(BitavidaAiAction.NavigationContext(EducationGroup.WEIGHT))
            assertTrue(model.state.value.suggestions.all { it.group == EducationGroup.WEIGHT })
            model.onAction(BitavidaAiAction.SelectGroup(EducationGroup.PRESSURE))
            model.onAction(BitavidaAiAction.NavigationContext(EducationGroup.WEIGHT))
            assertEquals(EducationGroup.PRESSURE, model.state.value.group)
            assertEquals(0, repository.calls)
            model.onAction(BitavidaAiAction.ReadTopic(EducationTopic.GLUCOSE_UNITS))
            assertTrue((model.state.value.result as EducationResult.Answer).fromLibrary)
            assertEquals(0, repository.calls)
        } finally { scope.cancel() }
    }

    @Test fun failedInferenceHasRecoverableFallbackAndLibraryStillWorks() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val repository = object : EducationRepository {
                override suspend fun classify(question: String): IntentPrediction = error("Synthetic failure")
                override fun entry(topic: EducationTopic) = fixture(topic)
            }
            val model = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            model.onAction(BitavidaAiAction.Ask("pregunta ficticia"))
            assertEquals(EducationResult.Fallback(EducationFallback.UNAVAILABLE), model.state.value.result)
            assertFalse(model.state.value.analyzing)
            model.onAction(BitavidaAiAction.ReadTopic(EducationTopic.WEIGHT_TRACKING))
            assertTrue(model.state.value.result is EducationResult.Answer)
            model.onAction(BitavidaAiAction.QuestionChanged("x".repeat(501)))
            assertFalse(model.state.value.inputLimitReached)
            assertEquals("pregunta ficticia", model.state.value.question)
        } finally { scope.cancel() }
    }

    private class DeferredRepository : EducationRepository {
        val first = CompletableDeferred<IntentPrediction>()
        val second = CompletableDeferred<IntentPrediction>()
        var calls = 0
        override suspend fun classify(question: String): IntentPrediction {
            calls++
            return if (calls == 1) first.await() else second.await()
        }
        override fun entry(topic: EducationTopic) = fixture(topic)
    }

    companion object {
        private fun fixture(topic: EducationTopic) = EducationEntry(
            topic, "Tema ficticio", "Explicación ficticia", null, emptyList(),
            EducationSource("Fuente ficticia", "Prueba JVM"),
        )
    }
}
