package com.bitavida.app.ui.report

import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.FollowUpReport
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human-readable counts remain distinct and agree in number with the underlying events. */
internal fun reportMedicationCounts(complete: Int, partial: Int, notPerformed: Int, unconfirmed: Int, incomplete: Boolean): String =
    "$complete ${if (complete == 1) "completa" else "completas"} · " +
        "$partial ${if (partial == 1) "parcial" else "parciales"} · " +
        "$notPerformed ${if (notPerformed == 1) "no realizada" else "no realizadas"} · " +
        "${if (incomplete) "al menos " else ""}$unconfirmed sin confirmar."

/** Compact, descriptive values computed from the same report snapshot as its detail. */
internal data class ReportSummaryMetric(val label: String, val value: String, val detail: String)
internal data class ReportSummaryMetricSelection(
    val cards: List<ReportSummaryMetric>,
    val additionalGlucose: List<ReportSummaryMetric>,
) {
    val hasMore: Boolean get() = additionalGlucose.isNotEmpty()
}

internal fun reportSummaryMetrics(report: FollowUpReport): ReportSummaryMetricSelection {
    val locale = Locale.forLanguageTag("es-MX")
    fun number(value: Double) = String.format(locale, "%.1f", value)
    val glucose = report.statistics?.glucoseByContext.orEmpty().entries.sortedBy { it.key.ordinal }.map { (context, stat) ->
        val contextLabel = when (context) {
            GlucoseContext.FASTING -> "en ayunas"
            GlucoseContext.BEFORE_MEAL -> "antes de comer"
            GlucoseContext.AFTER_MEAL -> "después de comer"
            GlucoseContext.BEFORE_SLEEP -> "antes de dormir"
            GlucoseContext.OTHER -> "otro momento"
            GlucoseContext.RESTING -> "en reposo"
            GlucoseContext.AFTER_PHYSICAL_ACTIVITY -> "después de actividad física"
            GlucoseContext.UPON_WAKING -> "al despertar"
            GlucoseContext.UNSPECIFIED -> "contexto no registrado"
        }
        ReportSummaryMetric(
            "Glucosa · promedio $contextLabel",
            "${number(stat.average)} mg/dL",
            "${stat.count} ${if (stat.count == 1) "lectura" else "lecturas"} · Mín. ${number(stat.minimum)} · Máx. ${number(stat.maximum)}",
        )
    }
    // A pressure statistic is a mean over complete paired readings, never a
    // pairing of independently recorded systolic and diastolic values.
    val pairs = report.measurements.mapNotNull { record ->
        val value = record.value as? MeasurementValue.BloodPressure ?: return@mapNotNull null
        val systolic = value.systolic.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        val diastolic = value.diastolic.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        systolic to diastolic
    }
    val pressure = pairs.takeIf { it.isNotEmpty() }?.let {
        ReportSummaryMetric(
            "Presión arterial · promedio",
            "${number(it.map { pair -> pair.first }.average())} / ${number(it.map { pair -> pair.second }.average())} mmHg",
            "${it.size} ${if (it.size == 1) "par" else "pares"}",
        )
    }
    val weights = report.measurements.mapNotNull { record ->
        val value = record.value as? MeasurementValue.Weight ?: return@mapNotNull null
        val number = value.kilograms.toDouble().takeIf { it.isFinite() } ?: return@mapNotNull null
        record.occurredAt to number
    }.sortedBy { it.first }
    val weight = weights.takeIf { it.isNotEmpty() }?.let { records ->
        val last = records.last()
        val date = last.first.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        val change = if (records.size > 1) " · Cambio ${number(last.second - records.first().second)} kg" else ""
        ReportSummaryMetric(
            "Peso · último registro",
            "${number(last.second)} kg",
            "$date · ${records.size} ${if (records.size == 1) "registro" else "registros"}$change",
        )
    }
    val reserved = listOfNotNull(pressure, weight)
    val cardCount = (4 - reserved.size).coerceAtLeast(0)
    val selected = glucose.take(cardCount) + reserved
    return ReportSummaryMetricSelection(selected, glucose.drop(cardCount))
}
