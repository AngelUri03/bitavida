package com.bitavida.app.domain.validation

import java.util.regex.Pattern

/** Unicode extended graphemes: accents, surrogate pairs, flags and joined emoji count as one. */
private val textGrapheme = Pattern.compile("\\X")

fun textCharacterCount(value: String): Int {
    val matches = textGrapheme.matcher(value)
    var count = 0
    while (matches.find()) count++
    return count
}

/** Used only for transient queries; persisted text is never shortened on read or save. */
fun limitTextCharacters(value: String, maximum: Int): String {
    require(maximum >= 0)
    val matches = textGrapheme.matcher(value)
    var count = 0
    var end = 0
    while (count < maximum && matches.find()) {
        end = matches.end()
        count++
    }
    return value.substring(0, end)
}

/** Historical text may remain unchanged; any replacement must meet today's capture policy. */
fun fitsTextLimit(value: String, maximum: Int, original: String? = null): Boolean =
    (original != null && value == original) ||
        (hasCompleteTextCharacters(value) && textCharacterCount(value) <= maximum)

internal fun hasCompleteTextCharacters(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (Character.isHighSurrogate(character)) {
            if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
            index += 2
        } else {
            if (Character.isLowSurrogate(character)) return false
            index++
        }
    }
    return true
}

internal fun isHistoricalTextDeletion(previous: String, proposed: String): Boolean {
    if (!hasCompleteTextCharacters(proposed) || textCharacterCount(proposed) >= textCharacterCount(previous)) return false
    val before = textGrapheme.matcher(previous)
    val after = textGrapheme.matcher(proposed)
    var needsMatch = after.find()
    while (needsMatch && before.find()) {
        if (before.group() == after.group()) needsMatch = after.find()
    }
    return !needsMatch
}
