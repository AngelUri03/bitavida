package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.AuthorizationDeclaration
import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.model.Caregiver
import com.bitavida.app.domain.model.ConsentRecord
import com.bitavida.app.domain.model.ConsentStatus
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
)

sealed interface CreateCareProfileResult {
    data class Created(val profile: CareProfile) : CreateCareProfileResult
    data class Invalid(val errors: Set<CareProfileFieldError>) : CreateCareProfileResult
    data object ProfileAlreadyExists : CreateCareProfileResult
}

class CreateCareProfileUseCase(
    private val repository: CareProfileRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    operator fun invoke(command: CreateCareProfileCommand): CreateCareProfileResult {
        val errors = CareProfileValidator.validate(
            caregiverName = command.caregiverName,
            personUnderCareName = command.personUnderCareName,
            authorizationDeclared = command.authorizationDeclared,
        )
        if (errors.isNotEmpty()) return CreateCareProfileResult.Invalid(errors)

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
                authorizationDeclaration = AuthorizationDeclaration.CAREGIVER_DECLARED,
                status = ConsentStatus.ACTIVE,
                recordedAtEpochMillis = nowEpochMillis(),
            ),
        )

        return when (repository.create(profile)) {
            CreateCareProfileStorageResult.Stored -> CreateCareProfileResult.Created(profile)
            CreateCareProfileStorageResult.ProfileAlreadyExists ->
                CreateCareProfileResult.ProfileAlreadyExists
        }
    }
}
