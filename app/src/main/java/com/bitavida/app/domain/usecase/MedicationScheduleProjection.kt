package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicationActivityRelation
import com.bitavida.app.domain.model.MedicationAdministration
import com.bitavida.app.domain.model.MedicationAdministrationTiming
import com.bitavida.app.domain.model.MedicationAmount
import com.bitavida.app.domain.model.MedicationConfiguration
import com.bitavida.app.domain.model.MedicationFrequencyKind
import com.bitavida.app.domain.model.MedicationHourlyIntervalMode
import com.bitavida.app.domain.model.MedicationPlan
import com.bitavida.app.domain.model.MedicationProductForm
import com.bitavida.app.domain.model.MedicationReminderPreference
import com.bitavida.app.domain.model.MedicationTreatmentStatus
import com.bitavida.app.domain.model.isTreatmentActiveAt
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class MedicationOccurrenceTiming {
    EXACT_TIME,
    TIME_WINDOW,
    ACTIVITY_NOTICE,
    HOURLY_INTERVAL,
}

data class UpcomingMedicationAdministration(
    val id: String,
    val medicationPlanId: String,
    val medicationPlanRevisionId: String,
    val administrationId: String?,
    val medicationName: String,
    val form: MedicationProductForm,
    val amount: MedicationAmount,
    val timing: MedicationOccurrenceTiming,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val reminderAt: LocalDateTime? = startsAt,
    val activityRelation: MedicationActivityRelation?,
    val customActivity: String?,
)

data class FirstMedicationAdministration(
    val date: LocalDate,
    val amount: MedicationAmount,
    val timing: MedicationOccurrenceTiming,
    val exactTime: java.time.LocalTime? = null,
    val windowStart: java.time.LocalTime? = null,
    val windowEnd: java.time.LocalTime? = null,
    val windowEndsNextDay: Boolean = false,
    val reminderTime: java.time.LocalTime? = null,
    val reminderNextDay: Boolean = false,
    val activityRelation: MedicationActivityRelation? = null,
    val customActivity: String? = null,
)

fun projectFirstMedicationAdministration(
    plan: MedicationPlan,
): FirstMedicationAdministration? {
    if (plan.contentMode != ClinicalContentMode.REAL_CLINICAL_DATA) return null
    val configuration = plan.currentRevision.configuration ?: return null
    if (
        configuration.status != MedicationTreatmentStatus.ACTIVE ||
        configuration.frequency == MedicationFrequencyKind.AS_NEEDED
    ) {
        return null
    }
    if (configuration.frequency == MedicationFrequencyKind.EVERY_N_HOURS) {
        return configuration.firstHourlyAdministration()
    }
    val date = configuration.firstApplicableDate(configuration.startsOn) ?: return null
    val selected = configuration.earliestAdministration() ?: return null
    return selected.toFirstAdministration(configuration, date)
}

/**
 * Projects the first administration that can begin at or after [notBefore].
 * This keeps a scheduled reactivation distinct from the treatment's historical
 * first dose and never revives an administration whose start already passed.
 */
fun projectNextMedicationAdministration(
    plan: MedicationPlan,
    notBefore: LocalDateTime,
): FirstMedicationAdministration? {
    if (plan.contentMode != ClinicalContentMode.REAL_CLINICAL_DATA) return null
    val configuration = plan.currentRevision.configuration ?: return null
    if (configuration.frequency == MedicationFrequencyKind.AS_NEEDED) return null
    if (configuration.frequency == MedicationFrequencyKind.EVERY_N_HOURS) {
        val searchEnd = when (configuration.hourlyMode) {
            MedicationHourlyIntervalMode.CONTINUOUS -> notBefore.plusHours(
                (configuration.hourlyInterval?.takeIf { it > 0 } ?: return null).toLong(),
            )
            MedicationHourlyIntervalMode.DAILY_WINDOW -> notBefore.plusDays(8)
            null -> return null
        }
        return projectMedicationAdministrations(
            plans = listOf(plan),
            rangeStart = notBefore,
            rangeEnd = searchEnd,
            limit = 1,
        ).firstOrNull()?.toFirstAdministration()
    }

    var candidateDate = configuration.firstApplicableDate(notBefore.toLocalDate())
        ?: return null
    repeat(2) {
        val minimumTime = notBefore.toLocalTime()
            .takeIf { candidateDate == notBefore.toLocalDate() }
        val selected = configuration.earliestAdministration(minimumTime)
        if (selected != null) {
            return selected.toFirstAdministration(configuration, candidateDate)
        }
        candidateDate = configuration.firstApplicableDate(candidateDate.plusDays(1))
            ?: return null
    }
    return null
}

private fun MedicationConfiguration.earliestAdministration(
    minimumTime: java.time.LocalTime? = null,
): MedicationAdministration? {
    val eligible = administrations.filter { administration ->
        val startTime = when (administration.timing) {
            MedicationAdministrationTiming.EXACT_TIME -> administration.exactTime
            MedicationAdministrationTiming.TIME_WINDOW -> administration.windowStart
            MedicationAdministrationTiming.ACTIVITY_ONLY -> administration.activityNoticeTime
        }
        minimumTime == null || startTime?.let { !it.isBefore(minimumTime) } == true
    }
    val undatedActivity = eligible.filter {
        it.timing == MedicationAdministrationTiming.ACTIVITY_ONLY &&
            it.activityNoticeTime == null
    }
    return when {
        eligible.size == 1 -> eligible.single()
        undatedActivity.isNotEmpty() -> null
        else -> eligible.minByOrNull { administration ->
            when (administration.timing) {
                MedicationAdministrationTiming.EXACT_TIME ->
                    administration.exactTime?.toSecondOfDay() ?: Int.MAX_VALUE
                MedicationAdministrationTiming.TIME_WINDOW ->
                    administration.windowStart?.toSecondOfDay() ?: Int.MAX_VALUE
                MedicationAdministrationTiming.ACTIVITY_ONLY ->
                    administration.activityNoticeTime?.toSecondOfDay() ?: Int.MAX_VALUE
            }
        }
    }
}

private fun MedicationAdministration.toFirstAdministration(
    configuration: MedicationConfiguration,
    date: LocalDate,
): FirstMedicationAdministration = FirstMedicationAdministration(
    date = date,
    amount = amountOverride ?: configuration.baseAmount,
    timing = when (timing) {
        MedicationAdministrationTiming.EXACT_TIME ->
            MedicationOccurrenceTiming.EXACT_TIME
        MedicationAdministrationTiming.TIME_WINDOW ->
            MedicationOccurrenceTiming.TIME_WINDOW
        MedicationAdministrationTiming.ACTIVITY_ONLY ->
            MedicationOccurrenceTiming.ACTIVITY_NOTICE
    },
    exactTime = exactTime,
    windowStart = windowStart,
    windowEnd = windowEnd,
    windowEndsNextDay = windowEndsNextDay,
    reminderTime =
        if (configuration.reminderPreference == MedicationReminderPreference.NONE) {
            null
        } else {
            when (timing) {
                MedicationAdministrationTiming.EXACT_TIME ->
                    exactTime.takeUnless { reminderEnabled == false }
                MedicationAdministrationTiming.TIME_WINDOW -> noticeTime
                MedicationAdministrationTiming.ACTIVITY_ONLY -> activityNoticeTime
            }
        },
    reminderNextDay = noticeNextDay,
    activityRelation = activityRelation,
    customActivity = customActivity,
)

private fun UpcomingMedicationAdministration.toFirstAdministration() =
    FirstMedicationAdministration(
        date = startsAt.toLocalDate(),
        amount = amount,
        timing = timing,
        exactTime = startsAt.toLocalTime(),
        windowStart = startsAt.toLocalTime().takeIf {
            timing == MedicationOccurrenceTiming.TIME_WINDOW
        },
        windowEnd = endsAt?.toLocalTime(),
        windowEndsNextDay = endsAt?.toLocalDate()?.isAfter(startsAt.toLocalDate()) == true,
        reminderTime = reminderAt?.toLocalTime(),
        reminderNextDay =
            reminderAt?.toLocalDate()?.isAfter(startsAt.toLocalDate()) == true,
        activityRelation = activityRelation,
        customActivity = customActivity,
    )

private fun MedicationConfiguration.firstApplicableDate(
    onOrAfter: LocalDate,
): LocalDate? {
    val effectiveStart = maxOf(startsOn, onOrAfter)
    val candidate = when (frequency) {
        MedicationFrequencyKind.EVERY_DAY -> effectiveStart
        MedicationFrequencyKind.SPECIFIC_DAYS -> {
            if (weekdays.isEmpty()) return null
            (0L..6L).map(effectiveStart::plusDays)
                .firstOrNull { it.dayOfWeek in weekdays }
        }
        MedicationFrequencyKind.EVERY_N_DAYS -> {
            val reference = everyNDaysReferenceDate ?: return null
            val interval = everyNDaysInterval?.takeIf { it > 0 } ?: return null
            if (!effectiveStart.isAfter(reference)) {
                reference
            } else {
                val elapsed = ChronoUnit.DAYS.between(reference, effectiveStart)
                reference.plusDays(
                    ((elapsed + interval - 1L) / interval) * interval,
                )
            }
        }
        MedicationFrequencyKind.CYCLE -> {
            val configuredCycle = cycle ?: return null
            val cycleLength = configuredCycle.activeDays + configuredCycle.restDays
            if (cycleLength <= 0) return null
            val cycleStart = maxOf(effectiveStart, configuredCycle.referenceDate)
            val elapsed = ChronoUnit.DAYS.between(
                configuredCycle.referenceDate,
                cycleStart,
            )
            val cycleIndex = elapsed / cycleLength
            val dayInCycle = elapsed % cycleLength
            val selectedCycle = if (dayInCycle < configuredCycle.activeDays) {
                cycleIndex
            } else {
                cycleIndex + 1L
            }
            if (
                configuredCycle.numberOfCycles != null &&
                selectedCycle >= configuredCycle.numberOfCycles
            ) {
                return null
            }
            if (dayInCycle < configuredCycle.activeDays) {
                cycleStart
            } else {
                configuredCycle.referenceDate.plusDays(selectedCycle * cycleLength)
            }
        }
        MedicationFrequencyKind.EVERY_N_HOURS,
        MedicationFrequencyKind.AS_NEEDED,
        -> return null
    } ?: return null
    return candidate.takeUnless { endsOn?.isBefore(it) == true }
}

private fun MedicationConfiguration.firstHourlyAdministration():
    FirstMedicationAdministration? {
    val intervalMinutes =
        (hourlyInterval?.takeIf { it > 0 } ?: return null).toLong() * 60L
    val occurrence = when (hourlyMode) {
        MedicationHourlyIntervalMode.CONTINUOUS -> {
            val reference = LocalDateTime.of(
                hourlyReferenceDate ?: return null,
                hourlyReferenceTime ?: return null,
            )
            val effectiveStart = startsOn.atStartOfDay()
            val elapsed = Duration.between(reference, effectiveStart).toMinutes()
            val index = if (elapsed <= 0L) {
                0L
            } else {
                (elapsed + intervalMinutes - 1L) / intervalMinutes
            }
            reference.plusMinutes(index * intervalMinutes)
        }
        MedicationHourlyIntervalMode.DAILY_WINDOW -> {
            val firstTime = hourlyFirstAdministration ?: return null
            val anchorDate = if (weekdays.isEmpty()) {
                startsOn
            } else {
                (0L..6L).map(startsOn::plusDays)
                    .firstOrNull { it.dayOfWeek in weekdays }
                    ?: return null
            }
            LocalDateTime.of(
                if (
                    hourlyWindowEndsNextDay &&
                    hourlyWindowStart?.let { firstTime < it } == true
                ) {
                    anchorDate.plusDays(1)
                } else {
                    anchorDate
                },
                firstTime,
            )
        }
        null -> return null
    }
    if (!containsActualDate(occurrence.toLocalDate())) return null
    return FirstMedicationAdministration(
        date = occurrence.toLocalDate(),
        amount = baseAmount,
        timing = MedicationOccurrenceTiming.HOURLY_INTERVAL,
        exactTime = occurrence.toLocalTime(),
        reminderTime = occurrence.toLocalTime()
            .takeUnless {
                reminderPreference == MedicationReminderPreference.NONE
            },
    )
}

fun projectUpcomingMedicationAdministrations(
    plans: List<MedicationPlan>,
    now: LocalDateTime,
    limit: Int = 4,
): List<UpcomingMedicationAdministration> {
    if (limit <= 0) return emptyList()
    val rangeStart = maxOf(now.toLocalDate().atStartOfDay(), now.minusHours(6))
    val rangeEnd = now.plusHours(6)
    return plans.asSequence()
        .filter { it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA }
        .flatMap { plan ->
            projectPlanOccurrences(plan, rangeStart, rangeEnd, ZoneId.systemDefault()).asSequence()
        }
        .distinctBy(UpcomingMedicationAdministration::id)
        .sortedWith(
            compareBy<UpcomingMedicationAdministration> {
                it.proximityPriority(now)
            }.thenBy {
                if (it.startsAt.isBefore(now)) {
                    -it.startsAt.toEpochSecond(java.time.ZoneOffset.UTC)
                } else {
                    it.startsAt.toEpochSecond(java.time.ZoneOffset.UTC)
                }
            }.thenBy(UpcomingMedicationAdministration::id),
        )
        .take(limit)
        .sortedWith(
            compareBy<UpcomingMedicationAdministration>(
                UpcomingMedicationAdministration::startsAt,
            ).thenBy(UpcomingMedicationAdministration::id),
        )
        .toList()
}

fun projectMedicationAdministrations(
    plans: List<MedicationPlan>,
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
    limit: Int = 256,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<UpcomingMedicationAdministration> {
    if (limit <= 0 || rangeEnd.isBefore(rangeStart)) return emptyList()
    return plans.asSequence()
        .filter { it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA }
        .flatMap { plan ->
            projectPlanOccurrences(plan, rangeStart, rangeEnd, zoneId).asSequence()
        }
        .distinctBy(UpcomingMedicationAdministration::id)
        .sortedWith(
            compareBy<UpcomingMedicationAdministration>(
                UpcomingMedicationAdministration::startsAt,
            ).thenBy(UpcomingMedicationAdministration::id),
        )
        .take(limit)
        .toList()
}

private fun projectPlanOccurrences(
    plan: MedicationPlan,
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
    zoneId: ZoneId,
): List<UpcomingMedicationAdministration> {
    val revision = plan.currentRevision
    val storedConfiguration = revision.configuration ?: return emptyList()
    val configuration = storedConfiguration.copy(
        status = MedicationTreatmentStatus.ACTIVE,
    )
    if (
        configuration.startsOn.isAfter(rangeEnd.toLocalDate()) ||
        configuration.endsOn?.isBefore(rangeStart.toLocalDate()) == true
    ) {
        return emptyList()
    }
    val projected = when (configuration.frequency) {
        MedicationFrequencyKind.AS_NEEDED -> emptyList()
        MedicationFrequencyKind.EVERY_N_HOURS ->
            projectHourlyOccurrences(
                plan = plan,
                configuration = configuration,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
            )
        else -> projectAdministrationOccurrences(
            plan = plan,
            configuration = configuration,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )
    }
    return projected.filter { occurrence ->
        plan.isTreatmentActiveAt(occurrence.startsAt, zoneId)
    }
}

private fun projectAdministrationOccurrences(
    plan: MedicationPlan,
    configuration: MedicationConfiguration,
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
): List<UpcomingMedicationAdministration> {
    val results = mutableListOf<UpcomingMedicationAdministration>()
    var anchorDate = maxOf(
        configuration.startsOn,
        rangeStart.toLocalDate().minusDays(1),
    )
    val lastAnchorDate = minOf(
        configuration.endsOn ?: rangeEnd.toLocalDate(),
        rangeEnd.toLocalDate(),
    )
    while (!anchorDate.isAfter(lastAnchorDate)) {
        if (configuration.appliesOn(anchorDate)) {
            configuration.administrations.forEach { administration ->
                administration.toOccurrence(
                    plan = plan,
                    configuration = configuration,
                    anchorDate = anchorDate,
                )?.takeIf { it.overlaps(rangeStart, rangeEnd) }?.let(results::add)
            }
        }
        anchorDate = anchorDate.plusDays(1)
    }
    return results
}

private fun projectHourlyOccurrences(
    plan: MedicationPlan,
    configuration: MedicationConfiguration,
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
): List<UpcomingMedicationAdministration> = when (configuration.hourlyMode) {
    MedicationHourlyIntervalMode.CONTINUOUS ->
        projectContinuousHourlyOccurrences(
            plan,
            configuration,
            rangeStart,
            rangeEnd,
        )
    MedicationHourlyIntervalMode.DAILY_WINDOW ->
        projectWindowedHourlyOccurrences(
            plan,
            configuration,
            rangeStart,
            rangeEnd,
        )
    null -> emptyList()
}

private fun projectContinuousHourlyOccurrences(
    plan: MedicationPlan,
    configuration: MedicationConfiguration,
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
): List<UpcomingMedicationAdministration> {
    val intervalMinutes =
        (configuration.hourlyInterval ?: return emptyList()).toLong() * 60L
    if (intervalMinutes <= 0L) return emptyList()
    val reference = LocalDateTime.of(
        configuration.hourlyReferenceDate ?: return emptyList(),
        configuration.hourlyReferenceTime ?: return emptyList(),
    )
    val effectiveStart = maxOf(
        rangeStart,
        configuration.startsOn.atStartOfDay(),
    )
    val minutesFromReference = Duration.between(reference, effectiveStart).toMinutes()
    val firstIndex = if (minutesFromReference <= 0L) {
        0L
    } else {
        (minutesFromReference + intervalMinutes - 1L) / intervalMinutes
    }
    val results = mutableListOf<UpcomingMedicationAdministration>()
    var occurrence = reference.plusMinutes(firstIndex * intervalMinutes)
    while (!occurrence.isAfter(rangeEnd)) {
        if (configuration.containsActualDate(occurrence.toLocalDate())) {
            results += configuration.hourlyOccurrence(plan, occurrence)
        }
        occurrence = occurrence.plusMinutes(intervalMinutes)
    }
    return results
}

private fun projectWindowedHourlyOccurrences(
    plan: MedicationPlan,
    configuration: MedicationConfiguration,
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
): List<UpcomingMedicationAdministration> {
    val intervalMinutes =
        (configuration.hourlyInterval ?: return emptyList()).toLong() * 60L
    val windowStart = configuration.hourlyWindowStart ?: return emptyList()
    val windowEnd = configuration.hourlyWindowEnd ?: return emptyList()
    val firstTime = configuration.hourlyFirstAdministration ?: return emptyList()
    if (intervalMinutes <= 0L) return emptyList()
    val results = mutableListOf<UpcomingMedicationAdministration>()
    var anchorDate = maxOf(
        configuration.startsOn,
        rangeStart.toLocalDate().minusDays(1),
    )
    val lastAnchorDate = minOf(
        configuration.endsOn ?: rangeEnd.toLocalDate(),
        rangeEnd.toLocalDate(),
    )
    while (!anchorDate.isAfter(lastAnchorDate)) {
        if (configuration.weekdays.isEmpty() ||
            anchorDate.dayOfWeek in configuration.weekdays
        ) {
            val start = LocalDateTime.of(anchorDate, windowStart)
            val end = LocalDateTime.of(
                if (configuration.hourlyWindowEndsNextDay) {
                    anchorDate.plusDays(1)
                } else {
                    anchorDate
                },
                windowEnd,
            )
            var occurrence = LocalDateTime.of(
                if (
                    configuration.hourlyWindowEndsNextDay &&
                    firstTime < windowStart
                ) {
                    anchorDate.plusDays(1)
                } else {
                    anchorDate
                },
                firstTime,
            )
            while (!occurrence.isAfter(end)) {
                if (
                    !occurrence.isBefore(start) &&
                    configuration.containsActualDate(occurrence.toLocalDate())
                ) {
                    configuration.hourlyOccurrence(plan, occurrence)
                        .takeIf { it.overlaps(rangeStart, rangeEnd) }
                        ?.let(results::add)
                }
                occurrence = occurrence.plusMinutes(intervalMinutes)
            }
        }
        anchorDate = anchorDate.plusDays(1)
    }
    return results
}

private fun MedicationConfiguration.appliesOn(date: LocalDate): Boolean {
    if (!containsActualDate(date)) return false
    return when (frequency) {
        MedicationFrequencyKind.EVERY_DAY -> true
        MedicationFrequencyKind.SPECIFIC_DAYS -> date.dayOfWeek in weekdays
        MedicationFrequencyKind.EVERY_N_DAYS -> {
            val reference = everyNDaysReferenceDate ?: return false
            val interval = everyNDaysInterval ?: return false
            val offset = ChronoUnit.DAYS.between(reference, date)
            interval > 0 && offset >= 0L && offset % interval == 0L
        }
        MedicationFrequencyKind.CYCLE -> {
            val configuredCycle = cycle ?: return false
            val offset = ChronoUnit.DAYS.between(configuredCycle.referenceDate, date)
            if (offset < 0L) return false
            val cycleLength = configuredCycle.activeDays + configuredCycle.restDays
            val cycleIndex = offset / cycleLength
            val withinDeclaredCycles =
                configuredCycle.numberOfCycles == null ||
                    cycleIndex < configuredCycle.numberOfCycles
            withinDeclaredCycles &&
                offset % cycleLength < configuredCycle.activeDays
        }
        MedicationFrequencyKind.EVERY_N_HOURS,
        MedicationFrequencyKind.AS_NEEDED,
        -> false
    }
}

private fun MedicationConfiguration.containsActualDate(date: LocalDate): Boolean =
    !date.isBefore(startsOn) &&
        when (status) {
            MedicationTreatmentStatus.REMOVED ->
                endsOn != null && date.isBefore(endsOn)
            else -> endsOn == null || !date.isAfter(endsOn)
        }

private fun MedicationAdministration.toOccurrence(
    plan: MedicationPlan,
    configuration: MedicationConfiguration,
    anchorDate: LocalDate,
): UpcomingMedicationAdministration? {
    val revision = plan.currentRevision
    val amount = amountOverride ?: configuration.baseAmount
    val (start, end, occurrenceTiming) = when (timing) {
        MedicationAdministrationTiming.EXACT_TIME -> {
            val dateTime = LocalDateTime.of(
                anchorDate,
                exactTime ?: return null,
            )
            Triple(dateTime, null, MedicationOccurrenceTiming.EXACT_TIME)
        }
        MedicationAdministrationTiming.TIME_WINDOW -> {
            val startTime = windowStart ?: return null
            val endTime = windowEnd ?: return null
            val startDateTime = LocalDateTime.of(anchorDate, startTime)
            val endDateTime = LocalDateTime.of(
                if (windowEndsNextDay) anchorDate.plusDays(1) else anchorDate,
                endTime,
            )
            Triple(
                startDateTime,
                endDateTime,
                MedicationOccurrenceTiming.TIME_WINDOW,
            )
        }
        MedicationAdministrationTiming.ACTIVITY_ONLY -> {
            val notice = activityNoticeTime ?: return null
            val dateTime = LocalDateTime.of(anchorDate, notice)
            Triple(
                dateTime,
                null,
                MedicationOccurrenceTiming.ACTIVITY_NOTICE,
            )
        }
    }
    return UpcomingMedicationAdministration(
        id = listOf(plan.id, revision.id, id, anchorDate).joinToString(":"),
        medicationPlanId = plan.id,
        medicationPlanRevisionId = revision.id,
        administrationId = id,
        medicationName = revision.medicationName,
        form = configuration.form,
        amount = amount,
        timing = occurrenceTiming,
        startsAt = start,
        endsAt = end,
        reminderAt = when (timing) {
            MedicationAdministrationTiming.EXACT_TIME ->
                start.takeUnless { reminderEnabled == false }
            MedicationAdministrationTiming.TIME_WINDOW ->
                noticeTime?.let { selectedNotice ->
                    LocalDateTime.of(
                        if (noticeNextDay) anchorDate.plusDays(1) else anchorDate,
                        selectedNotice,
                    )
                }
            MedicationAdministrationTiming.ACTIVITY_ONLY -> start
        },
        activityRelation = activityRelation.takeIf {
            timing == MedicationAdministrationTiming.ACTIVITY_ONLY
        },
        customActivity = customActivity.takeIf {
            timing == MedicationAdministrationTiming.ACTIVITY_ONLY
        },
    )
}

private fun MedicationConfiguration.hourlyOccurrence(
    plan: MedicationPlan,
    startsAt: LocalDateTime,
): UpcomingMedicationAdministration {
    val revision = plan.currentRevision
    return UpcomingMedicationAdministration(
        id = listOf(
            plan.id,
            revision.id,
            "hourly",
            startsAt,
        ).joinToString(":"),
        medicationPlanId = plan.id,
        medicationPlanRevisionId = revision.id,
        administrationId = null,
        medicationName = revision.medicationName,
        form = form,
        amount = baseAmount,
        timing = MedicationOccurrenceTiming.HOURLY_INTERVAL,
        startsAt = startsAt,
        endsAt = null,
        reminderAt = startsAt,
        activityRelation = null,
        customActivity = null,
    )
}

private fun UpcomingMedicationAdministration.overlaps(
    rangeStart: LocalDateTime,
    rangeEnd: LocalDateTime,
): Boolean {
    val effectiveEnd = endsAt ?: startsAt
    return !effectiveEnd.isBefore(rangeStart) && !startsAt.isAfter(rangeEnd)
}

private fun UpcomingMedicationAdministration.proximityPriority(
    now: LocalDateTime,
): Int {
    val effectiveEnd = endsAt ?: startsAt
    return when {
        !startsAt.isAfter(now) && !effectiveEnd.isBefore(now) -> 0
        timing != MedicationOccurrenceTiming.TIME_WINDOW &&
            kotlin.math.abs(Duration.between(now, startsAt).toMinutes()) <= 30L -> 0
        startsAt.isAfter(now) -> 1
        else -> 2
    }
}
