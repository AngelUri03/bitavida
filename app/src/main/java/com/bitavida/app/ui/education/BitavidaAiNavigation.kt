package com.bitavida.app.ui.education

import com.bitavida.app.domain.education.EducationEntry
import com.bitavida.app.domain.education.EducationGroup
import com.bitavida.app.domain.education.EducationResult
import com.bitavida.app.domain.education.EducationTokenizer
import com.bitavida.app.domain.validation.limitTextCharacters

enum class AiPage { HOME, ANSWER, LIBRARY, DETAIL, ABOUT, PROCEDURE_HOME, PROCEDURE_LIBRARY, PROCEDURE_DETAIL, PROCEDURE_ANSWER }

/** Session-only entries: never serialize questions or results into SavedState. */
data class AiPageEntry(
    val id: Long = 0,
    val page: AiPage = AiPage.HOME,
    val answer: EducationResult? = null,
    val submittedQuestion: String = "",
    val procedureId: String? = null,
    val procedureResult: com.bitavida.app.domain.education.ProcedureQuestionResult? = null,
    val procedureSection: String? = null,
)

fun filterEducationLibrary(
    entries: List<EducationEntry>,
    group: EducationGroup?,
    query: String,
    unicodeSearch: Boolean = false,
): List<EducationEntry> {
    val terms = EducationTokenizer.words(if (unicodeSearch) limitTextCharacters(query, 100) else query.take(100))
        .filter { it.isNotBlank() }
    return entries.filter { entry ->
        (group == null || entry.topic.group == group) && terms.all { term ->
            val text = EducationTokenizer.normalize(
                buildList {
                    add(entry.title)
                    add(entry.topic.question)
                    add(entry.explanation)
                    addAll(entry.searchTerms)
                    addAll(entry.possibleMeanings)
                    addAll(entry.whatToObserve)
                }.joinToString(" "),
            )
            term in text
        }
    }
}
