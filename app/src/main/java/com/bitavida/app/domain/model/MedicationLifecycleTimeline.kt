package com.bitavida.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class MedicationLifecycleTransition(
    val revision: MedicationPlanRevision,
    val status: MedicationTreatmentStatus,
    val effectiveAtEpochMillis: Long,
)

fun MedicationPlan.lifecycleTransitions(
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationLifecycleTransition> {
    val transitions = mutableListOf<MedicationLifecycleTransition>()
    revisions.sortedBy { it.revisionNumber }.forEach { revision ->
        val effectiveAt = revision.lifecycleEffectiveAtEpochMillis
            ?: if (revision.changeKind == MedicationPlanChangeKind.CREATED) {
                revision.startsOn.atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            } else {
                revision.capturedAtEpochMillis
            }
        if (
            revision.changeKind in setOf(
                MedicationPlanChangeKind.SUSPENDED,
                MedicationPlanChangeKind.REACTIVATION_RESCHEDULED,
                MedicationPlanChangeKind.REACTIVATION_CANCELLED,
                MedicationPlanChangeKind.COMPLETED,
                MedicationPlanChangeKind.REMOVED,
            )
        ) {
            transitions.removeAll {
                it.status == MedicationTreatmentStatus.ACTIVE &&
                    it.effectiveAtEpochMillis > revision.capturedAtEpochMillis
            }
        }
        val status = revision.changeKind.lifecycleStatus(
            revision.configuration?.status,
        ) ?: return@forEach
        transitions += MedicationLifecycleTransition(
            revision = revision,
            status = status,
            effectiveAtEpochMillis = effectiveAt,
        )
    }
    return transitions.sortedWith(
        compareBy<MedicationLifecycleTransition>(
            MedicationLifecycleTransition::effectiveAtEpochMillis,
        ).thenBy { it.revision.revisionNumber },
    )
}

fun MedicationPlan.treatmentStatusAt(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MedicationTreatmentStatus {
    val transition = lifecycleTransitions(zoneId)
        .lastOrNull { it.effectiveAtEpochMillis <= epochMillis }
    return transition?.status
        ?: revisions.first().configuration?.status
        ?: currentRevision.configuration?.status
        ?: MedicationTreatmentStatus.ACTIVE
}

fun MedicationPlan.treatmentStatusAt(
    dateTime: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MedicationTreatmentStatus =
    treatmentStatusAt(dateTime.atZone(zoneId).toInstant().toEpochMilli(), zoneId)

fun MedicationPlan.currentSuspension(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MedicationLifecycleTransition? =
    lifecycleTransitions(zoneId)
        .lastOrNull { it.effectiveAtEpochMillis <= epochMillis }
        ?.takeIf { it.status == MedicationTreatmentStatus.SUSPENDED }

fun MedicationPlan.pendingReactivation(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MedicationLifecycleTransition? =
    lifecycleTransitions(zoneId)
        .firstOrNull {
            it.status == MedicationTreatmentStatus.ACTIVE &&
                it.effectiveAtEpochMillis > epochMillis
        }

fun MedicationPlan.lastActiveDateBeforeCurrentSuspension(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate? {
    val suspension = currentSuspension(epochMillis, zoneId) ?: return null
    val localMoment = Instant.ofEpochMilli(suspension.effectiveAtEpochMillis)
        .atZone(zoneId)
        .toLocalDateTime()
    return if (localMoment.toLocalTime() == LocalTime.MIDNIGHT) {
        localMoment.toLocalDate().minusDays(1)
    } else {
        localMoment.toLocalDate()
    }
}

fun MedicationPlan.lastTreatmentDate(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate? {
    val completion = lifecycleTransitions(zoneId)
        .lastOrNull {
            it.status == MedicationTreatmentStatus.COMPLETED &&
                it.effectiveAtEpochMillis <= epochMillis
        }
    val configuredEnd = completion?.revision?.configuration?.endsOn
        ?: completion?.revision?.endsOn
        ?: currentRevision.configuration?.endsOn
        ?: currentRevision.endsOn
    if (completion == null) return configuredEnd
    val localMoment = Instant.ofEpochMilli(completion.effectiveAtEpochMillis)
        .atZone(zoneId)
        .toLocalDateTime()
    val effectiveLastDay = if (localMoment.toLocalTime() == LocalTime.MIDNIGHT) {
        localMoment.toLocalDate().minusDays(1)
    } else {
        localMoment.toLocalDate()
    }
    return configuredEnd?.let { minOf(it, effectiveLastDay) } ?: effectiveLastDay
}

fun MedicationPlan.isTreatmentActiveAt(
    dateTime: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean =
    treatmentStatusAt(dateTime, zoneId) == MedicationTreatmentStatus.ACTIVE

enum class MedicationReactivationIssue {
    MISSING_CONFIGURATION,
    PLAN_NOT_YET_VALID,
    PLAN_ENDED,
    LINKED_INDICATION_MISSING,
    INDICATION_INACTIVE,
    INDICATION_NOT_YET_VALID,
    INDICATION_EXPIRED,
}

fun MedicationPlan.reactivationIssue(
    indication: MedicalIndication?,
    effectiveAt: LocalDateTime,
): MedicationReactivationIssue? {
    val configuration = currentRevision.configuration
        ?: return MedicationReactivationIssue.MISSING_CONFIGURATION
    val date = effectiveAt.toLocalDate()
    if (configuration.startsOn.isAfter(date)) {
        return MedicationReactivationIssue.PLAN_NOT_YET_VALID
    }
    if (configuration.endsOn?.isBefore(date) == true) {
        return MedicationReactivationIssue.PLAN_ENDED
    }
    val linkedIndicationId = medicalIndicationId ?: return null
    if (indication?.id != linkedIndicationId) {
        return MedicationReactivationIssue.LINKED_INDICATION_MISSING
    }
    val revision = indication.currentRevision
    if (revision.status != MedicalIndicationStatus.ACTIVE) {
        return MedicationReactivationIssue.INDICATION_INACTIVE
    }
    if (revision.validFrom?.isAfter(date) == true) {
        return MedicationReactivationIssue.INDICATION_NOT_YET_VALID
    }
    if (revision.validUntil?.isBefore(date) == true) {
        return MedicationReactivationIssue.INDICATION_EXPIRED
    }
    return null
}

private fun MedicationPlanChangeKind.lifecycleStatus(
    storedStatus: MedicationTreatmentStatus?,
): MedicationTreatmentStatus? = when (this) {
    MedicationPlanChangeKind.CREATED ->
        storedStatus ?: MedicationTreatmentStatus.ACTIVE
    MedicationPlanChangeKind.SUSPENDED -> MedicationTreatmentStatus.SUSPENDED
    MedicationPlanChangeKind.REACTIVATED,
    MedicationPlanChangeKind.REACTIVATION_RESCHEDULED,
    -> MedicationTreatmentStatus.ACTIVE
    MedicationPlanChangeKind.REACTIVATION_CANCELLED -> null
    MedicationPlanChangeKind.COMPLETED -> MedicationTreatmentStatus.COMPLETED
    MedicationPlanChangeKind.REMOVED -> MedicationTreatmentStatus.REMOVED
    MedicationPlanChangeKind.CORRECTED,
    MedicationPlanChangeKind.BASE_DETAILS_CORRECTED,
    MedicationPlanChangeKind.SCHEDULE_CORRECTED,
    -> null
}
