package com.bitavida.app.domain.education

/** Educational browsing categories, never categories imposed on a saved reminder. */
enum class ReminderEducationCategory(val label: String) {
    EVERYDAY("Organización cotidiana"),
    SHOPPING("Compras y suministros"),
    DOCUMENTS("Documentos y trámites"),
    FAMILY("Coordinación familiar"),
    OUTINGS("Preparación de salidas"),
    TASKS("Gestión de pendientes"),
    APP("Uso de Recordatorios"),
}

data class ReminderSuggestion(val id: String, val title: String, val explanation: String)

data class ReminderTitleAssistance(
    val suggestedTitle: String? = null,
    val suggestions: List<ReminderSuggestion> = emptyList(),
    val relatedTopics: List<EducationTopic> = emptyList(),
    val message: String? = null,
    val clarifications: List<String> = emptyList(),
    val category: ReminderEducationCategory? = null,
)
