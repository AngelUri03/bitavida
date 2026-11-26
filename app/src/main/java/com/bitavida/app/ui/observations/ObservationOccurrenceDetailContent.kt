package com.bitavida.app.ui.observations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.bitavida.app.R
import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationOccurrence
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.ui.measurements.InformationCard
import com.bitavida.app.ui.measurements.MeasurementButton
import com.bitavida.app.ui.measurements.MeasurementButtonStyle
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.measurements.SecondaryText
import com.bitavida.app.ui.measurements.SectionTitle
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val occurrenceLocale = Locale.forLanguageTag("es-MX")
private val occurrenceDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(occurrenceLocale)
private val occurrenceTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(occurrenceLocale)

@Composable
internal fun ObservationOccurrenceDetailContent(
    state: ObservationsUiState,
    onAction: (ObservationsAction) -> Unit,
    onRegister: (ObservationOccurrence) -> Unit,
) {
    val occurrence = state.selectedOccurrence ?: return
    val definition = ObservationCatalog.get(occurrence.observationId)
    val storedRecord = state.snapshot.records.firstOrNull { it.occurrenceId == occurrence.id }
    val followUp = state.snapshot.followUps.firstOrNull { it.schedule.id == occurrence.followUpId }
    val revision = followUp?.schedule?.revisions?.firstOrNull { it.id == occurrence.followUpRevisionId }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        Text(
            definition.name,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        MeasurementPanel(modifier = Modifier.testTag("observation_occurrence_detail")) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeasurementIcon(R.drawable.ic_v2_clock)
                Column(Modifier.weight(1f)) {
                    SectionTitle("Revisión programada", small = true)
                    SecondaryText(
                        occurrence.scheduledAt.toLocalDate().format(occurrenceDate) + " · " +
                            occurrence.scheduledAt.toLocalTime().format(occurrenceTime),
                    )
                }
            }
            occurrence.windowEndAt?.let {
                SecondaryText(
                    "Ventana hasta " + it.toLocalTime().format(occurrenceTime) +
                        if (it.toLocalDate() != occurrence.scheduledAt.toLocalDate()) " del día siguiente" else "",
                )
            }
            Text(
                storedRecord?.result?.label() ?: "Sin confirmar",
                style = MaterialTheme.typography.titleLarge,
                color = if (storedRecord == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
            revision?.indication?.takeIf(String::isNotBlank)?.let {
                SectionTitle("Indicación correspondiente", small = true)
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        when {
            storedRecord == null -> {
                InformationCard(
                    "Pendiente de confirmar",
                    "Aún no se ha declarado si esta revisión se realizó. Sin confirmar no significa que no hubiera cambios.",
                )
                MeasurementButton(
                    "Registrar revisión",
                    { onRegister(occurrence) },
                    Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                    icon = R.drawable.ic_v2_plus,
                )
                MeasurementButton(
                    "No se realizó",
                    { onAction(ObservationsAction.NotPerformed(occurrence)) },
                    Modifier.fillMaxWidth(),
                    style = MeasurementButtonStyle.OUTLINED,
                    enabled = !state.saving,
                    icon = R.drawable.ic_v2_close,
                )
            }
            storedRecord.result == ObservationResult.NOT_PERFORMED -> {
                InformationCard(
                    "Revisión declarada como no realizada",
                    "Puedes corregir esta declaración. La versión anterior y el motivo de la corrección permanecerán en el historial.",
                )
                MeasurementButton(
                    "Corregir y registrar revisión",
                    { onAction(ObservationsAction.CorrectRecord(storedRecord.id)) },
                    Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                    icon = R.drawable.ic_v2_edit,
                )
                MeasurementButton(
                    "Consultar declaración",
                    { onAction(ObservationsAction.ViewRecord(storedRecord.id)) },
                    Modifier.fillMaxWidth(),
                    style = MeasurementButtonStyle.OUTLINED,
                    enabled = !state.saving,
                )
            }
            else -> {
                InformationCard(
                    "Revisión registrada",
                    "El resultado está asociado a esta ocurrencia programada. Las correcciones conservarán la declaración anterior.",
                )
                MeasurementButton(
                    "Consultar registro",
                    { onAction(ObservationsAction.ViewRecord(storedRecord.id)) },
                    Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                )
                MeasurementButton(
                    "Corregir observación",
                    { onAction(ObservationsAction.CorrectRecord(storedRecord.id)) },
                    Modifier.fillMaxWidth(),
                    style = MeasurementButtonStyle.OUTLINED,
                    enabled = !state.saving,
                    icon = R.drawable.ic_v2_edit,
                )
            }
        }
    }
}
