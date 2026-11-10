package com.bitavida.app.data.careprofile

import com.bitavida.app.domain.model.CareProfile
import com.bitavida.app.domain.repository.CareProfileRepository
import com.bitavida.app.domain.repository.CreateCareProfileStorageResult

/**
 * Process-local transient storage. It never writes personal or medical data to disk.
 */
class InMemoryCareProfileRepository : CareProfileRepository {
    private var profile: CareProfile? = null

    @Synchronized
    override fun create(profile: CareProfile): CreateCareProfileStorageResult {
        if (this.profile != null) {
            return CreateCareProfileStorageResult.ProfileAlreadyExists
        }
        this.profile = profile
        return CreateCareProfileStorageResult.Stored
    }

    @Synchronized
    override fun get(): CareProfile? = profile
}
