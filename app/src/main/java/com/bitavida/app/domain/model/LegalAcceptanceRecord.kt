package com.bitavida.app.domain.model

/**
 * Future auditable record for a legal document decision.
 *
 * The phase 3.1 onboarding does not create or persist this record.
 */
data class LegalAcceptanceRecord(
    val id: String,
    val actorId: String,
    val document: LegalDocument,
    val documentVersion: String,
    val decision: LegalDocumentDecision,
    val recordedAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank())
        require(actorId.isNotBlank())
        require(documentVersion.isNotBlank())
        require(recordedAtEpochMillis >= 0L)
        val expectedDecision = when (document) {
            LegalDocument.TERMS_OF_USE -> LegalDocumentDecision.ACCEPTED
            LegalDocument.PRIVACY_NOTICE -> LegalDocumentDecision.ACKNOWLEDGED
        }
        require(decision == expectedDecision)
    }
}

enum class LegalDocument {
    TERMS_OF_USE,
    PRIVACY_NOTICE,
}

enum class LegalDocumentDecision {
    ACCEPTED,
    ACKNOWLEDGED,
}
