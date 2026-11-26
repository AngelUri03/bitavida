package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val reportScheduleDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val reportScheduleTime = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Describe only the prescription configuration actually captured in this revision.
 * In particular, do not derive a schedule from later revisions or from reminder times.
 */
internal fun reportMedicationSchedule(revision: MedicationPlanRevision): String? {
    val configuration = revision.configuration
    if (configuration == null) {
        return buildList {
            revision.frequencyAsIndicated?.takeIf(String::isNotBlank)?.let(::add)
            val times = revision.scheduledDoses.map { it.time }.distinct().sorted()
            if (times.isNotEmpty()) add("Horarios: " + times.joinToString(", ") { it.format(reportScheduleTime) })
        }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    fun LocalDate.asReportDate() = format(reportScheduleDate)
    fun LocalTime.asReportTime() = format(reportScheduleTime)
    val parts = mutableListOf<String>()
    when (configuration.frequency) {
        MedicationFrequencyKind.EVERY_DAY -> parts += "Todos los días"
        MedicationFrequencyKind.SPECIFIC_DAYS -> {
            val days = configuration.weekdays.sortedBy { it.value }.map {
                when (it) {
                    DayOfWeek.MONDAY -> "lunes"
                    DayOfWeek.TUESDAY -> "martes"
                    DayOfWeek.WEDNESDAY -> "miércoles"
                    DayOfWeek.THURSDAY -> "jueves"
                    DayOfWeek.FRIDAY -> "viernes"
                    DayOfWeek.SATURDAY -> "sábado"
                    DayOfWeek.SUNDAY -> "domingo"
                }
            }
            parts += if (days.isEmpty()) "Días específicos no registrados" else days.joinToString(", ")
        }
        MedicationFrequencyKind.EVERY_N_DAYS -> {
            parts += configuration.everyNDaysInterval?.let {
                if (it == 1) "Cada día" else "Cada $it días"
            } ?: "Intervalo en días no registrado"
            configuration.everyNDaysReferenceDate?.let { parts += "Fecha de referencia: ${it.asReportDate()}" }
        }
        MedicationFrequencyKind.EVERY_N_HOURS -> {
            parts += configuration.hourlyInterval?.let {
                if (it == 1) "Cada hora" else "Cada $it horas"
            } ?: "Intervalo en horas no registrado"
            if (configuration.hourlyMode == MedicationHourlyIntervalMode.DAILY_WINDOW) {
                val start = configuration.hourlyWindowStart
                val end = configuration.hourlyWindowEnd
                if (start != null && end != null) {
                    parts += "Ventana: ${start.asReportTime()}–${end.asReportTime()}" +
                        if (configuration.hourlyWindowEndsNextDay) " del día siguiente" else ""
                }
                configuration.hourlyFirstAdministration?.let { parts += "Primera toma de la ventana: ${it.asReportTime()}" }
            } else {
                val date = configuration.hourlyReferenceDate
                val time = configuration.hourlyReferenceTime
                if (date != null || time != null) {
                    parts += "Referencia: " + listOfNotNull(date?.asReportDate(), time?.asReportTime()).joinToString(" ")
                }
            }
        }
        MedicationFrequencyKind.CYCLE -> {
            val cycle = configuration.cycle
            if (cycle == null) parts += "Datos del ciclo no registrados"
            else {
                parts += "Ciclo: ${cycle.activeDays} días de toma y ${cycle.restDays} de descanso"
                parts += "Fecha de referencia: ${cycle.referenceDate.asReportDate()}"
                cycle.numberOfCycles?.let { parts += "Número de ciclos: $it" }
            }
        }
        MedicationFrequencyKind.AS_NEEDED -> {
            parts += "Cuando sea necesario"
            configuration.asNeededInstruction?.takeIf(String::isNotBlank)?.let { parts += "Indicación: $it" }
            configuration.asNeededRestrictions?.takeIf(String::isNotBlank)?.let { parts += "Restricciones: $it" }
        }
    }
    configuration.administrations.forEach { administration ->
        val timing = when (administration.timing) {
            MedicationAdministrationTiming.EXACT_TIME -> administration.exactTime?.asReportTime()
            MedicationAdministrationTiming.TIME_WINDOW -> {
                val start = administration.windowStart
                val end = administration.windowEnd
                if (start == null || end == null) null
                else "${start.asReportTime()}–${end.asReportTime()}" +
                    if (administration.windowEndsNextDay) " del día siguiente" else ""
            }
            MedicationAdministrationTiming.ACTIVITY_ONLY -> null
        }
        val activity = when (administration.activityRelation) {
            MedicationActivityRelation.BEFORE_BREAKFAST -> "antes del desayuno"
            MedicationActivityRelation.WITH_BREAKFAST -> "con el desayuno"
            MedicationActivityRelation.AFTER_BREAKFAST -> "después del desayuno"
            MedicationActivityRelation.BEFORE_LUNCH -> "antes de la comida"
            MedicationActivityRelation.WITH_LUNCH -> "con la comida"
            MedicationActivityRelation.AFTER_LUNCH -> "después de la comida"
            MedicationActivityRelation.BEFORE_DINNER -> "antes de la cena"
            MedicationActivityRelation.WITH_DINNER -> "con la cena"
            MedicationActivityRelation.AFTER_DINNER -> "después de la cena"
            MedicationActivityRelation.BEFORE_SLEEP -> "antes de dormir"
            MedicationActivityRelation.OTHER -> administration.customActivity?.takeIf(String::isNotBlank)
            null -> null
        }
        val amount = administration.amountOverride?.let { "${it.value} ${reportPresentation(it.unit)}" }
        val description = listOfNotNull(timing, activity, amount).joinToString(" · ")
        if (description.isNotBlank()) parts += "Toma: $description"
    }
    revision.frequencyAsIndicated?.trim()?.takeIf { original ->
        original.isNotEmpty() &&
            parts.none { it.equals(original, ignoreCase = true) } &&
            original !in setOf("Cada cierto número de horas", "Cada cierto número de días")
    }?.let { parts += "Frecuencia transcrita: $it" }
    return parts.joinToString(" · ").takeIf(String::isNotBlank)
}

/**
 * Keep answers attached to the original catalog question whenever that schema is available.
 * Unknown fields remain visible, without guessing their meaning.
 */
internal fun reportObservationAnswers(record: ObservationRecord): List<String> {
    val definition = ObservationCatalog.all.firstOrNull {
        it.id == record.observationId && it.schemaVersion == record.schemaVersion
    }
    val fields = definition?.fields.orEmpty().associateBy { it.id }
    val orderedKeys = definition?.fields.orEmpty().map { it.id } +
        record.answers.keys.filterNot(fields::containsKey).sorted()
    return orderedKeys.mapNotNull { key ->
        val values = record.answers[key].orEmpty().filter(String::isNotBlank)
        if (values.isEmpty()) null
        else {
            val label = fields[key]?.label ?: "Campo registrado ($key)"
            "$label: " + values.joinToString(", ")
        }
    }
}
