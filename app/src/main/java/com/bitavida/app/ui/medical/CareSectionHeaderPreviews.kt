package com.bitavida.app.ui.medical

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.R
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Medicamentos · claro", widthDp = 390, heightDp = 400, showBackground = true)
@Preview(name = "Medicamentos · oscuro", widthDp = 390, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Medicamentos · 320 dp", widthDp = 320, heightDp = 400, showBackground = true)
@Preview(name = "Medicamentos · texto 200 %", widthDp = 320, heightDp = 550, fontScale = 2f, showBackground = true)
@Composable
private fun MedicationsSectionHeaderPreview() {
    SectionHeaderPreviewContent(measurements = false)
}

@Preview(name = "Mediciones · claro", widthDp = 390, heightDp = 400, showBackground = true)
@Preview(name = "Mediciones · oscuro", widthDp = 390, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Mediciones · 320 dp", widthDp = 320, heightDp = 400, showBackground = true)
@Preview(name = "Mediciones · texto 200 %", widthDp = 320, heightDp = 550, fontScale = 2f, showBackground = true)
@Composable
private fun MeasurementsSectionHeaderPreview() {
    SectionHeaderPreviewContent(measurements = true)
}

@Composable
private fun SectionHeaderPreviewContent(measurements: Boolean) {
    BitavidaTheme {
        BitavidaFluidBackground {
            CareSectionHeader(
                title = if (measurements) "Mediciones" else "Medicamentos",
                person = "Persona de ejemplo",
                iconRes = if (measurements) R.drawable.ic_v2_droplet else R.drawable.ic_v2_pill,
                onBack = {},
                motionAllowed = false,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = BitavidaSpacing.ScreenHorizontal),
                subtitle = if (measurements) "Seguimiento de diabetes tipo 2" else null,
            )
        }
    }
}
