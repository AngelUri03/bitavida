package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.repository.CareProfileRepository
import com.bitavida.app.domain.repository.UpdateCareProfileNamesCommand
import com.bitavida.app.domain.repository.UpdateCareProfileNamesResult
import com.bitavida.app.domain.validation.CareProfileField
import com.bitavida.app.domain.validation.CareProfileFieldError
import com.bitavida.app.domain.validation.CareProfileValidator

sealed interface UpdateNamesResult {
    data class Invalid(val errors: Set<CareProfileFieldError>) : UpdateNamesResult
    data class Finished(val result: UpdateCareProfileNamesResult) : UpdateNamesResult
}

class UpdateCareProfileNames(private val repository: CareProfileRepository) {
    suspend operator fun invoke(command: UpdateCareProfileNamesCommand): UpdateNamesResult {
        val errors = listOfNotNull(
            CareProfileValidator.validateName(command.caregiverName, CareProfileField.CAREGIVER_NAME),
            CareProfileValidator.validateName(command.personName, CareProfileField.PERSON_UNDER_CARE_NAME),
        ).toSet()
        if (errors.isNotEmpty()) return UpdateNamesResult.Invalid(errors)
        return UpdateNamesResult.Finished(repository.updateNames(command.copy(
            caregiverName = CareProfileValidator.normalizeName(command.caregiverName),
            personName = CareProfileValidator.normalizeName(command.personName),
        )))
    }
}
