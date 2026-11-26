package com.bitavida.app.ui.demo

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme

/** Fixed visual phase of an already confirmed fictitious record; no animation or mutation runs. */
@Composable
private fun TodayMedicationConfirmationFrame(progress: Float) {
    BitavidaTheme {
        TodayTypography {
            BitavidaFluidBackground(motionAllowed = false) {
                Box(Modifier.padding(BitavidaSpacing.ScreenHorizontal)) {
                    TodayMedicationSurface(
                        "preview-confirmed", "Medicamento ficticio", "1 tableta", "10:00 a. m.",
                        enabled = false, motion = true, onTaken = {}, onOptions = {},
                        confirmed = true, confirmationProgress = { progress },
                    )
                }
            }
        }
    }
}

@Preview(name = "Confirmación persistida · Claro", widthDp = 390, heightDp = 150, locale = "es")
@Preview(name = "Confirmación persistida · Oscuro", widthDp = 390, heightDp = 150, locale = "es",
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Confirmación persistida · Texto 200 %", widthDp = 320, heightDp = 360,
    fontScale = 2f, locale = "es")
@Preview(name = "Confirmación persistida · Oscuro 200 %", widthDp = 320, heightDp = 360,
    fontScale = 2f, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayMedicationConfirmationPreview() = TodayMedicationConfirmationFrame(0.62f)

@Preview(name = "Confirmación en curso · Claro", widthDp = 390, heightDp = 150, locale = "es")
@Preview(name = "Confirmación en curso · Oscuro", widthDp = 390, heightDp = 150, locale = "es",
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Confirmación en curso · Texto 200 %", widthDp = 320, heightDp = 360,
    fontScale = 2f, locale = "es")
@Preview(name = "Confirmación en curso · Oscuro 200 %", widthDp = 320, heightDp = 360,
    fontScale = 2f, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun TodayMedicationConfirmationMidPreview() = TodayMedicationConfirmationFrame(0.4f)
