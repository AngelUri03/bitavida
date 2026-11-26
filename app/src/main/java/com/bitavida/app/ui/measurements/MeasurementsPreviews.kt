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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.usecase.projectMeasurementOverview
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.math.BigDecimal
import java.time.LocalDate

private val previewToday = LocalDate.of(2026, 11, 18)

/** Fictitious fixtures are kept exclusively in preview functions, never supplied by the app route. */
private fun previewMeasurements(): List<MeasurementRecord> = (0L..2L).flatMap { day ->
    val date = previewToday.minusDays(day).atTime(8, 15)
    listOf(
        MeasurementRecord(
            "preview-glucose-$day",
            MeasurementValue.Glucose((126 + day * 4).toString(), GlucoseContext.FASTING),
            date, date,
        ),
        MeasurementRecord(
            "preview-pressure-$day",
            MeasurementValue.BloodPressure((118 + day * 2).toString(), (72 + day).toString(), "78"),
            date, date,
        ),
        MeasurementRecord(
            "preview-weight-$day",
            MeasurementValue.Weight(BigDecimal("74.2").add(BigDecimal.valueOf(day, 1)), BigDecimal("1.68")),
            date, date,
        ),
    )
}

@Preview(name = "Principal · claro", showBackground = true)
@Preview(name = "Principal · oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Principal · 320 dp", widthDp = 320, heightDp = 760, showBackground = true)
@Preview(name = "Principal · texto 200 %", widthDp = 320, heightDp = 900, fontScale = 2f, showBackground = true)
@Composable
private fun MeasurementsMainPreview() {
    BitavidaTheme {
        MeasurementsScreen(
            personUnderCareDisplayName = "Persona de ejemplo",
            onBack = {},
            onTodaySelected = {},
            today = previewToday,
        )
    }
}

@Preview(name = "Principal con datos ficticios", showBackground = true)
@Preview(
    name = "Principal con datos · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(name = "Principal con datos · 320 dp", widthDp = 320, heightDp = 900, showBackground = true)
@Preview(
    name = "Principal con datos · texto 200 %",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun MeasurementsPopulatedPreview() {
    BitavidaTheme {
        MeasurementsScreen("Persona de ejemplo", {}, {}, records = previewMeasurements(), today = previewToday)
    }
}

class MeasurementKindPreviewProvider : PreviewParameterProvider<MeasurementKind> {
    override val values = MeasurementKind.entries.asSequence()
}

@Preview(name = "Detalle vacío · claro", showBackground = true)
@Preview(name = "Detalle vacío · oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Detalle vacío · 320 dp", widthDp = 320, heightDp = 760, showBackground = true)
@Preview(name = "Detalle vacío · texto 200 %", widthDp = 320, heightDp = 900, fontScale = 2f, showBackground = true)
@Composable
private fun MeasurementEmptyDetailPreview(@PreviewParameter(MeasurementKindPreviewProvider::class) kind: MeasurementKind) {
    MeasurementDetailPreviewContent(kind, emptyList())
}

@Preview(name = "Detalle con datos · claro", showBackground = true)
@Preview(name = "Detalle con datos · oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Detalle con datos · 320 dp", widthDp = 320, heightDp = 760, showBackground = true)
@Preview(name = "Detalle con datos · texto 200 %", widthDp = 320, heightDp = 900, fontScale = 2f, showBackground = true)
@Composable
private fun MeasurementPopulatedDetailPreview(@PreviewParameter(MeasurementKindPreviewProvider::class) kind: MeasurementKind) {
    MeasurementDetailPreviewContent(kind, previewMeasurements())
}

@Composable
private fun MeasurementDetailPreviewContent(kind: MeasurementKind, records: List<MeasurementRecord>) {
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                MeasurementHeader(kind.label(), "Persona de ejemplo", false, kind)
                MeasurementDetail(
                    summary = projectMeasurementOverview(records, previewToday).primary.first { it.kind == kind },
                    records = records,
                    today = previewToday,
                    onRegister = null,
                    onFollowUpAction = null,
                )
            }
        }
    }
}
