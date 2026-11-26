package com.bitavida.app.ui.demo

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.R
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Claro · 320", showBackground = true, widthDp = 320, heightDp = 440)
@Preview(name = "Oscuro · 320", showBackground = true, widthDp = 320, heightDp = 440,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Amplio · Claro", showBackground = true, widthDp = 390, heightDp = 440)
@Preview(name = "Texto 200 %", showBackground = true, widthDp = 320, heightDp = 650, fontScale = 2f)
@Preview(name = "Oscuro · Texto 200 %", showBackground = true, widthDp = 320, heightDp = 650,
    fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class TodayMedicationLayoutVariants

@TodayMedicationLayoutVariants
@Composable
internal fun TodayMedicationFixedLayoutPreview() {
    TodayMedicationLayoutSample("fixed", "10:00 a. m.")
}

@TodayMedicationLayoutVariants
@Composable
internal fun TodayMedicationMorningWindowLayoutPreview() {
    TodayMedicationLayoutSample("morning-window", "11:00 a. m. – 12:00 p. m.")
}

@TodayMedicationLayoutVariants
@Composable
internal fun TodayMedicationAfternoonWindowLayoutPreview() {
    TodayMedicationLayoutSample("afternoon-window", "12:00 p. m. – 1:00 p. m.")
}

@TodayMedicationLayoutVariants
@Composable
internal fun TodayMedicationContextLayoutPreview() {
    TodayMedicationLayoutSample(
        "context", "Con el desayuno · 10:00 a. m.", R.drawable.ic_v2_utensils,
    )
}

/** Fictitious data: every schedule uses the same surface, metadata flow and integrated action band. */
@Composable
private fun TodayMedicationLayoutSample(
    id: String,
    schedule: String,
    scheduleIcon: Int = R.drawable.ic_v2_clock,
) {
    BitavidaTheme {
        TodayTypography {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = BitavidaSpacing.Section, vertical = BitavidaSpacing.Large),
                ) {
                    TodayMedicationSurface(
                        id = "layout-preview-$id",
                        name = "Medicamento ficticio",
                        amount = "1 tableta",
                        schedule = schedule,
                        enabled = true,
                        motion = false,
                        onTaken = {},
                        onOptions = {},
                        scheduleIcon = scheduleIcon,
                    )
                }
            }
        }
    }
}
