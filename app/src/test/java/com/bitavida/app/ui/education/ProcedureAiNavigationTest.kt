package com.bitavida.app.ui.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.education.ProcedureCategory
import com.bitavida.app.domain.education.ProcedureQuestionResult
import com.bitavida.app.domain.usecase.AskBitavidaAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class ProcedureAiNavigationTest {
    private fun model(scope: CoroutineScope) = BitavidaAiViewModel(
        AskBitavidaAi(BundledEducationRepository(loadModelText = { "" })),
        Dispatchers.Unconfined,
        scope,
    )

    @Test fun centralLibraryGuideAndBackPreserveOriginAndSearch() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = model(scope)
            vm.onAction(BitavidaAiAction.CentralContext(true))
            vm.onAction(BitavidaAiAction.OpenLibrary())
            vm.onAction(BitavidaAiAction.SearchLibrary("riñones"))
            vm.onAction(BitavidaAiAction.OpenProcedureLibrary)
            vm.onAction(BitavidaAiAction.SearchProcedure("ojo"))
            vm.onAction(BitavidaAiAction.SelectProcedureCategory(ProcedureCategory.EYES))
            val library = vm.state.value.current.id
            vm.rememberScroll(library, 3, 40)
            vm.onAction(BitavidaAiAction.OpenProcedureGuide("hba1c"))
            val detail = vm.state.value.current.id
            vm.onAction(BitavidaAiAction.ToggleProcedureSection(detail, "preparation"))
            assertEquals(setOf("preparation"), vm.state.value.procedureSections[detail])
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(AiPage.PROCEDURE_LIBRARY, vm.state.value.current.page)
            assertEquals("ojo", vm.state.value.procedureSearch)
            assertEquals(ProcedureCategory.EYES, vm.state.value.procedureCategory)
            assertEquals(3, vm.scrollIndex(library))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(AiPage.LIBRARY, vm.state.value.current.page)
            assertEquals("riñones", vm.state.value.librarySearch)
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(AiPage.HOME, vm.state.value.current.page)
        } finally { scope.cancel() }
    }

    @Test fun directAppointmentGuideIsRootAndSessionCanBeCleared() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = model(scope)
            vm.onAction(BitavidaAiAction.StartProcedureSession(listOf("hba1c")))
            assertEquals(AiPage.PROCEDURE_DETAIL, vm.state.value.current.page)
            assertFalse(vm.state.value.canGoBack)
            vm.onAction(BitavidaAiAction.OpenProcedureLibrary)
            vm.onAction(BitavidaAiAction.SearchProcedure("retina"))
            vm.onAction(BitavidaAiAction.StartProcedureSession(emptyList())) // Route recreated with retained ViewModel
            assertEquals(AiPage.PROCEDURE_LIBRARY, vm.state.value.current.page)
            assertEquals("retina", vm.state.value.procedureSearch)
            vm.onAction(BitavidaAiAction.Back)
            assertEquals("hba1c", vm.state.value.current.procedureId)
            vm.onAction(BitavidaAiAction.Clear)
            assertEquals(AiPage.HOME, vm.state.value.current.page)
            assertNull(vm.state.value.current.procedureId)
        } finally { scope.cancel() }
    }

    @Test fun relatedQuestionsUseOnlyExplicitDocumentedSection() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = model(scope)
            vm.onAction(BitavidaAiAction.StartProcedureSession(listOf("consulta-diabetes")))
            vm.onAction(BitavidaAiAction.ReadProcedureSection("consulta-diabetes", "questions"))
            assertEquals(AiPage.PROCEDURE_ANSWER, vm.state.value.current.page)
            assertEquals("questions", vm.state.value.current.procedureSection)
            assertEquals("consulta-diabetes", (vm.state.value.current.procedureResult as ProcedureQuestionResult.Match).guide.id)
            val count = vm.state.value.pages.size
            vm.onAction(BitavidaAiAction.ReadProcedureSection("consulta-diabetes", "arbitrary"))
            assertEquals(count, vm.state.value.pages.size)
            vm.onAction(BitavidaAiAction.AskProcedure("¿Debo cambiar mis medicamentos antes de una consulta de diabetes?"))
            assertEquals(ProcedureQuestionResult.OutsideScope, vm.state.value.current.procedureResult)
        } finally { scope.cancel() }
    }

    @Test fun centralBypassQuestionPreservesAmbiguityAndPersonalSafety() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = model(scope)
            vm.onAction(BitavidaAiAction.CentralContext(true))
            vm.onAction(BitavidaAiAction.Ask("¿Qué es una cirugía de bypass?"))
            val result = vm.state.value.current.procedureResult as ProcedureQuestionResult.Choices
            assertTrue(result.guides.any { it.id == "bypass-coronario" })
            assertTrue(result.guides.any { it.id == "bypass-gastrico" })
            vm.onAction(BitavidaAiAction.Back)
            vm.onAction(BitavidaAiAction.Ask("Mi papá toma metformina y tiene el pie infectado, ¿qué es monofilamento?"))
            assertEquals(ProcedureQuestionResult.OutsideScope, vm.state.value.current.procedureResult)
        } finally { scope.cancel() }
    }
}
