package com.bitavida.app.ui.observations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.bitavida.app.R
import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationGuideCatalog
import com.bitavida.app.ui.measurements.InformationCard
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.measurements.SecondaryText
import com.bitavida.app.ui.measurements.SectionTitle
import com.bitavida.app.ui.theme.BitavidaSpacing

@Composable
internal fun ObservationGuideContent(state: ObservationsUiState) {
    val observationId = state.draft?.observationId ?: state.selectedObservationId ?: return
    val definition = ObservationCatalog.get(observationId)
    val guide = ObservationGuideCatalog.get(observationId)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
    ) {
        Text(
            "Cómo observar",
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            definition.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        SecondaryText(definition.context)
        ObservationGuideSection("¿En qué puedes fijarte?", guide.lookFor)
        ObservationGuideSection("¿Cómo puedes describirlo?", guide.examples, examples = true)
        ObservationGuideSection("¿Qué información puede ser útil?", guide.usefulInformation)
        InformationCard(
            "Registra hechos, no necesitas conocer la causa",
            "Esta guía ayuda a observar y describir. Bitavida no interpreta lo encontrado ni establece diagnósticos.",
        )
    }
}

@Composable
private fun ObservationGuideSection(
    title: String,
    items: List<String>,
    examples: Boolean = false,
) {
    MeasurementPanel {
        SectionTitle(title, small = true)
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            ) {
                MeasurementIcon(if (examples) R.drawable.ic_v2_edit else R.drawable.ic_v2_file, 18)
                Text(
                    if (examples) "“$item”" else item,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
