package com.bitavida.app.ui.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.education.ProcedureQuestionResult
import com.bitavida.app.domain.usecase.AskBitavidaAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

/** Context remains local to the explicitly opened OCT and never bypasses clinical guards. */
class OctFollowupViewModelTest {
    private fun model(scope: CoroutineScope) = BitavidaAiViewModel(
        AskBitavidaAi(BundledEducationRepository(loadModelText = { "" })),
        Dispatchers.Unconfined,
        scope,
    )

    @Test fun procedureFollowupUsesOpenedOctButUrgencyStillTakesPrecedence() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val model = model(scope)
            model.onAction(BitavidaAiAction.StartProcedureSession(listOf("oct")))
            model.onAction(BitavidaAiAction.AskProcedure("¿Duele?"))
            val page = model.state.value.current
            assertEquals(AiPage.PROCEDURE_ANSWER, page.page)
            assertEquals("oct", (page.procedureResult as ProcedureQuestionResult.Match).guide.id)
            assertEquals("during", page.procedureSection)

            model.onAction(BitavidaAiAction.AskProcedure("Mi familiar no respira, ¿duele?"))
            assertEquals(ProcedureQuestionResult.Urgent, model.state.value.current.procedureResult)
            model.onAction(BitavidaAiAction.AskProcedure("¿Duele?"))
            assertEquals(ProcedureQuestionResult.Unknown, model.state.value.current.procedureResult)
        } finally {
            scope.cancel()
        }
    }

    @Test fun centralFollowupUsesLastGuideAndClearDiscardsContext() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val model = model(scope)
            model.onAction(BitavidaAiAction.CentralContext(true))
            model.onAction(BitavidaAiAction.OpenProcedureGuide("oct"))
            model.onAction(BitavidaAiAction.NewQuestion)
            model.onAction(BitavidaAiAction.Ask("¿Necesita gotas?"))
            val page = model.state.value.current
            assertEquals(AiPage.PROCEDURE_ANSWER, page.page)
            assertEquals("oct", (page.procedureResult as ProcedureQuestionResult.Match).guide.id)
            assertEquals("preparation", page.procedureSection)

            model.onAction(BitavidaAiAction.Clear)
            model.onAction(BitavidaAiAction.Ask("¿Necesita gotas?"))
            assertFalse(model.state.value.current.procedureResult is ProcedureQuestionResult.Match)
        } finally {
            scope.cancel()
        }
    }

    @Test fun explicitComparisonWithRetinographyUsesTheOpenedOctWithoutChangingSubjects() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val model = model(scope)
            model.onAction(BitavidaAiAction.StartProcedureSession(listOf("oct")))
            model.onAction(BitavidaAiAction.AskProcedure("¿Es lo mismo que una retinografía?"))
            val page = model.state.value.current
            assertEquals("oct", (page.procedureResult as ProcedureQuestionResult.Match).guide.id)
            assertEquals("comparison", page.procedureSection)
        } finally {
            scope.cancel()
        }
    }

    @Test fun relatedQuestionOpensItsDocumentedSection() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val model = model(scope)
            model.onAction(BitavidaAiAction.StartProcedureSession(listOf("oct")))
            model.onAction(BitavidaAiAction.ReadProcedureSection("oct", "oct-retinography"))
            val page = model.state.value.current
            assertEquals("¿Es lo mismo que una retinografía?", page.submittedQuestion)
            assertEquals("comparison", page.procedureSection)
            assertEquals("oct", (page.procedureResult as ProcedureQuestionResult.Match).guide.id)
        } finally {
            scope.cancel()
        }
    }

    @Test fun personalInterpretationAndAnotherNamedProcedureNeverInheritOct() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val model = model(scope)
            model.onAction(BitavidaAiAction.StartProcedureSession(listOf("oct")))
            model.onAction(BitavidaAiAction.AskProcedure("Mi OCT es anormal, ¿qué hago?"))
            assertEquals(ProcedureQuestionResult.OutsideScope, model.state.value.current.procedureResult)

            model.onAction(BitavidaAiAction.OpenProcedureGuide("oct"))
            model.onAction(BitavidaAiAction.AskProcedure("¿Qué es la retinografía?"))
            val result = model.state.value.current.procedureResult
            assertTrue(result !is ProcedureQuestionResult.Match || result.guide.id != "oct")
        } finally {
            scope.cancel()
        }
    }
}
