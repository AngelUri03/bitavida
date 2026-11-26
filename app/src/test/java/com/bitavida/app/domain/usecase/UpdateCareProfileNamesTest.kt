package com.bitavida.app.domain.usecase

import com.bitavida.app.data.careprofile.InMemoryCareProfileRepository
import com.bitavida.app.domain.repository.UpdateCareProfileNamesCommand
import com.bitavida.app.domain.repository.UpdateCareProfileNamesResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateCareProfileNamesTest {
    @Test fun namesChangeWithoutReplacingIdentitiesAndStaleUpdateIsRejected() = runBlocking {
        val repository = InMemoryCareProfileRepository()
        val created = CreateCareProfileUseCase(repository)(
            CreateCareProfileCommand("Ana", "María", true, true, true)
        ) as CreateCareProfileResult.Created
        val original = created.profile
        val command = UpdateCareProfileNamesCommand(
            "operation-1", original.id, original.caregiver.id, original.personUnderCare.id,
            "Ana", "María", "  Ana   Nueva ", "María Nueva", 100L,
        )
        val useCase = UpdateCareProfileNames(repository)
        val first = useCase(command) as UpdateNamesResult.Finished
        val updated = (first.result as UpdateCareProfileNamesResult.Stored).profile
        assertEquals("Ana Nueva", updated.caregiver.displayName)
        assertEquals(original.caregiver.id, updated.caregiver.id)
        assertEquals(original.personUnderCare.id, updated.personUnderCare.id)
        assertEquals(original.consent, updated.consent)
        val stale = useCase(command.copy(operationId = "operation-2")) as UpdateNamesResult.Finished
        assertEquals(UpdateCareProfileNamesResult.Conflict, stale.result)
        val replay = useCase(command) as UpdateNamesResult.Finished
        assertTrue(replay.result is UpdateCareProfileNamesResult.Stored)
    }

    @Test fun invalidAndWrongIdentityDoNotChangeProfile() = runBlocking {
        val repository = InMemoryCareProfileRepository()
        val created = CreateCareProfileUseCase(repository)(
            CreateCareProfileCommand("Ana", "María", true, true, true)
        ) as CreateCareProfileResult.Created
        val original = created.profile
        val command = UpdateCareProfileNamesCommand(
            "operation-1", original.id, original.caregiver.id, original.personUnderCare.id,
            "Ana", "María", "A", "María", 100L,
        )
        assertTrue(UpdateCareProfileNames(repository)(command) is UpdateNamesResult.Invalid)
        val wrong = UpdateCareProfileNames(repository)(command.copy(caregiverId = "otro", caregiverName = "Ana Nueva")) as UpdateNamesResult.Finished
        assertEquals(UpdateCareProfileNamesResult.Unauthorized, wrong.result)
        assertEquals(original, repository.get())
    }
}
