package com.bitavida.app.ui.education

import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.usecase.AskBitavidaAi
import com.bitavida.app.domain.validation.textCharacterCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class CareEducationTextLimitsTest {
    @Test fun contextualQuestionAndSearchRespectWholeCharactersWithoutAnInvasiveError() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.REMINDERS))
            val title = "a\u0301".repeat(499) + "👩‍👩‍👧‍👦"
            vm.onAction(BitavidaAiAction.QuestionChanged(title))
            assertEquals(title, vm.state.value.question)
            vm.onAction(BitavidaAiAction.QuestionChanged(title + "z"))
            assertEquals(title, vm.state.value.question)
            assertFalse(vm.state.value.inputLimitReached)
            vm.onAction(BitavidaAiAction.SearchLibrary("🇲🇽".repeat(101)))
            assertEquals("🇲🇽".repeat(100), vm.state.value.librarySearch)
            assertEquals(100, textCharacterCount(vm.state.value.librarySearch))
        } finally { scope.cancel() }
    }

    @Test fun medicationContextPreservesItsPreviousCapturePolicy() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.MEDICATIONS))
            vm.onAction(BitavidaAiAction.QuestionChanged("z".repeat(501)))
            assertTrue(vm.state.value.inputLimitReached)
            assertEquals("", vm.state.value.question)
        } finally { scope.cancel() }
    }

    private val repository = object : EducationRepository {
        override suspend fun classify(question: String) = IntentPrediction(null, 0.0, 0.0)
        override fun entry(topic: EducationTopic) = EducationEntry(
            topic, "Tema ficticio", "Explicación ficticia", null, emptyList(),
            EducationSource("Fuente ficticia", "Prueba local"),
        )
    }
}
