package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.LocalDateTime

/** Immutable, patient-scoped records and their complete append-only revisions. */
data class MeasurementReportHistory(
    val glucose: List<Pair<MeasurementRecord, List<GlucoseRevision>>>,
    val pressure: List<Pair<MeasurementRecord, List<BloodPressureRevision>>>,
    val weight: List<Pair<MeasurementRecord, List<WeightRevision>>>,
)

/**
 * Reconstruct only the last revision captured by the report cutoff. Deletions after
 * the cutoff do not erase historical measurements; deletions at/before it do.
 * An incomplete history is rejected rather than silently substituted with the
 * current mutable values.
 */
fun MeasurementReportHistory.recordsAt(cutoff: LocalDateTime): List<MeasurementRecord> {
    fun <T> complete(
        record: MeasurementRecord, revisions: List<T>, id: (T) -> String, number: (T) -> Int,
        time: (T) -> LocalDateTime, created: (T) -> Boolean, deleted: (T) -> Boolean,
    ) {
        check(revisions.isNotEmpty() && revisions.all { id(it) == record.id })
        val ordered = revisions.sortedBy(number)
        check(ordered.map(number) == (1..ordered.size).toList())
        check(created(ordered.first()) && ordered.drop(1).none(created))
        check(ordered.zipWithNext().all { (a, b) -> !time(b).isBefore(time(a)) })
        check(ordered.dropLast(1).none(deleted))
    }
    val glucoseRecords = glucose.mapNotNull { (record, versions) ->
        complete(record, versions, GlucoseRevision::recordId, GlucoseRevision::revisionNumber,
            GlucoseRevision::capturedAt, { it.operation == GlucoseRevisionOperation.CREATED },
            { it.operation == GlucoseRevisionOperation.DELETED })
        val revision = versions.filter { !it.capturedAt.isAfter(cutoff) }.maxByOrNull { it.revisionNumber }
            ?: return@mapNotNull null
        if (revision.operation == GlucoseRevisionOperation.DELETED) return@mapNotNull null
        record.copy(
            value = MeasurementValue.Glucose(revision.reading, revision.context),
            occurredAt = revision.actualAt,
            capturedAt = versions.minBy { it.revisionNumber }.capturedAt,
            glucoseMetadata = record.glucoseMetadata?.copy(note = revision.note),
            historicalAssociationUncertain = versions.any {
                it.operation == GlucoseRevisionOperation.CORRECTED && it.capturedAt.isAfter(cutoff)
            },
        )
    }
    val pressureRecords = pressure.mapNotNull { (record, versions) ->
        complete(record, versions, BloodPressureRevision::recordId, BloodPressureRevision::revisionNumber,
            BloodPressureRevision::capturedAt, { it.operation == BloodPressureRevisionOperation.CREATED },
            { it.operation == BloodPressureRevisionOperation.DELETED })
        val revision = versions.filter { !it.capturedAt.isAfter(cutoff) }.maxByOrNull { it.revisionNumber }
            ?: return@mapNotNull null
        if (revision.operation == BloodPressureRevisionOperation.DELETED) return@mapNotNull null
        record.copy(
            value = MeasurementValue.BloodPressure(revision.systolic, revision.diastolic),
            occurredAt = revision.actualAt,
            capturedAt = versions.minBy { it.revisionNumber }.capturedAt,
            bloodPressureMetadata = record.bloodPressureMetadata?.copy(
                actualContext = revision.context, note = revision.note),
            historicalAssociationUncertain = versions.any {
                it.operation == BloodPressureRevisionOperation.CORRECTED && it.capturedAt.isAfter(cutoff)
            },
        )
    }
    val weightRecords = weight.mapNotNull { (record, versions) ->
        complete(record, versions, WeightRevision::recordId, WeightRevision::revisionNumber,
            WeightRevision::capturedAt, { it.operation == WeightRevisionOperation.CREATED },
            { it.operation == WeightRevisionOperation.DELETED })
        val revision = versions.filter { !it.capturedAt.isAfter(cutoff) }.maxByOrNull { it.revisionNumber }
            ?: return@mapNotNull null
        if (revision.operation == WeightRevisionOperation.DELETED) return@mapNotNull null
        record.copy(
            value = MeasurementValue.Weight(revision.kilograms, revision.heightMeters),
            occurredAt = revision.actualAt,
            capturedAt = versions.minBy { it.revisionNumber }.capturedAt,
            weightMetadata = record.weightMetadata?.copy(
                actualContext = revision.context, note = revision.note),
            historicalAssociationUncertain = versions.any {
                it.operation == WeightRevisionOperation.CORRECTED && it.capturedAt.isAfter(cutoff)
            },
        )
    }
    return (glucoseRecords + pressureRecords + weightRecords).sortedBy { it.occurredAt }
}
