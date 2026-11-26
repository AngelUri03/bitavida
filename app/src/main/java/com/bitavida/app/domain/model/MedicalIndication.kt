package com.bitavida.app.domain.model

import java.time.LocalDate

enum class ClinicalContentMode {
    FICTITIOUS_DEVELOPMENT,
    REAL_CLINICAL_DATA,
}

enum class MedicalIndicationStatus {
    ACTIVE,
    SUSPENDED,
    COMPLETED,
}

enum class MedicalIndicationChangeKind {
    CREATED,
    CORRECTED,
    STATUS_CHANGED,
}

data class MedicalIndicationRevision(
    val id: String,
    val indicationId: String,
    val revisionNumber: Int,
    val originalText: String,
    val indicatedOn: LocalDate,
    val capturedAtEpochMillis: Long,
    val declaredProfessionalOrSource: String?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val status: MedicalIndicationStatus,
    val changeKind: MedicalIndicationChangeKind,
    val changeReason: String?,
    val authorActorId: String,
) {
    init {
        require(id.isNotBlank())
        require(indicationId.isNotBlank())
        require(revisionNumber > 0)
        require(originalText.isNotBlank())
        require(originalText.length <= 4_000)
        require(capturedAtEpochMillis >= 0L)
        require(declaredProfessionalOrSource == null || declaredProfessionalOrSource.length <= 200)
        require(validFrom == null || validUntil == null || !validUntil.isBefore(validFrom))
        require(authorActorId.isNotBlank())
        require(changeKind == MedicalIndicationChangeKind.CREATED || !changeReason.isNullOrBlank())
    }
}

data class MedicalIndication(
    val id: String,
    val personUnderCareId: String,
    val origin: InformationOrigin,
    val createdAtEpochMillis: Long,
    val createdByActorId: String,
    val contentMode: ClinicalContentMode,
    val revisions: List<MedicalIndicationRevision>,
) {
    init {
        require(id.isNotBlank())
        require(personUnderCareId.isNotBlank())
        require(origin == InformationOrigin.MEDICAL_PRESCRIPTION_TRANSCRIPTION)
        require(createdAtEpochMillis >= 0L)
        require(createdByActorId.isNotBlank())
        require(revisions.isNotEmpty())
        require(revisions.all { it.indicationId == id })
        require(revisions.map { it.revisionNumber } == (1..revisions.size).toList())
    }

    val currentRevision: MedicalIndicationRevision
        get() = revisions.last()
}
