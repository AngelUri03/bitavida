package com.bitavida.app.ui.measurements

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDate

@Preview(name = "Historial glucosa · claro", showBackground = true)
@Composable
private fun GlucoseHistoryLightPreview() {
    GlucoseHistoryPreviewContent()
}

@Preview(
    name = "Historial glucosa · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun GlucoseHistoryDarkPreview() {
    GlucoseHistoryPreviewContent(
        initialPeriodKind = com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind.MONTH,
    )
}

@Preview(
    name = "Historial glucosa · 320 dp",
    widthDp = 320,
    heightDp = 1000,
    showBackground = true,
)
@Composable
private fun GlucoseHistoryCompactPreview() {
    GlucoseHistoryPreviewContent()
}

@Preview(
    name = "Historial glucosa · texto 200 %",
    widthDp = 320,
    heightDp = 1200,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun GlucoseHistoryLargeTextPreview() {
    GlucoseHistoryPreviewContent()
}

@Composable
private fun GlucoseHistoryPreviewContent(
    initialPeriodKind: com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind =
        com.bitavida.app.domain.usecase.GlucoseHistoryPeriodKind.WEEK,
) {
    val today = LocalDate.of(2026, 11, 18)
    // Datos ficticios exclusivos del preview.
    val records = listOf(
        previewHistoryRecord("one", today.minusDays(2), 7, "102", GlucoseContext.FASTING),
        previewHistoryRecord("two", today.minusDays(2), 14, "148", GlucoseContext.AFTER_MEAL),
        previewHistoryRecord("three", today.minusDays(1), 8, "HI", GlucoseContext.FASTING),
        previewHistoryRecord("four", today, 7, "110", GlucoseContext.FASTING),
        previewHistoryRecord("five", today, 13, "136", GlucoseContext.BEFORE_MEAL),
        previewHistoryRecord("six", today, 18, "124", GlucoseContext.OTHER),
    )
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                GlucoseHistoryContent(
                    records = records,
                    today = today,
                    initialPeriodKind = initialPeriodKind,
                )
            }
        }
    }
}

private fun previewHistoryRecord(
    id: String,
    date: LocalDate,
    hour: Int,
    reading: String,
    context: GlucoseContext,
): MeasurementRecord {
    val occurredAt = date.atTime(hour, 15)
    return MeasurementRecord(
        id = id,
        value = MeasurementValue.Glucose(reading, context),
        occurredAt = occurredAt,
        capturedAt = occurredAt,
    )
}
