package com.bitavida.app.ui.report

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.bitavida.app.R
import com.bitavida.app.domain.usecase.FollowUpReport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-MX")

internal fun reportPeriodTitle(mode: String, start: LocalDate): String =
    when (mode) {
        "month" -> start.format(DateTimeFormatter.ofPattern("MMMM yyyy", spanish))
            .replaceFirstChar { it.titlecase(spanish) }
        "consultations" -> "Entre consultas"
        else -> "Periodo personalizado"
    }

internal fun reportPeriodDescription(start: LocalDate, end: LocalDate): String {
    val firstPattern = if (start.year == end.year) "d 'de' MMMM" else "d 'de' MMMM yyyy"
    val first = start.format(DateTimeFormatter.ofPattern(firstPattern, spanish))
    val lastPattern = if (start.year == end.year) "d 'de' MMMM" else "d 'de' MMMM yyyy"
    return "Del $first al ${end.format(DateTimeFormatter.ofPattern(lastPattern, spanish))}"
}

@Composable
internal fun ReportHero(
    start: LocalDate,
    end: LocalDate,
    mode: String,
    periodContext: String?,
    actionsEnabled: Boolean,
    exporting: Boolean,
    onSelectPeriod: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) {
                    Icon(painterResource(R.drawable.ic_v2_file), null, Modifier.padding(10.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Text("Tu informe de seguimiento", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Surface(onClick = onSelectPeriod, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_v2_calendar), null, Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(reportPeriodTitle(mode, start), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(reportPeriodDescription(start, end), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        periodContext?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Icon(painterResource(R.drawable.ic_v2_chevron), null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPreview, enabled = actionsEnabled && !exporting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Vista previa") }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable fun SecondaryAction(label: String, icon: Int, onClick: () -> Unit) {
                        OutlinedButton(onClick = onClick, enabled = actionsEnabled && !exporting,
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, if (actionsEnabled && !exporting)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(painterResource(icon), null, Modifier.size(20.dp))
                                Text(label, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    SecondaryAction(if (exporting) "Preparando…" else "Exportar PDF", R.drawable.ic_v2_file, onExport)
                    SecondaryAction("Compartir PDF", android.R.drawable.ic_menu_share, onShare)
                }
            }
            if (!actionsEnabled && !exporting) {
                Text("Disponible cuando haya información para este periodo.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun ReportActivity(report: FollowUpReport) {
    val counts = report.summaries.associate { it.label to it.count }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("Actividad registrada", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text("Información disponible para este informe", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ActivityGrid {
            ActivityTile("Medicamentos", counts["Registros de toma"] ?: 0, "tomas", R.drawable.ic_v2_pill)
            ActivityTile("Mediciones", (counts["Lecturas de glucosa"] ?: 0) +
                (counts["Lecturas de presión"] ?: 0) + (counts["Registros de peso"] ?: 0),
                "registros", R.drawable.ic_v2_droplet)
            ActivityTile("Observaciones", counts["Registros de observación"] ?: 0, "registros",
                R.drawable.ic_v2_stethoscope)
            ActivityTile("Citas", counts["Citas"] ?: 0, "eventos",
                R.drawable.ic_v2_calendar)
        }
    }
}

@Composable
private fun ActivityGrid(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val width = ((constraints.maxWidth - gap) / 2).coerceAtLeast(0)
        val height = measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        val boundedHeight = height.coerceAtMost((Constraints.Infinity - gap) / 2)
        val children = measurables.map {
            it.measure(Constraints.fixed(width, boundedHeight))
        }
        layout(constraints.maxWidth, boundedHeight * 2 + gap) {
            children.forEachIndexed { index, child ->
                child.placeRelative((index % 2) * (width + gap), (index / 2) * (boundedHeight + gap))
            }
        }
    }
}

@Composable
private fun ActivityTile(label: String, count: Int, unit: String, @DrawableRes icon: Int,
    modifier: Modifier = Modifier) {
    Surface(modifier = modifier.heightIn(min = 80.dp),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(unit, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ReportSections(enabled: Boolean, onOpen: (String) -> Unit) {
    val sections = listOf(
        Triple("Medicamentos y seguimiento", "Tomas, omisiones e indicaciones registradas.", R.drawable.ic_v2_pill),
        Triple("Observaciones", "Cambios, señales y revisiones documentadas.", R.drawable.ic_v2_stethoscope),
        Triple("Citas", "Consultas, estudios y acontecimientos médicos.", R.drawable.ic_v2_calendar),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Explorar el informe", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f))) {
            Column {
                sections.forEachIndexed { index, (title, description, icon) ->
                    Surface(onClick = { onOpen(title) }, enabled = enabled, color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(painterResource(icon), null, Modifier.padding(9.dp).size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(painterResource(R.drawable.ic_v2_chevron), null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index < sections.lastIndex)
                        HorizontalDivider(Modifier.padding(start = 58.dp, end = 14.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
    }
}
