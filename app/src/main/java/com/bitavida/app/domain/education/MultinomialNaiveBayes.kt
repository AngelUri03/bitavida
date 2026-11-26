package com.bitavida.app.domain.education

import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

/** The same linguistic preprocessing is used for development training and local inference. */
object EducationTokenizer {
    const val MAX_QUESTION_LENGTH = 500
    private val stopWords = (
        "a al de del el la las los un una unos unas y o con en mi mis me te tu tus su sus " +
            "este esta esto estos estas ese esa esos esas yo usted ustedes nos le les se " +
            "oye explicas explicame favor gracias quiero saber puedes podrias"
        ).split(" ").toSet()
    private val marksAndFormat = Regex("[\\p{M}\\p{Cf}]+")
    private val separators = Regex("[^a-z0-9]+")
    private val spaces = Regex("\\s+")
    private val letters = Regex("[a-z]+")
    private val becauseForms = setOf("porque", "porq", "pq", "xq", "pk", "xk", "porke", "porqe", "porqu")
    private val queForms = setOf("q", "k", "ke")
    private val suffixes = listOf(
        "amientos", "imientos", "amiento", "imiento", "aciones", "acion", "mente",
        "ando", "iendo", "ados", "adas", "idos", "idas", "aron", "ieron", "ado", "ada",
        "ido", "ida", "ar", "er", "ir", "es", "os", "as", "s",
    )

    /** Compatibility folding also handles full-width letters and typographic ligatures. */
    fun normalize(text: String): String = Normalizer.normalize(
        text.lowercase(Locale.ROOT), Normalizer.Form.NFKD,
    ).replace(marksAndFormat, "").replace(separators, " ").trim().replace(spaces, " ")

    fun words(text: String): List<String> =
        letters.findAll(normalize(text.take(MAX_QUESTION_LENGTH))).flatMap { match ->
            // General functional abbreviations; none identifies an educational topic.
            when (val word = match.value) {
                in becauseForms -> sequenceOf("por", "que")
                in queForms -> sequenceOf("que")
                "pa" -> sequenceOf("para")
                "x" -> sequenceOf("por")
                else -> sequenceOf(word)
            }
        }.toList()

    private fun stem(input: String): String {
        var word = input
        for (ending in listOf("nos", "los", "las", "me", "te", "se", "lo", "la")) {
            if (word.endsWith(ending)) {
                val base = word.dropLast(ending.length)
                if (base.endsWith("ar") || base.endsWith("er") || base.endsWith("ir")) {
                    word = base
                    break
                }
            }
        }
        for (ending in suffixes) {
            if (word.length - ending.length >= 4 && word.endsWith(ending)) return word.dropLast(ending.length)
        }
        return word
    }

    fun tokens(text: String): List<String> =
        words(text).filter { it.length > 1 && it !in stopWords }.map(::stem).take(80)

    fun features(tokens: List<String>): List<String> =
        tokens.map { "w:$it" } +
            tokens.zipWithNext { first, second -> "b:${first}_$second" } +
            tokens.filter { it.length >= 4 }.flatMap { word ->
                val bounded = "_${word}_"
                (0..bounded.length - 4).map { "c:" + bounded.substring(it, it + 4) }
            }
}

data class NaiveBayesParameters(
    val topics: List<EducationTopic>,
    val documentCounts: List<Int>,
    val tokenCounts: Map<String, List<Int>>,
    val alpha: Double = 0.1,
)

/**
 * Learned multinomial statistics for word unigrams, word bigrams and character four-grams.
 * Spelling support uses the learned vocabulary, never a phrase-to-intent table.
 */
class MultinomialNaiveBayes(parameters: NaiveBayesParameters) {
    private val topics = parameters.topics.toList()
    private val classCount = parameters.documentCounts.size
    private val vocabulary = parameters.tokenCounts.keys.toSet()
    private val wordVocabulary = vocabulary.filter { it.startsWith("w:") }.map { it.removePrefix("w:") }
    private val wordSet = wordVocabulary.toSet()
    private val wordFrequencies = wordVocabulary.associateWith { parameters.tokenCounts.getValue("w:$it").sum() }
    private val documentTotal = parameters.documentCounts.sum().toDouble()
    private val logPriors = parameters.documentCounts.map { ln(it / documentTotal) }
    private val totals = (0 until classCount).map { c -> parameters.tokenCounts.values.sumOf { it[c] } }
    private val logLikelihoods = parameters.tokenCounts.mapValues { (_, counts) ->
        DoubleArray(classCount) { c ->
            ln((counts[c] + parameters.alpha) / (totals[c] + parameters.alpha * vocabulary.size))
        }
    }

    fun classify(question: String): IntentPrediction {
        if (question.length > EducationTokenizer.MAX_QUESTION_LENGTH) return rejected()
        val tokens = EducationTokenizer.tokens(question).map(::repair)
        val distinct = tokens.toSet()
        val known = distinct.intersect(wordSet)
        // Long unknown content words must not be outweighed by many short known words.
        // This linguistic coverage measure applies to every topic and the background class.
        val totalMass = distinct.sumOf { it.length.coerceAtMost(12) }
        val coverage = if (totalMass == 0) 0.0 else known.sumOf { it.length.coerceAtMost(12) }.toDouble() / totalMass
        if (known.size < 2 || coverage < MIN_COVERAGE) return rejected(coverage)
        val frequencies = EducationTokenizer.features(tokens).groupingBy { it }.eachCount()
        val scores = (0 until classCount).map { c ->
            logPriors[c] + frequencies.entries.sumOf { (feature, count) ->
                val weight = if (feature.startsWith("w:")) 1.0 else 0.1
                (logLikelihoods[feature]?.get(c) ?: 0.0) * count.coerceAtMost(2) * weight
            }
        }
        val peak = scores.max()
        val exponentials = scores.map { exp(it - peak) }
        val sum = exponentials.sum()
        val probabilities = exponentials.map { it / sum }
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val winner = ranked[0]
        val best = probabilities[winner]
        val second = probabilities[ranked[1]]
        val suggestions = if (probabilities.last() < 0.5) ranked.filter { it < topics.size }
            .filter { probabilities[it] >= 0.015 }.take(2).map { topics[it] } else emptyList()
        val winningTopic = topics.getOrNull(winner)
        val requiredPosterior = if (winningTopic?.group == EducationGroup.MEDICATIONS) MIN_PHARMACY_POSTERIOR else MIN_POSTERIOR
        return IntentPrediction(
            topic = winningTopic?.takeIf { best >= requiredPosterior && best - second >= MIN_MARGIN },
            posterior = best,
            vocabularyCoverage = coverage,
            suggestions = suggestions,
        )
    }

    private fun repair(word: String): String {
        if (word in wordSet || word.length < 4) return word
        val candidates = wordVocabulary.filter { it.length >= 4 && oneEditApart(word, it) }
            .sortedByDescending { wordFrequencies.getValue(it) }
        return when {
            candidates.size == 1 -> candidates[0]
            candidates.size > 1 && wordFrequencies.getValue(candidates[0]) >=
                2 * wordFrequencies.getValue(candidates[1]) -> candidates[0]
            else -> word
        }
    }

    private fun oneEditApart(first: String, second: String): Boolean {
        if (kotlin.math.abs(first.length - second.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < first.length && j < second.length) {
            if (first[i] == second[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (first.length == second.length && i + 1 < first.length && j + 1 < second.length &&
                first[i] == second[j + 1] && first[i + 1] == second[j]) {
                i += 2; j += 2; continue
            }
            if (first.length >= second.length) i++
            if (second.length >= first.length) j++
        }
        return edits + first.length - i + second.length - j == 1
    }

    private fun rejected(coverage: Double = 0.0) = IntentPrediction(null, 0.0, coverage)

    companion object {
        // Selected against independent calibration; posterior is not a clinical confidence score.
        const val MIN_COVERAGE = 0.80
        const val MIN_POSTERIOR = 0.75
        // Additional pharmaceutical calibration requires more conservative intent selection.
        const val MIN_PHARMACY_POSTERIOR = 0.90
        const val MIN_MARGIN = 0.20
    }
}
