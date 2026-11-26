package com.bitavida.app.domain.model

import java.time.LocalDateTime

enum class BloodPressureEntryKind { INDEPENDENT, SCHEDULED, ADDITIONAL }

enum class BloodPressureRevisionOperation { CREATED, CORRECTED, DELETED }

data class BloodPressureRevision(
    val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val operation: BloodPressureRevisionOperation,
    val systolic: String,
    val diastolic: String,
    val context: GlucoseContext,
    val actualAt: LocalDateTime,
    val note: String?,
    val actorId: String,
    val capturedAt: LocalDateTime,
)

data class ConfirmedBloodPressureOccurrence(
    val id: String,
    val followUpId: String,
    val scheduledAt: LocalDateTime,
    val expectedContext: GlucoseContext?,
    val timingType: GlucoseFollowUpTimingType =
        GlucoseFollowUpTimingType.EXACT_TIME,
    val windowEndAt: LocalDateTime? = null,
)

sealed interface BloodPressureRegistrationContext {
    data object Independent : BloodPressureRegistrationContext
    data class Additional(
        val followUpId: String?,
    ) : BloodPressureRegistrationContext
    data class Scheduled(
        val occurrence: ConfirmedBloodPressureOccurrence,
    ) : BloodPressureRegistrationContext
}

fun bloodPressureRegistrationContext(
    followUp: MeasurementFollowUp?,
    occurrence: ConfirmedBloodPressureOccurrence? = null,
): BloodPressureRegistrationContext {
    val active = followUp?.takeIf {
        it.kind == MeasurementKind.BLOOD_PRESSURE && !it.suspended
    }
    if (occurrence != null) {
        require(active != null && active.id == occurrence.followUpId)
        require(occurrence.id.isNotBlank() && occurrence.followUpId.isNotBlank())
        return BloodPressureRegistrationContext.Scheduled(occurrence)
    }
    return if (active == null) {
        BloodPressureRegistrationContext.Independent
    } else {
        BloodPressureRegistrationContext.Additional(active.id)
    }
}

data class BloodPressureRecordMetadata(
    val entryKind: BloodPressureEntryKind,
    val followUpId: String? = null,
    val scheduledOccurrenceId: String? = null,
    val scheduledAt: LocalDateTime? = null,
    val expectedContext: GlucoseContext? = null,
    val actualContext: GlucoseContext,
    val note: String? = null,
)

fun BloodPressureRecordMetadata.registrationContext():
    BloodPressureRegistrationContext = when (entryKind) {
    BloodPressureEntryKind.INDEPENDENT ->
        BloodPressureRegistrationContext.Independent
    BloodPressureEntryKind.ADDITIONAL ->
        BloodPressureRegistrationContext.Additional(followUpId)
    BloodPressureEntryKind.SCHEDULED ->
        BloodPressureRegistrationContext.Scheduled(
            ConfirmedBloodPressureOccurrence(
                id = requireNotNull(scheduledOccurrenceId),
                followUpId = requireNotNull(followUpId),
                scheduledAt = requireNotNull(scheduledAt),
                expectedContext = expectedContext,
            ),
        )
}

fun BloodPressureRegistrationContext.metadata(
    moment: GlucoseContext,
    note: String?,
): BloodPressureRecordMetadata = when (this) {
    BloodPressureRegistrationContext.Independent ->
        BloodPressureRecordMetadata(
            entryKind = BloodPressureEntryKind.INDEPENDENT,
            actualContext = moment,
            note = note,
        )
    is BloodPressureRegistrationContext.Additional ->
        BloodPressureRecordMetadata(
            entryKind = BloodPressureEntryKind.ADDITIONAL,
            followUpId = followUpId,
            actualContext = moment,
            note = note,
        )
    is BloodPressureRegistrationContext.Scheduled ->
        BloodPressureRecordMetadata(
            entryKind = BloodPressureEntryKind.SCHEDULED,
            followUpId = occurrence.followUpId,
            scheduledOccurrenceId = occurrence.id,
            scheduledAt = occurrence.scheduledAt,
            expectedContext = occurrence.expectedContext,
            actualContext = moment,
            note = note,
        )
}

fun GlucoseScheduledOccurrence.confirmedBloodPressure() =
    ConfirmedBloodPressureOccurrence(
        id = id,
        followUpId = followUpId,
        scheduledAt = scheduledAt,
        expectedContext = suggestedContext,
        timingType = timingType,
        windowEndAt = windowEndAt,
    )

fun projectBloodPressureToday(
    date: java.time.LocalDate,
    followUp: GlucoseFollowUp?,
    records: List<MeasurementRecord>,
    decisions: List<GlucoseOccurrenceDecision>,
): GlucoseTodaySummary {
    return projectMeasurementToday(date, followUp, records, decisions, MeasurementKind.BLOOD_PRESSURE)
}
