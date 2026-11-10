package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.AuthorizationDeclaration
import com.bitavida.app.domain.model.CURRENT_LEGAL_DOCUMENT_VERSION
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.model.Caregiver
import com.bitavida.app.domain.model.ConsentKind
import com.bitavida.app.domain.model.ConsentRecord
import com.bitavida.app.domain.model.ConsentStatus
import com.bitavida.app.domain.model.LegalAcceptanceRecord
import com.bitavida.app.domain.model.LegalAcceptanceStatus
import com.bitavida.app.domain.model.LegalDocument
import com.bitavida.app.domain.model.LegalDocumentDecision
import com.bitavida.app.domain.model.PersonUnderCare
import com.bitavida.app.domain.repository.CareProfileRepository
import com.bitavida.app.domain.repository.CreateCareProfileStorageResult
import com.bitavida.app.domain.validation.CareProfileFieldError
import com.bitavida.app.domain.validation.CareProfileValidator
import java.util.UUID

data class CreateCareProfileCommand(
    val caregiverName: String,
    val personUnderCareName: String,
    val authorizationDeclared: Boolean,
    val termsAccepted: Boolean,
    val privacyNoticeAcknowledged: Boolean,
    val legalDocumentVersion: String = CURRENT_LEGAL_DOCUMENT_VERSION,
)

sealed interface CreateCareProfileResult {
    data class Created(val profile: CareProfile) : CreateCareProfileResult
    data class Invalid(val errors: Set<CareProfileFieldError>) : CreateCareProfileResult
    data class MissingLegalDecisions(
        val documents: Set<LegalDocument>,
    ) : CreateCareProfileResult
    data object ProfileAlreadyExists : CreateCareProfileResult
}

class CreateCareProfileUseCase(
    private val repository: CareProfileRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(command: CreateCareProfileCommand): CreateCareProfileResult {
        val errors = CareProfileValidator.validate(
            caregiverName = command.caregiverName,
            personUnderCareName = command.personUnderCareName,
            authorizationDeclared = command.authorizationDeclared,
        )
        if (errors.isNotEmpty()) return CreateCareProfileResult.Invalid(errors)

        val missingLegalDecisions = buildSet {
            if (!command.termsAccepted) add(LegalDocument.TERMS_OF_USE)
            if (!command.privacyNoticeAcknowledged) add(LegalDocument.PRIVACY_NOTICE)
        }
        if (missingLegalDecisions.isNotEmpty()) {
            return CreateCareProfileResult.MissingLegalDecisions(missingLegalDecisions)
        }

        val recordedAt = nowEpochMillis()
        val caregiver = Caregiver(
            id = newId(),
            displayName = CareProfileValidator.normalizeName(command.caregiverName),
        )
        val personUnderCare = PersonUnderCare(
            id = newId(),
            displayName = CareProfileValidator.normalizeName(command.personUnderCareName),
            declaredByCaregiverId = caregiver.id,
        )
        val profile = CareProfile(
            id = newId(),
            caregiver = caregiver,
            personUnderCare = personUnderCare,
            consent = ConsentRecord(
                id = newId(),
                caregiverId = caregiver.id,
                personUnderCareId = personUnderCare.id,
                kind = ConsentKind.CAREGIVER_AUTHORIZATION_DECLARATION,
                authorizationDeclaration = AuthorizationDeclaration.CAREGIVER_DECLARED,
                status = ConsentStatus.ACTIVE,
                recordedAtEpochMillis = recordedAt,
            ),
            legalAcceptances = listOf(
                LegalAcceptanceRecord(
                    id = newId(),
                    actorId = caregiver.id,
                    document = LegalDocument.TERMS_OF_USE,
                    documentVersion = command.legalDocumentVersion,
                    decision = LegalDocumentDecision.ACCEPTED,
                    status = LegalAcceptanceStatus.ACTIVE,
                    recordedAtEpochMillis = recordedAt,
                ),
                LegalAcceptanceRecord(
                    id = newId(),
                    actorId = caregiver.id,
                    document = LegalDocument.PRIVACY_NOTICE,
                    documentVersion = command.legalDocumentVersion,
                    decision = LegalDocumentDecision.ACKNOWLEDGED,
                    status = LegalAcceptanceStatus.ACTIVE,
                    recordedAtEpochMillis = recordedAt,
                ),
            ),
            createdAtEpochMillis = recordedAt,
        )

        return when (repository.create(profile)) {
            CreateCareProfileStorageResult.Stored -> CreateCareProfileResult.Created(profile)
            CreateCareProfileStorageResult.ProfileAlreadyExists ->
                CreateCareProfileResult.ProfileAlreadyExists
        }
    }
}
