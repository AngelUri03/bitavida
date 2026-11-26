package com.bitavida.app.domain.education

/** Educational content only: no patient records, inference, scheduling, or care recommendations. */
enum class ProcedureCategory(val label: String) {
    CONSULTS("Consultas y seguimiento"),
    LABS("Análisis y laboratorio"),
    EYES("Ojos y visión"),
    FEET("Pies y circulación"),
    HEART("Corazón y vasos"),
    KIDNEYS("Riñones"),
    VACCINES("Vacunación"),
    SURGERY("Cirugías y procedimientos"),
}

/** Optional, topic-specific educational material and its supporting source URLs. */
data class ProcedureGuideSection(
    val id: String,
    val title: String,
    val body: String,
    val sourceReferences: List<String> = emptyList(),
)

/** A documented question points to a guide section; no generated answer is implied. */
data class ProcedureRelatedQuestion(val id: String, val question: String, val sectionId: String)

data class ProcedureGuide(
    val id: String,
    val title: String,
    val aliases: List<String>,
    val categories: List<ProcedureCategory>,
    val context: String,
    val description: String,
    val purpose: String,
    val during: String,
    val method: String,
    val preparation: String,
    val after: String,
    val questions: List<String>,
    val important: String,
    val source: EducationSource,
    val reviewedOn: String = "Fecha no documentada",
    val reviewStatus: EducationReviewStatus = EducationReviewStatus.PENDING_HUMAN_REVIEW,
    val summary: String? = null,
    val extraSections: List<ProcedureGuideSection> = emptyList(),
    val additionalSources: List<EducationSource> = emptyList(),
    val contentVersion: String? = null,
    val sectionSourceReferences: Map<String, List<String>> = emptyMap(),
    val relatedQuestions: List<ProcedureRelatedQuestion> = emptyList(),
)
