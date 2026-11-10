package com.bitavida.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareProfileValidatorTest {
    @Test
    fun acceptsInternationalCompoundNamesAndNormalizesOnlyOnConfirmation() {
        val rawName = "  María-José   O’Connor  "
        val unicodeName = "Zoë 李"

        assertNull(
            CareProfileValidator.validateName(rawName, CareProfileField.CAREGIVER_NAME),
        )
        assertNull(
            CareProfileValidator.validateName(
                unicodeName,
                CareProfileField.PERSON_UNDER_CARE_NAME,
            ),
        )
        assertEquals("María-José O’Connor", CareProfileValidator.normalizeName(rawName))
    }

    @Test
    fun reportsSpecificMinimumCharacterAndControlErrors() {
        assertEquals(
            CareProfileValidationError.REQUIRED,
            CareProfileValidator.validateName(
                "   ",
                CareProfileField.CAREGIVER_NAME,
            )?.error,
        )
        assertEquals(
            CareProfileValidationError.TOO_SHORT,
            CareProfileValidator.validateName(
                "A",
                CareProfileField.CAREGIVER_NAME,
            )?.error,
        )
        assertEquals(
            CareProfileValidationError.CONTROL_CHARACTERS_NOT_ALLOWED,
            CareProfileValidator.validateName(
                "Ana\nMaría",
                CareProfileField.CAREGIVER_NAME,
            )?.error,
        )
        assertEquals(
            CareProfileValidationError.INVALID_CHARACTERS,
            CareProfileValidator.validateName(
                "Ana <script>",
                CareProfileField.CAREGIVER_NAME,
            )?.error,
        )
    }

    @Test
    fun enforcesEightyGraphemesWithoutSplittingACombiningSequence() {
        val eightyLetters = "A".repeat(CARE_PROFILE_NAME_MAX_LENGTH)
        val overLimit = eightyLetters + "B"
        val combiningAtBoundary = "A".repeat(CARE_PROFILE_NAME_MAX_LENGTH - 1) +
            "e\u0301" +
            "Z"

        assertNull(
            CareProfileValidator.validateName(
                eightyLetters,
                CareProfileField.PERSON_UNDER_CARE_NAME,
            ),
        )
        assertEquals(
            CareProfileValidationError.TOO_LONG,
            CareProfileValidator.validateName(
                overLimit,
                CareProfileField.PERSON_UNDER_CARE_NAME,
            )?.error,
        )

        val limited = CareProfileValidator.limitNameInput(combiningAtBoundary)
        assertEquals(CARE_PROFILE_NAME_MAX_LENGTH, CareProfileValidator.nameLength(limited))
        assertEquals("e\u0301", limited.takeLast(2))
    }
}
