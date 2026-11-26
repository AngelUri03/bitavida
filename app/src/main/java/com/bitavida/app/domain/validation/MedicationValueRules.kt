package com.bitavida.app.domain.validation

/** Capture rules only: persisted values remain readable under their original format. */
internal object MedicationValueRules {
    const val MAX_LENGTH = 8

    fun acceptsInput(value: String): Boolean =
        value.length <= MAX_LENGTH &&
            value.all { it in '0'..'9' || it == '/' } &&
            value.count { it == '/' } <= 1

    fun isValid(value: String): Boolean =
        value.isNotEmpty() && acceptsInput(value) &&
            value.split('/').all { part ->
                part.isNotEmpty() && part.any { it in '1'..'9' }
            }

    /** An unchanged stored value may accompany a correction of unrelated data. */
    fun isValidReplacement(value: String, originalValue: String?): Boolean =
        isValid(value) || (value.isNotEmpty() && value == originalValue)
}
