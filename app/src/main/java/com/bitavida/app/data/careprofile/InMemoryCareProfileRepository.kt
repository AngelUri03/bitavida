package com.bitavida.app.data.careprofile

import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.repository.CareProfileRepository
import com.bitavida.app.domain.repository.CreateCareProfileStorageResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Test-only process-local storage. Production uses encrypted Room persistence.
 */
class InMemoryCareProfileRepository : CareProfileRepository {
    private val mutex = Mutex()
    private var profile: CareProfile? = null
    private val revisions = mutableMapOf<String, com.bitavida.app.domain.repository.UpdateCareProfileNamesCommand>()

    override suspend fun create(profile: CareProfile): CreateCareProfileStorageResult =
        mutex.withLock {
            if (this.profile != null) {
                return@withLock CreateCareProfileStorageResult.ProfileAlreadyExists
            }
            this.profile = profile
            CreateCareProfileStorageResult.Stored
        }

    override suspend fun get(): CareProfile? = mutex.withLock { profile }

    override suspend fun updateNames(command: com.bitavida.app.domain.repository.UpdateCareProfileNamesCommand):
        com.bitavida.app.domain.repository.UpdateCareProfileNamesResult = mutex.withLock {
        val current = profile ?: return@withLock com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Unauthorized
        if (current.id != command.profileId || current.caregiver.id != command.caregiverId ||
            current.personUnderCare.id != command.personUnderCareId || !current.consent.isCurrent)
            return@withLock com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Unauthorized
        val prior = revisions[command.operationId]
        if (prior != null) return@withLock if (prior == command)
            com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Stored(current)
        else com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Conflict
        if (current.caregiver.displayName != command.expectedCaregiverName ||
            current.personUnderCare.displayName != command.expectedPersonName)
            return@withLock com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Conflict
        val updated = current.copy(
            caregiver = current.caregiver.copy(displayName = command.caregiverName),
            personUnderCare = current.personUnderCare.copy(displayName = command.personName),
        )
        if (current != updated) revisions[command.operationId] = command
        profile = updated
        com.bitavida.app.domain.repository.UpdateCareProfileNamesResult.Stored(updated)
    }
}
