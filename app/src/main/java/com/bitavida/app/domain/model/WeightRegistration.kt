package com.bitavida.app.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

enum class WeightEntryKind { INDEPENDENT, SCHEDULED, ADDITIONAL }

enum class WeightRevisionOperation { CREATED, CORRECTED, DELETED }

data class WeightRevision(
    val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val operation: WeightRevisionOperation,
    val kilograms: BigDecimal,
    val heightMeters: BigDecimal?,
    val context: GlucoseContext?,
    val actualAt: LocalDateTime,
    val note: String?,
    val actorId: String,
    val capturedAt: LocalDateTime,
)

data class ConfirmedWeightOccurrence(
    val id: String,
    val followUpId: String,
    val scheduledAt: LocalDateTime,
    val expectedContext: GlucoseContext?,
    val timingType: GlucoseFollowUpTimingType =
        GlucoseFollowUpTimingType.EXACT_TIME,
    val windowEndAt: LocalDateTime? = null,
)

sealed interface WeightRegistrationContext {
    data object Independent : WeightRegistrationContext
    data class Additional(
        val followUpId: String?,
    ) : WeightRegistrationContext
    data class Scheduled(
        val occurrence: ConfirmedWeightOccurrence,
    ) : WeightRegistrationContext
}

fun weightRegistrationContext(
    followUp: MeasurementFollowUp?,
    occurrence: ConfirmedWeightOccurrence? = null,
): WeightRegistrationContext {
    val active = followUp?.takeIf {
        it.kind == MeasurementKind.WEIGHT && !it.suspended
    }
    if (occurrence != null) {
        require(active != null && active.id == occurrence.followUpId)
        require(occurrence.id.isNotBlank() && occurrence.followUpId.isNotBlank())
        return WeightRegistrationContext.Scheduled(occurrence)
    }
    return if (active == null) {
        WeightRegistrationContext.Independent
    } else {
        WeightRegistrationContext.Additional(active.id)
    }
}

data class WeightRecordMetadata(
    val entryKind: WeightEntryKind,
    val followUpId: String? = null,
    val scheduledOccurrenceId: String? = null,
    val scheduledAt: LocalDateTime? = null,
    /** Preserved only when loading legacy scheduled records. */
    val expectedContext: GlucoseContext? = null,
    /** Preserved only when loading legacy records created before Peso dropped context. */
    val actualContext: GlucoseContext? = null,
    val note: String? = null,
)

fun WeightRecordMetadata.registrationContext():
    WeightRegistrationContext = when (entryKind) {
    WeightEntryKind.INDEPENDENT ->
        WeightRegistrationContext.Independent
    WeightEntryKind.ADDITIONAL ->
        WeightRegistrationContext.Additional(followUpId)
    WeightEntryKind.SCHEDULED ->
        WeightRegistrationContext.Scheduled(
            ConfirmedWeightOccurrence(
                id = requireNotNull(scheduledOccurrenceId),
                followUpId = requireNotNull(followUpId),
                scheduledAt = requireNotNull(scheduledAt),
                expectedContext = expectedContext,
            ),
        )
}

fun WeightRegistrationContext.metadata(
    note: String?,
): WeightRecordMetadata = when (this) {
    WeightRegistrationContext.Independent ->
        WeightRecordMetadata(
            entryKind = WeightEntryKind.INDEPENDENT,
            note = note,
        )
    is WeightRegistrationContext.Additional ->
        WeightRecordMetadata(
            entryKind = WeightEntryKind.ADDITIONAL,
            followUpId = followUpId,
            note = note,
        )
    is WeightRegistrationContext.Scheduled ->
        WeightRecordMetadata(
            entryKind = WeightEntryKind.SCHEDULED,
            followUpId = occurrence.followUpId,
            scheduledOccurrenceId = occurrence.id,
            scheduledAt = occurrence.scheduledAt,
            expectedContext = occurrence.expectedContext,
            note = note,
        )
}

fun GlucoseScheduledOccurrence.confirmedWeight() =
    ConfirmedWeightOccurrence(
        id = id,
        followUpId = followUpId,
        scheduledAt = scheduledAt,
        expectedContext = suggestedContext.takeUnless {
            it == GlucoseContext.UNSPECIFIED
        },
        timingType = timingType,
        windowEndAt = windowEndAt,
    )

fun projectWeightToday(
    date: java.time.LocalDate,
    followUp: GlucoseFollowUp?,
    records: List<MeasurementRecord>,
    decisions: List<GlucoseOccurrenceDecision>,
): GlucoseTodaySummary {
    return projectMeasurementToday(date, followUp, records, decisions, MeasurementKind.WEIGHT)
}
