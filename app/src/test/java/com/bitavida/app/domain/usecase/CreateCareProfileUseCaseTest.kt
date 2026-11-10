package com.bitavida.app.domain.usecase

import com.bitavida.app.data.careprofile.InMemoryCareProfileRepository
import com.bitavida.app.domain.model.AuthorizationDeclaration
import com.bitavida.app.domain.model.CURRENT_LEGAL_DOCUMENT_VERSION
import com.bitavida.app.domain.model.CareProfileUsage
import com.bitavida.app.domain.model.ConsentKind
import com.bitavida.app.domain.model.ConsentStatus
import com.bitavida.app.domain.model.LegalAcceptanceStatus
import com.bitavida.app.domain.model.LegalDocument
import com.bitavida.app.domain.model.LegalDocumentDecision
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileValidationError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateCareProfileUseCaseTest {
    @Test
    fun rejectsMissingNamesAndAuthorizationDeclaration() = runBlocking {
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
                termsAccepted = true,
                privacyNoticeAcknowledged = true,
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
    fun createsNormalizedEncryptedProfileWithVersionedLegalDecisions() = runBlocking {
        val repository = InMemoryCareProfileRepository()
        val useCase = CreateCareProfileUseCase(
            repository = repository,
            nowEpochMillis = { 1234L },
            newId = idSequence(),
        )

        val result = useCase(validCommand())

        assertTrue(result is CreateCareProfileResult.Created)
        val profile = (result as CreateCareProfileResult.Created).profile
        assertEquals("Ana Prueba", profile.caregiver.displayName)
        assertEquals("Elena Prueba", profile.personUnderCare.displayName)
        assertEquals(
            AuthorizationDeclaration.CAREGIVER_DECLARED,
            profile.consent.authorizationDeclaration,
        )
        assertEquals(ConsentKind.CAREGIVER_AUTHORIZATION_DECLARATION, profile.consent.kind)
        assertEquals(ConsentStatus.ACTIVE, profile.consent.status)
        assertTrue(profile.consent.isCurrent)
        assertEquals(CareProfileUsage.PERSISTED_ENCRYPTED_LOCAL, profile.usage)
        assertEquals(1234L, profile.createdAtEpochMillis)
        assertEquals(2, profile.legalAcceptances.size)
        assertTrue(
            profile.legalAcceptances.all {
                it.documentVersion == CURRENT_LEGAL_DOCUMENT_VERSION &&
                    it.status == LegalAcceptanceStatus.ACTIVE &&
                    it.recordedAtEpochMillis == 1234L
            },
        )
        assertEquals(
            LegalDocumentDecision.ACCEPTED,
            profile.legalAcceptances.single {
                it.document == LegalDocument.TERMS_OF_USE
            }.decision,
        )
        assertEquals(
            LegalDocumentDecision.ACKNOWLEDGED,
            profile.legalAcceptances.single {
                it.document == LegalDocument.PRIVACY_NOTICE
            }.decision,
        )
        assertEquals(profile, repository.get())
    }

    @Test
    fun rejectsASecondProfileInTheSameRepository() = runBlocking {
        val repository = InMemoryCareProfileRepository()
        val useCase = CreateCareProfileUseCase(
            repository = repository,
            nowEpochMillis = { 200L },
            newId = idSequence(),
        )

        assertTrue(useCase(validCommand()) is CreateCareProfileResult.Created)
        assertEquals(
            CreateCareProfileResult.ProfileAlreadyExists,
            useCase(validCommand()),
        )
    }

    private fun validCommand() = CreateCareProfileCommand(
        caregiverName = "  Ana   Prueba ",
        personUnderCareName = " Elena   Prueba ",
        authorizationDeclared = true,
        termsAccepted = true,
        privacyNoticeAcknowledged = true,
    )

    private fun idSequence(): () -> String {
        var value = 0
        return { "test-${++value}" }
    }
}
