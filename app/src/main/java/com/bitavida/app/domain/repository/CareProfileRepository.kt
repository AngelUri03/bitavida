package com.bitavida.app.domain.repository

import com.bitavida.app.domain.model.CareProfile

interface CareProfileRepository {
    suspend fun create(profile: CareProfile): CreateCareProfileStorageResult

    suspend fun get(): CareProfile?

    /** Performs identity-checked, audited name changes without replacing the relationship. */
    suspend fun updateNames(command: UpdateCareProfileNamesCommand): UpdateCareProfileNamesResult
}

data class UpdateCareProfileNamesCommand(
    val operationId: String,
    val profileId: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val expectedCaregiverName: String,
    val expectedPersonName: String,
    val caregiverName: String,
    val personName: String,
    val capturedAtEpochMillis: Long,
)

sealed interface UpdateCareProfileNamesResult {
    data class Stored(val profile: CareProfile) : UpdateCareProfileNamesResult
    data object Conflict : UpdateCareProfileNamesResult
    data object Unauthorized : UpdateCareProfileNamesResult
}

sealed interface CreateCareProfileStorageResult {
    data object Stored : CreateCareProfileStorageResult
    data object ProfileAlreadyExists : CreateCareProfileStorageResult
}

sealed class CareProfileRepositoryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class CareProfileKeyUnavailableException(
    cause: Throwable? = null,
) : CareProfileRepositoryException("The local encryption key is unavailable.", cause)

class CareProfileStorageUnavailableException(
    cause: Throwable? = null,
) : CareProfileRepositoryException("The encrypted profile storage is unavailable.", cause)

class CareProfileIntegrityException(
    cause: Throwable? = null,
) : CareProfileRepositoryException("The stored care profile is incomplete or invalid.", cause)
