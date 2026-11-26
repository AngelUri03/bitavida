package com.bitavida.app.ui.report

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.FollowUpReportRow
import com.bitavida.app.domain.usecase.reportMedicationSchedule
import com.bitavida.app.domain.usecase.reportPresentation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read-only document projection shared by both previews and the PDF writer. */
internal data class FollowUpDocumentSection(val title: String, val rows: List<FollowUpReportRow>)
internal data class FollowUpDocumentModel(
    val patient: String,
    val caregiver: String,
    val start: LocalDate,
    val end: LocalDate,
    val generatedOn: LocalDate,
    val snapshotAt: LocalDateTime,
    val cutoffAt: LocalDateTime,
    val summary: List<String>,
    val sections: List<FollowUpDocumentSection>,
    /** Null for legacy snapshots that do not yet supply provenance evidence. */
    val sourceWarning: String? = null,
    val containsSimulatedData: Boolean = false,
    val medicationIdentity: ReportMedicationIdentity = ReportMedicationIdentity(emptyMap(), emptyMap()),
)

private val documentLocale = Locale.forLanguageTag("es-MX")
private val documentDate = DateTimeFormatter.ofPattern("d MMM yyyy", documentLocale)
private val documentDateTime = DateTimeFormatter.ofPattern("d MMM HH:mm", documentLocale)

internal fun buildFollowUpDocument(
    report: FollowUpReport,
    patient: String,
    caregiver: String,
    generatedOn: LocalDate,
): FollowUpDocumentModel {
    val medicationIdentity = reportMedicationIdentity(report)
    // Link supplementary text and correction history only by persisted identity. Two
    // independent facts with the same date, name, or value must remain separate.
    val relatedRows = report.rows.filter { it.sourceId != null }.groupBy { it.sourceId!! }
    val merged = relatedRows.mapNotNull { (id, related) ->
        val primary = when {
            id.startsWith("care-event:") -> related.firstOrNull { it.section == "Citas" }
            id.startsWith("medication-intake:") -> related.firstOrNull { it.section == "Medicamentos" }
            // A plan can have several distinct effective versions; keep each as a
            // separate chronological row instead of concatenating it into one cell.
            else -> null
        } ?: return@mapNotNull null
        // Intake corrections belong in the audit appendix, not in the clinical row.
        val extras = if (id.startsWith("medication-intake:")) emptyList()
            else related.filter { it !== primary }.sortedBy { it.occurredAt }
        val detail = buildString {
            append(reportMedicationDisplayDetail(primary))
            extras.forEach { extra ->
                append("\n")
                append(extra.section).append(" · ")
                append(extra.occurredAt.format(documentDateTime)).append(" · ")
                append(extra.title).append(": ").append(extra.detail)
                if (extra.provenance.isNotBlank()) append(" · ").append(extra.provenance)
            }
        }
        id to primary.copy(detail = detail)
    }.toMap()
    val used = mutableSetOf<String>()
    val documentRows = report.rows.mapNotNull { row ->
        val id = row.sourceId
        if (id?.startsWith("medication-intake:") == true && row.section != "Medicamentos") {
            null // A correction remains audit information even when its original intake predates the period.
        } else if (id != null && id in merged) {
            if (used.add(id)) merged[id] else null
        } else if (row.section == "Medicamentos") row.copy(detail = reportMedicationDisplayDetail(row))
        else row
    }.map { row ->
        val label = reportMedicationPlanId(row)?.let { medicationIdentity.planLabels[it] }
        if (row.section == "Programación de medicamentos" && label != null)
            row.copy(title = "${row.title} · $label") else row
    }
    val auditRows = relatedRows.flatMap { (id, related) ->
        if (!id.startsWith("medication-intake:")) emptyList() else {
            val primary = related.firstOrNull { it.section == "Medicamentos" }
            related.filter { it !== primary }
                .map { correction ->
                    val labels = listOfNotNull(
                        correction.medicationPlanId?.let { medicationIdentity.planLabels[it] },
                        correction.sourceId?.let { medicationIdentity.declarationLabels[it] },
                    )
                    correction.copy(section = "Auditoría y correcciones",
                        title = correction.title + labels.joinToString(" · ", prefix = if (labels.isEmpty()) "" else " · "))
                }
        }
    }.sortedWith(compareBy({ it.occurredAt }, { it.revisionNumber ?: 0 }))
    // Compact consecutive projections only for the same persisted follow-up revision
    // and slot. Equal labels and times alone do not establish a relationship.
    val future = documentRows.filter {
        it.section in setOf("Mediciones programadas", "Observaciones programadas") &&
            it.detail == "Programada" && it.occurredAt.isAfter(report.cutoffAt)
    }
    val futureMedication = documentRows.filter { it.section == "Programación de medicamentos" }
    val futureRows = future.filter { it.groupId != null }
        .groupBy { listOf(it.section, it.title, it.occurredAt.toLocalTime(), it.groupId) }
        .values.flatMap { rows ->
            val sorted = rows.sortedBy { it.occurredAt }
            if (sorted.map { it.occurredAt.toLocalDate() }.distinct().size != sorted.size) {
                sorted
            } else {
                val runs = mutableListOf<MutableList<FollowUpReportRow>>()
                sorted.forEach { row ->
                    val run = runs.lastOrNull()
                    if (run != null && run.last().occurredAt.toLocalDate().plusDays(1) == row.occurredAt.toLocalDate()) {
                        run.add(row)
                    } else runs.add(mutableListOf(row))
                }
                runs.map { run ->
                    if (run.size == 1) run.first() else run.first().copy(
                        detail = "Programada diariamente hasta ${run.last().occurredAt.format(documentDateTime)} · ${run.size} ocasiones",
                    )
                }
            }
        }.plus(future.filter { it.groupId == null }).plus(futureMedication).sortedBy { it.occurredAt }
    val occurredRows = documentRows.filterNot { it in future || it.section == "Programación de medicamentos" }
    val groups = listOf(
        "Mediciones" to setOf("Mediciones", "Mediciones programadas"),
        "Medicamentos" to setOf("Indicaciones médicas", "Cambios de medicamentos", "Medicamentos", "Cambios de indicaciones"),
        "Observaciones" to setOf("Observaciones", "Observaciones programadas"),
        "Citas" to setOf("Citas"),
    )
    val covered = groups.flatMap { it.second }.toSet()
    val sections = groups.mapNotNull { (name, names) ->
        val sourceRows = occurredRows.filter { it.section in names }
        // Readings and scheduled occasions share their measurement type and timeline.
        val rows = (if (name == "Mediciones") sourceRows.map { it.copy(section = it.title) } else sourceRows)
            .sortedWith(compareBy<FollowUpReportRow> { it.section }.thenBy { it.occurredAt }.thenBy { it.title })
        if (rows.isEmpty()) null else FollowUpDocumentSection(name, rows)
    } + occurredRows.filter { it.section !in covered && it.section != "Auditoría y correcciones" }.groupBy { it.section }
        .map { (name, rows) -> FollowUpDocumentSection(name, rows) } +
        listOfNotNull(report.currentMedications.takeIf { it.isNotEmpty() }?.let { current ->
            val label = "Tratamiento vigente al corte"
            FollowUpDocumentSection(label, current.map { item ->
                val revision = item.revision
                val detail = buildList {
                    add("Cantidad indicada: ${revision.doseAsIndicated}")
                    revision.unitOrPresentation?.takeIf(String::isNotBlank)?.let { add("Presentación: ${reportPresentation(it)}") }
                    reportMedicationSchedule(revision)?.let { add("Frecuencia: $it") }
                    add("Inicio indicado: ${revision.startsOn.format(documentDate)}")
                    revision.endsOn?.let { add("Fin indicado: ${it.format(documentDate)}") }
                    revision.originalAdditionalInstructions?.takeIf(String::isNotBlank)?.let(::add)
                }.joinToString(" · ")
                val planLabel = medicationIdentity.planLabels[item.planId]?.let { " · $it" }.orEmpty()
                FollowUpReportRow(label, report.cutoffAt, revision.medicationName + planLabel, detail,
                    "Indicación médica transcrita", sourceId = "current-medication-plan:${item.planId}",
                    medicationPlanId = item.planId)
            })
        }) + listOfNotNull(futureRows.takeIf { it.isNotEmpty() }?.let {
            FollowUpDocumentSection("Programación posterior al corte", it)
        }) + listOfNotNull((auditRows + occurredRows.filter { it.section == "Auditoría y correcciones" })
            .sortedWith(compareBy({ it.occurredAt }, { it.revisionNumber ?: 0 }))
            .takeIf { it.isNotEmpty() }?.let {
                FollowUpDocumentSection("Auditoría y correcciones", it)
            })

    val summary = buildList {
        val counts = report.summaries.associate { it.label to it.count }
        val intakeCount = counts["Registros de toma"] ?: 0
        val measurementCount = (counts["Lecturas de glucosa"] ?: 0) +
            (counts["Lecturas de presión"] ?: 0) + (counts["Registros de peso"] ?: 0)
        val observationCount = counts["Registros de observación"] ?: 0
        val eventCount = counts["Citas"] ?: 0
        add("Actividad: $intakeCount ${if (intakeCount == 1) "toma registrada" else "tomas registradas"} · " +
            "$measurementCount ${if (measurementCount == 1) "medición" else "mediciones"} · " +
            "$observationCount ${if (observationCount == 1) "observación" else "observaciones"} · " +
            "$eventCount ${if (eventCount == 1) "cita" else "citas"}.")
        val medicationRows = report.rows.filter { it.section == "Medicamentos" }
        val notTaken = medicationRows.count(::reportMedicationWasNotPerformed)
        val unconfirmed = medicationRows.count(::reportMedicationIsUnconfirmed)
        // Corrections to an administration remain in its detail; they are not new changes in treatment.
        val changes = report.rows.filter { it.section == "Cambios de medicamentos" &&
            it.sourceId?.startsWith("medication-intake:") != true }
        val medicationCount = report.medicationSeriesIds.size
        add("Tratamiento: $medicationCount ${if (medicationCount == 1) "medicamento" else "medicamentos"} registrados · " +
            "$notTaken ${if (notTaken == 1) "no realizada" else "no realizadas"} · $unconfirmed sin confirmar · " +
            "${changes.size} ${if (changes.size == 1) "cambio" else "cambios"} de seguimiento.")
        val indications = report.rows.filter {
            it.section == "Indicaciones médicas" && it.sourceId?.startsWith("medication-plan:") == true
        }.groupBy { it.sourceId }.values.mapNotNull { versions -> versions.maxByOrNull { it.occurredAt } }
        if (indications.isNotEmpty()) {
            val shown = indications.take(2)
            val text = shown.joinToString("; ") { "${it.title.trim()}: ${it.detail.trim()}" }
            add("Indicaciones: $text" + if (indications.size > shown.size) "; ${indications.size - shown.size} más en el detalle." else ".")
        }
        val current = report.currentMedications
        if (current.isNotEmpty()) {
            val names = current.take(2).joinToString("; ") { "${it.revision.medicationName}: ${it.revision.doseAsIndicated}" }
            add("Tratamiento vigente al corte: $names" +
                if (current.size > 2) "; ${current.size - 2} más." else ".")
        } else add("Tratamiento vigente al corte: sin indicaciones activas registradas.")
        report.statistics?.let { stats ->
            fun fmt(value: Double) = String.format(documentLocale, "%.1f", value)
            stats.glucoseByContext.entries.sortedBy { it.key.ordinal }.forEach { (context, value) ->
                val label = when (context) {
                    GlucoseContext.FASTING -> "En ayunas"
                    GlucoseContext.BEFORE_MEAL -> "Antes de comer"
                    GlucoseContext.AFTER_MEAL -> "Después de comer"
                    GlucoseContext.BEFORE_SLEEP -> "Antes de dormir"
                    GlucoseContext.OTHER -> "Otro momento"
                    GlucoseContext.RESTING -> "En reposo"
                    GlucoseContext.AFTER_PHYSICAL_ACTIVITY -> "Después de actividad física"
                    GlucoseContext.UPON_WAKING -> "Al despertar"
                    GlucoseContext.UNSPECIFIED -> "Sin contexto registrado"
                }
                add("Glucosa · $label: promedio ${fmt(value.average)} mg/dL · ${value.count} ${if (value.count == 1) "lectura" else "lecturas"}.")
            }
        }
        reportSummaryMetrics(report).cards.filter {
            it.label.startsWith("Presión arterial") || it.label.startsWith("Peso")
        }.forEach { metric ->
            add("${metric.label}: ${metric.value} · ${metric.detail}.")
        }
        val glucoseValues = report.measurements.mapNotNull { record ->
            val value = record.value as? MeasurementValue.Glucose ?: return@mapNotNull null
            val numeric = value.reading.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            Triple(record, value, numeric)
        }
        if (glucoseValues.isNotEmpty()) {
            val min = glucoseValues.minBy { it.third }
            val max = glucoseValues.maxBy { it.third }
            fun context(value: GlucoseContext?) = when (value) {
                GlucoseContext.FASTING -> "en ayunas"
                GlucoseContext.BEFORE_MEAL -> "antes de comer"
                GlucoseContext.AFTER_MEAL -> "después de comer"
                GlucoseContext.BEFORE_SLEEP -> "antes de dormir"
                GlucoseContext.OTHER -> "otro momento"
                GlucoseContext.RESTING -> "en reposo"
                GlucoseContext.AFTER_PHYSICAL_ACTIVITY -> "después de actividad física"
                GlucoseContext.UPON_WAKING -> "al despertar"
                else -> null
            }
            fun describe(item: Triple<com.bitavida.app.domain.model.MeasurementRecord, MeasurementValue.Glucose, Double>): String =
                "${item.second.reading} mg/dL (${item.first.occurredAt.format(documentDateTime)}" +
                    (context(item.second.context)?.let { ", $it" } ?: "") + ")"
            add("Extremos de glucosa: ${describe(min)} · ${describe(max)}.")
        }
        val pressureRecords = report.measurements.mapNotNull { record ->
            val reading = record.value as? MeasurementValue.BloodPressure ?: return@mapNotNull null
            val systolic = reading.systolic.toDoubleOrNull()?.takeIf { it.isFinite() }
            val diastolic = reading.diastolic.toDoubleOrNull()?.takeIf { it.isFinite() }
            Triple(record, systolic, diastolic)
        }
        val systolics = pressureRecords.filter { it.second != null }
        val diastolics = pressureRecords.filter { it.third != null }
        if (systolics.isNotEmpty() && diastolics.isNotEmpty()) {
            val minSys = systolics.minBy { it.second!! }; val maxSys = systolics.maxBy { it.second!! }
            val minDia = diastolics.minBy { it.third!! }; val maxDia = diastolics.maxBy { it.third!! }
            fun at(record: com.bitavida.app.domain.model.MeasurementRecord) = record.occurredAt.format(documentDateTime)
            add("Extremos de presión (mmHg): sistólica ${minSys.second} (${at(minSys.first)})–${maxSys.second} (${at(maxSys.first)}); diastólica ${minDia.third} (${at(minDia.first)})–${maxDia.third} (${at(maxDia.first)}).")
        }
        val weights = report.measurements.filter { it.value is MeasurementValue.Weight }.sortedBy { it.occurredAt }
        if (weights.isNotEmpty()) {
            val first = weights.first(); val last = weights.last()
            add("Peso: primer registro ${first.occurredAt.format(documentDateTime)}; último ${last.occurredAt.format(documentDateTime)}.")
        }
    }
    val coverage = report.sourceCoverage
    val sourceWarning = when {
        report.containsSimulatedScenario -> "Incluye datos simulados. No utilizar para atención médica."
        coverage == null -> null // Legacy input: provenance has not yet been integrated.
        coverage.hasSimulated && !coverage.isComplete ->
            "Incluye datos simulados; parte de la procedencia no está verificada."
        coverage.hasSimulated && coverage.hasReal ->
            "Incluye datos reales y simulados. Identifica su procedencia antes de usarlos."
        coverage.hasSimulated -> "Datos simulados. No utilizar para atención médica."
        !coverage.isComplete -> "No se ha verificado la procedencia de todos los datos."
        else -> null
    }
    return FollowUpDocumentModel(patient, caregiver, report.start, report.end, generatedOn,
        report.snapshotAt, report.cutoffAt, summary, sections, sourceWarning,
        containsSimulatedData = report.containsSimulatedScenario || coverage?.hasSimulated == true, medicationIdentity = medicationIdentity)
}
