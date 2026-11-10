package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.repository.CareProfileRepository

class GetCareProfileUseCase(
    private val repository: CareProfileRepository,
) {
    operator fun invoke(): CareProfile? = repository.get()
}
