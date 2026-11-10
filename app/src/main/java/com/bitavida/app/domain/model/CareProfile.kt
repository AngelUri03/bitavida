package com.bitavida.app.domain.model

/**
 * The single local relationship supported by the initial profile scope.
 */
data class CareProfile(
    val id: String,
    val caregiver: Caregiver,
    val personUnderCare: PersonUnderCare,
    val consent: ConsentRecord,
    val usage: CareProfileUsage = CareProfileUsage.TRANSIENT_LOCAL,
) {
    init {
        require(personUnderCare.declaredByCaregiverId == caregiver.id)
        require(consent.caregiverId == caregiver.id)
        require(consent.personUnderCareId == personUnderCare.id)
    }
}

enum class CareProfileUsage {
    TRANSIENT_LOCAL,
}
