package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.education.*

/** The existing learned classifier supplies context; conservative proposals never change a draft. */
class AssistReminderTitle(private val ask: AskBitavidaAi) {
    suspend operator fun invoke(text: String): ReminderTitleAssistance {
        val normalized = EducationTokenizer.normalize(text)
        val result = ask(text, EducationGroup.REMINDERS)
        // A noun alone identifies an object, never the action the person wishes to perform.
        if (normalized in ambiguousDocuments) return ReminderTitleAssistance(
            clarifications = listOf("Preparar documentos", "Recoger documentos", "Organizar documentos"),
            category = ReminderEducationCategory.DOCUMENTS,
        )
        return when (result) {
            is EducationResult.Answer -> ReminderTitleAssistance(
                suggestedTitle = clearerDeclaredTitle(normalized, result.entry.topic),
                category = result.entry.reminderCategory,
            )
            else -> ReminderTitleAssistance()
        }
    }

    private fun clearerDeclaredTitle(words: String, topic: EducationTopic): String? {
        if (topic != EducationTopic.REMINDER_BATTERIES) return null
        val tokens = words.split(' ').toSet()
        if (tokens.none { it in setOf("pila", "pilas", "bateria", "baterias") } ||
            tokens.any { it !in batteryWords }) return null
        val device = when {
            "glucometro" in tokens -> "el glucómetro"
            tokens.any { it in setOf("aparato", "dispositivo") } -> "el dispositivo"
            else -> return null
        }
        val action = when {
            "reponer" in tokens -> "Reponer"
            "cambiar" in tokens -> "Cambiar"
            else -> "Comprar"
        }
        return "$action pilas para $device".takeIf { EducationTokenizer.normalize(it) != words }
    }

    private companion object {
        val ambiguousDocuments = setOf(
            "papeles", "documentos", "papeles abuelito", "papeles del abuelito",
            "papeles abuelita", "papeles del abuelo", "papeles de la abuela",
        )
        val batteryWords = setOf(
            "pila", "pilas", "bateria", "baterias", "del", "de", "el", "la", "las", "los", "para",
            "glucometro", "aparato", "dispositivo", "comprar", "reponer", "cambiar",
        )
    }
}
