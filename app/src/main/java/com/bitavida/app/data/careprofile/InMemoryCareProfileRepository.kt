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

    override suspend fun create(profile: CareProfile): CreateCareProfileStorageResult =
        mutex.withLock {
            if (this.profile != null) {
                return@withLock CreateCareProfileStorageResult.ProfileAlreadyExists
            }
            this.profile = profile
            CreateCareProfileStorageResult.Stored
        }

    override suspend fun get(): CareProfile? = mutex.withLock { profile }
}
