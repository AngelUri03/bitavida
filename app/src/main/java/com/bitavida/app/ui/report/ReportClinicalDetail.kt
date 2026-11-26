package com.bitavida.app.ui.report

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.bitavida.app.R
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.GlucoseHistoryPeriod
import com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind
import com.bitavida.app.domain.usecase.BloodPressureHistoryPeriod
import com.bitavida.app.domain.usecase.calculateGlucoseStatistics
import com.bitavida.app.domain.usecase.calculateBloodPressureStatistics
import com.bitavida.app.domain.usecase.projectBloodPressurePlot
import com.bitavida.app.domain.usecase.projectMeasurementHistoryRange
import com.bitavida.app.ui.measurements.GlucoseEvolutionChart
import com.bitavida.app.ui.measurements.BloodPressureHistoryChart
import com.bitavida.app.ui.measurements.PressureSeriesLegend
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reportSpanish = Locale.forLanguageTag("es-MX")
private val reportDateTime = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", reportSpanish)

@Stable
internal class ReportMedicationUiState {
    val expanded = mutableStateMapOf<String, Boolean>()
    val showAll = mutableStateMapOf<String, Boolean>()
    val historyExpanded = mutableStateMapOf<String, Boolean>()
}

@Stable
internal class ReportMeasurementUiState {
    var kind by mutableStateOf(MeasurementKind.CAPILLARY_GLUCOSE)
    var showAll by mutableStateOf(false)
    var scheduledExpanded by mutableStateOf(false)
    var showAllScheduled by mutableStateOf(false)
    val expandedRecords = mutableStateMapOf<String, Boolean>()
}

private fun capturedTime(epoch: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

private fun inReport(moment: LocalDateTime, report: FollowUpReport): Boolean =
    !moment.toLocalDate().isBefore(report.start) && !moment.toLocalDate().isAfter(report.end) &&
        !moment.isAfter(LocalDateTime.now())

private fun medicationOutcome(value: MedicationIntakeOutcome): String = when (value) {
    MedicationIntakeOutcome.COMPLETED -> "Se tomó"
    MedicationIntakeOutcome.PARTIAL -> "Toma parcial"
    MedicationIntakeOutcome.OMITTED -> "No se tomó"
    MedicationIntakeOutcome.REFUSED -> "Se rechazó"
    MedicationIntakeOutcome.UNCONFIRMED -> "Sin confirmar"
}

private fun medicationChange(value: MedicationPlanChangeKind): String = when (value) {
    MedicationPlanChangeKind.CREATED -> "Medicamento agregado"
    MedicationPlanChangeKind.CORRECTED -> "Indicación corregida"
    MedicationPlanChangeKind.BASE_DETAILS_CORRECTED -> "Datos corregidos"
    MedicationPlanChangeKind.SCHEDULE_CORRECTED -> "Horario corregido"
    MedicationPlanChangeKind.SUSPENDED -> "Suspensión registrada"
    MedicationPlanChangeKind.REACTIVATED -> "Reactivación registrada"
    MedicationPlanChangeKind.REACTIVATION_RESCHEDULED -> "Reactivación reprogramada"
    MedicationPlanChangeKind.REACTIVATION_CANCELLED -> "Reactivación cancelada"
    MedicationPlanChangeKind.COMPLETED -> "Tratamiento finalizado"
    MedicationPlanChangeKind.REMOVED -> "Medicamento retirado"
}

private fun presentation(text: String?): String? {
    val value = text?.takeIf { it.isNotBlank() } ?: return null
    return when (value.trim().uppercase(Locale.ROOT)) {
        "TABLET" -> "Tabletas"
        "CAPSULE" -> "Cápsulas"
        "ORAL_LIQUID" -> "Solución oral"
        "DROPS" -> "Gotas"
        "INJECTABLE" -> "Inyectable"
        "INHALER" -> "Inhalador"
        "TOPICAL" -> "Tópico"
        "PATCH" -> "Parche"
        "POWDER" -> "Polvo"
        "OTHER" -> "Otra presentación"
        else -> value
    }
}

/** Groups by persisted plan identity, never by a medication's display name. */
internal fun LazyListScope.reportMedicationItems(
    report: FollowUpReport,
    overview: MedicalCareOverview,
    personUnderCareId: String,
    state: ReportMedicationUiState,
) {
    val plans = overview.medicationPlans.filter {
        it.personUnderCareId == personUnderCareId && it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
    }.distinctBy { it.id }
    val planIds = plans.map { it.id }.toSet()
    // Select the exact revision already chosen by the report snapshot; do not
    // recalculate the cut or filter a scheduled response by its actual timestamp.
    val intakeRows = report.rows.filter { it.section == "Medicamentos" && it.sourceId?.startsWith("medication-intake:") == true }
        .associateBy { it.sourceId }
    val intakes = overview.medicationIntakes.distinctBy { it.id }.mapNotNull { intake ->
        if (intake.medicationPlanId !in planIds) return@mapNotNull null
        val row = intakeRows["medication-intake:${intake.id}"] ?: return@mapNotNull null
        val revision = intake.revisions.singleOrNull { it.revisionNumber == row.revisionNumber } ?: return@mapNotNull null
        intake to revision
    }
    val displayedPlans = plans.filter { it.id in report.medicationPlanIds }
    item("med_summary") {
        ClinicalPanel {
            Text("Resumen del periodo", style = MaterialTheme.typography.titleMedium)
            val counts = report.summaries.associate { it.label to it.count }
            val taken = counts["Tomas completas"] ?: 0
            val notTaken = counts["Tomas no realizadas"] ?: 0
            val unknown = counts["Tomas sin confirmar"] ?: 0
            val metrics = listOf(
                Triple("Medicamentos", displayedPlans.size, R.drawable.ic_v2_pill),
                Triple("Tomas registradas", taken, R.drawable.ic_v2_file),
                Triple("No realizadas", notTaken, R.drawable.ic_v2_close),
                Triple("Sin confirmar", unknown, R.drawable.ic_v2_clock),
            )
            metrics.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, count, icon) ->
                        Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(count.toString(), style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Text("Sin confirmar no significa incumplimiento; las programaciones históricas sin registro no pueden reconstruirse de forma completa.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (displayedPlans.isEmpty()) item("med_empty") {
        ClinicalPanel { Text("No hay medicamentos o declaraciones para este periodo.") }
    }
    displayedPlans.forEach { plan ->
        val records = intakes.filter { it.first.medicationPlanId == plan.id }
            .sortedByDescending { it.second.actualAt ?: it.second.scheduledAt }
        val cutoff = report.cutoffAt
        val known = plan.revisions.filter { !capturedTime(it.capturedAtEpochMillis).isAfter(cutoff) }
            .maxByOrNull { it.revisionNumber }
        val changes = plan.revisions.filter {
            val captured = capturedTime(it.capturedAtEpochMillis)
            inReport(captured, report) && !captured.isAfter(report.cutoffAt)
        }
            .sortedByDescending { it.capturedAtEpochMillis }
        item("med_plan_${plan.id}") {
            val open = state.expanded[plan.id] == true
            Surface(onClick = { state.expanded[plan.id] = !open },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { stateDescription = if (open) "Expandido" else "Contraído" },
                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_v2_pill), null, Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(known?.medicationName ?: "Medicamento registrado", style = MaterialTheme.typography.titleMedium)
                        if (known != null) {
                            Text(known.doseAsIndicated, style = MaterialTheme.typography.bodyMedium)
                            presentation(known.unitOrPresentation)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Última indicación registrada hasta el cierre del periodo.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else Text("La indicación histórica no está disponible para este periodo.",
                            style = MaterialTheme.typography.bodySmall)
                        val taken = records.count { it.second.outcome == MedicationIntakeOutcome.COMPLETED }
                        val notTaken = records.count { it.second.outcome in setOf(MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED) }
                        val unknown = report.rows.count {
                            it.section == "Medicamentos" && it.medicationPlanId == plan.id && reportMedicationIsUnconfirmed(it)
                        }
                        Text("$taken tomas · $notTaken no realizadas · $unknown sin confirmar",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (changes.isNotEmpty()) Text("${changes.size} cambios en el periodo",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.ic_v2_chevron), null,
                        Modifier.size(20.dp).graphicsLayer { rotationZ = if (open) 90f else 0f },
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (state.expanded[plan.id] == true) {
            if (records.isEmpty()) item("med_no_intakes_${plan.id}") {
                Text("No hay administraciones declaradas en este periodo.", style = MaterialTheme.typography.bodyMedium)
            }
            val shown = if (state.showAll[plan.id] == true) records else records.take(3)
            items(shown, key = { "intake_${it.first.id}" }) { (intake, revision) ->
                val historyKey = "intake:${intake.id}"
                val open = state.historyExpanded[historyKey] == true
                val corrected = intake.revisions.size > 1
                val motion = rememberBitavidaMotionAllowed(null)
                val content: @Composable () -> Unit = {
                    Column(Modifier.then(if (motion) Modifier.animateContentSize() else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text((revision.actualAt ?: revision.scheduledAt).format(reportDateTime),
                                    style = MaterialTheme.typography.titleSmall)
                                Text(if (revision.actualAt == null) "Hora programada" else "Hora declarada",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val amount = revision.actualAmount?.let { " · ${it.value} ${it.unit}" }.orEmpty()
                                Text(medicationOutcome(revision.outcome) + amount, style = MaterialTheme.typography.bodyMedium)
                                intakeRows["medication-intake:${intake.id}"]?.detail
                                    ?.takeIf { it.contains("Otra declaración de esta ocasión registra una respuesta") }
                                    ?.let { Text("Otra declaración de esta ocasión registra una respuesta; relación entre declaraciones no determinada.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                revision.observation?.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                if (corrected) Text("Con correcciones", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            if (corrected) Icon(painterResource(R.drawable.ic_v2_chevron), null,
                                Modifier.size(20.dp).graphicsLayer { rotationZ = if (open) 90f else 0f },
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        if (open && corrected) {
                            Text("Historial de correcciones", style = MaterialTheme.typography.labelLarge)
                            intake.revisions.forEach { version ->
                                Text("${capturedTime(version.capturedAtEpochMillis).format(reportDateTime)} · ${medicationOutcome(version.outcome)}",
                                    style = MaterialTheme.typography.bodySmall)
                                version.changeReason?.takeIf(String::isNotBlank)?.let {
                                    Text("Motivo: $it", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                if (corrected) {
                    Surface(onClick = { state.historyExpanded[historyKey] = !open },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { stateDescription = if (open) "Correcciones expandidas" else "Correcciones contraídas" },
                        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        content = content)
                } else {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        content = content)
                }
            }
            if (records.size > 3) item("med_all_${plan.id}") {
                val all = state.showAll[plan.id] == true
                ClinicalToggle(if (all) "Mostrar menos" else "Ver todas las administraciones") { state.showAll[plan.id] = !all }
            }
            if (changes.isNotEmpty()) {
                item("med_changes_header_${plan.id}") {
                    val key = "plan:${plan.id}"
                    val open = state.historyExpanded[key] == true
                    ClinicalToggle(if (open) "Ocultar cambios" else "Ver cambios durante el periodo") {
                        state.historyExpanded[key] = !open
                    }
                }
                if (state.historyExpanded["plan:${plan.id}"] == true) {
                    items(changes, key = { "med_change_${it.id}" }) { revision ->
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(medicationChange(revision.changeKind), style = MaterialTheme.typography.titleSmall)
                                Text(capturedTime(revision.capturedAtEpochMillis).format(reportDateTime),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Indicación: ${revision.doseAsIndicated}", style = MaterialTheme.typography.bodyMedium)
                                revision.changeReason?.takeIf(String::isNotBlank)?.let {
                                    Text("Motivo: $it", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.reportMeasurementItems(report: FollowUpReport, state: ReportMeasurementUiState) {
    val kinds = listOf(MeasurementKind.CAPILLARY_GLUCOSE, MeasurementKind.BLOOD_PRESSURE, MeasurementKind.WEIGHT)
    val labels = mapOf(MeasurementKind.CAPILLARY_GLUCOSE to "Glucosa", MeasurementKind.BLOOD_PRESSURE to "Presión arterial", MeasurementKind.WEIGHT to "Peso")
    val rowTitles = mapOf(MeasurementKind.CAPILLARY_GLUCOSE to "Glucosa capilar", MeasurementKind.BLOOD_PRESSURE to "Presión arterial", MeasurementKind.WEIGHT to "Peso")
    val records = report.measurements.filter { it.kind == state.kind }.sortedByDescending { it.occurredAt }
    val pending = report.rows.filter { it.section == "Mediciones programadas" && it.title == rowTitles[state.kind] }
        .sortedByDescending { it.occurredAt }
    item("measurement_overview") {
        ClinicalPanel {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { kind ->
                    FilterChip(selected = state.kind == kind, onClick = {
                        state.kind = kind; state.showAll = false; state.scheduledExpanded = false; state.showAllScheduled = false
                    }, label = { Text(labels.getValue(kind)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary, labelColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, if (state.kind == kind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                }
            }
            val stats = report.statistics
            val metrics = when (state.kind) {
                MeasurementKind.CAPILLARY_GLUCOSE -> listOf(
                    "Promedio descriptivo" to (stats?.glucose?.let { "${"%.1f".format(reportSpanish, it.average)} mg/dL" } ?: "—"),
                    "Mínimo" to (stats?.glucose?.let { "${it.minimum} mg/dL" } ?: "—"),
                    "Máximo" to (stats?.glucose?.let { "${it.maximum} mg/dL" } ?: "—"),
                    "Lecturas" to records.size.toString(),
                )
                MeasurementKind.BLOOD_PRESSURE -> listOf(
                    "Promedio sistólica" to (stats?.systolic?.let { "${"%.1f".format(reportSpanish, it.average)} mmHg" } ?: "—"),
                    "Promedio diastólica" to (stats?.diastolic?.let { "${"%.1f".format(reportSpanish, it.average)} mmHg" } ?: "—"),
                    "Mín–máx sistólica" to (stats?.systolic?.let { "${it.minimum}–${it.maximum} mmHg" } ?: "—"),
                    "Mín–máx diastólica" to (stats?.diastolic?.let { "${it.minimum}–${it.maximum} mmHg" } ?: "—"),
                    "Lecturas" to records.size.toString(),
                )
                MeasurementKind.WEIGHT -> listOf(
                    "Peso inicial" to (stats?.firstWeight?.let { "$it kg" } ?: "—"),
                    "Último peso" to (stats?.lastWeight?.let { "$it kg" } ?: "—"),
                    "Diferencia" to (stats?.weightDifference?.let { "$it kg" } ?: "—"),
                    "Lecturas" to records.size.toString(),
                )
            }
            metrics.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text("Valores descriptivos; no son un diagnóstico.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (records.none { quickTrendPoints(it).isNotEmpty() }) {
                Text("No hay valores numéricos para graficar en este periodo.", style = MaterialTheme.typography.bodyMedium)
            } else {
                ReportHistoryChart(report, records, state.kind)
            }
            if (state.kind == MeasurementKind.WEIGHT) report.currentWeightTarget?.let {
                Text("Referencia configurada actualmente: ${it.kilograms} kg; su vigencia histórica no está registrada.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item("measurement_recent") {
        Text("Registros", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    }
    if (records.isEmpty()) item("measurement_empty") { Text("No hay lecturas para este periodo.") }
    items(if (state.showAll) records else records.take(3), key = { "measurement_${it.kind}_${it.id}" }) { record ->
        val key = "${record.kind}:${record.id}"
        val expanded = state.expandedRecords[key] == true
        Surface(
            onClick = { state.expandedRecords[key] = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
            val motion = rememberBitavidaMotionAllowed(null)
            Column((if (motion) Modifier.animateContentSize() else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val text = when (val value = record.value) {
                is MeasurementValue.Glucose -> "${value.reading} mg/dL"
                is MeasurementValue.BloodPressure -> "${value.systolic}/${value.diastolic} mmHg" + (value.pulse?.takeIf(String::isNotBlank)?.let { " · Pulso: $it" } ?: "")
                is MeasurementValue.Weight -> "${value.kilograms.toPlainString()} kg"
            }
            val context = (record.value as? MeasurementValue.Glucose)?.context
            val contextLabel = when (context) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(record.occurredAt.format(reportDateTime), style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    contextLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Icon(painterResource(R.drawable.ic_v2_chevron), contentDescription = null,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                val note = record.glucoseMetadata?.note ?: record.bloodPressureMetadata?.note ?: record.weightMetadata?.note
                note?.takeIf(String::isNotBlank)?.let { Text("Nota: $it", style = MaterialTheme.typography.bodyMedium) }
                val scheduled = record.glucoseMetadata?.scheduledAt ?: record.bloodPressureMetadata?.scheduledAt ?: record.weightMetadata?.scheduledAt
                scheduled?.let { Text("Fecha programada: ${it.format(reportDateTime)}", style = MaterialTheme.typography.bodySmall) }
                Text("Fecha de registro: ${record.capturedAt.format(reportDateTime)}", style = MaterialTheme.typography.bodySmall)
                (record.value as? MeasurementValue.Weight)?.heightMeters?.let {
                    Text("Estatura registrada: ${it.toPlainString()} m", style = MaterialTheme.typography.bodySmall)
                }
            }
            }
        }
    }
    if (records.size > 3) item("measurement_all") {
        ClinicalToggle(if (state.showAll) "Mostrar menos" else "Ver todos los registros") { state.showAll = !state.showAll }
    }
    item("measurement_pending") {
        Surface(
            onClick = { state.scheduledExpanded = !state.scheduledExpanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = if (state.scheduledExpanded) "Expandido" else "Contraído" },
            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Programación y estados · ${pending.size}", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall)
                Icon(painterResource(R.drawable.ic_v2_chevron), contentDescription = null,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (state.scheduledExpanded) 90f else 0f },
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (state.scheduledExpanded) {
        if (pending.isEmpty()) item("measurement_pending_empty") {
            Text("No hay programaciones para este periodo.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(if (state.showAllScheduled) pending else pending.take(3)) { row ->
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.occurredAt.format(reportDateTime), style = MaterialTheme.typography.titleSmall)
                    Text(row.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (pending.size > 3) item("measurement_pending_all") {
            ClinicalToggle(if (state.showAllScheduled) "Mostrar menos" else "Ver todas las programaciones") {
                state.showAllScheduled = !state.showAllScheduled
            }
        }
    }
}

/** Keeps the official history charts bounded for long reports without discarding readings. */
@Composable
private fun ReportHistoryChart(
    report: FollowUpReport,
    records: List<MeasurementRecord>,
    kind: MeasurementKind,
) {
    var selectedDate by remember(report.start, report.end, kind) {
        mutableStateOf(records.first().occurredAt.toLocalDate())
    }
    val dayCount = ChronoUnit.DAYS.between(report.start, report.end) + 1
    val segmentDays = 60L
    val segmentCount = ((dayCount + segmentDays - 1) / segmentDays).toInt()
    if (kind == MeasurementKind.BLOOD_PRESSURE) PressureSeriesLegend()
    if (segmentCount == 1) {
        ReportHistoryChartSegment(report.start, report.end, records, kind, selectedDate, false) {
            selectedDate = it
        }
    } else {
        Text(
            "Desliza para recorrer el periodo. Cada tramo tiene su propia escala.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(Modifier.fillMaxWidth()) {
            items(segmentCount, key = { it }) { index ->
                val start = report.start.plusDays(index * segmentDays)
                val end = minOf(report.end, start.plusDays(segmentDays - 1))
                val count = ChronoUnit.DAYS.between(start, end).toInt() + 1
                Column(Modifier.width((count * 48 + 52).dp)) {
                    Text(
                        "${start.format(DateTimeFormatter.ofPattern("d MMM yyyy", reportSpanish))} – " +
                            end.format(DateTimeFormatter.ofPattern("d MMM yyyy", reportSpanish)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ReportHistoryChartSegment(start, end, records, kind, selectedDate, true) {
                        selectedDate = it
                    }
                }
            }
        }
    }
    val selectedRecords = records.filter { it.occurredAt.toLocalDate() == selectedDate }
        .sortedBy { it.occurredAt }
    Text(
        if (selectedRecords.isEmpty()) "Sin lecturas el ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", reportSpanish))}"
        else selectedRecords.joinToString("\n") { record ->
            val value = when (val reading = record.value) {
                is MeasurementValue.Glucose -> "${reading.reading} mg/dL"
                is MeasurementValue.BloodPressure -> "${reading.systolic}/${reading.diastolic} mmHg"
                is MeasurementValue.Weight -> "${reading.kilograms.toPlainString()} kg"
            }
            "${record.occurredAt.format(reportDateTime)} · $value"
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ReportHistoryChartSegment(
    start: LocalDate,
    end: LocalDate,
    records: List<MeasurementRecord>,
    kind: MeasurementKind,
    selectedDate: LocalDate,
    fitAllDays: Boolean,
    onSelectDate: (LocalDate) -> Unit,
) {
    val history = remember(records, start, end, kind) {
        projectMeasurementHistoryRange(records, kind, start, end, LocalDate.now())
    }
    val selectedInSegment = selectedDate.coerceIn(start, end)
    if (kind == MeasurementKind.BLOOD_PRESSURE) {
        val period = remember(history) {
            BloodPressureHistoryPeriod(
                kind = GlucoseHistoryPeriodKind.MONTH,
                start = start,
                endInclusive = end,
                history = history,
                plot = projectBloodPressurePlot(history),
                statistics = calculateBloodPressureStatistics(history.records),
                days = emptyList(),
            )
        }
        BloodPressureHistoryChart(period, selectedInSegment, onSelectDate, fitAllDays)
    } else {
        val period = remember(history, kind) {
            GlucoseHistoryPeriod(
                kind = GlucoseHistoryPeriodKind.MONTH,
                start = start,
                endInclusive = end,
                history = history,
                statistics = calculateGlucoseStatistics(history.records, kind),
                days = emptyList(),
            )
        }
        GlucoseEvolutionChart(
            period = period,
            selectedDate = selectedInSegment,
            onSelectDate = onSelectDate,
            unit = if (kind == MeasurementKind.WEIGHT) "kg" else "mg/dL",
            referenceValue = null,
            fitAllDays = fitAllDays,
        )
    }
}

@Composable
private fun ClinicalPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun ClinicalToggle(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text(label) }
}
