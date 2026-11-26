package com.bitavida.app.ui.report

import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.usecase.FollowUpReport
import com.bitavida.app.domain.usecase.FollowUpReportRow
import com.bitavida.app.domain.usecase.FollowUpReportSummary
import com.bitavida.app.domain.usecase.buildFollowUpReport
import com.bitavida.app.ui.theme.BitavidaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Debug-only fixture. Contains no access to the user's repositories or their stored data.
 * The extra "scenario" accepts "five_days" (default), "long", or "empty".
 * The generated synthetic file is private to the debug app and can be inspected via run-as.
 */
class ReportFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "five_days"
        val report = fixtureReport(scenario)
        val generatedOn = report.end
        var pageText by mutableStateOf("Preparando documento ficticio…")
        setContent {
            BitavidaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize()) {
                        Text(pageText, style = MaterialTheme.typography.labelMedium)
                        FollowUpPdfPreview(
                            report = report,
                            caregiver = "Cuidadora ficticia",
                            patient = "Paciente ficticio",
                            onBack = { finish() },
                            generatedOn = generatedOn,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            pageText = withContext(Dispatchers.IO) {
                runCatching {
                    val regular = requireNotNull(ResourcesCompat.getFont(this@ReportFixtureActivity, R.font.kodchasan_regular))
                    val bold = requireNotNull(ResourcesCompat.getFont(this@ReportFixtureActivity, R.font.kodchasan_bold))
                    val file = File(cacheDir, "report-fixture.pdf")
                    file.outputStream().use {
                        writeFollowUpPdf(it, report, "Cuidadora ficticia", "Paciente ficticio", regular, bold, generatedOn)
                    }
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pages = PdfRenderer(descriptor).use { it.pageCount }
                    "Solo datos ficticios · $pages páginas · ${report.rows.size} filas"
                }.getOrElse { "No se pudo preparar el documento ficticio." }
            }
        }
    }
}

internal fun fixtureReport(scenario: String): FollowUpReport {
    val first = LocalDate.of(2025, 11, 16)
    val last = first.plusDays(4)
    if (scenario == "empty") return FollowUpReport(first, last, emptyList())

    val measurements = buildList {
        repeat(5) { day ->
            val date = first.plusDays(day.toLong())
            add(MeasurementRecord("g$day", MeasurementValue.Glucose("${112 + day * 3}", GlucoseContext.FASTING),
                date.atTime(8, 0), date.atTime(8, 2)))
            add(MeasurementRecord("p$day", MeasurementValue.BloodPressure("${118 + day}", "${76 + day}", "70"),
                date.atTime(9, 0), date.atTime(9, 2)))
            if (day == 0 || day == 4) add(MeasurementRecord(
                "w$day", MeasurementValue.Weight(BigDecimal(if (day == 0) "69.3" else "69.8")),
                date.atTime(7, 0), date.atTime(7, 2),
            ))
        }
    }
    val base = buildFollowUpReport(
        first, last, "paciente-ficticio", MedicalCareOverview(emptyList(), emptyList()),
        measurements, emptyList(),
    )
    val medicationIds = (1..4).map { "medicamento-ficticio-$it" }
    val planRows = medicationIds.mapIndexed { index, id ->
        FollowUpReportRow(
            "Indicaciones médicas", first.atStartOfDay(), "Medicamento ficticio ${index + 1}",
            "Indicación transcrita ficticia: una unidad según el horario registrado.",
            "Indicación médica transcrita", timeKnown = false, sourceId = "medication-plan:$id",
            groupId = "medication-series:$id",
        )
    }
    val medicationRows = buildList {
        medicationIds.forEachIndexed { med, planId ->
            repeat(5) { day ->
                repeat(2) { dose ->
                    val at = first.plusDays(day.toLong()).atTime(8 + dose * 10, 0)
                    val status = when {
                        med == 0 && day == 2 && dose == 0 -> "No se tomó"
                        med == 1 && day == 3 && dose == 1 -> "Sin confirmar"
                        else -> "Se tomó"
                    }
                    add(FollowUpReportRow("Medicamentos", at, "Medicamento ficticio ${med + 1}",
                        "$status · cantidad declarada: una unidad.", "Declaración de la persona cuidadora",
                        sourceId = "medication-intake:$med-$day-$dose", groupId = "medication-series:$planId",
                        medicationPlanId = planId, scheduledAt = at))
                }
            }
        }
    }
    val changes = listOf(
        FollowUpReportRow("Cambios de medicamentos", first.atTime(10, 0), "Medicamento ficticio 1",
            "Medicamento agregado. Indicación transcrita ficticia.", "Registro de seguimiento", sourceId = "medication-plan:${medicationIds[0]}", groupId = "medication-series:${medicationIds[0]}"),
        FollowUpReportRow("Cambios de medicamentos", first.plusDays(2).atTime(10, 0), "Medicamento ficticio 2",
            "Indicación modificada. Motivo declarado: texto ficticio.", "Registro de seguimiento", sourceId = "medication-plan:${medicationIds[1]}", groupId = "medication-series:${medicationIds[1]}"),
        FollowUpReportRow("Cambios de medicamentos", first.plusDays(3).atTime(10, 0), "Medicamento ficticio 3",
            "Seguimiento suspendido. Motivo declarado: texto ficticio.", "Registro de seguimiento", sourceId = "medication-plan:${medicationIds[2]}", groupId = "medication-series:${medicationIds[2]}"),
    )
    val correction = FollowUpReportRow("Cambios de medicamentos", first.plusDays(3).atTime(12, 0),
        "Medicamento ficticio 1", "Corrección ficticia: conserva el motivo declarado.",
        "Corrección declarada por la persona cuidadora", sourceId = "medication-intake:0-2-0",
        groupId = "medication-series:${medicationIds[0]}", medicationPlanId = medicationIds[0])
    val observations = (0..4).map { day ->
        val detail = if (scenario == "long" && day == 2) {
            "Descripción ficticia extensa. " +
                ("Línea de ejemplo con acentos, eñes y puntuación; conserva el salto de página. ".repeat(220))
        } else {
            "Cambio declarado de ejemplo; nota ficticia ${day + 1}. " +
                if (day == 1) "Fotografía adjunta, no incluida." else "Sin interpretación automática."
        }
        FollowUpReportRow("Observaciones", first.plusDays(day.toLong()).atTime(15, 30),
            "Observación ficticia ${day + 1}", detail, "Declaración de la persona cuidadora")
    }
    val appointments = listOf(
        FollowUpReportRow("Citas", first.atTime(11, 0), "Consulta ficticia", "Medicina familiar · Confirmada · Unidad ficticia.", "Registro declarado"),
        FollowUpReportRow("Citas", first.plusDays(2).atTime(12, 0), "Estudio ficticio", "Estudio · Realizado · Indicaciones: texto de ejemplo.", "Registro declarado"),
        FollowUpReportRow("Citas", last.atTime(13, 0), "Procedimiento ficticio", "Procedimiento · Cancelado.", "Registro declarado"),
    )
    val taken = medicationRows.count { it.detail.startsWith("Se tomó") }
    val summaries = listOf(
        FollowUpReportSummary("Registros de toma", taken),
        FollowUpReportSummary("Lecturas de glucosa", 5),
        FollowUpReportSummary("Lecturas de presión", 5),
        FollowUpReportSummary("Registros de peso", 2),
        FollowUpReportSummary("Registros de observación", observations.size),
        FollowUpReportSummary("Citas", appointments.size),
    )
    return base.copy(
        rows = planRows + medicationRows + changes + correction + base.rows + observations + appointments,
        summaries = summaries,
        medicationPlanIds = medicationIds.toSet(),
        medicationSeriesIds = medicationIds.toSet(),
    )
}
