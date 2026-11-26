package com.bitavida.app.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

/** Closed catalog. Primary measurements are permanent, independently of follow-up. */
enum class MeasurementKind {
    CAPILLARY_GLUCOSE,
    BLOOD_PRESSURE,
    WEIGHT,
}

object MeasurementCatalog {
    val primary: List<MeasurementKind> = listOf(
        MeasurementKind.CAPILLARY_GLUCOSE,
        MeasurementKind.BLOOD_PRESSURE,
        MeasurementKind.WEIGHT,
    )

    // No secondary types have been approved in the functional source yet.
    val secondary: List<MeasurementKind> = emptyList()
}

enum class GlucoseContext {
    FASTING,
    BEFORE_MEAL,
    AFTER_MEAL,
    BEFORE_SLEEP,
    OTHER,
    RESTING,
    AFTER_PHYSICAL_ACTIVITY,
    UPON_WAKING,
    /** Technical compatibility value for measurements that do not use context. */
    UNSPECIFIED,
}

val glucoseMeasurementContexts: List<GlucoseContext> = listOf(
    GlucoseContext.FASTING,
    GlucoseContext.BEFORE_MEAL,
    GlucoseContext.AFTER_MEAL,
    GlucoseContext.BEFORE_SLEEP,
    GlucoseContext.OTHER,
)

val bloodPressureMeasurementContexts: List<GlucoseContext> =
    glucoseMeasurementContexts + listOf(
        GlucoseContext.RESTING,
        GlucoseContext.AFTER_PHYSICAL_ACTIVITY,
    )


sealed interface MeasurementValue {
    /** Preserves the meter's transcription, including HI and LO; never classifies it. */
    data class Glucose(
        val reading: String,
        val context: GlucoseContext? = null,
    ) : MeasurementValue

    data class BloodPressure(
        val systolic: String,
        val diastolic: String,
        val pulse: String? = null,
    ) : MeasurementValue

    data class Weight(
        val kilograms: BigDecimal,
        val heightMeters: BigDecimal? = null,
    ) : MeasurementValue
}

/** A declared event; capture time is separate and never determines its place in the week. */
data class MeasurementRecord(
    val id: String,
    val value: MeasurementValue,
    val occurredAt: LocalDateTime,
    val capturedAt: LocalDateTime,
    val glucoseMetadata: GlucoseRecordMetadata? = null,
    val bloodPressureMetadata: BloodPressureRecordMetadata? = null,
    val weightMetadata: WeightRecordMetadata? = null,
    /** Report-only: old revisions did not retain their scheduling association. */
    val historicalAssociationUncertain: Boolean = false,
) {
    val kind: MeasurementKind
        get() = when (value) {
            is MeasurementValue.Glucose -> MeasurementKind.CAPILLARY_GLUCOSE
            is MeasurementValue.BloodPressure -> MeasurementKind.BLOOD_PRESSURE
            is MeasurementValue.Weight -> MeasurementKind.WEIGHT
        }
}

/** Only an explicitly configured summary may appear on a measurement card. */
data class MeasurementFollowUp(
    val kind: MeasurementKind,
    val summary: String,
    val suspended: Boolean = false,
    val frequency: String? = null,
    val periodicity: String? = null,
    val moments: List<String> = emptyList(),
    val reminders: MeasurementReminderInfo? = null,
    val id: String? = null,
)

/** Declared configuration information; does not assert notification delivery. */
enum class MeasurementReminderStatus { DISABLED, CONFIGURED, SYSTEM_LIMITED }

data class MeasurementReminderInfo(
    val status: MeasurementReminderStatus,
    val detail: String? = null,
)
