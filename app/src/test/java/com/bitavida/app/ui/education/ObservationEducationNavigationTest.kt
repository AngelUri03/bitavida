package com.bitavida.app.ui.education

import com.bitavida.app.data.education.ObservationEducationLibrary
import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.model.ObservationCategory
import com.bitavida.app.domain.usecase.AskBitavidaAi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationEducationNavigationTest {
    @Test fun observationContextKeepsItsHomeSuggestionsLibraryFilterAndBackStack() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val repository = object : EducationRepository {
                override suspend fun classify(question: String) = IntentPrediction(null, 0.0, 0.0)
                override fun entry(topic: EducationTopic) =
                    ObservationEducationLibrary.entries.getValue(topic)
            }
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.OBSERVATIONS))
            assertTrue(vm.state.value.observationContext)
            assertFalse(vm.state.value.medicationContext)
            assertEquals(
                listOf(
                    EducationTopic.OBS_001_FEET_REVIEW,
                    EducationTopic.OBS_010_THIRST_DRY_MOUTH,
                    EducationTopic.OBS_022_FALL,
                    EducationTopic.OBSERVATION_GENERAL,
                ),
                vm.state.value.suggestions,
            )
            vm.onAction(BitavidaAiAction.OpenLibrary())
            val libraryId = vm.state.value.current.id
            vm.onAction(BitavidaAiAction.SelectObservationCategory(ObservationCategory.FEET))
            vm.onAction(BitavidaAiAction.SearchLibrary("AMPOLLA"))
            vm.rememberScroll(libraryId, 2, 24)
            vm.onAction(BitavidaAiAction.ReadTopic(EducationTopic.OBS_002_WOUNDS))
            vm.onAction(BitavidaAiAction.Back)
            assertEquals(libraryId, vm.state.value.current.id)
            assertEquals(ObservationCategory.FEET, vm.state.value.observationCategory)
            assertEquals("AMPOLLA", vm.state.value.librarySearch)
            assertEquals(2, vm.scrollIndex(libraryId))
            assertEquals(24, vm.scrollOffset(libraryId))
        } finally {
            scope.cancel()
        }
    }

    @Test fun observationContextNeverShowsAnUnrelatedConfidentAnswer() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val repository = object : EducationRepository {
                override suspend fun classify(question: String) =
                    IntentPrediction(EducationTopic.PRESSURE_PURPOSE, 0.99, 1.0)
                override fun entry(topic: EducationTopic) =
                    ObservationEducationLibrary.entries[topic] ?: EducationEntry(
                        topic, topic.question, "Explicación ficticia", null, emptyList(),
                        EducationSource("Fuente ficticia", "Referencia ficticia"),
                    )
            }
            val vm = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            vm.onAction(BitavidaAiAction.NavigationContext(EducationGroup.OBSERVATIONS))
            vm.onAction(BitavidaAiAction.Ask("Pregunta ambigua ficticia"))
            val fallback = vm.state.value.current.answer as EducationResult.Fallback
            assertEquals(EducationFallback.UNCERTAIN, fallback.reason)
            assertTrue(fallback.suggestions.all { it.group == EducationGroup.OBSERVATIONS })
            assertTrue(fallback.suggestions.isNotEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun catalogUsesOneApprovedTwoRowActionGroupAndPlanOpensTheSharedAiSession() {
        val observations = projectFile(
            "app/src/main/java/com/bitavida/app/ui/observations/ObservationsScreen.kt",
        ).readText()
        val plan = projectFile(
            "app/src/main/java/com/bitavida/app/ui/medical/MedicalPlanScreen.kt",
        ).readText()
        assertTrue(observations.contains("\"Historial de observaciones\""))
        assertTrue(observations.contains("\"Guías y ayuda\""))
        assertTrue(observations.contains("ObservationLibraryActions("))
        assertTrue(observations.contains("MeasurementManagementRow("))
        assertTrue(plan.contains("openObservationHelp"))
        assertTrue(plan.contains("EducationGroup.OBSERVATIONS"))
        assertTrue(plan.contains("onOpenEducation = openObservationHelp"))
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
