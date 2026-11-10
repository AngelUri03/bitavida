package com.bitavida.app.domain.validation

import java.text.BreakIterator
import java.util.Locale

const val CARE_PROFILE_NAME_MIN_SIGNIFICANT_LENGTH = 2
const val CARE_PROFILE_NAME_MAX_LENGTH = 80
const val CARE_PROFILE_NAME_COUNTER_THRESHOLD = 72

enum class CareProfileField {
    CAREGIVER_NAME,
    PERSON_UNDER_CARE_NAME,
    AUTHORIZATION_DECLARATION,
}

enum class CareProfileValidationError {
    REQUIRED,
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    CONTROL_CHARACTERS_NOT_ALLOWED,
    AUTHORIZATION_REQUIRED,
}

data class CareProfileFieldError(
    val field: CareProfileField,
    val error: CareProfileValidationError,
)

object CareProfileValidator {
    fun validateName(
        value: String,
        field: CareProfileField,
    ): CareProfileFieldError? {
        require(field != CareProfileField.AUTHORIZATION_DECLARATION)
        val normalized = normalizeName(value)
        return when {
            normalized.isEmpty() ->
                CareProfileFieldError(field, CareProfileValidationError.REQUIRED)
            containsDisallowedControl(value) ->
                CareProfileFieldError(
                    field,
                    CareProfileValidationError.CONTROL_CHARACTERS_NOT_ALLOWED,
                )
            nameLength(value) > CARE_PROFILE_NAME_MAX_LENGTH ->
                CareProfileFieldError(field, CareProfileValidationError.TOO_LONG)
            hasInvalidNameCharacters(value) ->
                CareProfileFieldError(field, CareProfileValidationError.INVALID_CHARACTERS)
            significantCharacterCount(normalized) < CARE_PROFILE_NAME_MIN_SIGNIFICANT_LENGTH ->
                CareProfileFieldError(field, CareProfileValidationError.TOO_SHORT)
            else -> null
        }
    }

    fun validate(
        caregiverName: String,
        personUnderCareName: String,
        authorizationDeclared: Boolean,
    ): Set<CareProfileFieldError> = buildSet {
        validateName(caregiverName, CareProfileField.CAREGIVER_NAME)?.let(::add)
        validateName(personUnderCareName, CareProfileField.PERSON_UNDER_CARE_NAME)?.let(::add)
        if (!authorizationDeclared) {
            add(
                CareProfileFieldError(
                    CareProfileField.AUTHORIZATION_DECLARATION,
                    CareProfileValidationError.AUTHORIZATION_REQUIRED,
                ),
            )
        }
    }

    fun nameLength(value: String): Int {
        val iterator = characterIterator(value)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count += 1
        return count
    }

    fun limitNameInput(value: String): String {
        val iterator = characterIterator(value)
        var boundary = iterator.first()
        repeat(CARE_PROFILE_NAME_MAX_LENGTH) {
            boundary = iterator.next()
            if (boundary == BreakIterator.DONE) return value
        }
        return value.substring(0, boundary)
    }

    fun normalizeName(value: String): String {
        val normalized = StringBuilder(value.length)
        var pendingSpace = false
        value.codePoints().forEach { codePoint ->
            if (isNameSpace(codePoint)) {
                if (normalized.isNotEmpty()) pendingSpace = true
            } else {
                if (pendingSpace) normalized.append(' ')
                normalized.appendCodePoint(codePoint)
                pendingSpace = false
            }
        }
        return normalized.toString()
    }

    private fun significantCharacterCount(value: String): Int {
        val iterator = characterIterator(value)
        var start = iterator.first()
        var count = 0
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (value.substring(start, end).codePoints().anyMatch(Character::isLetter)) {
                count += 1
            }
            start = end
            end = iterator.next()
        }
        return count
    }

    private fun containsDisallowedControl(value: String): Boolean =
        value.codePoints().anyMatch { codePoint ->
            when (Character.getType(codePoint)) {
                Character.CONTROL.toInt(),
                Character.FORMAT.toInt(),
                Character.SURROGATE.toInt(),
                Character.LINE_SEPARATOR.toInt(),
                Character.PARAGRAPH_SEPARATOR.toInt(),
                -> true
                else -> false
            }
        }

    private fun hasInvalidNameCharacters(value: String): Boolean {
        val iterator = characterIterator(value)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            var clusterHasLetter = false
            var clusterHasMark = false
            val codePoints = value.substring(start, end).codePoints().toArray()
            for (codePoint in codePoints) {
                when {
                    Character.isLetter(codePoint) -> clusterHasLetter = true
                    isCombiningMark(codePoint) -> clusterHasMark = true
                    isNameSpace(codePoint) || isApostrophe(codePoint) || isDash(codePoint) -> Unit
                    else -> return true
                }
            }
            if (clusterHasMark && !clusterHasLetter) return true
            start = end
            end = iterator.next()
        }
        return false
    }

    private fun characterIterator(value: String): BreakIterator =
        BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(value) }

    private fun isNameSpace(codePoint: Int): Boolean =
        Character.getType(codePoint) == Character.SPACE_SEPARATOR.toInt()

    private fun isCombiningMark(codePoint: Int): Boolean =
        when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            -> true
            else -> false
        }

    private fun isApostrophe(codePoint: Int): Boolean =
        when (codePoint) {
            0x27, 0x2019, 0x02BC -> true
            else -> false
        }

    private fun isDash(codePoint: Int): Boolean =
        Character.getType(codePoint) == Character.DASH_PUNCTUATION.toInt()
}
