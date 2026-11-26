package com.bitavida.app.ui.demo

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Opciones de toma · Claro", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Opciones de toma · Oscuro", showBackground = true, widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Opciones de toma · 320 dp", showBackground = true, widthDp = 320, heightDp = 720)
@Preview(name = "Opciones de toma · Texto 200 %", showBackground = true, widthDp = 320, heightDp = 1100,
    fontScale = 2f)
@Composable
private fun TodayMedicationOptionsPreview() {
    BitavidaTheme {
        TodayMedicationOptionsSheet(
            item = todayPreviewProjection(false).medications.first(),
            motionAllowed = false,
            enabled = true,
            onOtherTime = {}, onWithNote = {}, onNotTaken = {}, onConsult = {}, onDismiss = {},
        )
    }
}
