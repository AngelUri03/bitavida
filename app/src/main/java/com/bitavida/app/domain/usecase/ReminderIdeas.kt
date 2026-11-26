package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.education.*

/** The creation picker reads the same knowledge catalogue as education, without persisting categories. */
data class ReminderIdeaGroup(
    val category: ReminderEducationCategory,
    val ideas: List<ReminderSuggestion>,
)

fun reminderIdeaGroups(ask: AskBitavidaAi): List<ReminderIdeaGroup> {
    val entries = EducationTopic.entries.filter { it.group == EducationGroup.REMINDERS }
        .map { ask.read(it).entry }
    val order = listOf(
        ReminderEducationCategory.SHOPPING, ReminderEducationCategory.DOCUMENTS,
        ReminderEducationCategory.FAMILY, ReminderEducationCategory.OUTINGS,
        ReminderEducationCategory.EVERYDAY, ReminderEducationCategory.TASKS, ReminderEducationCategory.APP,
    )
    return order.mapNotNull { category ->
        entries.filter { it.reminderCategory == category }.flatMap { it.reminderExamples }
            .distinctBy { EducationTokenizer.normalize(it.title) }
            .takeIf { it.isNotEmpty() }?.let { ReminderIdeaGroup(category, it) }
    }
}

/** A compact picker can prioritize titles already related to the declared need, without another model call. */
fun matchingReminderIdeas(
    groups: List<ReminderIdeaGroup>,
    selected: ReminderEducationCategory?,
    query: String,
): List<ReminderSuggestion> {
    val words = ideaWords(query)
    if (words.isEmpty()) return groups.firstOrNull { it.category == selected }?.ideas.orEmpty()
    return groups.flatMap { it.ideas }.distinctBy { EducationTokenizer.normalize(it.title) }
        .map { idea ->
            val titleWords = ideaWords(idea.title)
            val score = words.count { it in titleWords }
            idea to score
        }
        .filter { (_, score) -> score > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
}

private val ideaStopWords = setOf("de", "del", "el", "la", "los", "las", "para", "con", "mi", "mis", "un", "una")
private fun ideaWords(text: String): Set<String> = EducationTokenizer.normalize(text).split(' ')
    .filter { it.length > 2 && it !in ideaStopWords }.map {
        when (it) {
            "pilas", "bateria", "baterias" -> "pila"
            "papeles", "documentos", "documentacion" -> "documento"
            "suministros", "insumos", "articulos" -> "articulo"
            "familiares", "familia" -> "familiar"
            else -> it
        }
    }.toSet()
