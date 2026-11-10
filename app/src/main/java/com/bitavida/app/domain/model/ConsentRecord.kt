package com.bitavida.app.domain.model

/**
 * Records what the caregiver declared and the current state of that consent.
 *
 * [CAREGIVER_DECLARED] is not legal verification by Bitavida.
 */
data class ConsentRecord(
    val id: String,
    val caregiverId: String,
    val personUnderCareId: String,
    val authorizationDeclaration: AuthorizationDeclaration,
    val status: ConsentStatus,
    val recordedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
) {
    init {
        require(recordedAtEpochMillis >= 0L)
        require(
            (status == ConsentStatus.ACTIVE && revokedAtEpochMillis == null) ||
                (status == ConsentStatus.REVOKED && revokedAtEpochMillis != null),
        )
    }

    val isCurrent: Boolean
        get() = status == ConsentStatus.ACTIVE
}

enum class AuthorizationDeclaration {
    CAREGIVER_DECLARED,
}

enum class ConsentStatus {
    ACTIVE,
    REVOKED,
}
