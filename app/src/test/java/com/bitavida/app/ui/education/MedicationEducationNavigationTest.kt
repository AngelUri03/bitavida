package com.bitavida.app.ui.education

import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.usecase.AskBitavidaAi
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MedicationEducationNavigationTest {
    private val tablet = DrugEducation("ficticio_oral", "Principio ficticio oral", "Familia ficticia",
        listOf("principio ficticio"), "Resumen ficticio.",
        listOf(DrugEducationSection(DrugIntent.PURPOSE, "Uso", "Explicación ficticia.")),
        listOf(DrugReference("Institución ficticia", "Documento de prueba", "https://example.org/fixture", "Prueba")))
    private val injectable = tablet.copy(id = "ficticio_inyectable", name = "Principio ficticio inyectable")
    private fun model(scope: CoroutineScope): BitavidaAiViewModel {
        val repository = object : EducationRepository {
            override suspend fun classify(question: String) = IntentPrediction(null, 0.0, 0.0)
            override fun entry(topic: EducationTopic) = EducationEntry(topic, topic.question,
                "Contenido ficticio", null, emptyList(), EducationSource("Ficticia", "Prueba"))
            override fun drugs() = listOf(tablet, injectable)
        }
        return BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
    }

    @Test fun pharmacyLibraryRetainsSearchFamilyScrollAndExpandedSectionsOnBack() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = model(scope)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.MEDICATIONS))
            assertTrue(vm.state.value.medicationContext)
            vm.onAction(BitavidaAiAction.QuestionChanged("Borrador ficticio"))
            vm.onAction(BitavidaAiAction.OpenLibrary())
            vm.onAction(BitavidaAiAction.SelectDrugFamily(tablet.family))
            vm.onAction(BitavidaAiAction.SearchLibrary("ficticio"))
            val library = vm.state.value.current.id
            vm.rememberScroll(library, 3, 42)
            vm.onAction(BitavidaAiAction.ReadDrug(tablet.id, DrugIntent.SIDE_EFFECTS))
            val detail = vm.state.value.current.id
            assertEquals(AiPage.DETAIL, vm.state.value.current.page)
            assertEquals(setOf(DrugIntent.SIDE_EFFECTS), vm.state.value.drugSections[detail])
            vm.onAction(BitavidaAiAction.ToggleDrugSection(detail, DrugIntent.PRECAUTIONS))
            vm.onAction(BitavidaAiAction.ToggleSource(detail))
            vm.rememberScroll(detail, 0, 123)
            vm.onAction(BitavidaAiAction.ReadDrug(injectable.id))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(detail, vm.state.value.current.id)
            assertEquals(123, vm.scrollOffset(detail))
            assertEquals(setOf(DrugIntent.SIDE_EFFECTS, DrugIntent.PRECAUTIONS), vm.state.value.drugSections[detail])
            assertEquals(true, vm.state.value.sourceVisibility[detail])
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(library, vm.state.value.current.id)
            assertEquals(tablet.family, vm.state.value.libraryFamily)
            assertEquals("ficticio", vm.state.value.librarySearch)
            assertEquals(3, vm.scrollIndex(library))
            assertEquals(42, vm.scrollOffset(library))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals("Borrador ficticio", vm.state.value.question)
        } finally { scope.cancel() }
    }

    @Test fun registeredNameNeverUsesFuzzyMatchingAndAmbiguityRequiresSelection() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val vm = model(scope)
            assertTrue(vm.registeredDrugGuides("Prinsipio ficticio").isEmpty())
            assertTrue(vm.registeredDrugGuides("Marca ficticia").isEmpty())
            vm.onAction(BitavidaAiAction.OpenRegisteredDrug("principio ficticio"))
            val options = vm.state.value.current.answer as EducationResult.DrugOptions
            assertEquals(2, options.candidates.size)
            vm.onAction(BitavidaAiAction.ReadDrug(injectable.id))
            val answer = vm.state.value.current.answer as EducationResult.DrugAnswer
            assertEquals(injectable.id, answer.drug.id)
            vm.onAction(BitavidaAiAction.Clear)
            assertTrue(vm.state.value.drugSections.isEmpty())
            assertEquals("", vm.state.value.librarySearch)
            assertNull(vm.state.value.libraryFamily)
            assertEquals(1, vm.state.value.pages.size)
        } finally { scope.cancel() }
    }

    @Test fun searchCombinesFamilyGenericAliasCaseAndAccentsWithoutClinicalData() {
        val entries = listOf(tablet.copy(aliases = listOf("sinónimo ficticio")), injectable)
        assertEquals(listOf(tablet.id), filterDrugLibrary(entries, tablet.family, "SINONIMO").map { it.id })
        assertEquals(listOf(injectable.id), filterDrugLibrary(entries, null, "ficticio inyectable").map { it.id })
        assertTrue(filterDrugLibrary(entries, "Otra familia", "").isEmpty())
        assertTrue(filterDrugLibrary(entries, null, "desconocido").isEmpty())
    }
}
