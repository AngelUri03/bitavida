package com.bitavida.app.domain.education

enum class DrugIntent {
    IDENTIFY, PURPOSE, MECHANISM, SIDE_EFFECTS, CONTRAINDICATIONS, PRECAUTIONS,
    INTERACTIONS, FORMULATIONS, CONSULTATION,
}

data class DrugEducationSection(val intent: DrugIntent, val title: String, val body: String)

/** A curated question leads only to an existing, non-generative educational section. */
data class DrugRelatedQuestion(val question: String, val intent: DrugIntent)

data class DrugReference(
    val institution: String,
    val document: String,
    val url: String,
    val version: String,
    val reviewedOn: String = "Fecha no documentada",
)

/** Original summaries of named, cited formulations, never an individual treatment assessment. */
data class DrugEducation(
    val id: String,
    val name: String,
    val family: String,
    val aliases: List<String>,
    val summary: String,
    val sections: List<DrugEducationSection>,
    val sources: List<DrugReference>,
    val availabilityNote: String = "Disponibilidad y autorización en México no verificadas.",
    val statusNote: String = "Información educativa pendiente de revisión clínica.",
    val relatedIds: List<String> = emptyList(),
    /** Generic aliases that do not confirm this formulation without a person choosing it. */
    val confirmationAliases: List<String> = emptyList(),
    val relatedQuestions: List<DrugRelatedQuestion> = emptyList(),
)

/** Approximate spelling may only trigger abstention and safety checks, never expose a drug candidate. */
data class DrugResolution(
    val exact: List<DrugEducation>,
    val intentQuestion: String,
    val requiresConfirmation: Boolean = false,
    val unresolvedSimilarName: Boolean = false,
)

/**
 * Resolves only curated generic names/aliases. It never infers an ingredient from a trade name.
 * Combination/formulation aliases take precedence over their contained ingredient names.
 */
class DrugNameResolver(catalog: List<DrugEducation>) {
    private data class Alias(val drug: DrugEducation, val text: String)
    private val aliases = catalog.flatMap { drug ->
        (drug.aliases + drug.name).map { Alias(drug, EducationTokenizer.normalize(it)) }
    }.filter { it.text.isNotBlank() }.distinctBy { it.drug.id to it.text }
    private val wordBoundary = Regex("\\s+")

    fun registered(name: String): List<DrugEducation> {
        val normalized = EducationTokenizer.normalize(name)
        return aliases.filter { it.text == normalized }.map { it.drug }.distinctBy { it.id }
    }

    fun requiresRegisteredConfirmation(name: String): Boolean {
        val normalized = EducationTokenizer.normalize(name)
        return registered(name).any { drug ->
            drug.confirmationAliases.any { EducationTokenizer.normalize(it) == normalized }
        }
    }

    fun resolve(question: String): DrugResolution {
        val normalized = EducationTokenizer.normalize(question)
        val padded = " $normalized "
        val matches = aliases.filter { padded.contains(" ${it.text} ") }
        val longest = matches.filter { match ->
            matches.none { other -> other.text.length > match.text.length &&
                " ${other.text} ".contains(" ${match.text} ") }
        }
        val exact = longest.map { it.drug }.distinctBy { it.id }
        var intentQuestion = normalized
        longest.map { it.text }.distinct().sortedByDescending { it.length }.forEach { alias ->
            intentQuestion = (" $intentQuestion ").replace(" $alias ", " medicamento ").trim()
        }
        if (exact.isNotEmpty()) return DrugResolution(exact, intentQuestion = intentQuestion,
            requiresConfirmation = longest.any { match ->
                match.drug.confirmationAliases.any { EducationTokenizer.normalize(it) == match.text }
            })
        val words = normalized.split(wordBoundary)
        // The signal deliberately carries no identity. It keeps the safety guard conservative
        // and makes the caller abstain rather than link a guide based on spelling similarity.
        val unresolvedSimilarName = aliases.any { alias ->
            val target = alias.text.split(wordBoundary)
            target.size == 1 && target[0].length >= 5 &&
                words.any { it.length >= 5 && oneEditApart(it, target[0]) }
        }
        return DrugResolution(emptyList(), intentQuestion = normalized, unresolvedSimilarName = unresolvedSimilarName)
    }

    private fun oneEditApart(a: String, b: String): Boolean {
        if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (a.length == b.length && i + 1 < a.length && j + 1 < b.length &&
                a[i] == b[j + 1] && a[i + 1] == b[j]) { i += 2; j += 2; continue }
            if (a.length >= b.length) i++
            if (b.length >= a.length) j++
        }
        return edits + a.length - i + b.length - j == 1
    }
}

fun EducationTopic.drugIntentOrNull(): DrugIntent? = when (this) {
    EducationTopic.MEDICATION_IDENTIFY -> DrugIntent.IDENTIFY
    EducationTopic.MEDICATION_PURPOSE -> DrugIntent.PURPOSE
    EducationTopic.MEDICATION_MECHANISM -> DrugIntent.MECHANISM
    EducationTopic.MEDICATION_SIDE_EFFECTS -> DrugIntent.SIDE_EFFECTS
    EducationTopic.MEDICATION_CONTRAINDICATIONS -> DrugIntent.CONTRAINDICATIONS
    EducationTopic.MEDICATION_PRECAUTIONS -> DrugIntent.PRECAUTIONS
    EducationTopic.MEDICATION_INTERACTIONS -> DrugIntent.INTERACTIONS
    EducationTopic.MEDICATION_FORMULATIONS -> DrugIntent.FORMULATIONS
    EducationTopic.MEDICATION_CONSULTATION -> DrugIntent.CONSULTATION
    else -> null
}
