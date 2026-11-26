package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** Translate known persisted presentation codes without changing arbitrary transcribed text. */
internal fun reportPresentation(value: String): String {
    val separator = value.indexOf(" · ")
    val token = if (separator < 0) value else value.substring(0, separator)
    val translated = when (token.trim().uppercase(Locale.ROOT)) {
        "TABLET" -> "Tableta"
        "CAPSULE" -> "Cápsula"
        "ORAL_LIQUID" -> "Solución oral"
        "DROPS" -> "Gotas"
        "INJECTABLE" -> "Inyectable"
        "INHALER" -> "Inhalador"
        "TOPICAL" -> "Tópico"
        "PATCH" -> "Parche"
        "POWDER" -> "Polvo"
        "OTHER" -> "Otra presentación"
        else -> return value
    }
    return translated + if (separator < 0) "" else value.substring(separator)
}

private fun reportMedicationOutcome(
    outcome: MedicationIntakeOutcome,
    historicalForm: MedicationProductForm? = null,
): String {
    if (historicalForm in setOf(MedicationProductForm.TOPICAL, MedicationProductForm.PATCH)) {
        when (outcome) {
            MedicationIntakeOutcome.COMPLETED -> return "Se aplicó"
            MedicationIntakeOutcome.PARTIAL -> return "Aplicación parcial"
            MedicationIntakeOutcome.OMITTED -> return "No se aplicó"
            else -> Unit
        }
    }
    if (historicalForm == MedicationProductForm.INHALER) {
        when (outcome) {
            MedicationIntakeOutcome.COMPLETED -> return "Se usó"
            MedicationIntakeOutcome.PARTIAL -> return "Uso parcial"
            MedicationIntakeOutcome.OMITTED -> return "No se usó"
            else -> Unit
        }
    }
    if (historicalForm == MedicationProductForm.INJECTABLE) {
        when (outcome) {
            MedicationIntakeOutcome.COMPLETED -> return "Se administró"
            MedicationIntakeOutcome.PARTIAL -> return "Administración parcial"
            MedicationIntakeOutcome.OMITTED -> return "No se administró"
            else -> Unit
        }
    }
    return when (outcome) {
        MedicationIntakeOutcome.COMPLETED -> "Se tomó"
        MedicationIntakeOutcome.PARTIAL -> "Toma parcial"
        MedicationIntakeOutcome.OMITTED -> "No se tomó"
        MedicationIntakeOutcome.REFUSED -> "Se rechazó"
        MedicationIntakeOutcome.UNCONFIRMED -> "Sin confirmar"
    }
}

private fun reportObservationOutcome(result: ObservationResult): String = when (result) {
    ObservationResult.CHANGES -> "Cambios declarados"
    ObservationResult.NO_CHANGES -> "Sin cambios declarados"
    ObservationResult.NOT_PERFORMED -> "No se realizó"
    ObservationResult.EVENT -> "Acontecimiento declarado"
}

private fun reportContextLabel(context: GlucoseContext?): String? = when (context) {
    GlucoseContext.FASTING -> "En ayunas"
    GlucoseContext.BEFORE_MEAL -> "Antes de comer"
    GlucoseContext.AFTER_MEAL -> "Después de comer"
    GlucoseContext.BEFORE_SLEEP -> "Antes de dormir"
    GlucoseContext.OTHER -> "Otro momento"
    GlucoseContext.RESTING -> "En reposo"
    GlucoseContext.AFTER_PHYSICAL_ACTIVITY -> "Después de actividad física"
    GlucoseContext.UPON_WAKING -> "Al despertar"
    GlucoseContext.UNSPECIFIED, null -> null
}

/** The saved time may be a reminder, and is not automatically a clinical administration time. */
enum class ReportScheduledTimeMeaning { EXPECTED, ACTIVITY_NOTICE, UNKNOWN }

/** Declared facts only; no clinical evaluation or inference is performed. */
data class FollowUpReportRow(
    val section: String,
    val occurredAt: LocalDateTime,
    val title: String,
    val detail: String,
    val provenance: String,
    val timeKnown: Boolean = true,
    /** Stable source identity for grouping related facts; never a displayed identifier. */
    val sourceId: String? = null,
    /** Persisted medication series identity, separate from plan and administration identities. */
    val groupId: String? = null,
    /** Actual and scheduled moments remain distinct; null means not supplied for this row. */
    val scheduledAt: LocalDateTime? = null,
    val actualAt: LocalDateTime? = null,
    /** Numeric version for deterministic ordering when capture timestamps coincide. */
    val revisionNumber: Int? = null,
    val scheduledTimeMeaning: ReportScheduledTimeMeaning = ReportScheduledTimeMeaning.UNKNOWN,
    /** Structured values used for presentation only when backed by persisted data. */
    val medicationOutcome: MedicationIntakeOutcome? = null,
    val medicationForm: MedicationProductForm? = null,
    /** Persisted treatment-period identity; distinct from the logical medication series. */
    val medicationPlanId: String? = null,
    /** Whether this row contributes one unresolved scheduled occasion, not a clinical response. */
    val medicationCountsAsUnconfirmed: Boolean? = null,
)

data class FollowUpReportSummary(val label: String, val count: Int)

data class ReportMeasurementSchedule(
    val kind: MeasurementKind,
    val followUp: GlucoseFollowUp?,
    val decisions: List<GlucoseOccurrenceDecision>,
)

/** Descriptive statistics over numeric declared values only; HI/LO remain individual records. */
data class ReportNumericStats(val count: Int, val average: Double, val minimum: Double, val maximum: Double)

data class FollowUpReportStatistics(
    val glucose: ReportNumericStats?,
    val systolic: ReportNumericStats?,
    val diastolic: ReportNumericStats?,
    val firstWeight: Double?,
    val lastWeight: Double?,
    val weightDifference: Double?,
    /** Separate arithmetic summaries; unspecified context is never inferred. */
    val glucoseByContext: Map<GlucoseContext, ReportNumericStats> = emptyMap(),
)

/** A treatment known to be active at the snapshot, independent of the report period. */
data class ReportCurrentMedication(
    val planId: String,
    val revision: MedicationPlanRevision,
    /** Persisted series identity; no name-based relationship is inferred. */
    val medicationSeriesId: String? = null,
)

/** Last declaration known by the local period boundary; later corrections remain in history. */
fun medicationIntakeAtReportEnd(
    record: MedicationIntakeRecord,
    end: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: LocalDateTime = LocalDateTime.now(),
): MedicationIntakeRevision? {
    val cutoff = minOf(end.atTime(LocalTime.MAX), now)
    return record.revisions.lastOrNull {
        !LocalDateTime.ofInstant(Instant.ofEpochMilli(it.capturedAtEpochMillis), zoneId).isAfter(cutoff)
    }
}

/**
 * A response linked to a persisted occurrence belongs to the scheduled interval.
 * Unlinked historical or additional declarations retain their fact date; a matching
 * clock time alone cannot prove that they answer a scheduled occurrence.
 */
internal fun medicationReportMoment(revision: MedicationIntakeRevision): LocalDateTime =
    if (revision.scheduledOccurrenceId != null) revision.scheduledAt
    else revision.actualAt ?: revision.scheduledAt

private fun reportNumericStats(values: List<Double>): ReportNumericStats? =
    if (values.isEmpty()) null
    else ReportNumericStats(values.size, values.average(), values.minOrNull()!!, values.maxOrNull()!!)

data class FollowUpReport(
    val start: LocalDate,
    val end: LocalDate,
    val rows: List<FollowUpReportRow>,
    val summaries: List<FollowUpReportSummary> = emptyList(),
    val statistics: FollowUpReportStatistics? = null,
    val measurements: List<MeasurementRecord> = emptyList(),
    val currentWeightTarget: WeightTarget? = null,
    /** Stable identities for plans with an applicable follow-up or a fact in this period. */
    val medicationPlanIds: Set<String> = emptySet(),
    val observations: List<ObservationRecord> = emptyList(),
    /** One immutable capture time shared by the projections, preview and document. */
    val snapshotAt: LocalDateTime = LocalDateTime.now(),
    /** Effective historical boundary: end of selected period or capture time, whichever is earlier. */
    val cutoffAt: LocalDateTime = minOf(end.atTime(LocalTime.MAX), snapshotAt),
    val currentMedications: List<ReportCurrentMedication> = emptyList(),
    /** Medication series represented by plans applicable to the selected period. */
    val medicationSeriesIds: Set<String> = emptySet(),
    /** Optional evidence supplied by the snapshot producer; null denotes legacy/unverified input. */
    val sourceCoverage: ReportSourceCoverage? = null,
    /** Global scenario metadata; does not attribute individual sources. */
    val containsSimulatedScenario: Boolean = false,
)

/**
 * Uses only the registered date of an appointment explicitly marked completed. This is not a
 * claim about its actual time and never infers completion from the passage of time.
 */
fun previousCompletedAppointmentDate(events: List<CareEvent>, personUnderCareId: String, now: LocalDateTime): LocalDate? =
    events.asSequence()
        .filter { it.personUnderCareId == personUnderCareId }
        .distinctBy { it.id }
        .filter { it.draft.kind == CareEventKind.APPOINTMENT && it.draft.status == CareEventStatus.COMPLETED }
        .mapNotNull { it.draft.scheduledAt }
        .filter { !it.isAfter(now) }
        .maxOrNull()
        ?.toLocalDate()

/**
 * Projects already loaded and patient-scoped snapshots. The caller must supply measurement
 * snapshots for the same person; those records intentionally have no patient ID in this model.
 * Observation snapshots contain their linked photo IDs, but no photo bytes; no record is created or modified.
 */
fun buildFollowUpReport(
    start: LocalDate,
    end: LocalDate,
    personUnderCareId: String,
    overview: MedicalCareOverview,
    measurements: List<MeasurementRecord>,
    observations: List<ObservationRecord>,
    events: List<CareEvent> = emptyList(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    measurementSchedules: List<ReportMeasurementSchedule> = emptyList(),
    observationFollowUps: List<ObservationFollowUp> = emptyList(),
    currentWeightTarget: WeightTarget? = null,
    now: LocalDateTime = LocalDateTime.now(),
    checkCancellation: () -> Unit = {},
    observationRevisions: List<ObservationRecordRevision> = emptyList(),
    supplementaryHistory: ReportSupplementaryHistory = ReportSupplementaryHistory(),
    sourceCoverage: ReportSourceCoverage? = null,
    containsSimulatedScenario: Boolean = false,
): FollowUpReport {
    require(!end.isBefore(start))
    fun inPeriod(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    fun capturedAt(epoch: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zoneId)
    fun eventDate(event: CareEvent) =
        event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate ?:
            event.draft.suggestedDate ?: event.createdAt.toLocalDate()
    val cutoff = minOf(end.atTime(LocalTime.MAX), now)
    val observationHistory = observationRevisions.groupBy { it.recordId }
    val historicalObservations = observations.asSequence()
        .filter { it.personUnderCareId == personUnderCareId }.distinctBy { it.id }
        .mapNotNull { record ->
            val versions = observationHistory[record.id]
            if (versions == null) record.takeUnless { it.createdAt.isAfter(cutoff) }
            else versions.filter { !it.correctedAt.isAfter(cutoff) }
                .maxByOrNull { it.revisionNumber }?.snapshot
        }.filter { it.personUnderCareId == personUnderCareId }.toList()
    val periodObservations = historicalObservations.filter {
        val knownTime = it.occurredAt.takeUnless { _ -> it.timePrecision == ObservationTimePrecision.UNKNOWN }
        inPeriod(it.occurredOn) && !it.occurredOn.atTime(knownTime ?: LocalTime.MIN).isAfter(now)
    }
    // Earlier measurement revisions did not snapshot scheduling metadata. If a later
    // correction exists, its current linkage cannot identify an earlier occasion.
    val uncertainMeasurementKinds = measurements.asSequence().filter {
        it.historicalAssociationUncertain && !it.occurredAt.isAfter(cutoff)
    }.map { it.kind }.toSet()
    val plans = overview.medicationPlans.filter {
        it.personUnderCareId == personUnderCareId && it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
    }.associateBy { it.id }
    // Restrict lifecycle reconstruction to versions known at the report boundary. A later
    // suspension must not rewrite an earlier report and a past suspension must not appear active.
    fun baselineFor(plan: MedicationPlan): MedicationPlanRevision? {
        val known = plan.revisions.filter { !capturedAt(it.capturedAtEpochMillis).isAfter(cutoff) }
        val baseline = known.lastOrNull { capturedAt(it.capturedAtEpochMillis).toLocalDate().isBefore(start) }
            ?: return null
        if (baseline.startsOn.isAfter(end) || baseline.endsOn?.isBefore(start) == true) return null
        // Later-captured lifecycle changes may declare an earlier effective date.
        // They must be considered if they were known by the report cutoff.
        val baselinePlan = plan.copy(revisions = known)
        val windowStart = maxOf(start, baseline.startsOn).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val lastDay = minOf(end, baseline.endsOn ?: end, cutoff.toLocalDate())
        if (lastDay.isBefore(maxOf(start, baseline.startsOn))) return null
        val windowEnd = minOf(lastDay.atTime(LocalTime.MAX), cutoff).atZone(zoneId).toInstant().toEpochMilli()
        val transitions = baselinePlan.lifecycleTransitions(zoneId)
        val overlaps = transitions.withIndex().any { (index, transition) ->
            transition.status == MedicationTreatmentStatus.ACTIVE &&
                transition.effectiveAtEpochMillis <= windowEnd &&
                (transitions.getOrNull(index + 1)?.effectiveAtEpochMillis ?: Long.MAX_VALUE) > windowStart
        }
        return baseline.takeIf { overlaps }
    }
    val medicationPlanIds = plans.values.filter { plan ->
        baselineFor(plan) != null || plan.revisions.any { revision ->
            val captured = capturedAt(revision.capturedAtEpochMillis)
            inPeriod(captured.toLocalDate()) && !captured.isAfter(cutoff)
        } || overview.medicationIntakes.any { intake ->
            if (intake.medicationPlanId != plan.id) false else {
                val revision = medicationIntakeAtReportEnd(intake, end, zoneId, now)
                val moment = revision?.let(::medicationReportMoment)
                moment != null && inPeriod(moment.toLocalDate()) && !moment.isAfter(now)
            }
        }
    }.mapTo(mutableSetOf()) { it.id }
    val expectedUnconfirmed = reportUnconfirmedMedicationOccurrences(
        plans.values, overview.medicationIntakes.distinctBy { it.id }, start.atStartOfDay(), cutoff, zoneId,
    )
    // Preserve separate declarations if their relationship is not explicitly a revision.
    val effectiveIntakes = overview.medicationIntakes.distinctBy { it.id }.mapNotNull { record ->
        medicationIntakeAtReportEnd(record, end, zoneId, now)?.let { record to it }
    }
    // Resolve occasion state independently from declarations. A persisted occurrence can
    // have multiple physical declarations; their relationship is not inferred.
    fun occurrenceKey(recordId: String): List<Any?>? =
        expectedUnconfirmed.resolvedByRecordId[recordId]?.let {
            listOf(it.medicationPlanId, it.administrationId, it.startsAt, it.endsAt, it.timing)
        }
    val linkedDeclarations = effectiveIntakes.mapNotNull { item ->
        occurrenceKey(item.first.id)?.let { it to item }
    }.groupBy({ it.first }, { it.second })
    val countsAsUnconfirmedByRecord = mutableMapOf<String, Boolean>()
    val answeredElsewhere = mutableSetOf<String>()
    linkedDeclarations.values.forEach { declarations ->
        val unconfirmed = declarations.filter { it.second.outcome == MedicationIntakeOutcome.UNCONFIRMED }
            .sortedBy { it.first.id }
        val hasResponse = declarations.any { it.second.outcome != MedicationIntakeOutcome.UNCONFIRMED }
        unconfirmed.forEachIndexed { index, item ->
            countsAsUnconfirmedByRecord[item.first.id] = !hasResponse && index == 0
            if (hasResponse) answeredElsewhere += item.first.id
        }
    }
    val ambiguousOccurrences = effectiveIntakes.filter { it.second.scheduledOccurrenceId != null }
        .groupBy { it.first.medicationPlanId to it.second.scheduledOccurrenceId }
        .filterValues { it.size > 1 }
        .values.flatMap { group ->
            group.sortedBy { it.first.id }.mapIndexed { index, item ->
                item.first.id to "Declaración ${index + 1} de ${group.size} para la misma ocasión; relación entre registros no determinada"
            }
        }.toMap()
    fun historicalMedicationName(plan: MedicationPlan, moment: LocalDateTime): String =
        plan.revisions.lastOrNull { !capturedAt(it.capturedAtEpochMillis).isAfter(moment) }
            ?.medicationName ?: (plan.revisions.first().medicationName + " (nombre histórico no disponible)")

    val rows = buildList {
        plans.values.forEach { plan ->
            // Preserve the last version known at the period boundary, without treating it as
            // an administration or asserting that a suspended plan was active.
            val baseline = baselineFor(plan)
            if (baseline != null) {
                val details = buildList {
                    add("Última versión registrada antes del periodo: ${baseline.revisionNumber}")
                    add("Cantidad indicada: ${baseline.doseAsIndicated}")
                    add("Inicio indicado: ${baseline.startsOn.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
                    baseline.endsOn?.let { add("Fin indicado: ${it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}") }
                    baseline.unitOrPresentation?.takeIf(String::isNotBlank)?.let { add("Presentación: ${reportPresentation(it)}") }
                    reportMedicationSchedule(baseline)?.let { add("Frecuencia: $it") }
                    baseline.originalAdditionalInstructions?.takeIf(String::isNotBlank)?.let(::add)
                }.joinToString(" · ")
                add(FollowUpReportRow("Indicaciones médicas", maxOf(start, baseline.startsOn).atStartOfDay(),
                    baseline.medicationName, details, "Indicación médica transcrita", timeKnown = false,
                    sourceId = "medication-plan:${plan.id}", groupId = "medication-series:${plan.medicationSeriesId}",
                    revisionNumber = baseline.revisionNumber))
            }
            plan.revisions.forEach { revision ->
                val captured = capturedAt(revision.capturedAtEpochMillis)
                if (inPeriod(captured.toLocalDate()) && !captured.isAfter(cutoff)) {
                    val action = when (revision.changeKind) {
                        MedicationPlanChangeKind.CREATED -> "Plan registrado"
                        MedicationPlanChangeKind.CORRECTED -> "Plan corregido"
                        MedicationPlanChangeKind.BASE_DETAILS_CORRECTED -> "Datos del plan corregidos"
                        MedicationPlanChangeKind.SCHEDULE_CORRECTED -> "Horario corregido"
                        MedicationPlanChangeKind.SUSPENDED -> "Suspensión registrada"
                        MedicationPlanChangeKind.REACTIVATED -> "Reactivación registrada"
                        MedicationPlanChangeKind.REACTIVATION_RESCHEDULED -> "Reactivación reprogramada"
                        MedicationPlanChangeKind.REACTIVATION_CANCELLED -> "Reactivación cancelada"
                        MedicationPlanChangeKind.COMPLETED -> "Finalización registrada"
                        MedicationPlanChangeKind.REMOVED -> "Retiro registrado"
                    }
                    val change = buildList {
                        add(action)
                        add("Momento de captura de la versión ${revision.revisionNumber}")
                        revision.lifecycleEffectiveAtEpochMillis?.let {
                            add("Vigencia declarada: ${capturedAt(it).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}")
                        }
                        revision.changeReason?.takeIf(String::isNotBlank)?.let { add("Motivo: $it") }
                    }.joinToString(" · ")
                    add(FollowUpReportRow("Cambios de medicamentos", captured, revision.medicationName,
                        change, "Cambio registrado por el cuidador", sourceId = "medication-plan:${plan.id}",
                        groupId = "medication-series:${plan.medicationSeriesId}", revisionNumber = revision.revisionNumber))
                    if (revision.changeKind !in setOf(
                            MedicationPlanChangeKind.SUSPENDED, MedicationPlanChangeKind.COMPLETED,
                            MedicationPlanChangeKind.REMOVED, MedicationPlanChangeKind.REACTIVATION_CANCELLED,
                        )) {
                        val transcribed = buildList {
                            add("Cantidad indicada: ${revision.doseAsIndicated}")
                            add("Inicio indicado: ${revision.startsOn.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
                            revision.endsOn?.let { add("Fin indicado: ${it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}") }
                            revision.unitOrPresentation?.takeIf(String::isNotBlank)?.let { add("Presentación: ${reportPresentation(it)}") }
                            reportMedicationSchedule(revision)?.let { add("Frecuencia: $it") }
                            revision.originalAdditionalInstructions?.takeIf(String::isNotBlank)?.let { add(it) }
                        }.joinToString(" · ")
                        add(FollowUpReportRow("Indicaciones médicas", captured,
                            revision.medicationName + " · versión ${revision.revisionNumber}",
                            transcribed, "Indicación médica transcrita", sourceId = "medication-plan:${plan.id}",
                            groupId = "medication-series:${plan.medicationSeriesId}", revisionNumber = revision.revisionNumber))
                    }
                }
            }
        }
        overview.medicationIntakes.distinctBy { it.id }.forEach { record ->
            val plan = plans[record.medicationPlanId] ?: return@forEach
            record.revisions.drop(1).forEachIndexed { index, revision ->
                val captured = capturedAt(revision.capturedAtEpochMillis)
                val factMoment = revision.actualAt ?: revision.scheduledAt
                if (inPeriod(captured.toLocalDate()) && !captured.isAfter(cutoff)) {
                    val previous = record.revisions[index]
                    val details = buildList {
                        val format = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        val previousOccurrence = expectedUnconfirmed.resolvedByRecordId[record.id]
                            ?.takeIf {
                                previous.scheduledOccurrenceId == revision.scheduledOccurrenceId &&
                                    previous.scheduledAt == it.startsAt
                            }
                        val timeLabel = when (previousOccurrence?.timing) {
                            MedicationOccurrenceTiming.ACTIVITY_NOTICE -> "Aviso de actividad"
                            null -> "Hora guardada (tipo no verificable)"
                            else -> "Ocasión prevista"
                        }
                        add("$timeLabel: ${previous.scheduledAt.format(format)}")
                        if (previous.scheduledAt != revision.scheduledAt)
                            add("Hora guardada corregida: ${revision.scheduledAt.format(format)}")
                        val historicalForm = previousOccurrence?.form
                        add("Estado: ${reportMedicationOutcome(previous.outcome, historicalForm)} → ${reportMedicationOutcome(revision.outcome, historicalForm)}")
                        revision.actualAt?.let { add("Hora declarada corregida: ${it.format(format)}") }
                        revision.actualAmount?.let { add("Cantidad declarada corregida: ${it.value} ${reportPresentation(it.unit)}") }
                        revision.observation?.takeIf(String::isNotBlank)?.let { add("Nota corregida: $it") }
                        revision.changeReason?.takeIf(String::isNotBlank)?.let { add("Motivo: $it") }
                        ambiguousOccurrences[record.id]?.let(::add)
                        val previousTime = (previous.actualAt ?: previous.scheduledAt)
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        add(if (previous.actualAt != null) "Hora declarada anterior: $previousTime" else "Hora guardada anterior: $previousTime")
                        previous.actualAmount?.let { add("Cantidad declarada anterior: ${it.value} ${reportPresentation(it.unit)}") }
                        previous.observation?.takeIf(String::isNotBlank)?.let { add("Nota anterior: $it") }
                    }.joinToString(" · ")
                    val historicalName = historicalMedicationName(plan, factMoment)
                    add(FollowUpReportRow("Cambios de medicamentos", captured,
                        historicalName, details,
                        "Corrección declarada por el cuidador", sourceId = "medication-intake:${record.id}",
                        groupId = "medication-series:${plan.medicationSeriesId}", revisionNumber = revision.revisionNumber,
                        medicationPlanId = plan.id))
                }
            }
            val current = medicationIntakeAtReportEnd(record, end, zoneId, now) ?: return@forEach
            val moment = medicationReportMoment(current)
            if (!inPeriod(moment.toLocalDate()) || moment.isAfter(now)) return@forEach
            val outcome = reportMedicationOutcome(current.outcome)
            val details = buildList {
                add(outcome)
                if (record.id in answeredElsewhere) add("Otra declaración de esta ocasión registra una respuesta; relación entre declaraciones no determinada")
                if (current.actualAt == null) add("Hora efectiva no registrada")
                current.actualAmount?.let { add("Cantidad declarada: ${it.value} ${reportPresentation(it.unit)}") }
                current.observation?.takeIf(String::isNotBlank)?.let(::add)
                ambiguousOccurrences[record.id]?.let(::add)
            }.joinToString(" · ")
            val nameAtOccurrence = historicalMedicationName(plan, moment)
            add(FollowUpReportRow("Medicamentos", moment,
                nameAtOccurrence, details,
                "Registro declarado por el cuidador", sourceId = "medication-intake:${record.id}",
                groupId = "medication-series:${plan.medicationSeriesId}", scheduledAt = current.scheduledAt,
                actualAt = current.actualAt, revisionNumber = current.revisionNumber,
                medicationOutcome = current.outcome,
                medicationPlanId = plan.id,
                medicationForm = expectedUnconfirmed.resolvedByRecordId[record.id]?.form,
                medicationCountsAsUnconfirmed = countsAsUnconfirmedByRecord[record.id],
                scheduledTimeMeaning = when (expectedUnconfirmed.resolvedByRecordId[record.id]?.timing) {
                    MedicationOccurrenceTiming.ACTIVITY_NOTICE -> ReportScheduledTimeMeaning.ACTIVITY_NOTICE
                    null -> ReportScheduledTimeMeaning.UNKNOWN
                    else -> ReportScheduledTimeMeaning.EXPECTED
                }))
        }
        expectedUnconfirmed.unmatched.forEach { occurrence ->
            val plan = plans[occurrence.medicationPlanId] ?: return@forEach
            add(FollowUpReportRow(
                "Medicamentos", occurrence.startsAt, occurrence.medicationName,
                if (occurrence.medicationPlanId in expectedUnconfirmed.plansWithUnverifiableRecords)
                    "Ocasión prevista · Asociación con registros existentes no verificable"
                else "Sin confirmar · Ocasión prevista sin confirmación al corte",
                "Programación registrada", sourceId = "medication-expected:${occurrence.id}",
                groupId = "medication-series:${plan.medicationSeriesId}",
                scheduledAt = occurrence.startsAt,
                medicationForm = occurrence.form,
                medicationPlanId = plan.id,
                scheduledTimeMeaning = if (occurrence.timing == MedicationOccurrenceTiming.ACTIVITY_NOTICE)
                    ReportScheduledTimeMeaning.ACTIVITY_NOTICE else ReportScheduledTimeMeaning.EXPECTED,
            ))
        }
        expectedUnconfirmed.inProgress.forEach { occurrence ->
            val plan = plans[occurrence.medicationPlanId] ?: return@forEach
            val endTime = requireNotNull(occurrence.endsAt)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            add(FollowUpReportRow(
                "Medicamentos", occurrence.startsAt, occurrence.medicationName,
                "Ocasión prevista · Ventana en curso hasta $endTime",
                "Programación registrada", sourceId = "medication-expected:${occurrence.id}",
                groupId = "medication-series:${plan.medicationSeriesId}",
                scheduledAt = occurrence.startsAt,
                medicationForm = occurrence.form,
                medicationPlanId = plan.id,
                scheduledTimeMeaning = if (occurrence.timing == MedicationOccurrenceTiming.ACTIVITY_NOTICE)
                    ReportScheduledTimeMeaning.ACTIVITY_NOTICE else ReportScheduledTimeMeaning.EXPECTED,
            ))
        }
        // Future treatment is a recorded plan, not a completed or missed administration.
        // Represent each known active lifecycle interval once rather than generating daily rows.
        if (end.atTime(LocalTime.MAX).isAfter(cutoff)) {
            plans.values.forEach { plan ->
                val known = plan.revisions.filter { !capturedAt(it.capturedAtEpochMillis).isAfter(cutoff) }
                val revision = known.lastOrNull() ?: return@forEach
                val schedule = reportMedicationSchedule(revision) ?: return@forEach
                val first = maxOf(start.atStartOfDay(), cutoff.plusNanos(1), revision.startsOn.atStartOfDay())
                val last = minOf(end.atTime(LocalTime.MAX), (revision.endsOn ?: end).atTime(LocalTime.MAX))
                if (first.isAfter(last)) return@forEach
                val transitions = plan.copy(revisions = known).lifecycleTransitions(zoneId)
                transitions.forEachIndexed { index, transition ->
                    if (transition.status != MedicationTreatmentStatus.ACTIVE) return@forEachIndexed
                    val transitionStart = capturedAt(transition.effectiveAtEpochMillis)
                    val transitionEnd = transitions.getOrNull(index + 1)?.let {
                        capturedAt(it.effectiveAtEpochMillis).minusNanos(1)
                    } ?: last
                    val segmentStart = maxOf(first, transitionStart)
                    val segmentEnd = minOf(last, transitionEnd)
                    if (segmentStart.isAfter(segmentEnd)) return@forEachIndexed
                    val format = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    val detail = "Pauta registrada al corte: $schedule · Intervalo previsto: " +
                        "${segmentStart.format(format)} – ${segmentEnd.format(format)}. " +
                        "La programación no confirma una administración."
                    add(FollowUpReportRow("Programación de medicamentos", segmentStart, revision.medicationName,
                        detail, "Indicación registrada", sourceId = "medication-future-plan:${plan.id}:$index",
                        groupId = "medication-series:${plan.medicationSeriesId}",
                        revisionNumber = revision.revisionNumber, medicationPlanId = plan.id))
                }
            }
        }
        measurements.distinctBy { it.kind to it.id }.forEach { record ->
            if (!inPeriod(record.occurredAt.toLocalDate()) || record.occurredAt.isAfter(now)) return@forEach
            val (title, detail) = when (val value = record.value) {
                is MeasurementValue.Glucose -> "Glucosa capilar" to buildList {
                    add(if (value.reading.toDoubleOrNull() != null) "${value.reading} mg/dL"
                        else value.reading)
                    reportContextLabel(value.context)?.let(::add)
                    record.glucoseMetadata?.note?.takeIf(String::isNotBlank)?.let { add("Nota: $it") }
                }.joinToString(" · ")
                is MeasurementValue.BloodPressure -> "Presión arterial" to buildList {
                    add("${value.systolic}/${value.diastolic} mmHg")
                    value.pulse?.takeIf(String::isNotBlank)?.let { add("Pulso: $it lpm") }
                    reportContextLabel(record.bloodPressureMetadata?.actualContext)?.let(::add)
                    record.bloodPressureMetadata?.note?.takeIf(String::isNotBlank)?.let { add("Nota: $it") }
                }.joinToString(" · ")
                is MeasurementValue.Weight -> "Peso" to buildList {
                    add("${value.kilograms.toPlainString()} kg")
                    value.heightMeters?.let { add("Estatura registrada: ${it.toPlainString()} m") }
                    reportContextLabel(record.weightMetadata?.actualContext)?.let(::add)
                    record.weightMetadata?.note?.takeIf(String::isNotBlank)?.let { add("Nota: $it") }
                }.joinToString(" · ")
            }
            val reportDetail = if (record.historicalAssociationUncertain)
                "$detail · Asociación con seguimiento no verificable al corte" else detail
            add(FollowUpReportRow("Mediciones", record.occurredAt, title, reportDetail,
                "Registro declarado por el cuidador", sourceId = "measurement:${record.kind}:${record.id}"))
        }
        periodObservations.forEach { record ->
                val title = ObservationCatalog.all.firstOrNull { it.id == record.observationId }?.name
                    ?: "Observación registrada"
                val outcome = reportObservationOutcome(record.result)
                val text = buildList {
                    add(outcome)
                    record.description.takeIf(String::isNotBlank)?.let(::add)
                    val answers = reportObservationAnswers(record)
                    if (answers.isNotEmpty()) add("Respuestas: " + answers.joinToString(", "))
                    if (record.localPhotoIds.isNotEmpty()) add("Fotografías adjuntas: ${record.localPhotoIds.size}")
                    if (record.occurredAt == null || record.timePrecision == ObservationTimePrecision.UNKNOWN) add("Hora no indicada")
                    else if (record.timePrecision == ObservationTimePrecision.APPROXIMATE) add("Hora aproximada")
                }.joinToString(" · ")
                val knownTime = record.occurredAt.takeUnless { record.timePrecision == ObservationTimePrecision.UNKNOWN }
                add(FollowUpReportRow("Observaciones", record.occurredOn.atTime(knownTime ?: LocalTime.MIN),
                    title, text, "Registro declarado por el cuidador", timeKnown = knownTime != null,
                    sourceId = "observation:${record.id}"))
                // Revisions are audit events, not extra clinical observations. Retain both
                // snapshots and the recorded reason without embedding the history in the row.
                fun observationSnapshot(value: ObservationRecord): String = buildList {
                    val time = value.occurredAt.takeUnless { value.timePrecision == ObservationTimePrecision.UNKNOWN }
                    val dayLabel = value.occurredOn.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    val timeLabel = time?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    add("Fecha: $dayLabel" + if (timeLabel != null) " · Hora: $timeLabel" else " · Hora no indicada")
                    if (time != null && value.timePrecision == ObservationTimePrecision.APPROXIMATE) add("Hora aproximada")
                    add(reportObservationOutcome(value.result))
                    value.description.takeIf(String::isNotBlank)?.let(::add)
                    reportObservationAnswers(value).takeIf { it.isNotEmpty() }
                        ?.let { add("Respuestas: " + it.joinToString(", ")) }
                    if (value.localPhotoIds.isNotEmpty()) add("Fotografías adjuntas: ${value.localPhotoIds.size}")
                }.joinToString(" · ")
                val versions = observationHistory[record.id].orEmpty().filter {
                    !it.correctedAt.isAfter(cutoff)
                }.sortedBy { it.revisionNumber }
                versions.zipWithNext().forEach { (previous, revision) ->
                    val detail = buildList {
                        revision.reason?.takeIf(String::isNotBlank)?.let { add("Motivo: $it") }
                        add("Versión anterior: ${observationSnapshot(previous.snapshot)}")
                        add("Versión corregida: ${observationSnapshot(revision.snapshot)}")
                    }.joinToString("\n")
                    add(FollowUpReportRow("Auditoría y correcciones", revision.correctedAt,
                        "Corrección de observación: $title", detail, "Corrección declarada por el cuidador",
                        sourceId = "observation-revision:${revision.id}", revisionNumber = revision.revisionNumber))
                }
            }
        events.asSequence().filter { it.personUnderCareId == personUnderCareId }.distinctBy { it.id }.forEach { event ->
            val draft = event.draft
            val confirmedDay = draft.scheduledAt?.toLocalDate() ?: draft.scheduledDate
            val reportDay = eventDate(event)
            if (!inPeriod(reportDay)) return@forEach
            val performed = draft.status in setOf(CareEventStatus.COMPLETED, CareEventStatus.PERFORMED,
                CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)
            if (performed && (draft.scheduledAt ?: reportDay.atStartOfDay()).isAfter(now)) return@forEach
            val label = when (draft.purpose) {
                CareEventPurpose.MEDICAL_CONSULTATION -> "Consulta"
                CareEventPurpose.STUDY -> "Estudio"
                CareEventPurpose.PROCEDURE -> "Procedimiento"
                CareEventPurpose.ADMINISTRATIVE -> "Gestión"
                CareEventPurpose.OTHER -> "Otra cita"
                CareEventPurpose.LEGACY -> when (draft.kind) {
                    CareEventKind.REVIEW_TO_SCHEDULE -> "Revisión por agendar"
                    CareEventKind.APPOINTMENT -> "Cita"
                    CareEventKind.STUDY -> "Estudio"
                }
            }
            val status = when (draft.status) {
                CareEventStatus.TO_SCHEDULE -> "Por agendar"
                CareEventStatus.CONFIRMED -> "Confirmada"
                CareEventStatus.COMPLETED -> "Realizada"
                CareEventStatus.CANCELLED -> "Cancelada"
                CareEventStatus.REQUESTED -> "Solicitado"
                CareEventStatus.STUDY_TO_SCHEDULE -> "Pendiente de programación"
                CareEventStatus.SCHEDULED -> "Programado"
                CareEventStatus.PERFORMED -> "Realizado"
                CareEventStatus.RESULT_PENDING -> "Resultado pendiente"
                CareEventStatus.RESULT_AVAILABLE -> "Resultado disponible"
                CareEventStatus.REVIEWED -> "Revisado en consulta"
            }
            val details = buildList {
                add("Estado declarado: $status")
                if (confirmedDay == null && draft.suggestedDate != null)
                    add("Fecha sugerida, no es una cita confirmada")
                else if (confirmedDay == null) add("Sin fecha confirmada; se muestra la fecha de registro")
                val consultation = when (draft.consultationType) {
                    CareConsultationType.FAMILY_MEDICINE -> "Medicina familiar"
                    CareConsultationType.GENERAL_MEDICINE -> "Medicina general"
                    CareConsultationType.SPECIALIST -> "Consulta de especialidad"
                    CareConsultationType.OTHER -> "Otra consulta"
                    CareConsultationType.UNSPECIFIED -> null
                }
                consultation?.let(::add)
                draft.specialty.takeIf(String::isNotBlank)?.let { add("Especialidad: $it") }
                draft.institution.takeIf(String::isNotBlank)?.let { add("Unidad: $it") }
                draft.location.takeIf(String::isNotBlank)?.let { add("Lugar: $it") }
                draft.office.takeIf(String::isNotBlank)?.let { add("Consultorio: $it") }
                draft.professional.takeIf(String::isNotBlank)?.let { add("Profesional: $it") }
                draft.caregiverNotes.takeIf(String::isNotBlank)?.let { add("Notas: $it") }
                draft.instructions.takeIf(String::isNotBlank)?.let { add("Indicaciones adicionales: $it") }
            }.joinToString(" · ")
            add(FollowUpReportRow("Citas", draft.scheduledAt ?: reportDay.atStartOfDay(),
                "$label: ${draft.title}", details, "Información declarada por el cuidador",
                timeKnown = draft.scheduledAt != null, sourceId = "care-event:${event.id}"))
            draft.transcribedIndication.takeIf(String::isNotBlank)?.let { text ->
                add(FollowUpReportRow("Indicaciones médicas",
                    draft.scheduledAt ?: reportDay.atStartOfDay(), "$label: ${draft.title}",
                    text, "Indicación médica transcrita", timeKnown = draft.scheduledAt != null,
                    sourceId = "care-event:${event.id}"))
            }
        }
        // Reuse the same read-only occurrence projection used by measurement histories.
        // Unconfirmed is uncertainty, never an omission, and cannot become a numeric value.
        var date = start
        while (date <= end && (measurementSchedules.isNotEmpty() || observationFollowUps.isNotEmpty())) {
            checkCancellation()
            measurementSchedules.forEach { schedule ->
                val label = when (schedule.kind) {
                    MeasurementKind.CAPILLARY_GLUCOSE -> "Glucosa capilar"
                    MeasurementKind.BLOOD_PRESSURE -> "Presión arterial"
                    MeasurementKind.WEIGHT -> "Peso"
                }
                projectMeasurementOccurrences(date, schedule.followUp, measurements, schedule.decisions, schedule.kind)
                    .filter { it.record == null }
                    .forEach { state ->
                        val status = when {
                            state.state == GlucoseOccurrenceState.NOT_PERFORMED -> "No realizada, declarado"
                            (state.occurrence.windowEndAt ?: state.occurrence.scheduledAt) > now -> "Programada"
                            schedule.kind in uncertainMeasurementKinds -> "Asociación con registros existentes no verificable"
                            else -> "Sin confirmar"
                        }
                        add(FollowUpReportRow("Mediciones programadas", state.occurrence.scheduledAt,
                            label, status, "Seguimiento programado",
                            sourceId = "measurement-occurrence:${state.occurrence.id}",
                            groupId = "measurement-slot:${schedule.kind}:${state.occurrence.followUpId}:${state.occurrence.followUpRevisionId}:${state.occurrence.slotId}"))
                    }
            }
            projectObservationOccurrences(date, observationFollowUps, historicalObservations, cutoff)
                .filter { it.record == null }
                .forEach { occurrence ->
                    val label = ObservationCatalog.all.firstOrNull { it.id == occurrence.observationId }?.name
                        ?: "Revisión de observación"
                    val status = if ((occurrence.windowEndAt ?: occurrence.scheduledAt) > now)
                        "Programada" else "Sin confirmar"
                    add(FollowUpReportRow("Observaciones programadas", occurrence.scheduledAt,
                        label, status, "Seguimiento programado",
                        sourceId = "observation-occurrence:${occurrence.id}",
                        groupId = "observation-slot:${occurrence.followUpId}:${occurrence.followUpRevisionId}:${occurrence.slotId}"))
                }
            if (date == end) break
            date = date.plusDays(1)
        }
        overview.indications.filter {
            it.personUnderCareId == personUnderCareId && it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
        }.distinctBy { it.id }.forEach { indication ->
            indication.revisions.drop(1).forEach { revision ->
                val captured = capturedAt(revision.capturedAtEpochMillis)
                if (inPeriod(captured.toLocalDate()) && !captured.isAfter(cutoff)) {
                    val description = buildList {
                        add("Versión ${revision.revisionNumber} de indicación · cambio registrado")
                        revision.changeReason?.takeIf(String::isNotBlank)?.let { add("Motivo: $it") }
                    }.joinToString(" · ")
                    add(FollowUpReportRow("Cambios de indicaciones", captured,
                        "Corrección registrada", description, "Cambio registrado por el cuidador"))
                }
            }
            // Select the last revision known by the end of the report rather than displaying a
            // later correction as if it had been known in a historical period.
            val revision = indication.revisions.lastOrNull {
                !capturedAt(it.capturedAtEpochMillis).isAfter(cutoff)
            }
            if (revision != null && !revision.indicatedOn.isAfter(end) &&
                (revision.validFrom == null || !revision.validFrom.isAfter(end)) &&
                (revision.validUntil == null || !revision.validUntil.isBefore(start))) {
                val displayDate = maxOf(start, revision.indicatedOn)
                add(FollowUpReportRow("Indicaciones médicas", displayDate.atStartOfDay(),
                    "Indicación transcrita (versión ${revision.revisionNumber} conocida al cierre)",
                    revision.originalText, "Indicación médica transcrita", timeKnown = false))
            }
        }
        addAll(supplementaryReportRows(start, end, cutoff, personUnderCareId, supplementaryHistory))
    }.sortedWith(compareBy<FollowUpReportRow> { it.section }.thenBy { it.occurredAt }
        .thenBy { it.revisionNumber ?: 0 }.thenBy { it.title })
    val personPlans = overview.medicationPlans.filter {
        it.personUnderCareId == personUnderCareId && it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
    }.map { it.id }.toSet()
    val intakes = overview.medicationIntakes.distinctBy { it.id }.mapNotNull { intake ->
        if (intake.medicationPlanId !in personPlans) return@mapNotNull null
        val revision = medicationIntakeAtReportEnd(intake, end, zoneId, now) ?: return@mapNotNull null
        val moment = medicationReportMoment(revision)
        if (inPeriod(moment.toLocalDate()) && !moment.isAfter(now)) intake to revision else null
    }
    val readings = measurements.distinctBy { it.kind to it.id }.filter {
        inPeriod(it.occurredAt.toLocalDate()) && !it.occurredAt.isAfter(now)
    }
    val observationCount = periodObservations.count { it.result != ObservationResult.NOT_PERFORMED }
    val eventCount = events.filter { it.personUnderCareId == personUnderCareId }
        .distinctBy { it.id }.count {
            val day = eventDate(it)
            val performed = it.draft.status in setOf(CareEventStatus.COMPLETED, CareEventStatus.PERFORMED,
                CareEventStatus.RESULT_PENDING, CareEventStatus.RESULT_AVAILABLE, CareEventStatus.REVIEWED)
            inPeriod(day) && (!performed || !(it.draft.scheduledAt ?: day.atStartOfDay()).isAfter(now))
        }
    val summaries = listOf(
        // Keep the existing home-screen counter as completed administrations. The document
        // also receives each declared outcome separately, after selecting one revision per record.
        FollowUpReportSummary("Registros de toma", intakes.count { it.second.outcome == MedicationIntakeOutcome.COMPLETED }),
        FollowUpReportSummary("Tomas completas", intakes.count { it.second.outcome == MedicationIntakeOutcome.COMPLETED }),
        FollowUpReportSummary("Tomas parciales", intakes.count { it.second.outcome == MedicationIntakeOutcome.PARTIAL }),
        FollowUpReportSummary("Tomas no realizadas", intakes.count {
            it.second.outcome == MedicationIntakeOutcome.OMITTED || it.second.outcome == MedicationIntakeOutcome.REFUSED
        }),
        FollowUpReportSummary("Tomas sin confirmar", intakes.count { (record, revision) -> revision.outcome == MedicationIntakeOutcome.UNCONFIRMED &&
            countsAsUnconfirmedByRecord[record.id] != false } +
            expectedUnconfirmed.unmatched.count { it.medicationPlanId !in expectedUnconfirmed.plansWithUnverifiableRecords }),
        FollowUpReportSummary("Ocasiones con asociación no verificable", expectedUnconfirmed.unmatched.count {
            it.medicationPlanId in expectedUnconfirmed.plansWithUnverifiableRecords
        }),
        FollowUpReportSummary("Pautas sin programación reconstruible", expectedUnconfirmed.plansWithoutReconstructibleSchedules.size),
        FollowUpReportSummary("Lecturas de glucosa", readings.count { it.kind == MeasurementKind.CAPILLARY_GLUCOSE }),
        FollowUpReportSummary("Lecturas de presión", readings.count { it.kind == MeasurementKind.BLOOD_PRESSURE }),
        FollowUpReportSummary("Registros de peso", readings.count { it.kind == MeasurementKind.WEIGHT }),
        FollowUpReportSummary("Registros de observación", observationCount),
        FollowUpReportSummary("Citas", eventCount),
    )
    val glucoseEntries = readings.mapNotNull { record ->
        val value = record.value as? MeasurementValue.Glucose ?: return@mapNotNull null
        val number = value.reading.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        (value.context ?: GlucoseContext.UNSPECIFIED) to number
    }
    val glucose = glucoseEntries.map { it.second }
    val glucoseByContext = glucoseEntries.groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> requireNotNull(reportNumericStats(values)) }
    val pressure = readings.mapNotNull { it.value as? MeasurementValue.BloodPressure }
    val weights = readings.sortedBy { it.occurredAt }
        .mapNotNull { (it.value as? MeasurementValue.Weight)?.kilograms?.toDouble() }
    val statistics = FollowUpReportStatistics(
        glucose = reportNumericStats(glucose),
        systolic = reportNumericStats(pressure.mapNotNull { it.systolic.toDoubleOrNull() }),
        diastolic = reportNumericStats(pressure.mapNotNull { it.diastolic.toDoubleOrNull() }),
        firstWeight = weights.firstOrNull(),
        lastWeight = weights.lastOrNull(),
        weightDifference = if (weights.size >= 2) weights.last() - weights.first() else null,
        glucoseByContext = glucoseByContext,
    )
    val cutoffEpoch = cutoff.atZone(zoneId).toInstant().toEpochMilli()
    val currentMedications = plans.values.mapNotNull { plan ->
        // Current treatment is determined at the report cutoff, not at export time.
        val known = plan.revisions.filter { it.capturedAtEpochMillis <= cutoffEpoch }
        val latest = known.lastOrNull() ?: return@mapNotNull null
        if (latest.startsOn.isAfter(cutoff.toLocalDate()) || latest.endsOn?.isBefore(cutoff.toLocalDate()) == true)
            return@mapNotNull null
        val transition = plan.copy(revisions = known).lifecycleTransitions(zoneId)
            .lastOrNull { it.effectiveAtEpochMillis <= cutoffEpoch }
        if (transition?.status != MedicationTreatmentStatus.ACTIVE) return@mapNotNull null
        ReportCurrentMedication(plan.id, latest, plan.medicationSeriesId)
    }
    return FollowUpReport(start, end, rows, summaries, statistics, readings.sortedBy { it.occurredAt },
        currentWeightTarget, medicationPlanIds, periodObservations, snapshotAt = now, cutoffAt = cutoff,
        currentMedications = currentMedications,
        medicationSeriesIds = medicationPlanIds.mapNotNullTo(mutableSetOf()) { plans[it]?.medicationSeriesId },
        sourceCoverage = sourceCoverage, containsSimulatedScenario = containsSimulatedScenario)
}
