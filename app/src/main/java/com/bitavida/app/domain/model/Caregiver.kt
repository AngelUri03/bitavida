package com.bitavida.app.domain.model

/**
 * Identity declared by the person configuring the local care profile.
 *
 * It is not a verified identity and must not be treated as proof of legal representation.
 */
data class Caregiver(
    val id: String,
    val displayName: String,
    val declarationSource: ProfileDeclarationSource = ProfileDeclarationSource.CAREGIVER_DECLARATION,
)

enum class ProfileDeclarationSource {
    CAREGIVER_DECLARATION,
}
