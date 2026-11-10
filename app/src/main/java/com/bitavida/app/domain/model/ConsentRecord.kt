package com.bitavida.app.domain.model

/**
 * Records the caregiver's declaration and its current status.
 *
 * A caregiver declaration is not verified legal representation and is distinct
 * from any future explicit consent for sensitive data.
 */
data class ConsentRecord(
    val id: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val kind: ConsentKind,
    val authorizationDeclaration: AuthorizationDeclaration,
    val status: ConsentStatus,
    val recordedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(recordedAtEpochMillis >= 0L)
        require(kind == ConsentKind.CAREGIVER_AUTHORIZATION_DECLARATION)
        require(
            (status == ConsentStatus.ACTIVE && revokedAtEpochMillis == null) ||
                (
                    status == ConsentStatus.REVOKED &&
                        revokedAtEpochMillis != null &&
                        revokedAtEpochMillis >= recordedAtEpochMillis
                    ),
        )
    }

    val isCurrent: Boolean
        get() = status == ConsentStatus.ACTIVE
}

enum class ConsentKind {
    CAREGIVER_AUTHORIZATION_DECLARATION,
    SENSITIVE_DATA_EXPLICIT_CONSENT,
}

enum class AuthorizationDeclaration {
    CAREGIVER_DECLARED,
}

enum class ConsentStatus {
    ACTIVE,
    REVOKED,
}
