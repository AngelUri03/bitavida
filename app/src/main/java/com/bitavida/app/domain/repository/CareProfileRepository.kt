package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.CareProfile

interface CareProfileRepository {
    fun create(profile: CareProfile): CreateCareProfileStorageResult

    fun get(): CareProfile?
}

sealed interface CreateCareProfileStorageResult {
    data object Stored : CreateCareProfileStorageResult
    data object ProfileAlreadyExists : CreateCareProfileStorageResult
}
