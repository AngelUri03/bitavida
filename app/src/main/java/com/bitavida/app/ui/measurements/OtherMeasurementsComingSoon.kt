package com.bitavida.app.ui.measurements

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.components.BitavidaIconContainer
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors

/** Informational content only; the existing destination owns its header and navigation. */
@Composable
internal fun OtherMeasurementsComingSoon(modifier: Modifier = Modifier) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    MeasurementPanel(modifier = modifier) {
        val icon: @Composable () -> Unit = {
            BitavidaIconContainer(
                painter = painterResource(R.drawable.ic_v2_chart),
                contentDescription = null,
                containerColor = MaterialTheme.bitavidaColors.paleBlue,
            )
        }
        val title: @Composable () -> Unit = {
            Text(
                "Más mediciones, próximamente",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                icon()
                title()
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Column(Modifier.weight(1f)) { title() }
            }
        }
        Text(
            "En una próxima actualización podrás agregar otras mediciones " +
                "para complementar el seguimiento de salud",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionDivider()
        SecondaryText(
            "Por ahora, puedes continuar registrando glucosa capilar, presión arterial y peso",
        )
    }
}

@Preview(name = "Próximamente · claro", widthDp = 411, heightDp = 760, showBackground = true)
@Preview(
    name = "Próximamente · oscuro", widthDp = 411, heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true,
)
@Preview(name = "Próximamente · 320 dp", widthDp = 320, heightDp = 760, showBackground = true)
@Preview(
    name = "Próximamente · texto 200 %", widthDp = 320, heightDp = 1000,
    fontScale = 2f, showBackground = true,
)
@Composable
private fun OtherMeasurementsComingSoonPreview() {
    BitavidaTheme {
        val scroll = rememberScrollState()
        BitavidaFluidBackground(
            scrollPixels = { scroll.value.toFloat() },
            motionAllowed = false,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(horizontal = BitavidaSpacing.ScreenHorizontal)
                    .padding(bottom = BitavidaSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Large),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
                    CurrentMedicationBack(onBack = {}, motionAllowed = false, enabled = true, label = "Mediciones")
                    MeasurementHeader("Otras mediciones", "Persona de ejemplo", showSubtitle = false)
                }
                OtherMeasurementsComingSoon()
            }
        }
    }
}
