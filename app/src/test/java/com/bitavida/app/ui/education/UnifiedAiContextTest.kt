package com.bitavida.app.ui.education

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import com.bitavida.app.domain.education.*
import com.bitavida.app.domain.usecase.AskBitavidaAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Test

class UnifiedAiContextTest {
    @Test fun explicitReminderRoutingIsCaseAndAccentInsensitive() {
        assertTrue(isExplicitReminderQuestion("¿PARA QUÉ SIRVEN LOS RECORDATORIOS?"))
        assertTrue(isExplicitReminderQuestion("Como usar un recordatorio"))
    }

    @Test fun centralReminderQuestionUsesExistingContextAndSafetyGuard() {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        try {
            var context: EducationGroup? = null
            val repository = object : EducationRepository {
                override suspend fun classify(question: String) =
                    IntentPrediction(EducationTopic.GLUCOSE_WHAT, 0.99, 1.0)
                override suspend fun classify(question: String, selected: EducationGroup?): IntentPrediction {
                    context = selected
                    return IntentPrediction(EducationTopic.REMINDER_IDEAS, 0.99, 1.0)
                }
                override fun entry(topic: EducationTopic) = EducationEntry(
                    topic, "Tema ficticio", "Explicación ficticia", null, emptyList(),
                    EducationSource("Fuente ficticia", "Prueba JVM"),
                )
            }
            val model = BitavidaAiViewModel(AskBitavidaAi(repository), Dispatchers.Unconfined, scope)
            model.onAction(BitavidaAiAction.CentralContext(true))
            model.onAction(BitavidaAiAction.Ask("¿Para qué sirven los recordatorios?"))
            assertEquals(EducationGroup.REMINDERS, context)
            assertEquals(EducationTopic.REMINDER_IDEAS, (model.state.value.result as EducationResult.Answer).entry.topic)
            model.onAction(BitavidaAiAction.Ask("¿Qué dosis de insulina pongo en mi recordatorio?"))
            assertEquals(EducationFallback.OUTSIDE_SCOPE, (model.state.value.result as EducationResult.Fallback).reason)
        } finally { scope.cancel() }
    }

    @Test fun unrelatedQuestionsDoNotGetForcedIntoReminderClassifier() {
        assertFalse(isExplicitReminderQuestion("¿Para qué sirve la metformina?"))
        assertFalse(isExplicitReminderQuestion("¿Cómo registrar una medición?"))
        assertFalse(isExplicitReminderQuestion("¿Qué es una observación?"))
    }
}
