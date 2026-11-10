package com.bitavida.app.domain.model

/**
 * The single encrypted local relationship supported by the current profile scope.
 */
data class CareProfile(
    val id: String,
    val caregiver: Caregiver,
    val personUnderCare: PersonUnderCare,
    val consent: ConsentRecord,
    val legalAcceptances: List<LegalAcceptanceRecord>,
    val createdAtEpochMillis: Long,
    val usage: CareProfileUsage = CareProfileUsage.PERSISTED_ENCRYPTED_LOCAL,
) {
    init {
        require(id.isNotBlank())
        require(createdAtEpochMillis >= 0L)
        require(personUnderCare.declaredByCaregiverId == caregiver.id)
        require(consent.caregiverId == caregiver.id)
        require(consent.personUnderCareId == personUnderCare.id)
        require(legalAcceptances.all { it.actorId == caregiver.id })
    }
}

enum class CareProfileUsage {
    PERSISTED_ENCRYPTED_LOCAL,
}
