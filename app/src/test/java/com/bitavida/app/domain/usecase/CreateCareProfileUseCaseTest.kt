package com.bitavida.app.domain.usecase

import com.bitavida.app.data.careprofile.InMemoryCareProfileRepository
import com.bitavida.app.domain.model.AuthorizationDeclaration
import com.bitavida.app.domain.model.CareProfileUsage
import com.bitavida.app.domain.model.ConsentStatus
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateCareProfileUseCaseTest {
    @Test
    fun rejectsMissingNamesAndAuthorizationDeclaration() {
        val useCase = CreateCareProfileUseCase(
            repository = InMemoryCareProfileRepository(),
            nowEpochMillis = { 100L },
            newId = idSequence(),
        )

        val result = useCase(
            CreateCareProfileCommand(
                caregiverName = " ",
                personUnderCareName = "",
                authorizationDeclared = false,
            ),
        )

        assertTrue(result is CreateCareProfileResult.Invalid)
        val errors = (result as CreateCareProfileResult.Invalid).errors
            .associate { it.field to it.error }
        assertEquals(
            CareProfileValidationError.REQUIRED,
            errors[CareProfileField.CAREGIVER_NAME],
        )
        assertEquals(
            CareProfileValidationError.REQUIRED,
            errors[CareProfileField.PERSON_UNDER_CARE_NAME],
        )
        assertEquals(
            CareProfileValidationError.AUTHORIZATION_REQUIRED,
            errors[CareProfileField.AUTHORIZATION_DECLARATION],
        )
    }

    @Test
    fun createsOnlyAnUnverifiedActiveTransientProfile() {
        val repository = InMemoryCareProfileRepository()
        val useCase = CreateCareProfileUseCase(
            repository = repository,
            nowEpochMillis = { 1234L },
            newId = idSequence(),
        )

        val result = useCase(
            CreateCareProfileCommand(
                caregiverName = "  Ana   Prueba ",
                personUnderCareName = " Elena   Prueba ",
                authorizationDeclared = true,
            ),
        )

        assertTrue(result is CreateCareProfileResult.Created)
        val profile = (result as CreateCareProfileResult.Created).profile
        assertEquals("Ana Prueba", profile.caregiver.displayName)
        assertEquals("Elena Prueba", profile.personUnderCare.displayName)
        assertEquals(
            AuthorizationDeclaration.CAREGIVER_DECLARED,
            profile.consent.authorizationDeclaration,
        )
        assertEquals(ConsentStatus.ACTIVE, profile.consent.status)
        assertTrue(profile.consent.isCurrent)
        assertEquals(CareProfileUsage.TRANSIENT_LOCAL, profile.usage)
        assertEquals(profile, repository.get())
    }

    @Test
    fun rejectsASecondProfileInTheSameRepository() {
        val repository = InMemoryCareProfileRepository()
        val useCase = CreateCareProfileUseCase(
            repository = repository,
            nowEpochMillis = { 200L },
            newId = idSequence(),
        )
        val command = CreateCareProfileCommand("Ana Prueba", "Elena Prueba", true)

        assertTrue(useCase(command) is CreateCareProfileResult.Created)
        assertEquals(
            CreateCareProfileResult.ProfileAlreadyExists,
            useCase(command),
        )
    }

    private fun idSequence(): () -> String {
        var value = 0
        return { "test-${++value}" }
    }
}
