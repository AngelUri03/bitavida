package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.usecase.AskBitavidaAi
import com.bitavida.app.ui.education.BitavidaAiAction
import com.bitavida.app.ui.education.BitavidaAiViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

/** Exercises routing against the actual bundled procedure and medication catalogues. */
class CentralProcedureSearchTest {
    private val ask = AskBitavidaAi(BundledEducationRepository(loadModelText = { "" }))

    private fun ids(result: ProcedureQuestionResult?): Set<String> = when (result) {
        is ProcedureQuestionResult.Match -> setOf(result.guide.id)
        is ProcedureQuestionResult.Choices -> result.guides.map { it.id }.toSet()
        else -> emptySet()
    }

    @Test fun centralQuestionsFindDocumentedProcedures() {
        val questions = mapOf(
            "¿Qué es la hemoglobina glucosilada?" to "hba1c",
            "¿Para qué sirve la HbA1c?" to "hba1c",
            "¿Qué es una vitrectomía?" to "vitrectomia",
            "¿Qué hacen en una revisión de pies?" to "pies-integral",
            "¿Qué revisan en una consulta de diabetes?" to "consulta-diabetes",
            "¿Cómo es un fondo de ojo?" to "fondo-ojo",
        )
        questions.forEach { (question, expectedId) ->
            assertTrue("$question must offer $expectedId", expectedId in ids(ask.centralProcedureQuestion(question)))
        }
    }

    @Test fun centralOctAliasUsesItsGuideWithoutBypassingSafetyGuards() {
        val result = ask.centralProcedureQuestion("¿Qué es una OCT?")
        assertTrue(result is ProcedureQuestionResult.Match)
        assertEquals("oct", (result as ProcedureQuestionResult.Match).guide.id)
        assertNull(ask.centralProcedureQuestion("¿Qué dice el doctor?"))
        assertEquals(ProcedureQuestionResult.OutsideScope,
            ask.centralProcedureQuestion("Mi OCT es anormal, ¿qué hago?"))
        assertEquals(ProcedureQuestionResult.Urgent,
            ask.centralProcedureQuestion("Mi familiar no respira, ¿qué es la OCT?"))
    }

    @Test fun unspecifiedBypassRequiresAnExplicitChoice() {
        val result = ask.centralProcedureQuestion("¿Qué es una cirugía de bypass?")
        assertTrue(result is ProcedureQuestionResult.Choices)
        val ids = ids(result)
        assertTrue("Coronary and gastric bypasses must both be available",
            setOf("bypass-coronario", "bypass-gastrico").all { it in ids })
    }

    @Test fun mixedPersonalSymptomsAndMedicinesNeverReturnAnEducationalMatch() {
        listOf(
            "Mi padre toma metformina y tiene el pie infectado, ¿qué es monofilamento?",
            "Mi mamá usa metformina y tiene la vista borrosa, ¿qué es fondo de ojo?",
            "Mi HbA1c es 9, ¿es normal?",
        ).forEach { question ->
            assertEquals(question, ProcedureQuestionResult.OutsideScope, ask.centralProcedureQuestion(question))
        }
        assertEquals(ProcedureQuestionResult.Urgent,
            ask.centralProcedureQuestion("Mi mamá toma metformina y no respira, ¿qué es HbA1c?"))
    }

    @Test fun failedProcedureLookupDoesNotFallThroughToAnotherClassifier() {
        var classifications = 0
        val repository = object : EducationRepository {
            override fun procedures(): List<ProcedureGuide> = error("Synthetic catalogue failure")
            override suspend fun classify(question: String): IntentPrediction {
                classifications++
                return IntentPrediction(null, 0.0, 0.0)
            }
            override fun entry(topic: EducationTopic): EducationEntry = error("Not used")
        }
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            val model = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            model.onAction(BitavidaAiAction.CentralContext(true))
            model.onAction(BitavidaAiAction.Ask("¿Qué es la vitrectomía?"))
            assertEquals(EducationResult.Fallback(EducationFallback.UNAVAILABLE), model.state.value.result)
            assertEquals(0, classifications)
            assertFalse(model.state.value.analyzing)
        } finally {
            scope.cancel()
        }
    }

    @Test fun unrelatedDrugQuestionsKeepExistingMedicationRoute() {
        assertNull(ask.centralProcedureQuestion("¿Para qué sirve la metformina?"))
        assertNull(ask.centralProcedureQuestion("¿Qué es un tema inexistente?"))
    }
}
