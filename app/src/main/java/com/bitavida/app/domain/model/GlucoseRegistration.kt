package com.bitavida.app.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

enum class GlucoseEntryKind { INDEPENDENT, SCHEDULED, ADDITIONAL }

enum class GlucoseRevisionOperation { CREATED, CORRECTED, DELETED }

data class GlucoseTargetRange(
    val lower: BigDecimal,
    val upper: BigDecimal,
) {
    init {
        require(lower <= upper)
    }
}

data class GlucoseRevision(
    val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val operation: GlucoseRevisionOperation,
    val reading: String,
    val context: GlucoseContext,
    val actualAt: LocalDateTime,
    val note: String?,
    val actorId: String,
    val capturedAt: LocalDateTime,
)

/** Only supplied by a host with an explicitly confirmed occurrence, never inferred from a clock. */
data class ConfirmedGlucoseOccurrence(
    val id: String,
    val followUpId: String,
    val scheduledAt: LocalDateTime,
    val expectedContext: GlucoseContext?,
    val timingType: GlucoseFollowUpTimingType =
        GlucoseFollowUpTimingType.EXACT_TIME,
    val windowEndAt: LocalDateTime? = null,
)

sealed interface GlucoseRegistrationContext {
    data object Independent : GlucoseRegistrationContext
    data class Additional(val followUpId: String?) : GlucoseRegistrationContext
    data class Scheduled(val occurrence: ConfirmedGlucoseOccurrence) : GlucoseRegistrationContext
}

fun glucoseRegistrationContext(
    followUp: MeasurementFollowUp?,
    occurrence: ConfirmedGlucoseOccurrence? = null,
): GlucoseRegistrationContext {
    val active = followUp?.takeIf { it.kind == MeasurementKind.CAPILLARY_GLUCOSE && !it.suspended }
    if (occurrence != null) {
        require(active != null && active.id == occurrence.followUpId)
        require(occurrence.id.isNotBlank() && occurrence.followUpId.isNotBlank())
        return GlucoseRegistrationContext.Scheduled(occurrence)
    }
    return if (active == null) GlucoseRegistrationContext.Independent
        else GlucoseRegistrationContext.Additional(active.id)
}

data class GlucoseRecordMetadata(
    val entryKind: GlucoseEntryKind,
    val followUpId: String? = null,
    val scheduledOccurrenceId: String? = null,
    val scheduledAt: LocalDateTime? = null,
    val expectedContext: GlucoseContext? = null,
    val note: String? = null,
)

fun GlucoseRecordMetadata.registrationContext(): GlucoseRegistrationContext =
    when (entryKind) {
        GlucoseEntryKind.INDEPENDENT -> GlucoseRegistrationContext.Independent
        GlucoseEntryKind.ADDITIONAL -> GlucoseRegistrationContext.Additional(followUpId)
        GlucoseEntryKind.SCHEDULED -> GlucoseRegistrationContext.Scheduled(
            ConfirmedGlucoseOccurrence(
                id = requireNotNull(scheduledOccurrenceId),
                followUpId = requireNotNull(followUpId),
                scheduledAt = requireNotNull(scheduledAt),
                expectedContext = expectedContext,
            ),
        )
    }

fun GlucoseRegistrationContext.metadata(note: String?): GlucoseRecordMetadata = when (this) {
    GlucoseRegistrationContext.Independent -> GlucoseRecordMetadata(GlucoseEntryKind.INDEPENDENT, note = note)
    is GlucoseRegistrationContext.Additional ->
        GlucoseRecordMetadata(GlucoseEntryKind.ADDITIONAL, followUpId = followUpId, note = note)
    is GlucoseRegistrationContext.Scheduled -> GlucoseRecordMetadata(
        entryKind = GlucoseEntryKind.SCHEDULED,
        followUpId = occurrence.followUpId,
        scheduledOccurrenceId = occurrence.id,
        scheduledAt = occurrence.scheduledAt,
        expectedContext = occurrence.expectedContext,
        note = note,
    )
}
