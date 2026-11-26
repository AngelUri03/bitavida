package com.bitavida.app.ui.demo

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.UpcomingMedicationAdministration
import com.bitavida.app.domain.usecase.projectObservationOccurrences
import com.bitavida.app.ui.medical.MedicationPlanDisplayState
import com.bitavida.app.ui.medical.displayState
import com.bitavida.app.ui.medical.hasDeclaredResponse
import com.bitavida.app.ui.medical.medicationOccurrencesForDay
import java.time.LocalDateTime
import java.time.LocalTime

data class TodayMedicationItem(val occurrence: UpcomingMedicationAdministration) {
    val id: String get() = occurrence.id
}

data class TodayMeasurementItem(val kind: MeasurementKind, val occurrence: GlucoseScheduledOccurrence) {
    val id: String get() = kind.name + ":" + occurrence.id
}

enum class TodayActivityKind { MEDICATION, MEASUREMENT, OBSERVATION, REMINDER, CARE_EVENT }
enum class TodayActivityOutcome { PERFORMED, PARTIALLY_PERFORMED, NOT_PERFORMED, DISCARDED, AMBIGUOUS, UNCONFIRMED }

data class TodayResolvedActivity(
    val id: String,
    val kind: TodayActivityKind,
    val title: String,
    val secondary: String? = null,
    val scheduledAt: LocalDateTime,
    val outcome: TodayActivityOutcome,
    val actualAt: LocalDateTime? = null,
    val recordId: String? = null,
    val sourceId: String? = null,
    val measurementKind: MeasurementKind? = null,
    val spontaneous: Boolean = false,
    val unlinked: Boolean = false,
    val measurementOccurrence: GlucoseScheduledOccurrence? = null,
)

/** Attended means an explicit response exists, never clinical adherence. */
data class TodayCareProgress(
    val total: Int = 0,
    val attended: Int = 0,
    val performed: Int = 0,
    val notPerformed: Int = 0,
    val discarded: Int = 0,
    val partiallyPerformed: Int = 0,
)

/** Read-only presentation of existing module state, never another source of stored activities. */
data class TodayCareProjection(
    val medications: List<TodayMedicationItem> = emptyList(),
    val measurements: List<TodayMeasurementItem> = emptyList(),
    val observations: List<ObservationOccurrence> = emptyList(),
    val reminders: List<CareReminder> = emptyList(),
    val hasScheduledActivities: Boolean = false,
    val progress: TodayCareProgress = TodayCareProgress(),
    val resolvedActivities: List<TodayResolvedActivity> = emptyList(),
    val spontaneousActivities: List<TodayResolvedActivity> = emptyList(),
    val unlinkedActivities: List<TodayResolvedActivity> = emptyList(),
    /** Separate declarations for a scheduled occasion; never counted as extra occasions. */
    val additionalActivities: List<TodayResolvedActivity> = emptyList(),
) {
    val pendingCount: Int get() = medications.size + measurements.size + observations.size + reminders.size
}

private fun UpcomingMedicationAdministration.logicalIdentity() = listOf(
    medicationPlanId, administrationId, startsAt, endsAt, timing,
)

/**
 * Every module keeps its canonical occurrence/resolution rules. Resolved calendar rows reuse
 * actual responses; a stale reference, spontaneous record or another patient's fact cannot add one.
 */
fun projectTodayCare(
    overview: MedicalCareOverview,
    glucoseFollowUp: GlucoseFollowUp? = null,
    glucoseRecords: List<MeasurementRecord> = emptyList(),
    glucoseDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    bloodPressureFollowUp: GlucoseFollowUp? = null,
    bloodPressureRecords: List<MeasurementRecord> = emptyList(),
    bloodPressureDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    weightFollowUp: GlucoseFollowUp? = null,
    weightRecords: List<MeasurementRecord> = emptyList(),
    weightDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    observations: ObservationSnapshot = ObservationSnapshot(),
    reminders: List<CareReminder> = emptyList(),
    personUnderCareId: String,
    now: LocalDateTime,
    observationNow: LocalDateTime = now,
): TodayCareProjection {
    val day = now.toLocalDate()
    val resolved = mutableListOf<TodayResolvedActivity>()
    val unlinkedMedications = mutableListOf<TodayResolvedActivity>()
    val additionalMedicationDeclarations = mutableListOf<TodayResolvedActivity>()
    val pendingMedications = mutableListOf<TodayMedicationItem>()
    val personPlans = overview.medicationPlans.filter { it.personUnderCareId == personUnderCareId }.distinctBy { it.id }
    val medicationSchedules = personPlans.map { plan ->
        val indicationStatus = overview.indications.singleOrNull {
            it.id == plan.medicalIndicationId && it.personUnderCareId == personUnderCareId
        }?.currentRevision?.status
        plan to medicationOccurrencesForDay(plan, day).filter {
            plan.displayState(it.startsAt, indicationStatus) == MedicationPlanDisplayState.CURRENT
        }.distinctBy { it.logicalIdentity() }
    }

    fun resolveMedication(event: UpcomingMedicationAdministration, record: MedicationIntakeRecord) {
        val outcome = when (record.currentRevision.outcome) {
            MedicationIntakeOutcome.COMPLETED -> TodayActivityOutcome.PERFORMED
            MedicationIntakeOutcome.PARTIAL -> TodayActivityOutcome.PARTIALLY_PERFORMED
            MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED -> TodayActivityOutcome.NOT_PERFORMED
            MedicationIntakeOutcome.UNCONFIRMED -> return
        }
        val amount = event.amount.value + " " + event.amount.unit
        val secondary = when (record.currentRevision.outcome) {
            MedicationIntakeOutcome.PARTIAL -> "Toma parcial declarada · $amount"
            MedicationIntakeOutcome.REFUSED -> "Se rechazó · $amount"
            MedicationIntakeOutcome.OMITTED -> "No se tomó · $amount"
            else -> amount
        }
        resolved += TodayResolvedActivity(event.id, TodayActivityKind.MEDICATION,
            event.medicationName, secondary, event.startsAt, outcome,
            actualAt = record.currentRevision.actualAt, recordId = record.id, sourceId = event.medicationPlanId)
    }

    fun resolveMedicationDeclarations(
        event: UpcomingMedicationAdministration,
        declarations: List<MedicationIntakeRecord>,
    ): Boolean {
        val distinct = declarations.distinctBy { it.id }
        val answered = distinct.filter { it.hasDeclaredResponse() }
        // Several independently identified declarations do not prove a revision
        // relationship. The occurrence is attended, but no outcome is chosen for it.
        if (answered.size > 1) {
            resolved += TodayResolvedActivity(event.id, TodayActivityKind.MEDICATION,
                event.medicationName, "Varias declaraciones para esta ocasión",
                event.startsAt, TodayActivityOutcome.AMBIGUOUS, sourceId = event.medicationPlanId)
        } else answered.singleOrNull()?.let { resolveMedication(event, it) }
        val extras = if (answered.size > 1) distinct else distinct.filterNot { it in answered }
        extras.forEach { record ->
            val revision = record.currentRevision
            val outcome = when (revision.outcome) {
                MedicationIntakeOutcome.COMPLETED -> TodayActivityOutcome.PERFORMED
                MedicationIntakeOutcome.PARTIAL -> TodayActivityOutcome.PARTIALLY_PERFORMED
                MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED -> TodayActivityOutcome.NOT_PERFORMED
                MedicationIntakeOutcome.UNCONFIRMED -> TodayActivityOutcome.UNCONFIRMED
            }
            val amount = revision.actualAmount?.let { "${it.value} ${it.unit}" }
            additionalMedicationDeclarations += TodayResolvedActivity(
                "declaration:" + record.id, TodayActivityKind.MEDICATION, event.medicationName,
                listOfNotNull("Declaración vinculada a la ocasión", amount).joinToString(" · "),
                event.startsAt, outcome, actualAt = revision.actualAt, recordId = record.id,
                sourceId = event.medicationPlanId,
            )
        }
        return answered.isNotEmpty()
    }

    medicationSchedules.forEach { (plan, events) ->
        val records = overview.intakesFor(plan.id).distinctBy { it.id }
        // Resolve explicit references through actual historical snapshots. A different slot
        // at the same clock time is not evidence that it is the same administration.
        val aliases = plan.revisions.indices.flatMap { index ->
            medicationOccurrencesForDay(plan.copy(revisions = plan.revisions.take(index + 1)), day)
        }.associateBy { it.id }
        val representedRecordIds = mutableSetOf<String>()
        events.forEach { event ->
            val explicitlyLinked = records.filter { record ->
                val revision = record.currentRevision
                val linked = revision.scheduledOccurrenceId?.let(aliases::get)
                linked != null && revision.scheduledAt == linked.startsAt &&
                    linked.logicalIdentity() == event.logicalIdentity()
            }
            // A timestamp alone cannot establish that an older declaration belongs
            // to this occasion; leave it separately visible as an unlinked fact.
            val candidates = explicitlyLinked
            representedRecordIds += candidates.map { it.id }
            if (!resolveMedicationDeclarations(event, candidates)) {
                pendingMedications += TodayMedicationItem(event)
            }
        }
        // Historical occurrences remain separate unless their real logical identity matches.
        val represented = events.map { it.logicalIdentity() }.toSet()
        records.mapNotNull { record ->
            if (record.id in representedRecordIds) return@mapNotNull null
            val revision = record.currentRevision
            val event = aliases[revision.scheduledOccurrenceId] ?: return@mapNotNull null
            if (revision.scheduledAt != event.startsAt || event.logicalIdentity() in represented) return@mapNotNull null
            event to record
        }.groupBy { it.first.logicalIdentity() }.values.forEach { versions ->
            val event = versions.first().first
            val declarations = versions.map { it.second }
            representedRecordIds += declarations.map { it.id }
            resolveMedicationDeclarations(event, declarations)
        }
        // Preserve real facts whose schedule reference is missing, ambiguous or no longer
        // verifiable, without guessing which occurrence they belong to. They do not affect
        // scheduled progress and appear on their actual date when one was recorded.
        val historicalAliasesByDate = mutableMapOf<java.time.LocalDate, Map<String, UpcomingMedicationAdministration>>()
        records.forEach recordLoop@ { record ->
            val revision = record.currentRevision
            if (!record.hasDeclaredResponse() || record.id in representedRecordIds) return@recordLoop
            val displayAt = revision.actualAt ?: revision.scheduledAt
            if (displayAt.toLocalDate() != day) return@recordLoop
            val scheduledDay = revision.scheduledAt.toLocalDate()
            val reference = revision.scheduledOccurrenceId
            val linked = if (reference != null) {
                val byId = historicalAliasesByDate.getOrPut(scheduledDay) {
                    plan.revisions.indices.flatMap { index ->
                        medicationOccurrencesForDay(plan.copy(revisions = plan.revisions.take(index + 1)), scheduledDay)
                    }.associateBy { it.id }
                }
                byId[reference]?.startsAt == revision.scheduledAt
            } else false
            if (!linked) {
                val outcome = when (revision.outcome) {
                    MedicationIntakeOutcome.COMPLETED -> TodayActivityOutcome.PERFORMED
                    MedicationIntakeOutcome.PARTIAL -> TodayActivityOutcome.PARTIALLY_PERFORMED
                    MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED -> TodayActivityOutcome.NOT_PERFORMED
                    MedicationIntakeOutcome.UNCONFIRMED -> return@recordLoop
                }
                unlinkedMedications += TodayResolvedActivity("unlinked:" + record.id, TodayActivityKind.MEDICATION,
                    plan.currentRevision.medicationName, "Registro sin vinculación inequívoca", displayAt, outcome,
                    actualAt = revision.actualAt, recordId = record.id, sourceId = plan.id, unlinked = true)
            }
        }
    }

    fun associatedFollowUp(record: MeasurementRecord): String? = when (record.kind) {
        MeasurementKind.CAPILLARY_GLUCOSE -> record.glucoseMetadata?.followUpId
        MeasurementKind.BLOOD_PRESSURE -> record.bloodPressureMetadata?.followUpId
        MeasurementKind.WEIGHT -> record.weightMetadata?.followUpId
    }
    fun scheduledMeasurementAt(record: MeasurementRecord): LocalDateTime? = when (record.kind) {
        MeasurementKind.CAPILLARY_GLUCOSE -> record.glucoseMetadata?.scheduledAt
        MeasurementKind.BLOOD_PRESSURE -> record.bloodPressureMetadata?.scheduledAt
        MeasurementKind.WEIGHT -> record.weightMetadata?.scheduledAt
    }
    val measurementAliasCache = mutableMapOf<Triple<MeasurementKind, String, java.time.LocalDate>, Map<String, GlucoseScheduledOccurrence>>()
    fun measurementAliases(kind: MeasurementKind, schedule: GlucoseFollowUp, date: java.time.LocalDate): Map<String, GlucoseScheduledOccurrence> =
        measurementAliasCache.getOrPut(Triple(kind, schedule.id, date)) {
            schedule.revisions.flatMap { it.declaredOccurrencesOn(date) }.associateBy { it.id }
        }
    fun measurement(kind: MeasurementKind, followUp: GlucoseFollowUp?,
        records: List<MeasurementRecord>, decisions: List<GlucoseOccurrenceDecision>): List<GlucoseTodayOccurrence> {
        val schedule = followUp?.takeIf { it.personUnderCareId == personUnderCareId } ?: return emptyList()
        val aliases = measurementAliases(kind, schedule, day)
        val eligible = records.filter { record ->
            if (record.kind != kind || associatedFollowUp(record) != schedule.id) return@filter false
            val storedAt = scheduledMeasurementAt(record)
            storedAt == null || aliases[record.scheduledOccurrenceId()]?.scheduledAt == storedAt
        }.distinctBy { it.id }
        return projectMeasurementOccurrences(day, schedule, eligible, decisions.distinctBy { it.id }, kind)
    }
    fun measurementSummary(record: MeasurementRecord): String = when (val value = record.value) {
        is MeasurementValue.Glucose -> value.reading + if (value.reading.uppercase() in setOf("HI", "LO")) "" else " mg/dL"
        is MeasurementValue.BloodPressure -> value.systolic + "/" + value.diastolic + " mmHg" +
            (value.pulse?.let { " · Pulso: $it" } ?: "")
        is MeasurementValue.Weight -> value.kilograms.toPlainString() + " kg"
    }
    fun measurementTitle(kind: MeasurementKind): String = when (kind) {
        MeasurementKind.CAPILLARY_GLUCOSE -> "Glucosa capilar"
        MeasurementKind.BLOOD_PRESSURE -> "Presión arterial"
        MeasurementKind.WEIGHT -> "Peso"
    }
    val measurementSchedules = listOf(
        MeasurementKind.CAPILLARY_GLUCOSE to measurement(MeasurementKind.CAPILLARY_GLUCOSE, glucoseFollowUp, glucoseRecords, glucoseDecisions),
        MeasurementKind.BLOOD_PRESSURE to measurement(MeasurementKind.BLOOD_PRESSURE, bloodPressureFollowUp, bloodPressureRecords, bloodPressureDecisions),
        MeasurementKind.WEIGHT to measurement(MeasurementKind.WEIGHT, weightFollowUp, weightRecords, weightDecisions),
    )
    val pendingMeasurements = measurementSchedules.flatMap { (kind, scheduled) ->
        scheduled.filter { it.state == GlucoseOccurrenceState.UNCONFIRMED }
            .map { TodayMeasurementItem(kind, it.occurrence) }
    }.distinctBy { it.id }.sortedWith(compareBy<TodayMeasurementItem> { it.occurrence.scheduledAt }.thenBy { it.id })
    measurementSchedules.forEach { (kind, scheduled) ->
        val title = when (kind) {
            MeasurementKind.CAPILLARY_GLUCOSE -> "Glucosa capilar"
            MeasurementKind.BLOOD_PRESSURE -> "Presión arterial"
            MeasurementKind.WEIGHT -> "Peso"
        }
        scheduled.filter { it.state != GlucoseOccurrenceState.UNCONFIRMED }.forEach {
            resolved += TodayResolvedActivity(kind.name + ":" + it.occurrence.id, TodayActivityKind.MEASUREMENT,
                title, secondary = it.record?.let(::measurementSummary), scheduledAt = it.occurrence.scheduledAt,
                outcome = if (it.state == GlucoseOccurrenceState.RECORDED) TodayActivityOutcome.PERFORMED
                    else TodayActivityOutcome.NOT_PERFORMED,
                actualAt = it.record?.occurredAt, recordId = it.record?.id, measurementKind = kind,
                measurementOccurrence = it.occurrence)
        }
    }

    val personFollowUps = observations.followUps.filter {
        it.schedule.personUnderCareId == personUnderCareId
    }.distinctBy { it.schedule.id }
    val personObservationRecords = observations.records.filter { record ->
        record.personUnderCareId == personUnderCareId && personFollowUps.any {
            it.schedule.id == record.followUpId && it.observationId == record.observationId
        }
    }.distinctBy { it.id }
    val observationEvaluationTime = observationNow.takeIf { it.toLocalDate() == day } ?: now
    val observationCandidates = projectObservationOccurrences(day, personFollowUps, emptyList(), observationEvaluationTime)
    val validObservationRecords = personObservationRecords.filter { record ->
        observationCandidates.any { event ->
            record.occurrenceId == event.id && record.followUpId == event.followUpId &&
                record.observationId == event.observationId && record.scheduledAt == event.scheduledAt
        }
    }
    val observationSchedules = projectObservationOccurrences(day, personFollowUps, validObservationRecords,
        observationEvaluationTime)
    fun resolveObservation(event: ObservationOccurrence, record: ObservationRecord) {
        resolved += TodayResolvedActivity(event.id, TodayActivityKind.OBSERVATION,
            ObservationCatalog.all.firstOrNull { it.id == event.observationId }?.name ?: "Revisión de observación",
            secondary = record.description.takeIf(String::isNotBlank), scheduledAt = event.scheduledAt,
            outcome = if (record.result == ObservationResult.NOT_PERFORMED) TodayActivityOutcome.NOT_PERFORMED
                else TodayActivityOutcome.PERFORMED,
            actualAt = record.occurredAt?.let { record.occurredOn.atTime(it) },
            recordId = record.id, sourceId = record.observationId)
    }
    observationSchedules.forEach { event -> event.record?.let { resolveObservation(event, it) } }
    val representedObservations = observationSchedules.map { it.id }.toSet()
    val observationAliases = personFollowUps.flatMap { followUp ->
        followUp.schedule.revisions.indices.flatMap { index ->
            val snapshot = followUp.copy(schedule = followUp.schedule.copy(
                revisions = followUp.schedule.revisions.take(index + 1)))
            projectObservationOccurrences(day, listOf(snapshot), emptyList(), day.atTime(LocalTime.MAX))
        }
    }.associateBy { it.id }
    personObservationRecords.mapNotNull { record ->
        val event = observationAliases[record.occurrenceId] ?: return@mapNotNull null
        if (record.scheduledAt != event.scheduledAt || record.followUpId != event.followUpId ||
            record.observationId != event.observationId || event.id in representedObservations) return@mapNotNull null
        event to record
    }.groupBy { it.first.id }.values.forEach { versions ->
        val (event, record) = versions.maxWith(compareBy<Pair<ObservationOccurrence, ObservationRecord>> {
            it.second.createdAt
        }.thenBy { it.second.id })
        resolveObservation(event, record)
    }

    val remindersForPerson = reminders.filter { it.personUnderCareId == personUnderCareId }.distinctBy { it.id }
    val remindersForDay = remindersForPerson.filter { it.scheduledAt.toLocalDate() == day }
    remindersForDay.filter { it.status != CareReminderStatus.PENDING }.forEach {
        resolved += TodayResolvedActivity(it.id, TodayActivityKind.REMINDER, it.title,
            it.details, it.scheduledAt,
            if (it.status == CareReminderStatus.DONE) TodayActivityOutcome.PERFORMED else TodayActivityOutcome.DISCARDED,
            actualAt = it.closedAt, recordId = it.id, sourceId = it.id)
    }
    val pendingObservations = observationSchedules.filter { it.record == null }.distinctBy { it.id }
    val pendingReminders = remindersForDay.filter { it.status == CareReminderStatus.PENDING }
        .sortedWith(compareBy<CareReminder> { it.scheduledAt }.thenBy { it.id })
    val medicationItems = pendingMedications.distinctBy { it.occurrence.logicalIdentity() }
        .sortedWith(compareBy<TodayMedicationItem> { it.occurrence.startsAt }.thenBy { it.id })
    val resolvedItems = resolved.distinctBy { it.kind to it.id }
        .sortedWith(compareBy<TodayResolvedActivity> { it.scheduledAt }.thenBy { it.kind }.thenBy { it.id })
    val pendingCount = medicationItems.size + pendingMeasurements.size + pendingObservations.size + pendingReminders.size
    val progress = TodayCareProgress(
        total = pendingCount + resolvedItems.size,
        attended = resolvedItems.size,
        performed = resolvedItems.count { it.outcome == TodayActivityOutcome.PERFORMED },
        notPerformed = resolvedItems.count { it.outcome == TodayActivityOutcome.NOT_PERFORMED },
        discarded = resolvedItems.count { it.outcome == TodayActivityOutcome.DISCARDED },
        partiallyPerformed = resolvedItems.count { it.outcome == TodayActivityOutcome.PARTIALLY_PERFORMED },
    )
    val representedMeasurementIds = measurementSchedules.flatMap { (kind, events) ->
        events.mapNotNull { it.record?.id?.let { id -> kind to id } }
    }.toSet()
    val allMeasurementRecords = (glucoseRecords + bloodPressureRecords + weightRecords).distinctBy { it.kind to it.id }
    val spontaneousMeasurements = allMeasurementRecords.filter { record ->
            (record.kind to record.id) !in representedMeasurementIds && record.occurredAt.toLocalDate() == day &&
                when (record.kind) {
                    MeasurementKind.CAPILLARY_GLUCOSE -> record.glucoseMetadata?.entryKind != GlucoseEntryKind.SCHEDULED &&
                        record.glucoseMetadata?.scheduledOccurrenceId == null
                    MeasurementKind.BLOOD_PRESSURE -> record.bloodPressureMetadata?.entryKind != BloodPressureEntryKind.SCHEDULED &&
                        record.bloodPressureMetadata?.scheduledOccurrenceId == null
                    MeasurementKind.WEIGHT -> record.weightMetadata?.entryKind != WeightEntryKind.SCHEDULED &&
                        record.weightMetadata?.scheduledOccurrenceId == null
                }
        }.map { record ->
            TodayResolvedActivity(record.id, TodayActivityKind.MEASUREMENT, measurementTitle(record.kind),
                measurementSummary(record), record.occurredAt, TodayActivityOutcome.PERFORMED,
                actualAt = record.occurredAt, recordId = record.id, measurementKind = record.kind, spontaneous = true)
        }
    val spontaneousObservations = observations.records.filter {
        it.personUnderCareId == personUnderCareId && it.occurredOn == day && it.occurrenceId == null
    }.distinctBy { it.id }.map { record ->
        val whenRecorded = record.occurredAt?.let { record.occurredOn.atTime(it) }
        TodayResolvedActivity(record.id, TodayActivityKind.OBSERVATION,
            ObservationCatalog.all.firstOrNull { it.id == record.observationId }?.name ?: "Revisión de observación",
            record.description.takeIf(String::isNotBlank), whenRecorded ?: record.occurredOn.atStartOfDay(),
            if (record.result == ObservationResult.NOT_PERFORMED) TodayActivityOutcome.NOT_PERFORMED else TodayActivityOutcome.PERFORMED,
            actualAt = whenRecorded, recordId = record.id, sourceId = record.observationId, spontaneous = true)
    }
    // If a scheduled record cannot be matched to its exact historical occurrence, preserve
    // the actual fact without guessing another slot from its time or treating it as spontaneous.
    val unlinkedMeasurements = allMeasurementRecords.filter { record ->
        if (record.occurredAt.toLocalDate() != day || (record.kind to record.id) in representedMeasurementIds) return@filter false
        val scheduledAt = scheduledMeasurementAt(record)
        val hasScheduleReference = when (record.kind) {
            MeasurementKind.CAPILLARY_GLUCOSE -> record.glucoseMetadata?.entryKind == GlucoseEntryKind.SCHEDULED || record.glucoseMetadata?.scheduledOccurrenceId != null
            MeasurementKind.BLOOD_PRESSURE -> record.bloodPressureMetadata?.entryKind == BloodPressureEntryKind.SCHEDULED || record.bloodPressureMetadata?.scheduledOccurrenceId != null
            MeasurementKind.WEIGHT -> record.weightMetadata?.entryKind == WeightEntryKind.SCHEDULED || record.weightMetadata?.scheduledOccurrenceId != null
        }
        if (!hasScheduleReference) return@filter false
        val followUp = when (record.kind) {
            MeasurementKind.CAPILLARY_GLUCOSE -> glucoseFollowUp
            MeasurementKind.BLOOD_PRESSURE -> bloodPressureFollowUp
            MeasurementKind.WEIGHT -> weightFollowUp
        }?.takeIf { it.personUnderCareId == personUnderCareId && it.id == associatedFollowUp(record) }
        val reference = record.scheduledOccurrenceId()
        val candidateDates = when {
            followUp == null || reference == null -> emptyList()
            scheduledAt != null -> listOf(scheduledAt.toLocalDate())
            else -> followUp.revisions.mapNotNull { revision ->
                val prefix = revision.id + ":"
                if (!reference.startsWith(prefix)) null else
                    runCatching { java.time.LocalDate.parse(reference.removePrefix(prefix).substringBefore(':')) }.getOrNull()
            }.distinct()
        }
        val linked = followUp != null && reference != null && candidateDates.any { date ->
            val occurrence = measurementAliases(record.kind, followUp, date)[reference]
            occurrence != null && (scheduledAt == null || occurrence.scheduledAt == scheduledAt)
        }
        !linked
    }.map { record ->
        TodayResolvedActivity("unlinked:" + record.id, TodayActivityKind.MEASUREMENT, measurementTitle(record.kind),
            measurementSummary(record), record.occurredAt, TodayActivityOutcome.PERFORMED,
            actualAt = record.occurredAt, recordId = record.id, measurementKind = record.kind, unlinked = true)
    }
    val observationAliasCache = mutableMapOf<Pair<String, java.time.LocalDate>, Map<String, ObservationOccurrence>>()
    val unlinkedObservations = observations.records.asSequence().filter { record ->
        if (record.personUnderCareId != personUnderCareId || record.occurredOn != day || record.occurrenceId == null) return@filter false
        val scheduledAt = record.scheduledAt ?: return@filter true
        val followUp = personFollowUps.firstOrNull {
            it.schedule.id == record.followUpId && it.observationId == record.observationId
        } ?: return@filter true
        val aliases = observationAliasCache.getOrPut(followUp.schedule.id to scheduledAt.toLocalDate()) {
            followUp.schedule.revisions.indices.flatMap { index ->
                val snapshot = followUp.copy(schedule = followUp.schedule.copy(revisions = followUp.schedule.revisions.take(index + 1)))
                projectObservationOccurrences(scheduledAt.toLocalDate(), listOf(snapshot), emptyList(),
                    scheduledAt.toLocalDate().atTime(LocalTime.MAX))
            }.associateBy { it.id }
        }
        val event = aliases[record.occurrenceId]
        event == null || event.followUpId != record.followUpId ||
            event.observationId != record.observationId || event.scheduledAt != scheduledAt
    }.distinctBy { it.id }.map { record ->
        val actual = record.occurredAt?.let { record.occurredOn.atTime(it) }
        TodayResolvedActivity("unlinked:" + record.id, TodayActivityKind.OBSERVATION,
            ObservationCatalog.all.firstOrNull { it.id == record.observationId }?.name ?: "Revisión de observación",
            record.description.takeIf(String::isNotBlank), actual ?: record.occurredOn.atStartOfDay(),
            if (record.result == ObservationResult.NOT_PERFORMED) TodayActivityOutcome.NOT_PERFORMED else TodayActivityOutcome.PERFORMED,
            actualAt = actual, recordId = record.id, sourceId = record.observationId, unlinked = true)
    }.toList()
    return TodayCareProjection(
        medications = medicationItems,
        measurements = pendingMeasurements,
        observations = pendingObservations,
        reminders = pendingReminders,
        hasScheduledActivities = progress.total > 0 ||
            remindersForPerson.any { it.originallyScheduledAt.toLocalDate() == day },
        progress = progress,
        resolvedActivities = resolvedItems,
        spontaneousActivities = (spontaneousMeasurements + spontaneousObservations).sortedBy { it.scheduledAt },
        unlinkedActivities = (unlinkedMedications + unlinkedMeasurements + unlinkedObservations)
            .distinctBy { Triple(it.kind, it.measurementKind, it.recordId) }.sortedBy { it.scheduledAt },
        additionalActivities = additionalMedicationDeclarations.distinctBy { it.recordId }.sortedBy { it.scheduledAt },
    )
}
