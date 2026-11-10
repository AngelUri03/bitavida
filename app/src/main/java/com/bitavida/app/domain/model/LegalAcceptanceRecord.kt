package com.bitavida.app.domain.model

const val CURRENT_LEGAL_DOCUMENT_VERSION = "2026.11"

/**
 * Auditable local decision for a versioned legal document.
 */
data class LegalAcceptanceRecord(
    val id: String,
    val actorId: String,
    val document: LegalDocument,
    val documentVersion: String,
    val decision: LegalDocumentDecision,
    val status: LegalAcceptanceStatus,
    val recordedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(actorId.isNotBlank())
        require(documentVersion.isNotBlank())
        require(recordedAtEpochMillis >= 0L)
        require(
            (status == LegalAcceptanceStatus.ACTIVE && revokedAtEpochMillis == null) ||
                (
                    status == LegalAcceptanceStatus.REVOKED &&
                        revokedAtEpochMillis != null &&
                        revokedAtEpochMillis >= recordedAtEpochMillis
                    ),
        )
        val expectedDecision = when (document) {
            LegalDocument.TERMS_OF_USE -> LegalDocumentDecision.ACCEPTED
            LegalDocument.PRIVACY_NOTICE -> LegalDocumentDecision.ACKNOWLEDGED
        }
        require(decision == expectedDecision)
    }

    val isCurrent: Boolean
        get() = status == LegalAcceptanceStatus.ACTIVE
}

enum class LegalDocument {
    TERMS_OF_USE,
    PRIVACY_NOTICE,
}

enum class LegalDocumentDecision {
    ACCEPTED,
    ACKNOWLEDGED,
}

enum class LegalAcceptanceStatus {
    ACTIVE,
    REVOKED,
}
