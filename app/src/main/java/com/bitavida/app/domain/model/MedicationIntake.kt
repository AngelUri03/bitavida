package com.bitavida.app.domain.model

import java.time.LocalDateTime

enum class MedicationIntakeOutcome {
    COMPLETED,
    PARTIAL,
    OMITTED,
    REFUSED,
    UNCONFIRMED,
}

enum class MedicationIntakeChangeKind {
    REGISTERED,
    CORRECTED,
    JUSTIFIED,
}

data class MedicationIntakeRevision(
    val id: String,
    val intakeRecordId: String,
    val revisionNumber: Int,
    val outcome: MedicationIntakeOutcome,
    val scheduledAt: LocalDateTime,
    val actualAt: LocalDateTime?,
    val actualAmount: MedicationAmount?,
    val observation: String?,
    val changeKind: MedicationIntakeChangeKind,
    val changeReason: String?,
    val capturedAtEpochMillis: Long,
    val authorActorId: String,
    val scheduledOccurrenceId: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(intakeRecordId.isNotBlank())
        require(revisionNumber > 0)
        require(observation == null || observation.length <= 1_000)
        require(capturedAtEpochMillis >= 0L)
        require(authorActorId.isNotBlank())
        require(
            changeKind == MedicationIntakeChangeKind.REGISTERED ||
                !changeReason.isNullOrBlank(),
        )
        require(changeReason == null || changeReason.length <= 500)
        require(
            outcome == MedicationIntakeOutcome.COMPLETED ||
                outcome == MedicationIntakeOutcome.PARTIAL ||
                actualAmount == null,
        )
    }
}

data class MedicationIntakeRecord(
    val id: String,
    val medicationPlanId: String,
    val createdAtEpochMillis: Long,
    val createdByActorId: String,
    val revisions: List<MedicationIntakeRevision>,
) {
    init {
        require(id.isNotBlank())
        require(medicationPlanId.isNotBlank())
        require(createdAtEpochMillis >= 0L)
        require(createdByActorId.isNotBlank())
        require(revisions.isNotEmpty())
        require(revisions.all { it.intakeRecordId == id })
        require(revisions.map { it.revisionNumber } == (1..revisions.size).toList())
    }

    val currentRevision: MedicationIntakeRevision
        get() = revisions.last()
}
