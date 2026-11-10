package com.bitavida.app.domain.model

/**
 * Person identified by the caregiver for the local care profile.
 */
data class PersonUnderCare(
    val id: String,
    val displayName: String,
    val declaredByCaregiverId: String,
    val declarationSource: ProfileDeclarationSource = ProfileDeclarationSource.CAREGIVER_DECLARATION,
)
